package com.onscripter.plus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.util.HashMap;
import java.util.Map.Entry;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.os.AsyncTask;
import android.os.StatFs;
import android.util.Log;
import android.util.Xml;

import com.bugsense.trace.BugSenseHandler;

public class VNPreferences {
    private static final String TAG = "VNPreferences";
    private static final String XML_HEADER = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>";
    public static final String PREF_FILE_NAME = "pref.xml";

    private static final int MIN_FILE_SPACE_KB = 300;

    // XML parsing texts
    private static final String XML_MAP_NODE = "map";
    private static final String XML_FLOAT_NODE = "float";
    private static final String XML_INT_NODE = "int";
    private static final String XML_STRING_NODE = "string";
    private static final String XML_BOOL_NODE = "boolean";
    private static final String XML_VALUE_ATTR = "value";
    private static final String XML_NAME_ATTR = "name";

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
            synchronized (mReadWriteLock) {
                InputStream in = null;
                try {
                    XmlPullParser parser = Xml.newPullParser();
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);

                    in = new FileInputStream(file);
                    parser.setInput(in, null);
                    parser.nextTag();
                    parser.require(XmlPullParser.START_TAG, null, XML_MAP_NODE);
                    while (parser.next() != XmlPullParser.END_TAG) {
                        if (parser.getEventType() != XmlPullParser.START_TAG) {
                            continue;
                        }
                        String itemName = parser.getName();

                        // Start looking for the line by its type
                        if (itemName.equals(XML_FLOAT_NODE)) {
                            parser.require(XmlPullParser.START_TAG, null, XML_FLOAT_NODE);
                            float value = Float.parseFloat(parser.getAttributeValue(null, XML_VALUE_ATTR));
                            putFloat(parser.getAttributeValue(null, XML_NAME_ATTR), value);
                            parser.nextTag();
                        } else if (itemName.equals(XML_INT_NODE)) {
                            parser.require(XmlPullParser.START_TAG, null, XML_INT_NODE);
                            int value = Integer.parseInt(parser.getAttributeValue(null, XML_VALUE_ATTR));
                            putInteger(parser.getAttributeValue(null, XML_NAME_ATTR), value);
                            parser.nextTag();
                        } else if (itemName.equals(XML_BOOL_NODE)) {
                            parser.require(XmlPullParser.START_TAG, null, XML_BOOL_NODE);
                            boolean value = parser.getAttributeValue(null, XML_VALUE_ATTR).equals("true");
                            putBoolean(parser.getAttributeValue(null, XML_NAME_ATTR), value);
                            parser.nextTag();
                        } else if (itemName.equals(XML_STRING_NODE)) {
                            parser.require(XmlPullParser.START_TAG, null, XML_STRING_NODE);
                            String name = parser.getAttributeValue(null, XML_NAME_ATTR);
                            if (parser.next() == XmlPullParser.TEXT) {
                                putString(name, parser.getText());
                                parser.nextTag();
                            }
                            parser.require(XmlPullParser.END_TAG, null, XML_STRING_NODE);
                        } else {
                            // Not a valid tag
                            if (parser.getEventType() != XmlPullParser.START_TAG) {
                                throw new IllegalStateException();
                            }
                            int depth = 1;
                            while (depth != 0) {
                                switch (parser.next()) {
                                case XmlPullParser.END_TAG:
                                    depth--;
                                    break;
                                case XmlPullParser.START_TAG:
                                    depth++;
                                    break;
                                }
                            }
                        }
                    }

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    BugSenseHandler.sendException(e);
                    return false;
                } catch (XmlPullParserException e) {
                    e.printStackTrace();
                    BugSenseHandler.sendException(e);
                    return false;
                } catch (IOException e) {
                    e.printStackTrace();
                    BugSenseHandler.sendException(e);
                    return false;
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException e) {}
                    }
                }
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
        mData.put(name, new Property(name, value));
    }

    public void putBoolean(String name, boolean value) {
        mData.put(name, new Property(name, value));
    }

    public void putString(String name, String value) {
        mData.put(name, new Property(name, value));
    }

    public void putFloat(String name, Float value) {
        mData.put(name, new Property(name, value));
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
        private final String mName;
        private String mStringVal;
        private boolean mBoolval;
        private float mFloatval;
        private final int mType;

        public Property(String name, int value) {
            mIntVal = value;
            mType = INTEGER;
            mName = name;
        }

        public Property(String name, String value) {
            mStringVal = value;
            mType = STRING;
            mName = name;
        }

        public Property(String name, boolean value) {
            mBoolval = value;
            mType = BOOLEAN;
            mName = name;
        }

        public Property(String name, float value) {
            mFloatval = value;
            mType = FLOAT;
            mName = name;
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

        public String toXMLLine() {
            switch (mType) {
            case INTEGER:
                return "<" + XML_INT_NODE + " " + XML_NAME_ATTR + "=\"" + mName + "\" " + XML_VALUE_ATTR + "=\"" + mIntVal
                        + "\"/>";
            case FLOAT:
                return "<" + XML_FLOAT_NODE + " " + XML_NAME_ATTR + "=\"" + mName + "\" " + XML_VALUE_ATTR + "=\"" + mFloatval
                        + "\"/>";
            case BOOLEAN:
                return "<" + XML_BOOL_NODE + " " + XML_NAME_ATTR + "=\"" + mName + "\" " + XML_VALUE_ATTR + "=\""
                        + (mBoolval ? "true" : "false") + "\"/>";
            case STRING:
                return "<" + XML_STRING_NODE + " " + XML_NAME_ATTR + "=\"" + mName + "\">" + mStringVal
                        + "</string>";
            default:
                return ""; // Not possible
            }
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
                StatFs stat = new StatFs(mPath);
                long bytesAvailable = (long)stat.getBlockSize() *(long)stat.getBlockCount();
                final long kbAvailable = bytesAvailable / 1024;

                // If at least 300kb is left over
                if (kbAvailable <= MIN_FILE_SPACE_KB) {
                    return OnLoadVNPrefListener.Result.NO_MEMORY;
                } else if (mData != null && !mData.isEmpty()) {
                    synchronized (mReadWriteLock) {
                        File file = new File(mPath + "/" + PREF_FILE_NAME);
                        final byte[] newLine = System.getProperty("line.separator").getBytes();
                        FileOutputStream os = null;
                        try {
                            if (!file.exists()) {
                                file.createNewFile();
                            }
                            os = new FileOutputStream(file);
                            os.write(XML_HEADER.getBytes());
                            os.write(newLine);
                            os.write(("<" + XML_MAP_NODE + ">").getBytes());
                            os.write(newLine);

                            for (Entry<String, Property> entry : mData.entrySet()) {
                                if (isCancelled()) {
                                    return OnLoadVNPrefListener.Result.CANCELLED;
                                }
                                os.write(entry.getValue().toXMLLine().getBytes());
                                os.write(newLine);
                            }

                            os.write(("</" + XML_MAP_NODE + ">").getBytes());
                            os.flush();
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                            BugSenseHandler.sendException(e);
                        } catch (IOException e) {
                            e.printStackTrace();
                            BugSenseHandler.sendException(e);
                        } finally {
                            if (os != null) {
                                try {
                                    os.close();
                                } catch (IOException e) {
                                }
                            }
                        }
                    }
                }
                break;
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
