package com.koushikdutta.desktopsms;

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
import android.view.View;

public class MainActivity extends ActivityBase implements ActivityResultDelegate {
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
                        String account = mSettings.getString("account");
                        if (Helper.isJavaScriptNullOrEmpty(account)) {
                            finish();
                            return;
                        }
                        
                        if (!result)
                            return;

                        ListItem acc = findItem(R.string.account);
                        acc.setSummary(account);
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
        builder.setCancelable(false);
        builder.create().show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        startService(new Intent(this, SyncService.class));
        
        String account = mSettings.getString("account");
        if (mSettings.getLong("last_missed_call", 0) == 0) {
            mSettings.setLong("last_missed_call", System.currentTimeMillis());
        }
        
        if (Helper.isJavaScriptNullOrEmpty(account)) {
            doLogin();
        }
        
        ServiceHelper.getSettings(this, new Callback<JSONObject>() {
            @Override
            public void onCallback(JSONObject result) {
                final boolean forward_xmpp = result.optBoolean("forward_xmpp", true);
                final boolean forward_email = result.optBoolean("forward_email", true);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ListItem gmail = findItem(R.string.gmail);
                        ListItem gtalk = findItem(R.string.google_talk);
                        gmail.setIsChecked(forward_email);
                        gtalk.setIsChecked(forward_xmpp);
                    }
                });
            }
        });

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
                ServiceHelper.updateSettings(MainActivity.this);
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

    Callback<Tuple<Integer, Tuple<Integer, Intent>>> mActivityResultCallback;

    public void setOnActivityResultCallback(Callback<Tuple<Integer, Tuple<Integer, Intent>>> callback) {
        mActivityResultCallback = callback;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (mActivityResultCallback != null)
            mActivityResultCallback.onCallback(new Tuple<Integer, Tuple<Integer, Intent>>(requestCode, new Tuple<Integer, Intent>(resultCode, data)));
    }
}
