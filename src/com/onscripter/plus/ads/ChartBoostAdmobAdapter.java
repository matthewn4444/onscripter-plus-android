package com.onscripter.plus.ads;

import android.app.Activity;
import android.util.Log;

import com.chartboost.sdk.Chartboost;
import com.chartboost.sdk.ChartboostDefaultDelegate;
import com.chartboost.sdk.Model.CBError.CBImpressionError;
import com.google.ads.mediation.MediationAdRequest;
import com.google.ads.mediation.customevent.CustomEventInterstitial;
import com.google.ads.mediation.customevent.CustomEventInterstitialListener;
import com.onscripter.plus.ActivityPlus;
import com.onscripter.plus.ActivityPlus.OnLifeCycleListener;

/**
 * When you integrate this with Admob mediation, you need to do this:
 *
 * 1. Create a custom event inside Interstitial Ads
 * 2. Set Class name to this class
 * 3. The paramter option must be your app id and your app signature in this format
 *
 *                  "<app id>,<app signature>"          (without quotes)
 *
 * @author matthewn4444
 *
 */

public class ChartBoostAdmobAdapter implements CustomEventInterstitial {

    public static boolean shouldCreate = true;
    public static boolean useActivitiesForImpressions = true;

    private static final String ChartBoostCustomEventTag = "ChartBoostAdmobAdapter";

    private Chartboost cb;

    private class InternalChartBoostDelegate extends ChartboostDefaultDelegate {
        CustomEventInterstitialListener mListener;

        public InternalChartBoostDelegate(CustomEventInterstitialListener listener) {
            mListener = listener;
        }

        @Override
        public void didCacheInterstitial(String location) {
            super.didCacheInterstitial(location);
            mListener.onReceivedAd();
        }

        @Override
        public void didFailToLoadInterstitial(String location, CBImpressionError error) {
            log("failed to load interstitial: " + location + " | " + error.name());
            super.didFailToLoadInterstitial(location, error);
            mListener.onFailedToReceiveAd();
        }

        @Override
        public void didDismissInterstitial(String location) {
            super.didDismissInterstitial(location);
            mListener.onDismissScreen();
            cb.cacheInterstitial(location);
        }

        @Override
        public void didClickInterstitial(String location) {
            log("clicked interstitial");
            super.didClickInterstitial(location);
            mListener.onLeaveApplication();
        }
    }

    @Override
    public void requestInterstitialAd(CustomEventInterstitialListener listener, final Activity activity, String label, String serverParameter, MediationAdRequest mediationAdRequest, Object customEventExtra) {
        log("Received an interstitial ad request for " + label);

        // Get the app id and signature from admob
        String[] parameters = serverParameter.split(",");
        if (parameters.length != 2) {
            log("Invalid parameter " + serverParameter + ", needed \"appId,appSignature\"");
            return;
        }

        cb = Chartboost.sharedChartboost();
        final String appId = parameters[0];
        final String appSignature = parameters[1];

        // Load an ad into cache
        cb.onCreate(activity, appId, appSignature, new InternalChartBoostDelegate(listener));
        cb.onStart(activity);

        // Cache ad only if not cached already
        if (cb.hasCachedInterstitial()) {
            log("Going to show cached ad later");
            listener.onReceivedAd();
        } else {
            log("Caching ad now");
            cb.cacheInterstitial();
        }

        // Set the activity's lifecycle to handle Chartboost events
        ((ActivityPlus)activity).setOnLifeCycleListener(new OnLifeCycleListener() {
            @Override
            public void onStop() {
                cb.onStop(activity);
            }
            @Override
            public void onDestroy() {
                cb.onDestroy(activity);
            }
            @Override
            public void onBackPressed() {
                cb.onBackPressed();
            }
        });
    }

    @Override
    public void showInterstitial() {
        log("Showing previously loaded interstitial ad");
        cb.showInterstitial();
    }

    @Override
    public void destroy() {
    }

    private void log(String s) {
        Log.d(ChartBoostCustomEventTag, s);
    }
}

