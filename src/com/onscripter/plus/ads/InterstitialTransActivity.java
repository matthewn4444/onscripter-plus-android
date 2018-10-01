package com.onscripter.plus.ads;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;

import com.bugsense.trace.BugSenseHandler;
import com.google.android.gms.ads.AdRequest;
import com.onscripter.plus.ActivityPlus;
import com.onscripter.plus.R;
import com.onscripter.plus.ads.InterstitialAdHelper.AdListener;

public class InterstitialTransActivity extends ActivityPlus {
    public static final String NextClassExtra = "next.class.extra";
    public static final String InterstitialRateExtra = "interstitial.rate.extra";
    static final private String PREF_LEFT_AD_BEFORE_BLOCKED = "InterstitialTransActivity.blocked.ad.missed.last.time";

    private static final int AdBlockFailedTimeout = 15000;
    private static final int AdFailedTimeout = 12000;

    private InterstitialAdHelper mInterHelper;
    private ProgressDialog mProgress;
    private Timer mCancelTimer;
    private SharedPreferences mPrefs;
    private long mCountdown;
    private boolean mNextRan = false;
    private boolean mAdFailedToLoad = false;

    @Override
    public void onBackPressed() {
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!isDebug()) {
            BugSenseHandler.initAndStartSession(this, getString(R.string.bugsense_key));
        }
        getWindow().getDecorView().setBackgroundColor(Color.BLACK);

        // Hide controls...
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        decorView.setSystemUiVisibility(uiOptions);

        // If last time skipped without finishing ad timeout, show it again
        if (lastTimeSkippedBlockedAd()) {
            showMissingDialog(0);
            return;
        }

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

                // If failed to load (ad block or no Internet), 40% chance to wait a few secs and forget showing the ad
                if (mCancelTimer == null && new Random().nextInt(10) >= 6) {
                    showMissingDialog(errorCode);
                } else if (mProgress == null) {
                    doNextAction();
                }
            }

            @Override
            public void onAdLeftApplication() {
                super.onAdLeftApplication();
                dismissDialog();
            }
        });

        if (!mInterHelper.show()) {
            doNextAction();
        }
    }

    @Override
    protected void onDestroy() {
        dismissDialog();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCountdown > 0) {
            // If user left app before passed, force the next time to play an ad
            mPrefs.edit().putBoolean(PREF_LEFT_AD_BEFORE_BLOCKED, true).apply();
        }
    }

    private void updateDialogTimeout() {
        if (mProgress != null) {
            mProgress.setMessage(String.format(getString(R.string.message_loading_ads), mCountdown));
        }
    }

    private void showMissingDialog(int errorCode) {
        if (mProgress == null) {
            mPrefs.edit().remove(PREF_LEFT_AD_BEFORE_BLOCKED).apply();

            long timeout = 0;
            switch (errorCode) {
            case InterstitialAdHelper.ERROR_CODE_ADBLOCK_ERROR:
                timeout = AdBlockFailedTimeout;
                break;
            case AdRequest.ERROR_CODE_NETWORK_ERROR:
                timeout = AdFailedTimeout;
                break;
            default:
                // Internet was not the reason, so skip the ad
                doNextAction();
                return;
            }
            mCountdown = timeout / 1000;
            mProgress = new ProgressDialog(this);
            updateDialogTimeout();
            mProgress.setCancelable(false);
            mProgress.show();

            if (mCancelTimer == null) {
                mCancelTimer = new Timer();
                mCancelTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        // Cancel after timeout error
                        doNextAction();
                    }
                }, timeout);

                mCancelTimer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateDialogTimeout();
                                mCountdown--;
                            }
                        });
                    }
                }, 0, 1000);
            }
        }
    }

    private boolean lastTimeSkippedBlockedAd() {
        return mPrefs.getBoolean(PREF_LEFT_AD_BEFORE_BLOCKED, false);
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
