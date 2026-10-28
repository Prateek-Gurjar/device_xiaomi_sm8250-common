/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.extras;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.R;

/** A single Settings entry point for the device specific controls. */
public class ExtrasFragment extends SettingsBasePreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(requireContext()));

        PreferenceCategory display = addCategory(R.string.extras_display_category);
        addEntry(display, R.string.refresh_title, R.string.refresh_summary,
                R.drawable.ic_per_app_refresh_rate, "org.lineageos.settings",
                "org.lineageos.settings.refreshrate.RefreshActivity");
        addEntry(display, R.string.saturation_title, R.string.saturation_summary,
                R.drawable.ic_saturation_tile, "org.lineageos.settings",
                "org.lineageos.settings.saturation.SaturationActivity");
        addEntry(display, R.string.hbm_fragment_title, R.string.hbm_fragment_summary,
                R.drawable.ic_hbm_tile, "org.lineageos.settings",
                "org.lineageos.settings.hbm.HBMActivity");
        addEntry(display, R.string.dc_dimming_enable_title, R.string.dc_dimming_enable_summary,
                R.drawable.ic_dc_tile, "org.lineageos.settings",
                "org.lineageos.settings.display.DcDimmingSettingsActivity");
        addEntry(display, R.string.htsr_title, R.string.htsr_enable_summary,
                R.drawable.ic_htsr, "org.lineageos.settings",
                "org.lineageos.settings.touchsampling.TouchSamplingSettingsActivity");
        addEntry(display, R.string.extras_pocket_mode, R.string.extras_pocket_summary,
                R.drawable.ic_pocket, "io.github.maytinhdibo.pocket",
                "io.github.maytinhdibo.pocket.PocketPreferenceActivity");

        PreferenceCategory performance = addCategory(R.string.extras_performance_category);
        addEntry(performance, R.string.thermal_title, R.string.thermal_summary,
                R.drawable.ic_settings_thermal, "org.lineageos.settings",
                "org.lineageos.settings.thermal.ThermalActivity");
        addEntry(performance, R.string.core_control_title, R.string.core_control_summary,
                R.drawable.ic_settings_core_control, "org.lineageos.settings",
                "org.lineageos.settings.corecontrol.CoreControlActivity");
        addEntry(performance, R.string.kernel_manager_title, R.string.kernel_manager_summary,
                R.drawable.ic_cpu_manager_tile, "org.lineageos.settings",
                "org.lineageos.settings.kernelmanager.KernelManagerActivity");
        addEntry(performance, R.string.gpu_manager_title, R.string.gpu_manager_summary,
                R.drawable.ic_gpu_manager_tile, "org.lineageos.settings",
                "org.lineageos.settings.gpumanager.GpuManagerActivity");
        addEntry(performance, R.string.extras_kprofiles, R.string.extras_kprofiles_summary,
                R.drawable.ic_kprofiles, "com.android.kprofiles",
                "com.android.kprofiles.battery.KprofilesSettingsActivity");

        PreferenceCategory sound = addCategory(R.string.extras_sound_category);
        addEntry(sound, R.string.dirac_title, R.string.dirac_summary,
                R.drawable.ic_dirac_tile, "org.lineageos.settings",
                "org.lineageos.settings.dirac.DiracActivity");
        addEntry(sound, R.string.clear_speaker_title, R.string.clear_speaker_summary,
                R.drawable.ic_clear_speaker, "org.lineageos.settings",
                "org.lineageos.settings.speaker.ClearSpeakerActivity");

        PreferenceCategory battery = addCategory(R.string.extras_battery_category);
        addEntry(battery, R.string.battery_monitor_title, R.string.battery_monitor_summary,
                R.drawable.ic_battery_monitor_tile, "org.lineageos.settings",
                "org.lineageos.settings.batterymonitor.BatteryMonitorActivity");
        addEntry(battery, R.string.charge_bypass_title, R.string.charge_bypass_summary,
                R.drawable.ic_bypass_charging_tile, "org.lineageos.settings",
                "org.lineageos.settings.charge.ChargeActivity");
    }

    private PreferenceCategory addCategory(int titleRes) {
        PreferenceCategory category = new PreferenceCategory(requireContext());
        category.setTitle(titleRes);
        getPreferenceScreen().addPreference(category);
        return category;
    }

    private void addEntry(PreferenceCategory category, int titleRes, int summaryRes,
            int iconRes, String packageName, String className) {
        Preference preference = new Preference(requireContext());
        preference.setTitle(titleRes);
        preference.setSummary(summaryRes);
        preference.setIcon(iconRes);

        ComponentName component = new ComponentName(packageName, className);
        try {
            getContext().getPackageManager().getActivityInfo(component,
                    PackageManager.MATCH_DISABLED_COMPONENTS);
            preference.setOnPreferenceClickListener(clicked -> {
                Intent intent = new Intent().setComponent(component);
                startActivity(intent);
                return true;
            });
        } catch (PackageManager.NameNotFoundException e) {
            preference.setEnabled(false);
            preference.setSummary(R.string.extras_item_unavailable);
        }
        category.addPreference(preference);
    }
}
