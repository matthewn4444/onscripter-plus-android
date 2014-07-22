package com.onscripter;

import java.io.File;
import java.lang.ref.WeakReference;

import android.app.Activity;
import android.os.Handler;
import android.os.Message;
import android.util.Log;


public class ONScripterView extends DemoGLSurfaceView {

    private static final int MSG_AUTO_MODE = 1;
    private static final int MSG_SKIP_MODE = 2;

    private final AudioThread mAudioThread;
    private final String mCurrentDirectory;
    private final Activity mActivity;

    // Native methods
    private native int nativeInitJavaCallbacks();
    private native int nativeGetWidth();
    private native int nativeGetHeight();
    private native void nativeSetSentenceFontScale(double scale);
    private native int nativeGetDialogFontSize();

    /**
     * Default constructor
     * @param activity
     * @param gameDirectory
     */
    public ONScripterView(Activity activity, String gameDirectory) {
        this(activity, gameDirectory, null);
    }

    /**
     * Constructor with font path
     * @param activity
     * @param gameDirectory
     * @param fontPath
     */
    public ONScripterView(Activity activity, String gameDirectory, String fontPath) {
        this(activity, gameDirectory, fontPath, false);
    }

    /**
     * Full constructor with the outline code
     * @param activity
     * @param gameDirectory is the location of the game
     * @param fontPath is the location of the font
     * @param shouldRenderOutline chooses whether to show outline on font
     */
    public ONScripterView(Activity activity, String gameDirectory, String fontPath, boolean shouldRenderOutline) {
        super(activity, gameDirectory, fontPath, shouldRenderOutline);
        nativeInitJavaCallbacks();

        mActivity = activity;
        mCurrentDirectory = gameDirectory;
        mAudioThread = new AudioThread(activity);

        sHandler = new UpdateHandler(this);
        setFocusableInTouchMode(true);
        setFocusable(true);
        requestFocus();
    }

    /** Receive State Updates from Native Code */
    private static UpdateHandler sHandler;

    private ONScripterEventListener mListener;

    public interface ONScripterEventListener {
        public void autoStateChanged(boolean selected);
        public void skipStateChanged(boolean selected);
        public void videoRequested(String filename, boolean clickToSkip, boolean shouldLoop);
    }

    static class UpdateHandler extends Handler {
        private final WeakReference<ONScripterView> mThisView;
        UpdateHandler(ONScripterView activity) {
            mThisView = new WeakReference<ONScripterView>(activity);
        }
        @Override
        public void handleMessage(Message msg)
        {
            ONScripterView view = mThisView.get();
            if (view != null) {
                view.updateControls(msg.what, (Boolean)msg.obj);
            }
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

    private void updateControls(int mode, boolean flag) {
        if (mListener != null) {
            switch(mode) {
            case MSG_AUTO_MODE:
                mListener.autoStateChanged(flag);
                break;
            case MSG_SKIP_MODE:
                mListener.skipStateChanged(flag);
                break;
            }
        }
    }

    public void setONScripterEventListener(ONScripterEventListener listener) {
        mListener = listener;
    }

    /** Send native key press to the app */
    public void sendNativeKeyPress(int keyCode) {
        nativeKey(keyCode, 1);
        nativeKey(keyCode, 0);
    }

    /** Get the font size of the text currently showing */
    public int getGameFontSize() {
        return nativeGetDialogFontSize();
    }

    @Override
    public void onPause() {
        super.onPause();
        mAudioThread.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        mAudioThread.onResume();
    }

    public int getGameWidth() {
        return nativeGetWidth();
    }

    public int getGameHeight() {
        return nativeGetHeight();
    }

    public void setFontScaling(double scaleFactor) {
        nativeSetSentenceFontScale(scaleFactor);
    }

    public void playVideo(char[] filename, boolean clickToSkip, boolean shouldLoop){
        if (mListener != null) {
            File video = new File(mCurrentDirectory + "/" + new String(filename));
            if (video.exists() && video.canRead()) {
                mListener.videoRequested(video.getAbsolutePath(), clickToSkip, shouldLoop);
            } else {
                Log.e("ONScripterView", "Cannot play video because it either does not exist or cannot be read. File: " + video.getPath());
            }
        }
    }

    /** Load the libraries */
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
