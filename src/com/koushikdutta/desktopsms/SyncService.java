package com.koushikdutta.desktopsms;

import java.util.Hashtable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;

public class SyncService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    static interface CursorGetter {
        void get(Cursor c, JSONObject j, String name, int index) throws JSONException;
    }
    
    static Hashtable<Class, CursorGetter> mapper = new Hashtable<Class, SyncService.CursorGetter>() {
        {
            put(int.class, new CursorGetter() {
                @Override
                public void get(Cursor c, JSONObject j, String name, int index) throws JSONException {
                    j.put(name, c.getInt(index));
                }
            });
            put(long.class, new CursorGetter() {
                @Override
                public void get(Cursor c, JSONObject j, String name, int index) throws JSONException {
                    j.put(name, c.getInt(index));
                }
            });
            put(String.class, new CursorGetter() {
                @Override
                public void get(Cursor c, JSONObject j, String name, int index) throws JSONException {
                    j.put(name, c.getString(index));
                }
            });
        }
    };
    
    static Hashtable<String, CursorGetter> smsmapper = new Hashtable<String, SyncService.CursorGetter>() {
        {
            put("body", mapper.get(String.class));
            put("seen", mapper.get(int.class));
            put("type", mapper.get(int.class));
            put("date", mapper.get(long.class));
            put("_id", mapper.get(long.class));
            put("address", mapper.get(String.class));
            put("read", mapper.get(int.class));
            put("thread_id", mapper.get(long.class));
        }
    };
    
    private void syncInternal() {
        Settings settings = Settings.getInstance(this);
        
        try {
            long lastSmsSync = settings.getLong("last_sms_sync", System.currentTimeMillis() - 3L * 24L * 60L * 60L * 1000L);
            Cursor c = getContentResolver().query(Uri.parse("content://sms"), null, "date > ?", new String[] { String.valueOf(lastSmsSync) }, null);
            
            String[] columnNames = c.getColumnNames();
            JSONArray smsArray = new JSONArray();
            while (c.moveToNext()) {
                JSONObject sms = new JSONObject();
                for (int i = 0; i < c.getColumnCount(); i++) {
                    String name = columnNames[i];
                    CursorGetter getter = smsmapper.get(name);
                    if (getter == null)
                        continue;
                    getter.get(c, sms, name, i);
                }
                smsArray.put(sms);
            }
            System.out.println(smsArray.toString(4));
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    Thread mSyncThread = null;
    private void sync() {
        if (mSyncThread != null)
            return;

        mSyncThread = new Thread() {
            @Override
            public void run() {
                syncInternal();
            }
        };
        mSyncThread.start();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        sync();
        return super.onStartCommand(intent, flags, startId);
    }
}
