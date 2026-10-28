package org.lineageos.settings.batterymonitor;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import androidx.preference.*;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

public class BatteryMonitorActivity extends CollapsingToolbarBaseActivity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getSupportFragmentManager().beginTransaction().replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame, new Settings()).commit();
    }
    @Override public boolean onOptionsItemSelected(MenuItem i) { if (i.getItemId()==android.R.id.home) { onBackPressed(); return true; } return false; }
    public static class Settings extends PreferenceFragmentCompat {
        @Override public void onCreatePreferences(Bundle b, String root) {
            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext()); setPreferenceScreen(screen);
            SwitchPreferenceCompat master = toggle(BatteryMonitorUtils.ENABLED, "Enable battery monitor", "Show selected battery information in an ongoing notification", false);
            master.setOnPreferenceChangeListener((pref, value) -> {
                Intent service = new Intent(requireContext(), BatteryMonitorService.class);
                if (Boolean.TRUE.equals(value)) requireContext().startForegroundService(service);
                else requireContext().stopService(service);
                return true;
            });
            screen.addPreference(master);
            PreferenceCategory metrics = new PreferenceCategory(requireContext()); metrics.setTitle("Notification data"); screen.addPreference(metrics);
            for (String[] x : new String[][]{
                    {"bm_show_level","Battery percentage"}, {"bm_show_temp","Battery temperature"},
                    {"bm_current","Battery current"}, {"bm_drain","Active drain rate"},
                    {"bm_idle","Idle drain rate"}, {"bm_charge","Charging current, power and full estimate"},
                    {"bm_screen_off","Screen off duration and consumption"},
                    {"bm_screen_on","Screen on duration and consumption"},
                    {"bm_deep_sleep","Deep sleep duration and consumption"},
                    {"bm_awake","Awake duration and consumption"}})
                metrics.addPreference(toggle(x[0],x[1],null,true));
            ListPreference style = new ListPreference(requireContext()); style.setKey("bm_style"); style.setTitle("Notification layout"); style.setEntries(new String[]{"Icons and values","Text and values","Icons, text and values"}); style.setEntryValues(new String[]{"icons","text","both"}); style.setValue("both"); metrics.addPreference(style);
            ListPreference unit = new ListPreference(requireContext()); unit.setKey("battery_temp_unit"); unit.setTitle("Temperature unit"); unit.setEntries(new String[]{"Celsius","Fahrenheit"}); unit.setEntryValues(new String[]{"c","f"}); unit.setValue("c"); metrics.addPreference(unit);
            PreferenceCategory reset = new PreferenceCategory(requireContext()); reset.setTitle("Statistics reset"); screen.addPreference(reset);
            reset.addPreference(toggle("bm_reset_plug", "Reset when plugged in", null, false));
            reset.addPreference(toggle("bm_reset_boot", "Reset after reboot", null, false));
            EditTextPreference threshold = new EditTextPreference(requireContext()); threshold.setKey("bm_reset_level"); threshold.setTitle("Reset at battery level"); threshold.setDialogTitle("Battery level (1–100%)"); threshold.setDefaultValue("100"); threshold.setSummary("Reset usage counters when charging reaches this level");
            threshold.setOnBindEditTextListener(edit -> { edit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); edit.setSelectAllOnFocus(true); });
            threshold.setOnPreferenceChangeListener((pref, value) -> {
                try { int level=Integer.parseInt(value.toString()); return level >= 1 && level <= 100; }
                catch (NumberFormatException e) { return false; }
            });
            reset.addPreference(threshold);
            Preference resetNow = new Preference(requireContext()); resetNow.setTitle("Reset statistics now"); resetNow.setSummary("Clear accumulated usage durations and consumption");
            resetNow.setOnPreferenceClickListener(pref -> { Intent i=new Intent(requireContext(),BatteryMonitorService.class); i.setAction("reset"); requireContext().startForegroundService(i); return true; });
            reset.addPreference(resetNow);
        }
        private SwitchPreferenceCompat toggle(String key,String title,String summary,boolean def) { SwitchPreferenceCompat p=new SwitchPreferenceCompat(requireContext()); p.setKey(key);p.setTitle(title);if(summary!=null)p.setSummary(summary);p.setDefaultValue(def);return p; }
    }
}
