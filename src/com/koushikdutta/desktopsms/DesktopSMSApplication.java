package com.koushikdutta.desktopsms;

import android.app.Application;
import android.content.pm.PackageInfo;

public class DesktopSMSApplication extends Application {
    public static int mVersionCode = 0;
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            PackageInfo pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            mVersionCode = pinfo.versionCode;
        }
        catch (Exception ex) {
        }
    }
}
