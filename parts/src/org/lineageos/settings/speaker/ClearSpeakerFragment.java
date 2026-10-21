/*
 * Copyright (C) 2023 Paranoid Android
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

package org.lineageos.settings.speaker;

import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;

import java.io.IOException;

public class ClearSpeakerFragment extends SettingsBasePreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String TAG = "ClearSpeakerFragment";
    private static final String PREF_CLEAR_SPEAKER = "clear_speaker_pref";
    private static final int PLAY_DURATION_MS = 30000;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private MediaPlayer mMediaPlayer;
    private SwitchPreferenceCompat mClearSpeakerPref;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.clear_speaker_settings);

        mClearSpeakerPref = findPreference(PREF_CLEAR_SPEAKER);
        mClearSpeakerPref.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mClearSpeakerPref) {
            boolean value = (Boolean) newValue;
            if (value && startPlaying()) {
                mHandler.removeCallbacksAndMessages(null);
                mHandler.postDelayed(this::stopPlaying, PLAY_DURATION_MS);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onStop() {
        stopPlaying();
        super.onStop();
    }

    public boolean startPlaying() {
        AudioManager audioManager = getContext().getSystemService(AudioManager.class);
        if (audioManager == null) {
            return false;
        }

        if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                < audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)) {
            Toast.makeText(getContext(), R.string.clear_speaker_volume_hint,
                    Toast.LENGTH_LONG).show();
        }

        try {
            getActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mMediaPlayer.setLooping(true);
            mMediaPlayer.setOnErrorListener((player, what, extra) -> {
                Log.e(TAG, "Speaker cleaning playback failed: what=" + what + ", extra=" + extra);
                stopPlaying();
                return true;
            });
            mMediaPlayer.setOnCompletionListener(player -> stopPlaying());
            mMediaPlayer.addOnRoutingChangedListener(player -> {
                AudioDeviceInfo routedDevice = player.getRoutedDevice();
                if (routedDevice != null
                        && routedDevice.getType() != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    Toast.makeText(getContext(), R.string.clear_speaker_route_hint,
                            Toast.LENGTH_LONG).show();
                    stopPlaying();
                }
            }, mHandler);
            try (AssetFileDescriptor afd = getResources().openRawResourceFd(
                    R.raw.clear_speaker_sound)) {
                mMediaPlayer.setDataSource(afd);
                mMediaPlayer.setVolume(1.0f, 1.0f);
                mMediaPlayer.prepare();
                mMediaPlayer.start();
                mClearSpeakerPref.setEnabled(false);
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            Log.e(TAG, "Failed to play speaker clean sound!", e);
            releasePlayer();
            return false;
        }
        return true;
    }

    public void stopPlaying() {
        mHandler.removeCallbacksAndMessages(null);
        if (mMediaPlayer != null) {
            try {
                if (mMediaPlayer.isPlaying()) {
                    mMediaPlayer.stop();
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "Failed to stop media player!", e);
            } finally {
                releasePlayer();
            }
        }
        if (mClearSpeakerPref != null) {
            mClearSpeakerPref.setEnabled(true);
            mClearSpeakerPref.setChecked(false);
        }
    }

    private void releasePlayer() {
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }
}
