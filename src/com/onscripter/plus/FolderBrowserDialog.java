package com.onscripter.plus;

import java.io.File;
import java.io.FileNotFoundException;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnKeyListener;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;
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

        setupDirectories(getPersistedString(null));
    }

    private void setupDirectories(String path) {
        // Detect if External sdcard is available, if not then remove the sdcard button and adjust the layout
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
        File openDir;
        if (path != null) {
            openDir = new File(path);
            if (!openDir.exists()) {
                openDir = InternalStorage;
            }
        } else {
            openDir = InternalStorage;
        }

        // Detect where the current directory is either from internal or external storage
        if (openDir.getPath().contains(InternalStorage.getPath())) {
            mUpperBoundFile = InternalStorage;
            mCurrentExternalPath = ExternalStorage;
        } else {
            mUpperBoundFile = ExternalStorage;
            mCurrentInternalPath = InternalStorage;
            mTogglePath.setText(mCtx.getString(R.string.dialog_interal_storage_text));
        }

        try {
            mAdapter = new FileSystemAdapter(mCtx, openDir, !openDir.equals(mUpperBoundFile), true);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            getDialog().dismiss();
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
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            String value = mAdapter.getCurrentDirectory().getPath();
            if (callChangeListener(value)) {
                persistString(value);
            }
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
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

    private void toggleGotoButton() {
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

    @Override
    public void onClick(View v) {
        toggleGotoButton();
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
        myState.text = mAdapter.getCurrentDirectory().getPath();
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

        InternalStorage = Environment.getExternalStorageDirectory();
        ExternalStorage = Environment2.getExternalSDCardDirectory();
        setupDirectories(myState.text);
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
