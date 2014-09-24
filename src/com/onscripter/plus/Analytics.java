package com.onscripter.plus;

import java.io.IOException;
import java.util.HashMap;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.XmlResourceParser;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.HitBuilders.AppViewBuilder;
import com.google.android.gms.analytics.Tracker;

public final class Analytics {
    public enum TrackerType { APP_TRACKER, GLOBAL_TRACKER };

    public enum Category {
        CLICK("Click"),
        CHANGES("Changes"),
        LAUNCHER_STATE("Launcher State"),
        GAME_STATE("Game State"),
        FEATURE("Feature"),
        GAMES("Games");

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
        TEXT_CHANGE("Text"),
        NETWORK_CONNECTED("Network Connected"),
        THEME_USED("Theme Used"),
        ADBLOCKER_USED("Adblocker Used"),
        ACTIVITY_FINISHED("Activity Finished"),
        VIDEO_LAUNCHED("Video Launched"),
        GAME_NAMES("Name of ONScripter Games");

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

    public enum ACTIVITY_END {
        WIFI_ENABLED("Wifi is Enabled"),
        WIFI_DISABLED("Wifi is Disabled"),
        NUMBER_OF_GAMES("Number of Games"),
        SESSION_LENGTH("Session Length"),
        WHITE_THEME("White theme"),
        BLACK_THEME("Black theme"),
        ADBLOCKER_ENABLED("Adblocker Enabled"),
        ADBLOCKER_DISABLED("Adblocker Disabled");

        private final String mVal;
        private ACTIVITY_END(final String s) {
            mVal = s;
        }
        @Override
        public String toString() {
            return mVal.trim();
        }
    };

    static final private String PROPERTY_ID = "UA-39733898-3";

    static private HashMap<TrackerType, Tracker> sTrackers = new HashMap<TrackerType, Tracker>();
    static private HashMap<String, String> sScreenNameAliases = new HashMap<String, String>();

    static private Tracker sCurrentTracker = null;
    static private ConnectivityManager sConnManager = null;

    synchronized static void initTrackers(Context ctx) {
        if (sTrackers.isEmpty()) {
            GoogleAnalytics analytics = GoogleAnalytics.getInstance(ctx);

            // For debug mode, do not send any analytics
            if ((ctx.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                analytics.setDryRun(true);
            }
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
        initTrackers(activity);
        Tracker tracker = appTracker();
        tracker.setScreenName(getAliasName(activity.getClass().getName()));
        AppViewBuilder builder = new HitBuilders.AppViewBuilder();
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

    public static void sendWifiEnabledEvent(Activity act) {
        Category cate = getStateCategoryByActivity(act);
        if (cate != null) {
            send(cate, Action.NETWORK_CONNECTED, (isNetworkConnected() ?
                    ACTIVITY_END.WIFI_ENABLED : ACTIVITY_END.WIFI_DISABLED).toString());
        }
    }

    public static void sendSessionLength(Activity act, long length) {
        Category cate = getStateCategoryByActivity(act);
        if (cate != null) {
            send(cate, Action.ACTIVITY_FINISHED, ACTIVITY_END.SESSION_LENGTH.toString(), length);
        }
    }

    public static void sendLauncherTheme(boolean isWhiteTheme) {
        send(Category.LAUNCHER_STATE, Action.THEME_USED, (isWhiteTheme ?
                ACTIVITY_END.WHITE_THEME : ACTIVITY_END.BLACK_THEME).toString());
    }

    public static void sendNumberOfGames(long numOfGames) {
        send(Category.LAUNCHER_STATE, Action.ACTIVITY_FINISHED, ACTIVITY_END.NUMBER_OF_GAMES.toString(), numOfGames);
    }

    public static void sendAdblockerUsed(Activity act) {
        final Category cate = getStateCategoryByActivity(act);
        if (cate != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    send(cate, Action.ADBLOCKER_USED, (Adblocker.check() ?
                            ACTIVITY_END.ADBLOCKER_ENABLED : ACTIVITY_END.ADBLOCKER_DISABLED).toString());
                }
            }).start();
        }
    }

    public static void sendVideoLaunched(String videoPath) {
        send(Category.FEATURE, Action.VIDEO_LAUNCHED, videoPath);
    }

    public static void sendGameName(String name) {
        send(Category.GAMES, Action.GAME_NAMES, name);
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

    private static Category getStateCategoryByActivity(Activity act) {
        Category cate = null;
        if (act instanceof LauncherActivity) {
            cate = Category.LAUNCHER_STATE;
        } else if (act instanceof ONScripter) {
            cate = Category.GAME_STATE;
        }
        return cate;
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
