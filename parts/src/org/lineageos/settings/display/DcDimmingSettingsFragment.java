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

package org.lineageos.settings.display;

import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceManager;

import androidx.preference.SwitchPreference;

import org.lineageos.settings.R;
import org.lineageos.settings.utils.FileUtils;

import org.lineageos.settings.hbm.HBMController;

public class DcDimmingSettingsFragment extends PreferenceFragment implements
        OnPreferenceChangeListener {

    private SwitchPreference mDcDimmingPreference;
    private static final String DC_DIMMING_ENABLE_KEY = "dc_dimming_enable";
    private static final String DC_DIMMING_NODE = "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/dimlayer_exposure";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.dcdimming_settings, rootKey);
        mDcDimmingPreference = findPreference(DC_DIMMING_ENABLE_KEY);
        if (FileUtils.fileExists(DC_DIMMING_NODE)) {
            mDcDimmingPreference.setEnabled(true);
            mDcDimmingPreference.setOnPreferenceChangeListener(this);
        } else {
            mDcDimmingPreference.setSummary(R.string.dc_dimming_enable_summary_not_supported);
            mDcDimmingPreference.setEnabled(false);
        }
        mDcDimmingPreference.setChecked(PreferenceManager.getDefaultSharedPreferences(getContext())
                .getBoolean(DC_DIMMING_ENABLE_KEY, false));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (DC_DIMMING_ENABLE_KEY.equals(preference.getKey())) {
            boolean enabled = (boolean) newValue;
            if (enabled && !HBMController.disableAll(getContext())) return false;
            if (!FileUtils.writeLine(DC_DIMMING_NODE, enabled ? "1" : "0")) return false;
            PreferenceManager.getDefaultSharedPreferences(getContext()).edit()
                    .putBoolean(DC_DIMMING_ENABLE_KEY, enabled).apply();
        }
        return true;
    }
}
