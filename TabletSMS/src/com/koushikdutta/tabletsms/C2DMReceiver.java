package com.koushikdutta.tabletsms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class C2DMReceiver extends BroadcastReceiver {
    private final static String LOGTAG = C2DMReceiver.class.getSimpleName();
    public static final String ACTION_REGISTRATION_RECEIVED = "com.koushikdutta.tabletsms.REGISTRATION_RECEIVED";
    public static final String PING = "com.koushikdutta.desktopsms.PING";

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
            if ("ping".equals(type)) {
                Intent bcast = new Intent(PING);
                context.sendBroadcast(bcast);
            }
            else if ("refresh".equals(type)) {
                intent.setClass(context, SyncService.class);
                context.startService(intent);
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void handleRegistration(final Context context, Intent intent) {
        String registration = intent.getStringExtra("registration_id");
        if (intent.getStringExtra("error") != null) {
            // Registration failed, should try again later.
            Log.i(LOGTAG, intent.getStringExtra("error"));
        }
        else if (intent.getStringExtra("unregistered") != null) {
            // unregistration done, new messages from the authorized sender will be rejected
        }
        else if (registration != null) {
            Log.i(LOGTAG, registration);
            Settings settings = Settings.getInstance(context);

            String oldRegistrationId = settings.getString("registration_id");
            settings.setString("registration_id", registration);

            // if the registration ids do not match, and we are registered, notify the server.
            if (oldRegistrationId != null && !oldRegistrationId.equals(registration) && settings.getBoolean("registered", false)) {
                Log.i(LOGTAG, oldRegistrationId);
                Log.i(LOGTAG, "Registration change detected!");
                ThreadingRunnable.background(new ThreadingRunnable() {
                    @Override
                    public void run() {
                        try {
                            TickleServiceHelper.registerWithServer(context);
                        }
                        catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                });
            }

            Intent i = new Intent(ACTION_REGISTRATION_RECEIVED);
            context.sendBroadcast(i);
        }
    }
}
