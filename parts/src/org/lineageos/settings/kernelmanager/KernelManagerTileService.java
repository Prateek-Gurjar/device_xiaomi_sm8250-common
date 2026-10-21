/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.kernelmanager;

import android.content.Intent;
import android.service.quicksettings.TileService;

/** Opens the CPU manager without directly changing CPU policy. */
public class KernelManagerTileService extends TileService {
    @Override
    public void onClick() {
        super.onClick();
        Intent intent = new Intent(this, KernelManagerActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityAndCollapse(intent);
    }
}
