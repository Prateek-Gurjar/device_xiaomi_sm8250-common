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
import android.text.TextUtils;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.utils.FileUtils;

import java.util.NoSuchElementException;

import vendor.xiaomi.hardware.touchfeature.V1_0.ITouchFeature;

public final class ThermalUtils {

    protected static final int STATE_DEFAULT = 0;
    protected static final int STATE_BENCHMARK = 1;
    protected static final int STATE_BROWSER = 2;
    protected static final int STATE_CAMERA = 3;
    protected static final int STATE_DIALER = 4;
    protected static final int STATE_GAMING = 5;
    protected static final int STATE_STREAMING = 6;
    protected static final int STATE_NAVIGATION = 7;
    protected static final int STATE_VIDEO = 8;
    protected static final int STATE_VIDEOCHAT = 9;
    protected static final int STATE_HIGH = 10;
    protected static final int STATE_EXTREME = 11;
    protected static final int STATE_HUANJI = 12;
    protected static final int STATE_ARVR = 13;
    protected static final int STATE_4K = 14;
    protected static final int STATE_8K = 15;
    protected static final int STATE_MGAME = 16;
    private static final String THERMAL_CONTROL = "thermal_control";
    private static final String THERMAL_ENABLED = "thermal_enabled";
    private static final Object sProfileLock = new Object();
    private static final String[] THERMAL_MODE_KEYS = {
            "thermal.benchmark=", "thermal.browser=", "thermal.camera=", "thermal.dialer=",
            "thermal.gaming=", "thermal.streaming=", "thermal.navigation=", "thermal.video=",
            "thermal.videochat=", "thermal.high=", "thermal.extreme=", "thermal.huanji=",
            "thermal.arvr=", "thermal.4k=", "thermal.8k=", "thermal.mgame="
    };
    private static final String[] THERMAL_STATE_VALUES = {
            "0", "10", "11", "12", "8", "9", "14", "19", "21", "4", "1", "2",
            "3", "15", "17", "18", "20"
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
        if (isServiceEnabled(context) && FileUtils.fileExists(THERMAL_SCONFIG)) {
            context.startServiceAsUser(new Intent(context, ThermalService.class),
                    UserHandle.CURRENT);
        }
    }

    public static boolean isServiceEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(THERMAL_ENABLED, true);
    }

    public static void setServiceEnabled(Context context, boolean enabled) {
        synchronized (sProfileLock) {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putBoolean(THERMAL_ENABLED, enabled)
                    .apply();
            if (!enabled) {
                ThermalUtils thermalUtils = new ThermalUtils(context);
                thermalUtils.setDefaultThermalProfile();
                thermalUtils.resetTouchModes(true);
            }
        }

        Intent serviceIntent = new Intent(context, ThermalService.class);
        if (enabled) {
            startService(context);
        } else {
            context.stopService(serviceIntent);
        }
    }

    private void writeValue(String profiles) {
        mSharedPrefs.edit().putString(THERMAL_CONTROL, profiles).apply();
    }

    private String getValue() {
        String value = mSharedPrefs.getString(THERMAL_CONTROL, null);

        if (value == null || value.isEmpty()) {
            value = getDefaultValue();
            writeValue(value);
        } else {
            String[] modes = value.split(":");
            if (modes.length == 6 || modes.length == 8 || modes.length == 9) {
                // Preserve saved assignments and append newly supported Munch modes.
                for (int i = modes.length; i < THERMAL_MODE_KEYS.length; i++) {
                    value += ":" + THERMAL_MODE_KEYS[i];
                }
                writeValue(value);
            } else if (modes.length != THERMAL_MODE_KEYS.length) {
                value = getDefaultValue();
                writeValue(value);
            }
        }
        return value;
    }

    private static String getDefaultValue() {
        StringBuilder value = new StringBuilder();
        for (String mode : THERMAL_MODE_KEYS) {
            if (value.length() > 0) value.append(':');
            value.append(mode);
        }
        return value.toString();
    }

    protected void writePackage(String packageName, int mode) {
        String value = getValue();
        value = value.replace(packageName + ",", "");
        String[] modes = value.split(":");
        if (mode > STATE_DEFAULT && mode <= THERMAL_MODE_KEYS.length) {
            modes[mode - 1] += packageName + ",";
        }
        writeValue(TextUtils.join(":", modes));
    }

    protected int getStateForPackage(String packageName) {
        String value = getValue();
        String[] modes = value.split(":");
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].contains(packageName + ",")) {
                return i + 1;
            }
        }
        return STATE_DEFAULT;
    }

    protected void setDefaultThermalProfile() {
        FileUtils.writeLine(THERMAL_SCONFIG, THERMAL_STATE_VALUES[STATE_DEFAULT]);
    }

    protected void setThermalProfile(String packageName) {
        synchronized (sProfileLock) {
            // A queued task-stack callback must not restore a profile after Off.
            if (!mSharedPrefs.getBoolean(THERMAL_ENABLED, true)) {
                setDefaultThermalProfile();
                resetTouchModes();
                return;
            }
            applyThermalProfile(packageName);
        }
    }

    private void applyThermalProfile(String packageName) {
        int mode = getStateForPackage(packageName);
        FileUtils.writeLine(THERMAL_SCONFIG, THERMAL_STATE_VALUES[mode]);

        if (mode == STATE_BENCHMARK || mode == STATE_GAMING || mode == STATE_MGAME) {
            updateTouchModes(packageName);
        } else if (mTouchModeChanged) {
            resetTouchModes();
        }
    }

    private void updateTouchModes(String packageName) {
        if (mTouchFeature == null) {
            return;
        }
        String values = mSharedPrefs.getString(packageName, null);
        resetTouchModes();

        if (values == null || values.isEmpty()) {
            return;
        }

        String[] value = values.split(",");
        int gameMode = Integer.parseInt(value[Constants.TOUCH_GAME_MODE]);
        int touchResponse = Integer.parseInt(value[Constants.TOUCH_RESPONSE]);
        int touchSensitivity = Integer.parseInt(value[Constants.TOUCH_SENSITIVITY]);
        int touchResistant = Integer.parseInt(value[Constants.TOUCH_RESISTANT]);
        int touchActiveMode = (touchResponse != 0 && touchSensitivity != 0 && touchResistant != 0)
                ? 1 : 0;
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

    protected void resetTouchModes() {
        resetTouchModes(false);
    }

    private void resetTouchModes(boolean force) {
        if (mTouchFeature == null || (!force && !mTouchModeChanged)) {
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
        if (mTouchFeature == null || !mTouchModeChanged) {
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
