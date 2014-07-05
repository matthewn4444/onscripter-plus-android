package com.onscripter.plus.ads;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import com.directtap.DirectTap;
import com.directtap.DirectTapBanner;
import com.google.ads.AdSize;
import com.google.ads.mediation.MediationAdRequest;
import com.google.ads.mediation.customevent.CustomEventBanner;
import com.google.ads.mediation.customevent.CustomEventBannerListener;

public class DirectTapBannerAdapter implements CustomEventBanner {

    private static final String DirectTapAdapterTag = "DirectTapBannerAdapter";

    @Override
    public void requestBannerAd(final CustomEventBannerListener listener,
            final Activity activity, String label, String serverParameter,
            AdSize adSize, MediationAdRequest request, Object customEventExtra) {
        log("Requesting banner from DirectTap");

        // Set the init here to avoid dependence of init at the activity level
        if (serverParameter == null || serverParameter.equals("")) {
            log("The application code is empty, please set it correctly!");
            listener.onFailedToReceiveAd();
            return;
        }

        // Get the banners
        boolean isDebug = (activity.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        new DirectTap.Starter(activity, serverParameter).setTestMode(isDebug).start();
        DirectTapBanner landscapeBanner = new DirectTap.LandscapeBanner(activity).build();
        listener.onReceivedAd(landscapeBanner);
    }

    @Override
    public void destroy() {
    }

    private void log(String s) {
        Log.d(DirectTapAdapterTag, s);
    }
}
