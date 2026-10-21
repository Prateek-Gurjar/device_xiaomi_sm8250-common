/*
 * Copyright (C) 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.saturation;

import android.app.ActivityTaskManager;
import android.app.Service;
import android.app.TaskStackListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.lineageos.settings.Constants;

import java.util.HashSet;
import java.util.Set;

/** Applies the existing SurfaceFlinger saturation control for the foreground package. */
public class PerAppSaturationService extends Service
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "PerAppSaturation";
    private SharedPreferences mPrefs;
    private int mSaturation = 100;
    private boolean mPerAppEnabled;
    private String mTopPackage = "";
    private Set<String> mDisabledPackages = new HashSet<>();

    private final TaskStackListener mTaskListener = new TaskStackListener() {
        @Override
        public void onTaskStackChanged() {
            updateTopPackage();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mPrefs.registerOnSharedPreferenceChangeListener(this);
        reloadPreferences();
        try {
            ActivityTaskManager.getService().registerTaskStackListener(mTaskListener);
            updateTopPackage();
        } catch (RemoteException | SecurityException e) {
            Log.e(TAG, "Unable to monitor foreground app changes", e);
            applyForTopPackage();
        }
    }

    @Override
    public void onDestroy() {
        try {
            ActivityTaskManager.getService().unregisterTaskStackListener(mTaskListener);
        } catch (RemoteException | SecurityException e) {
            Log.w(TAG, "Unable to unregister task listener", e);
        }
        if (mPrefs != null) mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (Constants.KEY_SATURATION.equals(key)
                || Constants.KEY_PER_APP_SATURATION_ENABLED.equals(key)
                || Constants.KEY_PER_APP_SATURATION_DISABLED_PACKAGES.equals(key)) {
            reloadPreferences();
            applyForTopPackage();
        }
    }

    private void reloadPreferences() {
        mSaturation = mPrefs.getInt(Constants.KEY_SATURATION, 100);
        mPerAppEnabled = mPrefs.getBoolean(Constants.KEY_PER_APP_SATURATION_ENABLED, false);
        Set<String> disabled = mPrefs.getStringSet(
                Constants.KEY_PER_APP_SATURATION_DISABLED_PACKAGES, null);
        mDisabledPackages = disabled == null ? new HashSet<>() : new HashSet<>(disabled);
    }

    private void updateTopPackage() {
        try {
            ActivityTaskManager.RootTaskInfo focused =
                    ActivityTaskManager.getService().getFocusedRootTaskInfo();
            String packageName = focused != null && focused.topActivity != null
                    ? focused.topActivity.getPackageName() : "";
            if (!packageName.equals(mTopPackage)) {
                mTopPackage = packageName;
                applyForTopPackage();
            }
        } catch (RemoteException | SecurityException e) {
            Log.e(TAG, "Unable to determine foreground app", e);
        }
    }

    private void applyForTopPackage() {
        boolean useSaturation = !mPerAppEnabled || !mDisabledPackages.contains(mTopPackage);
        float value = useSaturation
                ? (mSaturation == 100 ? 1.001f : mSaturation / 100.0f) : 1.0f;
        applySaturation(value);
    }

    private void applySaturation(float value) {
        IBinder surfaceFlinger = ServiceManager.getService("SurfaceFlinger");
        if (surfaceFlinger == null) {
            Log.e(TAG, "SurfaceFlinger is unavailable");
            return;
        }
        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken("android.ui.ISurfaceComposer");
            data.writeFloat(value);
            if (!surfaceFlinger.transact(1022, data, null, 0)) {
                Log.e(TAG, "SurfaceFlinger rejected saturation transaction");
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to apply saturation", e);
        } finally {
            data.recycle();
        }
    }
}
