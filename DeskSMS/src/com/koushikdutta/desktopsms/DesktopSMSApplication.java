package com.koushikdutta.desktopsms;

import com.clockworkmod.billing.ClockworkModBillingClient;

import android.app.Application;
import android.content.pm.PackageInfo;

public class DesktopSMSApplication extends Application {
    static final String CLOCKWORKMOD_PUBLICKEY = "MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBALzBgQZi3DDuJTpoPmAB6tg42Sh02lvsbPSjzV1P8yie2YRYVh9yOrJBfWTvJSnlzksFRCFuB1TGu5tekulk61MCAwEAAQ==";
    static final String MARKET_PUBLICKEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtBKmQb/Czwai4WdFJ+ZHWbYXF2pxAPcRXy9cs2QHsd4pZY7AZjimETh+BKfLH77+sgwJdc5eRYjv9w3Dd3ofwLXmPcJHURfiWZbTN/WaQXH6ypQVgm4WZcAwmQcpg1+sWdeNjTLJp6V9VHqos9NTbmwbSYS6gJJSSyVgPMKCRu3j1v/57Qk3C0Wuxr8mRKdgqLEEN/tPZ4fLsfiY3o6Ie6TbdTvhR7uItNxxAXSWkzYorM2gVSKA78yIgmZ5KamgxEUfl/7psyHtSqQY9ukB5U1ERNbrLVWszPL7AWLaA8iy92O/YU9LmK5FSjnb/Qzfd7+41/ngD+znNtxg0aZaOwIDAQAB";

    public static int mVersionCode = 0;
    @Override
    public void onCreate() {
        System.out.println("starting");
        doStuff();
    }
    void doStuff() {
        super.onCreate();
        try {
            PackageInfo pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            mVersionCode = pinfo.versionCode;

            ClockworkModBillingClient.init(this, "koushd@gmail.com", CLOCKWORKMOD_PUBLICKEY, MARKET_PUBLICKEY, Helper.SANDBOX).refreshMarketPurchases();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
