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
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.utils.FileUtils;

import java.io.File;

public final class TouchSamplingUtils {

    private static final String TAG = "TouchSamplingUtils";
    public static final String HTSR_FILE = "/sys/devices/virtual/touch/touch_dev/bump_sample_rate";
    public static final String STATE_PREFS = "SHAREDHTSR";
    private static final String STATE_KEY = "SHAREDHTSR";
    private static final String LEGACY_ENABLE_KEY = "htsr_enable";

    private TouchSamplingUtils() { }

    public static boolean isSupported() {
        File node = new File(HTSR_FILE);
        return node.exists() && node.canRead() && node.canWrite();
    }

    public static boolean isEnabled() {
        return "1".equals(FileUtils.readOneLine(HTSR_FILE));
    }

    public static boolean setEnabled(Context context, boolean enabled) {
        if (!isSupported() || !FileUtils.writeLine(HTSR_FILE, enabled ? "1" : "0")) {
            return false;
        }
        saveEnabled(context, enabled);
        return true;
    }

    private static void saveEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).edit()
                .putInt(STATE_KEY, enabled ? 1 : 0).apply();
    }

    public static void restoreSamplingValue(Context context) {
        if (!isSupported()) {
            Log.i(TAG, "Touch sampling node is unavailable or not writable");
            return;
        }

        SharedPreferences statePrefs = context.getSharedPreferences(
                STATE_PREFS, Context.MODE_PRIVATE);
        boolean enabled;
        if (statePrefs.contains(STATE_KEY)) {
            enabled = statePrefs.getInt(STATE_KEY, 0) != 0;
        } else {
            // Migrate the value saved by earlier builds that only used the default prefs.
            SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context);
            enabled = defaultPrefs.getBoolean(LEGACY_ENABLE_KEY, false);
            saveEnabled(context, enabled);
        }

        if (!FileUtils.writeLine(HTSR_FILE, enabled ? "1" : "0")) {
            Log.w(TAG, "Unable to restore touch sampling state");
        }
    }
}
