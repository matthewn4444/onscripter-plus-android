package com.onscripter.plus.ads;

import android.app.Activity;
import android.util.Log;

import com.google.ads.mediation.MediationAdRequest;
import com.google.ads.mediation.customevent.CustomEventInterstitial;
import com.google.ads.mediation.customevent.CustomEventInterstitialListener;
import com.startapp.android.publish.Ad;
import com.startapp.android.publish.AdDisplayListener;
import com.startapp.android.publish.AdEventListener;
import com.startapp.android.publish.StartAppAd;
import com.startapp.android.publish.StartAppSDK;


public class StartappInterstitialAdapter implements CustomEventInterstitial {

    private StartAppAd mAd;
    private CustomEventInterstitialListener mAdmobListener;

    @Override
    public void requestInterstitialAd(CustomEventInterstitialListener listener, Activity activity,
            String label, String serverParameter, MediationAdRequest mediationAdRequest, Object customEventExtra) {
        mAdmobListener = listener;
        String devId = null, appId = null;

        if (serverParameter != null) {
            String[] parts = serverParameter.split(",");
            if (parts.length == 2) {
                devId = parts[0];
                appId = parts[1];
            }
        }

        if (devId == null) {
            log("Failed to get ad: server parameter is invalid");
            listener.onFailedToReceiveAd();
            return;
        }

        // Start initialization
        StartAppSDK.init(activity, devId, appId, true);

        mAd = new StartAppAd(activity);
        mAd.loadAd(new AdEventListener() {
            @Override
            public void onReceiveAd(Ad arg0) {
                if (mAdmobListener != null) {
                    mAdmobListener.onReceivedAd();
                }
            }
            @Override
            public void onFailedToReceiveAd(Ad arg0) {
                if (mAdmobListener != null) {
                    mAdmobListener.onFailedToReceiveAd();
                }
            }
        });
    }

    @Override
    public void showInterstitial() {
        mAd.showAd(new AdDisplayListener() {

            @Override
            public void adHidden(Ad arg0) {
                if (mAdmobListener != null) {
                    mAdmobListener.onDismissScreen();
                }
            }

            @Override
            public void adDisplayed(Ad arg0) {
                if (mAdmobListener != null) {
                    mAdmobListener.onPresentScreen();
                }
            }

            @Override
            public void adClicked(Ad arg0) {
                if (mAdmobListener != null) {
                    mAdmobListener.onLeaveApplication();
                }
            }
        });
    }

    private void log(String s) {
        Log.d("StartappInterstitialAdapter", s);
    }

    @Override
    public void destroy() {
    }

}
