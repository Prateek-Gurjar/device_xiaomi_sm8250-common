/*
 * Copyright (C) 2021 crDroid Android Project
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

package org.lineageos.settings;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.view.Display;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class RefreshRateTileService extends TileService {
    private static final String KEY_MIN_REFRESH_RATE = "min_refresh_rate";
    private static final String KEY_PREFERRED_REFRESH_RATE = "preferred_refresh_rate";
    private static final String KEY_PEAK_REFRESH_RATE = "peak_refresh_rate";

    private Context mContext;
    private Tile mTile;
    private final List<Float> mAvailableRates = new ArrayList<>();
    private int mActiveRateMin;
    private int mActiveRateMax;

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = getApplicationContext();
        refreshAvailableRates();
    }

    private void refreshAvailableRates() {
        mAvailableRates.clear();
        DisplayManager displayManager = mContext.getSystemService(DisplayManager.class);
        Display display = displayManager == null
                ? null : displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null) return;

        Display.Mode currentMode = display.getMode();
        if (currentMode == null) return;
        for (Display.Mode mode : display.getSupportedModes()) {
            if (mode.getPhysicalWidth() != currentMode.getPhysicalWidth()
                    || mode.getPhysicalHeight() != currentMode.getPhysicalHeight()) {
                continue;
            }
            float rate = normalizeRate(mode.getRefreshRate());
            if (!mAvailableRates.contains(rate)) mAvailableRates.add(rate);
        }
        Collections.sort(mAvailableRates);
    }

    private float normalizeRate(float rate) {
        return Math.round(rate * 100f) / 100f;
    }

    private int findRateIndex(float rate) {
        if (mAvailableRates.isEmpty()) return -1;
        int nearestIndex = 0;
        float nearestDifference = Math.abs(mAvailableRates.get(0) - rate);
        for (int i = 1; i < mAvailableRates.size(); i++) {
            float difference = Math.abs(mAvailableRates.get(i) - rate);
            if (difference < nearestDifference) {
                nearestDifference = difference;
                nearestIndex = i;
            }
        }
        return nearestIndex;
    }

    private int getSettingIndex(String key, float fallbackRate) {
        float rate = Settings.System.getFloat(mContext.getContentResolver(), key, fallbackRate);
        return findRateIndex(rate);
    }

    private void syncFromSettings() {
        DisplayManager displayManager = mContext.getSystemService(DisplayManager.class);
        Display display = displayManager == null
                ? null : displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        float currentRate = display == null || display.getMode() == null
                ? 60f : display.getMode().getRefreshRate();
        mActiveRateMin = getSettingIndex(KEY_MIN_REFRESH_RATE, currentRate);
        mActiveRateMax = getSettingIndex(KEY_PEAK_REFRESH_RATE, currentRate);
    }

    private void cycleRefreshRate() {
        if (mAvailableRates.isEmpty()) return;
        int currentIndex = getSettingIndex(KEY_PREFERRED_REFRESH_RATE,
                mAvailableRates.get(mActiveRateMax));
        int nextIndex = (currentIndex + 1) % mAvailableRates.size();
        float rate = mAvailableRates.get(nextIndex);
        Settings.System.putFloat(mContext.getContentResolver(), KEY_MIN_REFRESH_RATE, rate);
        Settings.System.putFloat(mContext.getContentResolver(), KEY_PREFERRED_REFRESH_RATE, rate);
        Settings.System.putFloat(mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE, rate);
    }

    private String formatRate(float rate) {
        String value = String.format(Locale.US, "%.02f", rate);
        if (value.endsWith(".00")) value = value.substring(0, value.length() - 3);
        else if (value.endsWith("0")) value = value.substring(0, value.length() - 1);
        return value + " Hz";
    }

    private void updateTileView() {
        if (mTile == null) return;
        if (mAvailableRates.isEmpty() || mActiveRateMin < 0 || mActiveRateMax < 0) {
            mTile.setState(Tile.STATE_UNAVAILABLE);
            mTile.updateTile();
            return;
        }
        float min = mAvailableRates.get(mActiveRateMin);
        float max = mAvailableRates.get(mActiveRateMax);
        String text = min == max ? formatRate(min)
                : formatRate(min) + " - " + formatRate(max);
        mTile.setContentDescription(text);
        mTile.setSubtitle(text);
        mTile.setState(min == max ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        mTile.updateTile();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        mTile = getQsTile();
        if (mTile == null) return;
        refreshAvailableRates();
        syncFromSettings();
        updateTileView();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (mTile == null) return;
        refreshAvailableRates();
        syncFromSettings();
        if (!mAvailableRates.isEmpty()) cycleRefreshRate();
        syncFromSettings();
        updateTileView();
    }
}
