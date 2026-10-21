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
import android.os.IBinder;
import android.os.PowerManager;
import androidx.preference.PreferenceManager;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AutoHBMService extends Service {
    private ScheduledExecutorService mExecutor;
    private ScheduledFuture<?> mPendingDisable;
    private SensorManager mSensorManager;
    private Sensor mLightSensor;
    private SharedPreferences mSharedPrefs;
    private volatile float mLatestLux;
    private boolean mRegistered;

    private final SensorEventListener mSensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.values == null || event.values.length == 0) return;
            synchronized (AutoHBMService.this) {
                mLatestLux = event.values[0];
                float threshold = getThreshold();
                if (mLatestLux > threshold) {
                    cancelPendingDisable();
                    KeyguardManager keyguard = getSystemService(KeyguardManager.class);
                    if (keyguard != null && !keyguard.isKeyguardLocked()) {
                        HBMController.setAuto(AutoHBMService.this, true);
                    }
                } else if (HBMController.isAutoEnabled(AutoHBMService.this)) {
                    scheduleDisable();
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    };

    private final BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                activateLightSensorRead();
            } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                deactivateLightSensorRead();
                HBMController.disableAll(context);
            }
        }
    };

    private float getThreshold() {
        try {
            return Float.parseFloat(mSharedPrefs.getString(
                    HBMFragment.KEY_AUTO_HBM_THRESHOLD, "7000"));
        } catch (NumberFormatException e) {
            return 7000f;
        }
    }

    private long getDisableDelaySeconds() {
        try {
            long seconds = Long.parseLong(mSharedPrefs.getString(
                    HBMFragment.KEY_HBM_DISABLE_TIME, "1"));
            return Math.max(1, Math.min(10, seconds));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private synchronized void scheduleDisable() {
        if (mPendingDisable != null && !mPendingDisable.isDone()) return;
        mPendingDisable = mExecutor.schedule(() -> {
            synchronized (AutoHBMService.this) {
                if (mLatestLux <= getThreshold()) HBMController.setAuto(this, false);
                mPendingDisable = null;
            }
        }, getDisableDelaySeconds(), TimeUnit.SECONDS);
    }

    private synchronized void cancelPendingDisable() {
        if (mPendingDisable != null) {
            mPendingDisable.cancel(false);
            mPendingDisable = null;
        }
    }

    private void activateLightSensorRead() {
        if (mSensorManager == null || mLightSensor == null || mRegistered) return;
        mRegistered = mSensorManager.registerListener(mSensorEventListener, mLightSensor,
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void deactivateLightSensorRead() {
        cancelPendingDisable();
        if (mSensorManager != null && mRegistered) {
            mSensorManager.unregisterListener(mSensorEventListener);
            mRegistered = false;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mExecutor = Executors.newSingleThreadScheduledExecutor();
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        HBMController.setAuto(this, false);
        mSensorManager = getSystemService(SensorManager.class);
        if (mSensorManager != null) mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mScreenStateReceiver, filter);
        PowerManager power = getSystemService(PowerManager.class);
        if (power != null && power.isInteractive()) activateLightSensorRead();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        deactivateLightSensorRead();
        HBMController.setAuto(this, false);
        unregisterReceiver(mScreenStateReceiver);
        if (mExecutor != null) mExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
