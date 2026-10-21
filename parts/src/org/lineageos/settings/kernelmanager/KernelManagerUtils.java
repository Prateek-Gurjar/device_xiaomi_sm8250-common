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

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Helpers for the three cpufreq policies exposed by Snapdragon 865 (munch). */
public final class KernelManagerUtils {
    private static final String TAG = "KernelManager";
    private static final String CPUFREQ_PATH = "/sys/devices/system/cpu/cpufreq";
    private static final String PREFS_NAME = "munch_kernel_manager";
    private static final String PREF_APPLY_ON_BOOT = "apply_on_boot";
    private static final int[] POLICY_CPUS = {0, 4, 7};

    private KernelManagerUtils() {}

    public static int[] getPolicyCpus() {
        List<Integer> cpus = new ArrayList<>();
        for (int cpu : POLICY_CPUS) {
            if (new File(policyPath(cpu)).isDirectory()) cpus.add(cpu);
        }
        int[] result = new int[cpus.size()];
        for (int i = 0; i < cpus.size(); i++) result[i] = cpus.get(i);
        return result;
    }

    public static String getPolicyName(int cpu) {
        switch (cpu) {
            case 0: return "Efficiency cluster (CPU 0–3)";
            case 4: return "Performance cluster (CPU 4–6)";
            case 7: return "Prime cluster (CPU 7)";
            default: return "CPU policy " + cpu;
        }
    }

    public static List<String> getAvailableGovernors(int[] policies) {
        Set<String> common = null;
        for (int cpu : policies) {
            List<String> available = readWords(policyPath(cpu) + "/scaling_available_governors");
            if (available.isEmpty()) continue;
            if (common == null) common = new LinkedHashSet<>(available);
            else common.retainAll(available);
        }
        return common == null ? new ArrayList<String>() : new ArrayList<>(common);
    }

    public static List<String> getAvailableFrequencies(int cpu) {
        return readWords(policyPath(cpu) + "/scaling_available_frequencies");
    }

    public static String readValue(int cpu, String node, String fallback) {
        String value = readText(policyPath(cpu) + "/" + node);
        return value.isEmpty() ? fallback : value;
    }

    public static boolean apply(int[] policies, String governor, List<String[]> ranges) {
        if (policies.length != ranges.size()) return false;
        boolean success = true;
        for (int i = 0; i < policies.length; i++) {
            int cpu = policies[i];
            String base = policyPath(cpu);
            List<String> governors = readWords(base + "/scaling_available_governors");
            if (governors.contains(governor)) success &= writeValue(base + "/scaling_governor", governor);
            else success = false;

            String[] range = ranges.get(i);
            List<String> frequencies = getAvailableFrequencies(cpu);
            if (!frequencies.contains(range[0]) || !frequencies.contains(range[1])) {
                success = false;
                continue;
            }
            try {
                if (Long.parseLong(range[0]) > Long.parseLong(range[1])) {
                    success = false;
                    continue;
                }
            } catch (NumberFormatException e) {
                success = false;
                continue;
            }
            success &= writeFrequencyRange(base, range[0], range[1]);
        }
        return success;
    }

    public static boolean applySavedSettings(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(PREF_APPLY_ON_BOOT, false)) return true;
        int[] policies = getPolicyCpus();
        if (policies.length == 0) return false;
        String governor = prefs.getString("governor", "schedutil");
        List<String[]> ranges = new ArrayList<>();
        for (int cpu : policies) {
            List<String> frequencies = getAvailableFrequencies(cpu);
            if (frequencies.isEmpty()) return false;
            String min = prefs.getString("policy_" + cpu + "_min", frequencies.get(0));
            String max = prefs.getString("policy_" + cpu + "_max",
                    frequencies.get(frequencies.size() - 1));
            ranges.add(new String[]{min, max});
        }
        return apply(policies, governor, ranges);
    }

    public static void saveSettings(Context context, String governor, int[] policies,
            List<String[]> ranges) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME,
                Context.MODE_PRIVATE).edit().putString("governor", governor);
        for (int i = 0; i < policies.length; i++) {
            editor.putString("policy_" + policies[i] + "_min", ranges.get(i)[0]);
            editor.putString("policy_" + policies[i] + "_max", ranges.get(i)[1]);
        }
        editor.apply();
    }

    public static boolean isApplyOnBoot(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_APPLY_ON_BOOT, false);
    }

    public static void setApplyOnBoot(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(PREF_APPLY_ON_BOOT, enabled).apply();
    }

    private static boolean writeFrequencyRange(String base, String min, String max) {
        try {
            long currentMin = Long.parseLong(readText(base + "/scaling_min_freq"));
            long currentMax = Long.parseLong(readText(base + "/scaling_max_freq"));
            long targetMin = Long.parseLong(min);
            long targetMax = Long.parseLong(max);
            if (targetMin > currentMax) {
                return writeValue(base + "/scaling_max_freq", max)
                        && writeValue(base + "/scaling_min_freq", min);
            }
            if (targetMax < currentMin) {
                return writeValue(base + "/scaling_min_freq", min)
                        && writeValue(base + "/scaling_max_freq", max);
            }
            return writeValue(base + "/scaling_min_freq", min)
                    && writeValue(base + "/scaling_max_freq", max);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid current frequency range for " + base, e);
            return false;
        }
    }

    private static String policyPath(int cpu) {
        return CPUFREQ_PATH + "/policy" + cpu;
    }

    private static List<String> readWords(String path) {
        String text = readText(path);
        if (text.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(text.split("\\s+")));
    }

    private static String readText(String path) {
        try {
            return new String(Files.readAllBytes(new File(path).toPath()),
                    StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static boolean writeValue(String path, String value) {
        try {
            Files.write(new File(path).toPath(), value.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to write " + path, e);
            return false;
        }
    }
}
