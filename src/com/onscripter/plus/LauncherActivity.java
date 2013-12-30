package com.onscripter.plus;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
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

    public static String gCurrentDirectoryPath;

    private File mCurrentDirectory = null;      // TODO this is not needed
    private ListView listView = null;
    private AlertDialog.Builder alertDialogBuilder = null;      // TODO make this smarter
    private FileSystemAdapter mAdapter = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        gCurrentDirectoryPath = Environment.getExternalStorageDirectory() + "/Android/data/" + getApplicationContext().getPackageName();
        alertDialogBuilder = new AlertDialog.Builder(this);

        gCurrentDirectoryPath = Environment.getExternalStorageDirectory() + "/ons";

        runLauncher();
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
            goToActivity(Settings.class);
            break;
        default:
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    protected void goToActivity(Class<?> cls) {
        Intent i = new Intent(this, cls);
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

    private void showErrorDialog(String mes)
    {
        alertDialogBuilder.setTitle("Error");
        alertDialogBuilder.setMessage(mes);
        alertDialogBuilder.setPositiveButton("Quit", new DialogInterface.OnClickListener(){
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                finish();
            }
        });
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    private void runLauncher() {
        mCurrentDirectory = new File(gCurrentDirectoryPath);
        if (mCurrentDirectory.exists() == false){
            gCurrentDirectoryPath = Environment.getExternalStorageDirectory().getPath();
            mCurrentDirectory = new File(gCurrentDirectoryPath);

            if (mCurrentDirectory.exists() == false) {
                showErrorDialog("Could not find SD card.");
            }
        }

        listView = new ListView(this);

        // Set up the adapter
        try {
            mAdapter = new FileSystemAdapter(this, mCurrentDirectory);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        listView.setAdapter(mAdapter);
        listView.setOnItemClickListener(this);


        setContentView(listView);
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
        // TODO simplify this function
        File oldCurrentDir = mCurrentDirectory;

        // Set new path for the file
        mCurrentDirectory = mAdapter.getFile(position);
        gCurrentDirectoryPath = mCurrentDirectory.getPath();

        // Check to see if this folder contains a visual novel
        File[] mDirectoryFiles = mCurrentDirectory.listFiles(new FileFilter() {
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
            mCurrentDirectory = oldCurrentDir;
            return;
        }

        if (mDirectoryFiles.length == 0){       // It is a regular folder, so open it
            mAdapter.setChildAsCurrent(position);
        } else {
            // Check to see if it has a font file inside
            mDirectoryFiles = mCurrentDirectory.listFiles(new FileFilter() {
                @Override
                public boolean accept(File file) {
                    return (file.isFile() &&
                            (file.getName().equals("default.ttf")));
                }
            });

            // Missing font
            if (mDirectoryFiles.length == 0){
                alertDialogBuilder.setTitle(getString(R.string.app_name));
                alertDialogBuilder.setMessage("default.ttf is missing.");
                alertDialogBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener(){
                        @Override
                        public void onClick(DialogInterface dialog, int whichButton) {
                            setResult(RESULT_OK);
                        }
                    });
                AlertDialog alertDialog = alertDialogBuilder.create();
                alertDialog.show();

                mCurrentDirectory = oldCurrentDir;
            }
            else{
                goToActivity(ONScripter.class);     // TODO change this to setActivityForResult and return the current directory
            }
        }
    }
}
