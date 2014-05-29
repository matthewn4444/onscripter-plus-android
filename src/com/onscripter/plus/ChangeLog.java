package com.onscripter.plus;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.TextView;

public class ChangeLog {
    private AlertDialog mDialog;
    private final Activity mCtx;
    private final SharedPreferences mPref;
    private ListView mList;
    private static String PREF_KEY = null;
    private static String BULLET_POINT = null;

    public ChangeLog(Activity ctx) {
        mPref = PreferenceManager.getDefaultSharedPreferences(ctx);
        mCtx = ctx;
        if (PREF_KEY == null) {
            PREF_KEY = ctx.getString(R.string.change_log_key);
            BULLET_POINT = ctx.getString(R.string.bullet_point);
        }
        LauncherActivity.log(1);
        long lastDate = mPref.getLong(PREF_KEY, 0);
        try {
            ApplicationInfo appInfo = ctx.getPackageManager()
                    .getApplicationInfo(ctx.getPackageName(), 0);
            String appFile = appInfo.sourceDir;
            LauncherActivity.log(1);
            long installed = new File(appFile).lastModified();
            LauncherActivity.log(lastDate < installed, lastDate, installed);
            if (lastDate < installed) {
                // Show the change log
                show();
            }
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void show() {
        if (mDialog == null) {
            AlertDialog.Builder b = new Builder(mCtx);
            b.setNeutralButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            setChangeLogTimestamp();
                        }
                    });
            mList = new ListView(mCtx);
            b.setPositiveButton(R.string.dialog_button_rate,
                    new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    showThisAppInMarket();
                    setChangeLogTimestamp();
                }
            });
            b.setNegativeButton(R.string.change_log_no_ads,
                    new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    showAppInMarket("com.onscripter.pluspro");
                    setChangeLogTimestamp();
                }
            });

            LinearLayout layout = new LinearLayout(mCtx);
            layout.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            int padding = (int)mCtx.getResources().getDimension(R.dimen.change_log_padding);
            layout.setPadding(padding, padding, padding, padding);
            layout.setOrientation(LinearLayout.VERTICAL);

            // If displaying English, then add the message to add translation
            String lang = Locale.getDefault().getLanguage();
            if (!lang.equals(Locale.JAPANESE.toString()) && !lang.equals(Locale.KOREAN.toString())) {
                layout.setPadding(padding, padding, padding, padding);
                TextView emailText = new TextView(mCtx);
                TextView translationText = new TextView(mCtx);
                emailText.setMovementMethod(LinkMovementMethod.getInstance());
                String email = mCtx.getString(R.string.email);
                emailText.setText(Html.fromHtml("<a href=\"" + email + "\">" + email + "</a>"));
                translationText.setText(Html.fromHtml(mCtx.getString(R.string.dialog_add_translation)));
                layout.addView(translationText);
                layout.addView(emailText);
            } else {
                layout.setPadding(padding, 0, padding, 0);
            }
            layout.addView(mList);
            b.setView(layout);
            b.setTitle(R.string.change_log_dialog_title);
            b.setCancelable(false);
            buildContents();
            mDialog = b.create();
        }
        mDialog.show();
    }

    public void hide() {
        if (mDialog != null) {
            mDialog.hide();
        }
    }

    private void showAppInMarket(String packageName) {
        try {
            mCtx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName)));
        } catch (android.content.ActivityNotFoundException anfe) {
            mCtx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + packageName)));
        }
    }

    private void showThisAppInMarket() {
        final String appPackageName = mCtx.getApplicationContext().getPackageName();
        showAppInMarket(appPackageName);
    }

    private void setChangeLogTimestamp() {
        Editor editor = mPref.edit();
        editor.putLong(PREF_KEY, Calendar.getInstance().getTimeInMillis());
        editor.commit();
    }

    private void buildContents() {
        ArrayList<Entry> entries = new ArrayList<ChangeLog.Entry>();
        String[] info = mCtx.getResources().getStringArray(R.array.change_log);

        // Build the list of entries
        Entry currentEntry = null;
        for (int i = 0; i < info.length; i++) {
            String line = info[i];
            if (line.startsWith("v")) {
                if (currentEntry != null) {
                    entries.add(currentEntry);
                }

                // Parse new version number
                currentEntry = new Entry();
                String[] items = line.split(",");
                currentEntry.versionName = items[0].substring(1, items[0].length());
                currentEntry.dateStr = items[1];
            } else if (currentEntry != null) {
                currentEntry.changeList.add(line);
            }
        }
        if (currentEntry != null) {
            entries.add(currentEntry);
        }

        ChangeLogAdapter adapter = new ChangeLogAdapter(mCtx, entries);
        mList.setAdapter(adapter);
        mList.setSelector(android.R.color.transparent);
    }

    private class Entry {
        public String dateStr;
        public String versionName;
        public ArrayList<String> changeList = new ArrayList<String>();
    }

    private static int[] WIDGET_IDS = { R.id.version, R.id.date, R.id.data };

    private class ChangeLogAdapter extends ViewAdapterBase<Entry> {
        public ChangeLogAdapter(Activity a, ArrayList<Entry> list) {
            super(a, R.layout.change_log_entry, WIDGET_IDS, list);
        }

        @Override
        protected void setWidgetValues(int position, Entry item,
                View[] elements, View layout) {
            ((TextView)elements[0]).setText(item.versionName);
            ((TextView)elements[1]).setText(item.dateStr);

            // Build the list of changes
            if (item.changeList.size() > 0) {
                StringBuilder sb = new StringBuilder();
                sb.append('\t').append(BULLET_POINT).append(' ').append(item.changeList.get(0));
                for (int i = 1; i < item.changeList.size(); i++) {
                    sb.append('\n').append('\t').append(BULLET_POINT).append(' ')
                            .append(item.changeList.get(i));
                }
                ((TextView)elements[2]).setText(sb);
                elements[2].setVisibility(View.VISIBLE);
            } else {
                elements[2].setVisibility(View.GONE);
            }
        }
    }
}
