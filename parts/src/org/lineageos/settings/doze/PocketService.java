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

    /* xiaomi.sensor.large_area_detect */
    private static final int TYPE_LARGE_AREA_TOUCH_SENSOR = 33171031;

    private PowerManager mPowerManager;
    private KeyguardManager mKeyguardManager;
    private SensorManager mSensorManager;
    private Sensor mTouchSensor;
    private boolean mSensorRegistered;

    @Override
    public void onCreate() {
        dlog("Creating service");
        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mKeyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mTouchSensor = mSensorManager.getDefaultSensor(TYPE_LARGE_AREA_TOUCH_SENSOR);
        if (mTouchSensor == null) {
            Log.w(TAG, "Large area touch sensor is unavailable; pocket detection disabled");
        }

        IntentFilter screenStateFilter = new IntentFilter();
        screenStateFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenStateFilter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(mScreenStateReceiver, screenStateFilter);

        // The service may start after ACTION_SCREEN_ON has already been sent.
        updateSensorMonitoring();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        dlog("Starting service");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        dlog("Destroying service");
        unregisterReceiver(mScreenStateReceiver);
        stopSensorMonitoring();
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
                    updateSensorMonitoring();
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    dlog("Received ACTION_SCREEN_OFF");
                    stopSensorMonitoring();
                    break;
                case Intent.ACTION_USER_PRESENT:
                    dlog("Received ACTION_USER_PRESENT");
                    // disable when unlocked
                    stopSensorMonitoring();
                    break;
            }
        }
    };

    private void updateSensorMonitoring() {
        if (mTouchSensor == null || mSensorRegistered) return;
        if (!mPowerManager.isInteractive() || !mKeyguardManager.isKeyguardLocked()) return;

        mSensorRegistered = mSensorManager.registerListener(mSensorListener,
                mTouchSensor, SensorManager.SENSOR_DELAY_NORMAL);
        if (!mSensorRegistered) {
            Log.w(TAG, "Unable to register large area touch sensor listener");
        }
    }

    private void stopSensorMonitoring() {
        if (!mSensorRegistered) return;
        mSensorManager.unregisterListener(mSensorListener, mTouchSensor);
        mSensorRegistered = false;
    }

    private SensorEventListener mSensorListener = new SensorEventListener() {
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // no-op
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.values == null || event.values.length == 0) return;
            boolean isTouchDetected = event.values[0] == 1;
            boolean isOnKeyguard = mKeyguardManager.isKeyguardLocked();

            dlog("onSensorChanged type=" + event.sensor.getType()
                    + " value=" + event.values[0] + " isTouchDetected="
                    + isTouchDetected + " isOnKeyguard=" + isOnKeyguard);

            if (isTouchDetected && isOnKeyguard && mPowerManager.isInteractive()) {
                Log.i(TAG, "In pocket, going to sleep");
                mPowerManager.goToSleep(SystemClock.uptimeMillis());
            }
        }
    };

    private static void dlog(String msg) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, msg);
        }
    }
}
