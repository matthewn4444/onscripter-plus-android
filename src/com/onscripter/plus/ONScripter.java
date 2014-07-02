package com.onscripter.plus;

import java.lang.ref.WeakReference;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;

import com.bugsense.trace.BugSenseHandler;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.onscripter.plus.Analytics.BUTTON;
import com.onscripter.plus.Analytics.CHANGE;
import com.onscripter.plus.InterstitialAdHelper.AdListener;
import com.onscripter.plus.TwoStateLayout.OnSideMovedListener;
import com.onscripter.plus.VNPreferences.OnLoadVNPrefListener;

public class ONScripter extends Activity implements OnClickListener, OnDismissListener, OnSideMovedListener, OnLoadVNPrefListener
{
    public static final String CURRENT_DIRECTORY_EXTRA = "current_directory_extra";
    public static final String USE_DEFAULT_FONT_EXTRA = "use_default_font_extra";
    public static final String DIALOG_FONT_SCALE_KEY = "dialog_font_scale_key";

    private static final int MSG_AUTO_MODE = 1;
    private static final int MSG_SKIP_MODE = 2;

    private static int BEZEL_SWIPE_DISTANCE = 0;
    private static int HIDE_CONTROLS_TIMEOUT_SECONDS = 0;
    private static int CONTROL_LAYOUT_WIDTH = 0;

    private static UpdateHandler sHandler;
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

    private int mScreenWidth, mScreenHeight, mGameWidth, mGameHeight, mDisplayWidth, mDisplayHeight;
    private boolean mIsLandscape = true;

    private String mCurrentDirectory;
    private boolean mUseDefaultFont;
    private SharedPreferences mPrefs;
    private VNPreferences mVNPrefs;

    private GestureDetector mGestureScanner;
    private boolean mAllowLeftBezelSwipe;
    private boolean mAllowRightBezelSwipe;
    private Handler mHideControlsHandler;

    private DemoGLSurfaceView mGLView = null;
    private AudioThread mAudioThread = null;
    private PowerManager.WakeLock mWakelock = null;
    private native int nativeInitJavaCallbacks();
    private native int nativeGetWidth();
    private native int nativeGetHeight();
    private native void nativeSetSentenceFontScale(double scale);
    public native int nativeGetDialogFontSize();

    private AdView mAdView;
    private int mAdViewHeight;
    private InterstitialAdHelper mInterstitialHelper;
    private long mSessionStart;
    static final private double LEAVE_GAME_INTERSTITIAL_AD_PERCENT = 100;

    Runnable mHideControlsRunnable = new Runnable() {
        @Override
        public void run() {
            hideControls();
        }
    };

    static class UpdateHandler extends Handler {
        private final WeakReference<ONScripter> mActivity;
        UpdateHandler(ONScripter activity) {
            mActivity = new WeakReference<ONScripter>(activity);
        }
        @Override
        public void handleMessage(Message msg)
        {
             ONScripter activity = mActivity.get();
             if (activity != null) {
                 activity.updateControls(msg.what, (Boolean)msg.obj);
             }
        }
    }

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
        View layout = getLayoutInflater().inflate(R.layout.onscripter, null);
        setContentView(layout);
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
        CONTROL_LAYOUT_WIDTH = mLeftLayout.getLayoutParams().width;
        updateControlPreferences();

        mVNPrefs = new VNPreferences(mCurrentDirectory);
        mVNPrefs.setOnLoadVNPrefListener(this);

        mLeftLayout.setOtherLayout(mRightLayout);
        mRightLayout.setOtherLayout(mLeftLayout);

        mLeftLayout.setOnSideMovedListener(this);

        sHandler = new UpdateHandler(this);
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
                mGLView.nativeKey( KeyEvent.KEYCODE_MENU, 2 ); // send SDL_QUIT
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

        FrameLayout.LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        mAdView.setLayoutParams(params);

        ViewGroup content = (ViewGroup)findViewById(android.R.id.content);
        content.addView(mAdView);

        // Request the ads
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);
        return AdSize.BANNER.getHeightInPixels(this);
    }

    private void updateControls(int mode, boolean flag) {
        switch(mode) {
        case MSG_AUTO_MODE:
            mAutoButton.setSelected(flag);
            break;
        case MSG_SKIP_MODE:
            mSkipButton.setSelected(flag);
            break;
        }
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
        nativeInitJavaCallbacks();

        mAudioThread = new AudioThread(this);
        if (mUseDefaultFont) {
            mGLView = new DemoGLSurfaceView(this, mCurrentDirectory, LauncherActivity.DEFAULT_FONT_PATH);
        } else {
            mGLView = new DemoGLSurfaceView(this, mCurrentDirectory);
        }
        mGLView.setFocusableInTouchMode(true);
        mGLView.setFocusable(true);
        mGLView.requestFocus();

        mGameWidth = nativeGetWidth();
        mGameHeight = nativeGetHeight();

        mScreenWidth = mDisplayWidth;
        mScreenHeight = mDisplayHeight;
        mIsLandscape = true;
        if (mDisplayWidth * mGameHeight >= mDisplayHeight * mGameWidth){
            mScreenWidth = (mDisplayHeight*mGameWidth/mGameHeight) & (~0x01); // to be 2 bytes aligned
        }
        else{
            mIsLandscape = false;
            mScreenHeight = mDisplayWidth*mGameHeight/mGameWidth;
        }

        android.view.ViewGroup.LayoutParams l = mGameLayout.getLayoutParams();
        l.width = mScreenWidth;
        l.height = mScreenHeight;
        mGameLayout.setLayoutParams(l);
        mGameLayout.addView(mGLView);

        if (mWakelock == null){
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakelock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "ONScripter");
            mWakelock.acquire();
        }
    }

    public int getGameHeight() {
        return mGameHeight;
    }

    @Override
    public void onClick(View v) {
        boolean refreshTimer = true;
        switch(v.getId()) {
        case R.id.controls_quit_button:
            Analytics.buttonEvent(BUTTON.BACK);
            removeHideControlsTimer();
            if (mInterstitialHelper.show()) {
                if( mGLView != null ) {
                    mGLView.onPause();
                }
                if( mAudioThread != null ) {
                    mAudioThread.onPause();
                }
                if (mAdView != null) {
                    mAdView.pause();
                }
                return;
            }
            mGLView.nativeKey( KeyEvent.KEYCODE_MENU, 2 ); // send SDL_QUIT
            refreshTimer = false;
            break;
        case R.id.controls_change_speed_button:
            Analytics.buttonEvent(BUTTON.CHANGE_SPEED);
            mGLView.nativeKey( KeyEvent.KEYCODE_O, 1 );
            mGLView.nativeKey( KeyEvent.KEYCODE_O, 0 );
            break;
        case R.id.controls_skip_button:
            Analytics.buttonEvent(BUTTON.SKIP);
            mGLView.nativeKey( KeyEvent.KEYCODE_S, 1 );
            mGLView.nativeKey( KeyEvent.KEYCODE_S, 0 );
            break;
        case R.id.controls_auto_button:
            Analytics.buttonEvent(BUTTON.AUTO);
            mGLView.nativeKey( KeyEvent.KEYCODE_A, 1 );
            mGLView.nativeKey( KeyEvent.KEYCODE_A, 0 );
            break;
        case R.id.controls_settings_button:
            Analytics.buttonEvent(BUTTON.SETTINGS);
            removeHideControlsTimer();
            mDialog.show();
            refreshTimer = false;
            break;
        case R.id.controls_rclick_button:
            mGLView.nativeKey( KeyEvent.KEYCODE_BACK, 1 );
            mGLView.nativeKey( KeyEvent.KEYCODE_BACK, 0 );
            break;
        case R.id.controls_scroll_up_button:
            mGLView.nativeKey( KeyEvent.KEYCODE_DPAD_LEFT, 1 );
            mGLView.nativeKey( KeyEvent.KEYCODE_DPAD_LEFT, 0 );
            break;
        case R.id.controls_scroll_down_button:
            mGLView.nativeKey( KeyEvent.KEYCODE_DPAD_RIGHT, 1 );
            mGLView.nativeKey( KeyEvent.KEYCODE_DPAD_RIGHT, 0 );
            break;
        default:
            return;
        }
        if (refreshTimer) {
            refreshHideControlsTimer();
        }
    }

    public static void receiveMessageFromNDK(int mode, boolean flag) {
        if (sHandler != null) {
            Message msg = new Message();
            msg.obj = flag;
            msg.what = mode;
            sHandler.sendMessage(msg);
        }
    }

	public void playVideo(char[] filename){
		try{
			String filename2 = "file:/" + mCurrentDirectory + "/" + new String(filename);
			filename2 = filename2.replace('\\', '/');
			Log.v("ONS", "playVideo: " + filename2);
			Uri uri = Uri.parse(filename2);
			Intent i = new Intent(Intent.ACTION_VIEW);
			i.setDataAndType(uri, "video/*");
			startActivityForResult(i, -1);
		}
		catch(Exception e){
			Log.e("ONS", "playVideo error:  " + e.getClass().getName());
		}
	}

	@Override
    public void onLoadVNPref(Result result) {
        if (result == Result.NO_ISSUES) {
	        // Load scale factor
	        double scaleFactor = mVNPrefs.getFloat(DIALOG_FONT_SCALE_KEY, 1);
	        mDialog.setFontScalingFactor(scaleFactor);
	        nativeSetSentenceFontScale(scaleFactor);
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
		// TODO: if application pauses it's screen is messed up
		if( mWakelock != null ) {
            mWakelock.release();
        }
		super.onPause();
		if( mGLView != null ) {
            mGLView.onPause();
        }
		if( mAudioThread != null ) {
            mAudioThread.onPause();
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
		if( mWakelock != null && !mWakelock.isHeld() ) {
            mWakelock.acquire();
        }
		super.onResume();
		if( mGLView != null ) {
            mGLView.onResume();
        }
		if( mAudioThread != null ) {
            mAudioThread.onResume();
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
		if( mGLView != null ) {
            mGLView.onStop();
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
		if( mGLView != null ) {
            mGLView.exitApp();
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
	    nativeSetSentenceFontScale(scaleFactor);
	    mVNPrefs.putFloat(DIALOG_FONT_SCALE_KEY, (float) scaleFactor);
	    mVNPrefs.commit();
	    Analytics.changeEvent(CHANGE.TEXT_SCALE, Math.round(scaleFactor * 100));
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

	static {
		System.loadLibrary("mad");
		System.loadLibrary("bz2");
		System.loadLibrary("tremor");
		System.loadLibrary("lua");
		System.loadLibrary("sdl");
		System.loadLibrary("sdl_mixer");
		System.loadLibrary("sdl_image");
		System.loadLibrary("sdl_ttf");
		System.loadLibrary("application");
		System.loadLibrary("sdl_main");
	}
}
