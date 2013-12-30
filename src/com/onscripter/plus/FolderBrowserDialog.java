package com.onscripter.plus;

import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.preference.DialogPreference;
import android.util.AttributeSet;

public class FolderBrowserDialog extends DialogPreference implements OnClickListener {

    public FolderBrowserDialog(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FolderBrowserDialog(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setPositiveButtonText(R.string.dialog_select_button_text);
        setNegativeButtonText(android.R.string.cancel);

        // Apply the layout for ics and up
        int currentapiVersion = android.os.Build.VERSION.SDK_INT;
        if (currentapiVersion > context.getResources().getInteger(R.integer.gingerbread_version)) {
            setLayoutResource(R.layout.dialog_preference);
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        // TODO Auto-generated method stub
    }
}
