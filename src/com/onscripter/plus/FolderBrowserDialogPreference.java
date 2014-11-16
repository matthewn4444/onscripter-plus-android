package com.onscripter.plus;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

public class FolderBrowserDialogPreference extends DialogPreference {
    private final FolderBrowserDialogWrapper mWrapper;

    public FolderBrowserDialogPreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FolderBrowserDialogPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mWrapper = new FolderBrowserDialogWrapper(context);

        setPositiveButtonText(R.string.dialog_select_button_text);
        setNegativeButtonText(android.R.string.cancel);

        // Apply the layout for ics and up
        int currentapiVersion = android.os.Build.VERSION.SDK_INT;
        if (currentapiVersion > context.getResources().getInteger(R.integer.gingerbread_version)) {
            setLayoutResource(R.layout.dialog_preference);
        }
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View v = super.onCreateView(parent);
        int paddingLeft = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                v.getResources().getDimension(R.dimen.preference_padding_start),
                v.getResources().getDisplayMetrics());
        v.setPadding(paddingLeft, 0, 0, 0);
        return v;
    }

    @Override
    protected View onCreateDialogView() {
        return mWrapper.getDialogLayout();
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        mWrapper.setDialog(getDialog());
        mWrapper.show(getPersistedString(null));
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        ViewGroup layout = mWrapper.getDialogLayout();
        if (layout.getParent() != null) {
            ((ViewGroup)layout.getParent()).removeView(layout);
        }
        super.onDismiss(dialog);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            String value = mWrapper.getResultDirectory().getPath();
            if (callChangeListener(value)) {
                persistString(value);
            }
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    // Code below is from EditTextPreference class
    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            // No need to save instance state since it's persistent
            return superState;
        }

        final SavedState myState = new SavedState(superState);
        myState.text = mWrapper.getResultDirectory().getPath();
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        mWrapper.setupDirectories(myState.text);
    }

    private static class SavedState extends BaseSavedState {
        String text;

        public SavedState(Parcel source) {
            super(source);
            text = source.readString();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(text);
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}
