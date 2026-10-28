/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.gpumanager;

import android.app.PendingIntent;
import android.content.Intent;
import android.service.quicksettings.TileService;

/** A safe shortcut to the GPU manager; it does not toggle low-level driver controls. */
public class GpuManagerTileService extends TileService {
    @Override
    public void onClick() {
        super.onClick();
        Intent intent = new Intent(this, GpuManagerActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Runnable launch = () -> startActivityAndCollapse(pendingIntent);
        if (isLocked()) {
            unlockAndRun(launch);
        } else {
            launch.run();
        }
    }
}
