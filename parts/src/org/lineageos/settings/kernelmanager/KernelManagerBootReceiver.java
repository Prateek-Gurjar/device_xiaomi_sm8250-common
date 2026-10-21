/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.kernelmanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Restores user CPU settings after device boot tuning has completed. */
public class KernelManagerBootReceiver extends BroadcastReceiver {
    private static final String TAG = "KernelManager";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!KernelManagerUtils.applySavedSettings(context)) {
            Log.w(TAG, "Could not restore saved CPU frequency settings after boot");
        }
    }
}
