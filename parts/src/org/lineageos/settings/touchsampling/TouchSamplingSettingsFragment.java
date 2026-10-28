/*
 * Copyright (C) 2018 The LineageOS Project
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

package org.lineageos.settings.touchsampling;

import android.os.Bundle;
import android.view.MenuItem;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceFragment;
import androidx.preference.SwitchPreferenceCompat;

import org.lineageos.settings.R;

public class TouchSamplingSettingsFragment extends PreferenceFragment implements
        OnPreferenceChangeListener {

    private SwitchPreferenceCompat mHTSRPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.htsr_settings);
        if (getActivity().getActionBar() != null) {
            getActivity().getActionBar().setDisplayHomeAsUpEnabled(true);
        }
        mHTSRPreference = (SwitchPreferenceCompat) findPreference(
                TouchSamplingUtils.HTSR_ENABLE_KEY);
        mHTSRPreference.setOnPreferenceChangeListener(this);
        updatePreferenceState();
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePreferenceState();
    }

    private void updatePreferenceState() {
        boolean supported = TouchSamplingUtils.isSupported();
        mHTSRPreference.setEnabled(supported);
        mHTSRPreference.setSummary(supported ? R.string.htsr_enable_summary
                : R.string.htsr_enable_summary_not_supported);
        mHTSRPreference.setChecked(supported && TouchSamplingUtils.isEnabled(getActivity()));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (TouchSamplingUtils.HTSR_ENABLE_KEY.equals(preference.getKey())) {
            boolean enabled = (Boolean) newValue;
            if (!TouchSamplingUtils.setEnabled(getActivity(), enabled)) {
                updatePreferenceState();
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getActivity().onBackPressed();
            return true;
        }
        return false;
    }
}
