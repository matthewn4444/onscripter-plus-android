package com.onscripter.plus;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;

public class ONScripter extends Activity
{
    public static final String CURRENT_DIRECTORY_EXTRA = "current_directory_extra";
    public static final String USE_DEFAULT_FONT_EXTRA = "use_default_font_extra";

    private final int num_file = 0;
    private final byte[] buf = null;
    private int screen_w, screen_h;
    private int button_w, button_h;
    private Button btn1, btn2, btn3, btn4, btn5, btn6, btn7, btn8;
    private LinearLayout layout  = null;
    private LinearLayout layout1 = null;
    private LinearLayout layout2 = null;
    private LinearLayout layout3 = null;
    private boolean mIsLandscape = true;
    private boolean mButtonVisible = true;

    private String mCurrentDirectory;
    private boolean mUseDefaultFont;

    private DemoGLSurfaceView mGLView = null;
    private AudioThread mAudioThread = null;
    private PowerManager.WakeLock wakeLock = null;
    private native int nativeInitJavaCallbacks();
    private native int nativeGetWidth();
    private native int nativeGetHeight();
    private final DataDownloader downloader = null;
    private AlertDialog.Builder alertDialogBuilder = null;
    private final ProgressDialog progDialog = null;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        SharedPreferences sp = getSharedPreferences("pref", MODE_PRIVATE);
        mButtonVisible = sp.getBoolean("button_visible", getResources().getBoolean(R.bool.button_visible));
        alertDialogBuilder = new AlertDialog.Builder(this);

        mCurrentDirectory = getIntent().getStringExtra(CURRENT_DIRECTORY_EXTRA);
        mUseDefaultFont = getIntent().getBooleanExtra(USE_DEFAULT_FONT_EXTRA, false);

        runSDLApp();
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

		int game_width  = nativeGetWidth();
		int game_height = nativeGetHeight();

		Display disp = ((WindowManager)this.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		int dw = disp.getWidth();
		int dh = disp.getHeight();

		screen_w = dw;
		screen_h = dh;
		mIsLandscape = true;
		if (dw * game_height >= dh * game_width){
			screen_w = (dh*game_width/game_height) & (~0x01); // to be 2 bytes aligned
			button_w = dw - screen_w;
			button_h = dh/6;
		}
		else{
			mIsLandscape = false;
			screen_h = dw*game_height/game_width;
			button_w = dw/6;
			button_h = dh - screen_h;
		}

		btn1 = new Button(this);
		btn1.setText(getResources().getString(R.string.button_rclick));
		btn1.setOnClickListener(new OnClickListener(){
			@Override
            public void onClick(View v){
				mGLView.nativeKey( KeyEvent.KEYCODE_BACK, 1 );
				mGLView.nativeKey( KeyEvent.KEYCODE_BACK, 0 );
			}
		});

		btn2 = new Button(this);
		btn2.setText(getResources().getString(R.string.button_lclick));
		btn2.setOnClickListener(new OnClickListener(){
			@Override
            public void onClick(View v){
				mGLView.nativeKey( KeyEvent.KEYCODE_ENTER, 1 );
				mGLView.nativeKey( KeyEvent.KEYCODE_ENTER, 0 );
			}
		});

		btn3 = new Button(this);
		btn3.setText(getResources().getString(R.string.button_left));
		btn3.setOnClickListener(new OnClickListener(){
			@Override
            public void onClick(View v){
				mGLView.nativeKey( KeyEvent.KEYCODE_DPAD_LEFT, 1 );
				mGLView.nativeKey( KeyEvent.KEYCODE_DPAD_LEFT, 0 );
			}
		});

		btn4 = new Button(this);
		btn4.setText(getResources().getString(R.string.button_right));
		btn4.setOnClickListener(new OnClickListener(){
			@Override
            public void onClick(View v){
				mGLView.nativeKey( KeyEvent.KEYCODE_DPAD_RIGHT, 1 );
				mGLView.nativeKey( KeyEvent.KEYCODE_DPAD_RIGHT, 0 );
			}
		});

		btn5 = new Button(this);
		btn5.setText(getResources().getString(R.string.button_up));
		btn5.setOnClickListener(new OnClickListener(){
			@Override
            public void onClick(View v){
				mGLView.nativeKey( KeyEvent.KEYCODE_DPAD_UP, 1 );
				mGLView.nativeKey( KeyEvent.KEYCODE_DPAD_UP, 0 );
			}
		});

		btn6 = new Button(this);
		btn6.setText(getResources().getString(R.string.button_down));
		btn6.setOnClickListener(new OnClickListener(){
			@Override
            public void onClick(View v){
				mGLView.nativeKey( KeyEvent.KEYCODE_DPAD_DOWN, 1 );
				mGLView.nativeKey( KeyEvent.KEYCODE_DPAD_DOWN, 0 );
			}
		});

		btn7 = new Button(this); // dummy button for Android 1.6
		btn7.setVisibility(View.INVISIBLE);
		btn8 = new Button(this); // dummy button for Android 1.6
		btn8.setVisibility(View.INVISIBLE);

		layout  = new LinearLayout(this);
		layout1 = new LinearLayout(this);
		layout2 = new LinearLayout(this);
		layout3 = new LinearLayout(this);

		if (mIsLandscape) {
            layout2.setOrientation(LinearLayout.VERTICAL);
        } else {
            layout.setOrientation(LinearLayout.VERTICAL);
        }

		layout1.addView(btn7);
		layout.addView(layout1, 0);

		layout.addView(mGLView, 1, new LinearLayout.LayoutParams(screen_w, screen_h));
		layout2.addView(btn1, 0);
		layout2.addView(btn2, 1);
		layout2.addView(btn3, 2);
		layout2.addView(btn4, 3);
		layout2.addView(btn5, 4);
		layout2.addView(btn6, 5);
		layout.addView(layout2, 2);

		layout3.addView(btn8);
		layout.addView(layout3, 3);

		resetLayout();

		setContentView(layout);

		if (wakeLock == null){
			PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "ONScripter");
			wakeLock.acquire();
		}
	}

	public void resetLayout()
	{
		Display disp = ((WindowManager)this.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		int dw = disp.getWidth();
		int dh = disp.getHeight();

		int bw = button_w, bh = button_h;
		int w1 = 0, h1 = 0;
		int w2 = dw, h2 = dh;
		if (mIsLandscape == true){
			if (!mButtonVisible){
				w1 = bw - bw/2;
				bw /= 2;
			}
			if (bw > bh*2) {
                bw = bh*2;
            }
			h1 = dh;
			w2 = bw;
		}
		else{
			if (!mButtonVisible){
				h1 = bh - bh/2;
				bh /= 2;
			}
			if (bh > bw*2) {
                bh = bw*2;
            }
			w1 = dw;
			h2 = bh;
		}

		btn1.setMinWidth(bw);
		btn1.setMinHeight(bh);
		btn1.setWidth(bw);
		btn1.setHeight(bh);

		btn2.setMinWidth(bw);
		btn2.setMinHeight(bh);
		btn2.setWidth(bw);
		btn2.setHeight(bh);

		btn3.setMinWidth(bw);
		btn3.setMinHeight(bh);
		btn3.setWidth(bw);
		btn3.setHeight(bh);

		btn4.setMinWidth(bw);
		btn4.setMinHeight(bh);
		btn4.setWidth(bw);
		btn4.setHeight(bh);

		btn5.setMinWidth(bw);
		btn5.setMinHeight(bh);
		btn5.setWidth(bw);
		btn5.setHeight(bh);

		btn6.setMinWidth(bw);
		btn6.setMinHeight(bh);
		btn6.setWidth(bw);
		btn6.setHeight(bh);

		if (mButtonVisible) {
            layout2.setVisibility(View.VISIBLE);
        } else {
            layout2.setVisibility(View.INVISIBLE);
        }

		layout.updateViewLayout(layout1, new LinearLayout.LayoutParams(w1, h1));
		layout.updateViewLayout(layout2, new LinearLayout.LayoutParams(w2, h2));
		layout.updateViewLayout(layout3, new LinearLayout.LayoutParams(dw-screen_w-w1-w2, dh-screen_h-h1-h2));
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
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		super.onPrepareOptionsMenu(menu);

		if (mGLView != null){
			menu.clear();
			menu.add(Menu.NONE, Menu.FIRST,   0, getResources().getString(R.string.menu_automode));
			menu.add(Menu.NONE, Menu.FIRST+1, 0, getResources().getString(R.string.menu_skip));
			menu.add(Menu.NONE, Menu.FIRST+2, 0, getResources().getString(R.string.menu_speed));

			SubMenu sm = menu.addSubMenu(getResources().getString(R.string.menu_settings));
			if (mButtonVisible) {
                sm.add(Menu.NONE, Menu.FIRST+4, 0, getResources().getString(R.string.menu_hide_buttons));
            } else {
                sm.add(Menu.NONE, Menu.FIRST+3, 0, getResources().getString(R.string.menu_show_buttons));
            }

			sm.add(Menu.NONE, Menu.FIRST+5, 0, getResources().getString(R.string.menu_version));
			menu.add(Menu.NONE, Menu.FIRST+6, 0, getResources().getString(R.string.menu_quit));
		}

		return true;
	}

    @Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		if (item.getItemId() == Menu.FIRST){
			mGLView.nativeKey( KeyEvent.KEYCODE_A, 1 );
			mGLView.nativeKey( KeyEvent.KEYCODE_A, 0 );
		} else if (item.getItemId() == Menu.FIRST+1){
			mGLView.nativeKey( KeyEvent.KEYCODE_S, 1 );
			mGLView.nativeKey( KeyEvent.KEYCODE_S, 0 );
		} else if (item.getItemId() == Menu.FIRST+2){
			mGLView.nativeKey( KeyEvent.KEYCODE_O, 1 );
			mGLView.nativeKey( KeyEvent.KEYCODE_O, 0 );
		} else if (item.getItemId() == Menu.FIRST+3){
			mButtonVisible = true;
			resetLayout();
		} else if (item.getItemId() == Menu.FIRST+4){
			mButtonVisible = false;
			resetLayout();
		} else if (item.getItemId() == Menu.FIRST+5){
			alertDialogBuilder.setTitle(getResources().getString(R.string.menu_version));
			alertDialogBuilder.setMessage(getResources().getString(R.string.version));
			alertDialogBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener(){
				@Override
                public void onClick(DialogInterface dialog, int whichButton) {
					setResult(RESULT_OK);
				}
			});
			AlertDialog alertDialog = alertDialogBuilder.create();
			alertDialog.show();
		} else if (item.getItemId() == Menu.FIRST+6){
			mGLView.nativeKey( KeyEvent.KEYCODE_MENU, 2 ); // send SDL_QUIT
		} else{
			return false;
		}

		Editor e = getSharedPreferences("pref", MODE_PRIVATE).edit();
		e.putBoolean("button_visible", mButtonVisible);
		e.commit();

		return true;
	}

	@Override
	protected void onPause()
	{
		// TODO: if application pauses it's screen is messed up
		if( wakeLock != null ) {
            wakeLock.release();
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
		if( wakeLock != null && !wakeLock.isHeld() ) {
            wakeLock.acquire();
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
