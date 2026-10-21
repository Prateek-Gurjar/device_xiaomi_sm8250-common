/*
* Copyright (C) 2018 The OmniROM Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/

package org.lineageos.settings.hbm;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.BroadcastReceiver;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import androidx.preference.PreferenceManager;

import org.lineageos.settings.display.DcDimmingTileService;

public class HBMModeTileService extends TileService {

    private BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                HBMController.disableAll(context);
                updateUI(false);
            }
        }
    };

    private void updateUI(boolean enabled) {
        final Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenStateReceiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(screenStateReceiver);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateUI(HBMController.isEnabled(this));
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
    }
    @Override
    public void onClick() {
        super.onClick();
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean dcDimmingEnabled = sharedPrefs.getBoolean(DcDimmingTileService.DC_DIMMING_ENABLE_KEY, false);
        if (dcDimmingEnabled) {
            updateUI(HBMController.isEnabled(this));
            return;
        }
        if (HBMController.isEnabled(this)) {
            HBMController.disableAll(this);
        } else {
            HBMController.setManual(this, true);
        }
        updateUI(HBMController.isEnabled(this));
    }
}
