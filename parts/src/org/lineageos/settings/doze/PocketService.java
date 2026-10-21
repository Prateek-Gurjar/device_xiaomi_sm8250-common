/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.doze;

import android.app.KeyguardManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;

public class PocketService extends Service {

    private static final String TAG = "PocketService";

    private static final String LARGE_AREA_TOUCH_SENSOR_TYPE =
            "xiaomi.sensor.large_area_detect";

    private PowerManager mPowerManager;
    private KeyguardManager mKeyguardManager;
    private SensorManager mSensorManager;
    private Sensor mTouchSensor;

    private boolean mListening;
    private boolean mSleepRequested;

    @Override
    public void onCreate() {
        dlog("Creating service");
        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mKeyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        for (Sensor sensor : mSensorManager.getSensorList(Sensor.TYPE_ALL)) {
            if (LARGE_AREA_TOUCH_SENSOR_TYPE.equals(sensor.getStringType())) {
                mTouchSensor = sensor;
                break;
            }
        }
        if (mTouchSensor == null) {
            Log.w(TAG, "Large area touch sensor is unavailable; pocket detection disabled");
        }

        IntentFilter screenStateFilter = new IntentFilter();
        screenStateFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenStateFilter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(mScreenStateReceiver, screenStateFilter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        dlog("Starting service");
        updateSensorListening();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        dlog("Destroying service");
        unregisterReceiver(mScreenStateReceiver);
        stopListening();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void startService(Context context) {
        context.startServiceAsUser(new Intent(context, PocketService.class), UserHandle.CURRENT);
    }

    private BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case Intent.ACTION_SCREEN_ON:
                    dlog("Received ACTION_SCREEN_ON");
                    mSleepRequested = false;
                    updateSensorListening();
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    dlog("Received ACTION_SCREEN_OFF");
                    stopListening();
                    mSleepRequested = false;
                    break;
                case Intent.ACTION_USER_PRESENT:
                    dlog("Received ACTION_USER_PRESENT");
                    // disable when unlocked
                    stopListening();
                    break;
            }
        }
    };

    private SensorEventListener mSensorListener = new SensorEventListener() {
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // no-op
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.values == null || event.values.length == 0) return;

            boolean isTouchDetected = event.values[0] == 1f;
            boolean isOnKeyguard = mKeyguardManager.isKeyguardLocked();

            dlog("onSensorChanged type=" + event.sensor.getType()
                    + " value=" + event.values[0] + " isTouchDetected="
                    + isTouchDetected + " isOnKeyguard=" + isOnKeyguard);

            if (isTouchDetected && isOnKeyguard && mPowerManager.isInteractive()
                    && !mSleepRequested) {
                mSleepRequested = true;
                Log.i(TAG, "In pocket, going to sleep");
                mPowerManager.goToSleep(SystemClock.uptimeMillis());
            }
        }
    };

    private void updateSensorListening() {
        if (mTouchSensor == null) return;
        boolean shouldListen = mPowerManager.isInteractive()
                && mKeyguardManager.isKeyguardLocked();
        if (shouldListen && !mListening) {
            mListening = mSensorManager.registerListener(mSensorListener, mTouchSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
            if (!mListening) {
                Log.w(TAG, "Unable to register large area touch sensor listener");
            }
        } else if (!shouldListen) {
            stopListening();
        }
    }

    private void stopListening() {
        if (mListening) {
            mSensorManager.unregisterListener(mSensorListener, mTouchSensor);
            mListening = false;
        }
    }

    private static void dlog(String msg) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, msg);
        }
    }
}
