package com.onscripter.plus;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.actionbarsherlock.app.SherlockFragment;
import com.onscripter.ONScripterView;
import com.onscripter.ONScripterView.ONScripterEventListener;
import com.vplayer.MediaStreamInfo;
import com.vplayer.VPlayerListener;
import com.vplayer.VPlayerView;
import com.vplayer.exception.VPlayerException;

public class ONScripterGame extends SherlockFragment implements ONScripterEventListener {
    private ONScripterView mGame;
    private VPlayerView mPlayer = null;
    private Thread mONScVideoThread = null;
    private ONScripterEventListener mListener;
    private OnGameReadyListener mReadyListener;
    private FrameLayout mGameLayout;
    private boolean mUseExternalVideo;

    // Values set after fragment is created
    private int mBoundedSize = -1;
    private boolean mIsBoundedByHeight = true;

    // Fragment bundle keys
    private static final String GameDirectoryKey = "game.directory.key";
    private static final String FontPathKey = "font.path.key";
    private static final String RenderOutlineKey = "render.outline.key";
    private static final String SavePathKey = "save.path.key";
    private static final String HQAudioKey = "hq.audio.key";

    public interface OnGameReadyListener {
        public void onReady();
    }

    private final VPlayerListener mVideoListener = new VPlayerListener() {
        @Override
        public void onMediaSourceLoaded(VPlayerException err,
                MediaStreamInfo[] streams) {
            super.onMediaSourceLoaded(err, streams);
            if (err != null) {
                // Skip video if failed to open
                Log.e("ONScripter", err.getMessage());
                finishVideo();
            } else if (mPlayer != null) {
                mPlayer.play();
            }
        }

        @Override
        public void onMediaUpdateTime(long mCurrentTimeUs, long mVideoDurationUs, boolean isFinished) {
            if (isFinished) {
                finishVideo();
            }
        }
    };

    private final OnClickListener mVideoClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            finishVideo();
        }
    };

    public static ONScripterGame newInstance(String gameDirectory, String fontPath, String savePath, boolean useHQAudio, boolean shouldRenderOutline) {
        ONScripterGame frag = new ONScripterGame();
        Bundle args = new Bundle();
        args.putString(GameDirectoryKey, gameDirectory);
        args.putString(FontPathKey, fontPath);
        args.putBoolean(HQAudioKey, useHQAudio);
        args.putBoolean(RenderOutlineKey, shouldRenderOutline);
        args.putString(SavePathKey, savePath);
        frag.setArguments(args);
        return frag;
    }

    public ONScripterGame() {
        mUseExternalVideo = false;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Bundle b = getArguments();
        mGame = new ONScripterView(getActivity(), b.getString(GameDirectoryKey),
                b.getString(FontPathKey), b.getString(SavePathKey), b.getBoolean(HQAudioKey),
                b.getBoolean(RenderOutlineKey));
        mGame.setONScripterEventListener(this);
        mGameLayout = new FrameLayout(getActivity());
        mGameLayout.addView(mGame);
        if (mReadyListener != null) {
            mReadyListener.onReady();
        }
        return mGameLayout;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Bound if called before
        if (mBoundedSize > 0) {
            if (mIsBoundedByHeight) {
                setBoundingHeight(mBoundedSize);
            } else {
                setBoundingWidth(mBoundedSize);
            }
        }
    }

    public int getGameHeight() {
        return mGame.getGameHeight();
    }

    public int getGameWidth() {
        return mGame.getGameWidth();
    }

    public int getGameFontSize() {
        return mGame.getGameFontSize();
    }

    public boolean isVideoShown() {
        return mPlayer != null && mONScVideoThread != null;
    }

    public void setBoundingHeight(int height) {
        if (mGame != null && mGameLayout.getLayoutParams() != null) {
            int gameWidth = mGame.getGameWidth();
            int gameHeight = mGame.getGameHeight();

            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) mGameLayout.getLayoutParams();
            p.width = (int) (height * gameWidth * 1.0 / gameHeight);
            p.height = height;
            mGameLayout.setLayoutParams(p);
        } else {
            mBoundedSize = height;
            mIsBoundedByHeight = true;
        }
    }

    public void useExternalVideo(boolean shouldUseExternal) {
        mUseExternalVideo = shouldUseExternal;
    }

    public void setBoundingWidth(int width) {
        if (mGame != null && mGameLayout.getLayoutParams() != null) {
            int gameWidth = mGame.getGameWidth();
            int gameHeight = mGame.getGameHeight();

            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) mGameLayout.getLayoutParams();
            p.height = (int) (width * gameHeight * 1.0 / gameWidth);
            p.width = width;
            mGameLayout.setLayoutParams(p);
        } else {
            mBoundedSize = width;
            mIsBoundedByHeight = false;
        }
    }

    public void setONScripterEventListener(ONScripterEventListener listener) {
        mListener = listener;
    }

    public void setOnGameReadyListener(OnGameReadyListener listener) {
        mReadyListener = listener;
    }

    public void setFontScaling(double scaleFactor) {
        mGame.setFontScaling(scaleFactor);
    }

    public void exitApp() {
        finishVideo();
        mGame.exitApp();
    }

    public void sendNativeKeyPress(int keyCode) {
        mGame.sendNativeKeyPress(keyCode);
    }

    public void finishVideo() {
        if (mONScVideoThread != null) {
            mPlayer.stop();

            // Clean up the video and resume the game
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mPlayer != null) {
                        mGame.setVisibility(View.VISIBLE);
                        mGameLayout.removeView(mPlayer);
                    }
                }
            });
            synchronized (mONScVideoThread) {
                mONScVideoThread.notifyAll();
                mONScVideoThread = null;
            }
        }
    }

    @Override
    public void autoStateChanged(boolean selected) {
        if (mListener != null) {
            mListener.autoStateChanged(selected);
        }
    }

    @Override
    public void skipStateChanged(boolean selected) {
        if (mListener != null) {
            mListener.skipStateChanged(selected);
        }
    }

    @Override
    public void videoRequested(final String filename, final boolean clickToSkip, final boolean shouldLoop) {
        if (mListener != null) {
            mListener.videoRequested(filename, clickToSkip, shouldLoop);
        }

        final Activity parent = getActivity();
        if (mUseExternalVideo) {
            try {
                String filename2 = filename.replace('\\', '/');
                Log.v("ONS", "playVideo: " + filename2);
                Uri uri = Uri.parse(filename2);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(uri, "video/*");
                startActivityForResult(i, -1);
            }
            catch(Exception e){
                Log.e("ONS", "playVideo error:  " + e.getClass().getName());
            }
        } else {
            parent.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mPlayer == null) {
                        mPlayer = new VPlayerView(parent);
                        mPlayer.setVideoListener(mVideoListener);
                    }
                    mPlayer.setOnClickListener(clickToSkip ? mVideoClickListener : null);
                    mPlayer.setLoop(shouldLoop);
                    mGameLayout.addView(mPlayer);
                    mPlayer.setDataSource(filename);
                    mGame.setVisibility(View.GONE);
                }
            });

            // Pause ONScripter thread till video finishes
            mONScVideoThread = Thread.currentThread();
            synchronized (mONScVideoThread) {
                try {
                    mONScVideoThread.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    mONScVideoThread = null;
                }
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mPlayer != null && mONScVideoThread != null) {
            mPlayer.onPause();
        }
        if( mGame != null && mONScVideoThread == null) {
            mGame.onPause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mPlayer != null && mONScVideoThread != null) {
            mPlayer.onResume();
        }
        if( mGame != null && mONScVideoThread == null ) {
            mGame.onResume();
        }
    }

    @Override
    public void onStop()
    {
        super.onStop();
        if( mGame != null && mONScVideoThread == null) {
            mGame.onStop();
        }
    }

    @Override
    public void onDestroy()
    {
        if( mGame != null ) {
            exitApp();
        }
        super.onDestroy();
    }
}
