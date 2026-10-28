/*
 * Copyright (C) 2016 The OmniROM Project
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.lineageos.settings.hbm;

import android.content.Context;

import androidx.preference.Preference;

public class HBMModeSwitch implements Preference.OnPreferenceChangeListener {
    private final Context mContext;

    public HBMModeSwitch(Context context) {
        mContext = context;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return HBMUtils.setManualEnabled(mContext, (Boolean) newValue);
    }
}
