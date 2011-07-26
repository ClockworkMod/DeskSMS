package com.koushikdutta.desktopsms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

public class DateChangedReceiver extends BroadcastReceiver {
    Handler mHandler = new Handler();

    @Override
    public void onReceive(Context context, Intent intent) {
        TickleServiceHelper.registerForPush(context, null);
    }
}
