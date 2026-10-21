package org.lineageos.settings.dirac;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

public class DiracTileService extends TileService {

    private static final String TAG = "DiracTileService";
    private DiracUtils mDiracUtils;

    @Override
    public void onStartListening() {
        super.onStartListening();
        refreshTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        Tile tile = getQsTile();
        if (tile == null) return;

        try {
            if (mDiracUtils == null) {
                mDiracUtils = DiracUtils.getInstance(getApplicationContext());
            }
            Boolean enabled = mDiracUtils.isAvailable() ? mDiracUtils.getDiracState() : null;
            if (enabled != null) {
                mDiracUtils.setEnabled(!enabled);
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to toggle Dirac", e);
        }
        refreshTile();
    }

    private void refreshTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        try {
            if (mDiracUtils == null) {
                mDiracUtils = DiracUtils.getInstance(getApplicationContext());
            }
            Boolean enabled = mDiracUtils.isAvailable() ? mDiracUtils.getDiracState() : null;
            if (enabled == null) {
                tile.setState(Tile.STATE_UNAVAILABLE);
            } else {
                tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to read Dirac state", e);
            tile.setState(Tile.STATE_UNAVAILABLE);
        }
        tile.updateTile();
    }
}
