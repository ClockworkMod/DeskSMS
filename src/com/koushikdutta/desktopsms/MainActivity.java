package com.koushikdutta.desktopsms;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.ArrayList;

import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;

public class MainActivity extends ActivityBase implements ActivityResultDelegate {
    private static final String SETTINGS_URL = "https://desksms.appspot.com/settings";
    private static final String LOGTAG = MainActivity.class.getSimpleName();
    private Handler mHandler = new Handler();

    
    boolean appExists(String pkg) {
        try {
            return getPackageManager().getPackageInfo(pkg, 0) != null;
        }
        catch (Exception ex) {
        }
        return false;
    }
    
    
    private void doLogin() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle(R.string.permission_request);
        builder.setMessage(R.string.requesting_permissions);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                TickleServiceHelper.login(MainActivity.this, MainActivity.this, new Callback<Boolean>() {
                    @Override
                    public void onCallback(Boolean result) {
                        if (!result)
                            return;

                        ListItem acc = findItem(R.string.account);
                        acc.setSummary(mSettings.getString("account"));
                        ListItem gmail = findItem(R.string.gmail);
                        ListItem gtalk = findItem(R.string.google_talk);
                        gmail.setEnabled(true);
                        gtalk.setEnabled(true);

                        ListItem testMessage = findItem(R.string.test_message);
                        testMessage.setEnabled(true);
                    }
                });
            }
        });
        builder.create().show();
    }
    
    private void addAuthentication(HttpMessage message) {
        String ascidCookie = mSettings.getString("Cookie");
        message.setHeader("Cookie", ascidCookie);
        message.setHeader("X-Same-Domain", "1"); // XSRF
    }
    
    private HttpPost getAuthenticatedPost(URI uri, ArrayList<NameValuePair> params) throws UnsupportedEncodingException {
        HttpPost post = new HttpPost(uri);
        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params, "UTF-8");
        post.setEntity(entity);
        addAuthentication(post);
        return post;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String account = mSettings.getString("account");
        
        if (Helper.isJavaScriptNullOrEmpty(account)) {
            doLogin();
        }
        
        new Thread() {
            public void run() {
                try {
                    HttpGet get = new HttpGet(new URI(SETTINGS_URL));
                    addAuthentication(get);
                    DefaultHttpClient client = new DefaultHttpClient();
                    HttpResponse res = client.execute(get);
                    JSONObject s = new JSONObject(StreamUtility.readToEnd(res.getEntity().getContent()));
                    final boolean forward_xmpp = s.optBoolean("forward_xmpp", true);
                    final boolean forward_email = s.optBoolean("forward_email", true);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ListItem gmail = findItem(R.string.gmail);
                            ListItem gtalk = findItem(R.string.google_talk);
                            gmail.setIsChecked(forward_email);
                            gtalk.setIsChecked(forward_xmpp);
                        }
                    });
                    Log.i(LOGTAG, "Status code from register: " + res.getStatusLine().getStatusCode());
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
            };
        }.start();

        addItem(R.string.account, new ListItem(this, getString(R.string.account), account) {
            @Override
            public void onClick(View view) {
                super.onClick(view);
                doLogin();
            }
        });

        final Runnable updateSettings = new Runnable() {
            @Override
            public void run() {
                ListItem sendXmpp = findItem(R.string.google_talk);
                ListItem sendEmail = findItem(R.string.gmail);
                final boolean xmpp = sendXmpp.getIsChecked();
                final boolean mail = sendEmail.getIsChecked();
                new Thread() {
                    public void run() {
                        try {
                            ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
                            params.add(new BasicNameValuePair("forward_xmpp", String.valueOf(xmpp)));
                            params.add(new BasicNameValuePair("forward_email", String.valueOf(mail)));

                            HttpPost post = getAuthenticatedPost(new URI(SETTINGS_URL), params);
                            DefaultHttpClient client = new DefaultHttpClient();
                            HttpResponse res = client.execute(post);
                            Log.i(LOGTAG, "Status code from register: " + res.getStatusLine().getStatusCode());
                        }
                        catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    };
                }.start();
            }
        };

        addItem(R.string.notifications, new ListItem(this, R.string.gmail, 0) {
            {
                CheckboxVisible = true;
                setIsChecked(mSettings.getBoolean("forward_email", true));
            }

            @Override
            public void onClick(View view) {
                super.onClick(view);
                mSettings.setBoolean("forward_email", getIsChecked());
                mHandler.postDelayed(updateSettings, 2000);
            }
        });

        addItem(R.string.notifications, new ListItem(this, R.string.google_talk, 0) {
            {
                CheckboxVisible = true;
                setIsChecked(mSettings.getBoolean("forward_xmpp", true));
            }

            @Override
            public void onClick(View view) {
                super.onClick(view);
                mSettings.setBoolean("forward_xmpp", getIsChecked());
                mHandler.postDelayed(updateSettings, 2000);
            }
        });

        addItem(R.string.troubleshooting, new ListItem(this, R.string.test_message, 0) {
            @Override
            public void onClick(View view) {
                super.onClick(view);

                TelephonyManager tm = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE); 
                final String number = tm.getLine1Number();
                
                if (number == null || "".equals(number)) {
                    Helper.showAlertDialog(MainActivity.this, R.string.number_retrieval_error);
                    return;
                }
                
                boolean handcent = appExists("com.handcent.nextsms");
                boolean gosms = appExists("com.jb.gosms");
                
                final Runnable pre = new Runnable() {
                    @Override
                    public void run() {
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        builder.setTitle(R.string.test_message);
                        builder.setMessage(R.string.test_message_info);
                        builder.setNegativeButton(android.R.string.cancel, null);
                        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                                String message = getString(R.string.test_message_content);
                                SmsManager sm = SmsManager.getDefault();
                                sm.sendTextMessage(number, null, message, null, null);
                            }
                        });
                        builder.setCancelable(true);
                        builder.create().show();
                    }
                };
                
                if (handcent || gosms) {
                    AlertDialog.Builder builder = new Builder(MainActivity.this);
                    builder.setMessage(getString(R.string.msg_app_detected, getString(handcent ? R.string.handcent : R.string.gosms)));
                    builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            pre.run();
                        }
                    });
                    builder.setCancelable(true);
                    builder.create().show();
                }
                else {
                    pre.run();
                }
            }
        });
    }

    Callback<Tuple<Integer, Intent>> mActivityResultCallback;

    public void setOnActivityResultCallback(Callback<Tuple<Integer, Intent>> callback) {
        mActivityResultCallback = callback;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (mActivityResultCallback != null)
            mActivityResultCallback.onCallback(new Tuple<Integer, Intent>(requestCode, data));
    }
}
