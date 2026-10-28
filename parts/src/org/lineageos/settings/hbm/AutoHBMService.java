/*
 * Copyright (C) 2018 The OmniROM Project
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.lineageos.settings.hbm;

import android.app.KeyguardManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.display.DcDimmingTileService;

public class AutoHBMService extends Service {
    private static final String TAG = "AutoHBMService";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private SensorManager mSensorManager;
    private Sensor mLightSensor;
    private KeyguardManager mKeyguardManager;
    private PowerManager mPowerManager;
    private SharedPreferences mSharedPrefs;
    private boolean mSensorRegistered;
    private boolean mAutoHBMActive;
    private float mLastLux = Float.NaN;

    private final Runnable mDisableHBM = () -> {
        float threshold = getLuxThreshold();
        if (mLastLux < threshold && !isManualHBMEnabled()
                && !isDcDimmingEnabled() && !mKeyguardManager.isKeyguardLocked()) {
            if (HBMUtils.setAutoEnabled(this, false)) mAutoHBMActive = false;
        }
    };

    private final SensorEventListener mSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.values == null || event.values.length == 0) return;
            mLastLux = event.values[0];
            updateHBMForLux(mLastLux);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    };

    private final BroadcastReceiver mStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                stopSensorMonitoring();
                if (isManualHBMEnabled()) {
                    HBMUtils.disableForScreenOff(AutoHBMService.this);
                    mAutoHBMActive = false;
                } else {
                    disableAutomaticHBM();
                }
                mLastLux = Float.NaN;
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                startSensorMonitoring();
            } else if (HBMUtils.ACTION_MODE_CHANGED.equals(action)) {
                handleModeChanged();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mSensorManager = getSystemService(SensorManager.class);
        mKeyguardManager = getSystemService(KeyguardManager.class);
        mPowerManager = getSystemService(PowerManager.class);
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mAutoHBMActive = HBMUtils.isAutoActive(this);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(HBMUtils.ACTION_MODE_CHANGED);
        registerReceiver(mStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        if (mPowerManager != null && mPowerManager.isInteractive()) {
            startSensorMonitoring();
        } else {
            disableAutomaticHBM();
        }
    }

    private void startSensorMonitoring() {
        if (mSensorRegistered || mSensorManager == null
                || !HBMFragment.isAUTOHBMEnabled(this)) return;
        mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (mLightSensor == null) {
            Log.w(TAG, "Ambient light sensor unavailable; automatic HBM is idle");
            disableAutomaticHBM();
            return;
        }
        mSensorRegistered = mSensorManager.registerListener(mSensorListener,
                mLightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        if (!mSensorRegistered) Log.w(TAG, "Unable to register ambient light listener");
    }

    private void stopSensorMonitoring() {
        mHandler.removeCallbacks(mDisableHBM);
        if (!mSensorRegistered || mSensorManager == null) return;
        mSensorManager.unregisterListener(mSensorListener, mLightSensor);
        mSensorRegistered = false;
    }

    private void updateHBMForLux(float lux) {
        float threshold = getLuxThreshold();
        if (isManualHBMEnabled()) {
            mHandler.removeCallbacks(mDisableHBM);
            // Manual HBM now owns the active panel mode.
            mAutoHBMActive = false;
            return;
        }
        if (isDcDimmingEnabled() || mKeyguardManager.isKeyguardLocked()) {
            mHandler.removeCallbacks(mDisableHBM);
            disableAutomaticHBM();
            return;
        }
        if (lux >= threshold) {
            mHandler.removeCallbacks(mDisableHBM);
            if (!mAutoHBMActive && HBMUtils.setAutoEnabled(this, true)) {
                mAutoHBMActive = true;
            }
        } else if (mAutoHBMActive) {
            mHandler.removeCallbacks(mDisableHBM);
            mHandler.postDelayed(mDisableHBM, getDisableDelayMillis());
        }
    }

    private void handleModeChanged() {
        if (isManualHBMEnabled()) {
            mHandler.removeCallbacks(mDisableHBM);
            mAutoHBMActive = false;
        } else if (isDcDimmingEnabled() || mKeyguardManager.isKeyguardLocked()) {
            disableAutomaticHBM();
        } else if (mSensorRegistered && !Float.isNaN(mLastLux)) {
            updateHBMForLux(mLastLux);
        }
    }

    private void disableAutomaticHBM() {
        mHandler.removeCallbacks(mDisableHBM);
        if (mAutoHBMActive && HBMUtils.setAutoEnabled(this, false)) {
            mAutoHBMActive = false;
        }
    }

    private boolean isManualHBMEnabled() {
        return mSharedPrefs.getBoolean(HBMFragment.KEY_HBM_SWITCH, false);
    }

    private boolean isDcDimmingEnabled() {
        return mSharedPrefs.getBoolean(DcDimmingTileService.DC_DIMMING_ENABLE_KEY, false);
    }

    private float getLuxThreshold() {
        try {
            return Float.parseFloat(mSharedPrefs.getString(
                    HBMFragment.KEY_AUTO_HBM_THRESHOLD, "7000"));
        } catch (NumberFormatException e) {
            return 7000;
        }
    }

    private long getDisableDelayMillis() {
        try {
            long seconds = Long.parseLong(mSharedPrefs.getString(
                    HBMFragment.KEY_HBM_DISABLE_TIME, "1"));
            return Math.max(1, Math.min(10, seconds)) * 1000;
        } catch (NumberFormatException e) {
            return 1000;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopSensorMonitoring();
        disableAutomaticHBM();
        unregisterReceiver(mStateReceiver);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
