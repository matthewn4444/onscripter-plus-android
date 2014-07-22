package com.onscripter.plus;

import com.actionbarsherlock.app.SherlockFragmentActivity;


public abstract class ActivityPlus extends SherlockFragmentActivity {

    private OnLifeCycleListener mListener;

    public static abstract class OnLifeCycleListener {
        public void onStart() {}
        public void onStop() {}
        public void onPause() {}
        public void onResume() {}
        public void onRestart() {}
        public void onBackPressed() {}
        public void onDestroy() {}
    }

    public void setOnLifeCycleListener(OnLifeCycleListener listener) {
        mListener = listener;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mListener != null) {
            mListener.onStart();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mListener != null) {
            mListener.onStart();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mListener != null) {
            mListener.onPause();
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (mListener != null) {
            mListener.onRestart();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mListener != null) {
            mListener.onResume();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (mListener != null) {
            mListener.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mListener != null) {
            mListener.onDestroy();
        }
    }
}
