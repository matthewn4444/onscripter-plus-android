package com.onscripter.plus.ads;

import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import com.directtap.DirectTap;
import com.directtap.DirectTapListener;
import com.google.ads.mediation.MediationAdRequest;
import com.google.ads.mediation.customevent.CustomEventInterstitial;
import com.google.ads.mediation.customevent.CustomEventInterstitialListener;
import com.onscripter.plus.R;

public class DirectTapInterstitialAdapter implements CustomEventInterstitial {

    private static final String DirectTapAdapterTag = "DirectTapInterstitialAdapter";
    private static final int TIMEOUT = 4000;
    private static final int PROGRESS_WAIT = 100;

    private DirectTap.FullScreen mFullscreenAd;
    private ProgressDialog mDialog;
    private Timer mCancelTimer;
    private Timer mProgressWaitTimer;

    private void dismissProgress() {
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
    }

    private void cancelTimer() {
        if (mCancelTimer != null) {
            mCancelTimer.cancel();
            mCancelTimer = null;
        }
        if (mProgressWaitTimer != null) {
            mProgressWaitTimer.cancel();
            mProgressWaitTimer = null;
        }
    }

    @Override
    public void requestInterstitialAd(final CustomEventInterstitialListener listener,
            final Activity activity, String label, String serverParameter,
            MediationAdRequest mediationAdRequest, Object customEventExtra) {
        log("Requesting interstitial from DirectTap");

        // Set the init here to avoid dependence of init at the activity level
        if (serverParameter == null || serverParameter.equals("")) {
            log("The application code is empty, please set it correctly!");
            listener.onFailedToReceiveAd();
            return;
        }

        boolean isDebug = (activity.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;

        // Init the SDK
        new DirectTap.Starter(activity, serverParameter).setTestMode(isDebug)
            .setFullScreenOrientation(DirectTap.Starter.ORIENTATION_AUTO).start();

        // Hack, most of the time this will work
        listener.onReceivedAd();

        mFullscreenAd = new DirectTap.FullScreen(activity).setDirectTapListener(new DirectTapListener() {
            @Override
            public void onStartWaiting(Activity act) {
                // After few milliseconds, show a progress dialog if not ready
                mProgressWaitTimer = new Timer();
                mProgressWaitTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        mProgressWaitTimer = null;
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mDialog = new ProgressDialog(activity);
                                mDialog.setCancelable(false);
                                mDialog.setIndeterminate(true);
                                mDialog.setMessage(activity.getString(R.string.dialog_please_wait));
                                mDialog.show();
                            }
                        });
                    }
                }, PROGRESS_WAIT);

                // Set a timer for 4 sec before leaving
                mCancelTimer = new Timer();
                mCancelTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        mCancelTimer = null;

                        // Cancel after timeout error
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                dismissProgress();
                                listener.onFailedToReceiveAd();
                            }
                        });
                    }
                }, TIMEOUT);
            }

            @Override
            public boolean onShowNotPossible(Activity arg0, int arg1) {
                dismissProgress();
                cancelTimer();
                listener.onFailedToReceiveAd();
                return false;
            }

            @Override
            public void onShow(Activity arg0) {
                cancelTimer();
                dismissProgress();
                listener.onPresentScreen();
            }

            @Override
            public void onDismiss(Activity arg0, int arg1) {
                listener.onDismissScreen();
            }
        });
    }

    @Override
    public void showInterstitial() {
        if (mFullscreenAd != null) {
            mFullscreenAd.show();
        }
    }

    @Override
    public void destroy() {
    }

    private void log(String s) {
        Log.d(DirectTapAdapterTag, s);
    }
}
