package com.onscripter.plus;

import java.io.File;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;
import android.widget.Toast;

public class VNSettingsDialog implements OnDismissListener, OnClickListener, OnSeekBarChangeListener, OnTabChangeListener {
    private final AlertDialog mDialog;
    private final ONScripter mActivity;
    private final TabHost mTabHost;
    private final FrameLayout mTabContent;
    private final SharedPreferences mPrefs;
    private final int mScreenHeight;
    private int mFontPreviewSize;
    private double mFontScale;

    // Tab Names/id
    private static String CONTROLS_TAB_NAME = "Controls";
    private static String TEXT_TAB_NAME = "Text";

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
    private final TextView mUpScalingNumber;
    private final TextView mUpScalingText;
    private final SeekBar mUpScalingScroller;

    // Settings view elements id
    public static final int DISPLAY_CONTROLS_ID = R.id.dialog_controls_display_checkbox;
    public static final int SWIPE_GESTURES_ID = R.id.dialog_controls_swipe_spinner;

    public static final int UPSCALE_NUMBER_ID = R.id.scaleNumber;
    public static final int UPSCALE_TEXT_ID = R.id.textPreview;
    public static final int TEXT_SCROLLER_ID = R.id.fontScaler;

    public VNSettingsDialog(ONScripter activity, String fontPath) {
        mActivity = activity;
        AlertDialog.Builder builder = new Builder(activity);
        mTabHost = (TabHost)activity.getLayoutInflater().inflate(R.layout.vnsettings_dialog, null);
        mTabHost.setup();
        builder.setView(mTabHost);
        mDialog = builder.create();
        mDialog.setOnDismissListener(this);
        mTabContent = mTabHost.getTabContentView();
        mTabHost.setOnTabChangedListener(this);

        // Set the height of the window
        WindowManager window = (WindowManager)mActivity.getSystemService(Context.WINDOW_SERVICE);
        mScreenHeight = window.getDefaultDisplay().getHeight();
        mTabHost.setMinimumHeight(mScreenHeight);
        mDialog.getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(mActivity);

        // Load static strings
        if (DISPLAY_CONTROLS_KEY == null) {
            DISPLAY_CONTROLS_KEY = mActivity.getString(R.string.settings_controls_display_key);
            SWIPE_GESTURES_KEY = mActivity.getString(R.string.settings_controls_swipe_key);
            SWIPE_GESTURES_ENTRIES = mActivity.getResources().getStringArray(R.array.settings_controls_swipe_entries);
            CONTROLS_TAB_NAME = mActivity.getString(R.string.dialog_settings_controls_title);
            TEXT_TAB_NAME = mActivity.getString(R.string.dialog_settings_text_title);
        }

        addTab(CONTROLS_TAB_NAME, R.layout.vnsettings_dialog_controls, null);
        addTab(TEXT_TAB_NAME, R.layout.vnsettings_dialog_text, null);

        mDisplayControlsChkbx = (CheckBox)mTabHost.findViewById(DISPLAY_CONTROLS_ID);
        mSwipeGesturesSpinner = (Spinner)mTabHost.findViewById(SWIPE_GESTURES_ID);
        mUpScalingNumber = (TextView)mTabHost.findViewById(UPSCALE_NUMBER_ID);
        mUpScalingText = (TextView)mTabHost.findViewById(UPSCALE_TEXT_ID);
        mUpScalingScroller = (SeekBar)mTabHost.findViewById(TEXT_SCROLLER_ID);
        mDisplayControlsChkbx.setOnClickListener(this);
        mUpScalingScroller.setOnSeekBarChangeListener(this);

        // Set the font family only if it exists, most of the time it will work, some people will fail
        if (fontPath == null) {
            fontPath = LauncherActivity.DEFAULT_FONT_PATH;
        }
        if (fontPath != null && new File(fontPath).exists()) {
            try {
                mUpScalingText.setTypeface(Typeface.createFromFile(fontPath));
            } catch (RuntimeException e) {
                // Cannot read the font file, then set the default one
                e.printStackTrace();
                Toast.makeText(activity, R.string.message_read_font_error, Toast.LENGTH_SHORT).show();
                if (!fontPath.equals(LauncherActivity.DEFAULT_FONT_PATH)) {
                    mUpScalingText.setTypeface(Typeface.createFromFile(LauncherActivity.DEFAULT_FONT_PATH));
                }
            }
        }
        onProgressChanged(mUpScalingScroller, 0, true);
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

        // Update the text upscaling font size
        mFontPreviewSize = mActivity.nativeGetDialogFontSize();
        updateUpscalingNumber();
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
        v.setMinimumHeight(mScreenHeight);
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

    public boolean isShowing() {
        return mDialog.isShowing();
    }

    @Override
    public void onTabChanged(String tabId) {
        if (tabId.equals(TEXT_TAB_NAME)) {
            mFontPreviewSize = mActivity.nativeGetDialogFontSize();
            updateUpscalingNumber();
        }
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

    public void setFontScalingFactor(double scaleFactor) {
        // Round to avoid trailing digits
        mFontScale = Math.round(scaleFactor * 10) / 10.0;
        mUpScalingScroller.setProgress((int)Math.round((mFontScale - 1.0) * 100));
    }

    public double getFontScalingFactor() {
        return mFontScale;
    }

    private void updateUpscalingNumber() {
        // Update the size of the text area
        ViewGroup vg = (ViewGroup) mUpScalingText.getParent();
        LayoutParams lp = (LayoutParams) vg.getLayoutParams();
        lp.height = mScreenHeight / 4;
        vg.setLayoutParams(lp);

        mUpScalingNumber.setText(mFontScale + "");
        mUpScalingText.setTextSize((float) (mFontScale * mFontPreviewSize * mActivity.getGameHeight() / mScreenHeight));
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress,
            boolean fromUser) {
        mFontScale = Math.round(progress / 10) / 10.0 + 1;
        updateUpscalingNumber();
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }

}
