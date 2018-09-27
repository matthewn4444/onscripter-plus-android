package com.onscripter.plus;

import java.io.File;
import java.io.FileFilter;

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
        "/mnt/ext_card",
        "/mnt/extsd",
        "/mnt/sdcard-ext",
        "/mnt/sdcard2",
        "/storage/sdcard1/",
        "/storage/external_SD",
        "/storage/MicroSD",
        "/sdcard2",
        "/storage/ext_sd",
        "/sdcard0",
        "/mnt/sdcard0",
        "/mnt/sdcard/",
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

        // Scan storage for XXXX-XXXX folder pattern that should represent the sdcard
        File storage = new File("/storage");
        if (ExternalSDCardStorageFile == null && storage.exists() && storage.canRead()) {
            File[] files = storage.listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return pathname.getName().matches("[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}");
                }
            });
            for (File file: files) {
                if (file.canRead()) {
                    ExternalSDCardStorageFile = file;
                    break;
                }
            }
        }

        if (ExternalSDCardStorageFile == null) {
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
        }

        HasScanned = true;
    }
}
