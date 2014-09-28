package com.onscripter.plus;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.onscripter.plus.FileSystemAdapter.FileListItem;

public class FileSystemAdapter extends ViewAdapterBase<FileListItem> {

    private File mCurrentDirectory;
    private boolean mShowHidden;
    private boolean mOnlyShowFolders;
    private boolean mShowBackItem;
    private FileListItem[] mFileList;
    private final FileSort mFileSorter = new FileSort();
    private TextView mBindedPath;
    private CustomFileTypeParser mTypeParser;
    private final ArrayList<File> mLowerBoundFiles;

    private static String BackString;
    private static FileListItem BackFileListItem;
    private static Drawable IconBackDrawable;
    private static Drawable IconFileDrawable;
    private static Drawable IconFolderDrawable;

    static final public int[] LAYOUT_IDS = {
        R.id.icon,
        R.id.filename
    };

    public static enum LIST_ITEM_TYPE {
        FILE, FOLDER, BACK
    };

    interface CustomFileTypeParser {
        public LIST_ITEM_TYPE onFileTypeParse(File file);
    }

    static class FileListItem {
        private final LIST_ITEM_TYPE mType;
        private final String mName;
        private final File mFile;

        public FileListItem(LIST_ITEM_TYPE type, String name) {
            mType = type;
            mName = name;
            mFile = null;
        }

        public FileListItem(LIST_ITEM_TYPE type, File file) {
            mType = type;
            mName = file.getName();
            mFile = file;
        }

        public boolean isFile() {
            return mType == LIST_ITEM_TYPE.FILE;
        }
        public boolean isFolder() {
            return mType == LIST_ITEM_TYPE.FOLDER;
        }
        public boolean isBackItem() {
            return mType == LIST_ITEM_TYPE.BACK;
        }
        public LIST_ITEM_TYPE getType() {
            return mType;
        }
        public String getName() {
            return mName;
        }
        public File getFile() {
            return mFile;
        }
    }

    static class FileSort implements Comparator<FileListItem>{
        @Override
        public int compare(FileListItem src, FileListItem target){
            return src.getName().compareTo(target.getName());
        }
    }

    public FileSystemAdapter(Context context, File startDirectory) throws FileNotFoundException {
        this(context, startDirectory, false);
    }

    public FileSystemAdapter(Context context, File startDirectory, boolean showBackButton)
            throws FileNotFoundException {
        this(context, startDirectory, showBackButton, false);
    }

    public FileSystemAdapter(Context context, File startDirectory, boolean showBackButton,
            boolean onlyShowFolders) throws FileNotFoundException {
        this(context, startDirectory, showBackButton, onlyShowFolders, false);
    }

    public FileSystemAdapter(Context context, File startDirectory, boolean showBackButton,
            boolean onlyShowFolders, boolean showHiddenFolders) throws FileNotFoundException {
        this(context, startDirectory, showBackButton, onlyShowFolders, false, null);
    }
    public FileSystemAdapter(Context context, File startDirectory, boolean showBackButton,
            boolean onlyShowFolders, boolean showHiddenFolders, CustomFileTypeParser parser) throws FileNotFoundException {
        super((Activity)context, R.layout.file_system_layout, LAYOUT_IDS, new ArrayList<FileListItem>());
        if (!startDirectory.exists() || !startDirectory.isDirectory()) {
            throw new FileNotFoundException("Cannot find directory.");
        }
        mLowerBoundFiles = new ArrayList<File>();
        mShowHidden = showHiddenFolders;
        mOnlyShowFolders = onlyShowFolders;
        mShowBackItem = showBackButton;
        mCurrentDirectory = startDirectory;
        if (BackString == null) {
            BackString = context.getString(R.string.directory_back);
            BackFileListItem = new FileListItem(LIST_ITEM_TYPE.BACK, BackString);
        }

        int[] attrs = new int[] { R.attr.folderUpImage, R.attr.fileImage, R.attr.folderImage };
        TypedArray ta = context.obtainStyledAttributes(attrs);
        IconBackDrawable = ta.getDrawable(0);
        IconFileDrawable = ta.getDrawable(1);
        IconFolderDrawable = ta.getDrawable(2);
        ta.recycle();

        mTypeParser = parser;
        setCurrentDirectory(mCurrentDirectory);
    }

    public void bindPathToTextView(TextView textView) {
        mBindedPath = textView;
        if (mBindedPath != null) {
            mBindedPath.setText(mCurrentDirectory.getPath());
        }
    }

    public String getCurrentDirectoryPath() {
        return mCurrentDirectory.getPath();
    }

    public void setCustomFileTypeParser(CustomFileTypeParser parser) {
        mTypeParser = parser;
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
            if (mShowBackItem) {
                insert(BackFileListItem, 0);
            } else {
                remove(BackFileListItem);
            }
        }
    }

    public boolean isBackListItemVisible() {
        return mShowBackItem;
    }

    /**
     * Gets the file from the listview that the adapter is
     * attached to. It will count 0 as the back button if
     * it exists.
     * @param index
     * @return
     */
    public File getFile(int index) {
        if (isBackButtonShown()) {
            if (index == 0) {   // Back button
                return null;
            }
            index--;
        }
        return mFileList[index].getFile();
    }

    /**
     * Gets the file that is in the list independent to whether
     * there is a back button shown in the listview.
     * @param index
     * @return
     */
    public File getFileFromList(int index) {
        return mFileList[index].getFile();
    }

    public int getSizeOfFileList() {
        return getCount() - (isBackButtonShown() ? 1 : 0);
    }

    public void refresh() {
        clear();
        if (mShowBackItem && !isDirectoryAtLowerBound()) {
            add(BackFileListItem);
        }
        Arrays.sort(mFileList, mFileSorter);
        addAll(mFileList);
        notifyDataSetChanged();
    }

    public void setChildAsCurrent(int index) {
        if (isBackButtonShown()) {
            if (index == 0) {
                moveUp();
                return;
            }
            index--;
        }
        if (index < mFileList.length) {
            setCurrentDirectory(mFileList[index].getFile());
        }
    }

    public boolean setCurrentDirectory(File currentDirectory) {
        if (currentDirectory.exists() && currentDirectory.isDirectory()) {
            File old = mCurrentDirectory;
            mCurrentDirectory = currentDirectory;

            // List the files and refresh interface
            File[] files = mCurrentDirectory.listFiles(new FileFilter() {
                @Override
                public boolean accept(File file) {
                    return (!mOnlyShowFolders && (!file.isHidden() || file.isHidden()
                            && mShowHidden)) || file.isDirectory();
                }
            });
            if (files == null) {
                Toast.makeText(getContext(), "Unable to open folder because of permissions", Toast.LENGTH_SHORT).show();
                mCurrentDirectory = old;
                return false;
            }

            // Build file list and set their types
            FileListItem[] fileList = new FileListItem[files.length];
            for (int i = 0; i < files.length; i++) {
                if (mTypeParser != null) {
                    LIST_ITEM_TYPE type = mTypeParser.onFileTypeParse(files[i]);
                    fileList[i] = new FileListItem(type, files[i]);
                } else {
                    if (files[i].isDirectory()) {
                        fileList[i] = new FileListItem(LIST_ITEM_TYPE.FOLDER, files[i]);
                    } else {
                        fileList[i] = new FileListItem(LIST_ITEM_TYPE.FILE, files[i]);
                    }
                }
            }
            mFileList = fileList;
            refresh();
            if (mBindedPath != null) {
                mBindedPath.setText(mCurrentDirectory.getPath());
            }
            return true;
        }
        return false;
    }

    public boolean moveUp() {
        if (mCurrentDirectory.getParent() == null) {
            return false;
        }
        return setCurrentDirectory(mCurrentDirectory.getParentFile());
    }

    public boolean fileExists(String name) {
        return new File(mCurrentDirectory + "/" + name).exists();

    }

    public boolean makeDirectory(String name) {
        boolean success =  new File(mCurrentDirectory + "/" + name).mkdir();
        if (success) {
            setCurrentDirectory(mCurrentDirectory);
        }
        return success;
    }

    public void addLowerBoundFile(File path) {
        if (path != null && path.exists()) {
            mLowerBoundFiles.add(path);
            refresh();
        }
    }

    public boolean isDirectoryAtLowerBound() {
        if (mLowerBoundFiles != null) {
            for (int i = 0; i < mLowerBoundFiles.size(); i++) {
                if (mLowerBoundFiles.get(i).getPath().equals(mCurrentDirectory.getPath())) {
                    return true;
                }
            }
        }
        return false;
    }

    protected boolean isBackButtonShown() {
        return mShowBackItem && !isDirectoryAtLowerBound();
    }

    @Override
    protected void setWidgetValues(int position, FileListItem item, View[] elements,
            View layout) {
        Drawable drawable = null;
        switch(item.getType()) {
        case FILE:
            drawable = IconFileDrawable;
            break;
        case BACK:
            drawable = IconBackDrawable;
            break;
        case FOLDER:
            drawable = IconFolderDrawable;
            break;
        }
        ((ImageView)elements[0]).setImageDrawable(drawable);
        ((TextView)elements[1]).setText(item.getName());
    }
}
