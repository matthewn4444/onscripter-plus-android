package com.onscripter.plus;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Comparator;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class FileSystemAdapter extends ArrayAdapter<String> {

    private File mCurrentDirectory;
    private boolean mShowHidden;
    private boolean mOnlyShowFolders;
    private boolean mShowBackItem;
    private File[] mFileList;
    private final FileSort mFileSorter = new FileSort();
    private TextView mBindedPath;

    static class FileSort implements Comparator<File>{
        @Override
        public int compare(File src, File target){
            return src.getName().compareTo(target.getName());
        }
    }

    public FileSystemAdapter(Context context, File startDirectory) throws FileNotFoundException {
        this(context, startDirectory, false);
    }

    public FileSystemAdapter(Context context, File startDirectory, boolean showBackButton) throws FileNotFoundException {
        this(context, startDirectory, showBackButton, false);
    }

    public FileSystemAdapter(Context context, File startDirectory, boolean showBackButton, boolean onlyShowFolders) throws FileNotFoundException {
        this(context, startDirectory, showBackButton, onlyShowFolders, false);
    }

    public FileSystemAdapter(Context context, File startDirectory, boolean showBackButton, boolean onlyShowFolders, boolean showHiddenFolders) throws FileNotFoundException {
        super(context, android.R.layout.simple_list_item_1);
        if (!startDirectory.exists() || !startDirectory.isDirectory()) {
            throw new FileNotFoundException("Cannot find directory.");
        }
        mShowHidden = showHiddenFolders;
        mOnlyShowFolders = onlyShowFolders;
        mShowBackItem = showBackButton;
        mCurrentDirectory = startDirectory;
        refresh();
    }

    public void bindPathToTextView(TextView textView) {
        mBindedPath = textView;
        if (mBindedPath != null) {
            mBindedPath.setText(mCurrentDirectory.getPath());
        }
    }

    public File getCurrentDirectory() {
        return mCurrentDirectory;
    }

    public void onlyShowFolders(boolean flag) {
        if (mOnlyShowFolders != flag) {
            mOnlyShowFolders = flag;
            refresh();
        }
    }

    public boolean isOnlyShowingFolders() {
        return mOnlyShowFolders;
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

    public void showBackListItem(boolean flag) {
        if (mShowBackItem != flag) {
            mShowBackItem = flag;
            refresh();
        }
    }

    public boolean isBackListItemVisible() {
        return mShowBackItem;
    }

    public File getFile(int index) {
        if (mShowBackItem) {
            if (index == 0) {   // Back button
                return null;
            }
            index--;
        }
        return mFileList[index];
    }

    public boolean refresh() {
        File[] files = mCurrentDirectory.listFiles(new FileFilter() {      // TODO split this into file and folder
            @Override
            public boolean accept(File file) {
                return (!mOnlyShowFolders && (!file.isHidden() || file.isHidden()
                        && mShowHidden)) || file.isDirectory();
            }
        });
        if (files == null) {
            Toast.makeText(getContext(), "Unable to open folder because of permissions", Toast.LENGTH_SHORT).show();
            return false;
        }
        clear();
        mFileList = files;
        if (mShowBackItem) {
            LauncherActivity.log("dsdsds");
            add(getContext().getString(R.string.directory_back));
        }
        Arrays.sort(mFileList, mFileSorter);
        for (int i = 0; i < mFileList.length; i++) {
            add(mFileList[i].getName());
        }
        if (mBindedPath != null) {
            mBindedPath.setText(mCurrentDirectory.getPath());
        }
        notifyDataSetChanged();
        return true;
    }

    public void setChildAsCurrent(int index) {
        if (mShowBackItem) {
            if (index == 0) {
                moveUp();
                return;
            }
            index--;
        }
        setCurrentDirectory(mFileList[index]);
    }

    public boolean setCurrentDirectory(File currentDirectory) {
        if (currentDirectory.exists() && currentDirectory.isDirectory()) {
            File old = mCurrentDirectory;
            mCurrentDirectory = currentDirectory;
            if (refresh()) {
                return true;
            } else {
                mCurrentDirectory = old;
            }
        }
        return false;
    }

    public boolean moveUp() {
        if (mCurrentDirectory.getParent() == null) {
            return false;
        }
        File old = mCurrentDirectory;
        mCurrentDirectory = mCurrentDirectory.getParentFile();
        if (refresh()) {
            return true;
        }
        mCurrentDirectory = old;
        return false;
    }
}
