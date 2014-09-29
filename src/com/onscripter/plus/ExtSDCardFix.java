package com.onscripter.plus;

import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.onscripter.plus.CopyFilesDialogTask.CopyFileInfo;
import com.onscripter.plus.CopyFilesDialogTask.CopyFilesDialogListener;

public final class ExtSDCardFix {
    private static boolean sExtSDCardWritable = true;
    private static boolean sAlreadyScanned = false;

    private ExtSDCardFixListener mListener;
    private final Activity mActivity;
    private final FileSystemAdapter mAdapter;
    private Dialog mFixDialog;

    public interface ExtSDCardFixListener {
        public void scanCompleteNeedFix();
    }

    public ExtSDCardFix(Activity activity, FileSystemAdapter adapter) {
        mActivity = activity;
        mAdapter = adapter;

        // For us to scan, user must be in the following state:
        //  1. Must be Kitkat (4.4) or higher
        //  2. Must have an external sd card
        //  3. User's default game folder must be in external sd card path
        //  4. User has games in this folder (which is in external memory)
        //  5. External memory card is not writable
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            File extSdCard = Environment2.getExternalSDCardDirectory();
            if (extSdCard != null && mAdapter.getCurrentDirectory().getPath().contains(extSdCard.getPath())) {
                // Need to fix if not writable
                boolean hasScanned = false;
                synchronized (ExtSDCardFix.this) {
                    hasScanned = sAlreadyScanned;
                }
                if (!hasScanned) {
                    new CheckWritableTask().execute();
                }
                return;
            }
       }

       // Everything is fine, moving on
       synchronized (ExtSDCardFix.this) {
           sExtSDCardWritable = Environment2.hasExternalSDCard();
           sAlreadyScanned = true;
       }
    }

    private class CheckWritableTask extends AsyncTask<Void, Void, Integer> {
        @Override
        protected Integer doInBackground(Void... params) {
            synchronized (ExtSDCardFix.this) {
                sExtSDCardWritable = checkIsExternalWritable();
                sAlreadyScanned = true;
            }
            if (mListener != null) {
               mListener.scanCompleteNeedFix();
            }
            return getCurrentONScripterGames().length;
        }
        @Override
        protected void onPostExecute(Integer numGames) {
            super.onPostExecute(numGames);
            // If there are currently games listed, show this dialog
            if (!sExtSDCardWritable && numGames > 0) {
                showFixDialog();
            }
        }
    }

    /**
     * This is shown when the user needs to move their files because Kitkat
     * does not allow 3rd party apps to write to external storage. Following
     * this there are 3 options to bypass the situation.
     */
    public void showFixDialog() {
        if (mFixDialog == null) {
            String[] options = mActivity.getResources().getStringArray(R.array.dialog_opt_fix_sd_card_write);
            final ListAdapter adapter = new ArrayAdapter<String>(mActivity, R.layout.radiobutton_choice_item, options);
            mFixDialog = new AlertDialog.Builder(mActivity)
                        .setTitle(R.string.dialog_fix_sd_card_write_title)
                        .setSingleChoiceItems(adapter, -1, null)
                        .setPositiveButton(R.string.dialog_select_button_text, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ListView lv = ((AlertDialog)mFixDialog).getListView();
                                switch (lv.getCheckedItemPosition()) {
                                case 0:
                                    option1CopyGameFiles();
                                    break;
                                case 1:
                                    // TODO move save files
                                    break;
                                case 2:
                                    // TODO apply root sd card fix
                                    break;
                                }
                            }
                        })
                        .setNeutralButton(android.R.string.cancel, null)
                        .create();
        }
        mFixDialog.show();
    }

    public void setExtSDCardFixListener(ExtSDCardFixListener listener) {
        mListener = listener;
    }

    public synchronized boolean isWritable() {
        return sExtSDCardWritable;
    }

    private File[] getCurrentONScripterGames() {
        return mAdapter.getCurrentDirectory().listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                if (pathname.isDirectory()) {
                    return LauncherActivity.isDirectoryONScripterGame(pathname);
                }
                return false;
            }
        });
    }

    private String randomFileName(int length) {
        Random r = new Random(); // just create one and keep it around
        final String alphabet = "abcdefghijklmnopqrstuvwxyz";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private boolean checkIsExternalWritable() {
        File ext = Environment2.getExternalSDCardDirectory();
        if (ext == null) {
            return false;
        }
        File file = null;
        do {
            file = new File(ext + "/" + randomFileName(15));
        } while(file.exists());

        FileWriter fw = null;
        try {
            fw = new FileWriter(file);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (fw != null) {
                try {
                    fw.close();
                } catch(Exception e) {}
            }
        }

        // Yep its writable
        file.delete();
        return true;
    }

    private void alert(int resMessage) {
        new AlertDialog.Builder(mActivity)
            .setTitle(mActivity.getString(R.string.app_name))
            .setMessage(resMessage)
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    /**
     * External SD Card Fixes
     */
    /* Option 1: Move all games in current folder to another folder to internal storage */
    private void option1CopyGameFiles() {
        final FolderBrowserDialogWrapper dialog = new FolderBrowserDialogWrapper(mActivity, false, true);

        AlertDialog.Builder builder = new Builder(mActivity);
        builder.setView(dialog.getDialogLayout());
        builder.setTitle(R.string.dialog_choose_copy_dst_title);
        builder.setPositiveButton(R.string.dialog_select_button_text, new OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                final File result = dialog.getResultDirectory();
                File[] games = getCurrentONScripterGames();
                final CopyFileInfo[] info = new CopyFileInfo[games.length];
                for (int i = 0; i < info.length; i++) {
                    info[i] = new CopyFileInfo(games[i].getAbsolutePath(), result + "/" + games[i].getName());
                }
                new CopyFilesDialogTask(mActivity, info, new CopyFilesDialogListener() {
                    @Override
                    public void onCopyCompleted(int resultCode) {
                        switch(resultCode) {
                        case CopyFilesDialogTask.RESULT_SUCCESS:
                            alert(R.string.message_copy_completed);

                            // Update the preferences with the new folder in internal memory
                            Editor editor = PreferenceManager.getDefaultSharedPreferences(mActivity).edit();
                            editor.putString(mActivity.getString(R.string.settings_folder_default_key), result.getAbsolutePath());
                            editor.apply();

                            // Change folder location
                            mAdapter.setCurrentDirectory(result);
                            break;
                        case CopyFilesDialogTask.RESULT_COPY_ERROR:
                            alert(R.string.message_copy_failed);
                            break;
                        }
                    }
                }).execute();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        dialog.setDialog(builder.create());
        dialog.show(Environment2.getInternalStorageDirectory().getAbsolutePath());
    }
}
