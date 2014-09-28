package com.onscripter.plus;

import java.io.File;
import java.io.FileNotFoundException;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnKeyListener;
import android.os.Environment;
import android.text.method.TextKeyListener;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.bugsense.trace.BugSenseHandler;

public class FolderBrowserDialogWrapper implements OnItemClickListener, OnKeyListener {
    private final LinearLayout mLayout;
    private final ListView mListView;
    private FileSystemAdapter mAdapter;
    private final Context mCtx;
    private final TextView mPathText;
    private final ImageButton mTogglePath;
    private final ImageButton mNewFolderButton;
    private final TextView mExternalNotFoundText;
    private final boolean mAccessExtStorage;
    private Dialog mDialog;
    private Dialog mNewFolderDialog;
    private boolean isInInternalStorage;

    // Files
    private File mCurrentInternalPath;
    private File mCurrentExternalPath;
    private static File InternalStorage;
    private static File ExternalStorage;

    public FolderBrowserDialogWrapper(Context context) {
        this(context, true, false);
    }

    public FolderBrowserDialogWrapper(Context context, boolean accessExternalStorage, boolean ableToMakeFolders) {
        mCtx = context;
        mAccessExtStorage = accessExternalStorage;

        // Inflate the dialog
        LayoutInflater inflater = (LayoutInflater) context.getSystemService( Context.LAYOUT_INFLATER_SERVICE);
        mLayout = (LinearLayout) inflater.inflate(R.layout.folder_browser_dialog, null);
        mListView = (ListView) mLayout.findViewById(R.id.list);
        mPathText = (TextView) mLayout.findViewById(R.id.path);
        mExternalNotFoundText = (TextView) mLayout.findViewById(R.id.extNotFound);
        mTogglePath = (ImageButton) mLayout.findViewById(R.id.toggleLocation);
        mNewFolderButton = (ImageButton) mLayout.findViewById(R.id.newFolder);
        mListView.setOnItemClickListener(this);
        mTogglePath.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleGotoButton();
            }
        });
        if (ableToMakeFolders) {
            mNewFolderButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    final EditText input = new EditText(mCtx);
                    if (mNewFolderDialog == null) {
                        mNewFolderDialog = new AlertDialog.Builder(mCtx)
                            .setTitle(R.string.dialog_create_new_folder)
                            .setView(input)
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    // Folder is created
                                    String name = input.getText().toString();
                                    if (input.length() > 0) {
                                        TextKeyListener.clear(input.getText());
                                    }
                                    if (mAdapter.fileExists(name)) {
                                        Toast.makeText(mCtx, R.string.message_folder_exists, Toast.LENGTH_SHORT).show();
                                    } else {
                                        mAdapter.makeDirectory(name);
                                    }
                                }
                            }).setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int whichButton) {
                                }
                            }).create();
                    }
                    mNewFolderDialog.show();
                }
            });
            mNewFolderButton.setVisibility(View.VISIBLE);
        } else {
            mNewFolderButton.setVisibility(View.GONE);
        }

        // Force dialog to max height
        WindowManager window = (WindowManager)mCtx.getSystemService(Context.WINDOW_SERVICE);
        Display display = window.getDefaultDisplay();
        mLayout.setMinimumHeight(display.getHeight());

        if (InternalStorage == null) {
            InternalStorage = Environment.getExternalStorageDirectory();
            ExternalStorage = Environment2.getExternalSDCardDirectory();
        }
        if (ExternalStorage != null || mCtx.getString(R.string.dialog_ext_not_found).equals("")) {
            mExternalNotFoundText.setVisibility(View.GONE);
        }
    }

    public ViewGroup getDialogLayout() {
        return mLayout;
    }

    public void setDialog(Dialog dialog) {
        mDialog = dialog;
    }

    public void show(String directory) {
        if (mDialog == null) {
            throw new NullPointerException("Did not run setDialog() before showing.");
        }
        mDialog.setCancelable(false);
        mDialog.setOnKeyListener(this);
        setupDirectories(directory);
        mDialog.show();
    }

    public File getResultDirectory() {
        return mAdapter.getCurrentDirectory();
    }

    public void setupDirectories(String path) {
        if (mDialog == null) {
            throw new NullPointerException("Did not run setDialog() before showing.");
        }
        // Detect if External sdcard is available, if not then remove the sdcard button and adjust the layout
        final LinearLayout.LayoutParams textLayout = (LinearLayout.LayoutParams) mPathText.getLayoutParams();
        if (ExternalStorage == null || !mAccessExtStorage) {
            ((View)mTogglePath).setVisibility(View.GONE);
        } else {
            ((View)mTogglePath).setVisibility(View.VISIBLE);
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
            isInInternalStorage = true;
            mCurrentExternalPath = ExternalStorage;
        } else {
            if (!mAccessExtStorage) {
                throw new IllegalStateException(
                        "Cannot set the default location to external sd card when you have no access to it.");
            }
            isInInternalStorage = false;
            mCurrentInternalPath = InternalStorage;
            mTogglePath.setImageResource(R.drawable.ic_action_phone);
        }

        try {
            mAdapter = new FileSystemAdapter(mCtx, openDir, true, true);
            mAdapter.addLowerBoundFile(InternalStorage);
            mAdapter.addLowerBoundFile(ExternalStorage);
            mAdapter.refresh();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            mDialog.dismiss();
            BugSenseHandler.sendException(e);
            Toast.makeText(mCtx, R.string.message_cannot_find_internal_storage, Toast.LENGTH_SHORT).show();
            return;
        }

        mAdapter.bindPathToTextView(mPathText);
        mListView.setAdapter(mAdapter);
    }

    private void toggleGotoButton() {
       // Toggle between the internal and external storage
       if (isInInternalStorage) {
           mCurrentInternalPath = mAdapter.getCurrentDirectory();
           mTogglePath.setImageResource(R.drawable.ic_action_phone);
           mAdapter.setCurrentDirectory(mCurrentExternalPath);
       } else {
           mCurrentExternalPath = mAdapter.getCurrentDirectory();
           mTogglePath.setImageResource(R.drawable.ic_action_sd_storage);
           mAdapter.setCurrentDirectory(mCurrentInternalPath);
       }
       isInInternalStorage = !isInInternalStorage;
   }

    @Override
    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
        // Move up a directory if back is pressed and have not hit the folder upperlimit
        if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
            if (mAdapter.isDirectoryAtLowerBound()) {
                mDialog.dismiss();
            } else {
                mAdapter.moveUp();
            }
        }
        return false;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        mAdapter.setChildAsCurrent(position);
    }
}
