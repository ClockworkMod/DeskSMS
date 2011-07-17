package com.koushikdutta.desktopsms;

import java.net.URI;
import java.util.ArrayList;
import java.util.Hashtable;

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
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.ContactsContract.PhoneLookup;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static final String MESSAGE_URL = "https://desksms.appspot.com/message";
    private static final String LOGTAG = SmsReceiver.class.getSimpleName();

    private void sendMessage(final String account, final String ascidCookie, final String number, final String message) {
        new Thread() {
            @Override
            public void run() {
                sendMessageInternal(account, ascidCookie, number, message, 0);
            }
        }.start();
    }
    
    private void sendMessageInternal(final String account, final String ascidCookie, final String number, final String message, int attempt) {
        try {
            URI uri = new URI(String.format("%s/%s/%s", MESSAGE_URL, account, number));
            ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
            Uri curi = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
            Cursor c = getContentResolver().query(curi, new String[] { PhoneLookup.DISPLAY_NAME }, null, null, null);

            if (c != null) {
                if (c.moveToNext()) {
                    String displayName = c.getString(c.getColumnIndex(PhoneLookup.DISPLAY_NAME));
                    if (displayName != null) {
                        Log.i(LOGTAG, "" + displayName);
                        params.add(new BasicNameValuePair("name", displayName));
                    }
                }
                c.close();
            }
            HttpPost post = new HttpPost(uri);
            params.add(new BasicNameValuePair("number", number));
            params.add(new BasicNameValuePair("message", message));
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
                    sendMessageInternal(account, newCookie, number, message, attempt + 1);
                }
                break;
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    private static class PendingSms {
        public long firstReceived = System.currentTimeMillis();
        public int length;
        public int count = 0;
        public String message;
    }

    void sendPendingMessages() {
        Settings settings = Settings.getInstance(SmsService.this);
        int forwarded = settings.getInt("forwarded", 0);
        String ascidCookie = settings.getString("Cookie");
        String account = settings.getString("account");
        // steal the hashtable to prevent any concurrency issues
        for (String number: mPendingMessages.keySet()) {
            PendingSms sms = mPendingMessages.get(number);
            // if we've been waiting a while for the rest, give up
            if (sms.firstReceived + 45000 > System.currentTimeMillis() && sms.count < sms.length)
                continue;
            mPendingMessages.remove(number);
            sendMessage(account, ascidCookie, number, sms.message);
            forwarded++;
        }
        settings.setInt("forwarded", forwarded);
    }

    Hashtable<String, PendingSms> mPendingMessages = new Hashtable<String, PendingSms>();
    Handler mHandler = new Handler();
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            Log.i(LOGTAG, "SMS Received");
            final Bundle bundle = intent.getExtras();
            final Object[] pdus = (Object[]) bundle.get("pdus");
            if (bundle != null && pdus.length > 0) {
                Settings settings = Settings.getInstance(SmsService.this);
                String ascidCookie = settings.getString("Cookie");
                String account = settings.getString("account");

                // aggregate the multipart messages
                for (Object pdu : pdus) {
                    try {
                        Log.i(LOGTAG, "Forwarding SMS");
                        SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
                        String number = sms.getOriginatingAddress();
                        String message = sms.getMessageBody().toString();
                        Log.i(LOGTAG, "" + number);
                        Log.i(LOGTAG, "" + message);
                        
                        if (message.charAt(0) == '(' && message.charAt(2) == '/' && message.charAt(4) == ')') {
                            int messageCount = message.charAt(3) - '0';
                            message = message.substring(5);
                            PendingSms pending = mPendingMessages.get(number);
                            if (pending != null) {
                                pending.message += message;
                                pending.count++;
                            }
                            else {
                                pending = new PendingSms();
                                pending.message = message;
                                pending.length = messageCount;
                                pending.count = 1;
                                mPendingMessages.put(number, pending);
                            }
                        }
                        else {
                            sendMessage(account, ascidCookie, number, message);
                        }
                    }
                    catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                
                // send what we can immediately
                sendPendingMessages();
                
                // and schedule a pedantic resend as well for dangling partial messages
                if (mPendingMessages.size() > 0) {
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            sendPendingMessages();
                        }
                    }, 15000);
                }
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        return super.onStartCommand(intent, flags, startId);
    }
}
