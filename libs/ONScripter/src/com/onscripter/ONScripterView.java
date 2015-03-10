package com.onscripter;

import java.io.File;
import java.lang.ref.WeakReference;

import android.app.Activity;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.onscripter.exception.NativeONSException;


/**
 * This class is a wrapper to render ONScripter games inside a single view object
 * without any extra code. All you need to do is create the object by the
 * constructor and add it to your layout. Then you can set a ONScripterEventListener
 * if you want to. Finally it is your job to set the size of this view.
 *
 * You must also pass the following events from your activity for this ONScripterView
 * to act normally: <b>onPause, onResume, and onUserLeaveHint</b> and also on the
 * <i>onDestroy</i> event you should call <b>exitApp()</b>. Fail to do any of these
 * will cause the game to crash.
 * @author Matthew Ng
 *
 */
public class ONScripterView extends TracedONScripterView {

    private static final int MSG_AUTO_MODE = 1;
    private static final int MSG_SKIP_MODE = 2;
    private static final int NUM_CONTROL_MODES = 2;

    public enum UserMessage {
        CORRUPT_SAVE_FILE
    };

    private final AudioThread mAudioThread;
    private final String mCurrentDirectory;
    private final Activity mActivity;

    // Native methods
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
        this(activity, gameDirectory, fontPath, null, false, false);
    }

    /**
     * Constructor with font path
     * @param activity
     * @param gameDirectory
     * @param fontPath
     * * @param shouldRenderOutline chooses whether to show outline on font
     */
    public ONScripterView(Activity activity, String gameDirectory, String fontPath, boolean useHQAudio, boolean shouldRenderOutline) {
        this(activity, gameDirectory, fontPath, null, useHQAudio, shouldRenderOutline);
    }

    /**
     * Full constructor with the outline code
     * @param activity
     * @param gameDirectory is the location of the game
     * @param fontPath is the location of the font
     * @param savePath is the location of the save files
     * @param shouldRenderOutline chooses whether to show outline on font
     */
    public ONScripterView(Activity activity, String gameDirectory, String fontPath, String savePath, boolean useHQAudio, boolean shouldRenderOutline) {
        super(activity, gameDirectory, fontPath, savePath, useHQAudio, shouldRenderOutline);

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
        public void onNativeError(NativeONSException e, String line, String backtrace);
        public void onUserMessage(UserMessage messageId);
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
                if (msg.what <= NUM_CONTROL_MODES) {
                    view.updateControls(msg.what, (Boolean)msg.obj);
                } else {
                    view.sendUserMessage(msg.what);
                }
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

    private void sendUserMessage(int messageIdFromNDK) {
        if (mListener != null) {
            switch(messageIdFromNDK) {
            case 3:
                mListener.onUserMessage(UserMessage.CORRUPT_SAVE_FILE);
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
        if (!allowVideo()) {
            return;
        }
        if (mListener != null) {
            File video = new File(mCurrentDirectory + "/" + new String(filename).replace("\\", "/"));
            if (video.exists() && video.canRead()) {
                ONScripterTracer.traceVideoStartEvent();
                mListener.videoRequested(video.getAbsolutePath(), clickToSkip, shouldLoop);
            } else {
                Log.e("ONScripterView", "Cannot play video because it either does not exist or cannot be read. File: " + video.getPath());
            }
        }
    }

    @Override
    public void receiveException(String message, String currentLineBuffer, String backtrace) {
        super.receiveException(message, currentLineBuffer, backtrace);
        if (currentLineBuffer != null) {
            Log.e("ONScripter", message + "\nCurrent line: " + currentLineBuffer + "\n" + backtrace);
        } else {
            Log.e("ONScripter", message + "\n" + backtrace);
        }
        if (mListener != null) {
            NativeONSException exception = new NativeONSException(message);
            mListener.onNativeError(exception, currentLineBuffer, backtrace);
        }
    }

    /** Load the libraries */
    static {
        System.loadLibrary("sdl");
        System.loadLibrary("application");
        System.loadLibrary("sdl_main");
    }
}
