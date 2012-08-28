package com.koushikdutta.tabletsms;

import android.app.Application;

public class TabletSmsApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Helper.startSync(this);
    }
}
