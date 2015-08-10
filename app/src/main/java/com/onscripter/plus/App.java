package com.onscripter.plus;

import android.app.Application;
import android.content.Context;

public final class App extends Application {
    private static Context StaticContext;

    @Override
    public void onCreate() {
        super.onCreate();
        StaticContext = this;
    }

    public static Context getContext() {
        return StaticContext;
    }

    public static String string(int resId) {
        return StaticContext.getString(resId);
    }
}
