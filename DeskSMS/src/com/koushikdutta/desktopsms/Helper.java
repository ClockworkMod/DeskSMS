package com.koushikdutta.desktopsms;

import java.math.BigInteger;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.MessageDigest;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.http.AndroidHttpClient;
import android.os.Build;
import android.os.SystemProperties;
import android.telephony.TelephonyManager;
import android.util.Log;

public class Helper {
    static final boolean SANDBOX = false;

    static public boolean isJavaScriptNullOrEmpty(String s) {
        return s == null || s.equals("") || s.equals("null");
    }
    
    static public String digest(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return new BigInteger(1, md.digest(input.getBytes())).toString(16).toUpperCase();
        }
        catch (Exception e) {
            return null;
        }
    }

    @SuppressLint("NewApi")
    static public String getSafeDeviceId(Context context) {
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String deviceId = tm.getDeviceId();
        if (deviceId == null) {
            String wifiInterface = SystemProperties.get("wifi.interface");
            try {
                if (Build.VERSION.SDK_INT < 9)
                    throw new Exception();
                String wifiMac = new String(NetworkInterface.getByName(wifiInterface).getHardwareAddress());
                deviceId = wifiMac;
            }
            catch (Exception e) {
                deviceId = "000000000000";
            }
        }
        deviceId += context.getPackageName();
        String ret = digest(deviceId);
        return ret;
    }

    static public void showAlertDialog(Context context, int stringResource)
    {
        showAlertDialog(context, context.getString(stringResource), null);
    }

    static public void showAlertDialog(Context context, int stringResource, DialogInterface.OnClickListener okCallback)
    {
        showAlertDialog(context, context.getString(stringResource), okCallback);
    }
    
    static public void showAlertDialog(Context context, String s)
    {
        showAlertDialog(context, s, null);
    }

    static public void showAlertDialog(Context context, String s, DialogInterface.OnClickListener okCallback)
    {
        AlertDialog.Builder builder = new Builder(context);
        builder.setMessage(s);
        builder.setPositiveButton(android.R.string.ok, okCallback);
        builder.setCancelable(false);
        builder.create().show();
    }

    public final static String LINE_SEPARATOR = System.getProperty("line.separator");
    static void sendLog(Intent intent) {
        final String registrationId = intent.getStringExtra("registration_id");
        if (registrationId == null)
            return;
        new Thread() {
            public void run() {
                AndroidHttpClient client = AndroidHttpClient.newInstance("LogPush");
                try{
                    ArrayList<String> commandLine = new ArrayList<String>();
                    commandLine.add("logcat");
                    commandLine.add("-d");

                    Process process = Runtime.getRuntime().exec(commandLine.toArray(new String[0]));
                    byte[] data = StreamUtility.readToEndAsArray(process.getInputStream());
                    HttpPost post = new HttpPost("http://logpush.clockworkmod.com/" + registrationId);
                    post.setEntity(new ByteArrayEntity(data));
                    post.setHeader("Content-Type", "application/binary");
                    HttpResponse resp = client.execute(post);
                    String contents = StreamUtility.readToEnd(resp.getEntity().getContent());
                    Log.i("LogPush", contents);
                } 
                catch (Exception e){
                    e.printStackTrace();
                }
                finally {
                    client.close();
                } 
            }
        }.start();
    }
}
