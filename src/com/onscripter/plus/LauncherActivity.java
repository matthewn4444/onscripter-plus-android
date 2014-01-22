package com.onscripter.plus;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.onscripter.plus.FileSystemAdapter.CustomFileTypeParser;
import com.onscripter.plus.FileSystemAdapter.LIST_ITEM_TYPE;

public class LauncherActivity extends SherlockActivity implements AdapterView.OnItemClickListener, CustomFileTypeParser
{
    private static final int REQUEST_CODE_SETTINGS = 1;
    private static final File DEFAULT_LOCATION = Environment.getExternalStorageDirectory();
    private static final String LAST_DIRECTORY = "last_directory_key";
    public static String DEFAULT_FONT_PATH = null;
    public static String DEFAULT_FONT_FILE = null;

    private AlertDialog.Builder mDialog = null;
    private FileSystemAdapter mAdapter = null;
    private FontFileCopyTask mCopyTask = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDialog = new AlertDialog.Builder(this);

        File directory = null;
        String lastDirectory = null;
        if (savedInstanceState != null) {
            lastDirectory = savedInstanceState.getString(LAST_DIRECTORY);
        }
        if (lastDirectory == null) {
            directory = getStartingDirectory();
            if (directory == null) {
                return;
            }
        } else {
            directory = new File(lastDirectory);
        }

        // Set up the listView and the adapter
        try {
            mAdapter = new FileSystemAdapter(this, directory, false, false, false, this);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        final ListView listView = new ListView(this);
        mAdapter.onlyShowFolders(true);
        listView.setAdapter(mAdapter);
        listView.setOnItemClickListener(this);
        setContentView(listView);

        if (DEFAULT_FONT_PATH == null || DEFAULT_FONT_FILE == null) {
            DEFAULT_FONT_FILE = getString(R.string.default_font_file);
            DEFAULT_FONT_PATH = getFilesDir() + "/" + DEFAULT_FONT_FILE;
        }

        // Copy the font file if it does not exist yet
        if (!new File(DEFAULT_FONT_PATH).exists()) {
            mCopyTask = new FontFileCopyTask(this, R.string.message_loading_fonts, false);
            mCopyTask.setCancelable(false);
            mCopyTask.execute();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(LAST_DIRECTORY, mAdapter.getCurrentDirectoryPath());
    }

    private File getStartingDirectory() {
        // Detect folder location if none is provided
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        String defaultDirPref = getString(R.string.settings_folder_default_key);
        String path = sp.getString(defaultDirPref, null);
        if (path == null) {
            // Check to see if there is a <internal storage>/ons
            File onsDefaultDir = new File(Environment.getExternalStorageDirectory() + "/ons");
            if (onsDefaultDir.exists()) {
                path = onsDefaultDir.getPath();
                sp.edit().putString(defaultDirPref, path).commit();
            } else if (Environment2.hasExternalSDCard()) {
                // Check to see if there is a <extSdCard storage>/ons
                onsDefaultDir = new File(Environment2.getExternalSDCardDirectory() + "/ons");
                if (onsDefaultDir.exists()) {
                    path = onsDefaultDir.getPath();
                    sp.edit().putString(defaultDirPref, path).commit();
                }
            }
        }
        File directory = path != null && new File(path).exists() ? new File(path) : DEFAULT_LOCATION;
        if (!directory.exists()){
            showError(getString(R.string.message_cannot_find_internal_storage));
            return null;
        }
        return directory;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
       MenuInflater inflater = getSupportMenuInflater();
       inflater.inflate(R.menu.menu_launcher, menu);
       return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
        case R.id.action_settings:
            Intent i = new Intent(this, Settings.class);
            startActivityForResult(i, REQUEST_CODE_SETTINGS);
            break;
        default:
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
        case REQUEST_CODE_SETTINGS:
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
            String path = sp.getString(getString(R.string.settings_folder_default_key), null);
            String currentPath = mAdapter.getCurrentDirectoryPath();
            if (path != null && path != currentPath) {
                File dir = new File(path);
                mAdapter.setCurrentDirectory(dir);
                currentPath = path;
            }
            break;
        }
    }

    @Override
    public LIST_ITEM_TYPE onFileTypeParse(File file) {
        if (file.isFile()) {
            return LIST_ITEM_TYPE.FILE;
        } else {
            // Folder
            if (isDirectoryONScripterGame(file)) {
                return LIST_ITEM_TYPE.FILE;
            } else {
                return LIST_ITEM_TYPE.FOLDER;
            }
        }
    }

    public static boolean isDirectoryONScripterGame(File file) {
        if (!file.canRead()) {
            return false;
        }
        File[] mDirectoryFiles = file.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return (file.isFile() &&
                        (file.getName().equals("0.txt") ||
                         file.getName().equals("00.txt") ||
                         file.getName().equals("nscr_sec.dat") ||
                         file.getName().equals("nscript.___") ||
                         file.getName().equals("nscript.dat")));
            }
        });
        return mDirectoryFiles.length > 0;
    }

    protected void goToActivity(Class<?> cls) {
        goToActivity(cls, null);
    }

    protected void goToActivity(Class<?> cls, Bundle bundle) {
        Intent i = new Intent(this, cls);
        if (bundle != null) {
            i.putExtras(bundle);
        }
        startActivity(i);
    }

    protected static void log(Object... txt) {
        String returnStr = "";
        int i = 1;
        int size = txt.length;
        if (size != 0) {
            returnStr = txt[0] == null ? "null" : txt[0].toString();
            for (; i < size; i++) {
                returnStr += ", "
                        + (txt[i] == null ? "null" : txt[i].toString());
            }
        }
        Log.i("lunch", returnStr);
    }

    private void alert(String message) {
        mDialog.setTitle(getString(R.string.app_name));
        mDialog.setMessage(message);
        mDialog.setPositiveButton(android.R.string.ok, null);
        mDialog.create().show();
    }

    private void showError(int stringResource) {
        showError(getString(stringResource));
    }
    private void showError(String message) {
        mDialog.setTitle("Error");
        mDialog.setMessage(message);
        mDialog.setPositiveButton(R.string.dialog_quit_button, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
        mDialog.create().show();
    }

    @Override
    public void onBackPressed() {
        if (mAdapter != null) {
            // Back button will exit
            if (mAdapter.getCurrentDirectory().equals(Environment.getExternalStorageDirectory())
                    || mAdapter.getCurrentDirectory().equals(Environment2.getExternalSDCardDirectory())) {
                super.onBackPressed();
            } else {
                mAdapter.moveUp();
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        File currentDir = mAdapter.getFile(position);

        // No permissions
        if (!currentDir.canRead()) {
            Toast.makeText(LauncherActivity.this, "Unable to open folder because of permissions", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isDirectoryONScripterGame(currentDir)) {
            // Use default font if there is none in the game folder
            if (new File(currentDir + "/" + DEFAULT_FONT_FILE).exists()) {
                startONScripter(currentDir.getPath());
            } else {
                if (mCopyTask != null) {    // Still copying, so wait till finished then run
                    mCopyTask.runNovelWhenFinished(currentDir.getPath());
                } else {
                    startONScripter(currentDir.getPath(), true);
                }
            }
        } else {
            mAdapter.setChildAsCurrent(position);
        }
    }

    private void startONScripter(String path) {
        startONScripter(path, false);
    }

    private void startONScripter(String path, boolean useDefaultFont) {
        Bundle b = new Bundle();
        if (useDefaultFont) {
            b.putBoolean(ONScripter.USE_DEFAULT_FONT_EXTRA, true);
        }
        b.putString(ONScripter.CURRENT_DIRECTORY_EXTRA, path);
        goToActivity(ONScripter.class, b);
    }

    class FontFileCopyTask extends ProgressDialogAsyncTask<Void, Void, Integer> {
        public FontFileCopyTask(Context ctx, String message, boolean showDialog) {
            super(ctx, message, showDialog);
        }
        public FontFileCopyTask(Context ctx, int stringResource, boolean showDialog) {
            super(ctx, ctx.getString(stringResource), showDialog);
        }

        private final int NO_PROBLEM = 0;
        private final int ASSET_NOT_FOUND = 1;
        private final int PRIVATE_PATH_NOT_FOUND = 2;
        private final int WRITING_ERROR = 3;

        private String mRunNovelWhenFinishedPath = null;

        public void runNovelWhenFinished(String path) {
            mRunNovelWhenFinishedPath = path;
            show();
        }

        @Override
        protected Integer doInBackground(Void... params) {
            InputStream is = null;
            OutputStream os = null;
            try {
                 is = getAssets().open(DEFAULT_FONT_FILE);
                 os = openFileOutput(DEFAULT_FONT_FILE, Context.MODE_PRIVATE);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                try {
                    is.close();
                } catch (IOException e1) {}
                return PRIVATE_PATH_NOT_FOUND;
            } catch (IOException e) {
                e.printStackTrace();
                return ASSET_NOT_FOUND;
            }

            // Copy file
            byte[] buffer = new byte[1024];
            int read;
            try {
                while((read = is.read(buffer)) != -1){
                  os.write(buffer, 0, read);
                }
                os.flush();
            } catch (IOException e) {
                e.printStackTrace();
                return WRITING_ERROR;
            } finally {
                try {
                    is.close();
                    os.close();
                } catch (IOException e) {}
            }
            return NO_PROBLEM;
        }

        @Override
        protected void onPostExecute(Integer result) {
            super.onPostExecute(result);
            switch (result) {
            case NO_PROBLEM:
                if (mRunNovelWhenFinishedPath != null) {
                    startONScripter(mRunNovelWhenFinishedPath, true);
                }
                break;
            case ASSET_NOT_FOUND:
                showError(R.string.message_asset_not_found);
                break;
            case PRIVATE_PATH_NOT_FOUND:
                showError(R.string.message_private_dir_not_found);
                break;
            case WRITING_ERROR:
                showError(R.string.message_internal_write_error);
                break;
            }
            mCopyTask = null;
        }
    }
}
