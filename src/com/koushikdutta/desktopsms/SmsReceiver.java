package com.koushikdutta.desktopsms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, Intent intent) {
        Intent i = new Intent(context, SmsService.class);
        i.putExtras(intent);
        context.startService(i);
    
        boolean disable = Settings.getInstance(context).getBoolean("disable_notifications", false);
        if (disable)
            setResultData(null);
    }

}
