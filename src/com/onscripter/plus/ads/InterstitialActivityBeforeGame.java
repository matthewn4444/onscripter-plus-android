package com.onscripter.plus.ads;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.os.Bundle;

import com.bugsense.trace.BugSenseHandler;
import com.onscripter.plus.ActivityPlus;
import com.onscripter.plus.ONScripter;
import com.onscripter.plus.R;
import com.onscripter.plus.ads.InterstitialAdHelper.AdListener;

public class InterstitialActivityBeforeGame extends ActivityPlus {
    private InterstitialAdHelper mInterHelper;
    private ProgressDialog mProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isDebug()) {
            BugSenseHandler.initAndStartSession(this, getString(R.string.bugsense_key));
        }
        getWindow().getDecorView().setBackgroundColor(Color.BLACK);

        mInterHelper = new InterstitialAdHelper(this);
        mInterHelper.setAdListener(new AdListener() {
            @Override
            public void onAdDismiss() {
                super.onAdDismiss();
                dismissDialog();
                goToONScripter();
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
            goToONScripter();
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

    private void goToONScripter() {
        Intent in = new Intent(this, ONScripter.class);
        in.putExtras(getIntent());
        startActivity(in);
    }

    private boolean isDebug() {
        return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
}
