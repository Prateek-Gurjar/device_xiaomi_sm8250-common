/* Copyright (C) 2026 The LineageOS Project - SPDX-License-Identifier: Apache-2.0 */
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
import java.util.HashSet
import java.util.Locale

class PerAppSaturationFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        rebuildAppList()
    }

    override fun onResume() {
        super.onResume()
        rebuildAppList()
    }

    private fun rebuildAppList() {
        val context = requireContext()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val disabledPackages = prefs.getStringSet(
            Constants.KEY_PER_APP_SATURATION_DISABLED_PACKAGES, emptySet()
        )?.toMutableSet() ?: mutableSetOf()
        val packageManager = context.packageManager
        val screen = preferenceManager.createPreferenceScreen(context)
        val category = PreferenceCategory(context).apply {
            title = getString(R.string.per_app_saturation_app_list)
        }
        screen.addPreference(category)

        packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.enabled && packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase(Locale.ROOT) }
            .forEach { app ->
                val item = SwitchPreferenceCompat(context).apply {
                    key = "per_app_saturation_app_${app.packageName}"
                    title = app.loadLabel(packageManager)
                    summary = app.packageName
                    icon = app.loadIcon(packageManager)
                    isPersistent = false
                    isChecked = app.packageName !in disabledPackages
                    onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
                        if (value as Boolean) disabledPackages.remove(app.packageName)
                        else disabledPackages.add(app.packageName)
                        prefs.edit().putStringSet(
                            Constants.KEY_PER_APP_SATURATION_DISABLED_PACKAGES,
                            HashSet(disabledPackages)
                        ).apply()
                        true
                    }
                }
                category.addPreference(item)
            }
        preferenceScreen = screen
    }
}
