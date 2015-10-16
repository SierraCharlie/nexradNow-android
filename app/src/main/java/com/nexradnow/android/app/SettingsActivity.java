package com.nexradnow.android.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import org.apache.commons.validator.routines.EmailValidator;

/**
 * This activity houses the "settings" portions of the application. The code here was pretty much
 * copied from the Android reference/tutorial material.
 *
 * Created by hobsonm on 10/5/15.
 */
public class SettingsActivity extends Activity {

    public static final String KEY_PREF_NEXRAD_STATIONLIST_URL = "pref_nexrad_stationlist_url";
    public static final String KEY_PREF_NEXRAD_FTPHOST = "pref_nexrad_ftphost";
    public static final String KEY_PREF_NEXRAD_FTPDIR = "pref_nexrad_ftpdir";
    public static final String KEY_PREF_NEXRAD_EMAILADDRESS = "pref_nexrad_emailaddress";
    public static final String KEY_PREF_NEXRAD_STATIONDISTANCE = "pref_nexrad_stationdistance";
    public static final String KEY_PREF_NEXRAD_PRODUCT = "pref_nexrad_product";

    public static class SettingsFragment extends PreferenceFragment
            implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);

            EditTextPreference emailAddress =
                    (EditTextPreference) getPreferenceScreen().findPreference(KEY_PREF_NEXRAD_EMAILADDRESS);
            emailAddress.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean isValid = true;
                    String eMailAddress = (String)newValue;
                    if (!EmailValidator.getInstance().isValid(eMailAddress)) {
                        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                        builder.setTitle("Invalid Input");
                        builder.setMessage("This email address does not appear to be valid");
                        builder.setPositiveButton(android.R.string.ok, null);
                        builder.show();
                        isValid = false;
                    }
                    return isValid;
                }
            });
        }
        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals(KEY_PREF_NEXRAD_STATIONLIST_URL)||
                    key.equals(KEY_PREF_NEXRAD_FTPHOST)||
                    key.equals(KEY_PREF_NEXRAD_FTPDIR)||
                    key.equals(KEY_PREF_NEXRAD_EMAILADDRESS)) {
                Preference pref = findPreference(key);
                // Set summary to be the user-description for the selected value
                pref.setSummary(sharedPreferences.getString(key, ""));
            } else if (key.equals(KEY_PREF_NEXRAD_STATIONDISTANCE)) {
                Preference pref = findPreference(key);
                int kmDistance = Integer.parseInt(sharedPreferences.getString(key,"-1"));
                if (kmDistance > 0) {
                    pref.setSummary(((kmDistance*10)/16)+ " miles");
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

}