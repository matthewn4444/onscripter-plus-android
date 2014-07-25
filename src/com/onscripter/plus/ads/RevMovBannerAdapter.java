package com.onscripter.plus.ads;

import android.app.Activity;
import android.util.Log;

import com.google.ads.AdSize;
import com.google.ads.mediation.MediationAdRequest;
import com.google.ads.mediation.customevent.CustomEventBanner;
import com.google.ads.mediation.customevent.CustomEventBannerListener;
import com.revmob.RevMob;
import com.revmob.ads.banner.RevMobBanner;

public class RevMovBannerAdapter implements CustomEventBanner {

    @Override
    public void destroy() {
    }

    @Override
    public void requestBannerAd(final CustomEventBannerListener listener,
            final Activity activity, String label, String serverParameter,
            AdSize adSize, MediationAdRequest request, Object customEventExtra) {
        log("Requesting banner from RevMovBannerAdapter");

        if (serverParameter == null || serverParameter.isEmpty()) {
            log("The application code is empty, please set it correctly!");
            listener.onFailedToReceiveAd();
            return;
        }

        @SuppressWarnings("deprecation")
        RevMob revmob = RevMob.start(activity, serverParameter);
        RevMobBanner banner = revmob.createBanner(activity);
        listener.onReceivedAd(banner);
    }

    private void log(String s) {
        Log.d("RevMovBannerAdapter", s);
    }
}
