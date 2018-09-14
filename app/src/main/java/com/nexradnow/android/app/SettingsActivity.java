package com.nexradnow.android.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.widget.ListAdapter;
import com.nexradnow.android.nexradproducts.NexradRenderer;
import com.nexradnow.android.nexradproducts.RendererInventory;
import org.apache.commons.validator.routines.EmailValidator;
import toothpick.Toothpick;

import javax.inject.Inject;

/**
 * This activity houses the "settings" portions of the application. The code here was pretty much
 * copied from the Android reference/tutorial material.
 *
 * Created by hobsonm on 10/5/15.
 */

public class SettingsActivity extends Activity {

    @Inject
    protected RendererInventory rendererInventory;

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
            Toothpick.inject(this,Toothpick.openScope(NexradApp.APPSCOPE));
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);

            // Enforce a valid email address
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

            // Populate the product selection preference with available choices
            ListPreference productSelectionList =
                    (ListPreference)getPreferenceScreen().findPreference(KEY_PREF_NEXRAD_PRODUCT);
            productSelectionList.setEntries(((SettingsActivity)getActivity()).rendererInventory.getDescriptions());
            productSelectionList.setEntryValues(((SettingsActivity)getActivity()).rendererInventory.getCodes());
            if (getActivity().getIntent().getBooleanExtra("com.nexradnow.android.forceEmailPreference",false)) {
                openPreference(KEY_PREF_NEXRAD_EMAILADDRESS);
            }

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
            Preference pref = findPreference(key);
            if (key.equals(KEY_PREF_NEXRAD_STATIONLIST_URL)||
                    key.equals(KEY_PREF_NEXRAD_FTPHOST)||
                    key.equals(KEY_PREF_NEXRAD_FTPDIR)||
                    key.equals(KEY_PREF_NEXRAD_EMAILADDRESS)) {
                // Set summary to be the user-description for the selected value
                pref.setSummary(sharedPreferences.getString(key, ""));
            } else if (key.equals(KEY_PREF_NEXRAD_STATIONDISTANCE)) {
                int kmDistance = Integer.parseInt(sharedPreferences.getString(key,"-1"));
                if (kmDistance > 0) {
                    pref.setSummary(((kmDistance*10)/16)+ " miles");
                }
            } else if (key.equals(KEY_PREF_NEXRAD_PRODUCT)) {
                String code = sharedPreferences.getString(key,"");
                if ((code != null)&&(!code.isEmpty())) {
                    NexradRenderer renderer = ((SettingsActivity)getActivity()).rendererInventory.getRenderer(code);
                    if (renderer != null) {
                        pref.setSummary(renderer.getProductDescription());
                    }
                }
            }
        }

        private void openPreference(String key) {
            PreferenceScreen preferenceScreen = getPreferenceScreen();
            final ListAdapter listAdapter = preferenceScreen.getRootAdapter();

            final int itemsCount = listAdapter.getCount();
            int itemNumber;
            for (itemNumber = 0; itemNumber < itemsCount; ++itemNumber) {
                if (listAdapter.getItem(itemNumber).equals(findPreference(key))) {
                    preferenceScreen.onItemClick(null, null, itemNumber, 0);
                    break;
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Toothpick.inject(this,Toothpick.openScope(NexradApp.APPSCOPE));
        super.onCreate(savedInstanceState);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();

    }

}