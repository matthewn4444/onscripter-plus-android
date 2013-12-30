package com.onscripter.plus;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Comparator;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class FileSystemAdapter extends ArrayAdapter<String> {

    private File mCurrentDirectory;
    private boolean mShowHidden;
    private boolean onlyShowFolders;
    private File[] mFileList;
    private final FileSort fileSorter = new FileSort();
    private TextView mBindedPath;

    static class FileSort implements Comparator<File>{
        @Override
        public int compare(File src, File target){
            return src.getName().compareTo(target.getName());
        }
    }

    public FileSystemAdapter(Context context, File startDirectory) throws FileNotFoundException {
        super(context, android.R.layout.simple_list_item_1);
        if (!startDirectory.exists() || !startDirectory.isDirectory()) {
            throw new FileNotFoundException("Cannot find directory.");
        }
        mCurrentDirectory = startDirectory;
        refresh();
    }

    public void bindPathToTextView(TextView textView) {
        mBindedPath = textView;
    }

    public File getCurrentDirectory() {
        return mCurrentDirectory;
    }

    public void onlyShowFolders(boolean flag) {
        if (onlyShowFolders != flag) {
            onlyShowFolders = flag;
            refresh();
        }
    }

    public boolean isOnlyShowingFolders() {
        return onlyShowFolders;
    }

    public void showHiddenFiles(boolean flag) {
        if (mShowHidden != flag) {
            mShowHidden = flag;
            refresh();
        }
    }

    public boolean isShowingHiddenFiles() {
        return mShowHidden;
    }

    public File getFile(int index) {
        return mFileList[index];
    }

    public void refresh() {
        clear();
        mFileList = mCurrentDirectory.listFiles(new FileFilter() {      // TODO split this into file and folder
            @Override
            public boolean accept(File file) {
                return (!onlyShowFolders && (!file.isHidden() || file.isHidden()
                        && mShowHidden)) || file.isDirectory();
            }
        });
        Arrays.sort(mFileList, fileSorter);
        for (int i = 0; i < mFileList.length; i++) {
            add(mFileList[i].getName());
        }
        if (mBindedPath != null) {
            mBindedPath.setText(mCurrentDirectory.getPath());
        }
        notifyDataSetChanged();
    }

    public void setChildAsCurrent(int index) {
        setCurrentDirectory(mFileList[index]);
    }

    public boolean setCurrentDirectory(File currentDirectory) {
        if (currentDirectory.exists() && currentDirectory.isDirectory()) {
            mCurrentDirectory = currentDirectory;
            refresh();
            return true;
        }
        return false;
    }

    public boolean moveUp() {
        if (mCurrentDirectory.getParent() == null) {
            return false;
        }
        mCurrentDirectory = mCurrentDirectory.getParentFile();
        refresh();
        return true;
    }
}
