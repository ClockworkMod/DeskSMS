package com.koushikdutta.desktopsms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class SettingsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            Log.i("DeskSMS", "Settings broadcast received.");
            Settings settings = Settings.getInstance(context);
            String[] vals = new String[] { "forward_xmpp", "forward_email", "forward_web" };
            for (String val: vals) {
                if (intent.hasExtra(val)) {
                    settings.setBoolean(val, intent.getBooleanExtra(val, true));
                }
            }
            ServiceHelper.updateSettings(context, settings.getBoolean("forward_xmpp", true), settings.getBoolean("forward_email", true), settings.getBoolean("forward_web", true), null);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
