package com.onscripter.plus;

import java.lang.ref.WeakReference;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class ONScripter extends Activity implements OnSeekBarChangeListener, OnClickListener, OnDismissListener, OnTouchListener
{
    public static final String CURRENT_DIRECTORY_EXTRA = "current_directory_extra";
    public static final String USE_DEFAULT_FONT_EXTRA = "use_default_font_extra";

    private static final int MSG_AUTO_MODE = 1;
    private static final int MSG_SKIP_MODE = 2;

    private static int BEZEL_SWIPE_DISTANCE = 0;
    private static int HIDE_CONTROLS_TIMEOUT_SECONDS = 0;

    private static UpdateHandler sHandler;
    private static String DISPLAY_CONTROLS_KEY;
    private static String SWIPE_GESTURES_KEY;
    private static String[] SWIPE_GESTURES_ENTRIES;

    private VNSettingsDialog mDialog;
    private FrameLayout mGameLayout;
    private LinearLayout mLeftLayout;
    private LinearLayout mRightLayout;
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

        mCurrentDirectory = getIntent().getStringExtra(CURRENT_DIRECTORY_EXTRA);
        mUseDefaultFont = getIntent().getBooleanExtra(USE_DEFAULT_FONT_EXTRA, false);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Setup layout
        View layout = getLayoutInflater().inflate(R.layout.onscripter, null);
        setContentView(layout);
        mGameLayout = (FrameLayout)findViewById(R.id.game_wrapper);
        mLeftLayout = (LinearLayout)findViewById(R.id.left_menu);
        mRightLayout = (LinearLayout)findViewById(R.id.right_menu);
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
        mDialog = new VNSettingsDialog(this);
        mDialog.setOnDimissListener(this);

        mGestureScanner = new GestureDetector(bezelGesture);
        layout.setOnTouchListener(this);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        updateControlPreferences();

        sHandler = new UpdateHandler(this);
        mHideControlsHandler = new Handler();

        // Get the dimensions of the screen
        Display disp = ((WindowManager)this.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        mDisplayWidth = disp.getWidth();
        mDisplayHeight = disp.getHeight();

        runSDLApp();
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
            SWIPE_GESTURES_ENTRIES = getResources().getStringArray(R.array.settings_controls_swipe_entries);
        }
        boolean allowBezelControls = mPrefs.getBoolean(DISPLAY_CONTROLS_KEY, false);
        if (allowBezelControls) {
            hideControls();
        } else {
            showControls();
        }

        // Update the bezel swipe conditions
        if (allowBezelControls) {
            int index = -1;
            String gestureValue = mPrefs.getString(SWIPE_GESTURES_KEY,
                    getString(R.string.settings_controls_swipe_default_entry));
            for (int i = 0; i < SWIPE_GESTURES_ENTRIES.length; i++) {
                if (gestureValue.equals(SWIPE_GESTURES_ENTRIES[i])) {
                    index = i;
                    break;
                }
            }
            switch(index) {
            case 0:     // All
                mAllowLeftBezelSwipe = true;
                mAllowRightBezelSwipe = true;
                break;
            case 1:     // Left
                mAllowLeftBezelSwipe = true;
                mAllowRightBezelSwipe = false;
                break;
            case 2:     // Right
                mAllowLeftBezelSwipe = false;
                mAllowRightBezelSwipe = true;
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

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress,
            boolean fromUser) {
        nativeSetSentenceFontScale(Math.floor(progress /10)/10.0 + 1);
    }
    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }
    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }
    @Override
    public void onClick(View v) {
        boolean refreshTimer = true;
        switch(v.getId()) {
        case R.id.controls_quit_button:
            removeHideControlsTimer();
            hideControls();
            mGLView.nativeKey( KeyEvent.KEYCODE_MENU, 2 ); // send SDL_QUIT
            refreshTimer = false;
            break;
        case R.id.controls_change_speed_button:
            mGLView.nativeKey( KeyEvent.KEYCODE_O, 1 );
            mGLView.nativeKey( KeyEvent.KEYCODE_O, 0 );
            break;
        case R.id.controls_skip_button:
            mGLView.nativeKey( KeyEvent.KEYCODE_S, 1 );
            mGLView.nativeKey( KeyEvent.KEYCODE_S, 0 );
            break;
        case R.id.controls_auto_button:
            mGLView.nativeKey( KeyEvent.KEYCODE_A, 1 );
            mGLView.nativeKey( KeyEvent.KEYCODE_A, 0 );
            break;
        case R.id.controls_settings_button:
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

	SimpleOnGestureListener bezelGesture = new SimpleOnGestureListener() {
	    @Override
        public boolean onDown(MotionEvent e) {
	        return true;
	    }
	    @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
	        if (BEZEL_SWIPE_DISTANCE == 0) {
	            BEZEL_SWIPE_DISTANCE = getResources().getInteger(R.integer.bezel_swipe_distance);
	        }
	        if (mAllowLeftBezelSwipe && e1.getX() < BEZEL_SWIPE_DISTANCE) {
	            // Left Bezel
	            showControls();
	            return true;
	        } else if (mAllowRightBezelSwipe && e1.getX() > mDisplayWidth - BEZEL_SWIPE_DISTANCE) {
	            // Right Bezel
	            showControls();
                return true;
            }
	        return false;
	    };
	};

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return mGestureScanner.onTouchEvent(event);
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
	}

	@Override
	protected void onStop()
	{
		super.onStop();
		if( mGLView != null ) {
            mGLView.onStop();
        }
	}

	@Override
	protected void onDestroy()
	{
		if( mGLView != null ) {
            mGLView.exitApp();
        }
		super.onDestroy();
	}

	@Override
    public void onDismiss(DialogInterface dialog) {
	    updateControlPreferences();
	    refreshHideControlsTimer();
    }

	private void hideControls() {
	    if (mLeftLayout.getVisibility() != View.VISIBLE) {
            return;
        }
	    mLeftLayout.setVisibility(View.GONE);      // TODO animate this in from the left
	    mRightLayout.setVisibility(View.GONE);     // TODO animate this in from the right
	}

	private void showControls() {
	    if (mLeftLayout.getVisibility() == View.VISIBLE) {
	        return;
	    }
	    mLeftLayout.setVisibility(View.VISIBLE);      // TODO animate this in from the left
        mRightLayout.setVisibility(View.VISIBLE);     // TODO animate this in from the right
        refreshHideControlsTimer();
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
