/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.batterystats;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import org.lineageos.settings.R;

public class BatteryStatsSettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int REQUEST_POST_NOTIFICATIONS = 10;
    private SwitchPreference notificationPreference;
    private SwitchPreference autoResetPreference;
    private Preference resetOnChargerPreference;
    private Preference resetOnRebootPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.battery_stats_settings, rootKey);
        notificationPreference = findPreference(
                BatteryStatsMonitorService.PREF_NOTIFICATION_ENABLED);
        notificationPreference.setOnPreferenceChangeListener(this);
        autoResetPreference = findPreference(BatteryStatsMonitorService.PREF_AUTO_RESET);
        autoResetPreference.setOnPreferenceChangeListener(this);
        resetOnChargerPreference = findPreference(
                BatteryStatsMonitorService.PREF_RESET_ON_CHARGER);
        resetOnRebootPreference = findPreference(
                BatteryStatsMonitorService.PREF_RESET_ON_REBOOT);
        setResetOptionsVisible(autoResetPreference.isChecked());
    }

    @Override
    public void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (BatteryStatsMonitorService.PREF_AUTO_RESET.equals(preference.getKey())) {
            setResetOptionsVisible((Boolean) newValue);
            return true;
        }
        if (!BatteryStatsMonitorService.PREF_NOTIFICATION_ENABLED.equals(preference.getKey())) {
            return true;
        }
        boolean enabled = (Boolean) newValue;
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS },
                    REQUEST_POST_NOTIFICATIONS);
            return false;
        }
        if (enabled) {
            BatteryStatsMonitorService.start(requireContext());
        } else {
            BatteryStatsMonitorService.stop(requireContext());
        }
        return true;
    }

    private void setResetOptionsVisible(boolean visible) {
        resetOnChargerPreference.setVisible(visible);
        resetOnRebootPreference.setVisible(visible);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_POST_NOTIFICATIONS || grantResults.length == 0
                || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                .putBoolean(BatteryStatsMonitorService.PREF_NOTIFICATION_ENABLED, true).apply();
        notificationPreference.setChecked(true);
        BatteryStatsMonitorService.start(requireContext());
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key != null && key.startsWith("battery_stats_show_")
                && sharedPreferences.getBoolean(
                        BatteryStatsMonitorService.PREF_NOTIFICATION_ENABLED, false)) {
            BatteryStatsMonitorService.refresh(requireContext());
        }
    }
}
