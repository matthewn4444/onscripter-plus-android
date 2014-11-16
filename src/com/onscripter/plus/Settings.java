package com.onscripter.plus;

import java.util.List;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources.Theme;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;
import com.onscripter.plus.settings.AboutSettingsFragment;
import com.onscripter.plus.settings.GeneralSettingsFragment;
import com.onscripter.plus.settings.LayoutPreference;
import com.onscripter.plus.settings.LayoutPreference.OnLayoutViewCreatedListener;

public final class Settings extends SherlockPreferenceActivity implements OnPreferenceClickListener {
    private ChangeLog mChangeLog;

    private static final String[] ValidFragments = {
        GeneralSettingsFragment.class.getName(),
        AboutSettingsFragment.class.getName()
    };

    public static void setVersionString(View layoutView, Context ctx) {
        PackageInfo pInfo;
        try {
            pInfo = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            String version = pInfo.versionName;
            ((TextView)layoutView.findViewById(R.id.version_text)).setText(version);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onApplyThemeResource(Theme theme, int resid, boolean first) {
      // Set the theme
      String defaultThemeName = getString(R.string.settings_theme_default_value);
      String themeName = PreferenceManager.getDefaultSharedPreferences(this).getString(
              getString(R.string.settings_theme_key), defaultThemeName);
      int id = themeName.equals(defaultThemeName) ? R.style.Theme_Light : R.style.Theme_Dark;
      super.onApplyThemeResource(theme, id, first);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.menu_action_settings);

        if (!isLargeTablet()) {
            // Old way of setting preferences for smaller devices
            addPreferencesFromResource(R.xml.general_settings);
            addPreferencesFromResource(R.xml.about_settings);

            Preference preference = getPreferenceScreen().findPreference(getString(R.string.settings_about_change_log_key));
            preference.setOnPreferenceClickListener(this);

            // Put the version number inside the app about preference screen
            LayoutPreference appAboutPref = (LayoutPreference)findPreference(getString(R.string.settings_about_app_key));
            appAboutPref.setOnLayoutViewCreatedListener(new OnLayoutViewCreatedListener() {
                @Override
                public void onLayoutViewCreated(View layoutView) {
                    setVersionString(layoutView, Settings.this);
                }
            });
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public void onBuildHeaders(List<Header> target) {
        if (isLargeTablet()) {
            loadHeadersFromResource(R.xml.settings_header, target);
        }
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        for (String name: ValidFragments) {
            if (fragmentName.equals(name)) {
                return true;
            }
        }
        return false;
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

    private boolean isLargeTablet() {
        return (getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK)
                >= Configuration.SCREENLAYOUT_SIZE_XLARGE
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
    }
}
