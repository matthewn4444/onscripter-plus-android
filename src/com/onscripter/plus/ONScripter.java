package com.onscripter.plus;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.bugsense.trace.BugSenseHandler;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.onscripter.ONScripterView;
import com.onscripter.ONScripterView.OnUpdateControlListener;
import com.onscripter.plus.Analytics.BUTTON;
import com.onscripter.plus.Analytics.CHANGE;
import com.onscripter.plus.TwoStateLayout.OnSideMovedListener;
import com.onscripter.plus.VNPreferences.OnLoadVNPrefListener;
import com.onscripter.plus.ads.InterstitialAdHelper;
import com.onscripter.plus.ads.InterstitialAdHelper.AdListener;

public class ONScripter extends ActivityPlus implements OnClickListener, OnDismissListener, OnSideMovedListener, OnLoadVNPrefListener, OnUpdateControlListener
{
    public static final String CURRENT_DIRECTORY_EXTRA = "current_directory_extra";
    public static final String USE_DEFAULT_FONT_EXTRA = "use_default_font_extra";
    public static final String DIALOG_FONT_SCALE_KEY = "dialog_font_scale_key";

    private static int HIDE_CONTROLS_TIMEOUT_SECONDS = 0;

    private static String DISPLAY_CONTROLS_KEY;
    private static String SWIPE_GESTURES_KEY;
    private static String[] SWIPE_GESTURES_VALUES;

    private VNSettingsDialog mDialog;
    private FrameLayout mGameLayout;
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

    private int mDisplayWidth, mDisplayHeight, mGameHeight;

    private String mCurrentDirectory;
    private boolean mUseDefaultFont;
    private SharedPreferences mPrefs;
    private VNPreferences mVNPrefs;

    private boolean mAllowLeftBezelSwipe;
    private boolean mAllowRightBezelSwipe;
    private Handler mHideControlsHandler;

    private ONScripterView mGame;

    private AdView mAdView;
    private InterstitialAdHelper mInterstitialHelper;
    private long mSessionStart;
    static final private double LEAVE_GAME_INTERSTITIAL_AD_PERCENT = 100;

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
        if (!isDebug()) {
            BugSenseHandler.initAndStartSession(this, getString(R.string.bugsense_key));
        }

        mCurrentDirectory = getIntent().getStringExtra(CURRENT_DIRECTORY_EXTRA);
        mUseDefaultFont = getIntent().getBooleanExtra(USE_DEFAULT_FONT_EXTRA, false);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Setup layout
        setContentView(R.layout.onscripter);
        mGameLayout = (FrameLayout)findViewById(R.id.game_wrapper);
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

        mVNPrefs = new VNPreferences(mCurrentDirectory);
        mVNPrefs.setOnLoadVNPrefListener(this);

        mLeftLayout.setOtherLayout(mRightLayout);
        mRightLayout.setOtherLayout(mLeftLayout);

        mLeftLayout.setOnSideMovedListener(this);

        mHideControlsHandler = new Handler();

        // Get the dimensions of the screen
        Display disp = ((WindowManager)this.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        mDisplayWidth = disp.getWidth();
        mDisplayHeight = disp.getHeight();

        // Only show ads on tablet devices
        if (getResources().getBoolean(R.bool.isTablet)) {
            mDisplayHeight -= attachAds();
        }
        mInterstitialHelper = new InterstitialAdHelper(this, LEAVE_GAME_INTERSTITIAL_AD_PERCENT);
        mInterstitialHelper.setAdListener(new AdListener() {
            @Override
            public void onAdDismiss() {
                super.onAdDismiss();
                findViewById(android.R.id.content).setVisibility(View.GONE);
                mGame.exitApp();
            }
        });
        mSessionStart = System.currentTimeMillis();

        runSDLApp();
    }

    private int attachAds() {
        // Create the ad and attach it to the content
        mAdView = new AdView(this);
        mAdView.setAdSize(AdSize.BANNER);
        mAdView.setAdUnitId(getString(R.string.admob_key));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        mAdView.setLayoutParams(params);

        ViewGroup content = (ViewGroup)findViewById(android.R.id.content);
        content.addView(mAdView);

        // Request the ads
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);
        return AdSize.BANNER.getHeightInPixels(this);
    }

    @Override
    public void autoStateChanged(boolean selected) {
        mAutoButton.setSelected(selected);
    }

    @Override
    public void skipStateChanged(boolean selected) {
        mSkipButton.setSelected(selected);
    }

    private void updateControlPreferences() {
        if (DISPLAY_CONTROLS_KEY == null) {
            DISPLAY_CONTROLS_KEY = getString(R.string.settings_controls_display_key);
            SWIPE_GESTURES_KEY = getString(R.string.settings_controls_swipe_key);
            SWIPE_GESTURES_VALUES = getResources().getStringArray(R.array.settings_controls_swipe_values);
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
                mPrefs.edit().putString(SWIPE_GESTURES_KEY, SWIPE_GESTURES_VALUES[0]).commit();
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
            mGame = new ONScripterView(this, mCurrentDirectory, LauncherActivity.DEFAULT_FONT_PATH, shouldRenderOutline);
        } else {
            mGame = new ONScripterView(this, mCurrentDirectory, null, shouldRenderOutline);
        }
        mGame.setOnUpdateControlListener(this);

        int gameWidth = mGame.getGameWidth();
        mGameHeight = mGame.getGameHeight();

        int screenWidth = mDisplayWidth;
        int screenHeight = mDisplayHeight;
        if (mDisplayWidth * mGameHeight >= mDisplayHeight * gameWidth){
            screenWidth = (mDisplayHeight*gameWidth/mGameHeight) & (~0x01); // to be 2 bytes aligned
        }
        else{
            screenHeight = mDisplayWidth*mGameHeight/gameWidth;
        }

        android.view.ViewGroup.LayoutParams l = mGameLayout.getLayoutParams();
        l.width = screenWidth;
        l.height = screenHeight;
        mGameLayout.setLayoutParams(l);
        mGameLayout.addView(mGame);

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
            Analytics.buttonEvent(BUTTON.BACK);
            removeHideControlsTimer();
            if (mInterstitialHelper.show()) {
                if( mGame != null ) {
                    mGame.onStop();
                }
                if (mAdView != null) {
                    mAdView.pause();
                }
                return;
            }
            mGame.exitApp();
            refreshTimer = false;
            break;
        case R.id.controls_change_speed_button:
            Analytics.buttonEvent(BUTTON.CHANGE_SPEED);
            mGame.sendNativeKeyPress(KeyEvent.KEYCODE_O);
            break;
        case R.id.controls_skip_button:
            Analytics.buttonEvent(BUTTON.SKIP);
            mGame.sendNativeKeyPress(KeyEvent.KEYCODE_S);
            break;
        case R.id.controls_auto_button:
            Analytics.buttonEvent(BUTTON.AUTO);
            mGame.sendNativeKeyPress(KeyEvent.KEYCODE_A);
            break;
        case R.id.controls_settings_button:
            Analytics.buttonEvent(BUTTON.SETTINGS);
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
        if (mDialog != null && mDialog.isShowing()) {
            final AdView ad = mDialog.getAdView();
            if (ad != null) {
                ad.pause();
            }
        }
        if (mAdView != null) {
            mAdView.pause();
        }
	}

    @Override
    protected void onResume()
    {
        super.onResume();
        if( mGame != null ) {
            mGame.onResume();
        }
        if (mDialog != null && mDialog.isShowing()) {
            final AdView ad = mDialog.getAdView();
            if (ad != null) {
                ad.resume();
            }
        }
        if (mAdView != null) {
            mAdView.resume();
        }
	}

	@Override
	protected void onStart() {
	    super.onStart();
	    Analytics.start(this);
	}

    @Override
    protected void onStop()
    {
        super.onStop();
        if( mGame != null ) {
            mGame.onStop();
        }
        Analytics.stop(this);

        // Adblocker used
        Analytics.sendAdblockerUsed(this);

        // Send if wifi is enabled
        Analytics.sendWifiEnabledEvent(this);

        // Send session time
        long sessionTime = Math.round((System.currentTimeMillis() - mSessionStart) / 1000.0);
        Analytics.sendSessionLength(this, sessionTime);
    }

    @Override
    protected void onDestroy()
    {
        if( mGame != null ) {
            mGame.exitApp();
        }
		super.onDestroy();
        if (mDialog != null) {
            final AdView ad = mDialog.getAdView();
            if (ad != null) {
                ad.destroy();
            }
        }
        if (mAdView != null) {
            mAdView.destroy();
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        updateControlPreferences();
        double scaleFactor = mDialog.getFontScalingFactor();
        Analytics.changeEvent(CHANGE.TEXT_SCALE, Math.round(scaleFactor * 100));
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

	private boolean isDebug() {
        return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
}
