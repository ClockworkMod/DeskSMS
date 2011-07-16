package com.koushikdutta.desktopsms;

import java.net.URI;
import java.util.ArrayList;
import java.util.Hashtable;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

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

    private void sendMessage(final String ascidCookie, final String number, final String message) {
        new Thread() {
            @Override
            public void run() {
                try {
                    URI uri = new URI(MESSAGE_URL);
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
                    DefaultHttpClient client = new DefaultHttpClient();
                    HttpResponse res = client.execute(post);
                    Log.i(LOGTAG, "Status code from register: " + res.getStatusLine().getStatusCode());
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }.start();
    }
    
    private static class PendingSms {
        public long firstReceived = System.currentTimeMillis();
        public int length;
        public int count = 0;
        public String message;
    }

    void sendPendingMessages() {
        Settings settings = Settings.getInstance(SmsService.this);
        String ascidCookie = settings.getString("Cookie");
        // steal the hashtable to prevent any concurrency issues
        for (String number: mPendingMessages.keySet()) {
            PendingSms sms = mPendingMessages.get(number);
            // if we've been waiting a while for the rest, give up
            if (sms.firstReceived + 45000 > System.currentTimeMillis() && sms.count < sms.length)
                continue;
            mPendingMessages.remove(number);
            sendMessage(ascidCookie, number, sms.message);
        }
    }

    Hashtable<String, PendingSms> mPendingMessages = new Hashtable<String, PendingSms>();
    Handler mHandler = new Handler();
    
    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);

        try {
            Log.i(LOGTAG, "SMS Received");
            final Bundle bundle = intent.getExtras();
            final Object[] pdus = (Object[]) bundle.get("pdus");
            if (bundle != null && pdus.length > 0) {
                Settings settings = Settings.getInstance(SmsService.this);
                String ascidCookie = settings.getString("Cookie");

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
                            sendMessage(ascidCookie, number, message);
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
    }
}
