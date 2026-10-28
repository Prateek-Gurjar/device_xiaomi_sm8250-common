/*
 * Copyright (C) 2018 The OmniROM Project
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.lineageos.settings.hbm;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.display.DcDimmingTileService;

public class HBMModeTileService extends TileService {
    private final BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                HBMUtils.disableForScreenOff(HBMModeTileService.this);
                updateUI(Tile.STATE_INACTIVE);
            }
        }
    };

    private void updateUI(int state) {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(state);
        tile.updateTile();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        registerReceiver(mScreenStateReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF),
                android.content.Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mScreenStateReceiver);
        super.onDestroy();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        if (!HBMUtils.isSupported()) {
            updateUI(Tile.STATE_UNAVAILABLE);
            return;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean(DcDimmingTileService.DC_DIMMING_ENABLE_KEY, false)) {
            updateUI(Tile.STATE_UNAVAILABLE);
        } else {
            updateUI(prefs.getBoolean("hbm", false) ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        }
    }

    @Override
    public void onClick() {
        super.onClick();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean(DcDimmingTileService.DC_DIMMING_ENABLE_KEY, false)) {
            updateUI(Tile.STATE_UNAVAILABLE);
            return;
        }
        boolean enabled = !prefs.getBoolean("hbm", false);
        if (HBMUtils.setManualEnabled(this, enabled)) {
            updateUI(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        }
    }
}
