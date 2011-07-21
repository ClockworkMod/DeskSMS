package com.koushikdutta.desktopsms;

import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SmsManager;
import android.util.Log;

public class C2DMReceiver extends BroadcastReceiver {
    private final static String LOGTAG = C2DMReceiver.class.getSimpleName();
    public static final String ACTION_REGISTRATION_RECEIVED = "com.koushikdutta.desktopsms.REGISTRATION_RECEIVED";
    
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("com.google.android.c2dm.intent.REGISTRATION")) {
            handleRegistration(context, intent);
        } else if (intent.getAction().equals("com.google.android.c2dm.intent.RECEIVE")) {
            handleMessage(context, intent);
         }
     }
    
    void sendUsingContentProvider(Context context, String number, String message) throws Exception {
        ContentResolver r = context.getContentResolver();
        //ContentProviderClient client = r.acquireContentProviderClient(Uri.parse("content://sms/queued"));
        ContentValues sv = new ContentValues();
        sv.put("address", number);
        sv.put("date", System.currentTimeMillis());
        sv.put("read", 1);
        sv.put("body", message);
        String n = null;
        sv.put("subject", n);
        //sv.put("status", 32);
        Uri u = r.insert(Uri.parse("content://sms/queued"), sv);
        if (u == null) {
            throw new Exception();
        }
        Intent bcast = new Intent("com.android.mms.transaction.SEND_MESSAGE");
        bcast.setClassName("com.android.mms", "com.android.mms.transaction.SmsReceiverService");
        context.startService(bcast);
    }
    
    void sendUsingSmsManager(Context context, String number, String message) {
        SmsManager sm = SmsManager.getDefault();
        ArrayList<String> messages = sm.divideMessage(message);
        int messageCount = messages.size();
        if (messageCount == 0)
            return;

        sm.sendMultipartTextMessage(number, null, messages, null, null);
        ContentValues values = new ContentValues();
        values.put("address", number);
        values.put("body", message);
        context.getContentResolver().insert(Uri.parse("content://sms/sent"), values);
    }
    
    private void sendOutbox(final Context context, String outboxData) {
        Settings settings = Settings.getInstance(context);
        final long lastOutboxSync = settings.getLong("last_outbox_sync", 0);
        long maxOutboxSync = lastOutboxSync;
        final String account = settings.getString("account");
        try {
            // the outbox MUST come in order, from the oldest to the newest.
            // TODO: this should be sorted just to sanity check I guess.
            JSONArray outbox = new JSONArray(outboxData);
            //Log.i(LOGTAG, outbox.toString(4));
            for (int i = 0; i < outbox.length(); i++) {
                try {
                    JSONObject sms = outbox.getJSONObject(i);
                    String number = sms.getString("number");
                    String message = sms.getString("message");
                    // make sure that any messages we get are new messages
                    long date = sms.getLong("date");
                    if (date <= lastOutboxSync)
                        continue;
                    //Log.i(LOGTAG, sms.toString(4));
                    maxOutboxSync = Math.max(maxOutboxSync, date);
                    sendUsingSmsManager(context, number, message);
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            settings.setLong("last_outbox_sync", maxOutboxSync);
            
            final long max = maxOutboxSync;
            new Thread() {
                public void run() {
                    AndroidHttpClient client = AndroidHttpClient.newInstance(context.getString(R.string.app_name) + "." + DesktopSMSApplication.mVersionCode);
                    try {
                        HttpDelete delete = new HttpDelete(String.format(ServiceHelper.OUTBOX_URL, account) + "?max_date=" + max);
                        ServiceHelper.retryExecute(context, account, client, delete);
                    }
                    catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    finally {
                        client.close();
                    }
                }
            }.start();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void handleMessage(final Context context, Intent intent) {
        Log.i(LOGTAG, "Tickle received!");

        Settings settings = Settings.getInstance(context);
        int proxied = settings.getInt("proxied", 0);
        try {
            String type = intent.getStringExtra("type");
            if ("notification".equals(type)) {
                String ticker = intent.getStringExtra("ticker");
                String title = intent.getStringExtra("title");
                String text = intent.getStringExtra("text");
                String pkg = intent.getStringExtra("package");
                String cls = intent.getStringExtra("class");
                String data = intent.getStringExtra("data");
                NotificationManager nm = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
                Notification n = new Notification(R.drawable.icon, ticker, System.currentTimeMillis());
                Intent i = new Intent();
                if (!Helper.isJavaScriptNullOrEmpty(data)) {
                    i.setData(Uri.parse(data));
                }
                else {
                    i.setClassName(pkg, cls);
                }
                PendingIntent pi = PendingIntent.getActivity(context, 0, i, 0);
                n.setLatestEventInfo(context, title, text, pi);
                nm.notify(1, n);
            }
            else if ("settings".equals(type)) {
                Bundle extras = intent.getExtras();
                for (String key: extras.keySet()) {
                    if ("type".equals(key))
                        continue;
                    String value = extras.getString(key);
                    settings.setString(key, value);
                }
                Intent i = new Intent(WidgetProvider.UPDATE);
                context.sendBroadcast(i);
            }
            else if (type == null || "message".equals("type")) {
                String number = intent.getStringExtra("to");
                String message = intent.getStringExtra("message");
                //sm.sendTextMessage(number, null, message, null, null);

                //sendUsingContentProvider(context, number, message);
                sendUsingSmsManager(context, number, message);

                proxied++;
                settings.setInt("proxied", proxied);
            }
            else if ("outbox".equals(type)) {
                final long lastOutboxSync = settings.getLong("last_outbox_sync", 0);
                String data = intent.getStringExtra("outbox");
                final String account = settings.getString("account");
                final Handler handler = new Handler();
                if (data == null) {
                    new Thread() {
                        public void run() {
                            AndroidHttpClient client = AndroidHttpClient.newInstance(context.getString(R.string.app_name) + "." + DesktopSMSApplication.mVersionCode);
                            try {
                                HttpGet get = new HttpGet(String.format(ServiceHelper.OUTBOX_URL, account) + "?min_date=%s" + lastOutboxSync);
                                ServiceHelper.addAuthentication(context, get);
                                HttpResponse res = client.execute(get);
                                final String data = StreamUtility.readToEnd(res.getEntity().getContent());
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        sendOutbox(context, data);
                                    }
                                });
                            }
                            catch (Exception ex) {
                                ex.printStackTrace();
                            }
                            finally {
                                client.close();
                            }
                        }
                    }.start();
                }
                else {
                    sendOutbox(context, data);
                }
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        /*
        for (String key: intent.getExtras().keySet()) {
            Log.i(LOGTAG, key + ": " + intent.getStringExtra(key));
        }
        */
    }

    private void handleRegistration(Context context, Intent intent) {
        String registration = intent.getStringExtra("registration_id"); 
        if (intent.getStringExtra("error") != null) {
            // Registration failed, should try again later.
        	Log.i(LOGTAG, intent.getStringExtra("error"));
        } else if (intent.getStringExtra("unregistered") != null) {
            // unregistration done, new messages from the authorized sender will be rejected
        } else if (registration != null) {
           Log.i(LOGTAG, registration);
           Settings settings = Settings.getInstance(context);
           settings.setString("registration_id", registration);
           
           Intent i = new Intent(ACTION_REGISTRATION_RECEIVED);
           context.sendBroadcast(i);
        }
    }
}
