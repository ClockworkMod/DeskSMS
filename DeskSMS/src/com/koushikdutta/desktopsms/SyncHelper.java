package com.koushikdutta.desktopsms;

import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;

import android.content.Context;
import android.content.Intent;
import android.net.http.AndroidHttpClient;
import android.util.Log;

public class SyncHelper {
    
    
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
