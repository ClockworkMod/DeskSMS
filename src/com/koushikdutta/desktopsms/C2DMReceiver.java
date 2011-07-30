package com.koushikdutta.desktopsms;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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
    
    private void handleMessage(final Context context, Intent intent) {
        Log.i(LOGTAG, "Tickle received!");

        Settings settings = Settings.getInstance(context);
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
            else if ("outbox".equals(type)) {
                Intent serviceIntent = new Intent(context, SyncService.class);
                serviceIntent.putExtra("outbox", intent.getStringExtra("outbox"));
                serviceIntent.putExtra("reason", "outbox");
                context.startService(serviceIntent);
            }
            else if ("dial".equals(type)) {
                Intent callIntent = new Intent(Intent.ACTION_CALL);
                callIntent.setData(Uri.parse(String.format("tel:%s", intent.getStringExtra("number"))));
                callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(callIntent);
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
