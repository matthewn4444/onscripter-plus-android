package com.onscripter.plus;

import java.io.File;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;

public class InterstitialAdHelper {
    private final Activity mAct;
    private InterstitialAd mAd;
    private AdListener mListener;
    private int mMissedTimes;
    private FrameLayout mOverlay;
    private final SharedPreferences mPrefs;
    private final boolean mShowIntermittantAd;
    private Timer mCancelTimer;
    private boolean mCancel = false;

    // Constants to decide whether to show the InterstitialAds now or later
    //      Notice how the first time and next time after seeing an ad, the
    //      percentage would be (close to) 0%
    /*
     * This is how often the game is opened but no ad was shown based on probability
     * Each time the user does not see the ad, it will increment 'n' and for each
     * 'n', it will add a value starting from 30 to the total percent change of seeineg
     * the ad, each time 'n' increments, the percent added is halved. For example, the
     * sequence would be: 30%, (30+15) 45%, (30+15+7.5) 52.5% etc
     */
    static final private int OFTEN_OPENED_SMALL_VALUE = 30;
    static final private int OFTEN_OPENED_SMALL_STEP = 2;
    static final private String PREF_NUM_TIMES_MISSED_AD = "InterstitialAdHelper.num.times.missed.ad";

    /*
     * This is the time elapsed that the user has not seen this ad. Within a 24 hour
     * period, the percent added is from 0 to 30, where 0 means that no time has passed
     * and 30 means 24 hours has passed since last time saw the ad (or installation time as
     * default). It maxes after 24 hours meaning that if the user hasnt seen an ad for a long
     * time, it will show up next time
     */
    static final private int TIME_NOT_USED_24_HOURS_VALUE = 30;
    static final private String PREF_LAST_DATE_SEEN_AD = "InterstitialAdHelper.last.date.seen.ad";

    static final private int TIMEOUT = 4000;

    /**
     * This constructor will run the intermittent simple algorithm given above
     * when to show the ads.
     * @param c
     */
    public InterstitialAdHelper(Activity a) {
        this(a, 0);
    }

    /**
     * This constructor specifies how often the ad should show given a flat percentage (out of 100) rate
     * @param c
     * @param flatPercent
     */
    public InterstitialAdHelper(Activity a, double flatPercent) {
        mAct = a;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mAct);
        mShowIntermittantAd = flatPercent == 0;
        if (flatPercent >= 100) {
            buildAd();
        } else {
            double percentage = mShowIntermittantAd ? calculateIntermittentShowAd() : flatPercent;
            if (shouldShowAd(percentage)) {
                buildAd();
            }
        }

        // Detect ads, just cancel showing the interstitial; need to be threaded
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (Adblocker.check()) {
                    mAct.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            handleAdLoadFail(AdRequest.ERROR_CODE_NETWORK_ERROR);
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * Add the event listener to interact with the ads
     * @param listener
     */
    public void setAdListener(AdListener listener) {
        mListener = listener;
    }

    /**
     * This will trigger the ad to show. If the calculation gets the percent and randomly
     * chooses whether to show it or not. This function will return true if shown and false
     * if it doesn't show the ad.
     * @return has shown or not
     */
    public boolean show() {
        if (mAd == null || mCancel) {
            incrementMissedAd();
            return false;
        }
        if (mAd.isLoaded()) {
            internalShow();
        } else {
            if (!mCancel) {
                attachOverlay();
                mCancelTimer = new Timer();
                mCancelTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        // Cancel after timeout error
                        mCancel = true;
                        removeOverlay();
                        mAct.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (mListener != null) {
                                    mListener.onAdFailedToLoad(AdRequest.ERROR_CODE_NETWORK_ERROR);
                                    mListener.onAdDismiss();
                                }
                            }
                        });
                    }
                }, TIMEOUT);
            } else {
                incrementMissedAd();
            }
        }
        return true;
    }

    private void incrementMissedAd() {
        if (mShowIntermittantAd) {
            // Didn't see the ad, now we have to increment it
            mPrefs.edit().putInt(PREF_NUM_TIMES_MISSED_AD, ++mMissedTimes).commit();
        }
    }

    private void internalShow() {
        if (mShowIntermittantAd) {
            // Showing the ad, now set it back to 0 missed times
            mPrefs.edit().putInt(PREF_NUM_TIMES_MISSED_AD, 0).commit();
        }
        mAd.show();
    }

    private void attachOverlay() {
        mAct.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Not ready yet, cover the entire app with black till ad is loaded
                FrameLayout fl = (FrameLayout)mAct.findViewById(android.R.id.content);
                mOverlay = new FrameLayout(mAct);
                mOverlay.setBackgroundColor(Color.BLACK);
                fl.addView(mOverlay);
                boolean userHasActionbar = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB;
                if (mAct instanceof SherlockActivity) {
                    ActionBar bar = ((SherlockActivity)mAct).getSupportActionBar();
                    if (bar != null) {
                        bar.hide();
                    }
                } else if (userHasActionbar) {
                    showHideActionBarAPI11(mAct, false);
                }
                mAct.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
        });
    }

    private void removeOverlay() {
        if (mOverlay != null) {
            mAct.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if ((ViewGroup)mOverlay.getParent() != null) {
                        ((ViewGroup)mOverlay.getParent()).removeView(mOverlay);
                    }
                    mOverlay = null;
                    mAct.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                    boolean userHasActionbar = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB;
                    if (mAct instanceof SherlockActivity) {
                        ActionBar bar = ((SherlockActivity)mAct).getSupportActionBar();
                        if (bar != null) {
                            bar.show();
                        }
                    } else if (userHasActionbar) {
                        showHideActionBarAPI11(mAct, true);
                    }
                }
            });
        }
    }

    @TargetApi(11)
    private void showHideActionBarAPI11(Activity act, boolean show) {
        android.app.ActionBar bar = act.getActionBar();
        if (bar != null) {
            if (show) {
                bar.show();
            } else {
                bar.hide();
            }
        }
    }

    private long getInstallationDateMilliseconds() {
        try {
            ApplicationInfo appInfo = mAct.getPackageManager()
                    .getApplicationInfo(mAct.getPackageName(), 0);
            String appFile = appInfo.sourceDir;
            return new File(appFile).lastModified();
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private int getRandomPercentage() {
        int Min = 0;
        int Max = 100;
        return Min + (int)(Math.random() * ((Max - Min) + 1));
    }

    private double calculateIntermittentShowAd() {
        long lastTimeSeenAd = mPrefs.getLong(PREF_LAST_DATE_SEEN_AD, getInstallationDateMilliseconds());
        mMissedTimes = mPrefs.getInt(PREF_NUM_TIMES_MISSED_AD, 0);

        long now = Calendar.getInstance().getTimeInMillis();
        double hoursDiff = (now - lastTimeSeenAd) / (1000 * 60 * 60.0);
        double percentLastSeen = Math.min(1, hoursDiff / 24.0) * TIME_NOT_USED_24_HOURS_VALUE;

        // Calculate the overall percentage
        double percentMissedTimes = 0;
        for (int i = 0, v = OFTEN_OPENED_SMALL_VALUE; i < mMissedTimes; i++, v /= 2.0) {
            percentMissedTimes += Math.max(v, OFTEN_OPENED_SMALL_STEP);
        }
        return percentLastSeen + percentMissedTimes;
    }

    private void handleAdLoadFail(int errorCode) {
        removeOverlay();
        mCancel = true;
        if (!mCancel && mCancelTimer != null) {
            mCancelTimer.cancel();
        }
        if (mListener != null) {
            mListener.onAdFailedToLoad(errorCode);
            if (mOverlay != null) {
                // Show when overlay is shown
                mListener.onAdDismiss();
            }
        }
    }

    private boolean shouldShowAd(double percentage) {
        // Now see if we randomly get into the percentage area
        int rPercent = getRandomPercentage();
        //Toast.makeText(mCtx, percentage + "% | R: " + rPercent + "%", Toast.LENGTH_SHORT).show();
        if (rPercent <= (int)percentage) {
            return true;
        }
        return false;
    }

    private void buildAd() {
        mAd = new InterstitialAd(mAct);
        mAd.setAdUnitId(mAct.getString(R.string.admob_interstitial_key));
        final AdRequest adRequest = new AdRequest.Builder().build();
        mAd.loadAd(adRequest);

        mAd.setAdListener(new com.google.android.gms.ads.AdListener() {
            @Override
            public void onAdClosed() {
                super.onAdClosed();
                removeOverlay();
                if (mListener != null) {
                    mListener.onAdClosed();
                    mListener.onAdDismiss();
                }
            }
            @Override
            public void onAdFailedToLoad(int errorCode) {
                super.onAdFailedToLoad(errorCode);
                handleAdLoadFail(errorCode);
            }
            @Override
            public void onAdLoaded() {
                super.onAdLoaded();
                if (!mCancel && mCancelTimer != null) {
                    mCancelTimer.cancel();
                }
                if (mListener != null) {
                    mListener.onAdLoaded();
                }
                // Waited for the ad to load
                if (mOverlay != null) {
                    internalShow();
                }
            }
            @Override
            public void onAdOpened() {
                super.onAdOpened();
                if (mListener != null) {
                    mListener.onAdOpened();
                }
            }
        });
    }

    public static class AdListener extends com.google.android.gms.ads.AdListener {
        public void onAdDismiss() {
        }
    }
}
