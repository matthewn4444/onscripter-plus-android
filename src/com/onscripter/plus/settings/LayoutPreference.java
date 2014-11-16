package com.onscripter.plus.settings;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class LayoutPreference extends Preference {
    private View mLayoutView;
    private OnLayoutViewCreatedListener mListener;

    public interface OnLayoutViewCreatedListener {
        public void onLayoutViewCreated(View layoutView);
    }

    public LayoutPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        final LayoutInflater layoutInflater =
                (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mLayoutView = layoutInflater.inflate(getLayoutResource(), parent, false);
        if (mListener != null) {
            mListener.onLayoutViewCreated(mLayoutView);
        }
        return mLayoutView;
    }

    public void setOnLayoutViewCreatedListener(OnLayoutViewCreatedListener listener) {
        mListener = listener;
    }

    public View getLayoutView() {
        return mLayoutView;
    }
}
