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

package org.lineageos.settings.refreshrate;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.OrientationEventListener;

import androidx.preference.PreferenceManager;

import java.util.HashSet;
import java.util.Set;

public final class RefreshUtils {

    private static final String TAG = "RefreshUtils";
    private static final String REFRESH_CONTROL = "refresh_control";
    private static final String KEY_PEAK_REFRESH_RATE = "peak_refresh_rate";
    private static final String KEY_MIN_REFRESH_RATE = "min_refresh_rate";
    private static final float DEFAULT_REFRESH_RATE = 120f;
    private static final float STANDARD_REFRESH_RATE = 60f;
    private static final float EXTREME_REFRESH_RATE = 120f;
    private static final float LANDSCAPE_REFRESH_RATE = 60f;

    protected static final int STATE_DEFAULT = 0;
    protected static final int STATE_STANDARD = 1;
    protected static final int STATE_EXTREME = 2;
    protected static final int STATE_LAND = 3;

    private static final String REFRESH_STANDARD = "refresh.standard=";
    private static final String REFRESH_EXTREME = "refresh.extreme=";
    private static final String REFRESH_LAND = "refresh.land=";

    private final Context mContext;
    private final SharedPreferences mSharedPrefs;
    private OrientationEventListener mOrientationListener;
    private boolean mIsLandscape;

    protected RefreshUtils(Context context) {
        mContext = context;
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static void startService(Context context) {
        context.startServiceAsUser(new Intent(context, RefreshService.class), UserHandle.CURRENT);
    }

    protected float getCurrentMaxRate() {
        return Settings.System.getFloat(mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE,
                DEFAULT_REFRESH_RATE);
    }

    protected float getCurrentMinRate() {
        return Settings.System.getFloat(mContext.getContentResolver(), KEY_MIN_REFRESH_RATE,
                DEFAULT_REFRESH_RATE);
    }

    protected void setRefreshRate(float minRate, float maxRate) {
        if (minRate > maxRate) {
            minRate = maxRate;
        }
        Settings.System.putFloat(mContext.getContentResolver(), KEY_MIN_REFRESH_RATE, minRate);
        Settings.System.putFloat(mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE, maxRate);
    }

    protected void applyProfile(String packageName, int state, float defaultMinRate,
            float defaultMaxRate) {
        disableOrientationListener();
        if (state == STATE_DEFAULT) {
            setRefreshRate(defaultMinRate, defaultMaxRate);
            return;
        }

        if (state == STATE_STANDARD) {
            setRefreshRate(defaultMinRate, getSupportedRateAtOrBelow(STANDARD_REFRESH_RATE));
        } else if (state == STATE_EXTREME) {
            setRefreshRate(defaultMinRate, getSupportedRateAtOrBelow(EXTREME_REFRESH_RATE));
        } else if (state == STATE_LAND) {
            mIsLandscape = mContext.getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE;
            applyLandscapeProfile(defaultMinRate);
            initializeOrientationListener(defaultMinRate);
        } else {
            setRefreshRate(defaultMinRate,
                    Math.min(defaultMaxRate, getSupportedRateAtOrBelow(defaultMaxRate)));
        }
    }

    private void applyLandscapeProfile(float defaultMinRate) {
        float requestedRate = mIsLandscape ? LANDSCAPE_REFRESH_RATE : EXTREME_REFRESH_RATE;
        float maxRate = getSupportedRateAtOrBelow(requestedRate);
        setRefreshRate(defaultMinRate, maxRate);
    }

    private float getSupportedRateAtOrBelow(float requestedRate) {
        DisplayManager displayManager = mContext.getSystemService(DisplayManager.class);
        Display display = displayManager == null
                ? null : displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null || display.getMode() == null) return requestedRate;

        float selectedRate = -1f;
        float lowestRate = Float.MAX_VALUE;
        Display.Mode currentMode = display.getMode();
        for (Display.Mode mode : display.getSupportedModes()) {
            if (mode.getPhysicalWidth() != currentMode.getPhysicalWidth()
                    || mode.getPhysicalHeight() != currentMode.getPhysicalHeight()) {
                continue;
            }
            float rate = mode.getRefreshRate();
            lowestRate = Math.min(lowestRate, rate);
            if (rate <= requestedRate + 0.01f) selectedRate = Math.max(selectedRate, rate);
        }
        if (selectedRate >= 0f) return selectedRate;
        return lowestRate == Float.MAX_VALUE ? requestedRate : lowestRate;
    }

    private void initializeOrientationListener(float defaultMinRate) {
        mOrientationListener = new OrientationEventListener(mContext) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation == ORIENTATION_UNKNOWN) return;
                boolean landscape = mContext.getResources().getConfiguration().orientation
                        == Configuration.ORIENTATION_LANDSCAPE;
                if (landscape != mIsLandscape) {
                    mIsLandscape = landscape;
                    applyLandscapeProfile(defaultMinRate);
                }
            }
        };

        if (mOrientationListener.canDetectOrientation()) {
            mOrientationListener.enable();
        } else {
            Log.w(TAG, "Orientation detection is unavailable; using current orientation");
        }
    }

    protected void disableOrientationListener() {
        if (mOrientationListener != null) {
            mOrientationListener.disable();
            mOrientationListener = null;
        }
    }

    private String[] getProfiles() {
        String value = mSharedPrefs.getString(REFRESH_CONTROL, null);
        if (value == null || value.isEmpty()) {
            value = REFRESH_STANDARD + ":" + REFRESH_EXTREME + ":" + REFRESH_LAND;
            mSharedPrefs.edit().putString(REFRESH_CONTROL, value).apply();
        }
        String[] modes = value.split(":", -1);
        if (modes.length < 3) {
            String[] repaired = {REFRESH_STANDARD, REFRESH_EXTREME, REFRESH_LAND};
            System.arraycopy(modes, 0, repaired, 0, modes.length);
            modes = repaired;
            mSharedPrefs.edit().putString(REFRESH_CONTROL,
                    modes[0] + ":" + modes[1] + ":" + modes[2]).apply();
        }
        return modes;
    }

    private Set<String> getPackages(String mode) {
        Set<String> packages = new HashSet<>();
        int separator = mode.indexOf('=');
        if (separator >= 0 && separator + 1 < mode.length()) {
            for (String packageName : mode.substring(separator + 1).split(",")) {
                if (!packageName.isEmpty()) packages.add(packageName);
            }
        }
        return packages;
    }

    protected void writePackage(String packageName, int state) {
        String[] modes = getProfiles();
        for (int i = 0; i < modes.length; i++) {
            Set<String> packages = getPackages(modes[i]);
            packages.remove(packageName);
            modes[i] = modes[i].substring(0, modes[i].indexOf('=') + 1)
                    + String.join(",", packages) + (packages.isEmpty() ? "" : ",");
        }
        int index = state == STATE_STANDARD ? 0 : state == STATE_EXTREME ? 1
                : state == STATE_LAND ? 2 : -1;
        if (index >= 0) {
            modes[index] += packageName + ",";
        }
        mSharedPrefs.edit().putString(REFRESH_CONTROL,
                modes[0] + ":" + modes[1] + ":" + modes[2]).apply();
    }

    protected int getStateForPackage(String packageName) {
        String[] modes = getProfiles();
        if (getPackages(modes[0]).contains(packageName)) return STATE_STANDARD;
        if (getPackages(modes[1]).contains(packageName)) return STATE_EXTREME;
        if (getPackages(modes[2]).contains(packageName)) return STATE_LAND;
        return STATE_DEFAULT;
    }
}
