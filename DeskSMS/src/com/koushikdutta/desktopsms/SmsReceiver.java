package com.koushikdutta.desktopsms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        try {
            Helper.startSyncService(context);

            /*
            Settings settings = Settings.getInstance(context);
            boolean disable = settings.getBoolean("disable_sms_notifications", false);
            if (!disable)
                return;

            boolean xmpp = settings.getBoolean("forward_xmpp", true);
            boolean email = settings.getBoolean("forward_email", true);
            boolean web = settings.getBoolean("forward_web", true);

            if (!xmpp && !email && !web)
                return;

            abortBroadcast();
            */
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
