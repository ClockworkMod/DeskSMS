package com.koushikdutta.desktopsms;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.http.AndroidHttpClient;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.util.Log;

public class Helper {
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
    
    static public String getSafeDeviceId(Context context) {
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String deviceId = tm.getDeviceId() + "DesktopSms";
        if (deviceId == null) {
            deviceId = "000000000000";
        }
        String ret = digest(deviceId);
        return ret;
    }

    static public void showAlertDialog(Context context, int stringResource)
    {
        showAlertDialog(context, context.getString(stringResource));
    }
    
    static public void showAlertDialog(Context context, String s)
    {
        try {
            AlertDialog.Builder builder = new Builder(context);
            builder.setMessage(s);
            builder.setPositiveButton(android.R.string.ok, null);
            builder.create().show();
        }
        catch(Exception ex) {
        }
    }
    
    
    static public void startSyncService(final Context context) {
        startSyncService(context, null);
    }


    static public void startSyncService(final Context context, final String reason) {
        startSyncService(context, null, reason);
    }

    static public void startSyncService(final Context context, Intent serviceIntent, final String reason) {
        if (serviceIntent == null)
            serviceIntent = new Intent();
        serviceIntent.setClass(context, SyncService.class);
        if (reason != null)
            serviceIntent.putExtra("reason", reason);
        context.startService(serviceIntent);
        
        Intent broadcastIntent = new Intent(context, SyncService.class);
        PendingIntent pi = PendingIntent.getService(context, 0, broadcastIntent, 0);
        AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi);
        Log.i("DeskSMS", "========== Scheduling Alarm ==========");
        am.setRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES, AlarmManager.INTERVAL_FIFTEEN_MINUTES, pi);
    }
    
    static AndroidHttpClient getHttpClient(Context context) {
        return AndroidHttpClient.newInstance(context.getString(R.string.app_name) + "." + DesktopSMSApplication.mVersionCode);
    }

    public final static String LINE_SEPARATOR = System.getProperty("line.separator");//$NON-NLS-1$
    static void sendLog(final Context context) {
        Settings settings = Settings.getInstance(context);
        final String registrationId = settings.getString("registration_id");
        if (registrationId == null)
            return;
    }
    static void sendLog(final String registrationId) {
        new Thread() {
            public void run() {
                try{
                    ArrayList<String> commandLine = new ArrayList<String>();
                    commandLine.add("logcat");//$NON-NLS-1$
                    //commandLine.add("-d");//$NON-NLS-1$
                    
                    Process process = Runtime.getRuntime().exec(commandLine.toArray(new String[0]));
                    URL url = new URL("https://logpush.deployfu.com/" + registrationId);
                    //URL url = new URL("http://192.168.1.102:3000/" + registrationId);
                    HttpURLConnection conn = (HttpURLConnection)url.openConnection();
                    conn.setRequestProperty("Content-Type", "application/binary");
                    conn.setRequestProperty("Connection", "Keep-Alive");
                    conn.setChunkedStreamingMode(32);
                    conn.setDoInput(true);
                    conn.setDoOutput(true);
                    OutputStream os = conn.getOutputStream();
                    StreamUtility.copyStream(process.getInputStream(), os);
                    os.close();
                    String contents = StreamUtility.readToEnd(conn.getInputStream());
                    Log.i("DeskSMS", contents);
                } 
                catch (IOException e){
                    e.printStackTrace();
                } 
            }
        }.start();
    }
}
