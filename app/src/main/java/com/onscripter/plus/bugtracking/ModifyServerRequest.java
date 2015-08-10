package com.onscripter.plus.bugtracking;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Pair;

public class ModifyServerRequest {
    static enum METHOD { PUT, POST };

    private HttpURLConnection mConnection;
    private ArrayList<String> mFieldData;
    private ArrayList<Pair<String, byte[]>> mFileData;

    private final METHOD mMethod;

    /**
     * Default constructor that sets method to be POST
     */
    public ModifyServerRequest() {
        mMethod = METHOD.POST;
    }

    /**
     * Constructor to specify the method to use, either
     * POST or PUT
     * @param method
     */
    public ModifyServerRequest(METHOD method) {
        mMethod = method;
    }

    /**
     * Open the connection specifying the url
     * @param url
     * @throws IOException
     */
    public void openConnection(String url) throws IOException {
        if (mConnection == null) {
            try {
                URL _url = new URL(url);
                System.setProperty("http.keepAlive", "false");
                mConnection = (HttpURLConnection) _url.openConnection();
                mConnection.setDoOutput(true);
                mConnection.setDoInput(true);
                mConnection.setUseCaches(false);
                mConnection.setRequestMethod(mMethod == METHOD.PUT ? "PUT" : "POST");
                mConnection.setReadTimeout(10000);
                mFieldData = new ArrayList<String>();
                mFileData = new ArrayList<Pair<String, byte[]>>();
            } catch (IOException e) {
                mConnection = null;
                throw e;
            }
        }
    }

    /**
     * Add a field to the POST/PUT as data to send to the server
     * @param field
     * @param value
     */
    public void putField(String field, String value) {
        if (mFieldData != null) {
            mFieldData.add(field + "=" + value);
        }
    }

    /**
     * Add a file with its contents handled as binary to send to the server
     * @param field
     * @param data
     */
    public void putFile(String field, byte[] data) {
        if (mFieldData != null) {
            mFileData.add(Pair.create(field + "|" + data.length + "=", data));
        }
    }

    /**
     * Send the request to the server after specifying the fields and files
     * @throws IOException
     */
    public void send() throws IOException {
        if (mConnection != null) {
            DataOutputStream writer = new DataOutputStream(mConnection.getOutputStream());
            for (int i = 0; i < mFieldData.size(); i++) {
                writer.write(mFieldData.get(i).getBytes("UTF-8"));
                if (i + 1 < mFieldData.size()) {
                    writer.writeBytes("&");
                }
            }
            if (mFileData.size() > 0) {
                writer.writeBytes("&");
                for (int i = 0; i < mFileData.size(); i++) {
                    Pair<String, byte[]> data = mFileData.get(i);
                    writer.writeBytes(data.first);
                    writer.write(data.second);
                    if (i + 1 < mFileData.size()) {
                        writer.writeBytes("&");
                    }
                }
            }
            writer.flush();
            writer.close();
            mFileData.clear();
            mFieldData.clear();
        }
    }

    /**
     * Disconnects communication to the server.
     * You should run this whenever you use openConnection (place this
     * in the finally clause of a try-catch)
     */
    public void disconnect() {
        if (mConnection != null) {
            mConnection.disconnect();
            mConnection = null;
        }
    }

    /**
     * Retrieves the response code from the server.
     * e.g. Good = 200
     * @return
     * @throws IOException
     */
    public int getResponseCode() throws IOException {
        return mConnection != null ? mConnection.getResponseCode() : 0;
    }

    /**
     * Retrieves the data that the server returns. If the response
     * code is 500 then this most likely will return null
     * @return
     * @throws IOException
     */
    public String getResponse() throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(mConnection.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line + "\n");
            }
        } finally {
            if (br != null) {
                br.close();
            }
        }
        return sb.toString();
    }

    /**
     * Returns the response data as a JSON. You must know if the server can
     * return JSON files otherwise, this will throw an exception.
     * @return
     * @throws IOException
     * @throws JSONException
     */
    public JSONObject getResponseJSON() throws IOException, JSONException {
        return new JSONObject(getResponse());
    }
}
