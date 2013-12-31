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
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class FolderBrowserDialog extends DialogPreference implements OnItemClickListener, OnKeyListener, OnClickListener {
    private final LinearLayout mLayout;
    private final ListView mListView;
    private FileSystemAdapter mAdapter;
    private final Context mCtx;
    private final TextView mPathText;
    private final Button mTogglePath;

    // Files
    private File mUpperBoundFile;
    private File mCurrentInternalPath;
    private File mCurrentExternalPath;
    private static File InternalStorage;
    private static File ExternalStorage;

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
        mTogglePath = (Button) mLayout.findViewById(R.id.toggleLocation);
        mListView.setOnItemClickListener(this);
        mTogglePath.setOnClickListener(this);

        // Force dialog to max height
        WindowManager window = (WindowManager)mCtx.getSystemService(Context.WINDOW_SERVICE);
        Display display = window.getDefaultDisplay();
        mLayout.setMinimumHeight(display.getHeight());

        if (InternalStorage == null) {
            InternalStorage = Environment.getExternalStorageDirectory();
            ExternalStorage = Environment2.getExternalSDCardDirectory();
        }
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

        // Detect if External sdcard is available, if not then remove the sdcard button and adjust the layout
        mCurrentExternalPath = ExternalStorage;
        final LinearLayout.LayoutParams textLayout = (LinearLayout.LayoutParams) mPathText.getLayoutParams();
        if (ExternalStorage != null) {
            ((View)mTogglePath.getParent()).setVisibility(View.VISIBLE);
            textLayout.weight = 6f;
        } else {
            ((View)mTogglePath.getParent()).setVisibility(View.GONE);
            textLayout.weight = 10f;
        }
        mPathText.setLayoutParams(textLayout);

        // Open default location from preference, if cannot find, then open storage
        File openDir = new File(Environment.getExternalStorageDirectory() + "/dssdds");
        if (!openDir.exists()) {
            openDir = InternalStorage;
        }

        // Detect where the current directory is either from internal or external storage
        LauncherActivity.log(openDir.getPath().contains(InternalStorage.getPath()));
        mUpperBoundFile = openDir.getPath().contains(InternalStorage.getPath()) ? InternalStorage : ExternalStorage;

        try {
            mAdapter = new FileSystemAdapter(mCtx, openDir, !openDir.equals(mUpperBoundFile), true);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            dialog.dismiss();
            Toast.makeText(mCtx, R.string.message_cannot_find_internal_storage, Toast.LENGTH_SHORT).show();
            return;
        }

        mAdapter.bindPathToTextView(mPathText);
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
        mAdapter.showBackListItem(position > 0 || !mAdapter.getCurrentDirectory()
                .equals(mUpperBoundFile));
    }

    @Override
    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
        // Move up a directory if back is pressed and have not hit the folder upperlimit
        if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
            if (mAdapter.getCurrentDirectory().equals(mUpperBoundFile)) {
                getDialog().dismiss();
            } else {
                if (mAdapter.getCurrentDirectory().getParentFile().equals(mUpperBoundFile)) {
                    mAdapter.showBackListItem(false);
                }
                mAdapter.moveUp();
            }
        }
        return false;
    }

    @Override
    public void onClick(View v) {
        // Toggle between the internal and external storage
        if (mUpperBoundFile.equals(InternalStorage)) {
            mCurrentInternalPath = mAdapter.getCurrentDirectory();
            mTogglePath.setText(mCtx.getString(R.string.dialog_interal_storage_text));
            mUpperBoundFile = ExternalStorage;
            mAdapter.setCurrentDirectory(mCurrentExternalPath);
        } else {
            mCurrentExternalPath = mAdapter.getCurrentDirectory();
            mTogglePath.setText(mCtx.getString(R.string.dialog_sd_card_text));
            mUpperBoundFile = InternalStorage;
            mAdapter.setCurrentDirectory(mCurrentInternalPath);
        }

        // Remove the back list item if we are at upperbound
        mAdapter.showBackListItem(!mUpperBoundFile.equals(mAdapter.getCurrentDirectory()));
    }
}
