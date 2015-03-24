package com.onscripter.plus;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.StatFs;
import android.text.Html;
import android.text.Spanned;
import android.text.format.Formatter;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckedTextView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bugsense.trace.BugSenseHandler;

public final class CopyFilesDialogTask {

    public static class CopyFileInfo {
        public CopyFileInfo(String src, String dst) {
            source = src;
            destination = dst;
        }
        public String source;
        public String destination;
    }

    public static interface CopyFilesDialogListener {
        public void onCopyCompleted(Result result);
    }

    public static enum Result { SUCCESS, CANCELLED, COPY_ERROR, NO_SPACE_ERROR };

    private final Context mCtx;
    private final CopyFilesDialogListener mListener;
    private CopyFileInfo[] mInfo;
    private final boolean mAllowUserChoice;
    private boolean mIsRunning;
    private boolean mSrcFromInternalStorage;        // This limits all src to come from same storage and same for dst
    private int mStoragePathLength = 0;
    private final FileFilter mFileFilter;
    private final FileFilter mDirectoryFilter;
    private final FileFilter mOverwriteFilter;

    public CopyFilesDialogTask(Context ctx, CopyFilesDialogListener listener) {
        this(ctx, listener, null, null, null);
    }

    public CopyFilesDialogTask(Context ctx, CopyFilesDialogListener listener,
            FileFilter fileFilter, FileFilter directoryFilter, FileFilter overwriteFilter) {
        this(ctx, listener, fileFilter, directoryFilter, overwriteFilter, true);
    }

    public CopyFilesDialogTask(Context ctx, CopyFilesDialogListener listener,
            FileFilter fileFilter, FileFilter directoryFilter, FileFilter overwriteFilter, boolean allowUserChoice) {
        mCtx = ctx;
        mListener = listener;
        mFileFilter = fileFilter;
        mDirectoryFilter = directoryFilter;
        mOverwriteFilter = overwriteFilter;
        mAllowUserChoice = allowUserChoice;
        mIsRunning = false;
    }

    public void executeCopy(CopyFileInfo[] info) {
        if (info.length > 0) {
            String internalStorage = Environment2.getInternalStorageDirectory().getAbsolutePath();
            String sourcePath = new File(info[0].source).getAbsolutePath();
            mSrcFromInternalStorage = sourcePath.startsWith(internalStorage);
            mStoragePathLength = mSrcFromInternalStorage ? internalStorage.length() :
                Environment2.getExternalSDCardDirectory().getPath().length();
            synchronized (this) {
                if (mIsRunning) {
                    return;
                } else {
                    mIsRunning = true;
                    mInfo = info;
                }
            }
            new InternalFileSpaceDialogTask().execute();
        } else if (mListener != null) {
            mListener.onCopyCompleted(Result.SUCCESS);
        }
    }

    private void scanFinishedUnsuccessfully(Result result) {
        synchronized (this) {
            mIsRunning = false;
        }
        if (mListener != null) {
            mListener.onCopyCompleted(result);
        }
    }

    private void scanFinished(long totalBytes) {
        new InternalCopyDialogTask(totalBytes).execute();
    }

    private void copyFinished(Result result) {
        synchronized (this) {
            mInfo = null;
            mIsRunning = false;
        }
        if (mListener != null) {
            mListener.onCopyCompleted(result);
        }
    }

    private class InternalFileSpaceDialogTask extends ProgressDialogAsyncTask<Void, Void, Long> {

        private final ArrayList<Pair<Spanned, Long>> mListing;
        private long mRemainingInternalBytes;
        private ListView mFileListView;
        private TextView mRemainingText;
        private long mCurrentSumBytes;
        private boolean mCurrentSetHasOverwrite;
        private AlertDialog mChooseDialog;

        public InternalFileSpaceDialogTask() {
            super(mCtx, mCtx.getString(R.string.dialog_scan_files_title), true);
            mListing = new ArrayList<Pair<Spanned, Long>>(mInfo.length);
            mCurrentSumBytes = 0;
        }

        /**
         * Recursively scans each file in given folder (or file) and returns the
         * total sum of their file size. Maybe slow with many files
         * @param source
         * @return
         */
        private long scanTotalBytes(File source, File destination) {
            long totalBytes = 0;
            if (source.isFile()) {
                if (mFileFilter != null && !mFileFilter.accept(source)) {
                    return 0;
                }
                // Because we overwrite the destination, if exists, then we dont need extra space for it
                if (destination.exists()) {
                    totalBytes -= destination.length();
                    mCurrentSetHasOverwrite = true;
                }
                totalBytes += source.length();
            } else {
                if (mDirectoryFilter != null && !mDirectoryFilter.accept(source)) {
                    return 0;
                }
                File[] children = source.listFiles();
                if (children != null) {
                    for (int i = 0; i < children.length; i++) {
                        File dstFile = new File(destination + "/" + children[i].getName());
                        totalBytes += scanTotalBytes(children[i], dstFile);
                    }
                }
            }
            return totalBytes;
        }

        private void updateAndRecalculateList() {
            mRemainingText.setText(Formatter.formatFileSize(mCtx, mRemainingInternalBytes - mCurrentSumBytes));
            boolean atLeastOneSelected = false;

            for (int i = 0; i < mFileListView.getChildCount(); i++) {
                CheckedTextView tv = (CheckedTextView)mFileListView.getChildAt(i);
                if (!tv.isChecked()) {
                    if (mRemainingInternalBytes - mCurrentSumBytes - mListing.get(i).second < 0) {
                        tv.setEnabled(false);
                    } else {
                        tv.setEnabled(true);
                    }
                } else {
                    atLeastOneSelected = true;
                }
            }
            mChooseDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(atLeastOneSelected);
        }

        @SuppressWarnings("deprecation")
        @Override
        protected Long doInBackground(Void... params) {
            long bytes = 0;

            // Scan files
            for (int i = 0; i < mInfo.length; i++) {
                if (isCancelled()) {
                    return 0L;
                }
                mCurrentSetHasOverwrite = false;
                long b = scanTotalBytes(new File(mInfo[i].source), new File(mInfo[i].destination));
                bytes += b;
                if (b < 0) {
                    b = 0;
                }
                String formattedSize = Formatter.formatFileSize(mCtx, b);
                if (mCurrentSetHasOverwrite) {
                    formattedSize += " <font color='red'>" + mCtx.getString(R.string.dialog_override_files) + "</font>";
                }
                Spanned listing = Html.fromHtml("<b>" + mInfo[i].source.substring(mStoragePathLength) + "</b><br><small>+" + formattedSize + "</small>");
                mListing.add(new Pair<Spanned, Long>(listing, b));
            }

            // Read remaining memory on internal storage
            File path = mSrcFromInternalStorage ? Environment2.getExternalSDCardDirectory()
                    : Environment2.getInternalStorageDirectory();
            StatFs stat = new StatFs(path.getPath());
            mRemainingInternalBytes = (long)stat.getBlockSize() * (long)stat.getAvailableBlocks();
            return bytes;
        }


        @Override
        protected void onPostExecute(Long totalBytes) {
            super.onPostExecute(totalBytes);

            // Show the choose dialog and tell users if any files are overwritten
            if (mInfo.length == 1 && mRemainingInternalBytes < mListing.get(0).second) {
                // Only copying one file and there is no more space, can't copy then
                scanFinishedUnsuccessfully(Result.NO_SPACE_ERROR);
            } else if (mAllowUserChoice) {
                // Inflate the dialog
                LayoutInflater inflater = (LayoutInflater) mCtx.getSystemService( Context.LAYOUT_INFLATER_SERVICE);
                LinearLayout view = (LinearLayout) inflater.inflate(R.layout.selective_file_dialog, null);

                // Get the elements of the list, setup the listview and events
                mRemainingText = (TextView)view.findViewById(R.id.spaceRemaining);
                mFileListView = (ListView)view.findViewById(R.id.fileList);
                mFileListView.setAdapter(new ViewAdapterBase<Pair<Spanned, Long>>(mCtx,
                        R.layout.selective_file_item, new int[]{android.R.id.text1}, mListing) {
                    @Override
                    protected void setWidgetValues(int position, Pair<Spanned, Long> item,
                            View[] elements, View layout) {
                        ((CheckedTextView)elements[0]).setText(item.first);
                    }
                });
                mFileListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        // change the checkbox state
                        CheckedTextView checkedTextView = ((CheckedTextView)view);
                        if (checkedTextView.isEnabled()) {
                            checkedTextView.toggle();

                            // Update the UI and count
                            if (checkedTextView.isChecked()) {
                                mCurrentSumBytes += mListing.get(position).second;
                            } else {
                                mCurrentSumBytes -= mListing.get(position).second;
                            }
                            updateAndRecalculateList();
                        }
                    }
                });

                // Build the dialog and attach the layout
                mChooseDialog = new AlertDialog.Builder(mCtx)
                    .setTitle(R.string.dialog_select_files_copy_title)
                    .setView(view)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            int j = 0;
                            ArrayList<CopyFileInfo> info = new ArrayList<CopyFileInfo>(mFileListView.getChildCount());
                            for (int i = 0; i < mFileListView.getChildCount(); i++) {
                                if (((CheckedTextView)mFileListView.getChildAt(i)).isChecked()) {
                                    info.add(mInfo[i]);
                                }
                            }
                            mInfo = info.toArray(new CopyFileInfo[info.size()]);
                            scanFinished(mCurrentSumBytes);
                        }
                    })
                    .setNeutralButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Cancel out
                            scanFinishedUnsuccessfully(Result.CANCELLED);
                        }
                    })
                    .create();

                mChooseDialog.setOnShowListener(new OnShowListener() {
                    @Override
                    public void onShow(DialogInterface dialog) {
                        updateAndRecalculateList();
                    }
                });
                mChooseDialog.show();
            } else {
                scanFinished(totalBytes);
            }
        }
    }

    /**
     * This is the task that creates the copy dialog and copies the files from
     * source to destination
     * @author CHaSEdBYmAnYcrAZy
     *
     */
    private class InternalCopyDialogTask extends AsyncTask<Void, Void, Result> {

        private Dialog mDialog;
        private boolean mShowDialogTimeout;
        private final LinearLayout mLayout;
        private final TextView mCurrentFileNameText;
        private final TextView mCurrentFileSizeText;
        private final TextView mCurrentPercentText;
        private final TextView mOverallPercentText;
        private final ProgressBar mCurrentPercentProgress;
        private final ProgressBar mOverallPercentProgress;

        // Progress data
        private final long mTotalBytesCopying;
        private String mCurrentFile;
        private long mCurrentFileSize;
        private long mCurrentFileTotalSize;
        private int mCurrentFilePercentage;

        public InternalCopyDialogTask(long totalBytes) {
            mTotalBytesCopying = totalBytes;

            // Inflate the dialog
            LayoutInflater inflater = (LayoutInflater) mCtx.getSystemService( Context.LAYOUT_INFLATER_SERVICE);
            mLayout = (LinearLayout) inflater.inflate(R.layout.copy_file_dialog, null);

            mCurrentFileNameText = (TextView) mLayout.findViewById(R.id.filename);
            mCurrentFileSizeText = (TextView) mLayout.findViewById(R.id.filesize);
            mCurrentPercentText = (TextView) mLayout.findViewById(R.id.filePercent);
            mOverallPercentText = (TextView) mLayout.findViewById(R.id.overallPercent);
            mCurrentPercentProgress = (ProgressBar) mLayout.findViewById(R.id.fileProgressbar);
            mOverallPercentProgress = (ProgressBar) mLayout.findViewById(R.id.overallProgressbar);
        }

        private void show() {
            if (mDialog == null) {
                mDialog = new AlertDialog.Builder(mCtx)
                    .setCancelable(false)
                    .setTitle(R.string.dialog_copy_files_title)
                    .setView(mLayout)
                    .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            cancel(true);
                        }
                    })
                    .create();
            }
            mShowDialogTimeout = true;

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mShowDialogTimeout) {
                        mDialog.show();
                    }
                }
            }, 250);
        }

        /**
         * Copies a file or folder recursively. Please make sure the source and destination
         * are both either folders or files.
         * @param source
         * @param destination
         * @return true if success
         */
        private boolean copy(File source, File destination) {
            return source.isFile() ? copyFile(source, destination) : copyFolder(source, destination);
        }

        private boolean copyFolder(File source, File destination) {
            // Skip folder if not accepted
            if (mDirectoryFilter != null && !mDirectoryFilter.accept(source)) {
                return true;
            }
            File[] children = source.listFiles();
            if (!destination.exists() && !destination.mkdir()) {
                // Failed to make a new directory
                Log.e("CopyFilesDialogTask", "Failed to make directory '" + destination.getAbsolutePath() + "'");
                return false;
            }
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    if (isCancelled()) {
                        return false;
                    }
                    File dstFile = new File(destination + "/" + children[i].getName());

                    // Failed to copy, kill the copying routine
                    if (!copy(children[i], dstFile)) {
                        return false;
                    }
                }
            }
            return true;
        }

        private boolean copyFile(File source, File destination) {
            // Skip file if not accepted
            if (mFileFilter != null && !mFileFilter.accept(source)) {
                return true;
            }

            // If destination file exists and is same size and not allowed to overwrite, then we skip copying it
            if (destination.exists()) {
                if (destination.length() == source.length()) {
                    if (mOverwriteFilter == null || (mOverwriteFilter != null && !mOverwriteFilter.accept(source))) {
                        return true;
                    }
                }
                destination.delete();       // Save space, delete before copying
            }
            InputStream in = null;
            OutputStream out = null;
            int copiedLen = 0;
            mCurrentFileTotalSize = source.length();
            mCurrentFile = source.getPath().substring(mStoragePathLength);
            mCurrentFilePercentage = 0;
            try {
                in = new FileInputStream(source);
                out = new FileOutputStream(destination);

                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    if (isCancelled()) {
                        break;
                    }

                    out.write(buf, 0, len);
                    copiedLen += len;
                    mCurrentFileSize += len;

                    int percent = (int) Math.round(copiedLen * 100.0 / mCurrentFileTotalSize);
                    if (percent != mCurrentFilePercentage) {
                        mCurrentFilePercentage = percent;
                        publishProgress();
                    }
                }
            } catch (IOException e) {
                BugSenseHandler.sendException(e);
                e.printStackTrace();
                return false;
            } finally {
                try {
                    if (in != null) {
                        in.close();
                    }
                    if (out != null) {
                        out.close();
                    }
                } catch(IOException e) {}
            }
            return true;
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            super.onProgressUpdate(values);
            int totalProgress = (int)Math.round(mCurrentFileSize * 100.0 / mTotalBytesCopying);
            mCurrentFileNameText.setText(mCurrentFile);
            mCurrentFileSizeText.setText(Formatter.formatFileSize(mCtx, mCurrentFileTotalSize));
            mCurrentPercentText.setText(mCurrentFilePercentage + "%");
            mOverallPercentText.setText(totalProgress + "%");
            mCurrentPercentProgress.setProgress(mCurrentFilePercentage);
            mOverallPercentProgress.setProgress(totalProgress);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            // If only copying 1 block, then don't need overall
            if (mInfo.length <= 1) {
                mOverallPercentText.setVisibility(View.GONE);
                mOverallPercentProgress.setVisibility(View.GONE);
            } else {
                mOverallPercentText.setVisibility(View.VISIBLE);
                mOverallPercentProgress.setVisibility(View.VISIBLE);
            }
            show();
        }

        @Override
        protected Result doInBackground(Void... params) {
            // Copy files
            mCurrentFileSize = 0;
            for (int i = 0; i < mInfo.length; i++) {
                if (isCancelled()) {
                    return Result.CANCELLED;
                }
                if (!copy(new File(mInfo[i].source), new File(mInfo[i].destination))) {
                    return isCancelled() ? Result.CANCELLED : Result.COPY_ERROR;
                }
            }
            return Result.SUCCESS;
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            copyFinished(Result.CANCELLED);
            mShowDialogTimeout = false;
        }

        @Override
        protected void onPostExecute(Result result) {
            super.onPostExecute(result);
            copyFinished(result);
            mShowDialogTimeout = false;
            mDialog.dismiss();
        }
    }
}
