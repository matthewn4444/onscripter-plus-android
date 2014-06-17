package com.onscripter.plus;

import java.io.IOException;
import java.util.HashMap;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.XmlResourceParser;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.HitBuilders.AppViewBuilder;
import com.google.android.gms.analytics.Tracker;

public final class Analytics {
    public enum TrackerType { APP_TRACKER, GLOBAL_TRACKER };

    public enum Category {
        CLICK("Click"),
        CHANGES("Changes");

        private final String mVal;
        private Category(final String s) {
            mVal = s;
        }
        @Override
        public String toString() {
            return mVal.trim();
        }
    };

    public enum Action {
        BUTTON_PRESS("Button Press"),
        TEXT_CHANGE("Text");

        private final String mVal;
        private Action(final String s) {
            mVal = s;
        }
        @Override
        public String toString() {
            return mVal.trim();
        }
    };

    public enum BUTTON {
        SETTINGS("Settings Button"),
        CHANGE_SPEED("Change Speed Button"),
        SKIP("Skip Button"),
        AUTO("Auto Button"),
        BACK("Back Button");

        private final String mVal;
        private BUTTON(final String s) {
            mVal = s;
        }
        @Override
        public String toString() {
            return mVal.trim();
        }
    };

    public enum CHANGE {
        TEXT_SCALE("Text Scale (%)");

        private final String mVal;
        private CHANGE(final String s) {
            mVal = s;
        }
        @Override
        public String toString() {
            return mVal.trim();
        }
    };

    /*
     * In Google Analytics console, this is the following custom dimension
     * 1 - Games per user           [dimension] (Number) {User}
     * 2 - Theme                    [dimension] (Black | White) {User}
     * 3 - Wifi/Data Enabled        [dimension] (True | False) {Session}
     * 4 - Control Gesture Type     [dimension] (Right | Left | Right and Left | Disabled) {Session}
     */
    private static class CustomDimension {
        public final static int GAMES_PER_USER = 1;
        public final static int THEME = 2;
        public final static int CONNECT_ENABLED = 3;
        public final static int CONTROL_GESTURE_TYPE = 4;
    }

    static final private String PROPERTY_ID = "UA-39733898-3";

    static private HashMap<TrackerType, Tracker> sTrackers = new HashMap<TrackerType, Tracker>();
    static private HashMap<String, String> sScreenNameAliases = new HashMap<String, String>();

    static private Tracker sCurrentTracker = null;
    static private SharedPreferences sPref = null;
    static private ConnectivityManager sConnManager = null;

    synchronized static void initTrackers(Context ctx) {
        if (sTrackers.isEmpty()) {
            GoogleAnalytics analytics = GoogleAnalytics.getInstance(ctx);
            for (TrackerType type : TrackerType.values()) {
                switch(type) {
                    case APP_TRACKER:
                        sTrackers.put(type, analytics.newTracker(PROPERTY_ID));
                        break;
                    case GLOBAL_TRACKER:
                        sTrackers.put(type, analytics.newTracker(R.xml.app_tracker));
                        break;
                }
            }
            sPref = PreferenceManager.getDefaultSharedPreferences(ctx);
            sConnManager = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);

            // Cache the path names
            XmlResourceParser parser = ctx.getResources().getXml(R.xml.global_tracker);
            try {
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.getEventType() == XmlPullParser.START_TAG) {
                        if (parser.getName().equals("screenName")) {
                            String className = parser.getAttributeValue(null, "name").trim();
                            if (parser.next() == XmlPullParser.TEXT) {
                                String alias  = parser.getText().trim();
                                sScreenNameAliases.put(className, alias);
                            }
                        }
                        parser.next();
                    }
                }
            } catch (XmlPullParserException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static synchronized Tracker appTracker() {
        sCurrentTracker = sTrackers.get(TrackerType.APP_TRACKER);
        return sCurrentTracker;
    }

    static synchronized Tracker globalTracker() {
        sCurrentTracker = sTrackers.get(TrackerType.GLOBAL_TRACKER);
        return sCurrentTracker;
    }

    // For easy activity app tracking
    public static void start(Activity activity) {
        start(activity, false);
    }
    public static void start(Activity activity, boolean newSession) {
        internalStart(activity, appTracker(), new HitBuilders.AppViewBuilder(), newSession);
    }

    public static void startLauncher(Activity activity, long numOfGames) {
        startLauncher(activity, numOfGames, false);
    }

    public static void startLauncher(Activity activity, long numOfGames, boolean newSession) {
        initTrackers(activity);
        HitBuilders.AppViewBuilder builder = new AppViewBuilder();

        // Get the theme
        String themeDefault = activity.getString(R.string.settings_theme_default_value);
        String themeVal = sPref.getString(activity.getString(R.string.settings_theme_key), themeDefault);
        String themeName = themeVal.equals(themeDefault) ? "White" : "Black";

        builder.setCustomDimension(CustomDimension.GAMES_PER_USER, numOfGames + "");
        builder.setCustomDimension(CustomDimension.THEME, themeName);
        builder.setCustomDimension(CustomDimension.CONNECT_ENABLED, isNetworkConnected() ? "True" : "False");
        internalStart(activity, appTracker(), builder, newSession);
    }

    public static void startONScripter(Activity activity) {
        startONScripter(activity, false);
    }

    public static void startONScripter(Activity activity, boolean newSession) {
        initTrackers(activity);
        HitBuilders.AppViewBuilder builder = new AppViewBuilder();

        // Get the gesture data
        boolean usesGestures = sPref.getBoolean(activity.getString(R.string.settings_controls_display_key), false);
        String gesture = "Disabled";
        if (usesGestures) {
            String[] gestureValues = activity.getResources().getStringArray(R.array.settings_controls_swipe_values);
            String value = sPref.getString(activity.getString(R.string.settings_controls_swipe_key),
                    activity.getString(R.string.settings_controls_swipe_default_value));
            if (gestureValues[1].equals(value)) {
                gesture = "Left";
            } else if (gestureValues[1].equals(value)) {
                gesture = "Right";
            } else {
                gesture = "Left & Right";
            }
        }
        builder.setCustomDimension(CustomDimension.CONNECT_ENABLED, isNetworkConnected() ? "True" : "False");
        builder.setCustomDimension(CustomDimension.CONTROL_GESTURE_TYPE, gesture);
        internalStart(activity, appTracker(), builder, newSession);
    }

    private static void internalStart(Activity activity, Tracker tracker, HitBuilders.AppViewBuilder builder, boolean newSession) {
        initTrackers(activity);
        tracker.setScreenName(getAliasName(activity.getClass().getName()));
        if (newSession) {
            builder.setNewSession();
        }
        tracker.send(builder.build());
    }

    public static void stop(Activity activity) {
        appTracker().setScreenName(null);
    }

    // Custom hits
    public static void buttonEvent(BUTTON button) {
        send(Category.CLICK, Action.BUTTON_PRESS, button.toString());
    }

    public static void changeEvent(CHANGE change, long value) {
        Action act = null;
        switch (change) {
        case TEXT_SCALE:
            act = Action.TEXT_CHANGE;
            break;
        }
        send(Category.CHANGES, act, change.toString(), value);
    }

    // Send hits
    public synchronized static void send(Category category, Action action, String label) {
        send(category, action, label, Long.MIN_VALUE);
    }

    public synchronized static void send(Category category, Action action, String label, long value) {
        if (sCurrentTracker != null) {
            HitBuilders.EventBuilder evt = new HitBuilders.EventBuilder();
            evt.setCategory(category.toString())
                .setAction(action.toString())
                .setLabel(label);
            if (value != Long.MIN_VALUE) {
                evt.setValue(value);
            }
            sCurrentTracker.send(evt.build());
        }
    }

    private static String getAliasName(String className) {
        String ret = sScreenNameAliases.get(className);
        return ret != null ? ret : className;
    }

    // Network Connection
    private static boolean isNetworkConnected() {
        NetworkInfo mWifi = sConnManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        NetworkInfo mMobile = sConnManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        boolean wifiConnected = mWifi != null ? mWifi.isConnected() : false;
        boolean mobileConnected = mMobile != null ? mMobile.isConnected() : false;
        return (wifiConnected || mobileConnected) && isNetworkAvailable();
    }

    private static boolean isNetworkAvailable() {
        NetworkInfo activeNetworkInfo = sConnManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
}
