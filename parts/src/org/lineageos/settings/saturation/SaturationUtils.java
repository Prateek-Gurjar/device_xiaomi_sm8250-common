/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.saturation;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

/** Shared SurfaceFlinger saturation transaction and slider conversion. */
final class SaturationUtils {
    private static final String TAG = "Saturation";
    private static final int TRANSACTION_SET_SATURATION = 1022;
    private static final float DEFAULT_SATURATION = 1.001f;

    private SaturationUtils() {}

    static float getSaturation(int progress) {
        int clampedProgress = Math.max(0, Math.min(200, progress));
        return clampedProgress == 100 ? DEFAULT_SATURATION : clampedProgress / 100.0f;
    }

    static boolean apply(int progress) {
        return applyValue(getSaturation(progress));
    }

    static boolean applyValue(float saturation) {
        IBinder surfaceFlinger = ServiceManager.getService("SurfaceFlinger");
        if (surfaceFlinger == null) {
            Log.e(TAG, "SurfaceFlinger is unavailable");
            return false;
        }

        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken("android.ui.ISurfaceComposer");
            data.writeFloat(saturation);
            if (!surfaceFlinger.transact(TRANSACTION_SET_SATURATION, data, null, 0)) {
                Log.e(TAG, "SurfaceFlinger rejected saturation transaction");
                return false;
            }
            return true;
        } catch (RemoteException | RuntimeException e) {
            Log.e(TAG, "Failed to apply saturation", e);
            return false;
        } finally {
            data.recycle();
        }
    }
}
