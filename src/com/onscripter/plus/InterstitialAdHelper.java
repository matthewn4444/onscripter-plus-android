package com.onscripter.plus;

import java.io.File;
import java.util.Calendar;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.preference.PreferenceManager;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;

public class InterstitialAdHelper {
    private final Context mCtx;
    private InterstitialAd mAd;
    private AdListener mListener;
    private int mMissedTimes;
    private final SharedPreferences mPrefs;
    private final boolean mShowIntermittantAd;

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

    /**
     * This constructor will run the intermittent simple algorithm given above
     * when to show the ads.
     * @param c
     */
    public InterstitialAdHelper(Context c) {
        this(c, 0);
    }

    /**
     * This constructor specifies how often the ad should show given a flat percentage (out of 100) rate
     * @param c
     * @param flatPercent
     */
    public InterstitialAdHelper(Context c, double flatPercent) {
        mCtx = c;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mCtx);
        mShowIntermittantAd = flatPercent == 0;
        double percentage = mShowIntermittantAd ? calculateIntermittentShowAd() : flatPercent;
        if (shouldShowAd(percentage)) {
            buildAd();
        }
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
        boolean willShow = mAd != null && mAd.isLoaded();
        if (willShow) {
            if (mShowIntermittantAd) {
                // Showing the ad, now set it back to 0 missed times
                mPrefs.edit().putInt(PREF_NUM_TIMES_MISSED_AD, 0).commit();
            }

            if (mListener != null) {
                mAd.setAdListener(mListener);
            }
            mAd.show();
        } else if (mShowIntermittantAd) {
            // Didn't see the ad, now we have to increment it
            mPrefs.edit().putInt(PREF_NUM_TIMES_MISSED_AD, ++mMissedTimes).commit();
        }
        return willShow;
    }

    private long getInstallationDateMilliseconds() {
        try {
            ApplicationInfo appInfo = mCtx.getPackageManager()
                    .getApplicationInfo(mCtx.getPackageName(), 0);
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
        mAd = new InterstitialAd(mCtx);
        mAd.setAdUnitId(mCtx.getString(R.string.admob_interstitial_key));
        final AdRequest adRequest = new AdRequest.Builder().build();
        mAd.loadAd(adRequest);
        if (mListener != null) {
            mAd.setAdListener(mListener);
        }
    }
}
