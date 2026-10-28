/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.batterymonitor;

import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick toggle for the ongoing battery telemetry notification. */
public class BatteryMonitorTileService extends TileService {
    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onClick() {
        super.onClick();
        boolean enabled = !BatteryMonitorUtils.prefs(this)
                .getBoolean(BatteryMonitorUtils.ENABLED, false);
        BatteryMonitorUtils.prefs(this).edit()
                .putBoolean(BatteryMonitorUtils.ENABLED, enabled).apply();
        if (enabled) {
            startForegroundService(new Intent(this, BatteryMonitorService.class));
        } else {
            stopService(new Intent(this, BatteryMonitorService.class));
        }
        updateTileState();
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean enabled = BatteryMonitorUtils.prefs(this)
                .getBoolean(BatteryMonitorUtils.ENABLED, false);
        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.battery_monitor_title));
        tile.setSubtitle(enabled ? getString(R.string.battery_monitor_tile_on)
                : getString(R.string.battery_monitor_tile_off));
        tile.updateTile();
    }
}
