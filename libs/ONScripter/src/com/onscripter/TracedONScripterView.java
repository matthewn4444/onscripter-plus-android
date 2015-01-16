package com.onscripter;

import java.io.File;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.os.Environment;

class TracedONScripterView extends DemoGLSurfaceView {

    private ONScripterTracer.Playback mPlayback;
    final String rootFolder;

    public TracedONScripterView(Activity context, String currentDirectory,
            String fontPath, String savePath, boolean useHQAudio,
            boolean shouldRenderOutline) {
        super(context, currentDirectory, fontPath, savePath, useHQAudio, shouldRenderOutline);

        rootFolder = savePath != null ? savePath : currentDirectory;

//        String traceFile = getContext().getApplicationContext().getFilesDir() + "/" + ONScripterTracer.TRACE_FILE_NAME;
        String traceFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                + "/" + ONScripterTracer.TRACE_FILE_NAME;
        if (isDebug() && ONScripterTracer.playbackEnabled() && new File(traceFile).exists()) {
            mPlayback = new ONScripterTracer.Playback(this, traceFile);
            mPlayback.start();
        } else {
            ONScripterTracer.init(context);
            ONScripterTracer.open(false);
        }
    }

    public void receiveException(String message, String currentLineBuffer, String backtrace) {
        if (mPlayback != null) {
            mPlayback.stop();
            mPlayback = null;
        } else {
            ONScripterTracer.traceCrash();
            ONScripterTracer.close();
            ONScripterTracer.open();
        }
    }

    @Override
    public void exitApp() {
        if (mPlayback != null) {
            mPlayback.stop();
            mPlayback = null;
        } else {
            ONScripterTracer.close();
        }
        super.exitApp();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mPlayback != null) {
            mPlayback.stop();
            mPlayback = null;
        } else {
            ONScripterTracer.close();
        }
    }

    @Override
    public void onResume() {
        if (mPlayback == null) {
            ONScripterTracer.open();
        }
        super.onResume();
    }

    @Override
    protected void nativeKey(int keyCode, int down) {
        if (mPlayback == null) {
            ONScripterTracer.traceKeyEvent(keyCode, down);
        }
        super.nativeKey(keyCode, down);
    }

    @Override
    protected void nativeMouse(int x, int y, int action) {
        if (mPlayback == null) {
            ONScripterTracer.traceMouseEvent(x, y, action);
        }
        super.nativeMouse(x, y, action);
    }

    /**
     * Always skip videos when playing back traces
     * @return
     */
    protected boolean allowVideo() {
        return mPlayback == null;
    }

    void triggerMouseEvent(int x, int y, int action) {
        nativeMouse( x, y, action );
    }

    void triggerKeyEvent(int keyCode, int down) {
        nativeKey( keyCode, down );
    }

    protected boolean isDebug() {
        return (getContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    protected void onLoadFile(String filename, String savePath) {
        ONScripterTracer.traceLoadEvent(filename, savePath);
    }
}
