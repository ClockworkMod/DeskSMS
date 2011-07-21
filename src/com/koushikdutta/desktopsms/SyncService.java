package com.koushikdutta.desktopsms;

import java.net.URI;
import java.util.ArrayList;
import java.util.Hashtable;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.os.Handler;
import android.os.IBinder;
import android.provider.ContactsContract.PhoneLookup;
import android.util.Log;

public class SyncService extends Service {
    private static final String LOGTAG = SyncService.class.getSimpleName();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    static interface CursorGetter {
        void get(Cursor c, JSONObject j, String name, int index) throws JSONException;
    }
    
    public static final int INCOMING_SMS = 1;
    public static final int OUTGOING_SMS = 2;
    
    static class TypeMapper extends Hashtable<Integer, String> {
        {
            put(OUTGOING_SMS, "outgoing");
            put(INCOMING_SMS, "incoming");
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
                    j.put(name, c.getLong(index));
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
    
    String getDisplayName(String number) {
        try {
            String displayName = mDisplayNames.get(number);
            if (displayName != null)
                return displayName;
            Uri curi = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
            Cursor c = getContentResolver().query(curi, new String[] { PhoneLookup.DISPLAY_NAME }, null, null, null);
            
            if (c != null) {
                if (c.moveToNext()) {
                    displayName = c.getString(c.getColumnIndex(PhoneLookup.DISPLAY_NAME));
                    if (!Helper.isJavaScriptNullOrEmpty(displayName)) {
                        mDisplayNames.put(number, displayName);
                        return displayName;
                    }
                }
                c.close();
            }
        }
        catch (Exception ex) {
        }
        return null;
    }
    
    Hashtable<String, String> mDisplayNames = new Hashtable<String, String>();
    
    private void syncInternal() {
        try {
            Settings settings = Settings.getInstance(this);
            String account = settings.getString("account");
            boolean sync = settings.getBoolean("sync_sms", false);

            // we may beat the provider to the punch, so let's try a few times
            for (int poll = 0; poll < 10; poll++) {
                Thread.sleep(1000);
                
                long lastSmsSync = settings.getLong("last_sms_sync", 0);
                boolean isInitialSync = false;
                if (lastSmsSync == 0) {
                    isInitialSync = true;
                    // only grab 3 days worth
                    lastSmsSync = System.currentTimeMillis() - 3L * 24L * 60L * 60L * 1000L;
                }
                Cursor c = getContentResolver().query(Uri.parse("content://sms"), null, "date > ?", new String[] { String.valueOf(lastSmsSync) }, null);
                
                String[] columnNames = c.getColumnNames();
                JSONArray smsArray = new JSONArray();
                long latestSms = lastSmsSync;
                int dateColumn = c.getColumnIndex("date");
                int addressColumn = c.getColumnIndex("address");
                int typeColumn = c.getColumnIndex("type");
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
                        // only incoming SMS needs to be marked up with the display name and subject
                        if (c.getInt(typeColumn) == INCOMING_SMS) {
                            String number = c.getString(addressColumn);
                            String displayName = getDisplayName(number);
                            if (displayName != null)
                                sms.put("name", displayName);
                            else
                                displayName = number;
                            sms.put("subject", getString(R.string.sms_received, displayName));
                        }
                        else {
                            // if we are not syncing, do not bother sending the outgoing message history
                            if (!sync)
                                continue;
                        }
                        smsArray.put(sms);
                        long date = c.getLong(dateColumn);
                        latestSms = Math.max(date, latestSms);
                    }
                    catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                
                if (smsArray.length() == 0) {
                    Log.i(LOGTAG, "No new messages");
                    continue;
                }
                
                JSONObject envelope = new JSONObject();
                envelope.put("is_initial_sync", isInitialSync);
                envelope.put("data", smsArray);
                envelope.put("version_code", DesktopSMSApplication.mVersionCode);
                envelope.put("sync", sync);
                System.out.println(envelope.toString(4));
                StringEntity entity = new StringEntity(envelope.toString());
                HttpPost post = ServiceHelper.getAuthenticatedPost(this, String.format(ServiceHelper.SMS_URL, account));
                post.setEntity(entity);
                //AndroidHttpClient client = AndroidHttpClient.newInstance(getString(R.string.app_name) + "." + DesktopSMSApplication.mVersionCode);
                DefaultHttpClient client = new DefaultHttpClient();
                try {
                    HttpResponse res = ServiceHelper.retryExecute(this, account, client, post);
                    if (res == null)
                        throw new Exception("Unable to authenticate");
                    String results = StreamUtility.readToEnd(res.getEntity().getContent());
                    System.out.println(results);
                    settings.setLong("last_sms_sync", latestSms);
                }
                finally {
                    //client.close();
                }
                break;
            }
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
