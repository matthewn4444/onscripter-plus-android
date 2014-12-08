package com.onscripter;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
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
public class ONScripterView extends DemoGLSurfaceView {

    private static final int MSG_AUTO_MODE = 1;
    private static final int MSG_SKIP_MODE = 2;

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

    public void receiveException(String message, String currentLineBuffer, String backtrace) {
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

    /* Getting caption from file */

    /**
     * This gets the name of passed path. It gets the caption of the game
     * and returns it regardless of the language.
     *
     * Currently only nscript.dat, 0.txt and 00.txt are supported (in terms
     * of encryption). If these files are not found or if the file is
     * corrupted, it will return null.
     *
     * This is synchronized as it does I/O operations, please place this on
     * a thread.
     * @param gamePath is the folder with nscript.dat, 0.txt or 00.txt in it
     * @return the name of the game (by its caption)
     */
    static public String getGameName(String gamePath) {
        final String[] names = {"nscript.dat", "0.txt", "00.txt"};      // Too lazy to support "nscr_sec.dat & nscript.___" encryption

        for (int i = 0; i < names.length; i++) {
            String path = gamePath + "/" + names[i];
            if (new File(path).exists()) {
                return GetONScripterCaption(path);
            }
        }
        return null;
    }

    static private int UTF8ByteLength(char c) {
        // 1 Byte (0 to 127)
        if ((c & 0x80) == 0) {
            return 1;
        }
        // 2 Bytes (128 - 2047)
        else if ((c & 0xE0) == 0xC0) {
            return 2;
        }
        // 3 Bytes (2048 to 55295 and 57344 to 65535)
        else if ((c & 0xF0) == 0xE0) {
            return 3;
        }
        // 4 Bytes (65536 to 1114111)
        else if ((c & 0xF8) == 0xF0) {
            return 4;
        }
        return -1;
    }

    static private boolean isTwoBytes(char c) {
        return ( (c & 0xe0) == 0xe0 || (c & 0xe0) == 0x80 );
    }

    static private boolean isKorean(int x) {
        return
        /* Hangul syllables */  ((x >= 0xB0A1 && x <= 0xC8FE)
        /* Standard Korean */ || (x >= 0xA141 && x <= 0xA974)
                                    ) == true;
    }

    static private String GetONScripterCaption(String filePath) {
        File script = new File(filePath);
        InputStream in = null;
        BufferedReader br = null;
        try {
            int length = 0;

            // Script is plain text, makes life easier
            if (filePath.endsWith(".txt")) {
                br = new BufferedReader(new FileReader(script));
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    // Match for caption ", ignoring whitespace
                    if (line.startsWith("caption")
                        && (line.charAt(7) == ' ' || line.charAt(7) < '\n')
                        && line.charAt(8) == '"') {
                        int endPos = line.indexOf('"', 9);
                        if (endPos == -1) {
                            return null;
                        }
                        return line.substring(9, endPos);
                    }
                }
            } else {
                final int blockSize = 4096;
                byte[] buffer = new byte[blockSize];
                int pos = 0, i = 0;

                in = new BufferedInputStream(new FileInputStream(script));
                while((length = in.read(buffer)) > 0) {
                    for (i= 0; i < length; i++) {
                        char c = (char) (buffer[i] & 0xFF);

                        // Detect and skip spaces and tabs
                        if (c == (0x20 ^ 0x84) || c < (0xA ^ 0x84)) {
                            continue;
                        }
                        // Detect new line
                        else if (c == (0xA ^ 0x84)) {
                            pos = i + 1;
                            continue;
                        }
                        // Check if this is a caption text
                        else {
                            if (i + 9 < length &&           // Make sure there's enough chars to scan
                                (c ^ 0x84) == 'c'
                                && ((buffer[++i] ^ 0x84) & 0xFF) == 'a'
                                && ((buffer[++i] ^ 0x84) & 0xFF) == 'p'
                                && ((buffer[++i] ^ 0x84) & 0xFF) == 't'
                                && ((buffer[++i] ^ 0x84) & 0xFF) == 'i'
                                && ((buffer[++i] ^ 0x84) & 0xFF) == 'o'
                                && ((buffer[++i] ^ 0x84) & 0xFF) == 'n'
                                && (((buffer[++i] ^ 0x84) & 0xFF) == ' '        // Can be a tab or space
                                || ((buffer[i] ^ 0x84) & 0xFF) < '\n')
                                && ((buffer[++i] ^ 0x84) & 0xFF) == '"'
                                ) {

                                // Scan bytes to find the end quote
                                int startPos = i;
                                int utf8Size = 0;
                                boolean allowInterpretUTF8 = true;

                                while(i + 1 < length) {
                                   c = (char)((buffer[++i] ^ 0x84) & 0xFF);
                                   if (c == '\n') {
                                       Log.e("ONScripterView", "Incorrect format for caption!");
                                       return null;
                                   } else if (c == ' ' || c < '\n') {   // Consume white space
                                       continue;
                                   } else if (c == '"') {
                                       break;
                                   } else if (isTwoBytes(c)) {
                                       // UTF-8
                                       if ((utf8Size = UTF8ByteLength(c)) > 1) {
                                           i += utf8Size - 1;
                                       } else {
                                           allowInterpretUTF8 = false;
                                           break;
                                       }
                                   }
                                   // UTF8
                                   else if ((utf8Size = UTF8ByteLength(c)) > 1) {
                                       i += utf8Size - 1;
                                   } else {
                                       // TODO check Chinese
                                       // Check Korean
                                       if (i + 1 < length) {
                                           int index = (c & 0xFF) << 8 | (buffer[++i] ^ 0x84) & 0xFF;
                                           if (isKorean(index)) {
                                               allowInterpretUTF8 = false;
                                               break;
                                           }
                                       }
                                   }
                                }

                                // Check if scanning line was successful
                                if (i < length) {
                                    i = startPos;

                                    // Now we can convert the bytes into a Java String
                                    StringBuilder sb = new StringBuilder();
                                    while(i + 1 < length) {
                                        c = (char)((buffer[++i] ^ 0x84) & 0xFF);

                                        // Success: Found the name of the game by its caption
                                        if (c == '"') {
                                            return sb.toString();
                                        } else {
                                            int index = (c & 0xFF) << 8 | (buffer[i + 1] ^ 0x84) & 0xFF;

                                            // UTF-8
                                            if (allowInterpretUTF8 && (utf8Size = UTF8ByteLength(c)) > 1) {
                                                byte[] bytes = new byte[4];
                                                bytes[0] = (byte)c;
                                                for (int j = 1; j < utf8Size; j++) {
                                                    bytes[j] = (byte)((buffer[++i] ^ 0x84) & 0xFF);
                                                }
                                                sb.append(new String(bytes, "UTF-8").trim());
                                            }
                                            // Korean
                                            else if (isKorean(index)) {
                                                byte[] b = {(byte)c, (byte)((buffer[++i] ^ 0x84) & 0xFF)};
                                                sb.append(new String(b, "EUC-KR"));
                                            }
                                            // Japanese
                                            else if (isTwoBytes(c)) {
                                                // TODO Chinese etc
                                                byte[] b = {(byte)c, (byte)((buffer[++i] ^ 0x84) & 0xFF)};
                                                sb.append(new String(b, "SHIFT_JIS"));
                                            } else {
                                                // Set 1Byte (ASCII) characters
                                                sb.append(c);
                                            }
                                        }
                                    }
                                }
                            }

                            // Caption is not in this line, go to next line
                            while(i + 1 < length) {
                                if ((buffer[++i] & 0xFF) == (0xA ^ 0x84)) {
                                    pos = i + 1;
                                    break;
                                }
                            }
                        }
                    }

                    // Add the rest of the line to the beginning of the buffer for the next read
                    if (pos != 0) {
                        System.arraycopy(buffer, pos, buffer, 0, length - pos);
                        pos = 0;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {}
            }
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {}
            }
        }
        return null;
    }

    /** Load the libraries */
    static {
        System.loadLibrary("sdl");
        System.loadLibrary("application");
        System.loadLibrary("sdl_main");
    }
}
