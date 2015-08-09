package com.onscripter.plus.settings;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import com.onscripter.plus.R;

public class GeneralSettingsFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.general_settings);
    }
}
