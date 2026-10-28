package org.lineageos.settings.dirac;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class DiracTileService extends TileService {

    private DiracUtils mDiracUtils;

    @Override
    public void onStartListening() {
        mDiracUtils = DiracUtils.getInstance(getApplicationContext());
        updateTileState();
        super.onStartListening();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (mDiracUtils == null) {
            mDiracUtils = DiracUtils.getInstance(getApplicationContext());
        }
        if (mDiracUtils.isAvailable()) {
            mDiracUtils.setEnabled(!mDiracUtils.isDiracEnabled());
        }
        updateTileState();
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) return;

        if (!mDiracUtils.isAvailable()) {
            tile.setState(Tile.STATE_UNAVAILABLE);
        } else if (mDiracUtils.isDiracEnabled()) {
            tile.setState(Tile.STATE_ACTIVE);
        } else {
            tile.setState(Tile.STATE_INACTIVE);
        }
        tile.updateTile();
    }
}
