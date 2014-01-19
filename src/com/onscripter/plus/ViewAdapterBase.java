package com.onscripter.plus;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;


public abstract class ViewAdapterBase<TItem> extends ArrayAdapter<TItem>{
    protected ArrayList<TItem> entries;
    private final Activity activity;
    private final int widgetLayout;
    private final int[] resources;

    public ViewAdapterBase(Activity a, int widgetResourceLayout, int[] viewResourceIdListInWidget, ArrayList<TItem> list) {
        super(a, 0, list);
        entries = list;
        activity = a;
        resources = viewResourceIdListInWidget;
        widgetLayout = widgetResourceLayout;
    }

    protected abstract void setWidgetValues(int position, TItem item, View[] elements, View layout);

    protected Activity getActivity() {
        return activity;
    }

    public ArrayList<TItem> getList() {
        return entries;
    }

    protected void onCreateListItem(int position, View item, ViewGroup parent) {
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View viewObj = convertView;
        View[] elements = null;
        if (viewObj == null) {
            LayoutInflater inflator = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            viewObj = inflator.inflate(widgetLayout, null);
            int size = resources.length;
            elements = new View[size];
            for (int i = 0; i < size; i++) {
                elements[i] = viewObj.findViewById(resources[i]);
            }
            viewObj.setTag(elements);
            onCreateListItem(position, viewObj, parent);
        } else {
            elements = (View[]) viewObj.getTag();
        }
        final TItem item = entries.get(position);
        for (View v: elements) {
            v.setVisibility(View.VISIBLE);
        }
        setWidgetValues(position, item, elements, viewObj);
        return viewObj;
    }
}
