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
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.util.Log;
import java.util.List;

public class DiracUtils {

    private static final String TAG = "DiracUtils";
    private static final int EQ_BAND_COUNT = 7;
    private static final long PLAYBACK_REFRESH_DELAY_MS = 250;

    private static DiracUtils mInstance;
    private DiracSound mDiracSound;
    private MediaSessionManager mMediaSessionManager;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Context mContext;
    private Runnable mPendingPlaybackRefresh;

    public DiracUtils(Context context) {
        mContext = context.getApplicationContext();
        mMediaSessionManager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        mDiracSound = new DiracSound(0, 0);
    }

    public static synchronized DiracUtils getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new DiracUtils(context);
        }

        return mInstance;
    }

    private int getMediaControllerPlaybackState(MediaController controller) {
        if (controller != null) {
            final PlaybackState playbackState = controller.getPlaybackState();
            if (playbackState != null) {
                return playbackState.getState();
            }
        }
        return PlaybackState.STATE_NONE;
    }

    private void refreshPlaybackIfNecessary() {
        if (mMediaSessionManager == null) {
            return;
        }

        final List<MediaController> sessions;
        try {
            sessions = mMediaSessionManager.getActiveSessionsForUser(null, UserHandle.ALL);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to query active media sessions", e);
            return;
        }
        for (MediaController controller : sessions) {
            if (PlaybackState.STATE_PLAYING == getMediaControllerPlaybackState(controller)) {
                schedulePlaybackRefresh(controller);
                return;
            }
        }
        cancelPlaybackRefresh();
    }

    private void schedulePlaybackRefresh(final MediaController controller) {
        cancelPlaybackRefresh();
        // Keep the existing pause/resume refresh required by the vendor effect, but use
        // transport controls and re-check playback state before resuming. A newer toggle
        // cancels this work so an old request cannot restart playback later.
        try {
            if (controller.getTransportControls() == null) return;
            controller.getTransportControls().pause();
            mPendingPlaybackRefresh = new Runnable() {
                @Override
                public void run() {
                    mPendingPlaybackRefresh = null;
                    try {
                        if (getMediaControllerPlaybackState(controller)
                                == PlaybackState.STATE_PAUSED) {
                            controller.getTransportControls().play();
                        }
                    } catch (RuntimeException e) {
                        Log.w(TAG, "Unable to resume playback after audio refresh", e);
                    }
                }
            };
            mHandler.postDelayed(mPendingPlaybackRefresh, PLAYBACK_REFRESH_DELAY_MS);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to refresh active media session", e);
        }
    }

    private void cancelPlaybackRefresh() {
        if (mPendingPlaybackRefresh != null) {
            mHandler.removeCallbacks(mPendingPlaybackRefresh);
            mPendingPlaybackRefresh = null;
        }
    }

    public boolean isAvailable() {
        return mDiracSound != null;
    }

    public Boolean getDiracState() {
        try {
            return mDiracSound == null ? null : mDiracSound.getMusic() == 1;
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to read Dirac state", e);
            return null;
        }
    }

    public boolean isDiracEnabled() {
        Boolean enabled = getDiracState();
        return enabled != null && enabled;
    }

    public boolean setEnabled(boolean enable) {
        if (mDiracSound == null) return false;
        cancelPlaybackRefresh();
        try {
            mDiracSound.setEnabled(enable);
            mDiracSound.setMusic(enable ? 1 : 0);
            refreshPlaybackIfNecessary();
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to set Dirac enabled=" + enable, e);
            return false;
        }
    }

    public boolean setLevel(String preset) {
        if (mDiracSound == null || preset == null) return false;
        String[] levels = preset.split(",", -1);
        if (levels.length != EQ_BAND_COUNT) return false;
        float[] parsedLevels = new float[EQ_BAND_COUNT];
        try {
            for (int band = 0; band < EQ_BAND_COUNT; band++) {
                parsedLevels[band] = Float.parseFloat(levels[band].trim());
                if (Float.isNaN(parsedLevels[band]) || Float.isInfinite(parsedLevels[band])) {
                    return false;
                }
            }
            for (int band = 0; band < EQ_BAND_COUNT; band++) {
                mDiracSound.setLevel(band, parsedLevels[band]);
            }
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to apply EQ preset", e);
            return false;
        }
    }

    public boolean setHeadsetType(int type) {
        if (mDiracSound == null) return false;
        try {
            mDiracSound.setHeadsetType(type);
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to set headset type", e);
            return false;
        }
    }

    public boolean getHifiMode() {
        try {
            AudioManager audioManager = mContext.getSystemService(AudioManager.class);
            return audioManager != null
                    && audioManager.getParameters("hifi_mode").contains("hifi_mode=true");
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to read Hi-Fi state", e);
            return false;
        }
    }

    public boolean setHifiMode(int mode) {
        if (mDiracSound == null) return false;
        try {
            AudioManager audioManager = mContext.getSystemService(AudioManager.class);
            if (audioManager == null) return false;
            boolean previousMode = getHifiMode();
            audioManager.setParameters("hifi_mode=" + (mode == 1));
            try {
                mDiracSound.setHifiMode(mode == 1 ? 1 : 0);
            } catch (RuntimeException e) {
                audioManager.setParameters("hifi_mode=" + previousMode);
                throw e;
            }
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to set Hi-Fi mode", e);
            return false;
        }
    }

    public boolean setScenario(int scene) {
        if (mDiracSound == null) return false;
        try {
            mDiracSound.setScenario(scene);
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to set audio scenario", e);
            return false;
        }
    }
}
