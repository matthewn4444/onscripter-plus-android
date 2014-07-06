package com.onscripter.plus.ads;

import android.app.Activity;
import android.util.Log;

import com.google.ads.mediation.MediationAdRequest;
import com.google.ads.mediation.customevent.CustomEventInterstitial;
import com.google.ads.mediation.customevent.CustomEventInterstitialListener;
import com.ironsource.mobilcore.CallbackResponse;
import com.ironsource.mobilcore.MobileCore;
import com.ironsource.mobilcore.MobileCore.AD_UNITS;
import com.ironsource.mobilcore.OnReadyListener;

public class MCAdMobPlugin implements CustomEventInterstitial {
    private CustomEventInterstitialListener mAdMobListener;
    private Activity mActivity;

    private static String MCAdMobPluginTag = "MobileCorePlugin";

    private static boolean mForceShow = false;
    private static boolean sShowStickeez = false;

    public static void eventuallyShowStickeez(final Activity activity, String devHash) {
        MobileCore.init(activity, devHash, MobileCore.LOG_TYPE.PRODUCTION, MobileCore.AD_UNITS.OFFERWALL, MobileCore.AD_UNITS.STICKEEZ);
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (MobileCore.isStickeeReady()) {
                    MobileCore.showStickee(activity);
                } else {
                    MobileCore.setStickeezReadyListener(new OnReadyListener() {
                        @Override
                        public void onReady(AD_UNITS adUnit) {
                            if (adUnit.equals(MobileCore.AD_UNITS.STICKEEZ)) {
                                MobileCore.showStickee(activity);
                            }
                        }
                    });
                }

            }
        }).start();
        sShowStickeez = true;
    }

    public static void hideStickeez() {
        if (sShowStickeez && MobileCore.isStickeeShowing()) {
            MobileCore.hideStickee();
            sShowStickeez = false;
        }
    }

    @Override
    public void requestInterstitialAd(
            CustomEventInterstitialListener adMobListener, Activity activity,
            String label, String serverParameter,
            MediationAdRequest mediationAdRequest, Object object) {
        mActivity = activity;
        mAdMobListener = adMobListener;
        Log.d(MCAdMobPluginTag, "Requesting interstitial from mobileCore");

        // Set the init here to avoid dependence of init at the activity level
        if (serverParameter == null || serverParameter.equals("")) {
            Log.d(MCAdMobPluginTag, "The developer hash from admob is empty, please set it correctly!");
            adMobListener.onFailedToReceiveAd();
            return;
        }
        try {
            MobileCore.init(activity, serverParameter, MobileCore.LOG_TYPE.PRODUCTION, MobileCore.AD_UNITS.OFFERWALL, MobileCore.AD_UNITS.STICKEEZ);
        } catch (Exception e) {
            e.printStackTrace();
            mAdMobListener.onFailedToReceiveAd();
        }

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
            Log.d(MCAdMobPluginTag, "Failed to receive ad from mobileCore");
        }
    }

    @Override
    public void destroy() {

    }

    public static void setForceShow(boolean forceShow) {
        mForceShow = forceShow;
    }

}
