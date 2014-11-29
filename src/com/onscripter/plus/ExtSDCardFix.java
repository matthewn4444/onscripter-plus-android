package com.onscripter.plus;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;

import jrummy.sdfix.SDFix;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.bugsense.trace.BugSenseHandler;
import com.onscripter.plus.Analytics.SDCARD_FIX_OPTIONS;
import com.onscripter.plus.CopyFilesDialogTask.CopyFileInfo;
import com.onscripter.plus.CopyFilesDialogTask.CopyFilesDialogListener;
import com.onscripter.plus.CopyFilesDialogTask.Result;

public final class ExtSDCardFix {
    // This value will be set before the user has any input in the app
    private static boolean sExtSDCardWritable = true;
    private static boolean sAlreadyScanned = false;

    private static boolean sHasInit = false;
    private static Object sSaveFolderLock = new Object();
    private static File sSaveFolder = null;

    private final Activity mActivity;
    private final FileSystemAdapter mAdapter;
    private OnSDCardFixListener mListener;
    private AlertDialog mFixDialog;
    private SharedPreferences mPrefs;

    private static final String SETTINGS_SAVE_FOLDER_KEY = App.string(R.string.settings_save_folder_key);

    interface OnSDCardFixListener {
        public void writeTestFinished();
        public void option1Finished();
        public void option2Finished();
        public void oneGameCopyFinished(String gamepath);
        public void copySaveFilesBack();
    }

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
        return isKitKatOrHigher() && !sExtSDCardWritable && isPathInExtSDCard(currentPath)
                && getNumONScripterGames(currentPath).length > 0;
    }

    public ExtSDCardFix(Activity activity, FileSystemAdapter adapter, OnSDCardFixListener listener) {
        mActivity = activity;
        mAdapter = adapter;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
        mListener = listener;

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

       if (mListener != null) {
           mListener.writeTestFinished();
       }
    }

    /**
     * Get the save folder where save files may exist
     * @return null if no save folder exists
     */
    public static File getSaveFolder() {
        ensureInit();
        return sSaveFolder;
    }

    /**
     * Set the save folder and check if it exists
     * @param path
     */
    public static void setSaveFolder(String path) {
        synchronized (sSaveFolderLock) {
            sSaveFolder = path != null ? new File(path) : null;
            verifySaveFileExistance();
            if (sSaveFolder != null) {
                PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .edit().putString(SETTINGS_SAVE_FOLDER_KEY, path).apply();
            }
        }
    }

    /**
     * Get's the games current VNPreference file (pref.xml) handler.
     * This will choose the preference file from save folder if the file
     * exists inside the game's folder in the save folder's path
     * Will return null only if there is no game folder in the save folder
     * when save folder exists
     * @param ctx
     * @param path to the game
     * @return an instance of VNPreference pointing to the preference file
     */
    public static VNPreferences getGameVNPreference(String path) {
        if (path == null) {
            return null;
        }
        File saveFolder = getSaveFolder();
        File pathFile = new File(path);
        if (folderNeedsFix(pathFile.getParentFile())) {
            File thatFolder = new File(saveFolder + "/" + pathFile.getName());
            if (thatFolder.exists()) {
                return new VNPreferences(thatFolder.getAbsolutePath());
            } else {
                return null;
            }
        }
        return new VNPreferences(path);
    }

    /**
     * Sets up the save folder
     */
    private static void ensureInit() {
        if (!sHasInit) {
            synchronized (sSaveFolderLock) {
                if (!sHasInit) {
                    sHasInit = true;
                    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(App.getContext());
                    String sf = pref.getString(SETTINGS_SAVE_FOLDER_KEY, null);
                    if (sf != null) {
                        sSaveFolder = new File(sf);
                        verifySaveFileExistance();
                    }
                }
            }
        }
    }

    private static void verifySaveFileExistance() {
        if (sSaveFolder == null || sSaveFolder != null && !sSaveFolder.exists()) {
            PreferenceManager.getDefaultSharedPreferences(App.getContext())
                .edit().remove(SETTINGS_SAVE_FOLDER_KEY).apply();
            sSaveFolder = null;
        }
    }

    /**
     * Checks if provided path is within the external sdcard path
     * @param file path
     * @return
     */
    private static boolean isPathInExtSDCard(File file) {
        return Environment2.getExternalSDCardDirectory() != null &&
                file.getAbsolutePath().contains(Environment2.getExternalSDCardDirectory().getAbsolutePath());
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

            // If there are currently games listed and in external sd card location
            // and no save folder in pref, show this dialog
            File saveFolder = getSaveFolder();
            if (needsFix()) {
                if (saveFolder == null) {
                    showFixDialog();
                }
            } else if (isKitKatOrHigher() && sExtSDCardWritable && saveFolder != null) {
                // Check to see if currently in external sdcard directory
                if (isPathInExtSDCard(mAdapter.getCurrentDirectory())) {
                    promptToCopySaveFilesBack();
                } else {
                    // Since we do not need to copy saves to internal storage, just output message
                    copySaveFilesBackFinished();
                }
            }

            if (mListener != null) {
                mListener.writeTestFinished();
            }
        }
    }

    private void copySaveFilesBackFinished() {
        Analytics.sendNowWritableCopySavesBack();

        alert(R.string.message_user_handle_left_over_saves);
        setSaveFolder(null);
        if (mListener != null) {
            mListener.copySaveFilesBack();
        }
    }

    private void promptToCopySaveFilesBack() {
        // Look for common folders from both directories
        final File saveFolder = getSaveFolder();
        final ArrayList<CopyFileInfo> commonFolders = new ArrayList<CopyFileInfo>();
        File folder = mAdapter.getCurrentDirectory();
        File[] files = folder.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                File src = new File(saveFolder + "/" + file.getName());
                if (src.exists() && src.isDirectory()) {
                    commonFolders.add(new CopyFileInfo(src.getAbsolutePath(), file.getAbsolutePath()));
                }
            }
        }

        // Regardless what the user chooses, the
        if (!commonFolders.isEmpty()) {
            // Before wasn't able to save and now they can, move save files back
            new AlertDialog.Builder(mActivity)
                .setMessage(R.string.message_move_save_files_back)
                .setPositiveButton(android.R.string.ok, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        CopyFileInfo[] info = commonFolders.toArray(new CopyFileInfo[commonFolders.size()]);
                        copyGameFiles(info, CopyGameFileFF, new FileFilter() {
                            @Override
                            public boolean accept(File pathname) {
                                return pathname.getParentFile().equals(saveFolder) // If parent is current directory
                                        || pathname.getName().toLowerCase(                  // If folder starts with save
                                                Locale.getDefault()).startsWith("save");
                            }
                        }, CopyGameFileFF,
                                new OnCopyRoutineFinished() {
                            @Override
                            public void onSuccess() {
                                copySaveFilesBackFinished();
                            }
                            @Override
                            public void onDialogCancel() {
                                copySaveFilesBackFinished();
                            }
                        });
                    }
                })
                .setNegativeButton(android.R.string.cancel, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        copySaveFilesBackFinished();
                    }
                })
                .show();
        } else {
            copySaveFilesBackFinished();
        }
    }

    /**
     * This is shown when the user needs to move their files because Kitkat
     * does not allow 3rd party apps to write to external storage. Following
     * this there are 3 options to bypass the situation.
     */
    public void showFixDialog() {
        boolean alreadyBuilt = true;
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
                                    option2CopyGameSaveFiles();
                                    break;
                                case 2:
                                    option3SetSystemWritable();
                                    break;
                                }
                            }
                        })
                        .setNeutralButton(android.R.string.cancel, null)
                        .create();

            listview.setOnItemClickListener(new OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view,
                        int position, long id) {
                    listview.setOnItemClickListener(null);
                    mFixDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                }
            });
            alreadyBuilt = false;
        }
        mFixDialog.show();
        if (!alreadyBuilt) {
            mFixDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        }
    }

    /**
     * Returns whether we are able to write to external sdcard
     * To see if we have that Kitkat issue, use folderNeedsFix().
     * Also if there is no external sdcard, then this will be false.
     * @return
     */
    public static synchronized boolean isWritable() {
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
     * Asks if we are able to launch the game given the path, if it can, it
     * will add the save folder to the bundle passed.
     * @param path
     * @param bundle to put save folder if it runs
     * @return if we should run the game
     */
    public boolean shouldLaunchGame(String path, Bundle bundle) {
        if (needsFix()) {
            // Fix #2: Check if this game has a folder in save directory, then use that
            File saveFolder = getSaveFolder();
            if (saveFolder != null && saveFolder.exists()) {
                // The game folder name is also in the save folder, use this save directory
                final File gameFile = new File(path);
                final String thatFolder = saveFolder + "/" + gameFile.getName();
                if (new File(thatFolder).exists()) {
                    bundle.putString(ONScripter.SAVE_DIRECTORY_EXTRA, thatFolder);
                } else {
                    moveOneGameSave(gameFile);
                    return false;
                }
            } else {
                showFixDialog();
                return false;
            }
        }
        return true;
    }

    /**
     * Asks user if they would like to move this folder to the save location,
     * then copies all save files to it
     * This can fail and will show dialog if not enough space
     * @param filepath of the game folder to copy
     */
    public void moveOneGameSave(final File filepath) {
        String name = filepath.getName();
        File saveFolder = getSaveFolder();

        final CopyFileInfo[] info = new CopyFileInfo[] {
                new CopyFileInfo(filepath.getAbsolutePath(), saveFolder + "/" + name)
        };
        copyGameFiles(info, CopyGameFileFF, CopyGameFolderFF, CopyGameFileFF, false,
                new OnCopyRoutineFinished() {
            @Override
            public void onSuccess() {
                if (mListener != null) {
                    mListener.oneGameCopyFinished(filepath.getAbsolutePath());
                }
            }
        });
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

    private static boolean isStrOneOf(String[] array, String item) {
        if (item == null) {
            return false;
        }
        for (String name : array) {
            if (item.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /* File Filter: copy save, images and other metadata files */
    private static FileFilter CopyGameFileFF = new FileFilter() {
        @Override
        public boolean accept(File pathname) {
            final String[] acceptable = {
                "pref.xml", "envdata", "gloval.sav", "kidoku.dat",
                "stderr.txt", "stdout.txt"
            };
            String name = pathname.getName();
            return name.startsWith("save") && (name.endsWith(".bmp")                    // File: save___.[dat/png/jpg/bmp]
                        || name.endsWith(".dat") || name.endsWith(".png")
                        || name.endsWith(".jpg"))
                    || pathname.getParentFile().getName().toString().startsWith("save") // Parent folder is save folder
                    ||isStrOneOf(acceptable, name);                                     // If file is one of the acceptable files
            }
    };

    /* Folder filter: copy only save folders (starts with "save") */
    private final FileFilter CopyGameFolderFF = new FileFilter() {
        @Override
        public boolean accept(File pathname) {
            return pathname.getParentFile().equals(mAdapter.getCurrentDirectory()) // If parent is current directory
                    || pathname.getName().toLowerCase(                  // If folder starts with save
                            Locale.getDefault()).startsWith("save");
        }
    };

    /**
     * External SD Card Fixes
     */
    /* Option 1: Move all games in current folder to another folder to internal storage */
    private void option1CopyGameFiles() {
        final CopyToInternalStorageRoutine routine = new CopyToInternalStorageRoutine(mActivity, null, null, CopyGameFileFF);
        routine.setOnCopyRoutineFinished(new OnCopyRoutineFinished() {
            @Override
            public void onSuccess() {
                Analytics.sendSDCardFixOption(SDCARD_FIX_OPTIONS.COPY_GAMES);

                File result = routine.getResultDirectory();
                alert(R.string.message_copy_completed);

                // Update the preferences with the new folder in internal memory
                mPrefs.edit()
                    .putString(mActivity.getString(R.string.settings_folder_default_key),
                        result.getAbsolutePath()).apply();

                // Change folder location
                mAdapter.setCurrentDirectory(result);

                if (mListener != null) {
                    mListener.option1Finished();
                }
            }
        });
        routine.execute();
    }

    /* Option 2: Move all saves for each game in the current folder to another folder to internal storage */
    private void option2CopyGameSaveFiles() {
        final File currentDir = mAdapter.getCurrentDirectory();
        final CopyToInternalStorageRoutine routine = new CopyToInternalStorageRoutine(mActivity,
                CopyGameFileFF, CopyGameFolderFF, CopyGameFileFF);
        routine.setOnCopyRoutineFinished(new OnCopyRoutineFinished() {
            @Override
            public void onSuccess() {
                Analytics.sendSDCardFixOption(SDCARD_FIX_OPTIONS.COPY_SAVES);

                File result = routine.getResultDirectory();
                alert(R.string.message_copy_saves_completed);

                // Update the preferences with the new save folder
                setSaveFolder(result.getAbsolutePath());

                if (mListener != null) {
                    mListener.option2Finished();
                }
            }
        });
        routine.execute();
    }

    /* Option 3: Needs Root; make external SD card writable again by editing system preference files */
    private void option3SetSystemWritable() {
        // Warn the user and ask the them if they want this app to modify the system files
        LayoutInflater inflator = mActivity.getLayoutInflater();
        View content = inflator.inflate(R.layout.sdcard_fix_su_dialog, null);

        final AlertDialog dialog = new AlertDialog.Builder(mActivity)
            .setTitle(R.string.app_name)
            .setView(content)
            .setCancelable(false)
            .setNegativeButton(R.string.dialog_back_button_text, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    showFixDialog();
                }
            })
            .setPositiveButton(R.string.dialog_next_button_text, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    applyRootSDCardFix();
                }
            })
            .show();

        final CheckedTextView chbxView = (CheckedTextView)content.findViewById(R.id.agree_checkbox);
        chbxView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                chbxView.setChecked(!chbxView.isChecked());
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(chbxView.isChecked());
            }
        });
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(chbxView.isChecked());
    }

    /**
     * If user has root, this will fix the external sdcard writing issues introduced in
     * kitkat on some devices. This also shows UI and at the end prompts the user
     * to reboot the device.
     *
     * This uses code form jrummy.sdfix to apply the permissions fix. All it does is
     * adds a line "<group gid=\"media_rw\" />" in "/system/etc/permissions/platform.xml"
     * so that on the next reboot, the user will have permission to write to external
     * sdcard.
     */
    private void applyRootSDCardFix() {
        try {
            if (SDFix.isRemovableStorageWritableFixApplied()) {
                // We can only get called here if user has not yet restarted the
                // phone after applying the fix
                alert(R.string.message_su_sdcard_fix_exists_no_write);
                return;
            }
        } catch (FileNotFoundException e2) {
            e2.printStackTrace();
            alert(R.string.message_su_sdcard_fix_no_file);
            return;
        } catch (IOException e2) {
            e2.printStackTrace();
            BugSenseHandler.sendException(e2);
            alert(R.string.message_su_sdcard_fix_issue);
            return;
        }

        // Run the external sdcard fix
        final ProgressDialog progressDialog = new ProgressDialog(mActivity);
        progressDialog.setMessage(mActivity.getString(R.string.dialog_su_sdcard_fix_applying));
        progressDialog.setCancelable(false);
        progressDialog.show();
        new Thread() {
            @Override
            public void run() {
                boolean isFixed = false;
                boolean notFoundThrown = false;
                boolean ioThrown = false;
                try {
                    isFixed = SDFix.fixPermissions(mActivity);
                } catch (IOException e) {
                    try {
                        isFixed = SDFix.isRemovableStorageWritableFixApplied();
                    } catch (FileNotFoundException e1) {
                        BugSenseHandler.sendException(e1);
                        e1.printStackTrace();
                        notFoundThrown = true;
                    } catch (IOException e1) {
                        BugSenseHandler.sendException(e1);
                        e1.printStackTrace();
                        ioThrown = true;
                    }
                }
                final boolean[] results = {isFixed, notFoundThrown, ioThrown};
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();
                        if (results[1]) {
                            // Check for FileNotFoundException
                            alert(R.string.message_su_sdcard_fix_no_file);
                        } else if (results[2]) {
                            // Check for IOException
                            alert(R.string.message_su_sdcard_fix_issue);
                        } else if (results[0]) {
                            Analytics.sendSDCardFixOption(SDCARD_FIX_OPTIONS.ROOT_PERMISSIONS);

                            // Worked fine, tell the user either to close app or reboot the phone
                            new AlertDialog.Builder(mActivity)
                                .setTitle(R.string.app_name)
                                .setCancelable(false)
                                .setMessage(R.string.message_restart_device)
                                .setNegativeButton(R.string.dialog_close_app, new OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface arg0, int arg1) {
                                        mActivity.finish();
                                    }
                                })
                                .setPositiveButton(R.string.dialog_reboot, new OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        try {
                                            Runtime.getRuntime().exec(new String[]{"su","-c","reboot now"});
                                        } catch (IOException e) {
                                            BugSenseHandler.sendException(e);
                                            e.printStackTrace();
                                        }
                                    }
                                })
                                .show();
                        } else {
                            // Failed to work
                            alert(R.string.message_su_sdcard_fix_issue);
                        }
                    }
                });
            }
        }.start();
    }

    /**
     * Intitiates the copying process to move files from one location to another
     * @param info
     * @param fileFilter
     * @param folderFilter
     * @param listener
     */
    private void copyGameFiles(CopyFileInfo[] info, final FileFilter fileFilter,
            final FileFilter folderFilter, final FileFilter overwriteFilter,
            final OnCopyRoutineFinished listener) {
        copyGameFiles(info, fileFilter, folderFilter, overwriteFilter, true, listener);
    }
    private void copyGameFiles(CopyFileInfo[] info, final FileFilter fileFilter,
            final FileFilter folderFilter, final FileFilter overwriteFilter,
            final boolean allowUserChoice, final OnCopyRoutineFinished listener) {
        new CopyFilesDialogTask(mActivity, new CopyFilesDialogListener() {
            @Override
            public void onCopyCompleted(Result resultCode) {
                switch(resultCode) {
                case SUCCESS:
                    if (listener != null) {
                        listener.onSuccess();
                    }
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
                case CANCELLED:
                    if (listener != null) {
                        listener.onDialogCancel();
                    }
                    break;
                default:
                    break;
                }
            }
        }, fileFilter, folderFilter, overwriteFilter, allowUserChoice).executeCopy(info);
    }

    private abstract class OnCopyRoutineFinished {
        public abstract void onSuccess();
        public void onDialogCancel() {}
    }

    private class CopyToInternalStorageRoutine extends FolderBrowserDialogWrapper {
        private OnCopyRoutineFinished mListener;
        public CopyToInternalStorageRoutine(Context context) {
            this(context, null, null, null);
        }

        public CopyToInternalStorageRoutine(final Context context, final FileFilter fileFilter,
                final FileFilter folderFilter, final FileFilter overwriteFilter) {
            super(context, false, true);

            AlertDialog.Builder builder = new Builder(context);
            builder.setView(getDialogLayout());
            builder.setTitle(R.string.dialog_choose_copy_dst_title);
            builder.setNegativeButton(android.R.string.cancel, null);
            builder.setPositiveButton(R.string.dialog_select_button_text, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    final File result = getResultDirectory();
                    File[] games = getCurrentONScripterGames();
                    final CopyFileInfo[] info = new CopyFileInfo[games.length];
                    for (int i = 0; i < info.length; i++) {
                        info[i] = new CopyFileInfo(games[i].getAbsolutePath(), result + "/" + games[i].getName());
                    }
                    copyGameFiles(info, fileFilter, folderFilter, overwriteFilter, mListener);
                }
            });
            setDialog(builder.create());
        }

        public void setOnCopyRoutineFinished(OnCopyRoutineFinished listener) {
            mListener = listener;
        }

        public void execute() {
            show(Environment2.getInternalStorageDirectory().getAbsolutePath());
        }
    }
}
