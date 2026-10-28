/*
 * Copyright (C) 2015 The CyanogenMod Project
 *               2017-2019 The LineageOS Project
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

package org.lineageos.settings.touchsampling;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import org.lineageos.settings.utils.FileUtils;

public final class TouchSamplingUtils {

    public static final String HTSR_FILE = "/sys/devices/virtual/touch/touch_dev/bump_sample_rate";
    public static final String HTSR_ENABLE_KEY = "htsr_enable";
    private static final String LEGACY_PREFERENCES = "SHAREDHTSR";
    private static final String LEGACY_ENABLE_KEY = "SHAREDHTSR";

    private TouchSamplingUtils() {
    }

    public static boolean isSupported() {
        return FileUtils.isFileWritable(HTSR_FILE);
    }

    public static boolean isEnabled(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (preferences.contains(HTSR_ENABLE_KEY)) {
            return preferences.getBoolean(HTSR_ENABLE_KEY, false);
        }

        // Migrate the setting saved by older builds and keep the user's choice.
        boolean enabled = context.getSharedPreferences(LEGACY_PREFERENCES, Context.MODE_PRIVATE)
                .getInt(LEGACY_ENABLE_KEY, 0) == 1;
        preferences.edit().putBoolean(HTSR_ENABLE_KEY, enabled).apply();
        return enabled;
    }

    public static boolean setEnabled(Context context, boolean enabled) {
        if (!isSupported() || !FileUtils.writeLine(HTSR_FILE, enabled ? "1" : "0")) {
            return false;
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(HTSR_ENABLE_KEY, enabled).apply();
        return true;
    }

    public static void restoreSamplingValue(Context context) {
        if (isSupported()) {
            setEnabled(context, isEnabled(context));
        }
    }
}
