package com.onscripter.plus;

import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;
import com.bugsense.trace.BugSenseHandler;

public final class Settings extends SherlockPreferenceActivity implements OnPreferenceClickListener {
    private ChangeLog mChangeLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isDebug()) {
            BugSenseHandler.initAndStartSession(this, getString(R.string.bugsense_key));
        }
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.menu_action_settings);
        addPreferencesFromResource(R.xml.settings);
        getListView().setBackgroundColor(Color.WHITE);

        Preference preference = getPreferenceScreen().findPreference(getString(R.string.settings_about_change_log_key));
        preference.setOnPreferenceClickListener(this);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (mChangeLog == null) {
            mChangeLog = new ChangeLog(this);
        }
        mChangeLog.show();
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            finish();
            break;
        }
        return true;
    }

    private boolean isDebug() {
        return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
}
