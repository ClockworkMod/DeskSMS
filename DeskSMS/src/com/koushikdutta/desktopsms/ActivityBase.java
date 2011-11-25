package com.koushikdutta.desktopsms;

import java.util.HashMap;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class ActivityBase extends Activity {
    protected Settings mSettings;
    boolean mDestroyed = false;
    
    private final static String LOGTAG = ActivityBase.class.getSimpleName();

    ListView mListView;
    MyAdapter mAdapter;
    
    class MyAdapter extends SeparatedListAdapter {
        public MyAdapter(Context context) {
            super(context);
        }
        
        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }
        
        @Override
        public boolean isEnabled(int position) {
            if (!super.isEnabled(position))
                return false;
            ListItem item = (ListItem) getItem(position);
            return item.Enabled;
        }
    }
    
    class MyListAdapter extends ArrayAdapter<ListItem> {

        public MyListAdapter(Context context) {
            super(context, 0);
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ListItem item = getItem(position);
            return item.getView(ActivityBase.this, convertView);
        }
        
        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }
        
        @Override
        public boolean isEnabled(int position) {
            ListItem item = getItem(position);
            return item.Enabled;
        }
    }

    HashMap<Integer, MyListAdapter> mAdapters = new HashMap<Integer, ActivityBase.MyListAdapter>();

    protected MyListAdapter ensureHeader(int sectionName) {
        MyListAdapter adapter = mAdapters.get(sectionName);
        if (adapter == null) {
            adapter = new MyListAdapter(this);
            mAdapters.put(sectionName, adapter);
            mAdapter.addSection(getString(sectionName), adapter);
            mListView.setAdapter(null);
            mListView.setAdapter(mAdapter);
        }
        return adapter;
    }

    protected ListItem addItem(int sectionName, ListItem item) {
        MyListAdapter adapter = mAdapters.get(sectionName);
        if (adapter == null) {
            adapter = new MyListAdapter(this);
            mAdapters.put(sectionName, adapter);
            mAdapter.addSection(getString(sectionName), adapter);
            mListView.setAdapter(null);
            mListView.setAdapter(mAdapter);
            //mAdapter.notifyDataSetChanged();
        }
        
        adapter.add(item);
        
        return item;
    }
    
    protected ListItem findItem(int item) {
        String text = getString(item);
        
        for (Adapter adapter: mAdapter.sections.values())
        {
            MyListAdapter m = (MyListAdapter)adapter;
            for (int i = 0; i < m.getCount(); i++) {
                ListItem li = m.getItem(i);
                if (text.equals(li.Title))
                    return li;
            }
        }
        
        return null;
    }

    protected boolean allowThemeOverride() {
        return true;
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= 11 && allowThemeOverride())
            setTheme(android.R.style.Theme_Holo);
        
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_base);
        mListView = (ListView)findViewById(R.id.listview);
        mListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View view, int arg2, long arg3) {
                ListItem li = (ListItem)mAdapter.getItem(arg2);
                li.onClickInternal(view);
                li.onClick(view);
            }
        });
        
        mListView.setOnItemLongClickListener(new OnItemLongClickListener() {

            @Override
            public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                ListItem li = (ListItem)mAdapter.getItem(arg2);
                return li.onLongClick();
            }
        });
        
        mAdapter = new MyAdapter(this);
        mListView.setAdapter(mAdapter);
        
        mSettings = Settings.getInstance(this);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDestroyed = true;
    }
    
    public int getListItemResource() {
        return R.layout.list_item;
    }
}
