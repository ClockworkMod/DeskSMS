package com.koushikdutta.tabletsms;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.http.client.ClientProtocolException;
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
    public final static String PUSH_URL = USER_URL + "/push";
    public final static String IMAGE_URL = USER_URL + "/image";
    public static final String SETTINGS_URL = USER_URL + "/settings";
    public static final String SMS_URL = USER_URL + "/sms";
    public static final String DELETE_CONVERSATION_URL = USER_URL + "/delete/conversation";
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
    
    static JSONObject retryExecuteAsJSONObject(Context context, String account, URL url, ConnectionCallback callback) throws ClientProtocolException, IOException, OperationCanceledException, AuthenticatorException, URISyntaxException, JSONException {
        HttpURLConnection conn = retryExecute(context, account, url, callback);
        JSONObject ret = null;
        if (conn != null) {
            ret = StreamUtility.downloadUriAsJSONObject(conn);
            conn.disconnect();
        }
        return ret;
    }

    static String retryExecuteAsString(Context context, String account, URL url, ConnectionCallback callback) throws ClientProtocolException, IOException, OperationCanceledException, AuthenticatorException, URISyntaxException {
        HttpURLConnection conn = retryExecute(context, account, url, callback);
        String ret = null;
        if (conn != null) {
            ret = StreamUtility.downloadUriAsString(conn);
            conn.disconnect();
        }
        return ret;
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
    
    public static interface ConnectionCallback {
        public void manage(HttpURLConnection conn) throws IOException;
    }
    
    public static class JSONPoster implements ConnectionCallback {
        JSONObject json;
        public JSONPoster(JSONObject json) {
            this.json = json;
        }
        @Override
        public void manage(HttpURLConnection conn) throws IOException {
            conn.setRequestProperty("Content-Type", "application/json");
            byte[] bytes = json.toString().getBytes();
            conn.setDoOutput(true);
            OutputStream os = conn.getOutputStream();
            os.write(bytes);
            os.close();
        }
    }

    public static class StringPoster implements ConnectionCallback {
        String s;
        public StringPoster(String s) {
            this.s = s;
        }
        @Override
        public void manage(HttpURLConnection conn) throws IOException {
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            byte[] bytes = s.getBytes();
            conn.setDoOutput(true);
            OutputStream os = conn.getOutputStream();
            os.write(bytes);
            os.close();
        }
    }

    public static class FilePoster implements ConnectionCallback {
        File file;
        public FilePoster(File file) {
            this.file = file;
        }
        @Override
        public void manage(HttpURLConnection conn) throws IOException {
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            OutputStream os = conn.getOutputStream();
            InputStream is = new FileInputStream(file);
            StreamUtility.copyStream(is, os);
            os.close();
            is.close();
        }
    }
    
    static HttpURLConnection setupConnection(Context context, URL url, ConnectionCallback callback) throws IOException {
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn = (HttpURLConnection)url.openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setDoInput(true);
        addAuthentication(context, conn);
        if (callback != null) {
            callback.manage(conn);
        }
        return conn;
    }

    static HttpURLConnection retryExecuteAndDisconnect(Context context, String account, URL url, ConnectionCallback callback) throws ClientProtocolException, IOException, OperationCanceledException, AuthenticatorException, URISyntaxException {
        HttpURLConnection conn = retryExecute(context, account, url, callback);
        if (conn != null)
            conn.disconnect();
        return conn;
    }

    private static HttpURLConnection retryExecute(Context context, String account, URL url, ConnectionCallback callback) throws ClientProtocolException, IOException, OperationCanceledException, AuthenticatorException, URISyntaxException {
        HttpURLConnection conn = setupConnection(context, url, callback);
        
        if (conn.getResponseCode() != 302)
            return conn;

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

        conn = setupConnection(context, url, callback);

        if (conn.getResponseCode() != 302)
            return conn;
        
        createAuthenticationNotification(context);
        Log.e(LOGTAG, "Authentication failure.");
        return null;
    }

    static void addAuthentication(Context context, HttpURLConnection conn) {
        Settings settings = Settings.getInstance(context);
        String ascidCookie = settings.getString("Cookie");
        conn.addRequestProperty("Cookie", ascidCookie);
        conn.addRequestProperty("X-Same-Domain", "1"); // XSRF
    }
}
