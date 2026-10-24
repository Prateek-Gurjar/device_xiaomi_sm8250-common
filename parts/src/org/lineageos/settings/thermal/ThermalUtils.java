/*
 * Copyright (C) 2020 The LineageOS Project
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

package org.lineageos.settings.thermal;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.utils.FileUtils;

import java.util.NoSuchElementException;

import vendor.xiaomi.hardware.touchfeature.V1_0.ITouchFeature;

public final class ThermalUtils {

    private static final String TAG = "ThermalUtils";

    protected static final int STATE_DEFAULT = 0;
    protected static final int STATE_BENCHMARK = 1;
    protected static final int STATE_BROWSER = 2;
    protected static final int STATE_CAMERA = 3;
    protected static final int STATE_DIALER = 4;
    protected static final int STATE_GAMING_MEDIUM = 5;
    protected static final int STATE_NAVIGATION = 6;
    protected static final int STATE_STREAMING = 7;
    protected static final int STATE_VIDEO = 8;
    protected static final int STATE_GAMING_HEAVY = 9;
    protected static final int STATE_GAMING_LIGHT = 10;
    protected static final int STATE_CAMERA_4K = 11;
    protected static final int STATE_YUANSHEN = 12;
    private static final int STATE_COUNT = STATE_YUANSHEN + 1;
    private static final String THERMAL_CONTROL = "thermal_control";
    public static final String ACTION_REFRESH_PROFILE =
            "org.lineageos.settings.action.REFRESH_THERMAL_PROFILE";

    // IDs verified against munch's encrypted thermal-map.conf. Munch has no
    // dedicated Genshin profile, so Genshin uses the supported heavy-game map.
    private static final String[] THERMAL_STATES = {
            "0", "10", "11", "12", "8", "9", "19", "21", "4", "20", "3", "17", "20"
    };

    // Preference slots follow spinner state order, excluding Default.
    private static final String[] THERMAL_PREFIXES = {
            "thermal.benchmark=", "thermal.browser=", "thermal.camera=", "thermal.dialer=",
            "thermal.gaming=", "thermal.navigation=", "thermal.streaming=", "thermal.video=",
            "thermal.gaming_heavy=", "thermal.gaming_light=", "thermal.camera_4k=",
            "thermal.yuanshen="
    };

    private static final String THERMAL_SCONFIG = "/sys/class/thermal/thermal_message/sconfig";

    private boolean mTouchModeChanged;

    private Display mDisplay;
    private ITouchFeature mTouchFeature = null;
    private SharedPreferences mSharedPrefs;

    protected ThermalUtils(Context context) {
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        WindowManager mWindowManager = context.getSystemService(WindowManager.class);
        mDisplay = mWindowManager.getDefaultDisplay();

        try {
            mTouchFeature = ITouchFeature.getService();
        } catch (RemoteException e) {
            // Do nothing
        } catch (NoSuchElementException e) {
            // Do nothing
        }

    }

    public static void startService(Context context) {
        if (FileUtils.fileExists(THERMAL_SCONFIG)) {
            context.startServiceAsUser(new Intent(context, ThermalService.class),
                    UserHandle.CURRENT);
        }
    }

    private void writeValue(String profiles) {
        mSharedPrefs.edit().putString(THERMAL_CONTROL, profiles).apply();
    }

    private String getValue() {
        String value = mSharedPrefs.getString(THERMAL_CONTROL, null);

        if (value != null) {
            String[] modes = value.split(":", -1);
            if (modes.length == THERMAL_PREFIXES.length
                    && hasExpectedPrefixes(modes, THERMAL_PREFIXES.length)) {
                return value;
            } else if (modes.length == 8 && hasCurrentPrefixes(modes)) {
                value = migrateCurrentProfiles(modes);
                writeValue(value);
            } else if (modes.length == 6 && hasLegacyPrefixes(modes)) {
                String[] migrated = { modes[0], modes[1], modes[2], modes[3], modes[4],
                        THERMAL_PREFIXES[5], modes[5], THERMAL_PREFIXES[7] };
                value = appendEmptyProfiles(migrated);
                writeValue(value);
            } else {
                value = null;
            }
        }

        if (value == null || value.isEmpty()) {
            value = joinPrefixes();
            writeValue(value);
        }
        return value;
    }

    private boolean hasExpectedPrefixes(String[] modes, int count) {
        if (modes.length != count) return false;
        for (int i = 0; i < count; i++) {
            if (!modes[i].startsWith(THERMAL_PREFIXES[i])) return false;
        }
        return true;
    }

    private boolean hasCurrentPrefixes(String[] modes) {
        for (int i = 0; i < modes.length; i++) {
            if (!modes[i].startsWith(THERMAL_PREFIXES[i])) return false;
        }
        return true;
    }

    private boolean hasLegacyPrefixes(String[] modes) {
        return modes.length == 6 && modes[0].startsWith(THERMAL_PREFIXES[0])
                && modes[1].startsWith(THERMAL_PREFIXES[1])
                && modes[2].startsWith(THERMAL_PREFIXES[2])
                && modes[3].startsWith(THERMAL_PREFIXES[3])
                && modes[4].startsWith(THERMAL_PREFIXES[4])
                && modes[5].startsWith("thermal.streaming=");
    }

    private String appendEmptyProfiles(String[] existingModes) {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < THERMAL_PREFIXES.length; i++) {
            if (i > 0) value.append(':');
            value.append(i < existingModes.length ? existingModes[i] : THERMAL_PREFIXES[i]);
        }
        return value.toString();
    }

    private String migrateCurrentProfiles(String[] existingModes) {
        // The old Video profile used Munch's playback profile (sconfig 21).
        // Keep those app assignments on Streaming; Video now means video chat.
        String[] migrated = existingModes.clone();
        migrated[6] = migrated[6] + migrated[7].substring(THERMAL_PREFIXES[7].length());
        migrated[7] = THERMAL_PREFIXES[7];
        return appendEmptyProfiles(migrated);
    }

    private String joinPrefixes() {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < THERMAL_PREFIXES.length; i++) {
            if (i > 0) value.append(':');
            value.append(THERMAL_PREFIXES[i]);
        }
        return value.toString();
    }

    public static void refreshService(Context context) {
        Intent intent = new Intent(ACTION_REFRESH_PROFILE).setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    protected void writePackage(String packageName, int mode) {
        String value = getValue();
        value = value.replace(packageName + ",", "");
        String[] modes = value.split(":", -1);
        if (mode > STATE_DEFAULT && mode < STATE_COUNT) {
            modes[mode - 1] = modes[mode - 1] + packageName + ",";
        }
        writeValue(android.text.TextUtils.join(":", modes));
    }

    protected int getStateForPackage(String packageName) {
        String[] modes = getValue().split(":", -1);
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].contains(packageName + ",")) {
                return i + 1;
            }
        }
        return STATE_DEFAULT;
    }

    protected void setDefaultThermalProfile() {
        writeThermalState(THERMAL_STATES[STATE_DEFAULT]);
    }

    protected void setThermalProfile(String packageName) {
        int selectedState = getStateForPackage(packageName);
        writeThermalState(THERMAL_STATES[selectedState]);

        if (selectedState == STATE_BENCHMARK || isGameProfile(selectedState)) {
            updateTouchModes(packageName);
        } else if (mTouchModeChanged) {
            resetTouchModes();
        }
    }

    protected static boolean isGameProfile(int state) {
        return state == STATE_GAMING_MEDIUM || state == STATE_GAMING_HEAVY
                || state == STATE_GAMING_LIGHT || state == STATE_YUANSHEN;
    }

    private void writeThermalState(String state) {
        if (!FileUtils.writeLine(THERMAL_SCONFIG, state)) {
            Log.e(TAG, "Unable to apply thermal profile " + state + " through "
                    + THERMAL_SCONFIG);
        }
    }

    private void updateTouchModes(String packageName) {
        String values = mSharedPrefs.getString(packageName, null);
        resetTouchModes();

        if (values == null || values.isEmpty()) {
            return;
        }

        if (mTouchFeature == null) {
            Log.w(TAG, "Touch feature HAL is unavailable; skipping per-app touch settings");
            return;
        }

        String[] value = values.split(",", -1);
        if (value.length != 4) {
            Log.w(TAG, "Ignoring malformed per-app touch settings for " + packageName);
            return;
        }
        final int gameMode;
        final int touchResponse;
        final int touchSensitivity;
        final int touchResistant;
        try {
            gameMode = Integer.parseInt(value[Constants.TOUCH_GAME_MODE]);
            touchResponse = Integer.parseInt(value[Constants.TOUCH_RESPONSE]);
            touchSensitivity = Integer.parseInt(value[Constants.TOUCH_SENSITIVITY]);
            touchResistant = Integer.parseInt(value[Constants.TOUCH_RESISTANT]);
        } catch (NumberFormatException e) {
            Log.w(TAG, "Ignoring invalid per-app touch settings for " + packageName, e);
            return;
        }
        if (!isInRange(gameMode, 0, 1) || !isInRange(touchResponse, 0, 3)
                || !isInRange(touchSensitivity, 0, 3) || !isInRange(touchResistant, 0, 3)) {
            Log.w(TAG, "Ignoring out-of-range per-app touch settings for " + packageName);
            return;
        }
        int touchActiveMode = (gameMode != 0 || touchResponse != 0
                || touchSensitivity != 0 || touchResistant != 0) ? 1 : 0;
        try {
            mTouchFeature.setTouchMode(Constants.MODE_TOUCH_TOLERANCE, touchSensitivity);
            mTouchFeature.setTouchMode(Constants.MODE_TOUCH_UP_THRESHOLD, touchResponse);
            mTouchFeature.setTouchMode(Constants.MODE_TOUCH_EDGE_FILTER, touchResistant);
            mTouchFeature.setTouchMode(Constants.MODE_TOUCH_GAME_MODE, gameMode);
            mTouchFeature.setTouchMode(Constants.MODE_TOUCH_ACTIVE_MODE, touchActiveMode);
        } catch (RemoteException e) {
            // Do nothing
        }

        mTouchModeChanged = true;
        updateTouchRotation();
    }

    private boolean isInRange(int value, int min, int max) {
        return value >= min && value <= max;
    }

    protected void resetTouchModes() {
        if (!mTouchModeChanged) {
            return;
        }

        if (mTouchFeature == null) {
            mTouchModeChanged = false;
            return;
        }

        try {
            mTouchFeature.resetTouchMode(Constants.MODE_TOUCH_GAME_MODE);
            mTouchFeature.resetTouchMode(Constants.MODE_TOUCH_ACTIVE_MODE);
            mTouchFeature.resetTouchMode(Constants.MODE_TOUCH_UP_THRESHOLD);
            mTouchFeature.resetTouchMode(Constants.MODE_TOUCH_TOLERANCE);
            mTouchFeature.resetTouchMode(Constants.MODE_TOUCH_EDGE_FILTER);
            mTouchFeature.resetTouchMode(Constants.MODE_TOUCH_ROTATION);
        } catch (RemoteException e) {
            // Do nothing
        }

        mTouchModeChanged = false;
    }

    protected void updateTouchRotation() {
        if (!mTouchModeChanged) {
            return;
        }

        int touchRotation = 0;
        switch (mDisplay.getRotation()) {
            case Surface.ROTATION_0:
                touchRotation = 0;
                break;
            case Surface.ROTATION_90:
                touchRotation = 1;
                break;
            case Surface.ROTATION_180:
                touchRotation = 2;
                break;
            case Surface.ROTATION_270:
                touchRotation = 3;
                break;
        }

        try {
            mTouchFeature.setTouchMode(Constants.MODE_TOUCH_ROTATION, touchRotation);
        } catch (RemoteException e) {
            // Do nothing
        }
    }
}
