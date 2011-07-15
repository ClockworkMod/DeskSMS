package com.koushikdutta.desktopsms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
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

    private void handleMessage(Context context, Intent intent) {
        Log.i(LOGTAG, "Tickle received!");

        Settings settings = Settings.getInstance(context);
        int proxied = settings.getInt("proxied", 0);
        try {
            String number = intent.getStringExtra("to");
            String message = intent.getStringExtra("message");
            SmsManager sm = SmsManager.getDefault();
            sm.sendTextMessage(number, null, message, null, null);
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
