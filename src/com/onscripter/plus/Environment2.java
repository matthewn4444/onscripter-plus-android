package com.onscripter.plus;

import java.io.File;

import android.annotation.SuppressLint;
import android.os.Environment;

public final class Environment2 {

    // List of all potential external sd card paths
    @SuppressLint("SdCardPath")
    private final static String[] EXTERNAL_SD_DIR = {
        "/mnt/external",
        "/mnt/sdcard/external_sd",
        "/mnt/sdcard/ext_sd",
        "/mnt/external_sd",
        "/mnt/ext_sd",
        "/mnt/ext_sdcard",
        "/mnt/extSdCard",
        "/mnt/extsd",
        "/mnt/sdcard-ext",
        "/storage/sdcard1/",
    };
    private static boolean HasScanned = false;

    // Private constructor so it is not used
    private Environment2(){}

    // Cached locations
    private static File InternalStorageFile;
    private static File ExternalSDCardStorageFile;

    static public File getInternalStorageDirectory() {
        verifyInternalStorage();
        return InternalStorageFile;
    }

    static public File getExternalSDCardDirectory() {
        verifyExternalSDCard();
        return ExternalSDCardStorageFile;
    }

    static public boolean hasInternalStorage() {
        verifyInternalStorage();
        return InternalStorageFile != null;
    }

    static public boolean hasExternalSDCard() {
        verifyExternalSDCard();
        return ExternalSDCardStorageFile != null;
    }

    // Checks to see if the file exists before returning, also rescans if anything may have gone wrong
    static private void verifyInternalStorage() {
        if (!HasScanned && InternalStorageFile == null ||
                InternalStorageFile != null && !InternalStorageFile.exists()) {
            reScan();
        }
    }

    // Checks to see if the file exists before returning, also rescans if anything may have gone wrong
    static private void verifyExternalSDCard() {
        if (!HasScanned && ExternalSDCardStorageFile == null ||
                ExternalSDCardStorageFile != null && !ExternalSDCardStorageFile.exists()) {
            reScan();
        }
    }

    // Rescans the potential locations for storage
    static public void reScan() {
        ExternalSDCardStorageFile = null;
        InternalStorageFile = Environment.getExternalStorageDirectory();
        for (int i = 0; i < EXTERNAL_SD_DIR.length; i++) {
            File file = new File(EXTERNAL_SD_DIR[i]);
            if (file != null && file.exists() && !file.equals(InternalStorageFile)      // Directory must exist and not be internal memory
                    && file.length() > 0) {
                // Directory also must have space and files
                String[] fileList = file.list();
                if (fileList != null && fileList.length > 0) {
                    ExternalSDCardStorageFile = file;
                    break;
                }
            }
        }
        HasScanned = true;
    }
}
