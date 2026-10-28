/*
 * MIT License
 *
 * Copyright (c) 2026 The LineageOS Project
 */
package io.github.maytinhdibo.pocket;

import android.content.ComponentName;
import android.content.Context;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class PocketModeTileService extends TileService {
    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (!PocketUtils.isSupported(this)) {
            updateTileState();
            return;
        }

        PocketUtils.setEnabled(this, !PocketUtils.isEnabled(this));
        updateTileState();
    }

    public static void refresh(Context context) {
        requestListeningState(context,
                new ComponentName(context, PocketModeTileService.class));
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) return;

        boolean supported = PocketUtils.isSupported(this);
        boolean enabled = PocketUtils.isEnabled(this);
        tile.setState(!supported ? Tile.STATE_UNAVAILABLE
                : enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setSubtitle(!supported ? getString(R.string.pocketmode_unavailable)
                : enabled ? getString(R.string.pocketmode_tile_on)
                : getString(R.string.pocketmode_tile_off));
        tile.updateTile();
    }
}
