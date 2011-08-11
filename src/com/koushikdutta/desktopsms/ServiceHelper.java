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
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class ServiceHelper {
    private static String LOGTAG = ServiceHelper.class.getSimpleName();
    public static final String BASE_URL = "https://desksms.appspot.com";
    public static final String AUTH_URL = BASE_URL + "/_ah/login";
    public static final String API_URL = BASE_URL + "/api/v1";
    public final static String REGISTER_URL = API_URL + "/register";
    public final static String PING_URL = API_URL + "/ping";
    public static final String USER_URL = API_URL + "/user/default";
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
        if (authToken == null)
            return null;
        String newCookie = TickleServiceHelper.getCookie(context);
        if (newCookie == null)
            return null;

        addAuthentication(context, req);
        res = client.execute(req);
        
        if (res.getStatusLine().getStatusCode() != 302)
            return res;
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
                    final Settings settings = Settings.getInstance(context);
                    final String account = settings.getString("account");
                    ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
                    params.add(new BasicNameValuePair("forward_xmpp", String.valueOf(xmpp)));
                    params.add(new BasicNameValuePair("forward_email", String.valueOf(mail)));
                    params.add(new BasicNameValuePair("forward_web", String.valueOf(web)));

                    HttpPost post = ServiceHelper.getAuthenticatedPost(context, String.format(SETTINGS_URL, account), params);
                    DefaultHttpClient client = new DefaultHttpClient();
                    HttpResponse res = client.execute(post);
                    Log.i(LOGTAG, "Status code from settings: " + res.getStatusLine().getStatusCode());
                    settings.setBoolean("forward_xmpp", xmpp);
                    settings.setBoolean("forward_email", mail);
                    settings.setBoolean("forward_web", web);
                    if (callback != null)
                        callback.onCallback(true);
                }
                catch (Exception ex) {
                    //ex.printStackTrace();
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
                    ServiceHelper.addAuthentication(context, get);
                    DefaultHttpClient client = new DefaultHttpClient();
                    HttpResponse res = client.execute(get);
                    final JSONObject s = new JSONObject(StreamUtility.readToEnd(res.getEntity().getContent()));
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
