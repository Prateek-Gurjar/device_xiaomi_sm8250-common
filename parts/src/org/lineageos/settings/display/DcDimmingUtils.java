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

package org.lineageos.settings.display;

import android.content.Context;
import android.content.Intent;
import androidx.preference.PreferenceManager;
import org.lineageos.settings.hbm.HBMUtils;
import org.lineageos.settings.utils.FileUtils;

public final class DcDimmingUtils {
    public static final String NODE = "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/dimlayer_exposure";

    private DcDimmingUtils() {}

    public static boolean isSupported() {
        return FileUtils.fileExists(NODE);
    }

    public static boolean setEnabled(Context context, boolean enabled) {
        if (!FileUtils.fileExists(NODE) || !FileUtils.writeLine(NODE, enabled ? "1" : "0")) {
            return false;
        }

        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(DcDimmingTileService.DC_DIMMING_ENABLE_KEY, enabled).apply();
        if (enabled) HBMUtils.disableForDcDimming(context);
        else context.sendBroadcast(new Intent(HBMUtils.ACTION_MODE_CHANGED)
                .setPackage(context.getPackageName()));
        return true;
    }
}
