/*
 * Copyright (C) 2024 The LineageOS Project
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

package org.lineageos.settings.touchsampling;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class TouchSamplingTileService extends TileService {

    private void updateUI(boolean enabled) {
        final Tile tile = getQsTile();
        if (tile == null) return;
        if (!TouchSamplingUtils.isSupported()) {
            tile.setState(Tile.STATE_UNAVAILABLE);
        } else {
            tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        }
        tile.updateTile();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateUI(TouchSamplingUtils.isEnabled());
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (!TouchSamplingUtils.isSupported()) {
            updateUI(false);
            return;
        }
        final boolean enabled = !TouchSamplingUtils.isEnabled();
        if (!TouchSamplingUtils.setEnabled(this, enabled)) {
            updateUI(TouchSamplingUtils.isEnabled());
            return;
        }
        updateUI(enabled);
    }
}
