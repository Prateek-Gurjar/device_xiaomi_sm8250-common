package org.lineageos.settings.batterymonitor;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

public final class BatteryMonitorUtils {
    public static final String ENABLED = "battery_monitor_enabled";
    private BatteryMonitorUtils() {}
    public static void start(Context context) {
        Intent intent = new Intent(context, BatteryMonitorService.class);
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(ENABLED, false))
            context.startForegroundService(intent);
        else context.stopService(intent);
    }
    public static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }
}
