package com.onscripter.plus;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.Display;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;

import com.onscripter.ONScripterView.ONScripterEventListener;
import com.onscripter.plus.TwoStateLayout.OnSideMovedListener;
import com.onscripter.plus.VNPreferences.OnLoadVNPrefListener;

public class ONScripter extends ActivityPlus implements OnClickListener, OnDismissListener, OnSideMovedListener, OnLoadVNPrefListener, ONScripterEventListener
{
    public static final String CURRENT_DIRECTORY_EXTRA = "current_directory_extra";
    public static final String SAVE_DIRECTORY_EXTRA = "save_directory_extra";
    public static final String USE_DEFAULT_FONT_EXTRA = "use_default_font_extra";
    public static final String DIALOG_FONT_SCALE_KEY = "dialog_font_scale_key";

    private static int HIDE_CONTROLS_TIMEOUT_SECONDS = 0;

    private static String DISPLAY_CONTROLS_KEY;
    private static String SWIPE_GESTURES_KEY;
    private static String[] SWIPE_GESTURES_VALUES;
    private static String USE_EXTERNAL_VIDEO_KEY;

    private VNSettingsDialog mDialog;
    private TwoStateLayout mLeftLayout;
    private TwoStateLayout mRightLayout;
    private ImageButton2 mBackButton;
    private ImageButton2 mChangeSpeedButton;
    private ImageButton2 mSkipButton;
    private ImageButton2 mAutoButton;
    private ImageButton2 mSettingsButton;
    private ImageButton2 mRightClickButton;
    private ImageButton2 mMouseScrollUpButton;
    private ImageButton2 mMouseScrollDownButton;

    private int mDisplayHeight, mGameHeight;

    private String mCurrentDirectory;
    private String mSaveDirectory;
    private boolean mUseDefaultFont;
    private SharedPreferences mPrefs;
    private VNPreferences mVNPrefs;

    private boolean mAllowLeftBezelSwipe;
    private boolean mAllowRightBezelSwipe;
    private Handler mHideControlsHandler;

    private ONScripterGame mGame;

    Runnable mHideControlsRunnable = new Runnable() {
        @Override
        public void run() {
            hideControls();
        }
    };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        mCurrentDirectory = getIntent().getStringExtra(CURRENT_DIRECTORY_EXTRA);
        mSaveDirectory = getIntent().getStringExtra(SAVE_DIRECTORY_EXTRA);
        mUseDefaultFont = getIntent().getBooleanExtra(USE_DEFAULT_FONT_EXTRA, false);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Setup layout
        setContentView(R.layout.onscripter);
        mLeftLayout = (TwoStateLayout)findViewById(R.id.left_menu);
        mRightLayout = (TwoStateLayout)findViewById(R.id.right_menu);
        mBackButton = (ImageButton2)findViewById(R.id.controls_quit_button);
        mChangeSpeedButton = (ImageButton2)findViewById(R.id.controls_change_speed_button);
        mSkipButton = (ImageButton2)findViewById(R.id.controls_skip_button);
        mAutoButton = (ImageButton2)findViewById(R.id.controls_auto_button);
        mSettingsButton = (ImageButton2)findViewById(R.id.controls_settings_button);
        mRightClickButton = (ImageButton2)findViewById(R.id.controls_rclick_button);
        mMouseScrollUpButton = (ImageButton2)findViewById(R.id.controls_scroll_up_button);
        mMouseScrollDownButton = (ImageButton2)findViewById(R.id.controls_scroll_down_button);
        mBackButton.setOnClickListener(this);
        mChangeSpeedButton.setOnClickListener(this);
        mSkipButton.setOnClickListener(this);
        mAutoButton.setOnClickListener(this);
        mSettingsButton.setOnClickListener(this);
        mRightClickButton.setOnClickListener(this);
        mMouseScrollUpButton.setOnClickListener(this);
        mMouseScrollDownButton.setOnClickListener(this);
        mDialog = new VNSettingsDialog(this, mUseDefaultFont ? LauncherActivity.DEFAULT_FONT_PATH
                : mCurrentDirectory + "/" + getString(R.string.default_font_file));
        mDialog.setOnDimissListener(this);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        updateControlPreferences();

        mVNPrefs = ExtSDCardFix.getGameVNPreference(mCurrentDirectory);
        mVNPrefs.setOnLoadVNPrefListener(this);

        mLeftLayout.setOtherLayout(mRightLayout);
        mRightLayout.setOtherLayout(mLeftLayout);

        mLeftLayout.setOnSideMovedListener(this);

        mHideControlsHandler = new Handler();

        // Get the dimensions of the screen
        Display disp = ((WindowManager)this.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        mDisplayHeight = disp.getHeight();

        runSDLApp();
    }

    @Override
    public void autoStateChanged(boolean selected) {
        mAutoButton.setSelected(selected);
    }

    @Override
    public void skipStateChanged(boolean selected) {
        mSkipButton.setSelected(selected);
    }

    @Override
    public void videoRequested(final String filename, final boolean clickToSkip, final boolean shouldLoop) {
        boolean shouldUseExternalVideo = mPrefs.getBoolean(USE_EXTERNAL_VIDEO_KEY, false);
        mGame.useExternalVideo(shouldUseExternalVideo);
    }

    private void updateControlPreferences() {
        if (DISPLAY_CONTROLS_KEY == null) {
            DISPLAY_CONTROLS_KEY = getString(R.string.settings_controls_display_key);
            SWIPE_GESTURES_KEY = getString(R.string.settings_controls_swipe_key);
            SWIPE_GESTURES_VALUES = getResources().getStringArray(R.array.settings_controls_swipe_values);
            USE_EXTERNAL_VIDEO_KEY = getResources().getString(R.string.settings_external_video_key);
        }
        boolean allowBezelControls = mPrefs.getBoolean(DISPLAY_CONTROLS_KEY, false);
        if (allowBezelControls) {
            hideControls();
        } else {
            showControls();
            mLeftLayout.setEnabled(true);
            mRightLayout.setEnabled(true);
        }

        // Update the bezel swipe conditions
        if (allowBezelControls) {
            int index = -1;
            String gestureValue = mPrefs.getString(SWIPE_GESTURES_KEY,
                    getString(R.string.settings_controls_swipe_default_value));
            for (int i = 0; i < SWIPE_GESTURES_VALUES.length; i++) {
                if (gestureValue.equals(SWIPE_GESTURES_VALUES[i])) {
                    index = i;
                    break;
                }
            }
            // If it did not find the correct value, we will default to 0
            if (index == -1) {
                mPrefs.edit().putString(SWIPE_GESTURES_KEY, SWIPE_GESTURES_VALUES[0]).apply();
                index = 0;
            }
            switch(index) {
            case 0:     // All
                mAllowLeftBezelSwipe = true;
                mAllowRightBezelSwipe = true;
                mLeftLayout.setEnabled(false);
                mRightLayout.setEnabled(false);
                break;
            case 1:     // Left
                mAllowLeftBezelSwipe = true;
                mAllowRightBezelSwipe = false;
                mLeftLayout.setEnabled(false);
                mRightLayout.setEnabled(true);
                break;
            case 2:     // Right
                mAllowLeftBezelSwipe = false;
                mAllowRightBezelSwipe = true;
                mLeftLayout.setEnabled(true);
                mRightLayout.setEnabled(false);
                break;
            }
        }
    }

    private void runSDLApp() {
        boolean shouldRenderOutline = mPrefs.getBoolean(getString(R.string.settings_render_font_outline_key),
                getResources().getBoolean(R.bool.render_font_outline));

        if (mUseDefaultFont) {
            mGame = ONScripterGame.newInstance(mCurrentDirectory, LauncherActivity.DEFAULT_FONT_PATH, mSaveDirectory, shouldRenderOutline);
        } else {
            mGame = ONScripterGame.newInstance(mCurrentDirectory, null, mSaveDirectory, shouldRenderOutline);
        }

        // Attach the game fragment
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        transaction.add(R.id.game_wrapper, mGame);
        transaction.commit();

        mGame.setONScripterEventListener(this);
        mGame.setBoundingHeight(mDisplayHeight);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public int getGameHeight() {
        return mGameHeight;
    }

    public int getGameFontSize() {
        return mGame.getGameFontSize();
    }

    @Override
    public void onClick(View v) {
        boolean refreshTimer = true;
        switch(v.getId()) {
        case R.id.controls_quit_button:
            removeHideControlsTimer();
            mGame.exitApp();
            refreshTimer = false;
            break;
        case R.id.controls_change_speed_button:
            mGame.sendNativeKeyPress(KeyEvent.KEYCODE_O);
            break;
        case R.id.controls_skip_button:
            mGame.sendNativeKeyPress(KeyEvent.KEYCODE_S);
            break;
        case R.id.controls_auto_button:
            mGame.sendNativeKeyPress(KeyEvent.KEYCODE_A);
            break;
        case R.id.controls_settings_button:
            removeHideControlsTimer();
            mDialog.show();
            refreshTimer = false;
            break;
        case R.id.controls_rclick_button:
            mGame.sendNativeKeyPress(KeyEvent.KEYCODE_BACK);
            break;
        case R.id.controls_scroll_up_button:
            mGame.sendNativeKeyPress(KeyEvent.KEYCODE_DPAD_LEFT);
            break;
        case R.id.controls_scroll_down_button:
            mGame.sendNativeKeyPress(KeyEvent.KEYCODE_DPAD_RIGHT);
            break;
        default:
            return;
        }
        if (refreshTimer) {
            refreshHideControlsTimer();
        }
    }

    @Override
    public void onLoadVNPref(Result result) {
        if (result == Result.NO_ISSUES) {
            // Load scale factor
            double scaleFactor = mVNPrefs.getFloat(DIALOG_FONT_SCALE_KEY, 1);
            mDialog.setFontScalingFactor(scaleFactor);
            mGame.setFontScaling(scaleFactor);
        }

        if (result == Result.NO_MEMORY) {
            AlertDialog.Builder dialog = new Builder(this);
            dialog.setTitle(getString(R.string.app_name));
            dialog.setMessage(R.string.message_cannot_write_pref);
            dialog.setPositiveButton(android.R.string.ok, null);
            dialog.show();
        }
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        if( mGame != null ) {
            mGame.onPause();
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        if( mGame != null ) {
            mGame.onResume();
        }
    }

    @Override
    protected void onStop()
    {
        super.onStop();
        if( mGame != null ) {
            mGame.onStop();
        }
    }

    @Override
    protected void onDestroy()
    {
        if( mGame != null ) {
            mGame.exitApp();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Do not allow back button when playing video, skip it otherwise
        if (mGame.isVideoShown()) {
            mGame.finishVideo();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        updateControlPreferences();
        double scaleFactor = mDialog.getFontScalingFactor();
        mGame.setFontScaling(scaleFactor);
        mVNPrefs.putFloat(DIALOG_FONT_SCALE_KEY, (float) scaleFactor);
        mVNPrefs.commit();
    }

    @Override
    public void onLeftSide(TwoStateLayout v) {
    }

    @Override
    public void onRightSide(TwoStateLayout v) {
        if (v == mLeftLayout) {
            refreshHideControlsTimer();
        }
    }

    private void hideControls() {
        hideControls(true);
    }

    private void showControls() {
        showControls(true);
    }

    private void hideControls(boolean animate) {
        mLeftLayout.moveLeft(animate);
        mRightLayout.moveRight(animate);
    }

    private void showControls(boolean animate) {
        mLeftLayout.moveRight(animate);
        mRightLayout.moveLeft(animate);
    }

    private void removeHideControlsTimer() {
        if (mAllowRightBezelSwipe || mAllowLeftBezelSwipe) {
            mHideControlsHandler.removeCallbacks(mHideControlsRunnable);
        }
    }

    private void refreshHideControlsTimer() {
        if (mAllowRightBezelSwipe || mAllowLeftBezelSwipe) {
            removeHideControlsTimer();
            if (HIDE_CONTROLS_TIMEOUT_SECONDS == 0) {
                HIDE_CONTROLS_TIMEOUT_SECONDS = getResources().getInteger(R.integer.hide_controls_timeout_seconds);
            }
            mHideControlsHandler.postDelayed(mHideControlsRunnable, 1000 * HIDE_CONTROLS_TIMEOUT_SECONDS);
        }
    }
}
