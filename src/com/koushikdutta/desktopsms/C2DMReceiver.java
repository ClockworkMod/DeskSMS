package com.koushikdutta.desktopsms;

import java.util.ArrayList;

import android.content.BroadcastReceiver;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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

    private void handleMessage(Context context, Intent intent) {
        Log.i(LOGTAG, "Tickle received!");

        Settings settings = Settings.getInstance(context);
        int proxied = settings.getInt("proxied", 0);
        try {
            String number = intent.getStringExtra("to");
            String message = intent.getStringExtra("message");
            //sm.sendTextMessage(number, null, message, null, null);

            //sendUsingContentProvider(context, number, message);
            sendUsingSmsManager(context, number, message);

            proxied++;
            settings.setInt("proxied", proxied);
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
