package com.onscripter.plus;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.view.KeyEvent;
import android.view.MotionEvent;


class DemoRenderer extends GLSurfaceView_SDL.Renderer {

	public DemoRenderer(Activity _context)
	{
		context = _context;
		int n = 1;
		if (LauncherActivity.gRenderFontOutline) {
            n++;
        }
		String[] arg = new String[n];
		n = 0;
		arg[n++] = "--open-only";
		if (LauncherActivity.gRenderFontOutline) {
            arg[n++] = "--render-font-outline";
        }
		nativeInit(LauncherActivity.gCurrentDirectoryPath, arg);
	}

	@Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		// nativeInit();
	}

	@Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
		//gl.glViewport(0, 0, w, h);
		gl.glMatrixMode(GL10.GL_PROJECTION);
		gl.glLoadIdentity();
		gl.glMatrixMode(GL10.GL_MODELVIEW);
		gl.glLoadIdentity();
		gl.glViewport(0, 0, w, h);
		gl.glOrthof(0.0f, w, h, 0.0f, 0.0f, 1.0f);
		nativeResize(w, h);
	}

	@Override
    public void onDrawFrame(GL10 gl) {

		nativeInitJavaCallbacks();

        // Calls main() and never returns, hehe - we'll call eglSwapBuffers() from native code
		int n = 0;
		if (LauncherActivity.gRenderFontOutline) {
            n++;
        }
		String[] arg = new String[n];
		n = 0;
		if (LauncherActivity.gRenderFontOutline) {
            arg[n++] = "--render-font-outline";
        }
		nativeInit(LauncherActivity.gCurrentDirectoryPath, arg);

	}

	public int swapBuffers() // Called from native code, returns 1 on success, 0 when GL context lost (user put app to background)
	{
		return super.SwapBuffers() ? 1 : 0;
	}

	public void exitApp() {
		 nativeDone();
	};

	private native void nativeInitJavaCallbacks();
	private native void nativeInit(String currentDirectoryPath, String[] arg);
	private native void nativeResize(int w, int h);
	private native void nativeDone();

	private Activity context = null;

	private final EGL10 mEgl = null;
	private final EGLDisplay mEglDisplay = null;
	private final EGLSurface mEglSurface = null;
	private final EGLContext mEglContext = null;
	private final int skipFrames = 0;
}

class DemoGLSurfaceView extends GLSurfaceView_SDL {
	public DemoGLSurfaceView(Activity context) {
		super(context);
		mRenderer = new DemoRenderer(context);
		setRenderer(mRenderer);
	}

	@Override
	public boolean onTouchEvent(final MotionEvent event)
	{
		// TODO: add multitouch support (added in Android 2.0 SDK)
		int action = -1;
		if( event.getAction() == MotionEvent.ACTION_DOWN ) {
            action = 0;
        }
		if( event.getAction() == MotionEvent.ACTION_UP ) {
            action = 1;
        }
		if( event.getAction() == MotionEvent.ACTION_MOVE ) {
            action = 2;
        }
		if ( action >= 0 ) {
            nativeMouse( (int)event.getX(), (int)event.getY(), action );
        }

		return true;
	}

	 public void exitApp() {
		 mRenderer.exitApp();
	 };

	@Override
	public boolean onKeyDown(int keyCode, final KeyEvent event)
	{
		if(keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN){
			Activity activity = (Activity)this.getContext();
			AudioManager audio = (AudioManager)activity.getSystemService(Context.AUDIO_SERVICE);
			int volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC) + (keyCode == KeyEvent.KEYCODE_VOLUME_UP ? 1 : (-1));
			if(volume >= 0 && volume <= audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)){
				audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, AudioManager.FLAG_SHOW_UI);
			}
			return true;
		}

		if (keyCode == KeyEvent.KEYCODE_MENU){
			super.onKeyDown(keyCode, event);
			return false;
		}
		nativeKey( keyCode, 1 );

		return true;
	 }

	@Override
	public boolean onKeyUp(int keyCode, final KeyEvent event)
	{
		if(keyCode == KeyEvent.KEYCODE_MENU){
			super.onKeyUp(keyCode, event);
			return false;
		}
		nativeKey( keyCode, 0 );
		return true;
	}

	@Override
	public void onPause() {
		nativeKey( 0, 3 ); // send SDL_ACTIVEEVENT
		super.onPause();
		surfaceDestroyed(this.getHolder());
	}

	@Override
	public void onResume() {
		super.onResume();
		nativeKey( 0, 3 ); // send SDL_ACTIVEEVENT
	}

	@Override
	public void onStop()
	{
		super.onStop();
	}

	DemoRenderer mRenderer;

	public native void nativeMouse( int x, int y, int action );
	public native void nativeKey( int keyCode, int down );
}
