package com.onscripter.plus;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InvalidObjectException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.AsyncTask;
import android.os.StatFs;
import android.util.Log;

import com.onscripter.plus.bugtracking.BugTrackingService;

public class VNPreferences {
    private static final String TAG = "VNPreferences";
    public static final String PREF_FILE_NAME = "pref.json";

    private static final int MIN_FILE_SPACE_KB = 300;
    private static final String UTF8_ENCODING = "UTF-8";

    private static final String JSON_NAME_TYPE = "type";
    private static final String JSON_NAME_VALUE = "value";

    private static final String TYPE_FLOAT = "float";
    private static final String TYPE_INT = "int";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_BOOL = "boolean";

    private final String mPath;
    private final Object mReadWriteLock = new Object();
    private boolean mLoaded;
    private LoadSaveSettingsTask mTask;
    private final HashMap<String, Property> mData;
    private OnLoadVNPrefListener mListener;

    public interface OnLoadVNPrefListener {
        public static enum Result { NO_ISSUES, CANCELLED, NO_MEMORY };

        public void onLoadVNPref(OnLoadVNPrefListener.Result returnVal);
    }

    public VNPreferences(String path) {
        mPath = path;
        mLoaded = false;
        mData = new HashMap<String, Property>();

        // Get the data from the files
        mTask = new LoadSaveSettingsTask(LoadSaveSettingsTask.LOAD_TASK);
        mTask.execute();
    }

    private boolean load() {
        File file = new File(mPath + "/" + PREF_FILE_NAME);
        if (file.exists()) {
            StringBuilder sb = new StringBuilder();
            synchronized (mReadWriteLock) {
                BufferedReader br = null;
                try {
                    // Read file
                    String line;
                    br = new BufferedReader(new InputStreamReader(
                            new FileInputStream(file), UTF8_ENCODING));
                    while((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                } finally {
                    if (br != null) {
                        try {
                            br.close();
                        } catch (IOException e) {}
                    }
                }
            }

            // Parse the JSON data
            JSONObject json;
            try {
                json = new JSONObject(sb.toString());
                for(Iterator<String> iter = json.keys(); iter.hasNext();) {
                    String key = iter.next();
                    JSONObject field = json.getJSONObject(key);
                    String type = field.getString(JSON_NAME_TYPE);
                    Property prop;
                    if (type.equals(TYPE_STRING)) {
                        prop = new Property(field.getString(JSON_NAME_VALUE));
                    } else if (type.equals(TYPE_FLOAT)) {
                        prop = new Property((float)field.getDouble(JSON_NAME_VALUE));
                    } else if (type.equals(TYPE_INT)) {
                        prop = new Property(field.getInt(JSON_NAME_VALUE));
                    } else if (type.equals(TYPE_BOOL)) {
                        prop = new Property(field.getBoolean(JSON_NAME_VALUE));
                    } else {
                        continue;       // Ignored
                    }
                    mData.put(key, prop);
                }
            } catch (JSONException e) {
                e.printStackTrace();
                String name = "*" + (new File(mPath).getName());
                BugTrackingService.sendBugReportWithFile(App.getContext(), name,
                        e, null, file.getAbsolutePath());
                return false;
            }
        }
        mLoaded = true;
        return true;
    }

    public void setOnLoadVNPrefListener(OnLoadVNPrefListener listener) {
        mListener = listener;
    }

    public boolean contains(String name) {
        return mData.containsKey(name);
    }

    public void putInteger(String name, int value) {
        Property prop = mData.get(name);
        if (prop != null) {
            prop.mIntVal = value;
        } else {
            mData.put(name, new Property(value));
        }
    }

    public void putBoolean(String name, boolean value) {
        Property prop = mData.get(name);
        if (prop != null) {
            prop.mBoolval = value;
        } else {
            mData.put(name, new Property(value));
        }
    }

    public void putString(String name, String value) {
        Property prop = mData.get(name);
        if (prop != null) {
            prop.mStringVal = value;
        } else {
            mData.put(name, new Property(value));
        }
    }

    public void putFloat(String name, Float value) {
        Property prop = mData.get(name);
        if (prop != null) {
            prop.mFloatval = value;
        } else {
            mData.put(name, new Property(value));
        }
    }

    public int getInteger(String name, int defaultValue) {
        if (!ensureLoaded()) {
            return defaultValue;
        }
        Property prop = mData.get(name);
        if (prop == null) {
            return defaultValue;
        }
        try {
            return prop.getInteger();
        } catch (InvalidObjectException e) {
            e.printStackTrace();
            return defaultValue;
        }
    }

    public boolean getBoolean(String name, boolean defaultValue) {
        if (!ensureLoaded()) {
            return defaultValue;
        }
        Property prop = mData.get(name);
        if (prop == null) {
            return defaultValue;
        }
        try {
            return prop.getBoolean();
        } catch (InvalidObjectException e) {
            e.printStackTrace();
            return defaultValue;
        }
    }

    public String getString(String name, String defaultValue) {
        if (!ensureLoaded()) {
            return defaultValue;
        }
        Property prop = mData.get(name);
        if (prop == null) {
            return defaultValue;
        }
        try {
            return prop.getString();
        } catch (InvalidObjectException e) {
            e.printStackTrace();
            return defaultValue;
        }
    }

    public float getFloat(String name, float defaultValue) {
        if (!ensureLoaded()) {
            return defaultValue;
        }
        Property prop = mData.get(name);
        if (prop == null) {
            return defaultValue;
        }
        try {
            return prop.getFloat();
        } catch (InvalidObjectException e) {
            e.printStackTrace();
            return defaultValue;
        }
    }

    public void commit() {
        if (mTask != null && mTask.isSaving()) {
            mTask.cancel(true);
        }
        mTask = new LoadSaveSettingsTask(LoadSaveSettingsTask.SAVE_TASK);
        mTask.execute();
    }

    private boolean ensureLoaded() {
        if (!mLoaded) {
            if (mTask != null && !mTask.isSaving()) {
                mTask.cancel(true);
            }
            if (!load()) {
                Log.e(TAG, "Failed to load preferences from file. Will revert to default values");
                if (mListener != null) {
                    mListener.onLoadVNPref(OnLoadVNPrefListener.Result.CANCELLED);
                }
                return false;
            }
            if (mListener != null) {
                mListener.onLoadVNPref(OnLoadVNPrefListener.Result.NO_ISSUES);
            }
        }
        return true;
    }

    private class Property {
        public static final int INTEGER = 0;
        public static final int STRING = 1;
        public static final int BOOLEAN = 2;
        public static final int FLOAT = 3;

        private int mIntVal;
        private String mStringVal;
        private boolean mBoolval;
        private float mFloatval;
        private final int mType;

        public Property(int value) {
            mIntVal = value;
            mType = INTEGER;
        }

        public Property(String value) {
            mStringVal = value;
            mType = STRING;
        }

        public Property(boolean value) {
            mBoolval = value;
            mType = BOOLEAN;
        }

        public Property(float value) {
            mFloatval = value;
            mType = FLOAT;
        }

        public int getInteger() throws InvalidObjectException {
            if (mType != INTEGER) {
                throw new InvalidObjectException(
                        "Cannot get integer when the property is not an integer.");
            }
            return mIntVal;
        }

        public boolean getBoolean() throws InvalidObjectException {
            if (mType != BOOLEAN) {
                throw new InvalidObjectException(
                        "Cannot get boolean when the property is not an boolean.");
            }
            return mBoolval;
        }

        public String getString() throws InvalidObjectException {
            if (mType != STRING) {
                throw new InvalidObjectException(
                        "Cannot get string when the property is not an string.");
            }
            return mStringVal;
        }

        public float getFloat() throws InvalidObjectException {
            if (mType != FLOAT) {
                throw new InvalidObjectException(
                        "Cannot get float when the property is not an float.");
            }
            return mFloatval;
        }

        public JSONObject toJSON() throws JSONException  {
            JSONObject obj = new JSONObject();
            switch (mType) {
            case INTEGER:
                obj.put(JSON_NAME_TYPE, TYPE_INT);
                obj.put(JSON_NAME_VALUE, mIntVal);
                break;
            case FLOAT:
                obj.put(JSON_NAME_TYPE, TYPE_FLOAT);
                obj.put(JSON_NAME_VALUE, mFloatval);
                break;
            case BOOLEAN:
                obj.put(JSON_NAME_TYPE, TYPE_BOOL);
                obj.put(JSON_NAME_VALUE, mBoolval);
                break;
            case STRING:
                obj.put(JSON_NAME_TYPE, TYPE_STRING);
                obj.put(JSON_NAME_VALUE, mStringVal);
                break;
            default:
                return null;
            }
            return obj;
        }
    }

    private class LoadSaveSettingsTask extends AsyncTask<Void, Void, OnLoadVNPrefListener.Result> {
        public static final int LOAD_TASK = 1;
        public static final int SAVE_TASK = 2;

        private final int mTaskCode;

        public LoadSaveSettingsTask(int taskCode) {
            mTaskCode = taskCode;
        }

        public boolean isSaving() {
            return mTaskCode == SAVE_TASK;
        }

        @Override
        protected OnLoadVNPrefListener.Result doInBackground(Void... params) {
            switch (mTaskCode) {
            case LOAD_TASK:
                load();
                break;
            case SAVE_TASK:
                // Check for space left
                long _kbAvailable = 0;
                try {
                    StatFs stat = new StatFs(mPath);
                    long bytesAvailable = (long)stat.getBlockSize() *(long)stat.getBlockCount();
                    _kbAvailable = bytesAvailable / 1024;
                } catch (Exception e) {
                    e.printStackTrace();
                    return OnLoadVNPrefListener.Result.NO_ISSUES;
                }
                final long kbAvailable = _kbAvailable;

                // If at least 300kb is left over
                if (kbAvailable <= MIN_FILE_SPACE_KB) {
                    return OnLoadVNPrefListener.Result.NO_MEMORY;
                } else if (mData != null && !mData.isEmpty()) {
                    return save();
                }
                break;
            }
            return OnLoadVNPrefListener.Result.NO_ISSUES;
        }

        private OnLoadVNPrefListener.Result save() {
            // Build the json object to write to file
            JSONObject data = new JSONObject();
            for (Entry<String, Property> entry : mData.entrySet()) {
                if (isCancelled()) {
                    return OnLoadVNPrefListener.Result.CANCELLED;
                }
                JSONObject obj;
                try {
                    obj = entry.getValue().toJSON();
                    if (obj != null) {
                        data.put(entry.getKey(), obj);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            if (isCancelled()) {
                return OnLoadVNPrefListener.Result.CANCELLED;
            }
            synchronized (mReadWriteLock) {
                // Write to file system
                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(mPath + "/" + PREF_FILE_NAME);
                    byte[] out = UnicodeUtil.convert(data.toString()
                            .getBytes("UTF-16"), UTF8_ENCODING);
                    fos.write(out);
                    fos.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (fos != null) {
                        try {
                            fos.close();
                        } catch (IOException e) {}
                    }
                }
            }
            return OnLoadVNPrefListener.Result.NO_ISSUES;
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            if (mListener != null && mTaskCode == LOAD_TASK) {
                mListener.onLoadVNPref(OnLoadVNPrefListener.Result.CANCELLED);
            }
            mTask = null;
        }

        @Override
        protected void onPostExecute(OnLoadVNPrefListener.Result result) {
            super.onPostExecute(result);
            if (mListener != null && mTaskCode == LOAD_TASK) {
                mListener.onLoadVNPref(OnLoadVNPrefListener.Result.NO_ISSUES);
            }
            mTask = null;
        }
    }

}
