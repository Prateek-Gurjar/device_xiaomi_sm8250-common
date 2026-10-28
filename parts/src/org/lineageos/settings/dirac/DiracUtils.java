/*
 * Copyright (C) 2018,2020 The LineageOS Project
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

package org.lineageos.settings.dirac;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

public class DiracUtils {

    private static final String TAG = "DiracUtils";

    private static DiracUtils mInstance;
    private DiracSound mDiracSound;
    private Context mContext;
    private boolean mAvailable;

    public DiracUtils(Context context) {
        mContext = context.getApplicationContext();
        try {
            mDiracSound = new DiracSound(0, 0);
            mAvailable = true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Mi Sound effect is unavailable", e);
        }
    }

    public static synchronized DiracUtils getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new DiracUtils(context);
        }

        return mInstance;
    }

    public void setEnabled(boolean enable) {
        if (!mAvailable) return;
        try {
            mDiracSound.setMusic(enable ? 1 : 0);
            mDiracSound.setEnabled(enable);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to change Mi Sound state", e);
            mAvailable = false;
        }
    }

    public boolean isDiracEnabled() {
        if (!mAvailable) return false;
        try {
            return mDiracSound.getMusic() == 1;
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to read Mi Sound state", e);
            mAvailable = false;
            return false;
        }
    }

    public boolean isAvailable() {
        return mAvailable;
    }

    public void setLevel(String preset) {
        if (!mAvailable) return;
        String[] level = preset.split("\\s*,\\s*");

        try {
            for (int band = 0; band < level.length; band++) {
                mDiracSound.setLevel(band, Float.valueOf(level[band]));
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to set Mi Sound equalizer preset", e);
            mAvailable = false;
        }
    }

    public void setHeadsetType(int paramInt) {
        if (!mAvailable) return;
        try {
            mDiracSound.setHeadsetType(paramInt);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to set Mi Sound headset type", e);
            mAvailable = false;
        }
    }

    public boolean getHifiMode() {
        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        return audioManager != null && audioManager.getParameters("hifi_mode").contains("true");
    }

    public void setHifiMode(int paramInt) {
        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        if (!mAvailable || audioManager == null) return;
        try {
            audioManager.setParameters("hifi_mode=" + (paramInt == 1));
            mDiracSound.setHifiMode(paramInt);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to set Mi Sound Hi-Fi mode", e);
            mAvailable = false;
        }
    }

    public void setScenario(int sceneInt) {
        if (!mAvailable) return;
        try {
            mDiracSound.setScenario(sceneInt);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to set Mi Sound scenario", e);
            mAvailable = false;
        }
    }
}
