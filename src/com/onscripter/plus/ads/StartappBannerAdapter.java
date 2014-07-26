package com.onscripter.plus.ads;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;

import com.google.ads.AdSize;
import com.google.ads.mediation.MediationAdRequest;
import com.google.ads.mediation.customevent.CustomEventBanner;
import com.google.ads.mediation.customevent.CustomEventBannerListener;
import com.startapp.android.publish.StartAppSDK;
import com.startapp.android.publish.banner.Banner;

public class StartappBannerAdapter implements CustomEventBanner {

    @Override
    public void destroy() {

    }

    @Override
    public void requestBannerAd(final CustomEventBannerListener listener,
            final Activity activity, String label, String serverParameter,
            AdSize adSize, MediationAdRequest request, Object customEventExtra) {
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

        Banner banner = new Banner(activity);
        banner.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onClick();
                listener.onPresentScreen();
                listener.onLeaveApplication();

            }
        });
        listener.onReceivedAd(banner);
    }

    private void log(String s) {
        Log.d("StartappBannerAdapter", s);
    }
}
