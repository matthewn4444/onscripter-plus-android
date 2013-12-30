package com.onscripter.plus;

import android.os.Bundle;

import com.actionbarsherlock.app.SherlockPreferenceActivity;

public final class Settings extends SherlockPreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.menu_action_settings);
        addPreferencesFromResource(R.xml.settings);
    }
}
