/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.saturation;

import android.app.ActivityTaskManager;
import android.app.Service;
import android.app.TaskStackListener;
import android.content.BroadcastReceiver;
import android.content.Context;
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

import androidx.preference.PreferenceManager;

import org.lineageos.settings.Constants;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Applies the selected saturation level according to the foreground app. */
public class PerAppSaturationService extends Service
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "PerAppSaturation";
    private static final long REAPPLY_DELAY_MS = 200;

    private final Handler mHandler = new Handler();
    private SharedPreferences mPrefs;
    private int mSaturation = 100;
    private boolean mPerAppEnabled;
    private boolean mTaskListenerRegistered;
    private String mTopPackage = "";
    private Set<String> mDisabledPackages = Collections.emptySet();

    private final Runnable mReapplyRunnable = this::applyForTopPackage;
    private final ContentObserver mColorModeObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            scheduleReapply();
        }
    };

    private final BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                // System color services can restore the selected mode shortly after wake.
                scheduleReapply();
            }
        }
    };

    private final TaskStackListener mTaskListener = new TaskStackListener() {
        @Override
        public void onTaskStackChanged() {
            mHandler.post(PerAppSaturationService.this::updateTopPackage);
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
        registerReceiver(mScreenReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED);
        syncTaskListener();
        updateTopPackage();
    }

    @Override
    public void onDestroy() {
        unregisterTaskListener();
        getContentResolver().unregisterContentObserver(mColorModeObserver);
        unregisterReceiver(mScreenReceiver);
        mHandler.removeCallbacks(mReapplyRunnable);
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
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
        mDisabledPackages = disabled == null ? Collections.emptySet() : new HashSet<>(disabled);
    }

    private void syncTaskListener() {
        if (mPerAppEnabled && !mTaskListenerRegistered) {
            try {
                ActivityTaskManager.getService().registerTaskStackListener(mTaskListener);
                mTaskListenerRegistered = true;
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
            Log.w(TAG, "Unable to unregister task stack listener", e);
        } finally {
            mTaskListenerRegistered = false;
        }
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
        boolean useCustomSaturation = !mPerAppEnabled || !mDisabledPackages.contains(mTopPackage);
        if (useCustomSaturation) {
            SaturationUtils.apply(mSaturation);
        } else {
            SaturationUtils.applyValue(1.0f);
        }
    }

    private void scheduleReapply() {
        mHandler.removeCallbacks(mReapplyRunnable);
        mHandler.postDelayed(mReapplyRunnable, REAPPLY_DELAY_MS);
    }
}
