package com.onscripter.plus.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.util.TypedValue;
import android.view.View;

import com.onscripter.plus.ChangeLog;
import com.onscripter.plus.R;
import com.onscripter.plus.Settings;
import com.onscripter.plus.settings.LayoutPreference.OnLayoutViewCreatedListener;

public class AboutSettingsFragment extends PreferenceFragment implements OnPreferenceClickListener {
    private ChangeLog mChangeLog;

    private final OnPreferenceClickListener mPrivacyPolicyListener = new OnPreferenceClickListener() {
        @Override
        public boolean onPreferenceClick(Preference preference) {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse(getString(R.string.website_privay_policy)));
            startActivity(browserIntent);
            return false;
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.about_settings);
        findPreference(getString(R.string.settings_about_change_log_key))
            .setOnPreferenceClickListener(this);

        findPreference(getString(R.string.settings_about_privacy_policy_key))
            .setOnPreferenceClickListener(mPrivacyPolicyListener);

        // Set the corrected padding on the left to follow the same indent as the rest and set version number
        LayoutPreference p = (LayoutPreference) findPreference(getString(R.string.settings_about_app_key));
        p.setOnLayoutViewCreatedListener(new OnLayoutViewCreatedListener() {
            @Override
            public void onLayoutViewCreated(View layoutView) {
                int paddingLeft = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                        getResources().getDimension(R.dimen.preference_padding_start),
                        getResources().getDisplayMetrics());
                layoutView.setPadding(paddingLeft, 0, 0, 0);
                Settings.setVersionString(layoutView, getActivity());
            }
        });
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (mChangeLog == null) {
            mChangeLog = new ChangeLog(getActivity());
        }
        mChangeLog.show();
        return false;
    }
}
