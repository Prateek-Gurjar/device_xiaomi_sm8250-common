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

package org.lineageos.settings.thermal;

import android.app.ActivityTaskManager;
import android.app.ActivityTaskManager.RootTaskInfo;
import android.app.IActivityTaskManager;
import android.app.Service;
import android.app.TaskStackListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.util.Log;

public class ThermalService extends Service {

    private static final String TAG = "ThermalService";
    private static final boolean DEBUG = false;

    private boolean mScreenOn = true;
    private String mCurrentApp = "";
    private ThermalUtils mThermalUtils;

    private IActivityTaskManager mActivityTaskManager;

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case Intent.ACTION_SCREEN_OFF:
                    mScreenOn = false;
                    setThermalProfile();
                    break;
                case Intent.ACTION_SCREEN_ON:
                    mScreenOn = true;
                    setThermalProfile();
                    break;
            }
        }
    };

    private BroadcastReceiver mRefreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            setThermalProfile();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.d(TAG, "Creating service");
        mThermalUtils = new ThermalUtils(this);
        PowerManager powerManager = getSystemService(PowerManager.class);
        mScreenOn = powerManager != null && powerManager.isInteractive();
        try {
            mActivityTaskManager = ActivityTaskManager.getService();
            mActivityTaskManager.registerTaskStackListener(mTaskListener);
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to register task stack listener", e);
        }
        registerReceiver();
        updateForegroundApp();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "Starting service");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mThermalUtils.updateTouchRotation();
    }

    @Override
    public void onDestroy() {
        try {
            if (mActivityTaskManager != null) {
                mActivityTaskManager.unregisterTaskStackListener(mTaskListener);
            }
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to unregister task stack listener", e);
        }
        try {
            unregisterReceiver(mIntentReceiver);
            unregisterReceiver(mRefreshReceiver);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Thermal receiver was not registered", e);
        }
        super.onDestroy();
    }

    private void registerReceiver() {
        IntentFilter screenFilter = new IntentFilter();
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenFilter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(mIntentReceiver, screenFilter, Context.RECEIVER_EXPORTED);

        IntentFilter refreshFilter = new IntentFilter(ThermalUtils.ACTION_REFRESH_PROFILE);
        registerReceiver(mRefreshReceiver, refreshFilter, Context.RECEIVER_NOT_EXPORTED);
    }

    private void setThermalProfile() {
        if (mScreenOn) {
            mThermalUtils.setThermalProfile(mCurrentApp);
        } else {
            mThermalUtils.setDefaultThermalProfile();
            mThermalUtils.resetTouchModes();
        }
    }

    private final TaskStackListener mTaskListener = new TaskStackListener() {
        @Override
        public void onTaskStackChanged() {
            updateForegroundApp();
        }
    };

    private void updateForegroundApp() {
        if (mActivityTaskManager == null) return;
        try {
            final RootTaskInfo info = mActivityTaskManager.getFocusedRootTaskInfo();
            if (info == null || info.topActivity == null) {
                mCurrentApp = "";
                setThermalProfile();
                return;
            }

            String foregroundApp = info.topActivity.getPackageName();
            if (!foregroundApp.equals(mCurrentApp)) mCurrentApp = foregroundApp;
            setThermalProfile();
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to determine foreground app", e);
        }
    }
}
