package com.onscripter.plus;

import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;

public class ListPreferencePlus extends ListPreference {
    public ListPreferencePlus(Context context) {
        super(context);
    }
    public ListPreferencePlus(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setValue(String value) {
        super.setValue(value);
        setSummary(getEntry());
    }
}
