/*
 * Copyright (C) 2015 The CyanogenMod Project
 *               2017-2018 The LineageOS Project
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

package org.lineageos.settings.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class ProximitySensor implements SensorEventListener {

    private volatile boolean mSawNear;
    private final SensorManager mSensorManager;
    private final Sensor mSensor;
    private volatile boolean mEnabled;

    public ProximitySensor(Context context) {
        Context appContext = context.getApplicationContext();
        mSensorManager = appContext.getSystemService(SensorManager.class);
        mSensor = mSensorManager == null ? null
                : mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY, false);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!mEnabled) return;
        if (event.values == null || event.values.length == 0 || event.sensor == null) return;
        mSawNear = event.values[0] < event.sensor.getMaximumRange();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        /* Empty */
    }

    public synchronized void enable() {
        if (mSensorManager == null || mSensor == null) {
            mSawNear = false;
            return;
        }
        if (mEnabled) return;

        mEnabled = mSensorManager.registerListener(this, mSensor,
                SensorManager.SENSOR_DELAY_NORMAL);
        if (!mEnabled) mSawNear = false;
    }

    public synchronized void disable() {
        if (mSensorManager != null && mSensor != null && mEnabled) {
            mSensorManager.unregisterListener(this, mSensor);
        }
        mEnabled = false;
        mSawNear = false;
    }

    public boolean getSawNear() {
        return mSawNear;
    }
}
