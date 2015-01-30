package com.onscripter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

public class ONScripterTracer {
    public static String TRACE_FILE_NAME = "trace.log";
    public static String SAVE_FILE_NAME = "save.dat";

    private static File sTraceFile;
    private static boolean sIsOpened = false;
    private static StringBuilder sBuffer;
    private static long sStartTime = 0;
    private static long sSkipTime = 0;
    private static long sLastLoggedTime = 0;
    private static boolean sHasLoadedSaveFile = false;
    private static boolean sAllowPlayback = false;
    private static int sViewWidth = 0;
    private static int sViewHeight = 0;

    private final static char KEY_EVENT = 'k';
    private final static char MOUSE_EVENT = 'm';
    private final static char CRASH_EVENT = 'c';
    private final static char LOAD_EVENT = 'l';

    private ONScripterTracer(){}

    public static void init(Context ctx) {
        sTraceFile = new File(ctx.getApplicationContext().getFilesDir() + "/" + TRACE_FILE_NAME);
    }

    public static void traceKeyEvent(int keyCode, int down) {
        traceText(KEY_EVENT + "," + keyCode + "," + down);
    }

    public static void traceMouseEvent(int x, int y, int action) {
        traceText(MOUSE_EVENT + "," + x + "," + y + "," + action);
    }

    public static void traceVideoStartEvent() {
        sSkipTime -= System.currentTimeMillis();
    }

    public static void traceVideoEndEvent() {
        sSkipTime += System.currentTimeMillis();
    }

    public static void traceLoadEvent(Context c, String saveFilePath, String savePath) {
        // Copy save file to the private application folder, it is not worth fixing when 2 load events overlap
        new CopySaveFileTask(c, saveFilePath).execute();
        restartTrace();
        traceText(LOAD_EVENT + (savePath != null ? savePath : ""));
        sHasLoadedSaveFile = true;
    }

    public static void traceViewDimensions(int width, int height) {
        sViewWidth = width;
        sViewHeight = height;
    }

    public static void traceCrash() {
        traceText(CRASH_EVENT + "");
    }

    public static boolean open() {
        return open(true);
    }

    public static boolean hasLoadedSaveFile() {
        return sHasLoadedSaveFile;
    }

    public static void allowPlayback(boolean flag) {
        sAllowPlayback = flag;
    }

    public static boolean playbackEnabled() {
        return sAllowPlayback;
    }

    public static long getCurrentLogTime() {
        return sLastLoggedTime;
    }

    public static synchronized boolean open(boolean append) {
        if (!sIsOpened) {
            sIsOpened = true;
            if (!append) {
                sTraceFile.delete();
                restartTrace();
            } else if (sStartTime > 0) {
                // Reopened so we need to skip the time that was closed
                sSkipTime += System.currentTimeMillis();
            }
            if (sStartTime == 0) {
                sStartTime = System.currentTimeMillis();
            }

            // This runs once on the first open
            if (sBuffer == null) {
                reset();
            }
            return true;
        }
        return false;
    }

    public static synchronized void close() {
        if (sIsOpened && sBuffer != null) {
            sSkipTime -= System.currentTimeMillis();
            sIsOpened = false;
            PrintWriter writer = null;
            try {
                writer = new PrintWriter(sTraceFile);
                writer.print(sViewWidth);
                writer.append(',').println(sViewHeight);
                writer.print(sBuffer);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } finally {
                if (writer != null) {
                    writer.close();
                }
            }
        }
    }

    private static void restartTrace() {
        reset();
        sStartTime = System.currentTimeMillis();
    }

    private static void reset() {
        sBuffer = new StringBuilder();
        sHasLoadedSaveFile = false;
        sLastLoggedTime = 0;
        sSkipTime = 0;
    }

    private static void traceText(String text) {
        if (sBuffer != null) {
            sLastLoggedTime = System.currentTimeMillis() - sSkipTime - sStartTime;
            sBuffer.append(sLastLoggedTime);
            sBuffer.append(',');
            sBuffer.append(text);
            sBuffer.append('\n');
        }
    }

    private static boolean copy(File src, File dst) {
        OutputStream out = null;
        InputStream in = null;
        try {
            in = new FileInputStream(src);
                out = new FileOutputStream(dst);

            // Transfer bytes from in to out
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {}
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {}
            }
        }
    }

    public static class Playback {
        private final File mTraceFile;
        private boolean mCanBeUsed = true;
        private boolean mIsPlaying = false;
        private final TracedONScripterView mGame;
        private Thread mThread;

        public Playback(TracedONScripterView gameView, String tracePath) {
            mTraceFile = new File(tracePath);
            mGame = gameView;
        }

        public void start() {
            if (mCanBeUsed && !mIsPlaying && mThread == null) {
                mIsPlaying = true;

                mThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        // Read file
                        ArrayList<String[]> commands = new ArrayList<String[]>();
                        BufferedReader br = null;
                        int width = 0, height = 0;
                        try {
                            br = new BufferedReader(new FileReader(mTraceFile));

                            // Parse the first line for the game width and height from that trace
                            String line = br.readLine();
                            if (line != null) {
                                String[] parts = line.split(",");
                                if (parts.length < 2) {
                                    toast("First line does not contain height and width of gameview!");
                                    return;
                                }
                                width = Integer.parseInt(parts[0]);
                                height = Integer.parseInt(parts[1]);
                                if (width <= 1 || height <= 1) {
                                    toast("Width or height is less than or equal to 1 and we cannot run playback");
                                    return;
                                }

                                // Parse the second line and check if we are loading any files
                                line = br.readLine();
                                if (line != null) {
                                    parts = line.split(",");
                                    if (parts[1].charAt(0) == LOAD_EVENT) {
                                        if (parts.length < 2) {
                                            toast("Playback cannot load because too little arguments");
                                            return;
                                        }

                                        // See if the save file is in the downloads folder
                                        File saveFile = new File(Environment.getExternalStoragePublicDirectory(
                                                Environment.DIRECTORY_DOWNLOADS) + "/" + SAVE_FILE_NAME);
                                        if (!saveFile.exists()) {
                                            toast("Unable to playback without the save file in the downloads folder! (save.dat)");
                                            return;
                                        }

                                        // Copy the save file from Downloads folder to game save folder as save1.dat
                                        File dst = new File(mGame.rootFolder + "/" + (parts.length >= 4 ? parts[3] : "") + "save1.dat");
                                        if (!copy(saveFile, dst)) {
                                            toast("Playback failed because could not copy save file");
                                            return;
                                        }

                                        // Load the game
                                        try {
                                            threadWait(2000);
                                            loadFirstGame();
                                        } catch (InterruptedException e1) {
                                            e1.printStackTrace();
                                            return;
                                        }
                                    } else if (parts.length < 1) {
                                        toast("First line does not have enough arguments");
                                    } else {
                                        commands.add(parts);
                                    }
                                }
                            }

                            // Parse the rest of the file
                            while((line = br.readLine()) != null) {
                                if (!mIsPlaying) {
                                    return;
                                }
                                if (line.trim().length() > 0) {
                                    commands.add(line.split(","));
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            toast("Playback: Failed to read file");
                            return;
                        } finally {
                            try {
                                if (br != null) {
                                    br.close();
                                }
                            } catch (IOException e){}
                        }

                        // Playback the data
                        toast("Starting playback...");

                        long startTime = System.currentTimeMillis();
                        try {
                            for (String[] command: commands) {
                                long time = Long.parseLong(command[0]);
                                char type = command[1].charAt(0);

                                if (!mIsPlaying) {
                                    return;
                                }

                                // See when we should execute the next command
                                if (time + startTime > System.currentTimeMillis()) {
                                    try {
                                        threadWait(time + startTime - System.currentTimeMillis());
                                        if (!mIsPlaying) {
                                            return;
                                        }
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                        toast("Playback: Thread interruption");
                                    }
                                }

                                // Execute command
                                switch(type) {
                                case KEY_EVENT:
                                    int keyCode = Integer.parseInt(command[2]);
                                    int downState = Integer.parseInt(command[3]);
                                    mGame.triggerKeyEvent(keyCode, downState);
                                    Log.v("ONScripter Playback", "Key Event [" + time + "]: " + command[2] + ", " + command[3]);
                                    break;
                                case MOUSE_EVENT:
                                    int x = (int)Math.round(Integer.parseInt(command[2]) * (sViewWidth * 1.0 / width));
                                    int y = (int)Math.round(Integer.parseInt(command[3]) * (sViewHeight* 1.0 / height));
                                    int action = Integer.parseInt(command[4]);
                                    mGame.triggerMouseEvent(x, y, action);
                                    Log.v("ONScripter Playback", "Mouse Event [" + time + "]: (" + x + "," + y + "), " + command[4]);
                                    break;
                                case CRASH_EVENT:
                                    Log.v("ONScripter Playback", "Playback was logged to crash now");
                                    stop();
                                    return;
                                }
                            }
                        } catch (NumberFormatException e) {
                            e.printStackTrace();
                            toast("Plaback: Failed to parse numbers from file correctly");
                        } catch (ArrayIndexOutOfBoundsException e) {
                            e.printStackTrace();
                            toast("Playback: Failed to parse file because accessing data out of bounds");
                        }
                        stop();
                    }
                });
                mThread.start();
            }
        }

        public void stop() {
            if (mCanBeUsed && mIsPlaying && mThread != null) {
                mCanBeUsed = false;
                mIsPlaying = false;
                synchronized (mThread) {
                    mThread.notify();
                }
                toast("Finished playback");
            }
        }

        private void loadFirstGame() throws InterruptedException {
            toast("Loading save file");
            Log.v("ONScripter Playback", "Load first game");
            mGame.nativeKey(KeyEvent.KEYCODE_1, 1);
            mGame.nativeKey(KeyEvent.KEYCODE_1, 0);
            threadWait(500);
            mGame.nativeMouse(0, 0, 0);
            mGame.nativeMouse(0, 0, 1);
            threadWait(1000);
        }

        private void threadWait(long time) throws InterruptedException {
            synchronized (mThread) {
                Thread.currentThread().wait(time);
            }
        }

        private void toast(final String message) {
            ((Activity)mGame.getContext()).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mGame.getContext(), message, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    static private class CopySaveFileTask extends AsyncTask<Void, Void, Void> {
        Context mCtx;
        String mFilePath;

        public CopySaveFileTask(Context c, String f) {
            mCtx = c;
            mFilePath = f;
        }

        @Override
        protected Void doInBackground(Void... params) {
            File src = new File(mFilePath);
            if (src.exists()) {
                File dst = new File(mCtx.getApplicationContext().getFilesDir() + "/" + SAVE_FILE_NAME);
                copy(src, dst);
            }
            return null;
        }
    }
}
