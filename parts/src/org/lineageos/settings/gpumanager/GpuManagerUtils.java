/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

package org.lineageos.settings.gpumanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** KGSL controls exposed by the Adreno 650 driver in the munch kernel. */
public final class GpuManagerUtils {
    private static final String TAG = "GpuManager";
    private static final String BASE = "/sys/class/kgsl/kgsl-3d0";
    private static final String PREFS = "munch_gpu_manager";
    private static final String PREF_APPLY_ON_BOOT = "apply_on_boot";
    private static final String PREF_GOVERNOR = "governor";
    private static final String PREF_MIN = "min_freq";
    private static final String PREF_MAX = "max_freq";
    private static final String[] TOGGLES = {
        "force_clk_on", "force_bus_on", "force_rail_on", "force_no_nap", "bus_split"
    };

    private GpuManagerUtils() {}

    public static boolean isAvailable() {
        return new File(BASE + "/gpu_available_frequencies").canRead();
    }

    public static String read(String node, String fallback) {
        try {
            String value = readText(BASE + "/" + node);
            return value.isEmpty() ? fallback : value;
        } catch (IOException e) {
            return fallback;
        }
    }

    public static String readDevfreq(String node, String fallback) {
        try {
            String value = readText(BASE + "/devfreq/" + node);
            return value.isEmpty() ? fallback : value;
        } catch (IOException e) {
            return fallback;
        }
    }

    public static List<String> getFrequencies() {
        String raw = read("gpu_available_frequencies", "").trim();
        if (raw.isEmpty()) return Collections.emptyList();
        List<String> values = new ArrayList<>(Arrays.asList(raw.split("\\s+")));
        // Do not rely on power-level array ordering: expose numeric min-to-max choices.
        Collections.sort(values, new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                return Long.compare(Long.parseLong(left), Long.parseLong(right));
            }
        });
        return values;
    }

    public static List<String> getGovernors() {
        String raw = readDevfreq("available_governors", "").trim();
        return raw.isEmpty() ? Collections.<String>emptyList()
                : Arrays.asList(raw.split("\\s+"));
    }

    public static boolean write(String node, String value) {
        return writePath(BASE + "/" + node, value);
    }

    public static boolean writeDevfreq(String node, String value) {
        return writePath(BASE + "/devfreq/" + node, value);
    }

    private static boolean writePath(String path, String value) {
        try {
            java.io.FileWriter writer = new java.io.FileWriter(path);
            try {
                writer.write(value);
            } finally {
                writer.close();
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Unable to write " + path, e);
            return false;
        }
    }

    private static String readText(String path) throws IOException {
        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(path));
        try {
            StringBuilder value = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (value.length() > 0) value.append(' ');
                value.append(line.trim());
            }
            return value.toString();
        } finally {
            reader.close();
        }
    }

    public static boolean apply(Context context, String governor, String min, String max,
            boolean[] toggles) {
        List<String> frequencies = getFrequencies();
        List<String> governors = getGovernors();
        if (!frequencies.contains(min) || !frequencies.contains(max)
                || !governors.contains(governor)) return false;
        try {
            if (Long.parseLong(min) > Long.parseLong(max)) return false;
        } catch (NumberFormatException e) {
            return false;
        }

        boolean ok = writeDevfreq("governor", governor);
        // Avoid transiently invalid ranges when raising the floor or lowering the ceiling.
        long currentMin = parse(readDevfreq("min_freq", min), min);
        long currentMax = parse(readDevfreq("max_freq", max), max);
        long targetMin = Long.parseLong(min);
        long targetMax = Long.parseLong(max);
        if (targetMin > currentMax) {
            ok &= writeDevfreq("max_freq", max);
            ok &= writeDevfreq("min_freq", min);
        } else if (targetMax < currentMin) {
            ok &= writeDevfreq("min_freq", min);
            ok &= writeDevfreq("max_freq", max);
        } else {
            ok &= writeDevfreq("min_freq", min);
            ok &= writeDevfreq("max_freq", max);
        }
        for (int i = 0; i < TOGGLES.length; i++) {
            ok &= write(TOGGLES[i], toggles[i] ? "1" : "0");
        }
        if (ok) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(PREF_GOVERNOR, governor).putString(PREF_MIN, min)
                    .putString(PREF_MAX, max).putBoolean("force_clk_on", toggles[0])
                    .putBoolean("force_bus_on", toggles[1]).putBoolean("force_rail_on", toggles[2])
                    .putBoolean("force_no_nap", toggles[3]).putBoolean("bus_split", toggles[4])
                    .apply();
        }
        return ok;
    }

    public static boolean applySavedSettings(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(PREF_APPLY_ON_BOOT, false) || !isAvailable()) return true;
        List<String> frequencies = getFrequencies();
        List<String> governors = getGovernors();
        if (frequencies.isEmpty() || governors.isEmpty()) return false;
        String governor = prefs.getString(PREF_GOVERNOR,
                governors.contains("msm-adreno-tz") ? "msm-adreno-tz" : governors.get(0));
        String min = prefs.getString(PREF_MIN, frequencies.get(0));
        String max = prefs.getString(PREF_MAX, frequencies.get(frequencies.size() - 1));
        boolean[] toggles = new boolean[TOGGLES.length];
        for (int i = 0; i < TOGGLES.length; i++) toggles[i] = prefs.getBoolean(TOGGLES[i], false);
        return apply(context, governor, min, max, toggles);
    }

    public static void setApplyOnBoot(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(PREF_APPLY_ON_BOOT, enabled).apply();
    }

    public static boolean isApplyOnBoot(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREF_APPLY_ON_BOOT, false);
    }

    private static long parse(String value, String fallback) {
        try { return Long.parseLong(value.trim()); }
        catch (NumberFormatException e) { return Long.parseLong(fallback); }
    }
}
