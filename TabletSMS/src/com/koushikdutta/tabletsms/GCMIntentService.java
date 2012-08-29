package com.koushikdutta.tabletsms;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gcm.GCMBaseIntentService;

public class GCMIntentService extends GCMBaseIntentService {
    public static final String PING = "com.koushikdutta.desktopsms.PING";
    public static final String ACTION_REGISTRATION_RECEIVED = "com.koushikdutta.tabletsms.REGISTRATION_RECEIVED";

    private final static String LOGTAG = GCMIntentService.class.getSimpleName();

    @Override
    protected void onError(Context arg0, String error) {
        Log.i(LOGTAG, error);
    }

    @Override
    protected void onMessage(Context context, Intent intent) {
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

    @Override
    protected void onRegistered(final Context context, String registration) {
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

    @Override
    protected void onUnregistered(Context arg0, String arg1) {
    }

}
