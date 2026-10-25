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

package org.lineageos.settings.charge;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;
import org.lineageos.settings.utils.FileUtils;

public class ChargeUtils {

    private static final String TAG = "ChargeUtils";

    // battery_input_suspend votes battery charge current to zero. input_suspend
    // suspends charger input and is not the bypass charging control.
    public static final String BYPASS_CHARGE_NODE =
            "/sys/class/power_supply/battery/battery_input_suspend";
    private static final String BATTERY_TEMP_NODE = "/sys/class/power_supply/battery/temp";
    private static final String BATTERY_CAPACITY_NODE = "/sys/class/power_supply/battery/capacity";

    private static final int MAX_BATTERY_TEMP = 450;
    private static final int MIN_BATTERY_CAPACITY = 20;

    private final Context context;

    public ChargeUtils(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Returns null if the node cannot be read or contains an invalid value. */
    public Boolean getBypassChargeState() {
        String value = FileUtils.readOneLine(BYPASS_CHARGE_NODE);
        if ("0".equals(value)) return false;
        if ("1".equals(value)) return true;
        return null;
    }

    public boolean isBypassChargeEnabled() {
        return Boolean.TRUE.equals(getBypassChargeState());
    }

    /** Writes and verifies the requested state. */
    public boolean enableBypassCharge(boolean enable) {
        if (enable) {
            SafetyCheckResult safetyCheck = performSafetyChecks();
            if (!safetyCheck.isSafe()) {
                Log.w(TAG, "Safety check failed: " + safetyCheck.getReason());
                return false;
            }
        }

        if (!FileUtils.writeLine(BYPASS_CHARGE_NODE, enable ? "1" : "0")) {
            Log.e(TAG, "Failed to write bypass charge status");
            return false;
        }

        Boolean state = getBypassChargeState();
        if (state == null || state != enable) {
            Log.e(TAG, "Bypass charge status did not match requested state");
            return false;
        }
        return true;
    }

    private boolean isNodeAccessible(String node) {
        return FileUtils.isFileReadable(node) && FileUtils.isFileWritable(node);
    }

    public boolean isBypassChargeSupported() {
        return isNodeAccessible(BYPASS_CHARGE_NODE);
    }

    public SafetyCheckResult performSafetyChecks() {
        if (!isBypassChargeSupported()) {
            return new SafetyCheckResult(false, "Bypass charging is not supported on this device");
        }

        if (!isExternalChargerConnected()) {
            return new SafetyCheckResult(false, "Connect a charger before enabling bypass charging");
        }

        int batteryTemp = getBatteryTemperature();
        if (batteryTemp < 0) {
            return new SafetyCheckResult(false, "Unable to read battery temperature");
        }
        if (batteryTemp >= MAX_BATTERY_TEMP) {
            return new SafetyCheckResult(false,
                    String.format("Battery temperature too high (%.1f°C)", batteryTemp / 10.0f));
        }

        int batteryLevel = getBatteryCapacity();
        if (batteryLevel < 0 || batteryLevel > 100) {
            return new SafetyCheckResult(false, "Unable to read battery level");
        }
        if (batteryLevel < MIN_BATTERY_CAPACITY) {
            return new SafetyCheckResult(false,
                    String.format("Battery level too low (%d%%)", batteryLevel));
        }

        return new SafetyCheckResult(true, "All safety checks passed");
    }

    private boolean isExternalChargerConnected() {
        Intent batteryStatus = context.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryStatus == null) return false;

        int plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        int supportedSources = BatteryManager.BATTERY_PLUGGED_AC
                | BatteryManager.BATTERY_PLUGGED_USB
                | BatteryManager.BATTERY_PLUGGED_WIRELESS;
        return (plugged & supportedSources) != 0;
    }

    private int getBatteryTemperature() {
        int temperature = readInteger(BATTERY_TEMP_NODE);
        if (temperature > 0) return temperature;

        Intent batteryStatus = context.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryStatus == null) return -1;

        temperature = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        return temperature > 0 ? temperature : -1;
    }

    private int getBatteryCapacity() {
        int capacity = readInteger(BATTERY_CAPACITY_NODE);
        if (capacity >= 0 && capacity <= 100) return capacity;

        BatteryManager batteryManager =
                (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (batteryManager == null) return -1;

        capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        return capacity >= 0 && capacity <= 100 ? capacity : -1;
    }

    private int readInteger(String node) {
        String value = FileUtils.readOneLine(node);
        if (value == null) return -1;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            Log.e(TAG, "Failed to parse value from " + node, e);
            return -1;
        }
    }

    public static class SafetyCheckResult {
        private final boolean safe;
        private final String reason;

        public SafetyCheckResult(boolean safe, String reason) {
            this.safe = safe;
            this.reason = reason;
        }

        public boolean isSafe() { return safe; }
        public String getReason() { return reason; }
    }
}
