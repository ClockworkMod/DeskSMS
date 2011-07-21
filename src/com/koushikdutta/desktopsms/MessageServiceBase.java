package com.koushikdutta.desktopsms;

import java.net.URI;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.ContactsContract.PhoneLookup;
import android.util.Log;

public class MessageServiceBase extends Service {
    private static final String LOGTAG = MessageServiceBase.class.getSimpleName();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    protected void sendMessage(final String number, final String message, final int subjectResource, final String type) {
        final String account = Settings.getInstance(this).getString("account");
        final String ascidCookie = Settings.getInstance(this).getString("Cookie");
        new Thread() {
            @Override
            public void run() {
                sendMessageInternal(account, ascidCookie, number, message, subjectResource, type, 0);
            }
        }.start();
    }
    
    private void sendMessageInternal(final String account, final String ascidCookie, final String number, final String message, final int subjectResource, String type, int attempt) {
        try {
            URI uri = new URI(String.format("%s/%s/%s", ServiceHelper.MESSAGE_URL, account, number));
            ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
            Uri curi = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
            Cursor c = getContentResolver().query(curi, new String[] { PhoneLookup.DISPLAY_NAME }, null, null, null);

            String displayName = null;
            if (c != null) {
                if (c.moveToNext()) {
                    displayName = c.getString(c.getColumnIndex(PhoneLookup.DISPLAY_NAME));
                    if (!Helper.isJavaScriptNullOrEmpty(displayName)) {
                        Log.i(LOGTAG, "" + displayName);
                        params.add(new BasicNameValuePair("name", displayName));
                    }
                }
                c.close();
            }
            if (Helper.isJavaScriptNullOrEmpty(displayName))
                displayName = number;

            HttpPost post = new HttpPost(uri);
            params.add(new BasicNameValuePair("version_code", String.valueOf(DesktopSMSApplication.mVersionCode)));
            params.add(new BasicNameValuePair("number", number));
            params.add(new BasicNameValuePair("message", message));
            params.add(new BasicNameValuePair("type", type));
            params.add(new BasicNameValuePair("subject", getString(subjectResource, displayName)));
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params, "UTF-8");
            post.setEntity(entity);
            post.setHeader("Cookie", ascidCookie);
            post.setHeader("X-Same-Domain", "1"); // XSRF
            
            // do not allow redirecting to the oauth page
            final HttpParams postParams = new BasicHttpParams();
            HttpClientParams.setRedirecting(postParams, false);
            post.setParams(postParams);
            DefaultHttpClient client = new DefaultHttpClient();
            HttpResponse res = client.execute(post);
            Log.i(LOGTAG, "Status code from register: " + res.getStatusLine().getStatusCode());
            
            if (res.getStatusLine().getStatusCode() == 200)
                return;
            
            switch (res.getStatusLine().getStatusCode()) {
            case 302:
                {
                    // we were redirected to the auth page, let's grab another cookie.
                    if (attempt == 1)
                        return;
                    AccountManager accountManager = AccountManager.get(this);
                    Account acct = new Account(account, "com.google");
                    String curAuthToken = Settings.getInstance(this).getString("web_connect_auth_token");
                    if (!Helper.isJavaScriptNullOrEmpty(curAuthToken))
                        accountManager.invalidateAuthToken(acct.type, curAuthToken);
                    Bundle bundle = accountManager.getAuthToken(acct, TickleServiceHelper.AUTH_TOKEN_TYPE, false, null, null).getResult();
                    final String authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
                    if (!Helper.isJavaScriptNullOrEmpty(authToken)) {
                        Settings settings = Settings.getInstance(this);
                        settings.setString("web_connect_auth_token", authToken);
                    }
                    if (authToken == null)
                        return;
                    String newCookie = TickleServiceHelper.getCookie(this);
                    if (newCookie == null)
                        return;
                    sendMessageInternal(account, newCookie, number, message, subjectResource, type, attempt + 1);
                }
                break;
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
