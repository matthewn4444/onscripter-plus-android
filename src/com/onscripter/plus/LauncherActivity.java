package com.onscripter.plus;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;

import android.app.AlertDialog;
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

public class LauncherActivity extends SherlockActivity implements AdapterView.OnItemClickListener
{
    private static final int REQUEST_CODE_SETTINGS = 1;

    private AlertDialog.Builder mDialog = null;
    private FileSystemAdapter mAdapter = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDialog = new AlertDialog.Builder(this);

        // Setup default directory location when null
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
        File directory = new File(path);
        if (!directory.exists()){
            directory = new File(Environment.getExternalStorageDirectory().getPath());

            if (!directory.exists()) {
                showError(getString(R.string.message_cannot_find_internal_storage));
                return;
            }
        }

        // Set up the listView and the adapter
        try {
            mAdapter = new FileSystemAdapter(this, directory);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        final ListView listView = new ListView(this);
        mAdapter.onlyShowFolders(true);
        listView.setAdapter(mAdapter);
        listView.setOnItemClickListener(this);
        setContentView(listView);
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
            if (mAdapter.getCurrentDirectory().equals(Environment.getExternalStorageDirectory())) {     // TODO change this to the "home folder"
                super.onBackPressed();
            } else {
                mAdapter.moveUp();
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        File currentDir = mAdapter.getFile(position);

        // Set new path for the file
        currentDir = mAdapter.getFile(position);

        // Check to see if this folder contains a visual novel
        File[] mDirectoryFiles = currentDir.listFiles(new FileFilter() {
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

        // Unable to read the folder
        if (mDirectoryFiles == null) {
            Toast.makeText(LauncherActivity.this, "Unable to open folder because of permissions", Toast.LENGTH_SHORT).show();
            return;
        }

        if (mDirectoryFiles.length == 0){       // It is a regular folder, so open it
            mAdapter.setChildAsCurrent(position);
        } else {
            // Check to see if it has a font file inside
            mDirectoryFiles = currentDir.listFiles(new FileFilter() {
                @Override
                public boolean accept(File file) {
                    return (file.isFile() &&
                            (file.getName().equals("default.ttf")));
                }
            });

            // Missing font
            if (mDirectoryFiles.length == 0){
                alert("default.ttf is missing.");
            } else{
                Bundle b = new Bundle();
                b.putString(ONScripter.CURRENT_DIRECTORY_EXTRA, currentDir.getPath());
                goToActivity(ONScripter.class, b);
            }
        }
    }
}
