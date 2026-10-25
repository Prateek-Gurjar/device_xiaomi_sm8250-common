/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.batterystats;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.R;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

public class BatteryStatsMonitorService extends Service {
    public static final String ACTION_START = "org.lineageos.settings.batterystats.START";
    public static final String ACTION_STOP = "org.lineageos.settings.batterystats.STOP";
    public static final String ACTION_REFRESH = "org.lineageos.settings.batterystats.REFRESH";
    public static final String PREF_NOTIFICATION_ENABLED = "battery_stats_notification_enabled";
    public static final String PREF_AUTO_RESET = "battery_stats_auto_reset";
    public static final String PREF_RESET_ON_CHARGER = "battery_stats_reset_on_charger";
    public static final String PREF_RESET_ON_REBOOT = "battery_stats_reset_on_reboot";

    private static final String KEY_PREFIX = "battery_stats_show_";
    private static final String KEY_SAMPLE_CURRENT_TOTAL = "battery_stats_sample_current_total_";
    private static final String KEY_SAMPLE_COUNT = "battery_stats_sample_count_";
    private static final String CHANNEL_ID = "battery_stats";
    private static final int NOTIFICATION_ID = 2201;
    private static final long UPDATE_INTERVAL_MS = 60_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final long[] sampleCurrentTotalUa = new long[2];
    private final long[] sampleCount = new long[2];
    private boolean receiverRegistered;

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_POWER_CONNECTED.equals(intent.getAction())
                    && PreferenceManager.getDefaultSharedPreferences(
                            BatteryStatsMonitorService.this).getBoolean(
                                    PREF_AUTO_RESET, true)
                    && PreferenceManager.getDefaultSharedPreferences(
                            BatteryStatsMonitorService.this).getBoolean(
                                    PREF_RESET_ON_CHARGER, true)) {
                clearSamples();
            }
            updateNotification();
        }
    };
    private final Runnable periodicUpdate = new Runnable() {
        @Override
        public void run() {
            updateNotification();
            handler.postDelayed(this, UPDATE_INTERVAL_MS);
        }
    };

    public static void start(Context context) {
        Intent intent = new Intent(context, BatteryStatsMonitorService.class)
                .setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void refresh(Context context) {
        context.startService(new Intent(context, BatteryStatsMonitorService.class)
                .setAction(ACTION_REFRESH));
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, BatteryStatsMonitorService.class));
    }

    public static void resetStoredSamples(Context context) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .remove(KEY_SAMPLE_CURRENT_TOTAL + "0")
                .remove(KEY_SAMPLE_CURRENT_TOTAL + "1")
                .remove(KEY_SAMPLE_COUNT + "0")
                .remove(KEY_SAMPLE_COUNT + "1")
                .apply();
    }

    private void clearSamples() {
        for (int i = 0; i < 2; i++) {
            sampleCurrentTotalUa[i] = 0;
            sampleCount[i] = 0;
        }
        resetStoredSamples(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification(
                getString(R.string.battery_stats_updating)));
        loadSamples();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(updateReceiver, filter);
        receiverRegistered = true;
        handler.post(periodicUpdate);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        updateNotification();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(periodicUpdate);
        if (receiverRegistered) unregisterReceiver(updateReceiver);
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void updateNotification() {
        BatteryManager manager = (BatteryManager) getSystemService(BATTERY_SERVICE);
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return;

        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int percent = scale > 0 && level >= 0 ? Math.round(level * 100f / scale) : -1;
        int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN);
        int tempTenths = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        long currentUa = manager == null ? Long.MIN_VALUE
                : manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        long chargeUah = manager == null ? Long.MIN_VALUE
                : manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        long currentAbsUa = currentUa == Long.MIN_VALUE ? -1 : Math.abs(currentUa);
        float drainPercentPerHour = -1;
        if (currentAbsUa > 0 && chargeUah > 0 && percent > 0) {
            float fullCapacityUah = chargeUah * 100f / percent;
            drainPercentPerHour = currentAbsUa * 100f / fullCapacityUah;
        }

        boolean screenOn = isInteractive();
        int sampleIndex = screenOn ? 1 : 0;
        if (!charging && currentAbsUa > 0) {
            sampleCurrentTotalUa[sampleIndex] += currentAbsUa;
            sampleCount[sampleIndex]++;
            saveSamples();
        }

        ArrayList<String> lines = new ArrayList<>();
        if (enabled("active_drain")) lines.add(getString(R.string.battery_stats_active_drain)
                + ": " + formatRate(!charging && screenOn ? drainPercentPerHour
                : (!charging ? sampledRate(1, chargeUah, percent) : -1)));
        if (enabled("idle_drain")) lines.add(getString(R.string.battery_stats_idle_drain)
                + ": " + formatRate(!charging && !screenOn ? drainPercentPerHour
                : (!charging ? sampledRate(0, chargeUah, percent) : -1)));
        if (enabled("charging")) lines.add(getString(R.string.battery_stats_charging_label)
                + ": " + getString(charging ? R.string.battery_stats_charging
                        : R.string.battery_stats_discharging));
        if (enabled("charge_time")) lines.add(getString(R.string.battery_stats_charge_time)
                + ": " + formatChargeTime(manager, charging, percent, drainPercentPerHour));
        if (enabled("remaining_time")) lines.add(getString(R.string.battery_stats_remaining_time)
                + ": " + formatRemainingTime(percent, drainPercentPerHour));
        if (enabled("temperature")) lines.add(tempTenths >= 0
                ? getString(R.string.battery_stats_temperature, tempTenths / 10f)
                : getString(R.string.battery_stats_unavailable));

        String text = lines.isEmpty() ? getString(R.string.battery_stats_no_metrics)
                : join(lines);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .notify(NOTIFICATION_ID, buildNotification(text));
    }

    private Notification buildNotification(String text) {
        Intent settings = new Intent(this, BatteryStatsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, settings,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.charger_24px)
                .setContentTitle(getString(R.string.battery_stats_title))
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(contentIntent)
                .setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.battery_stats_title), NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.battery_stats_notification_summary));
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                    .createNotificationChannel(channel);
        }
    }

    private boolean enabled(String metric) {
        return PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(KEY_PREFIX + metric, true);
    }

    private float sampledRate(int index, long chargeUah, int percent) {
        if (sampleCount[index] <= 0 || chargeUah <= 0 || percent <= 0) return -1;
        float fullCapacityUah = chargeUah * 100f / percent;
        float averageCurrentUa = (float) sampleCurrentTotalUa[index] / sampleCount[index];
        return averageCurrentUa * 100f / fullCapacityUah;
    }

    private void loadSamples() {
        android.content.SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(this);
        for (int i = 0; i < 2; i++) {
            sampleCurrentTotalUa[i] = prefs.getLong(KEY_SAMPLE_CURRENT_TOTAL + i, 0);
            sampleCount[i] = prefs.getLong(KEY_SAMPLE_COUNT + i, 0);
        }
    }

    private void saveSamples() {
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putLong(KEY_SAMPLE_CURRENT_TOTAL + "0", sampleCurrentTotalUa[0])
                .putLong(KEY_SAMPLE_CURRENT_TOTAL + "1", sampleCurrentTotalUa[1])
                .putLong(KEY_SAMPLE_COUNT + "0", sampleCount[0])
                .putLong(KEY_SAMPLE_COUNT + "1", sampleCount[1])
                .apply();
    }

    private String formatRate(float rate) {
        return rate >= 0 ? getString(R.string.battery_stats_drain, rate)
                : getString(R.string.battery_stats_unavailable);
    }

    private String formatChargeTime(BatteryManager manager, boolean charging, int percent,
            float rate) {
        if (!charging || percent < 0) return getString(R.string.battery_stats_unavailable);
        long kernelSeconds = readKernelTimeSeconds("time_to_full_now");
        if (kernelSeconds >= 0) return formatDuration(kernelSeconds / 60);
        long remainingMs = manager == null ? -1 : manager.computeChargeTimeRemaining();
        if (remainingMs > 0) return formatDuration(remainingMs / 60_000);
        return rate > 0 ? formatDuration(Math.round((100 - percent) / rate * 60))
                : getString(R.string.battery_stats_unavailable);
    }

    private String formatRemainingTime(int percent, float rate) {
        long kernelSeconds = readKernelTimeSeconds("time_to_empty_avg");
        if (kernelSeconds >= 0) return formatDuration(kernelSeconds / 60);
        return percent >= 0 && rate > 0 ? formatDuration(Math.round(percent / rate * 60))
                : getString(R.string.battery_stats_unavailable);
    }

    private long readKernelTimeSeconds(String property) {
        String path = "/sys/class/power_supply/bms/" + property;
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            long seconds = Long.parseLong(reader.readLine().trim());
            return seconds >= 0 ? seconds : -1;
        } catch (IOException | NumberFormatException | SecurityException e) {
            return -1;
        }
    }

    private String formatDuration(long minutes) {
        return String.format(Locale.getDefault(), "%dh %02dm", minutes / 60, minutes % 60);
    }

    private String join(ArrayList<String> lines) {
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            if (result.length() > 0) result.append('\n');
            result.append(line);
        }
        return result.toString();
    }
}
