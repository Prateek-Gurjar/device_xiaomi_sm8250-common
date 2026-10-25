/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.batterystats;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.preference.PreferenceManager;

public class BatteryStatsResetReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_POWER_CONNECTED.equals(intent.getAction())
                && PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                        BatteryStatsMonitorService.PREF_AUTO_RESET, true)
                && PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                        BatteryStatsMonitorService.PREF_RESET_ON_CHARGER, true)) {
            BatteryStatsMonitorService.resetStoredSamples(context);
        }
    }
}
