package com.onscripter.plus;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Comparator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class LauncherActivity extends Activity implements AdapterView.OnItemClickListener
{

    public static String gCurrentDirectoryPath;
    public static boolean gRenderFontOutline;
    public static CheckBox checkRFO = null;

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

     // fullscreen mode
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                             WindowManager.LayoutParams.FLAG_FULLSCREEN);

        LauncherActivity.gCurrentDirectoryPath = Environment.getExternalStorageDirectory() + "/Android/data/" + getApplicationContext().getPackageName();
        alertDialogBuilder = new AlertDialog.Builder(this);
        SharedPreferences sp = getSharedPreferences("pref", MODE_PRIVATE);
        LauncherActivity.gRenderFontOutline = sp.getBoolean("render_font_outline", getResources().getBoolean(R.bool.render_font_outline));

        LauncherActivity.gCurrentDirectoryPath = Environment.getExternalStorageDirectory() + "/ons";

        runLauncher();
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

        LinearLayout layoutH = new LinearLayout(this);

        checkRFO = new CheckBox(this);
        checkRFO.setText("Render Font Outline");
        checkRFO.setBackgroundColor(Color.rgb(244,244,255));
        checkRFO.setTextColor(Color.BLACK);
        checkRFO.setChecked(gRenderFontOutline);
        checkRFO.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked){
                    Editor e = getSharedPreferences("pref", MODE_PRIVATE).edit();
                    e.putBoolean("render_font_outline", isChecked);
                    e.commit();
                }
            });

        layoutH.addView(checkRFO, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT, 1.0f));

        listView.addHeaderView(layoutH, null, false);

        setupDirectorySelector();

        setContentView(listView);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        position--; // for header

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
                gRenderFontOutline = checkRFO.isChecked();
//                runSDLApp();
                Intent i = new Intent(this, ONScripter.class);
                startActivity(i);
            }
        }
    }

}
