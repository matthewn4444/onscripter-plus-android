package com.onscripter.plus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class CopyFilesDialogTask extends AsyncTask<Void, Void, Integer> {

    public static class CopyFileInfo {
        public CopyFileInfo(String src, String dst) {
            source = src;
            destination = dst;
        }
        public String source;
        public String destination;
    }

    public static interface CopyFilesDialogListener {
        public void onCopyCompleted(int result);
    }

    public final static int RESULT_SUCCESS = 0;
    public final static int RESULT_CANCELLED = 1;
    public final static int RESULT_COPY_ERROR = 2;

    private Dialog mDialog;
    private final Context mCtx;
    private final CopyFileInfo[] mInfo;
    private final CopyFilesDialogListener mListener;
    private final LinearLayout mLayout;
    private final TextView mCurrentFileNameText;
    private final TextView mCurrentFileSizeText;
    private final TextView mCurrentPercentText;
    private final TextView mOverallPercentText;
    private final ProgressBar mCurrentPercentProgress;
    private final ProgressBar mOverallPercentProgress;
    private final int mExtSDCardPathLength;

    // Progress data
    private String mCurrentFile;
    private long mCurrentFileSize;
    private long mTotalBytesCopying;
    private long mCurrentFileTotalSize;
    private int mCurrentFilePercentage;

    public CopyFilesDialogTask(Context ctx, CopyFileInfo[] info,
            CopyFilesDialogListener listener) {
        mCtx = ctx;
        mInfo = info;
        mListener = listener;

        // Inflate the dialog
        LayoutInflater inflater = (LayoutInflater) mCtx.getSystemService( Context.LAYOUT_INFLATER_SERVICE);
        mLayout = (LinearLayout) inflater.inflate(R.layout.copy_file_dialog, null);

        mCurrentFileNameText = (TextView) mLayout.findViewById(R.id.filename);
        mCurrentFileSizeText = (TextView) mLayout.findViewById(R.id.filesize);
        mCurrentPercentText = (TextView) mLayout.findViewById(R.id.filePercent);
        mOverallPercentText = (TextView) mLayout.findViewById(R.id.overallPercent);
        mCurrentPercentProgress = (ProgressBar) mLayout.findViewById(R.id.fileProgressbar);
        mOverallPercentProgress = (ProgressBar) mLayout.findViewById(R.id.overallProgressbar);

        mExtSDCardPathLength = Environment2.getExternalSDCardDirectory().getPath().length();
    }

    private void show() {
        if (mDialog == null) {
            mDialog = new AlertDialog.Builder(mCtx)
                .setCancelable(false)
                .setTitle(R.string.dialog_scan_files_title)
                .setView(mLayout)
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        cancel(true);
                    }
                })
                .create();
        }
        mDialog.show();
    }

    /**
     * Recursively scans each file in given folder (or file) and returns the
     * total sum of their file size. Maybe slow with many files
     * @param source
     * @return
     */
    private long scanTotalBytes(File source) {
        long totalBytes = 0;
        if (source.isFile()) {
            totalBytes += source.length();
        } else {
            File[] children = source.listFiles();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    totalBytes += scanTotalBytes(children[i]);
                }
            }
        }
        return totalBytes;
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
        InputStream in = null;
        OutputStream out = null;
        int copiedLen = 0;
        mCurrentFileTotalSize = source.length();
        mCurrentFile = source.getPath().substring(mExtSDCardPathLength);
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
        mCurrentFileSizeText.setText(mCurrentFileTotalSize / 1024 + " kb");
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
    protected Integer doInBackground(Void... params) {
        mTotalBytesCopying = 0;
        mCurrentFileSize = 0;
        // Scan files
        for (int i = 0; i < mInfo.length; i++) {
            if (isCancelled()) {
                return RESULT_CANCELLED;
            }
            mTotalBytesCopying += scanTotalBytes(new File(mInfo[i].source));
        }

        // TODO detect if running out of space

        // Copy files
        mDialog.setTitle(R.string.dialog_copy_files_title);
        for (int i = 0; i < mInfo.length; i++) {
            if (isCancelled()) {
                return RESULT_CANCELLED;
            }
            if (!copy(new File(mInfo[i].source), new File(mInfo[i].destination))) {
                return isCancelled() ? RESULT_CANCELLED : RESULT_COPY_ERROR;
            }
        }
        return RESULT_SUCCESS;
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        if (mListener != null) {
            mListener.onCopyCompleted(RESULT_CANCELLED);
        }
    }

    @Override
    protected void onPostExecute(Integer result) {
        super.onPostExecute(result);
        mDialog.dismiss();
        if (mListener != null) {
            mListener.onCopyCompleted(result);
        }
    }
}
