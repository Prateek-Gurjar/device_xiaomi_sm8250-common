/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.refreshrate;

import android.app.PendingIntent;
import android.content.Intent;
import android.service.quicksettings.TileService;

/** Opens the per-app refresh rate settings from Quick Settings. */
public class RefreshSettingsTileService extends TileService {
    @Override
    public void onClick() {
        super.onClick();
        Intent intent = new Intent(this, RefreshActivity.class)
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
