/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.gpumanager;

import android.os.Bundle;
import android.widget.Toast;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;
import java.util.List;
import org.lineageos.settings.R;

public class GpuManagerFragment extends SettingsBasePreferenceFragment {
    private ListPreference mGovernor;
    private ListPreference mMin;
    private ListPreference mMax;
    private SwitchPreferenceCompat[] mToggles;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(getContext());
        setPreferenceScreen(screen);
        if (!GpuManagerUtils.isAvailable()) {
            Preference unavailable = new Preference(getContext());
            unavailable.setTitle(R.string.gpu_manager_unavailable);
            unavailable.setSelectable(false);
            screen.addPreference(unavailable);
            return;
        }

        PreferenceCategory info = category(screen, R.string.gpu_manager_info);
        addInfo(info, R.string.gpu_manager_model, GpuManagerUtils.read("gpu_model", "Unknown"));
        addInfo(info, R.string.gpu_manager_frequency,
                formatFrequency(GpuManagerUtils.read("gpuclk", "Unknown")));
        addInfo(info, R.string.gpu_manager_load,
                GpuManagerUtils.read("gpu_busy_percentage", "Unknown"));
        addInfo(info, R.string.gpu_manager_temperature,
                formatTemperature(GpuManagerUtils.read("temp", "")));
        addInfo(info, R.string.gpu_manager_thermal_level,
                GpuManagerUtils.read("thermal_pwrlevel", "Unknown"));

        List<String> governors = GpuManagerUtils.getGovernors();
        PreferenceCategory tuning = category(screen, R.string.gpu_manager_settings);
        mGovernor = list(tuning, "gpu_governor", R.string.gpu_manager_governor,
                governors.toArray(new String[0]), governors.toArray(new String[0]),
                GpuManagerUtils.readDevfreq("governor", ""));
        List<String> frequencies = GpuManagerUtils.getFrequencies();
        String[] values = frequencies.toArray(new String[0]);
        CharSequence[] entries = new CharSequence[values.length];
        for (int i = 0; i < values.length; i++) entries[i] = formatFrequency(values[i]);
        mMin = list(tuning, "gpu_min_frequency", R.string.gpu_manager_min_frequency,
                entries, values, GpuManagerUtils.readDevfreq("min_freq", values.length == 0 ? "" : values[0]));
        mMax = list(tuning, "gpu_max_frequency", R.string.gpu_manager_max_frequency,
                entries, values, GpuManagerUtils.readDevfreq("max_freq",
                        values.length == 0 ? "" : values[values.length - 1]));

        PreferenceCategory power = category(screen, R.string.gpu_manager_power);
        String[] keys = {"force_clk_on", "force_bus_on", "force_rail_on", "force_no_nap", "bus_split"};
        int[] titles = {R.string.gpu_manager_force_clk, R.string.gpu_manager_force_bus,
                R.string.gpu_manager_force_rail, R.string.gpu_manager_no_nap,
                R.string.gpu_manager_bus_split};
        mToggles = new SwitchPreferenceCompat[keys.length];
        for (int i = 0; i < keys.length; i++) {
            mToggles[i] = new SwitchPreferenceCompat(getContext());
            mToggles[i].setKey(keys[i]);
            mToggles[i].setTitle(titles[i]);
            mToggles[i].setPersistent(false);
            mToggles[i].setChecked("1".equals(GpuManagerUtils.read(keys[i], "0")));
            power.addPreference(mToggles[i]);
        }

        PreferenceCategory actions = category(screen, R.string.gpu_manager_actions);
        Preference apply = new Preference(getContext());
        apply.setTitle(R.string.gpu_manager_apply);
        apply.setOnPreferenceClickListener(preference -> applySettings());
        actions.addPreference(apply);
        SwitchPreferenceCompat applyOnBoot = new SwitchPreferenceCompat(getContext());
        applyOnBoot.setTitle(R.string.gpu_manager_apply_on_boot);
        applyOnBoot.setSummary(R.string.gpu_manager_apply_on_boot_summary);
        applyOnBoot.setChecked(GpuManagerUtils.isApplyOnBoot(getContext()));
        applyOnBoot.setOnPreferenceChangeListener((preference, value) -> {
            GpuManagerUtils.setApplyOnBoot(getContext(), (Boolean) value);
            return true;
        });
        actions.addPreference(applyOnBoot);
        Preference reset = new Preference(getContext());
        reset.setTitle(R.string.gpu_manager_reset);
        reset.setOnPreferenceClickListener(preference -> resetSettings());
        actions.addPreference(reset);
    }

    private PreferenceCategory category(PreferenceScreen screen, int title) {
        PreferenceCategory category = new PreferenceCategory(getContext());
        category.setTitle(title);
        screen.addPreference(category);
        return category;
    }

    private void addInfo(PreferenceCategory category, int title, String value) {
        Preference item = new Preference(getContext());
        item.setTitle(title);
        item.setSummary(value);
        item.setSelectable(false);
        category.addPreference(item);
    }

    private ListPreference list(PreferenceCategory category, String key, int title,
            CharSequence[] entries, CharSequence[] values, String current) {
        ListPreference preference = new ListPreference(getContext());
        preference.setKey(key);
        preference.setTitle(title);
        preference.setDialogTitle(title);
        preference.setEntries(entries);
        preference.setEntryValues(values);
        preference.setPersistent(false);
        preference.setEnabled(values.length > 0);
        preference.setValue(find(values, current) ? current : (values.length == 0 ? "" : values[0].toString()));
        preference.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        category.addPreference(preference);
        return preference;
    }

    private boolean applySettings() {
        String min = mMin.getValue(), max = mMax.getValue(), governor = mGovernor.getValue();
        try {
            if (Long.parseLong(min) > Long.parseLong(max)) {
                Toast.makeText(getContext(), R.string.gpu_manager_invalid_range, Toast.LENGTH_SHORT).show();
                return true;
            }
        } catch (RuntimeException e) {
            Toast.makeText(getContext(), R.string.gpu_manager_apply_failed, Toast.LENGTH_SHORT).show();
            return true;
        }
        boolean[] toggles = new boolean[mToggles.length];
        for (int i = 0; i < toggles.length; i++) toggles[i] = mToggles[i].isChecked();
        boolean applied = GpuManagerUtils.apply(getContext(), governor, min, max, toggles);
        Toast.makeText(getContext(), applied ? R.string.gpu_manager_applied
                : R.string.gpu_manager_apply_failed, Toast.LENGTH_SHORT).show();
        return true;
    }

    private boolean resetSettings() {
        List<String> frequencies = GpuManagerUtils.getFrequencies();
        List<String> governors = GpuManagerUtils.getGovernors();
        if (frequencies.isEmpty() || governors.isEmpty()) return true;
        String governor = governors.contains("msm-adreno-tz") ? "msm-adreno-tz" : governors.get(0);
        boolean[] defaults = {false, false, false, true, true};
        boolean ok = GpuManagerUtils.apply(getContext(), governor, frequencies.get(0),
                frequencies.get(frequencies.size() - 1), defaults);
        if (ok) {
            mGovernor.setValue(governor);
            mMin.setValue(frequencies.get(0));
            mMax.setValue(frequencies.get(frequencies.size() - 1));
            for (int i = 0; i < defaults.length; i++) mToggles[i].setChecked(defaults[i]);
        }
        Toast.makeText(getContext(), ok ? R.string.gpu_manager_reset_done
                : R.string.gpu_manager_apply_failed, Toast.LENGTH_SHORT).show();
        return true;
    }

    private static boolean find(CharSequence[] values, String target) {
        for (CharSequence value : values) if (value.toString().equals(target)) return true;
        return false;
    }

    private static String formatFrequency(String value) {
        try { return String.format(java.util.Locale.ROOT, "%.0f MHz", Long.parseLong(value) / 1000000.0); }
        catch (NumberFormatException e) { return value; }
    }

    private static String formatTemperature(String value) {
        try { return String.format(java.util.Locale.ROOT, "%.1f °C", Integer.parseInt(value) / 1000.0); }
        catch (NumberFormatException e) { return value.isEmpty() ? "Unknown" : value; }
    }
}
