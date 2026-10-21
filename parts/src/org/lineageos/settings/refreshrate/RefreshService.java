/*
 * Copyright (C) 2020 The LineageOS Project
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

package org.lineageos.settings.refreshrate;

import android.app.ActivityTaskManager;
import android.app.ActivityTaskManager.RootTaskInfo;
import android.app.IActivityTaskManager;
import android.app.Service;
import android.app.TaskStackListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.preference.PreferenceManager;

public class RefreshService extends Service {

    private static final String TAG = "RefreshService";
    private static final String KEY_SESSION_ACTIVE = "refresh_session_active";
    private static final String KEY_BASELINE_MIN = "refresh_baseline_min";
    private static final String KEY_BASELINE_MAX = "refresh_baseline_max";
    private RefreshUtils mRefreshUtils;
    private IActivityTaskManager mActivityTaskManager;
    private String mPreviousApp;
    private float mDefaultMinRate;
    private float mDefaultMaxRate;
    private boolean mReceiverRegistered;
    private boolean mTaskListenerRegistered;
    private boolean mHasManagedProfile;
    private android.content.SharedPreferences mPreferences;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener
            mPreferenceListener = (preferences, key) -> {
                if ("refresh_control".equals(key)) {
                    mPreviousApp = null;
                    updateForegroundApp();
                }
            };

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                mPreviousApp = null;
            } else {
                updateForegroundApp();
            }
        }
    };

    private final TaskStackListener mTaskListener = new TaskStackListener() {
        @Override
        public void onTaskStackChanged() {
            updateForegroundApp();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mRefreshUtils = new RefreshUtils(this);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mPreferences.registerOnSharedPreferenceChangeListener(mPreferenceListener);
        mHasManagedProfile = mPreferences.getBoolean(KEY_SESSION_ACTIVE, false);
        mDefaultMinRate = mPreferences.getFloat(KEY_BASELINE_MIN,
                mRefreshUtils.getCurrentMinRate());
        mDefaultMaxRate = mPreferences.getFloat(KEY_BASELINE_MAX,
                mRefreshUtils.getCurrentMaxRate());
        registerReceiver();
        try {
            mActivityTaskManager = ActivityTaskManager.getService();
            mActivityTaskManager.registerTaskStackListener(mTaskListener);
            mTaskListenerRegistered = true;
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to register task stack listener", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        updateForegroundApp();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(mIntentReceiver, filter);
        mReceiverRegistered = true;
    }

    private void updateForegroundApp() {
        if (mActivityTaskManager == null || mRefreshUtils == null) return;
        try {
            RootTaskInfo info = mActivityTaskManager.getFocusedRootTaskInfo();
            String foregroundApp = info == null || info.topActivity == null
                    ? null : info.topActivity.getPackageName();
            if (foregroundApp == null) {
                restoreDefaultRate();
                mPreviousApp = null;
                return;
            }

            int state = mRefreshUtils.getStateForPackage(foregroundApp);
            if (!foregroundApp.equals(mPreviousApp)) {
                if (state == RefreshUtils.STATE_DEFAULT && mHasManagedProfile) {
                    restoreDefaultRate();
                }
                mRefreshUtils.applyProfile(foregroundApp, state,
                        mDefaultMinRate, mDefaultMaxRate);
                mHasManagedProfile = state != RefreshUtils.STATE_DEFAULT;
                mPreferences.edit().putBoolean(KEY_SESSION_ACTIVE, mHasManagedProfile)
                        .putFloat(KEY_BASELINE_MIN, mDefaultMinRate)
                        .putFloat(KEY_BASELINE_MAX, mDefaultMaxRate).apply();
                mPreviousApp = foregroundApp;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to get the foreground task", e);
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to apply foreground refresh rate", e);
        }
    }

    private void restoreDefaultRate() {
        if (mRefreshUtils != null) {
            mRefreshUtils.disableOrientationListener();
            mRefreshUtils.setRefreshRate(mDefaultMinRate, mDefaultMaxRate);
        }
        mHasManagedProfile = false;
        if (mPreferences != null) {
            mPreferences.edit().putBoolean(KEY_SESSION_ACTIVE, false).apply();
        }
    }

    @Override
    public void onDestroy() {
        if (mHasManagedProfile) restoreDefaultRate();
        if (mRefreshUtils != null) mRefreshUtils.disableOrientationListener();
        if (mReceiverRegistered) unregisterReceiver(mIntentReceiver);
        if (mPreferences != null) {
            mPreferences.unregisterOnSharedPreferenceChangeListener(mPreferenceListener);
        }
        if (mTaskListenerRegistered && mActivityTaskManager != null) {
            try {
                mActivityTaskManager.unregisterTaskStackListener(mTaskListener);
            } catch (RemoteException e) {
                Log.w(TAG, "Unable to unregister task stack listener", e);
            }
        }
        super.onDestroy();
    }
}
