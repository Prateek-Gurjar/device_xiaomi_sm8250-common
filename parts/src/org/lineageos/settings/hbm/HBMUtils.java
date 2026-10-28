/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.hbm;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.provider.Settings;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.display.DcDimmingTileService;
import org.lineageos.settings.utils.FileUtils;

public final class HBMUtils {
    public static final String ACTION_MODE_CHANGED = "org.lineageos.settings.hbm.MODE_CHANGED";
    private static final String[] HBM_NODES = {
        "/sys/class/drm/card0-DSI-1/disp_param",
        "/sys/class/drm/card0/card0-DSI-1/disp_param"
    };
    private static final String BACKLIGHT = "/sys/class/backlight/panel0-backlight/brightness";
    private static final String PREF_HBM = "hbm";
    private static final String PREF_SAVED_BRIGHTNESS = "hbm_saved_brightness";
    private static final String PREF_AUTO_ACTIVE = "auto_hbm_active";

    private HBMUtils() {}

    public static boolean isSupported() {
        return getHbmNode() != null && FileUtils.isFileWritable(BACKLIGHT);
    }

    public static boolean isAutoSupported(Context context) {
        SensorManager sensorManager = context.getSystemService(SensorManager.class);
        return isSupported() && sensorManager != null
                && sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) != null;
    }

    public static boolean setManualEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (enabled && prefs.getBoolean(DcDimmingTileService.DC_DIMMING_ENABLE_KEY, false)) {
            return false;
        }
        boolean applied = setEnabled(context, enabled);
        if (applied) {
            prefs.edit().putBoolean(PREF_HBM, enabled)
                    .putBoolean(PREF_AUTO_ACTIVE, false).apply();
            notifyModeChanged(context);
        }
        return applied;
    }

    public static boolean setAutoEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (enabled && (prefs.getBoolean(PREF_HBM, false)
                || prefs.getBoolean(DcDimmingTileService.DC_DIMMING_ENABLE_KEY, false))) {
            return false;
        }
        boolean applied = setEnabled(context, enabled);
        if (applied) prefs.edit().putBoolean(PREF_AUTO_ACTIVE, enabled).apply();
        return applied;
    }

    public static void disableForDcDimming(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putBoolean(PREF_HBM, false).putBoolean(PREF_AUTO_ACTIVE, false).apply();
        if (!setEnabled(context, false)) restoreBrightness(context, prefs);
        notifyModeChanged(context);
    }

    public static void disableForScreenOff(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putBoolean(PREF_HBM, false).putBoolean(PREF_AUTO_ACTIVE, false).apply();
        if (!setEnabled(context, false)) restoreBrightness(context, prefs);
        notifyModeChanged(context);
    }

    public static boolean isAutoActive(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_AUTO_ACTIVE, false);
    }

    private static synchronized boolean setEnabled(Context context, boolean enabled) {
        String node = getHbmNode();
        if (node == null || !FileUtils.isFileWritable(BACKLIGHT)) return false;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (enabled && !prefs.contains(PREF_SAVED_BRIGHTNESS)) {
            int brightness = Settings.System.getInt(context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, 255);
            prefs.edit().putInt(PREF_SAVED_BRIGHTNESS, brightness).commit();
        }

        if (enabled) {
            boolean settingsSet = Settings.System.putInt(context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, 255);
            boolean backlightSet = FileUtils.writeLine(BACKLIGHT, "2047");
            if (!settingsSet || !backlightSet) {
                restoreBrightness(context, prefs);
                return false;
            }
        }

        boolean modeSet = FileUtils.writeLine(node, enabled ? "0x10000" : "0xF0000");
        if (!enabled || !modeSet) restoreBrightness(context, prefs);
        return modeSet;
    }

    private static synchronized void restoreBrightness(Context context, SharedPreferences prefs) {
        int brightness = prefs.getInt(PREF_SAVED_BRIGHTNESS, -1);
        if (brightness < 0) return;
        boolean settingsRestored = Settings.System.putInt(context.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, brightness);
        boolean backlightRestored = FileUtils.writeLine(BACKLIGHT,
                String.valueOf(brightness * 2047 / 255));
        if (settingsRestored && backlightRestored) {
            prefs.edit().remove(PREF_SAVED_BRIGHTNESS).apply();
        }
    }

    private static String getHbmNode() {
        for (String node : HBM_NODES) {
            if (FileUtils.isFileWritable(node)) return node;
        }
        return null;
    }

    private static void notifyModeChanged(Context context) {
        Intent intent = new Intent(ACTION_MODE_CHANGED).setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }
}
