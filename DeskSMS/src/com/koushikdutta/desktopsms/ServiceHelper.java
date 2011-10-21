package com.koushikdutta.desktopsms;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.http.AndroidHttpClient;
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;

public class ServiceHelper {
    private static String LOGTAG = ServiceHelper.class.getSimpleName();
    public static final String BASE_URL = Helper.SANDBOX ? "https://2.desksms.appspot.com" : "https://desksms.appspot.com";
    public static final String AUTH_URL = BASE_URL + "/_ah/login";
    public static final String API_URL = BASE_URL + "/api/v1";
    public final static String REGISTER_URL = API_URL + "/register";
    public static final String USER_URL = API_URL + "/user/default";
    public final static String PING_URL = USER_URL + "/ping";
    public static final String SETTINGS_URL = USER_URL + "/settings";
    public static final String SMS_URL = USER_URL + "/sms";
    public static final String CALL_URL = USER_URL + "/call";
    public static final String WHOAMI_URL = USER_URL + "/whoami";
    public static final String STATUS_URL = USER_URL + "/status";
    public static final String OUTBOX_URL = USER_URL + "/outbox";
    
    static String numbersOnly(String number, boolean allowPlus) {
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < number.length(); i++) {
            char c = number.charAt(i);
            if ((c >= '0' && c <= '9') || (c == '+' && allowPlus))
                ret.append(c);
        }
        
        return ret.toString();
    }
    
    static JSONObject retryExecuteAsJSONObject(Context context, String account, HttpUriRequest req) throws ClientProtocolException, IOException, OperationCanceledException, AuthenticatorException, URISyntaxException, JSONException {
        AndroidHttpClient client = Helper.getHttpClient(context);
        try {
            return StreamUtility.downloadUriAsJSONObject(retryExecute(context, account, client, req));
        }
        finally {
            client.close();
        }
    }

    static String retryExecuteAsString(Context context, String account, HttpUriRequest req) throws ClientProtocolException, IOException, OperationCanceledException, AuthenticatorException, URISyntaxException {
        AndroidHttpClient client = Helper.getHttpClient(context);
        try {
            return StreamUtility.downloadUriAsString(retryExecute(context, account, client, req));
        }
        finally {
            client.close();
        }
    }
    
    static void createAuthenticationNotification(Context context) {
        NotificationManager n = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notification = new Notification(R.drawable.icon, context.getString(R.string.authentification_notification), System.currentTimeMillis());
        notification.contentView = new RemoteViews(context.getPackageName(), R.layout.notification);
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("relogin", true);
        notification.contentIntent = PendingIntent.getActivity(context, 0, intent, Intent.FLAG_ACTIVITY_NEW_TASK);
        n.cancel(444);
        n.notify(444, notification);
    }
    
    static HttpResponse retryExecute(Context context, String account, HttpClient client, HttpUriRequest req) throws ClientProtocolException, IOException, OperationCanceledException, AuthenticatorException, URISyntaxException {
        addAuthentication(context, req);

        final HttpParams httpParams = new BasicHttpParams();
        HttpClientParams.setRedirecting(httpParams, false);
        req.setParams(httpParams);

        HttpResponse res = client.execute(req);
        
        if (res.getStatusLine().getStatusCode() != 302)
            return res;
        
        AccountManager accountManager = AccountManager.get(context);
        Account acct = new Account(account, "com.google");
        String curAuthToken = Settings.getInstance(context).getString("web_connect_auth_token");
        if (!Helper.isJavaScriptNullOrEmpty(curAuthToken))
            accountManager.invalidateAuthToken(acct.type, curAuthToken);
        Bundle bundle = accountManager.getAuthToken(acct, TickleServiceHelper.AUTH_TOKEN_TYPE, false, null, null).getResult();
        final String authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
        if (!Helper.isJavaScriptNullOrEmpty(authToken)) {
            Settings settings = Settings.getInstance(context);
            settings.setString("web_connect_auth_token", authToken);
        }
        if (authToken == null) {
            Log.e(LOGTAG, "Authentication failure.");
            createAuthenticationNotification(context);
            return null;
        }
        String newCookie = TickleServiceHelper.getCookie(context);
        if (newCookie == null) {
            Log.e(LOGTAG, "Authentication failure.");
            createAuthenticationNotification(context);
            return null;
        }

        addAuthentication(context, req);
        res = client.execute(req);
        
        if (res.getStatusLine().getStatusCode() != 302)
            return res;
        
        createAuthenticationNotification(context);
        Log.e(LOGTAG, "Authentication failure.");
        return null;
    }

    static void addAuthentication(Context context, HttpMessage message) {
        Settings settings = Settings.getInstance(context);
        String ascidCookie = settings.getString("Cookie");
        message.setHeader("Cookie", ascidCookie);
        message.setHeader("X-Same-Domain", "1"); // XSRF
    }
    
    static HttpPost getAuthenticatedPost(Context context, String url) throws UnsupportedEncodingException, URISyntaxException {
        URI uri = new URI(url);
        HttpPost post = new HttpPost(uri);
        addAuthentication(context, post);
        return post;
    }
    
    static HttpPost getAuthenticatedPost(Context context, String url, ArrayList<NameValuePair> params) throws UnsupportedEncodingException, URISyntaxException {
        URI uri = new URI(url);
        HttpPost post = new HttpPost(uri);
        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params, "UTF-8");
        post.setEntity(entity);
        addAuthentication(context, post);
        return post;
    }

    static void updateSettings(final Context context, final boolean xmpp, final boolean mail, final boolean web, final Callback<Boolean> callback) {
        new Thread() {
            public void run() {
                try {
                    Log.i(LOGTAG, "Attempting to update settings.");
                    final Settings settings = Settings.getInstance(context);
                    final String account = settings.getString("account");
                    ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
                    params.add(new BasicNameValuePair("forward_xmpp", String.valueOf(xmpp)));
                    params.add(new BasicNameValuePair("forward_email", String.valueOf(mail)));
                    params.add(new BasicNameValuePair("forward_web", String.valueOf(web)));

                    HttpPost post = ServiceHelper.getAuthenticatedPost(context, String.format(SETTINGS_URL, account), params);
                    String res = ServiceHelper.retryExecuteAsString(context, account, post);
                    Log.i(LOGTAG, "Status code from settings: " + res);
                    settings.setBoolean("forward_xmpp", xmpp);
                    settings.setBoolean("forward_email", mail);
                    settings.setBoolean("forward_web", web);
                    Log.i(LOGTAG, "Settings updated.");
                    if (callback != null)
                        callback.onCallback(true);
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                    if (callback != null)
                        callback.onCallback(false);
                }
                finally {
                    Intent i = new Intent(WidgetProvider.UPDATE);
                    context.sendBroadcast(i);
                }
            };
        }.start();        
    }
    
    static void getSettings(final Context context, final Callback<JSONObject> callback) {
        new Thread() {
            @Override
            public void run() {
                try {
                    final Settings settings = Settings.getInstance(context);
                    final String account = settings.getString("account");
                    HttpGet get = new HttpGet(new URI(String.format(SETTINGS_URL, account)));
                    JSONObject s = retryExecuteAsJSONObject(context, account, get);
                    Iterator<String> keys = s.keys();
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
