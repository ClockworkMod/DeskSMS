package com.koushikdutta.desktopsms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

public class DateChangedReceiver extends BroadcastReceiver {
    Handler mHandler = new Handler();

    @Override
    public void onReceive(final Context context, Intent intent) {
        TickleServiceHelper.registerForPush(context, new Callback<Void>() {
            @Override
            public void onCallback(Void result) {
                try {
                    TickleServiceHelper.registerWebConnect(context);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
