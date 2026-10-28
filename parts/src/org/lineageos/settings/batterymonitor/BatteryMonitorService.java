package org.lineageos.settings.batterymonitor;

import android.app.*;
import android.content.*;
import android.os.*;
import android.util.Log;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Polls the standard power_supply nodes provided by the device QCOM fuel gauge and charger. */
public class BatteryMonitorService extends Service {
    private static final String CHANNEL = "battery_monitor";
    private static final int NOTIFICATION_ID = 74;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long previousElapsed, previousUptime;
    private long previousScreenElapsed;
    private boolean screenInteractive;
    private int previousLevel = -1;
    private double screenOnDrain, screenOffDrain, deepSleepDrain, awakeDrain;
    private long screenOnMs, screenOffMs, deepSleepMs, awakeMs;
    private final Runnable update = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 60_000); }
    };
    private final BroadcastReceiver powerReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_POWER_CONNECTED.equals(intent.getAction())
                    && BatteryMonitorUtils.prefs(context).getBoolean("bm_reset_plug", false)) resetUsage();
        }
    };
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            long now = SystemClock.elapsedRealtime();
            addScreenDuration(now - previousScreenElapsed, screenInteractive);
            previousScreenElapsed = now;
            screenInteractive = Intent.ACTION_SCREEN_ON.equals(intent.getAction());
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Battery monitor", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Battery readings and usage estimates");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        startForeground(NOTIFICATION_ID, notification("Reading battery…", ""));
        android.content.SharedPreferences p = BatteryMonitorUtils.prefs(this);
        if (p.getBoolean("bm_reset_boot", false)) resetUsage(); else loadStats();
        registerReceiver(powerReceiver, new IntentFilter(Intent.ACTION_POWER_CONNECTED));
        previousElapsed = SystemClock.elapsedRealtime();
        previousUptime = SystemClock.uptimeMillis();
        screenInteractive = getSystemService(PowerManager.class).isInteractive();
        previousScreenElapsed = previousElapsed;
        IntentFilter screenFilter = new IntentFilter();
        screenFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, screenFilter);
        handler.post(update);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "reset".equals(intent.getAction())) resetUsage();
        if (!BatteryMonitorUtils.prefs(this).getBoolean(BatteryMonitorUtils.ENABLED, false)) {
            stopSelf(); return START_NOT_STICKY;
        }
        refresh(); return START_STICKY;
    }

    private void refresh() {
        try {
            android.content.SharedPreferences p = BatteryMonitorUtils.prefs(this);
            long elapsed = SystemClock.elapsedRealtime(), uptime = SystemClock.uptimeMillis();
            long elapsedDelta = Math.max(0, elapsed - previousElapsed);
            long uptimeDelta = Math.max(0, uptime - previousUptime);
            long sleepDelta = Math.max(0, elapsedDelta - uptimeDelta);
            boolean screenOn = getSystemService(PowerManager.class).isInteractive();
            int level = number("/sys/class/power_supply/battery/capacity", -1);
            int temp = number("/sys/class/power_supply/battery/temp", -1);
            int current = number("/sys/class/power_supply/battery/current_now", 0);
            int voltage = number("/sys/class/power_supply/battery/voltage_now", 0);
            int inputCurrent = firstNumber("/sys/class/power_supply/usb/current_now", "/sys/class/power_supply/main/current_now");
            int inputVoltage = firstNumber("/sys/class/power_supply/usb/voltage_now", "/sys/class/power_supply/main/voltage_now");
            long fullSeconds = number("/sys/class/power_supply/battery/time_to_full_now", -1);
            String status = read("/sys/class/power_supply/battery/status");
            boolean charging = "Charging".equals(status);

            if (level >= 0) {
                if (checkLevelReset(level, p, charging)) elapsedDelta = uptimeDelta = sleepDelta = 0;
                if (previousLevel >= 0 && level < previousLevel && elapsedDelta > 0 && !charging) {
                    double consumed = previousLevel - level;
                    if (screenOn) {
                        screenOnDrain += consumed;
                    } else {
                        screenOffDrain += consumed;
                    }
                    if (elapsedDelta > 0) {
                        deepSleepDrain += consumed * sleepDelta / (double) elapsedDelta;
                        awakeDrain += consumed * uptimeDelta / (double) elapsedDelta;
                    }
                }
                previousLevel = level;
            }
            awakeMs += uptimeDelta;
            deepSleepMs += sleepDelta;
            previousElapsed = elapsed; previousUptime = uptime;
            addScreenDuration(elapsed - previousScreenElapsed, screenInteractive);
            previousScreenElapsed = elapsed;
            saveStats();

            List<String> fields = new ArrayList<>();
            add(fields, p, "bm_show_level", "🔋", "Battery", level < 0 ? "—" : level + "%");
            String unit = p.getString("battery_temp_unit", "c");
            String tempValue = temp < 0 ? "—" : String.format(Locale.getDefault(), "%.1f°%s",
                    unit.equals("f") ? (temp / 10f * 9 / 5) + 32 : temp / 10f, unit.equals("f") ? "F" : "C");
            add(fields, p, "bm_show_temp", "🌡", "Temperature", tempValue);
            add(fields, p, "bm_current", "⚡", charging ? "Charge current" : "Discharging", String.format(Locale.getDefault(), "%d mA", Math.abs((charging && inputCurrent != 0 ? inputCurrent : current) / 1000)));
            if (!charging) {
                add(fields, p, "bm_drain", "↘", "Active drain", rate(screenOnDrain, screenOnMs));
                add(fields, p, "bm_idle", "◌", "Idle drain", rate(screenOffDrain, screenOffMs));
            }
            if (charging && p.getBoolean("bm_charge", true)) {
                double watts = Math.abs(inputCurrent) * (inputVoltage > 0 ? inputVoltage : voltage) / 1.0e9;
                String charge = String.format(Locale.getDefault(), "%d mA · %.1f W", Math.abs(inputCurrent / 1000), watts);
                if (fullSeconds > 0) charge += " · " + formatDuration(fullSeconds * 1000);
                add(fields, p, "bm_charge", "ϟ", "Charging", charge);
            }
            addUsage(fields, p, "bm_screen_off", "☾", "Screen off", screenOffMs, screenOffDrain);
            addUsage(fields, p, "bm_screen_on", "☀", "Screen on", screenOnMs, screenOnDrain);
            addUsage(fields, p, "bm_deep_sleep", "⌁", "Deep sleep", deepSleepMs, deepSleepDrain);
            addUsage(fields, p, "bm_awake", "◷", "Awake", awakeMs, awakeDrain);
            render(fields, p.getString("bm_style", "both"));
        } catch (Exception e) { Log.w("BatteryMonitor", "Unable to update battery telemetry", e); }
    }

    private void addUsage(List<String> out, android.content.SharedPreferences p, String key,
            String icon, String label, long duration, double consumed) {
        if (p.getBoolean(key, true)) add(out, p, key, icon, label,
                formatDuration(duration) + " · " + String.format(Locale.getDefault(), "%.1f%%", consumed));
    }
    private static void add(List<String> out, android.content.SharedPreferences p, String key,
            String icon, String label, String value) {
        if (p.getBoolean(key, true)) out.add(icon + "\u0001" + label + "\u0001" + value);
    }
    private String rate(double consumed, long duration) {
        return duration <= 0 ? "— %/h" : String.format(Locale.getDefault(), "%.1f%%/h", consumed * 3_600_000.0 / duration);
    }
    private void render(List<String> fields, String mode) {
        StringBuilder shortText = new StringBuilder();
        StringBuilder bigText = new StringBuilder();
        for (String field : fields) {
            int first = field.indexOf('\u0001');
            int second = field.indexOf('\u0001', first + 1);
            String[] part = {field.substring(0, first), field.substring(first + 1, second), field.substring(second + 1)};
            String line;
            if ("icons".equals(mode)) line = part[0] + " " + part[2];
            else if ("text".equals(mode)) line = part[1] + ": " + part[2];
            else line = part[0] + " " + part[1] + "  " + part[2];
            if (bigText.length() > 0) bigText.append('\n');
            bigText.append(line);
            if (shortText.length() < 90) { if (shortText.length() > 0) shortText.append("  ·  "); shortText.append(line); }
        }
        if (bigText.length() == 0) bigText.append("No notification data selected");
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID,
                notification(shortText.toString(), bigText.toString()));
    }
    private Notification notification(String shortText, String expandedText) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, BatteryMonitorActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
                .setContentTitle("Battery monitor").setContentText(shortText)
                .setStyle(new Notification.BigTextStyle().bigText(expandedText))
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).build();
    }
    private boolean checkLevelReset(int level, android.content.SharedPreferences p, boolean charging) {
        int threshold;
        try { threshold = Integer.parseInt(p.getString("bm_reset_level", "100")); }
        catch (NumberFormatException e) { threshold = 100; }
        threshold = Math.max(1, Math.min(100, threshold));
        if (charging && level >= threshold && previousLevel >= 0 && previousLevel < threshold) { resetUsage(); return true; }
        return false;
    }
    private void loadStats() {
        android.content.SharedPreferences p = BatteryMonitorUtils.prefs(this);
        screenOnMs=p.getLong("bm_on_ms",0); screenOffMs=p.getLong("bm_off_ms",0);
        deepSleepMs=p.getLong("bm_deep_ms",0); awakeMs=p.getLong("bm_awake_ms",0);
        screenOnDrain=Double.longBitsToDouble(p.getLong("bm_on_drain",0));
        screenOffDrain=Double.longBitsToDouble(p.getLong("bm_off_drain",0));
        deepSleepDrain=Double.longBitsToDouble(p.getLong("bm_deep_drain",0));
        awakeDrain=Double.longBitsToDouble(p.getLong("bm_awake_drain",0));
    }
    private void saveStats() {
        BatteryMonitorUtils.prefs(this).edit().putLong("bm_on_ms",screenOnMs).putLong("bm_off_ms",screenOffMs)
                .putLong("bm_deep_ms",deepSleepMs).putLong("bm_awake_ms",awakeMs)
                .putLong("bm_on_drain",Double.doubleToRawLongBits(screenOnDrain))
                .putLong("bm_off_drain",Double.doubleToRawLongBits(screenOffDrain))
                .putLong("bm_deep_drain",Double.doubleToRawLongBits(deepSleepDrain))
                .putLong("bm_awake_drain",Double.doubleToRawLongBits(awakeDrain)).apply();
    }
    private void resetUsage() {
        screenOnMs=screenOffMs=deepSleepMs=awakeMs=0;
        screenOnDrain=screenOffDrain=deepSleepDrain=awakeDrain=0;
        saveStats(); previousLevel=-1;
        long nowElapsed=SystemClock.elapsedRealtime();
        previousElapsed=nowElapsed; previousUptime=SystemClock.uptimeMillis(); previousScreenElapsed=nowElapsed;
    }
    private void addScreenDuration(long delta, boolean screenOn) {
        if (delta <= 0) return;
        if (screenOn) screenOnMs += delta; else screenOffMs += delta;
    }
    private static String read(String path) { try (BufferedReader r = new BufferedReader(new FileReader(path))) { return r.readLine().trim(); } catch (Exception e) { return ""; } }
    private static int number(String path, int fallback) { try { return Integer.parseInt(read(path)); } catch (Exception e) { return fallback; } }
    private static int firstNumber(String first, String second) { int value=number(first,-1); return value < 0 ? number(second,0) : value; }
    private static String formatDuration(long ms) { long minutes=ms/60_000; return (minutes/60)+"h "+(minutes%60)+"m"; }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { handler.removeCallbacks(update); try { unregisterReceiver(powerReceiver); unregisterReceiver(screenReceiver); } catch (Exception ignored) {} super.onDestroy(); }
}
