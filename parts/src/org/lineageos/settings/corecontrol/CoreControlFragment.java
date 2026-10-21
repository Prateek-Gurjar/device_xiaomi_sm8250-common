/*
 * Copyright (C) 2025 The LineageOS Project
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

package org.lineageos.settings.corecontrol;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.BitSet;

public class CoreControlFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    private static final String TAG = "CoreControlFragment";
    private static final int NUM_CORES = 8;
    private static final int FIRST_LITTLE_CORE = 0;
    private static final int LAST_LITTLE_CORE = 3;
    private static final int MIN_ONLINE_LITTLE_CORES = 2;

    private final TwoStatePreference[] mCorePrefs = new TwoStatePreference[NUM_CORES];

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.core_control_settings);
        for (int core = 0; core < NUM_CORES; core++) {
            TwoStatePreference preference = (TwoStatePreference) findPreference("core_" + core);
            mCorePrefs[core] = preference;
            if (preference != null) {
                preference.setOnPreferenceChangeListener(this);
            }
        }
        refreshCoreStates();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshCoreStates();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        int core = findCore(preference);
        if (core < 0 || !(newValue instanceof Boolean)) {
            return false;
        }

        boolean online = (Boolean) newValue;
        if (!online && !canOffline(core)) {
            Toast.makeText(getContext(), R.string.core_control_min_little_cores,
                    Toast.LENGTH_SHORT).show();
            return false;
        }

        if (!writeCoreState(core, online)) {
            Toast.makeText(getContext(), R.string.core_control_write_failed,
                    Toast.LENGTH_SHORT).show();
            refreshCoreStates();
            return false;
        }
        return true;
    }

    private int findCore(Preference preference) {
        for (int core = 0; core < NUM_CORES; core++) {
            if (preference == mCorePrefs[core]) {
                return core;
            }
        }
        return -1;
    }

    private void refreshCoreStates() {
        BitSet onlineCpus = readOnlineCpus();
        for (int core = 0; core < NUM_CORES; core++) {
            TwoStatePreference preference = mCorePrefs[core];
            if (preference == null) {
                continue;
            }

            File onlineNode = new File(cpuOnlinePath(core));
            boolean stateKnown = onlineCpus != null;
            preference.setChecked(stateKnown && onlineCpus.get(core));
            // CPU 0 is not hotpluggable on this platform. Disable controls for
            // nodes the running kernel does not expose or permit writing.
            preference.setEnabled(core != 0 && stateKnown && onlineNode.canWrite());
        }
    }

    private boolean canOffline(int core) {
        if (core == 0) {
            return false;
        }
        if (core < FIRST_LITTLE_CORE || core > LAST_LITTLE_CORE) {
            return true;
        }

        BitSet onlineCpus = readOnlineCpus();
        if (onlineCpus == null) {
            return false;
        }

        int remainingLittleCores = 0;
        for (int little = FIRST_LITTLE_CORE; little <= LAST_LITTLE_CORE; little++) {
            if (little != core && onlineCpus.get(little)) {
                remainingLittleCores++;
            }
        }
        return remainingLittleCores >= MIN_ONLINE_LITTLE_CORES;
    }

    private BitSet readOnlineCpus() {
        String onlineList = readFile("/sys/devices/system/cpu/online");
        if (onlineList.isEmpty()) {
            return null;
        }

        BitSet onlineCpus = new BitSet(NUM_CORES);
        try {
            for (String range : onlineList.split(",")) {
                String[] bounds = range.trim().split("-");
                int first = Integer.parseInt(bounds[0]);
                int last = bounds.length == 1 ? first : Integer.parseInt(bounds[1]);
                if (first < 0 || last >= NUM_CORES || first > last) {
                    return null;
                }
                onlineCpus.set(first, last + 1);
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid online CPU list: " + onlineList, e);
            return null;
        }
        return onlineCpus;
    }

    private String cpuOnlinePath(int core) {
        return "/sys/devices/system/cpu/cpu" + core + "/online";
    }

    private boolean writeCoreState(int core, boolean online) {
        try {
            Files.write(new File(cpuOnlinePath(core)).toPath(),
                    (online ? "1" : "0").getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to set CPU " + core + " online=" + online, e);
            return false;
        }
    }

    private String readFile(String path) {
        try {
            return new String(Files.readAllBytes(new File(path).toPath()),
                    StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            Log.e(TAG, "Failed to read " + path, e);
            return "";
        }
    }
}
