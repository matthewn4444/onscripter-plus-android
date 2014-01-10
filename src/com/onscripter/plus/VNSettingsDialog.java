package com.onscripter.plus;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;

public class VNSettingsDialog implements OnDismissListener, OnClickListener {
    private final AlertDialog mDialog;
    private final Activity mActivity;
    private final TabHost mTabHost;
    private final FrameLayout mTabContent;
    private final SharedPreferences mPrefs;

    // Event listeners
    private OnDismissListener mDismissListener;

    // Preference keys
    private static String DISPLAY_CONTROLS_KEY;
    private static String SWIPE_GESTURES_KEY;

    // Preference values
    private static String[] SWIPE_GESTURES_ENTRIES;

    // Settings view elements
    private final CheckBox mDisplayControlsChkbx;
    private final Spinner mSwipeGesturesSpinner;

    // Settings view elements id
    public static final int DISPLAY_CONTROLS_ID = R.id.dialog_controls_display_checkbox;
    public static final int SWIPE_GESTURES_ID = R.id.dialog_controls_swipe_spinner;

    public VNSettingsDialog(Activity activity) {
        mActivity = activity;
        AlertDialog.Builder builder = new Builder(activity);
        mTabHost = (TabHost)activity.getLayoutInflater().inflate(R.layout.vnsettings_dialog, null);
        mTabHost.setup();
        builder.setView(mTabHost);
        mDialog = builder.create();
        mDialog.setOnDismissListener(this);
        mTabContent = mTabHost.getTabContentView();

        // Set the height of the window
        WindowManager window = (WindowManager)mActivity.getSystemService(Context.WINDOW_SERVICE);
        mTabHost.setMinimumHeight(window.getDefaultDisplay().getHeight());
        mDialog.getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        addTab("Controls", R.layout.vnsettings_dialog_controls, null);
        addTab("Text", R.layout.vnsettings_dialog_text, null);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(mActivity);

        // Load static strings
        if (DISPLAY_CONTROLS_KEY == null) {
            DISPLAY_CONTROLS_KEY = mActivity.getString(R.string.settings_controls_display_key);
            SWIPE_GESTURES_KEY = mActivity.getString(R.string.settings_controls_swipe_key);
            SWIPE_GESTURES_ENTRIES = mActivity.getResources().getStringArray(R.array.settings_controls_swipe_entries);
        }

        mDisplayControlsChkbx = (CheckBox)mTabHost.findViewById(DISPLAY_CONTROLS_ID);
        mSwipeGesturesSpinner = (Spinner)mTabHost.findViewById(SWIPE_GESTURES_ID);
        mDisplayControlsChkbx.setOnClickListener(this);
    }

    public void setOnDimissListener(OnDismissListener listener) {
        mDismissListener = listener;
    }

    private void loadPreferences() {
        // Controls menu
        boolean hideControls = mPrefs.getBoolean(DISPLAY_CONTROLS_KEY, false);
        mDisplayControlsChkbx.setChecked(hideControls);

        // Swipe spinner
        int index = -1;
        String gestureValue = mPrefs.getString(SWIPE_GESTURES_KEY,
                mActivity.getString(R.string.settings_controls_swipe_default_entry));
        for (int i = 0; i < SWIPE_GESTURES_ENTRIES.length; i++) {
            if (gestureValue.equals(SWIPE_GESTURES_ENTRIES[i])) {
                index = i;
                break;
            }
        }
        mSwipeGesturesSpinner.setEnabled(hideControls);
        mSwipeGesturesSpinner.setSelection(index);
    }

    private void savePreferences() {
        Editor editor = mPrefs.edit();
        editor.putBoolean(DISPLAY_CONTROLS_KEY, mDisplayControlsChkbx.isChecked());
        editor.putString(SWIPE_GESTURES_KEY, mSwipeGesturesSpinner.getSelectedItem().toString());
        editor.commit();
    }

    protected void addTab(String tab, int resourceLayoutId, int[] viewIds) {
        TabSpec spec = mTabHost.newTabSpec(tab);
        spec.setIndicator(tab);
        View v = mActivity.getLayoutInflater().inflate(resourceLayoutId, null);
        mTabContent.addView(v);
        spec.setContent(v.getId());
        mTabHost.addTab(spec);
    }

    public int getStatusBarHeight() {
        int result = 0;
        int resourceId = mActivity.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = mActivity.getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    public void hide() {
        mDialog.hide();
    }

    public void show() {
        loadPreferences();
        mDialog.show();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        savePreferences();
        if (mDismissListener != null) {
            mDismissListener.onDismiss(dialog);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
        case DISPLAY_CONTROLS_ID:
            mSwipeGesturesSpinner.setEnabled(((CheckBox)v).isChecked());
            break;
        }
    }
}
