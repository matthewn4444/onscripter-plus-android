package com.onscripter.plus.ads;

import android.app.Activity;
import android.util.Log;

import com.google.ads.mediation.MediationAdRequest;
import com.google.ads.mediation.customevent.CustomEventInterstitial;
import com.google.ads.mediation.customevent.CustomEventInterstitialListener;
import com.revmob.RevMob;
import com.revmob.RevMobAdsListener;
import com.revmob.ads.fullscreen.RevMobFullscreen;

public class RevMovInterstitialAdapter implements CustomEventInterstitial {

    private RevMobFullscreen mFullscreen;
    private RevMob mRevmob;
    private CustomEventInterstitialListener mAdmobListener;

    private final RevMobAdsListener mRMListener = new RevMobAdsListener() {

        @Override
        public void onRevMobAdClicked() {
            log("onRevMobAdClicked");
            if (mAdmobListener != null) {
                mAdmobListener.onLeaveApplication();
            }
        }

        @Override
        public void onRevMobAdDismiss() {
            log("onRevMobAdDismiss");
            if (mAdmobListener != null) {
                mAdmobListener.onDismissScreen();
            }
        }

        @Override
        public void onRevMobAdDisplayed() {
            log("onRevMobAdDisplayed");
            if (mAdmobListener != null) {
                mAdmobListener.onPresentScreen();
            }
        }

        @Override
        public void onRevMobAdNotReceived(String arg0) {
            log("onRevMobAdNotReceived");
            if (mAdmobListener != null) {
                mAdmobListener.onFailedToReceiveAd();
            }
        }

        @Override
        public void onRevMobAdReceived() {
            log("onRevMobAdReceived");
            if (mAdmobListener != null) {
                mAdmobListener.onReceivedAd();
            }
        }

        @Override
        public void onRevMobEulaIsShown() {
        }

        @Override
        public void onRevMobEulaWasAcceptedAndDismissed() {
        }

        @Override
        public void onRevMobEulaWasRejected() {
        }

        @Override
        public void onRevMobSessionIsStarted() {
        }

        @Override
        public void onRevMobSessionNotStarted(String arg0) {
        }
    };

    @Override
    public void destroy() {
    }

    @SuppressWarnings("deprecation")
    @Override
    public void requestInterstitialAd(CustomEventInterstitialListener listener, Activity activity,
            String label, String serverParameter, MediationAdRequest mediationAdRequest, Object customEventExtra) {
        log("Requested interstitial from RevMovAdmobAdapter");

        if (serverParameter == null || serverParameter.isEmpty()) {
            log("Failed to get ad: server parameter is invalid");
            listener.onFailedToReceiveAd();
            return;
        }
        mAdmobListener = listener;
        mRevmob = RevMob.start(activity, serverParameter);

        mFullscreen = mRevmob.createFullscreen(activity, mRMListener);
    }

    @Override
    public void showInterstitial() {
        mFullscreen.show();
    }

    private void log(String s) {
        Log.d("RevMovAdmobAdapter", s);
    }
}
