package com.koushikdutta.desktopsms;


import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.clockworkmod.billing.ClockworkModBillingClient;
import com.clockworkmod.billing.ThreadingRunnable;
import com.google.android.gms.gcm.GoogleCloudMessaging;

public class GCMIntentService extends BroadcastReceiver {
    public static final String PING = "com.koushikdutta.desktopsms.PING";
    public static final String ACTION_REGISTRATION_RECEIVED = "com.koushikdutta.tabletsms.REGISTRATION_RECEIVED";

    private final static String LOGTAG = GCMIntentService.class.getSimpleName();
    
    @Override
    public void onReceive(final Context context, Intent intent) {
    	if (intent == null || !"com.google.android.c2dm.intent.RECEIVE".equals(intent.getAction()))
    		return;

        Settings settings = Settings.getInstance(context);
        try {
            String type = intent.getStringExtra("type");
            Log.i(LOGTAG, "Tickle type: " + type);
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
                    Log.i(LOGTAG, key + "=" + value);
                    settings.setString(key, value);
                }
                Intent i = new Intent(WidgetProvider.UPDATE);
                context.sendBroadcast(i);
            }
            else if ("outbox".equals(type)) {
                markAllAsRead(context);
                Intent serviceIntent = new Intent(context, SyncService.class);
                serviceIntent.putExtra("outbox", intent.getStringExtra("outbox"));
                SyncHelper.startSyncService(context, serviceIntent, "outbox");
            }
            else if ("dial".equals(type)) {
                Intent callIntent = new Intent(Intent.ACTION_CALL);
                callIntent.setData(Uri.parse(String.format("tel:%s", intent.getStringExtra("number"))));
                callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(callIntent);
            }
            else if ("ping".equals(type)) {
                Intent bcast = new Intent(PING);
                context.sendBroadcast(bcast);
            }
            else if ("log".equals(type)) {
                Helper.sendLog(intent);
            }
            else if ("read".equals(type)) {
                markAllAsRead(context);
            }
            else if ("refreshmarket".equals(type)) {
                ClockworkModBillingClient.getInstance().refreshMarketPurchases();
            }
            else if ("register-device".equals(type)) {
                String registrations = settings.getString("registrations");
                String registration = intent.getStringExtra("registration");
                String device = intent.getStringExtra("device");
                JSONObject r = null;
                try {
                    r = new JSONObject(registrations);
                }
                catch (Exception ex) {
                }
                if (r == null) {
                    r = new JSONObject();
                }
                r.put(device, registration);
                settings.setString("registrations", r.toString());
                Log.i(LOGTAG, "Registered device! " + registration);
            }
            else if ("echo".equals(type)) {
                doEcho(context, settings);
            }
            else if ("pong".equals(type)) {
                try {
                    final JSONObject envelope = new JSONObject();
                    try {
                        String registrations = settings.getString("registrations");
                        JSONObject r = new JSONObject(registrations);
                        JSONArray names = r.names();
                        JSONArray values = new JSONArray();
                        for (int i = 0; i < names.length(); i++) {
                            String name = names.getString(i);
                            String value = r.getString(name);
                            values.put(value);
                        }
                        envelope.put("registrations", values);
                    }
                    catch (Exception ex) {
                    }
                    JSONArray data = new JSONArray();
                    JSONObject sms = new JSONObject();
                    sms.put("subject", "Test successful");
                    sms.put("message", "Response received from phone.");
                    sms.put("type", "incoming");
                    sms.put("number", "DeskSMS");
                    sms.put("date", System.currentTimeMillis());
                    envelope.put("is_initial_sync", false);
                    envelope.put("version_code", DesktopSMSApplication.mVersionCode);
                    envelope.put("data", data);
                    data.put(sms);
                    final String account = settings.getString("account");
                    ThreadingRunnable.background(new ThreadingRunnable() {
                        @Override
                        public void run() {
                            try {
                                ServiceHelper.retryExecuteAsJSONObject(context, account, new URL(String.format(ServiceHelper.SMS_URL, account)), new ServiceHelper.JSONPoster(envelope));
                            }
                            catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                    });
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }


    private void markAllAsRead(Context context) {
        Uri contentProviderUri = Uri.parse("content://sms");
        Cursor c = context.getContentResolver().query(contentProviderUri, new String[] { "_id" }, "read = 0", null, null);;
        try {
            int idColumn = c.getColumnIndex("_id");
            ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
            while (c.moveToNext()) {
                int id = c.getInt(idColumn);
                ContentProviderOperation op = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(contentProviderUri, id))
                        .withValue("read", 1).build();
                ops.add(op);
            }

            context.getContentResolver().applyBatch("sms", ops);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        finally {
            if (c != null)
                c.close();
        }
    }

    public static void doEcho(final Context context, final Settings settings) {

        ThreadingRunnable.background(new ThreadingRunnable() {
            @Override
            public void run() {
                final String account = settings.getString("account");
                String registrations = settings.getString("registrations");
                try {
                    JSONObject r = new JSONObject(registrations);
                    JSONArray names = r.names();
                    for (int i = 0; i < names.length(); i++) {
                        try {
                            String name = names.getString(i);
                            String registration = r.getString(name);
                            URL url = new URL(ServiceHelper.PUSH_URL + "?type=ping&registration=" + URLEncoder.encode(registration));
                            JSONObject result = ServiceHelper.retryExecuteAsJSONObject(context, account, url, null);
                            if (!result.optBoolean("success", false)) {
                                r.remove(registration);
                                Log.i(LOGTAG, "Purging due to failure: " + registration);
                            }
                        }
                        catch (Exception ex) {
                        }
                    }
                    registrations = r.toString();
                }
                catch (Exception ex) {
                }
                finally {
                    settings.setString("registrations", registrations);
                }
            }
        });
    }
}