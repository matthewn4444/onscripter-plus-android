package com.onscripter.plus.ads;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.os.Bundle;

import com.bugsense.trace.BugSenseHandler;
import com.onscripter.plus.ActivityPlus;
import com.onscripter.plus.R;
import com.onscripter.plus.ads.InterstitialAdHelper.AdListener;

public class InterstitialTransActivity extends ActivityPlus {
    public static final String NextClassExtra = "next.class.extra";
    public static final String InterstitialRateExtra = "interstitial.rate.extra";

    private InterstitialAdHelper mInterHelper;
    private ProgressDialog mProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isDebug()) {
            BugSenseHandler.initAndStartSession(this, getString(R.string.bugsense_key));
        }
        getWindow().getDecorView().setBackgroundColor(Color.BLACK);

        int adRate = getIntent().getIntExtra(InterstitialRateExtra, 0);
        mInterHelper = new InterstitialAdHelper(this, adRate);
        mInterHelper.setAdListener(new AdListener() {
            @Override
            public void onAdDismiss() {
                super.onAdDismiss();
                dismissDialog();
                doNextAction();
            }

            @Override
            public void onAdLeftApplication() {
                super.onAdLeftApplication();
                dismissDialog();
            }
        });

        if (mInterHelper.show()) {
            mProgress = new ProgressDialog(this);
            mProgress.setMessage(getString(R.string.message_loading_ads));
            mProgress.setCancelable(false);
            mProgress.show();
        } else {
            doNextAction();
        }
    }

    @Override
    protected void onDestroy() {
        dismissDialog();
        super.onDestroy();
    }

    private void dismissDialog() {
        try {
            if (mProgress != null && mProgress.isShowing()) {
                mProgress.dismiss();
                mProgress = null;
            }
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            BugSenseHandler.sendException(e);
        }
    }

    private void doNextAction() {
        Intent prevIntent = getIntent();
        String classPath = prevIntent.getStringExtra(NextClassExtra);
        if (classPath != null) {
            try {
                Intent in = new Intent(this, Class.forName(getApplicationContext().getPackageName() + classPath));
                in.putExtras(prevIntent);
                startActivity(in);
                return;
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                BugSenseHandler.sendException(e);
            }
        }
        finish();
    }

    private boolean isDebug() {
        return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
}
