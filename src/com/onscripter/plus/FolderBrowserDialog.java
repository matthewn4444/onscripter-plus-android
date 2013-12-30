package com.onscripter.plus;

import java.io.File;
import java.io.FileNotFoundException;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnKeyListener;
import android.os.Bundle;
import android.os.Environment;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class FolderBrowserDialog extends DialogPreference implements OnItemClickListener, OnKeyListener {
    private final LinearLayout mLayout;
    private final ListView mListView;
    private FileSystemAdapter mAdapter;
    private final Context mCtx;
    private final TextView mPathText;

    public FolderBrowserDialog(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FolderBrowserDialog(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mCtx = context;
        setPositiveButtonText(R.string.dialog_select_button_text);
        setNegativeButtonText(android.R.string.cancel);

        // Apply the layout for ics and up
        int currentapiVersion = android.os.Build.VERSION.SDK_INT;
        if (currentapiVersion > context.getResources().getInteger(R.integer.gingerbread_version)) {
            setLayoutResource(R.layout.dialog_preference);
        }

        // Inflate the dialog
        LayoutInflater inflater = (LayoutInflater) context.getSystemService( Context.LAYOUT_INFLATER_SERVICE);
        mLayout = (LinearLayout) inflater.inflate(R.layout.folder_browser_dialog, null);
        mListView = (ListView) mLayout.findViewById(R.id.list);
        mPathText = (TextView) mLayout.findViewById(R.id.path);
        mListView.setOnItemClickListener(this);

        // Force dialog to max height
        WindowManager window = (WindowManager)mCtx.getSystemService(Context.WINDOW_SERVICE);
        Display display = window.getDefaultDisplay();
        mLayout.setMinimumHeight(display.getHeight());
    }

    @Override
    protected View onCreateDialogView() {
        return mLayout;
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        Dialog dialog = getDialog();
        dialog.setCancelable(false);
        dialog.setOnKeyListener(this);

        // Open default location from preference, if cannot find, then open storage
        File openDir = new File(Environment.getExternalStorageDirectory() + "/dssdds");
        if (!openDir.exists()) {
            openDir = Environment.getExternalStorageDirectory();
        }
        try {
            mAdapter = new FileSystemAdapter(mCtx, openDir);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            dialog.dismiss();
            Toast.makeText(mCtx, R.string.message_cannot_find_internal_storage, Toast.LENGTH_SHORT).show();
            return;
        }
        mAdapter.bindPathToTextView(mPathText);
        mAdapter.onlyShowFolders(true);
        mListView.setAdapter(mAdapter);
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        if (mLayout.getParent() != null) {
            ((ViewGroup)mLayout.getParent()).removeView(mLayout);
        }
        super.onDismiss(dialog);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        mAdapter.setChildAsCurrent(position);
    }

    @Override
    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
            if (mAdapter.getCurrentDirectory().equals(Environment.getExternalStorageDirectory())) {
                getDialog().dismiss();
            } else {
                mAdapter.moveUp();
            }
        }
        return false;
    }
}
