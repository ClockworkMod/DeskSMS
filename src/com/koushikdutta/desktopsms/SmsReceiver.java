package com.koushikdutta.desktopsms;

import java.net.URI;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.PhoneLookup;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsReceiver extends BroadcastReceiver {
    private static final String MESSAGE_URL = "https://desksms.appspot.com/message";
    private static final String LOGTAG = SmsReceiver.class.getSimpleName();

    @Override
    public void onReceive(final Context context, Intent intent) {
        Log.i(LOGTAG, "SMS Received");
        //---get the SMS message passed in---
        final Bundle bundle = intent.getExtras();
        final Object[] pdus = (Object[]) bundle.get("pdus");
        final ContentResolver resolver = context.getContentResolver();
        if (bundle != null && pdus.length > 0)
        {
            new Thread() {
                public void run() {
                    try {
                        Settings settings = Settings.getInstance(context);
                        String ascidCookie = settings.getString("Cookie");
                        
                        int forwarded = settings.getInt("forwarded", 0);
                        
                        //---retrieve the SMS message received---
                        for (Object pdu: pdus){
                            try {
                                Log.i(LOGTAG, "Forwarding SMS");
                                SmsMessage sms = SmsMessage.createFromPdu((byte[])pdu);
                                URI uri = new URI(MESSAGE_URL);
                                HttpPost post = new HttpPost(uri);
                                ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
                                String number = sms.getOriginatingAddress();
                                String message = sms.getMessageBody().toString();
                                String displayName = null;
                                params.add(new BasicNameValuePair("number", number));
                                params.add(new BasicNameValuePair("message", message));
                                Log.i(LOGTAG, "" + number);
                                Log.i(LOGTAG, "" + message);

                                Uri curi = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
                                Cursor c = resolver.query(curi, new String[]{PhoneLookup.DISPLAY_NAME}, null, null, null);
                                
                                if (c != null) {
                                    if (c.moveToNext()) {
                                        displayName = c.getString(c.getColumnIndex(PhoneLookup.DISPLAY_NAME));
                                        if (displayName != null) {
                                            Log.i(LOGTAG, "" + displayName);
                                            params.add(new BasicNameValuePair("name", displayName));
                                        }
                                    }
                                    c.close();
                                }

                                UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params, "UTF-8");
                                post.setEntity(entity);
                                post.setHeader("Cookie", ascidCookie);
                                settings.setString("Cookie", ascidCookie);
                                post.setHeader("X-Same-Domain", "1"); // XSRF
                                DefaultHttpClient client = new DefaultHttpClient();
                                HttpResponse res = client.execute(post);
                                Log.i(LOGTAG, "Status code from register: " + res.getStatusLine().getStatusCode());
                                forwarded++;
                            }
                            catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                        
                        settings.setInt("forwarded", forwarded);
                    }
                    catch (Exception ex) {
                        ex.printStackTrace();
                    }
                };
            }.start();
        }  
    }

}
