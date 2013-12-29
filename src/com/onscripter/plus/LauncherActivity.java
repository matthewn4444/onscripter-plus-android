package com.onscripter.plus;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Comparator;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

public class LauncherActivity extends SherlockActivity implements AdapterView.OnItemClickListener
{

    public static String gCurrentDirectoryPath;

 // Launcher contributed by katane-san
    private File mCurrentDirectory = null;
    private File mOldCurrentDirectory = null;
    private File [] mDirectoryFiles = null;
    private ListView listView = null;
    private AlertDialog.Builder alertDialogBuilder = null;

    static class FileSort implements Comparator<File>{
        @Override
        public int compare(File src, File target){
            return src.getName().compareTo(target.getName());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LauncherActivity.gCurrentDirectoryPath = Environment.getExternalStorageDirectory() + "/Android/data/" + getApplicationContext().getPackageName();
        alertDialogBuilder = new AlertDialog.Builder(this);

        LauncherActivity.gCurrentDirectoryPath = Environment.getExternalStorageDirectory() + "/ons";

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

    private void setupDirectorySelector() {
        mDirectoryFiles = mCurrentDirectory.listFiles(new FileFilter() {
                @Override
                public boolean accept(File file) {
                    return (!file.isHidden() && file.isDirectory());
                }
            });

        Arrays.sort(mDirectoryFiles, new FileSort());

        int length = mDirectoryFiles.length;
        if (mCurrentDirectory.getParent() != null) {
            length++;
        }
        String [] names = new String[length];

        int j=0;
        if (mCurrentDirectory.getParent() != null) {
            names[j++] = "..";
        }
        for (int i=0 ; i<mDirectoryFiles.length ; i++){
            names[j++] = mDirectoryFiles[i].getName();
        }

        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, names);

        listView.setAdapter(arrayAdapter);
        listView.setOnItemClickListener(this);
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

        setupDirectorySelector();

        setContentView(listView);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        TextView textView = (TextView)v;
        mOldCurrentDirectory = mCurrentDirectory;

        if (textView.getText().equals("..")){
            mCurrentDirectory = new File(mCurrentDirectory.getParent());
            gCurrentDirectoryPath = mCurrentDirectory.getPath();
        } else {
            if (mCurrentDirectory.getParent() != null) {
                position--;
            }
            gCurrentDirectoryPath = mDirectoryFiles[position].getPath();
            mCurrentDirectory = new File(gCurrentDirectoryPath);
        }

        mDirectoryFiles = mCurrentDirectory.listFiles(new FileFilter() {
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

        if (mDirectoryFiles.length == 0){
            setupDirectorySelector();
        }
        else{
            mDirectoryFiles = mCurrentDirectory.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File file) {
                        return (file.isFile() &&
                                (file.getName().equals("default.ttf")));
                    }
                });

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

                mCurrentDirectory = mOldCurrentDirectory;
                setupDirectorySelector();
            }
            else{
                goToActivity(ONScripter.class);
            }
        }
    }

}
