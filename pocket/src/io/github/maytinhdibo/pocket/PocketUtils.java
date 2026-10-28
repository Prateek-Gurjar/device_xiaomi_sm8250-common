/*
 * MIT License
 *
 * Copyright (c) 2021 Trần Mạnh Cường <maytinhdibo>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.github.maytinhdibo.pocket;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

public class PocketUtils {
    private static final String TAG = "PocketMode";
    private static final String SETTING_ENABLED = "xiaomi_pocket_mode_enabled";
    private static final int TYPE_LARGE_AREA_TOUCH_SENSOR = 33171031;

    public static boolean isEnabled(Context context) {
        int stored = Settings.Secure.getInt(context.getContentResolver(), SETTING_ENABLED, -1);
        if (stored != -1) return stored == 1;

        android.content.SharedPreferences preferences = context.getSharedPreferences(
                PocketPreferenceFragment.BATTERY_POCKET_MODE, Context.MODE_PRIVATE);
        boolean legacy = preferences.contains("enable")
                ? preferences.getBoolean("enable", false) : true;
        Settings.Secure.putInt(context.getContentResolver(), SETTING_ENABLED, legacy ? 1 : 0);
        return legacy;
    }

    public static void setEnabled(Context context, boolean enabled) {
        Settings.Secure.putInt(context.getContentResolver(), SETTING_ENABLED, enabled ? 1 : 0);
        context.getSharedPreferences(PocketPreferenceFragment.BATTERY_POCKET_MODE,
                Context.MODE_PRIVATE).edit().putBoolean("enable", enabled).apply();
        if (enabled) startService(context);
        else stopService(context);
    }

    public static boolean isSupported(Context context) {
        SensorManager manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (manager == null) return false;
        return manager.getDefaultSensor(TYPE_LARGE_AREA_TOUCH_SENSOR) != null
                || manager.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null;
    }

    public static void startService(Context context) {
        try {
            if (!isEnabled(context)) return;
            startCommonService(context);
            if (hasLargeAreaSensor(context)) {
                context.stopService(new Intent(context, PocketService.class));
            } else {
                context.startService(new Intent(context, PocketService.class));
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to start pocket detection", e);
        }

    }

    public static void stopService(Context context) {
        try {
            context.stopService(new Intent(context, PocketService.class));
            context.stopService(new Intent().setClassName("org.lineageos.settings",
                    "org.lineageos.settings.doze.PocketService"));
        } catch (Exception e) {
            Log.e(TAG, "Unable to stop pocket detection", e);
        }
    }

    private static void startCommonService(Context context) {
        Intent intent = new Intent().setClassName("org.lineageos.settings",
                "org.lineageos.settings.doze.PocketService");
        context.startServiceAsUser(intent, UserHandle.CURRENT);
    }

    private static boolean hasLargeAreaSensor(Context context) {
        SensorManager manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        return manager != null && manager.getDefaultSensor(TYPE_LARGE_AREA_TOUCH_SENSOR) != null;
    }
}
