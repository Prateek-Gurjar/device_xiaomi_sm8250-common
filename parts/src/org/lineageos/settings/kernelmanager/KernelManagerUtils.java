/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Helpers for runtime-discovered CPU frequency policies. */
public final class KernelManagerUtils {
    private static final String TAG = "KernelManager";
    private static final String CPUFREQ_PATH = "/sys/devices/system/cpu/cpufreq";
    private static final String PREFS_NAME = "munch_kernel_manager";
    private static final String PREF_APPLY_ON_BOOT = "apply_on_boot";
    private static final String PREF_GOVERNOR = "governor";

    private KernelManagerUtils() {}

    public static int[] getPolicyCpus() {
        File[] entries = new File(CPUFREQ_PATH).listFiles();
        if (entries == null) return new int[0];
        List<Integer> cpus = new ArrayList<>();
        for (File entry : entries) {
            String name = entry.getName();
            if (!entry.isDirectory() || !name.matches("policy[0-9]+")) continue;
            try {
                cpus.add(Integer.parseInt(name.substring("policy".length())));
            } catch (NumberFormatException e) {
                Log.w(TAG, "Ignoring invalid cpufreq policy directory: " + name);
            }
        }
        Collections.sort(cpus);
        int[] result = new int[cpus.size()];
        for (int i = 0; i < cpus.size(); i++) result[i] = cpus.get(i);
        return result;
    }

    public static String getPolicyName(int cpu) {
        String related = readText(policyPath(cpu) + "/related_cpus");
        if (related.isEmpty()) related = readText(policyPath(cpu) + "/affected_cpus");
        List<String> cpus = words(related);
        if (cpus.isEmpty()) return "CPU policy " + cpu;
        if (cpus.size() == 1) return "CPU " + cpus.get(0) + " policy";
        return "CPU " + cpus.get(0) + "–" + cpus.get(cpus.size() - 1)
                + " policy (" + cpus.size() + " CPUs)";
    }

    public static List<String> getAvailableGovernors(int[] policies) {
        if (policies == null || policies.length == 0) return Collections.emptyList();
        Set<String> common = null;
        for (int cpu : policies) {
            List<String> available = readWords(policyPath(cpu) + "/scaling_available_governors");
            if (available.isEmpty()) {
                Log.w(TAG, "Unable to read available governors for policy" + cpu);
                return Collections.emptyList();
            }
            if (common == null) common = new LinkedHashSet<>(available);
            else common.retainAll(available);
        }
        return common == null ? Collections.<String>emptyList() : new ArrayList<>(common);
    }

    public static List<String> getAvailableFrequencies(int cpu) {
        List<String> frequencies = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>();
        for (String token : readWords(policyPath(cpu) + "/scaling_available_frequencies")) {
            try {
                long frequency = Long.parseLong(token);
                if (frequency > 0) unique.add(Long.toString(frequency));
            } catch (NumberFormatException e) {
                Log.w(TAG, "Ignoring invalid frequency for policy" + cpu + ": " + token);
            }
        }
        frequencies.addAll(unique);
        Collections.sort(frequencies, new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                return Long.compare(Long.parseLong(left), Long.parseLong(right));
            }
        });
        return frequencies;
    }

    public static String readValue(int cpu, String node, String fallback) {
        String value = readText(policyPath(cpu) + "/" + node);
        return value.isEmpty() ? fallback : value;
    }

    public static boolean apply(int[] policies, String governor, List<String[]> ranges) {
        if (policies == null || ranges == null || policies.length == 0
                || policies.length != ranges.size() || governor == null || governor.isEmpty()) {
            return false;
        }

        // Validate every requested value before changing any policy.
        for (int i = 0; i < policies.length; i++) {
            String base = policyPath(policies[i]);
            String[] range = ranges.get(i);
            if (range == null || range.length != 2
                    || !readWords(base + "/scaling_available_governors").contains(governor)) {
                Log.e(TAG, "Invalid governor or range for policy" + policies[i]);
                return false;
            }
            List<String> frequencies = getAvailableFrequencies(policies[i]);
            if (!frequencies.contains(range[0]) || !frequencies.contains(range[1])) {
                Log.e(TAG, "Requested frequency is no longer available for policy" + policies[i]);
                return false;
            }
            try {
                if (Long.parseLong(range[0]) > Long.parseLong(range[1])) return false;
            } catch (NumberFormatException e) {
                Log.e(TAG, "Invalid frequency range for policy" + policies[i], e);
                return false;
            }
        }

        boolean success = true;
        for (int i = 0; i < policies.length; i++) {
            int cpu = policies[i];
            String base = policyPath(cpu);
            String[] range = ranges.get(i);
            boolean policySuccess = writeValue(base + "/scaling_governor", governor);
            policySuccess &= governor.equals(readValue(cpu, "scaling_governor", ""));
            policySuccess &= writeFrequencyRange(base, range[0], range[1]);
            policySuccess &= range[0].equals(readValue(cpu, "scaling_min_freq", ""));
            policySuccess &= range[1].equals(readValue(cpu, "scaling_max_freq", ""));
            if (!policySuccess) Log.e(TAG, "Failed to apply CPU settings to policy" + cpu);
            success &= policySuccess;
        }
        return success;
    }

    public static boolean applySavedSettings(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(PREF_APPLY_ON_BOOT, false)) return true;
        int[] policies = getPolicyCpus();
        if (policies.length == 0) {
            Log.e(TAG, "No CPU frequency policies found while restoring settings");
            return false;
        }

        List<String> governors = getAvailableGovernors(policies);
        if (governors.isEmpty()) {
            Log.e(TAG, "No common CPU governor is available while restoring settings");
            return false;
        }
        String governor = prefs.getString(PREF_GOVERNOR, "");
        if (!governors.contains(governor)) {
            governor = governors.contains("schedutil") ? "schedutil" : governors.get(0);
            Log.w(TAG, "Saved governor unavailable; using " + governor);
        }

        List<String[]> ranges = new ArrayList<>();
        for (int cpu : policies) {
            List<String> frequencies = getAvailableFrequencies(cpu);
            if (frequencies.isEmpty()) {
                Log.e(TAG, "No CPU frequencies available for policy" + cpu);
                return false;
            }
            String min = prefs.getString("policy_" + cpu + "_min", "");
            String max = prefs.getString("policy_" + cpu + "_max", "");
            if (!frequencies.contains(min)) {
                min = frequencies.get(0);
                Log.w(TAG, "Saved minimum unavailable for policy" + cpu + "; using kernel minimum");
            }
            if (!frequencies.contains(max)) {
                max = frequencies.get(frequencies.size() - 1);
                Log.w(TAG, "Saved maximum unavailable for policy" + cpu + "; using kernel maximum");
            }
            if (Long.parseLong(min) > Long.parseLong(max)) {
                min = frequencies.get(0);
                max = frequencies.get(frequencies.size() - 1);
                Log.w(TAG, "Saved range invalid for policy" + cpu + "; using full range");
            }
            ranges.add(new String[]{min, max});
        }

        boolean applied = apply(policies, governor, ranges);
        if (!applied) Log.e(TAG, "Unable to restore saved CPU settings at boot");
        return applied;
    }

    public static void saveSettings(Context context, String governor, int[] policies,
            List<String[]> ranges) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME,
                Context.MODE_PRIVATE).edit().putString(PREF_GOVERNOR, governor);
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
        return words(readText(path));
    }

    private static List<String> words(String text) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();
        return new ArrayList<>(Arrays.asList(text.trim().split("\\s+")));
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
