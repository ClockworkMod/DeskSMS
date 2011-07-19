package com.koushikdutta.desktopsms;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ServiceHelper {
    private static String LOGTAG = ServiceHelper.class.getSimpleName();
    private static final String SETTINGS_URL = "https://desksms.appspot.com/settings";

    static void addAuthentication(Context context, HttpMessage message) {
        Settings settings = Settings.getInstance(context);
        String ascidCookie = settings.getString("Cookie");
        message.setHeader("Cookie", ascidCookie);
        message.setHeader("X-Same-Domain", "1"); // XSRF
    }
    
    static HttpPost getAuthenticatedPost(Context context, URI uri, ArrayList<NameValuePair> params) throws UnsupportedEncodingException {
        HttpPost post = new HttpPost(uri);
        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params, "UTF-8");
        post.setEntity(entity);
        addAuthentication(context, post);
        return post;
    }
    
    static void updateSettings(final Context context) {
        Settings settings = Settings.getInstance(context);
        final boolean xmpp = settings.getBoolean("forward_xmpp", true);
        final boolean mail = settings.getBoolean("forward_email", true);
        new Thread() {
            public void run() {
                try {
                    ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
                    params.add(new BasicNameValuePair("forward_xmpp", String.valueOf(xmpp)));
                    params.add(new BasicNameValuePair("forward_email", String.valueOf(mail)));

                    HttpPost post = ServiceHelper.getAuthenticatedPost(context, new URI(SETTINGS_URL), params);
                    DefaultHttpClient client = new DefaultHttpClient();
                    HttpResponse res = client.execute(post);
                    Log.i(LOGTAG, "Status code from register: " + res.getStatusLine().getStatusCode());
                    Intent i = new Intent(WidgetProvider.UPDATE);
                    context.sendBroadcast(i);
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
            };
        }.start();        
    }
    
    static void getSettings(final Context context, final Callback<JSONObject> callback) {
        new Thread() {
            @Override
            public void run() {
                try {
                    HttpGet get = new HttpGet(new URI(SETTINGS_URL));
                    ServiceHelper.addAuthentication(context, get);
                    DefaultHttpClient client = new DefaultHttpClient();
                    HttpResponse res = client.execute(get);
                    final JSONObject s = new JSONObject(StreamUtility.readToEnd(res.getEntity().getContent()));
                    Iterator<String> keys = s.keys();
                    Settings settings = Settings.getInstance(context);
                    while (keys.hasNext()) {
                        String key = keys.next();
                        String value = s.optString(key, null);
                        if (value == null)
                            continue;
                        settings.setString(key, value);
                    }
                    callback.onCallback(s);
                    Intent i = new Intent(WidgetProvider.UPDATE);
                    context.sendBroadcast(i);
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }.start();
    }
}
