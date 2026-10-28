/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.display;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.utils.FileUtils;

public final class DcDimmingUtils {
    public static final String NODE = "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/dimlayer_exposure";
    private static final String HBM_NODE = "/sys/class/drm/card0/card0-DSI-1/disp_param";
    private static final String BACKLIGHT_NODE = "/sys/class/backlight/panel0-backlight/brightness";
    private static final String HBM_KEY = "hbm";
    private static final String SAVED_BRIGHTNESS = "hbm_saved_brightness";

    private DcDimmingUtils() {}

    public static boolean isSupported() {
        return FileUtils.fileExists(NODE);
    }

    public static boolean setEnabled(Context context, boolean enabled) {
        if (!FileUtils.fileExists(NODE) || !FileUtils.writeLine(NODE, enabled ? "1" : "0")) {
            return false;
        }

        if (enabled) disableHBM(context);
        return true;
    }

    private static void disableHBM(Context context) {
        FileUtils.writeLine(HBM_NODE, "0xF0000");
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int savedBrightness = prefs.getInt(SAVED_BRIGHTNESS, -1);
        if (savedBrightness >= 0) {
            Settings.System.putInt(context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, savedBrightness);
            FileUtils.writeLine(BACKLIGHT_NODE, String.valueOf(savedBrightness * 2047 / 255));
            prefs.edit().remove(SAVED_BRIGHTNESS).apply();
        }
        prefs.edit().putBoolean(HBM_KEY, false).apply();
    }
}
