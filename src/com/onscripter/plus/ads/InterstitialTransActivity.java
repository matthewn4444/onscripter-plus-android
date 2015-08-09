package com.onscripter.plus.ads;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import com.bugsense.trace.BugSenseHandler;
import com.onscripter.plus.ActivityPlus;
import com.onscripter.plus.R;
import com.onscripter.plus.ads.InterstitialAdHelper.AdListener;

public class InterstitialTransActivity extends ActivityPlus {
    public static final String NextClassExtra = "next.class.extra";
    public static final String InterstitialRateExtra = "interstitial.rate.extra";

    private static final int AdFailedTimeout = 3000;

    private InterstitialAdHelper mInterHelper;
    private ProgressDialog mProgress;
    private Timer mCancelTimer;
    private boolean mNextRan = false;
    private boolean mAdFailedToLoad = false;

    @Override
    public void onBackPressed() {
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isDebug()) {
            BugSenseHandler.initAndStartSession(this, getString(R.string.bugsense_key));
        }
        getWindow().getDecorView().setBackgroundColor(Color.BLACK);

        // Hide controls...
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        decorView.setSystemUiVisibility(uiOptions);

        int adRate = getIntent().getIntExtra(InterstitialRateExtra, 0);
        mInterHelper = new InterstitialAdHelper(this, adRate);
        mInterHelper.setAdListener(new AdListener() {
            @Override
            public void onAdDismiss() {
                super.onAdDismiss();
                doNextAction();
            }

            @Override
            public void onAdFailedToLoad(int errorCode) {
                super.onAdFailedToLoad(errorCode);

                synchronized (this) {
                    if (mAdFailedToLoad) {
                        return;
                    }
                    mAdFailedToLoad = true;
                }

                // If failed to load (ad block or no Internet), 50% chance to wait a few secs and forget showing the ad
                if (mCancelTimer == null && new Random().nextBoolean()) {
                    if (errorCode == InterstitialAdHelper.ERROR_CODE_ADBLOCK_ERROR) {
                        showDialog();
                    }

                    mCancelTimer = new Timer();
                    mCancelTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            // Cancel after timeout error
                            doNextAction();
                        }
                    }, AdFailedTimeout);
                } else {
                    doNextAction();
                }
            }

            @Override
            public void onAdLeftApplication() {
                super.onAdLeftApplication();
                dismissDialog();
            }
        });

        // If the internet is off
        if (!isNetworkAvailable(this)) {
            showDialog();
        }
        if (!mInterHelper.show()) {
            doNextAction();
        }
    }

    @Override
    protected void onDestroy() {
        dismissDialog();
        super.onDestroy();
    }

    private void showDialog() {
        if (mProgress == null) {
            mProgress = new ProgressDialog(this);
            mProgress.setMessage(getString(R.string.message_loading_ads));
            mProgress.setCancelable(false);
            mProgress.show();
        }
    }

    private synchronized void dismissDialog() {
        try {
            if (mProgress != null && mProgress.isShowing()) {
                mProgress.dismiss();
                mProgress = null;
            }
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            BugSenseHandler.sendException(e);
        }
    }

    private void doNextAction() {
        synchronized (this) {
            if (mNextRan) {
                return;
            }
            mNextRan = true;
            if (mCancelTimer != null) {
                mCancelTimer.cancel();
            }
        }
        dismissDialog();
        Intent prevIntent = getIntent();
        String classPath = prevIntent.getStringExtra(NextClassExtra);
        if (classPath != null) {
            try {
                Intent in = new Intent(this, Class.forName(getApplicationContext().getPackageName() + classPath));
                in.putExtras(prevIntent);
                startActivity(in);
                return;
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                BugSenseHandler.sendException(e);
            }
        }
        finish();
    }

    private static boolean isNetworkAvailable(Context ctx) {
        ConnectivityManager connectivityManager
              = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private boolean isDebug() {
        return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
}
