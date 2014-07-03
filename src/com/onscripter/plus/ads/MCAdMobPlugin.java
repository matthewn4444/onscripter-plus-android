package com.onscripter.plus.ads;

import android.app.Activity;
import android.content.Context;

import com.google.ads.mediation.MediationAdRequest;
import com.google.ads.mediation.customevent.CustomEventInterstitial;
import com.google.ads.mediation.customevent.CustomEventInterstitialListener;
import com.ironsource.mobilcore.CallbackResponse;
import com.ironsource.mobilcore.MobileCore;
import com.ironsource.mobilcore.MobileCore.AD_UNITS;
import com.ironsource.mobilcore.MobileCore.LOG_TYPE;
import com.ironsource.mobilcore.OnReadyListener;

public class MCAdMobPlugin implements CustomEventInterstitial {
    private CustomEventInterstitialListener mAdMobListener;
    private Activity mActivity;

    private static boolean mForceShow = false;

    public static void init(Context context, String devHash, LOG_TYPE logLevel, AD_UNITS adUnits) {
        try {
            MobileCore.init(context, devHash, logLevel, adUnits);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void requestInterstitialAd(
            CustomEventInterstitialListener adMobListener, Activity activity,
            String label, String serverParameter,
            MediationAdRequest mediationAdRequest, Object object) {
        mActivity = activity;
        mAdMobListener = adMobListener;

        MobileCore.setOfferwallReadyListener(new OnReadyListener() {
            @Override
            public void onReady(AD_UNITS adUnit) {
                if (adUnit == AD_UNITS.OFFERWALL) {
                    mAdMobListener.onReceivedAd();
                }
            }
        });
        if (!MobileCore.isOfferwallReady()) {
            MobileCore.refreshOffers();
        }
    }

    @Override
    public void showInterstitial() {
        try {
            mAdMobListener.onPresentScreen();
            MobileCore.showOfferWall(mActivity, new CallbackResponse() {
                @Override
                public void onConfirmation(TYPE type) {
                    mAdMobListener.onDismissScreen();
                }
            }, mForceShow);
        } catch (Exception e) {
            mAdMobListener.onFailedToReceiveAd();
        }
    }

    @Override
    public void destroy() {

    }

    public static void setForceShow(boolean forceShow) {
        mForceShow = forceShow;
    }

}
