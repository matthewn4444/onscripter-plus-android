package com.onscripter.plus;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.os.AsyncTask;

public abstract class ProgressDialogAsyncTask<Params, Progress, Result> extends
        AsyncTask<Params, Progress, Result> implements OnCancelListener {
    private static final String DEFAULT_MESSAGE = "Working...";

    private ProgressDialog mProgressDial;
    private String mMessage;
    private final Context mCtx;
    private boolean mCancelable;
    private boolean mShowDialog;

    public ProgressDialogAsyncTask(Context ctx) {
        this(ctx, DEFAULT_MESSAGE, true);
    }

    public ProgressDialogAsyncTask(Context ctx, String message) {
        this(ctx, message, true);
    }

    public ProgressDialogAsyncTask(Context ctx, String message, boolean showDialog) {
        mCtx = ctx;
        mCancelable = true;
        setMessage(message);
        mShowDialog = showDialog;
    }

    public void setMessage(String message) {
        mMessage = message;
        if (mProgressDial != null) {
            mProgressDial.setMessage(mMessage);
        }
    }

    public String getMessage() {
        return mMessage;
    }

    public void show() {
        mShowDialog = true;
        mProgressDial = ProgressDialog.show(mCtx, "", mMessage, true, mCancelable, this);
    }

    public void setCancelable(boolean cancelable) {
        mCancelable = cancelable;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        if (mShowDialog) {
            show();
        }
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        this.cancel(mCancelable);
    }

    public void dismiss() {
        if (mProgressDial != null && mProgressDial.isShowing()) {
            mProgressDial.dismiss();
            mProgressDial = null;
        }
    }

    @Override
    protected void onPostExecute(Result result) {
        super.onPostExecute(result);
        dismiss();
    }
}
