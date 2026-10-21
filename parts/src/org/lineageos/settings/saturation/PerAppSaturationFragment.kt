/* Copyright (C) 2025 The LineageOS Project - SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.saturation

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import org.lineageos.settings.Constants
import org.lineageos.settings.R

class PerAppSaturationFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val screen = preferenceManager.createPreferenceScreen(requireContext())
        val category = PreferenceCategory(requireContext()).apply {
            title = getString(R.string.per_app_saturation_app_list)
        }
        screen.addPreference(category)
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val disabled = prefs.getStringSet(
            Constants.KEY_PER_APP_SATURATION_DISABLED_PACKAGES, emptySet()
        )?.toMutableSet() ?: mutableSetOf()
        val pm = requireContext().packageManager
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
            .forEach { app ->
                val item = SwitchPreferenceCompat(requireContext()).apply {
                    key = "per_app_saturation_app_${app.packageName}"
                    title = app.loadLabel(pm)
                    summary = app.packageName
                    icon = app.loadIcon(pm)
                    isChecked = app.packageName !in disabled
                    onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
                        if (value as Boolean) disabled.remove(app.packageName)
                        else disabled.add(app.packageName)
                        prefs.edit().putStringSet(
                            Constants.KEY_PER_APP_SATURATION_DISABLED_PACKAGES,
                            HashSet(disabled)
                        ).apply()
                        true
                    }
                }
                category.addPreference(item)
            }
        preferenceScreen = screen
    }
}
