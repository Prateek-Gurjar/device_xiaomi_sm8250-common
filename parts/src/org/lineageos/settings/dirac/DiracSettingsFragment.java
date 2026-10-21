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

import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;

public class DiracSettingsFragment extends SettingsBasePreferenceFragment implements
        OnPreferenceChangeListener, OnCheckedChangeListener {

    private static final String TAG = "DiracSettingsFragment";
    private static final String PREF_ENABLE = "dirac_enable";
    private static final String PREF_HEADSET = "dirac_headset_pref";
    private static final String PREF_HIFI = "dirac_hifi_pref";
    private static final String PREF_PRESET = "dirac_preset_pref";
    private static final String PREF_SCENE = "scenario_selection";

    private MainSwitchPreference mSwitchBar;

    private ListPreference mHeadsetType;
    private ListPreference mPreset;
    private ListPreference mScenes;
    private SwitchPreferenceCompat mHifi;
    private DiracUtils mDiracUtils;
    private boolean mUpdatingUi;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.dirac_settings);

        try {
            mDiracUtils = DiracUtils.getInstance(getActivity());
        } catch (Exception e) {
            Log.d(TAG, "Dirac is not present in system");
        }

        mSwitchBar = (MainSwitchPreference) findPreference(PREF_ENABLE);
        mSwitchBar.addOnSwitchChangeListener(this);

        mHeadsetType = (ListPreference) findPreference(PREF_HEADSET);
        mHeadsetType.setOnPreferenceChangeListener(this);

        mPreset = (ListPreference) findPreference(PREF_PRESET);
        mPreset.setOnPreferenceChangeListener(this);

        mHifi = (SwitchPreferenceCompat) findPreference(PREF_HIFI);
        mHifi.setOnPreferenceChangeListener(this);

        mScenes = (ListPreference) findPreference(PREF_SCENE);
        mScenes.setOnPreferenceChangeListener(this);

        refreshPreferences();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshPreferences();
    }

    private void refreshPreferences() {
        Boolean diracState = mDiracUtils != null && mDiracUtils.isAvailable()
                ? mDiracUtils.getDiracState() : null;
        boolean available = diracState != null;
        boolean enabled = available && diracState;
        mUpdatingUi = true;
        mSwitchBar.setEnabled(available);
        mSwitchBar.setChecked(enabled);
        mHeadsetType.setEnabled(enabled);
        mPreset.setEnabled(enabled);
        mHifi.setEnabled(enabled);
        mScenes.setEnabled(enabled);
        if (available) {
            mHifi.setChecked(mDiracUtils.getHifiMode());
        }
        mUpdatingUi = false;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (mDiracUtils == null || !mDiracUtils.isAvailable()) return false;
        boolean applied;
        try {
            switch (preference.getKey()) {
                case PREF_HEADSET:
                    applied = mDiracUtils.setHeadsetType(Integer.parseInt(newValue.toString()));
                    break;
                case PREF_HIFI:
                    applied = mDiracUtils.setHifiMode((Boolean) newValue ? 1 : 0);
                    break;
                case PREF_PRESET:
                    applied = mDiracUtils.setLevel((String) newValue);
                    break;
                case PREF_SCENE:
                    applied = mDiracUtils.setScenario(Integer.parseInt(newValue.toString()));
                    break;
                default:
                    return false;
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Invalid Dirac preference value", e);
            applied = false;
        }
        if (!applied) {
            Toast.makeText(getContext(), R.string.dirac_operation_failed, Toast.LENGTH_SHORT).show();
        }
        return applied;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (mUpdatingUi) return;
        if (mDiracUtils == null || !mDiracUtils.setEnabled(isChecked)) {
            Toast.makeText(getContext(), R.string.dirac_operation_failed, Toast.LENGTH_SHORT).show();
            refreshPreferences();
            return;
        }
        refreshPreferences();
    }
}
