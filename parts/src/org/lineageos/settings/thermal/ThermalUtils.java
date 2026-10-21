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
    protected static final int STATE_GAMING = 5;
    protected static final int STATE_STREAMING = 6;
    protected static final int STATE_NAVIGATION = 7;
    protected static final int STATE_VIDEO = 8;
    private static final String THERMAL_CONTROL = "thermal_control";
    public static final String ACTION_REFRESH_PROFILE =
            "org.lineageos.settings.action.REFRESH_THERMAL_PROFILE";

    private static final String THERMAL_STATE_DEFAULT = "0";
    private static final String THERMAL_STATE_BENCHMARK = "10";
    private static final String THERMAL_STATE_BROWSER = "11";
    private static final String THERMAL_STATE_CAMERA = "12";
    private static final String THERMAL_STATE_DIALER = "8";
    private static final String THERMAL_STATE_GAMING = "9";
    private static final String THERMAL_STATE_NAVIGATION = "19";
    private static final String THERMAL_STATE_STREAMING = "14";
    private static final String THERMAL_STATE_VIDEO = "21";

    private static final String THERMAL_BENCHMARK = "thermal.benchmark=";
    private static final String THERMAL_BROWSER = "thermal.browser=";
    private static final String THERMAL_CAMERA = "thermal.camera=";
    private static final String THERMAL_DIALER = "thermal.dialer=";
    private static final String THERMAL_GAMING = "thermal.gaming=";
    private static final String THERMAL_NAVIGATION = "thermal.navigation=";
    private static final String THERMAL_STREAMING = "thermal.streaming=";
    private static final String THERMAL_VIDEO = "thermal.video=";

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
            // Older builds stored benchmark, browser, camera, dialer, gaming,
            // streaming. Keep those assignments when adding navigation and video.
            if (modes.length == 6 && hasLegacyPrefixes(modes)) {
                value = modes[0] + ":" + modes[1] + ":" + modes[2] + ":" + modes[3]
                        + ":" + modes[4] + ":" + THERMAL_NAVIGATION + ":" + modes[5]
                        + ":" + THERMAL_VIDEO;
                writeValue(value);
            } else if (modes.length != 8 || !hasExpectedPrefixes(modes, 8)) {
                value = null;
            }
        }

        if (value == null || value.isEmpty()) {
            value = THERMAL_BENCHMARK + ":" + THERMAL_BROWSER + ":" + THERMAL_CAMERA + ":" + THERMAL_DIALER + ":" +
                    THERMAL_GAMING + ":" + THERMAL_NAVIGATION + ":" + THERMAL_STREAMING + ":" + THERMAL_VIDEO;
            writeValue(value);
        }
        return value;
    }

    private boolean hasExpectedPrefixes(String[] modes, int count) {
        String[] prefixes = { THERMAL_BENCHMARK, THERMAL_BROWSER, THERMAL_CAMERA,
                THERMAL_DIALER, THERMAL_GAMING, THERMAL_NAVIGATION,
                THERMAL_STREAMING, THERMAL_VIDEO };
        if (modes.length != count) return false;
        for (int i = 0; i < count; i++) {
            if (!modes[i].startsWith(prefixes[i])) return false;
        }
        return true;
    }

    private boolean hasLegacyPrefixes(String[] modes) {
        return modes.length == 6 && modes[0].startsWith(THERMAL_BENCHMARK)
                && modes[1].startsWith(THERMAL_BROWSER)
                && modes[2].startsWith(THERMAL_CAMERA)
                && modes[3].startsWith(THERMAL_DIALER)
                && modes[4].startsWith(THERMAL_GAMING)
                && modes[5].startsWith(THERMAL_STREAMING);
    }

    public static void refreshService(Context context) {
        Intent intent = new Intent(ACTION_REFRESH_PROFILE).setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    protected void writePackage(String packageName, int mode) {
        String value = getValue();
        value = value.replace(packageName + ",", "");
        String[] modes = value.split(":", -1);
        String finalString;

        switch (mode) {
            case STATE_BENCHMARK:
                modes[0] = modes[0] + packageName + ",";
                break;
            case STATE_BROWSER:
                modes[1] = modes[1] + packageName + ",";
                break;
            case STATE_CAMERA:
                modes[2] = modes[2] + packageName + ",";
                break;
            case STATE_DIALER:
                modes[3] = modes[3] + packageName + ",";
                break;
            case STATE_GAMING:
                modes[4] = modes[4] + packageName + ",";
                break;
            case STATE_NAVIGATION:
                modes[5] = modes[5] + packageName + ",";
                break;
            case STATE_STREAMING:
                modes[6] = modes[6] + packageName + ",";
                break;
            case STATE_VIDEO:
                modes[7] = modes[7] + packageName + ",";
                break;
        }

        finalString = modes[0] + ":" + modes[1] + ":" + modes[2] + ":" + modes[3] + ":" +
                modes[4] + ":" + modes[5] + ":" + modes[6] + ":" + modes[7];

        writeValue(finalString);
    }

    protected int getStateForPackage(String packageName) {
        String value = getValue();
        String[] modes = value.split(":", -1);
        int state = STATE_DEFAULT;
        if (modes[0].contains(packageName + ",")) {
            state = STATE_BENCHMARK;
        } else if (modes[1].contains(packageName + ",")) {
            state = STATE_BROWSER;
        } else if (modes[2].contains(packageName + ",")) {
            state = STATE_CAMERA;
        } else if (modes[3].contains(packageName + ",")) {
            state = STATE_DIALER;
        } else if (modes[4].contains(packageName + ",")) {
            state = STATE_GAMING;
        } else if (modes[5].contains(packageName + ",")) {
            state = STATE_NAVIGATION;
        } else if (modes[6].contains(packageName + ",")) {
            state = STATE_STREAMING;
        } else if (modes[7].contains(packageName + ",")) {
            state = STATE_VIDEO;
        }

        return state;
    }

    protected void setDefaultThermalProfile() {
        writeThermalState(THERMAL_STATE_DEFAULT);
    }

    protected void setThermalProfile(String packageName) {
        String value = getValue();
        String state = THERMAL_STATE_DEFAULT;

        if (value != null) {
            String[] modes = value.split(":", -1);

            if (modes[0].contains(packageName + ",")) {
                state = THERMAL_STATE_BENCHMARK;
            } else if (modes[1].contains(packageName + ",")) {
                state = THERMAL_STATE_BROWSER;
            } else if (modes[2].contains(packageName + ",")) {
                state = THERMAL_STATE_CAMERA;
            } else if (modes[3].contains(packageName + ",")) {
                state = THERMAL_STATE_DIALER;
            } else if (modes[4].contains(packageName + ",")) {
                state = THERMAL_STATE_GAMING;
            } else if (modes[5].contains(packageName + ",")) {
                state = THERMAL_STATE_NAVIGATION;
            } else if (modes[6].contains(packageName + ",")) {
                state = THERMAL_STATE_STREAMING;
            } else if (modes[7].contains(packageName + ",")) {
                state = THERMAL_STATE_VIDEO;
            }
        }
        writeThermalState(state);

        if (THERMAL_STATE_BENCHMARK.equals(state) || THERMAL_STATE_GAMING.equals(state)) {
            updateTouchModes(packageName);
        } else if (mTouchModeChanged) {
            resetTouchModes();
        }
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
