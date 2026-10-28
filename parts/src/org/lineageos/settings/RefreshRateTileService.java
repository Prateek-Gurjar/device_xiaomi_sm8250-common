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
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.view.Display;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class RefreshRateTileService extends TileService {
    private static final String KEY_MIN_REFRESH_RATE = "min_refresh_rate";
    private static final String KEY_PREFERRED_REFRESH_RATE = "preferred_refresh_rate";
    private static final String KEY_PEAK_REFRESH_RATE = "peak_refresh_rate";

    private Context context;
    private Tile tile;

    private final List<Float> availableRates = new ArrayList<>();
    private int activeRateMin;
    private int activeRateMax;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        Display display = context.getDisplay();
        if (display == null) {
            syncFromSettings();
            return;
        }
        Display.Mode mode = display.getMode();
        Display.Mode[] modes = display.getSupportedModes();
        if (mode == null || modes == null) {
            syncFromSettings();
            return;
        }
        Set<Float> rates = new LinkedHashSet<>();
        for (Display.Mode m : modes) {
            if (m.getPhysicalWidth() == mode.getPhysicalWidth() &&
                m.getPhysicalHeight() == mode.getPhysicalHeight()) {
                rates.add(roundRate(m.getRefreshRate()));
            }
        }
        availableRates.addAll(rates);
        Collections.sort(availableRates);
        syncFromSettings();
    }

    private float roundRate(float rate) {
        return Math.round(rate * 100f) / 100f;
    }

    private int getSettingOf(String key) {
        float rate = Settings.System.getFloat(context.getContentResolver(), key, 60);
        if (availableRates.isEmpty()) return -1;
        float target = roundRate(rate);
        int closest = 0;
        for (int i = 1; i < availableRates.size(); i++) {
            if (Math.abs(availableRates.get(i) - target)
                    < Math.abs(availableRates.get(closest) - target)) {
                closest = i;
            }
        }
        return closest;
    }

    private void syncFromSettings() {
        activeRateMin = getSettingOf(KEY_MIN_REFRESH_RATE);
        activeRateMax = getSettingOf(KEY_PEAK_REFRESH_RATE);
    }

    private void cycleRefreshRate() {
        if (availableRates.isEmpty()) return;
        if (activeRateMin < availableRates.size() - 1) {
            activeRateMin++;
        } else {
            activeRateMin = 0;
        }

        float rate = availableRates.get(activeRateMin);
        Settings.System.putFloat(context.getContentResolver(), KEY_MIN_REFRESH_RATE, rate);
        Settings.System.putFloat(context.getContentResolver(), KEY_PREFERRED_REFRESH_RATE, rate);
        Settings.System.putFloat(context.getContentResolver(), KEY_PEAK_REFRESH_RATE, rate);
    }

    private String getFormatRate(float rate) {
        return String.format(Locale.US, "%.02f Hz", rate).replaceAll("[\\.,]00", "");
    }

    private void updateTileView() {
        if (tile == null) return;
        if (availableRates.isEmpty()) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setSubtitle(null);
            tile.setContentDescription(getString(R.string.refresh_rate_unavailable));
            tile.updateTile();
            return;
        }
        String displayText;
        float min = availableRates.get(activeRateMin);
        float max = availableRates.get(activeRateMax);

        displayText = String.format(Locale.US, min == max ? "%s" : "%s - %s",
            getFormatRate(min), getFormatRate(max));
        tile.setContentDescription(displayText);
        tile.setSubtitle(displayText);
        tile.setState(min == max ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        tile = getQsTile();
        syncFromSettings();
        updateTileView();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (availableRates.isEmpty()) return;
        cycleRefreshRate();
        syncFromSettings();
        updateTileView();
    }
}
