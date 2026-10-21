/*
 * Copyright (C) 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.saturation;

import android.app.ActivityTaskManager;
import android.app.Service;
import android.app.TaskStackListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
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
    private boolean mTaskListenerRegistered;
    private final Handler mHandler = new Handler();

    private final ContentObserver mColorModeObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            reapplyAfterColorModeChange();
        }
    };

    private final android.content.BroadcastReceiver mScreenReceiver =
            new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                // ColorService restores the display mode 100 ms after screen-on.
                mHandler.removeCallbacks(mReapplyRunnable);
                mHandler.postDelayed(mReapplyRunnable, 200);
            }
        }
    };

    private final Runnable mReapplyRunnable = this::applyForTopPackage;

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
        getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.DISPLAY_COLOR_MODE), false,
                mColorModeObserver, UserHandle.USER_CURRENT);
        IntentFilter screenFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        registerReceiver(mScreenReceiver, screenFilter);
        syncTaskListener();
        updateTopPackage();
    }

    @Override
    public void onDestroy() {
        unregisterTaskListener();
        getContentResolver().unregisterContentObserver(mColorModeObserver);
        unregisterReceiver(mScreenReceiver);
        mHandler.removeCallbacks(mReapplyRunnable);
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
            syncTaskListener();
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

    private void syncTaskListener() {
        if (mPerAppEnabled && !mTaskListenerRegistered) {
            try {
                ActivityTaskManager.getService().registerTaskStackListener(mTaskListener);
                mTaskListenerRegistered = true;
                updateTopPackage();
            } catch (RemoteException | SecurityException e) {
                Log.e(TAG, "Unable to monitor foreground app changes", e);
            }
        } else if (!mPerAppEnabled) {
            unregisterTaskListener();
        }
    }

    private void unregisterTaskListener() {
        if (!mTaskListenerRegistered) return;
        try {
            ActivityTaskManager.getService().unregisterTaskStackListener(mTaskListener);
        } catch (RemoteException | SecurityException e) {
            Log.w(TAG, "Unable to unregister task listener", e);
        } finally {
            mTaskListenerRegistered = false;
        }
    }

    private void reapplyAfterColorModeChange() {
        mHandler.removeCallbacks(mReapplyRunnable);
        mHandler.postDelayed(mReapplyRunnable, 200);
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
        if (useSaturation) {
            SaturationUtils.apply(mSaturation);
        } else {
            SaturationUtils.applyValue(1.0f);
        }
    }
}
