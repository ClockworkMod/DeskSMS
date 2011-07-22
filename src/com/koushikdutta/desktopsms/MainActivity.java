package com.koushikdutta.desktopsms;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;

import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.ContactsContract.RawContacts;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
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

                        mSettings.setLong("last_sms_sync", 0);
                        if (mSettings.getBoolean("sync_sms", false))
                            startService(new Intent(MainActivity.this, SyncService.class));
                    }
                });
            }
        });
        builder.setCancelable(false);
        builder.create().show();
    }

    private void addDeskSmsContactInfo() {
        final ProgressDialog dlg = new ProgressDialog(this);
        dlg.setMessage(getString(R.string.please_wait_contact_list));
        dlg.show();
        
        final String account = mSettings.getString("account");
        new Thread() {
            public void run() {
                try {
                    int deleted = getContentResolver().delete(ContactsContract.Data.CONTENT_URI, String.format("%s = '%s' and %s = 'DeskSMS'", Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE, CommonDataKinds.Email.LABEL), null);

                    HashSet<Long> rawContacts = new HashSet<Long>();
                    Cursor c = getContentResolver().query(ContactsContract.RawContacts.CONTENT_URI, new String[] { RawContacts._ID }, String.format("%s = 'com.google' AND %s = '%s'", RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME, account), null, null);
                    int idColumn = c.getColumnIndex(RawContacts._ID);
                    while (c.moveToNext()) {
                        long id = c.getLong(idColumn);
                        rawContacts.add(id);
                    }
                    c.close();
                    
                    Hashtable<Long, String> numbers = new Hashtable<Long, String>();
                    c = getContentResolver().query(Data.CONTENT_URI, new String[] { Phone.NUMBER, Phone.TYPE, Data.RAW_CONTACT_ID }, String.format("%s = '%s' and (%s = '%s' or %s = '%s')", ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE, Phone.TYPE, Phone.TYPE_HOME, Phone.TYPE, Phone.TYPE_MOBILE), null, null);
                    int typeColumn = c.getColumnIndex(Phone.TYPE);
                    idColumn = c.getColumnIndex(Data.RAW_CONTACT_ID);
                    int numberColumn = c.getColumnIndex(Phone.NUMBER);
                    while (c.moveToNext()) {
                        long rawContact = c.getLong(idColumn);
                        if (!rawContacts.contains(rawContact))
                            continue;
                        int type = c.getInt(typeColumn);
                        String number = c.getString(numberColumn);
                        if (type == Phone.TYPE_MOBILE) {
                            numbers.put(rawContact, number);
                        }
                        else {
                            if (!numbers.contains(number))
                                numbers.put(rawContact, number);
                        }
                    }
                    c.close();
                    
                    
                    ArrayList<ContentValues> cvs = new ArrayList<ContentValues>();
                    for (long rawContact: numbers.keySet()) {
                        String number = numbers.get(rawContact);
                        number = ServiceHelper.numbersOnly(number);
                        ContentValues cv = new ContentValues();
                        cv.put(Data.RAW_CONTACT_ID, rawContact);
                        cv.put(Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE);
                        String email = String.format("%s@desksms.appspotmail.com", number);
                        cv.put(CommonDataKinds.Email.DATA, email);
                        cv.put(CommonDataKinds.Email.TYPE, CommonDataKinds.Email.TYPE_CUSTOM);
                        cv.put(CommonDataKinds.Email.LABEL, "DeskSMS");
                        cvs.add(cv);
                        
                        cv = new ContentValues();
                        cv.put(Data.RAW_CONTACT_ID, rawContact);
                        cv.put(Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE);
                        String xmpp = String.format("%s@desksms.appspotchat.com", number);
                        cv.put(CommonDataKinds.Email.DATA, xmpp);
                        cv.put(CommonDataKinds.Email.TYPE, CommonDataKinds.Email.TYPE_CUSTOM);
                        cv.put(CommonDataKinds.Email.LABEL, "DeskSMS");
                        cvs.add(cv);
                    }
                    
                    ContentValues[] cvsArray = new ContentValues[cvs.size()];
                    cvs.toArray(cvsArray);
                    getContentResolver().bulkInsert(Data.CONTENT_URI, cvsArray);
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
                finally {
                    dlg.dismiss();
                }
            };
        }.start();
    }
    
    private void removeDeskSmsContactInfo() {
        
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
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
        
        addItem(R.string.account, new ListItem(this, R.string.sync_sms, R.string.sync_sms_summary) {
            {
                CheckboxVisible = true;
                Settings settings = Settings.getInstance(MainActivity.this);
                setIsChecked(settings.getBoolean("sync_sms", false));
            }
            @Override
            public void onClick(View view) {
                super.onClick(view);
                Settings settings = Settings.getInstance(MainActivity.this);
                settings.setBoolean("sync_sms", getIsChecked());
                if (getIsChecked()) {
                    // reset the sync counter so it resends the sms history
                    settings.setLong("last_sms_sync", 0);
                    startService(new Intent(MainActivity.this, SyncService.class));
                }
            }
        });

        final Runnable updateSettings = new Runnable() {
            @Override
            public void run() {
                final ListItem gmail = findItem(R.string.gmail);
                final ListItem gtalk = findItem(R.string.google_talk);
                final ProgressDialog dialog = new ProgressDialog(MainActivity.this);
                dialog.setMessage(getString(R.string.updating_settings));
                dialog.show();
                dialog.setCancelable(false);
                ServiceHelper.updateSettings(MainActivity.this, gtalk.getIsChecked(), gmail.getIsChecked(), new Callback<Boolean>() {
                    @Override
                    public void onCallback(final Boolean result) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                dialog.dismiss();
                                if (!result) {
                                    Helper.showAlertDialog(MainActivity.this, R.string.updating_settings_error);
                                    gmail.setIsChecked(mSettings.getBoolean("forward_email", true));
                                    gtalk.setIsChecked(mSettings.getBoolean("forward_xmpp", true));
                                }
                            }
                        });
                    }
                });
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
                updateSettings.run();
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
                updateSettings.run();
            }
        });
        
        addItem(R.string.notifications, new ListItem(this, R.string.disable_notifications, R.string.disable_notifications_summary) {
            {
                CheckboxVisible = true;
                setIsChecked(mSettings.getBoolean("disable_notifications", false));
            }
            
            @Override
            public void onClick(View view) {
                super.onClick(view);
                mSettings.setBoolean("disable_notifications", getIsChecked());
            }
        });

        addItem(R.string.contacts, new ListItem(this, R.string.add_desksms_contact_info, R.string.add_desksms_contact_info_summary) {
            @Override
            public void onClick(View view) {
                super.onClick(view);
                addDeskSmsContactInfo();
            }
        });
        
        addItem(R.string.contacts, new ListItem(this, R.string.remove_desksms_contact_info, 0) {
            @Override
            public void onClick(View view) {
                super.onClick(view);
                removeDeskSmsContactInfo();
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
