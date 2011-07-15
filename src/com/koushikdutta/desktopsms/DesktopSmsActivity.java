package com.koushikdutta.desktopsms;

import java.net.URI;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

public class DesktopSmsActivity extends Activity implements ActivityResultDelegate {
    private static final String LOGTAG = DesktopSmsActivity.class.getSimpleName();
    private Handler mHandler = new Handler();
    private static final String MESSAGE_URL = "https://desksms.appspot.com/settings";
    
    boolean mDestroyed = false;
    
    protected void onDestroy() {
        mDestroyed = true;
    }
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        final Settings settings = Settings.getInstance(this);

        final TextView forwarded = (TextView)findViewById(R.id.forwarded);
        final TextView proxied = (TextView)findViewById(R.id.proxied);
        
        final Runnable looper = new Runnable() {
            @Override
            public void run() {
                if (mDestroyed)
                    return;
                mHandler.postDelayed(this, 30000);
                
                forwarded.setText(getString(R.string.forwarded, settings.getString("forwarded", "0")));
                proxied.setText(getString(R.string.proxied, settings.getString("proxied", "0")));
            }
        };
        
        looper.run();
        
        
        final CheckBox sendEmail = (CheckBox)findViewById(R.id.send_email);
        final CheckBox sendXmpp = (CheckBox)findViewById(R.id.send_xmpp);
        
        sendEmail.setChecked(settings.getBoolean("forward_email", true));
        sendXmpp.setChecked(settings.getBoolean("forward_xmpp", true));
        
        final Runnable updateSettings = new Runnable() {
            @Override
            public void run() {
                final boolean xmpp = sendXmpp.isChecked();
                final boolean mail = sendEmail.isChecked();
                new Thread() {
                    public void run() {
                        try {
                            URI uri = new URI(MESSAGE_URL);
                            HttpPost post = new HttpPost(uri);
                            ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
                            params.add(new BasicNameValuePair("forward_xmpp", String.valueOf(xmpp)));
                            params.add(new BasicNameValuePair("forward_email", String.valueOf(mail)));

                            String ascidCookie = settings.getString("Cookie");
                            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params, "UTF-8");
                            post.setEntity(entity);
                            post.setHeader("Cookie", ascidCookie);
                            settings.setString("Cookie", ascidCookie);
                            post.setHeader("X-Same-Domain", "1"); // XSRF
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
        
        sendEmail.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mHandler.postDelayed(updateSettings, 2000);
                settings.setBoolean("forward_email", sendEmail.isChecked());
            }
        });
        
        sendXmpp.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mHandler.postDelayed(updateSettings, 2000);
                settings.setBoolean("forward_xmpp", sendXmpp.isChecked());
            }
        });
        
        String acc = settings.getString("account");
        final TextView currentAccount = (TextView)findViewById(R.id.current_account);
        currentAccount.setText(getString(R.string.current_account, acc));
        
        final Button testMessage = (Button)findViewById(R.id.test_message);
        testMessage.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                TelephonyManager tm = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE); 
                final String number = tm.getLine1Number();
                
                if (number == null || "".equals(number)) {
                    Helper.showAlertDialog(DesktopSmsActivity.this, R.string.number_retrieval_error);
                    return;
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(DesktopSmsActivity.this);
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
                builder.create().show();
            }
        });
        
        if (null == acc || "".equals(acc)) {
            currentAccount.setText(R.string.login_with_google);
            sendEmail.setEnabled(false);
            sendXmpp.setEnabled(false);
            testMessage.setEnabled(false);
        }

        Button login = (Button)findViewById(R.id.login);
        login.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(DesktopSmsActivity.this);
                builder.setTitle(R.string.permission_request);
                builder.setMessage(R.string.requesting_permissions);
                builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        TickleServiceHelper.login(DesktopSmsActivity.this, DesktopSmsActivity.this, new Callback<Boolean>() {
                            @Override
                            public void onCallback(Boolean result) {
                                if (!result)
                                    return;
                                
                                currentAccount.setText(getString(R.string.current_account, settings.getString("account")));
                                sendEmail.setEnabled(true);
                                sendXmpp.setEnabled(true);
                                testMessage.setEnabled(true);
                            }
                        });
                    }
                });
                builder.create().show();
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