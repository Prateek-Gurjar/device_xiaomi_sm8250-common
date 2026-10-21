package org.lineageos.settings.hbm;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.display.DcDimmingTileService;
import org.lineageos.settings.utils.FileUtils;

/** Serializes HBM ownership and brightness transitions for the settings switch, tile and sensor. */
public final class HBMController {
    private static final String HBM = "/sys/class/drm/card0/card0-DSI-1/disp_param";
    private static final String BACKLIGHT = "/sys/class/backlight/panel0-backlight/brightness";
    private static final String HBM_KEY = "hbm";
    private static final String AUTO_ACTIVE_KEY = "auto_hbm_active";
    private static final String SAVED_BRIGHTNESS = "hbm_saved_brightness";
    private static final String MAX_BRIGHTNESS = "/sys/class/backlight/panel0-backlight/max_brightness";
    private static final int SETTINGS_MAX_BRIGHTNESS = 255;

    private HBMController() { }

    public static synchronized boolean isEnabled(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(HBM_KEY, false) || prefs.getBoolean(AUTO_ACTIVE_KEY, false);
    }

    public static synchronized boolean isAutoEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(AUTO_ACTIVE_KEY, false);
    }

    public static synchronized boolean setManual(Context context, boolean enabled) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (enabled && isDcDimmingEnabled(prefs)) return false;
        return setOwner(context, HBM_KEY, enabled);
    }

    public static synchronized boolean setAuto(Context context, boolean enabled) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (enabled && isDcDimmingEnabled(prefs)) return false;
        return setOwner(context, AUTO_ACTIVE_KEY, enabled);
    }

    /** Turns off all HBM owners, used when the display sleeps or DC dimming takes over. */
    public static synchronized boolean disableAll(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean wasEnabled = isEnabled(context);
        boolean wasManual = prefs.getBoolean(HBM_KEY, false);
        boolean wasAuto = prefs.getBoolean(AUTO_ACTIVE_KEY, false);
        prefs.edit().putBoolean(HBM_KEY, false).putBoolean(AUTO_ACTIVE_KEY, false).apply();
        if (wasEnabled && !writeHBM(false)) {
            prefs.edit().putBoolean(HBM_KEY, wasManual)
                    .putBoolean(AUTO_ACTIVE_KEY, wasAuto).apply();
            return false;
        }
        if (wasEnabled) restoreBrightness(context, prefs);
        return true;
    }

    private static boolean setOwner(Context context, String key, boolean enabled) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean wasEnabled = isEnabled(context);
        boolean previousOwner = prefs.getBoolean(key, false);
        if (previousOwner == enabled) return true;
        prefs.edit().putBoolean(key, enabled).apply();
        boolean nowEnabled = isEnabled(context);
        if (wasEnabled == nowEnabled) return true;
        if (!nowEnabled) {
            if (!writeHBM(false)) {
                prefs.edit().putBoolean(key, previousOwner).apply();
                return false;
            }
            restoreBrightness(context, prefs);
            return true;
        }

        int current = Settings.System.getInt(context.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, SETTINGS_MAX_BRIGHTNESS);
        prefs.edit().putInt(SAVED_BRIGHTNESS, current).apply();
        int panelMax = getPanelMaxBrightness();
        if (panelMax <= 0 || !FileUtils.writeLine(BACKLIGHT, Integer.toString(panelMax))) {
            prefs.edit().putBoolean(key, previousOwner).remove(SAVED_BRIGHTNESS).apply();
            return false;
        }
        Settings.System.putInt(context.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, SETTINGS_MAX_BRIGHTNESS);
        if (!writeHBM(true)) {
            prefs.edit().putBoolean(key, previousOwner).apply();
            restoreBrightness(context, prefs);
            return false;
        }
        return true;
    }

    private static boolean writeHBM(boolean enabled) {
        return FileUtils.writeLine(HBM, enabled ? "0x10000" : "0xF0000");
    }

    private static void restoreBrightness(Context context, SharedPreferences prefs) {
        int saved = prefs.getInt(SAVED_BRIGHTNESS, -1);
        if (saved < 0) return;
        saved = Math.max(0, Math.min(SETTINGS_MAX_BRIGHTNESS, saved));
        Settings.System.putInt(context.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, saved);
        int panelMax = getPanelMaxBrightness();
        if (panelMax > 0) {
            FileUtils.writeLine(BACKLIGHT,
                    Integer.toString(saved * panelMax / SETTINGS_MAX_BRIGHTNESS));
        }
        prefs.edit().remove(SAVED_BRIGHTNESS).apply();
    }

    private static int getPanelMaxBrightness() {
        try {
            return Integer.parseInt(FileUtils.readOneLine(MAX_BRIGHTNESS));
        } catch (NumberFormatException | NullPointerException e) {
            return -1;
        }
    }

    private static boolean isDcDimmingEnabled(SharedPreferences prefs) {
        return prefs.getBoolean(DcDimmingTileService.DC_DIMMING_ENABLE_KEY, false);
    }
}
