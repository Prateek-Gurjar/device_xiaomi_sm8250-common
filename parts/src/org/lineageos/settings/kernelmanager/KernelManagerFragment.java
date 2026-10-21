/*
 * Copyright (C) 2026 The LineageOS Project
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

package org.lineageos.settings.kernelmanager;

import android.os.Bundle;
import android.widget.Toast;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;

import java.util.ArrayList;
import java.util.List;

public class KernelManagerFragment extends SettingsBasePreferenceFragment {
    private static final String GOVERNOR_KEY = "cpu_governor";
    private final List<PolicyPreferences> mPolicyPreferences = new ArrayList<>();
    private ListPreference mGovernorPreference;
    private int[] mPolicies;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(getContext());
        setPreferenceScreen(screen);
        mPolicies = KernelManagerUtils.getPolicyCpus();
        if (mPolicies.length == 0) {
            Preference unavailable = new Preference(getContext());
            unavailable.setTitle(R.string.kernel_manager_unavailable);
            unavailable.setSelectable(false);
            screen.addPreference(unavailable);
            return;
        }

        PreferenceCategory governorCategory = new PreferenceCategory(getContext());
        governorCategory.setTitle(R.string.cpu_governor_title);
        screen.addPreference(governorCategory);
        mGovernorPreference = new ListPreference(getContext());
        mGovernorPreference.setKey(GOVERNOR_KEY);
        mGovernorPreference.setTitle(R.string.cpu_governor_title);
        mGovernorPreference.setDialogTitle(R.string.cpu_governor_title);
        mGovernorPreference.setPersistent(false);
        List<String> governors = KernelManagerUtils.getAvailableGovernors(mPolicies);
        String[] governorValues = governors.toArray(new String[0]);
        mGovernorPreference.setEntries(governorValues);
        mGovernorPreference.setEntryValues(governorValues);
        String currentGovernor = KernelManagerUtils.readValue(mPolicies[0],
                "scaling_governor", governors.isEmpty() ? "" : governors.get(0));
        mGovernorPreference.setValue(governors.contains(currentGovernor)
                ? currentGovernor : (governors.isEmpty() ? "" : governors.get(0)));
        mGovernorPreference.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        mGovernorPreference.setEnabled(!governors.isEmpty());
        governorCategory.addPreference(mGovernorPreference);

        for (int cpu : mPolicies) addPolicyPreferences(screen, cpu);

        PreferenceCategory actions = new PreferenceCategory(getContext());
        actions.setTitle(R.string.kernel_manager_actions);
        screen.addPreference(actions);

        Preference apply = new Preference(getContext());
        apply.setTitle(R.string.kernel_manager_apply);
        apply.setSummary(R.string.kernel_manager_apply_summary);
        apply.setOnPreferenceClickListener(preference -> applySettings());
        actions.addPreference(apply);

        Preference reset = new Preference(getContext());
        reset.setTitle(R.string.kernel_manager_reset);
        reset.setSummary(R.string.kernel_manager_reset_summary);
        reset.setOnPreferenceClickListener(preference -> resetSettings());
        actions.addPreference(reset);

        SwitchPreferenceCompat applyOnBoot = new SwitchPreferenceCompat(getContext());
        applyOnBoot.setTitle(R.string.kernel_manager_apply_on_boot);
        applyOnBoot.setSummary(R.string.kernel_manager_apply_on_boot_summary);
        applyOnBoot.setChecked(KernelManagerUtils.isApplyOnBoot(getContext()));
        applyOnBoot.setOnPreferenceChangeListener((preference, value) -> {
            KernelManagerUtils.setApplyOnBoot(getContext(), (Boolean) value);
            return true;
        });
        actions.addPreference(applyOnBoot);

        Preference footer = new Preference(getContext());
        footer.setTitle(R.string.kernel_manager_description);
        footer.setSelectable(false);
        actions.addPreference(footer);
    }

    private void addPolicyPreferences(PreferenceScreen screen, int cpu) {
        List<String> frequencies = KernelManagerUtils.getAvailableFrequencies(cpu);
        PreferenceCategory category = new PreferenceCategory(getContext());
        category.setTitle(KernelManagerUtils.getPolicyName(cpu));
        screen.addPreference(category);
        if (frequencies.isEmpty()) {
            Preference unavailable = new Preference(getContext());
            unavailable.setTitle(R.string.kernel_manager_frequency_unavailable);
            unavailable.setSelectable(false);
            category.addPreference(unavailable);
            return;
        }

        String[] values = frequencies.toArray(new String[0]);
        CharSequence[] entries = new CharSequence[values.length];
        for (int i = 0; i < values.length; i++) entries[i] = formatFrequency(values[i]);
        ListPreference min = createFrequencyPreference(cpu, "min", R.string.cpu_min_freq_title,
                values, entries, KernelManagerUtils.readValue(cpu, "scaling_min_freq", values[0]));
        ListPreference max = createFrequencyPreference(cpu, "max", R.string.cpu_max_freq_title,
                values, entries, KernelManagerUtils.readValue(cpu, "scaling_max_freq",
                        values[values.length - 1]));
        category.addPreference(min);
        category.addPreference(max);
        mPolicyPreferences.add(new PolicyPreferences(cpu, min, max, values));
    }

    private ListPreference createFrequencyPreference(int cpu, String bound, int title,
            String[] values, CharSequence[] entries, String current) {
        ListPreference preference = new ListPreference(getContext());
        preference.setKey("policy_" + cpu + "_" + bound);
        preference.setTitle(title);
        preference.setDialogTitle(title);
        preference.setEntries(entries);
        preference.setEntryValues(values);
        preference.setPersistent(false);
        preference.setValue(contains(values, current) ? current
                : ("min".equals(bound) ? values[0] : values[values.length - 1]));
        preference.setSummaryProvider(preference1 -> formatFrequency(
                ((ListPreference) preference1).getValue()));
        return preference;
    }

    private boolean applySettings() {
        List<String[]> ranges = collectRanges();
        if (ranges == null) return false;
        int[] managedPolicies = getManagedPolicies();
        String governor = mGovernorPreference.getValue();
        if (governor == null || governor.isEmpty()) {
            Toast.makeText(getContext(), R.string.kernel_manager_apply_failed,
                    Toast.LENGTH_SHORT).show();
            return true;
        }
        if (KernelManagerUtils.apply(managedPolicies, governor, ranges)) {
            KernelManagerUtils.saveSettings(getContext(), governor, managedPolicies, ranges);
            Toast.makeText(getContext(), R.string.kernel_manager_applied, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), R.string.kernel_manager_apply_failed,
                    Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private boolean resetSettings() {
        int[] managedPolicies = getManagedPolicies();
        List<String[]> ranges = new ArrayList<>();
        for (PolicyPreferences policy : mPolicyPreferences) {
            ranges.add(new String[]{policy.frequencies[0],
                    policy.frequencies[policy.frequencies.length - 1]});
        }
        List<String> governors = KernelManagerUtils.getAvailableGovernors(managedPolicies);
        String governor = governors.contains("schedutil") ? "schedutil"
                : (governors.isEmpty() ? "" : governors.get(0));
        if (governor.isEmpty() || !KernelManagerUtils.apply(managedPolicies, governor, ranges)) {
            Toast.makeText(getContext(), R.string.kernel_manager_apply_failed,
                    Toast.LENGTH_SHORT).show();
            return true;
        }
        mGovernorPreference.setValue(governor);
        for (int i = 0; i < mPolicyPreferences.size(); i++) {
            PolicyPreferences policy = mPolicyPreferences.get(i);
            policy.min.setValue(ranges.get(i)[0]);
            policy.max.setValue(ranges.get(i)[1]);
        }
        KernelManagerUtils.saveSettings(getContext(), governor, managedPolicies, ranges);
        Toast.makeText(getContext(), R.string.kernel_manager_reset_done, Toast.LENGTH_SHORT).show();
        return true;
    }

    private List<String[]> collectRanges() {
        List<String[]> ranges = new ArrayList<>();
        for (PolicyPreferences policy : mPolicyPreferences) {
            String min = policy.min.getValue();
            String max = policy.max.getValue();
            if (!contains(policy.frequencies, min) || !contains(policy.frequencies, max)
                    || Long.parseLong(min) > Long.parseLong(max)) {
                Toast.makeText(getContext(), R.string.kernel_manager_invalid_range,
                        Toast.LENGTH_SHORT).show();
                return null;
            }
            ranges.add(new String[]{min, max});
        }
        return ranges;
    }

    private int[] getManagedPolicies() {
        int[] policies = new int[mPolicyPreferences.size()];
        for (int i = 0; i < policies.length; i++) policies[i] = mPolicyPreferences.get(i).cpu;
        return policies;
    }

    private static boolean contains(String[] values, String value) {
        if (value == null) return false;
        for (String candidate : values) if (candidate.equals(value)) return true;
        return false;
    }

    private static String formatFrequency(String frequency) {
        if (frequency == null || frequency.isEmpty()) return "Unknown";
        try {
            return (Long.parseLong(frequency) / 1000) + " MHz";
        } catch (NumberFormatException e) {
            return frequency;
        }
    }

    private static final class PolicyPreferences {
        final int cpu;
        final ListPreference min;
        final ListPreference max;
        final String[] frequencies;

        PolicyPreferences(int cpu, ListPreference min, ListPreference max, String[] frequencies) {
            this.cpu = cpu;
            this.min = min;
            this.max = max;
            this.frequencies = frequencies;
        }
    }
}
