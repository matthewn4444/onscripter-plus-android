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
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.onscripter.plus.CopyFilesDialogTask.CopyFileInfo;
import com.onscripter.plus.CopyFilesDialogTask.CopyFilesDialogListener;
import com.onscripter.plus.CopyFilesDialogTask.Result;

public final class ExtSDCardFix {
    // This value will be set before the user has any input in the app
    private static boolean sExtSDCardWritable = true;
    private static boolean sAlreadyScanned = false;

    private final Activity mActivity;
    private final FileSystemAdapter mAdapter;
    private Dialog mFixDialog;

    // For us to scan, user must be in the following state:
    //  1. Must be Kitkat (4.4) or higher
    //  2. Must have an external sd card
    //  3. User has games in this folder (which is in external memory)
    //  4. External memory card is not writable
    //  5. User's default game folder must be in external sd card path
    public static boolean folderNeedsFix(File currentPath) {
        if (currentPath.isFile()) {
            currentPath = currentPath.getParentFile();
        }
        return isKitKatOrHigher() && !sExtSDCardWritable && Environment2.getExternalSDCardDirectory() != null
                && currentPath.getPath().contains(Environment2.getExternalSDCardDirectory().getPath())
                && getNumONScripterGames(currentPath).length > 0;
    }


    public ExtSDCardFix(Activity activity, FileSystemAdapter adapter) {
        mActivity = activity;
        mAdapter = adapter;

        // Don't rescan if already scanned
        synchronized (ExtSDCardFix.this) {
            if (sAlreadyScanned) {
                return;
            }
        }

        // Only calculate if over kitkat and has external sd card
        if (isKitKatOrHigher()) {
            if (Environment2.getExternalSDCardDirectory() != null) {
                new CheckWritableTask().execute();
                return;
            }
       }

       // Everything is fine, moving on
       synchronized (ExtSDCardFix.this) {
           sExtSDCardWritable = Environment2.hasExternalSDCard();
           sAlreadyScanned = true;
       }
    }

    private static boolean isKitKatOrHigher() {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT;
    }

    /**
     * This task runs very fast just to test if we can write on the external sdcard
     * This will occur on startup and will finish before user can do anything.
     * @author CHaSEdBYmAnYcrAZy
     *
     */
    private class CheckWritableTask extends AsyncTask<Void, Void, Integer> {
        @Override
        protected Integer doInBackground(Void... params) {
            synchronized (ExtSDCardFix.this) {
                sExtSDCardWritable = checkIsExternalWritable();
                sAlreadyScanned = true;
            }
            return getCurrentONScripterGames().length;
        }
        @Override
        protected void onPostExecute(Integer numGames) {
            super.onPostExecute(numGames);
            // If there are currently games listed and in external sd card location, show this dialog
            if (needsFix()) {
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

            // Inflate the dialog
            LayoutInflater inflater = (LayoutInflater) mActivity.getSystemService( Context.LAYOUT_INFLATER_SERVICE);
            LinearLayout layout = (LinearLayout) inflater.inflate(R.layout.fix_dialog, null);
            final ListView listview = (ListView) layout.findViewById(R.id.options);
            listview.setAdapter(adapter);
            listview.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            mFixDialog = new AlertDialog.Builder(mActivity)
                        .setTitle(R.string.dialog_fix_sd_card_write_title)
                        .setView(layout)
                        .setCancelable(false)
                        .setPositiveButton(R.string.dialog_select_button_text, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                switch (listview.getCheckedItemPosition()) {
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

    /**
     * Returns whether we are able to write to external sdcard
     * To see if we have that Kitkat issue, use folderNeedsFix().
     * Also if there is no external sdcard, then this will be false.
     * @return
     */
    public synchronized boolean isWritable() {
        return sExtSDCardWritable;
    }

    /**
     * Same thing as folderNeedsFix() but the path is taken from
     * the adapter passed in
     * @return
     */
    public boolean needsFix() {
        return folderNeedsFix(mAdapter.getCurrentDirectory());
    }

    /**
     * Get the number of ONScripter games in the folder attached
     * to the file system adapter.
     * @return
     */
    private File[] getCurrentONScripterGames() {
        return getNumONScripterGames(mAdapter.getCurrentDirectory());
    }

    /**
     * Get the number of ONScripter games in a folder
     * @param folder
     * @return
     */
    private static File[] getNumONScripterGames(File folder) {
        return folder.listFiles(new FileFilter() {
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

    /**
     * Checks the external sdcard to see if we can write to it
     * Writes a small file to the external sdcard and if successful,
     * then we return true, if exception occurs, returns false.
     * @return
     */
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
                new CopyFilesDialogTask(mActivity, new CopyFilesDialogListener() {
                    @Override
                    public void onCopyCompleted(Result resultCode) {
                        switch(resultCode) {
                        case SUCCESS:
                            alert(R.string.message_copy_completed);

                            // Update the preferences with the new folder in internal memory
                            Editor editor = PreferenceManager.getDefaultSharedPreferences(mActivity).edit();
                            editor.putString(mActivity.getString(R.string.settings_folder_default_key), result.getAbsolutePath());
                            editor.apply();

                            // Change folder location
                            mAdapter.setCurrentDirectory(result);
                            break;
                        case NO_FILE_SELECTED:      // This is pretty much impossible to happen
                            alert(R.string.message_copy_failed_no_files);
                            break;
                        case NO_SPACE_ERROR:
                            alert(R.string.message_copy_failed_no_space);
                            break;
                        case COPY_ERROR:
                            alert(R.string.message_copy_failed);
                            break;
                        }
                    }
                }).executeCopy(info);
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        dialog.setDialog(builder.create());
        dialog.show(Environment2.getInternalStorageDirectory().getAbsolutePath());
    }
}
