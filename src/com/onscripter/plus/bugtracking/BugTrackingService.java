package com.onscripter.plus.bugtracking;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.onscripter.ONScripterTracer;
import com.onscripter.plus.R;
import com.onscripter.plus.bugtracking.ModifyServerRequest.METHOD;

public class BugTrackingService extends IntentService {
    private static final String TAG = "BugTrackingService";

//    private static final String URL_SERVER_HOST = "http://10.0.0.5:1111/";            // For localhost
    private static final String URL_SERVER_HOST = "http://onscripter-plus-bug-server.herokuapp.com/";
    private static final String URL_PUT_QUERY_NEW_ERROR = URL_SERVER_HOST + "game/trace/";
    private static final String URL_POST_QUERY_NEW_BUG = URL_SERVER_HOST + "game/bug/";

    private static final String RETURN_JSON_KEY_SUCCESS = "success";
    private static final String RETURN_JSON_KEY_MESSAGE = "message";
    private static final String RETURN_JSON_KEY_ID = "id";

    private static final String INTENT_KEY_EXCEPTION_MSG = "in.key.exception.msg";
    private static final String INTENT_KEY_GAME_NAME = "in.key.game.name";
    private static final String INTENT_KEY_STACKTRACE = "in.key.stacktrace";
    private static final String INTENT_KEY_LOG_TIME = "in.key.log.time";
    private static final String INTENT_KEY_EXTRA = "in.key.extra";
    private static final String INTENT_KEY_DATE = "in.key.date";
    private static final String INTENT_KEY_HAS_SAVE = "in.key.has.save.file";
    private static final String INTENT_KEY_EXTRA_FILE = "in.key.extra.file";

    private static final char PARAMS_DELIMITER = '|';

    private static String APP_VERSION_CODE;
    private static String APP_VERSION_NAME;
    private static String APP_TRACE_FOLDER;

    public static final String PREF_KEY_PENDING_REPORT = "pref.key.pending.report";

    public BugTrackingService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String exceptionMessage = intent.getStringExtra(INTENT_KEY_EXCEPTION_MSG);
        String gameName = intent.getStringExtra(INTENT_KEY_GAME_NAME);
        String stacktrace = intent.getStringExtra(INTENT_KEY_STACKTRACE);
        String logTime = intent.getStringExtra(INTENT_KEY_LOG_TIME);
        String extraData = intent.getStringExtra(INTENT_KEY_EXTRA);
        String date = intent.getStringExtra(INTENT_KEY_DATE);
        boolean hasSaveFile = intent.getBooleanExtra(INTENT_KEY_HAS_SAVE, false);
        String uploadFile = intent.getStringExtra(INTENT_KEY_EXTRA_FILE);
        try {
            if (logTime != null) {
                sendTraceData(exceptionMessage, gameName, stacktrace, logTime, extraData, date, hasSaveFile);
            } else {
                sendBugData(exceptionMessage, gameName, stacktrace, extraData, uploadFile);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Send any pending reports held in preferences.
     *
     * This is used to hold information from the ONScripter game because if there was
     * a crash, it cannot send any data to the database. When the app goes back to
     * home screen, this should be called to send any pending report.
     *
     * First it checks for a string in preferences that hold data to send online,
     * if there is internet it will send it and deletes pref even if no internet.
     * Finally it checks the data params and creates a service to upload the data.
     *
     * Read createCrashReport for more info.
     * @param ctx
     */
    public static void sendPendingReport(Context ctx) {
        if (APP_VERSION_CODE == null) {
            APP_VERSION_CODE = getAppVersionCode(ctx) + "";
            APP_VERSION_NAME = getAppVersionName(ctx) + "";
            APP_TRACE_FOLDER = ctx.getFilesDir().getAbsolutePath();
        }

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(ctx);
        String data = pref.getString(PREF_KEY_PENDING_REPORT, null);
        if (data != null) {
            String[] params = data.toString().split("\\|");

            // Parse game specific bugs and decide whether to just show a message or send to server
            try {
                if (handleGameCrashesShowMessage(ctx, params[0], Long.parseLong(params[3]))) {
                    pref.edit().remove(PREF_KEY_PENDING_REPORT).apply();
                    return;
                }
            } catch(NumberFormatException e) {
                e.printStackTrace();
            }

            // Debug mode should output the error and not send the data
            if (isDebug(ctx)) {
                pref.edit().remove(PREF_KEY_PENDING_REPORT).apply();
                if (params.length >= 1) {
                    Toast.makeText(ctx, params[0].trim(), Toast.LENGTH_SHORT).show();
                }
            } else if (isNetworkAvailable(ctx)) {
                pref.edit().remove(PREF_KEY_PENDING_REPORT).apply();
                if (params.length != 7) {
                    Log.w(TAG, "Unable to send data to server because we lack number of arguments.");
                    return;
                }

                // Only send if the log time is longer than 1 second
                if (Long.parseLong(params[3], 10) < 1000) {
                    return;
                }

                Intent in = new Intent(ctx, BugTrackingService.class);
                in.putExtra(INTENT_KEY_EXCEPTION_MSG, params[0]);
                in.putExtra(INTENT_KEY_GAME_NAME, params[1]);
                in.putExtra(INTENT_KEY_STACKTRACE, params[2]);
                in.putExtra(INTENT_KEY_LOG_TIME, params[3]);
                in.putExtra(INTENT_KEY_EXTRA, params[4]);
                in.putExtra(INTENT_KEY_DATE, params[5]);
                in.putExtra(INTENT_KEY_HAS_SAVE, Boolean.parseBoolean(params[6]));
                ctx.startService(in);
            }
        }
    }

    /**
     * Sends a generic bug report to the server, will fail if activity crashes after this.
     *
     * Will not send if user has no internet connection.
     * @param ctx
     * @param gameName
     * @param exception
     * @param extraData
     */
    public static void sendBugReport(Context ctx, String gameName, Exception exception,
            Map<String, String> extraData) {
        if (!isDebug(ctx) && !ONScripterTracer.playbackEnabled() && isNetworkAvailable(ctx)) {
            Intent in = new Intent(ctx, BugTrackingService.class);
            in.putExtra(INTENT_KEY_EXCEPTION_MSG, exception.getMessage());
            in.putExtra(INTENT_KEY_GAME_NAME, gameName);
            in.putExtra(INTENT_KEY_STACKTRACE, Log.getStackTraceString(exception));
            in.putExtra(INTENT_KEY_EXTRA, extrasMapToJSONString(extraData));
            ctx.startService(in);
        }
    }

    public static void sendBugReportWithFile(Context ctx, String gameName, Exception exception,
            Map<String, String> extraData, String file) {
        if (!isDebug(ctx) && !ONScripterTracer.playbackEnabled() && isNetworkAvailable(ctx)) {
            Intent in = new Intent(ctx, BugTrackingService.class);
            in.putExtra(INTENT_KEY_EXCEPTION_MSG, exception.getMessage());
            in.putExtra(INTENT_KEY_GAME_NAME, gameName);
            in.putExtra(INTENT_KEY_STACKTRACE, Log.getStackTraceString(exception));
            in.putExtra(INTENT_KEY_EXTRA, extrasMapToJSONString(extraData));
            if (new File(file).exists()) {
                in.putExtra(INTENT_KEY_EXTRA_FILE, file);
            }
            ctx.startService(in);
        }
    }

    /**
     * Creates a crash report for individual games and uploads their traces.
     *
     * This does not send because most of the time this will run, it expects the activity to
     * exit in NDK causing any connections to fail. Therefore the data is saved to preferences
     * and uploaded later when we get back to the home screen.
     * @param ctx
     * @param exceptionMessage
     * @param gameName
     * @param stacktrace
     * @param extraData
     */
    public static void createCrashReport(final Context ctx, final String exceptionMessage,
            final String gameName, final String stacktrace, final Map<String, String> extraData) {
        StringBuilder params = new StringBuilder()
            .append(exceptionMessage).append(PARAMS_DELIMITER)
            .append(gameName).append(PARAMS_DELIMITER)
            .append(stacktrace).append(PARAMS_DELIMITER)
            .append(ONScripterTracer.getCurrentLogTime() + "").append(PARAMS_DELIMITER)
            .append(extrasMapToJSONString(extraData)).append(PARAMS_DELIMITER)
            .append(System.currentTimeMillis() + "").append(PARAMS_DELIMITER)
            .append(ONScripterTracer.hasLoadedSaveFile());

        PreferenceManager.getDefaultSharedPreferences(ctx).edit()
            .putString(PREF_KEY_PENDING_REPORT, params.toString()).commit();
    }

    /**
     * All the data to send to the Internet for generic bug report.
     *
     * Make sure that if you run this explicitly that it is threaded and sendPendingReport() should
     * have ran to init all the static variables.
     * @param exceptionMessage
     * @param gameName
     * @param stacktrace
     * @param extraData
     * @throws IOException
     */
    private static void sendBugData(String exceptionMessage, String gameName, String stacktrace,
            String extraData, String uploadFile) throws IOException {
        ModifyServerRequest request = null;
        try {
            request = new ModifyServerRequest(METHOD.POST);
            request.openConnection(URL_POST_QUERY_NEW_BUG);
            request.putField("stacktrace", stacktrace);
            request.putField("phoneModel", Build.MODEL);
            request.putField("androidVersion", Build.VERSION.RELEASE);
            request.putField("locale", Resources.getSystem().getConfiguration().locale.getDisplayName());
            request.putField("extra", extraData);
            request.putField("number", APP_VERSION_CODE);
            request.putField("versionString", APP_VERSION_NAME);
            request.putField("message", exceptionMessage);
            request.putField("name", gameName);
            if (uploadFile != null) {
                byte[] data = readBytesFromFile(uploadFile);
                if (data != null) {
                    request.putFile("extraFile", data);
                }
            }
            request.send();

            handleGenericJSONResult(request);
        } finally {
            if (request != null) {
                request.disconnect();
            }
        }
    }

    /**
     * All the data to send to the Internet for a trace to be recorded.
     *
     * Make sure that if you run this explicitly that it is threaded and sendPendingReport() should
     * have ran to init all the static variables.
     *
     * This will upload some metadata first (sendErrorMetadata), if the server says we can
     * upload the trace and optional save file, then that will be on the second request; to minimize
     * traffic on the server.
     * @param exceptionMessage
     * @param gameName
     * @param stacktrace
     * @param logTimeStr
     * @param extraData
     * @param dateStr
     * @param hasSaveFile
     * @throws IOException
     */
    private static void sendTraceData(String exceptionMessage, String gameName, String stacktrace,
            String logTimeStr, String extraData, String dateStr, boolean hasSaveFile) throws IOException {
        CookieManager cookieManager = new CookieManager();
        CookieHandler.setDefault(cookieManager);

        String id = sendErrorMetadata(exceptionMessage, gameName, logTimeStr, dateStr);
        if (id != null) {
            ModifyServerRequest request = null;
            try {
                byte[] logFileData = readBytesFromFile(APP_TRACE_FOLDER + "/" + ONScripterTracer.TRACE_FILE_NAME);
                byte[] saveFileData = null;
                if (hasSaveFile) {
                    saveFileData = readBytesFromFile(APP_TRACE_FOLDER + "/" + ONScripterTracer.SAVE_FILE_NAME);
                }
                if (logFileData != null && (hasSaveFile && saveFileData != null || !hasSaveFile)) {
                    request = new ModifyServerRequest(METHOD.POST);
                    request.openConnection(URL_SERVER_HOST + "game/" + id + "/trace/");
                    request.putField("stacktrace", stacktrace);
                    request.putField("traceLength", logTimeStr);
                    request.putField("phoneModel", Build.MODEL);
                    request.putField("androidVersion", Build.VERSION.RELEASE);
                    request.putField("date", dateStr);
                    request.putField("locale", Resources.getSystem().getConfiguration().locale.getDisplayName());
                    request.putField("extra", extraData);
                    request.putFile("traceLog", logFileData);
                    if (hasSaveFile) {
                        request.putFile("saveFile", saveFileData);
                    }
                    request.send();

                    handleGenericJSONResult(request);
                } else {
                    Log.e(TAG, "Unable to open log file to send");
                }
            } finally {
                if (request != null) {
                    request.disconnect();
                }
            }
        } else {
            Log.i(TAG, "Did not except this bug because full already.");
        }
    }

    /**
     * Send metadata to the server. This generally will increment the occurrences of this bug.
     *
     * This will return an id for the game object, if none is given then we do not upload
     * any data to the server because it is full.
     * @param exceptionMessage
     * @param gameName
     * @param traceLengthStr
     * @param dateStr
     * @return
     * @throws IOException
     */
    private static String sendErrorMetadata(String exceptionMessage,
            String gameName, String traceLengthStr, String dateStr) throws IOException {
        ModifyServerRequest request = null;
        try {
            request = new ModifyServerRequest(METHOD.PUT);
            request.openConnection(URL_PUT_QUERY_NEW_ERROR);
            request.putField("number", APP_VERSION_CODE);
            request.putField("versionString", APP_VERSION_NAME);
            request.putField("message", exceptionMessage);
            request.putField("name", gameName);
            request.putField("date", dateStr);
            request.putField("traceLength", traceLengthStr);
            request.send();

            JSONObject result;
            try {
                result = request.getResponseJSON();
                if (result.getBoolean(RETURN_JSON_KEY_SUCCESS)) {
                    return result.getString(RETURN_JSON_KEY_ID);
                } else {
                    Log.w(TAG, "Failed to send meta data to server: "
                            + result.getString(RETURN_JSON_KEY_MESSAGE));
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                Log.e(TAG, "Could not parse return data: " + e.getMessage());
                e.printStackTrace();
            }
        } finally {
            if (request != null) {
                request.disconnect();
            }
        }
        return null;
    }

    /**
     * Handles sending messages to user if there is a crash but only some games
     * hardcoded to the situation listed in the function.
     *
     * @param ctx
     * @param exceptionMessage
     * @param traceLength (ms)
     * @return Whether to consume the crash or not
     */
    private static boolean handleGameCrashesShowMessage(Context ctx, String exceptionMessage, long traceLength) {
        if (exceptionMessage != null) {
            // 'Label "define" is not found' exception that is called under 2 sec
            if (exceptionMessage.contains("Label \"define\" is not found.") && traceLength < 2 * 1000
                || exceptionMessage.contains("Label \"l_op01\" is not found")) {
                new AlertDialog.Builder(ctx)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.message_missing_label_define)
                    .setNeutralButton(android.R.string.ok, null)
                    .show();
                return true;
            }
        }
        return false;
    }

    /**
     * Handles a generic result from ModifyServerRequest.
     *
     * Returns true if request was success.
     * @param request
     * @return
     * @throws IOException
     */
    private static boolean handleGenericJSONResult(ModifyServerRequest request) throws IOException {
        JSONObject result;
        try {
            result = request.getResponseJSON();
            if (result.has(RETURN_JSON_KEY_SUCCESS)) {
                Log.i(TAG, "Bug report was send successfully. Return value: "
                        + result.getBoolean(RETURN_JSON_KEY_SUCCESS));
                return result.getBoolean(RETURN_JSON_KEY_SUCCESS);
            } else {
                Log.w(TAG, "Bug report failed to send to server: "
                        + result.getString(RETURN_JSON_KEY_MESSAGE));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Could not parse return data: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Gets the bytes from a file path
     * @param filepath
     * @return
     */
    private static byte[] readBytesFromFile(String filepath) {
        File file = new File(filepath);
        FileInputStream is = null;
        int size = (int) file.length();
        byte[] buffer = new byte[size];
        try {
            is = new FileInputStream(file);
            is.read(buffer, 0, size);
            return buffer;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {}
            }
        }
        return null;
    }

    /**
     * Used to convert a map of data into a json string for the server to read later
     * @param extraData
     * @return
     */
    private static String extrasMapToJSONString(Map<String, String> extraData) {
        if (extraData != null) {
            StringBuilder json = new StringBuilder();
            json.append('{');
            Iterator<Entry<String, String>> entries = extraData.entrySet().iterator();
            while (entries.hasNext()) {
                Entry<String, String> entry = entries.next();
                json.append('"').append(entry.getKey()).append("\":\"").append(entry.getValue()).append('"');
                if (entries.hasNext()) {
                    json.append(',');
                }
            }
            json.append('}');
            return json.toString();
        }
        return null;
    }

    private static int getAppVersionCode(Context c) {
        try {
            String pkg = c.getPackageName();
            return c.getPackageManager().getPackageInfo(pkg, 0).versionCode;
        } catch (NameNotFoundException e) {
            return 0;
        }
    }

    private static String getAppVersionName(Context c) {
        try {
            String pkg = c.getPackageName();
            return c.getPackageManager().getPackageInfo(pkg, 0).versionName;
        } catch (NameNotFoundException e) {
            return "?";
        }
    }

    private static boolean isDebug(Context ctx) {
        return (ctx.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private static boolean isNetworkAvailable(Context ctx) {
        ConnectivityManager connectivityManager
              = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
}
