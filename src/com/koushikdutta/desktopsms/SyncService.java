package com.koushikdutta.desktopsms;

import java.net.URI;
import java.util.ArrayList;
import java.util.Hashtable;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;

public class SyncService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    static interface CursorGetter {
        void get(Cursor c, JSONObject j, String name, int index) throws JSONException;
    }
    
    static class TypeMapper extends Hashtable<Integer, String> {
        {
            put(2, "outgoing");
            put(1, "incoming");
        }
        
        public static TypeMapper Instance = new TypeMapper();
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
            put(boolean.class, new CursorGetter() {
                @Override
                public void get(Cursor c, JSONObject j, String name, int index) throws JSONException {
                    j.put(name, c.getInt(index) != 0);
                }
            });
            put(TypeMapper.class, new CursorGetter() {
                @Override
                public void get(Cursor c, JSONObject j, String name, int index) throws JSONException {
                    j.put(name, TypeMapper.Instance.get(c.getInt(index)));
                }
            });
        }
    };
    
    static Hashtable<String, Tuple<String, CursorGetter>> smsmapper = new Hashtable<String, Tuple<String, CursorGetter>>() {
        {
            put("body", new Tuple<String, CursorGetter>("message", mapper.get(String.class)));
            //put("seen", new Tuple<String, CursorGetter>("seen", mapper.get(int.class)));
            put("type", new Tuple<String, CursorGetter>("type", mapper.get(TypeMapper.class)));
            put("date", new Tuple<String, CursorGetter>("date", mapper.get(long.class)));
            put("_id", new Tuple<String, CursorGetter>("id", mapper.get(long.class)));
            put("address", new Tuple<String, CursorGetter>("number", mapper.get(String.class)));
            put("read", new Tuple<String, CursorGetter>("read", mapper.get(boolean.class)));
            put("thread_id", new Tuple<String, CursorGetter>("thread_id", mapper.get(long.class)));
        }
    };
    
    private static final String SMS_URI = "https://2.desksms.appspot.com/api/v1/user/%s/sms";
    private void syncInternal() {
        try {
            Settings settings = Settings.getInstance(this);
            String account = settings.getString("account");
            
            long lastSmsSync = settings.getLong("last_sms_sync", System.currentTimeMillis() - 3L * 24L * 60L * 60L * 1000L);
            Cursor c = getContentResolver().query(Uri.parse("content://sms"), null, "date > ?", new String[] { String.valueOf(lastSmsSync) }, null);
            
            String[] columnNames = c.getColumnNames();
            JSONArray smsArray = new JSONArray();
            while (c.moveToNext()) {
                try {
                    JSONObject sms = new JSONObject();
                    for (int i = 0; i < c.getColumnCount(); i++) {
                        String name = columnNames[i];
                        Tuple<String, CursorGetter> tuple = smsmapper.get(name);
                        if (tuple == null)
                            continue;
                        tuple.Second.get(c, sms, tuple.First, i);
                    }
                    smsArray.put(sms);
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            
            ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("data", smsArray.toString()));
            HttpPost post = ServiceHelper.getAuthenticatedPost(this, new URI(String.format(SMS_URI, account)), params);
            DefaultHttpClient client = new DefaultHttpClient();
            HttpResponse res = client.execute(post);
            String results = StreamUtility.readToEnd(res.getEntity().getContent());
            System.out.println(results);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        finally {
            mHandler.post(new Runnable() {
               @Override
                public void run() {
                   mSyncThread = null;
                } 
            });
        }
    }

    Handler mHandler = new Handler();
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
