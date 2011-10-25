package com.koushikdutta.desktopsms;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;

import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.net.http.AndroidHttpClient;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;

import com.clockworkmod.billing.ClockworkModBillingClient;
import com.clockworkmod.billing.ThreadingRunnable;

public class MainActivity extends ActivityBase implements ActivityResultDelegate {
    private static final String LOGTAG = MainActivity.class.getSimpleName();
    private Handler mHandler = new Handler();
    
    private void doLogin() {
        mSettings.setString("account", null);
        mSettings.setString("registration_id", null);
        mSettings.setLong("last_sms_sync", 0);
        mSettings.setLong("last_mms_sync", 0);
        mSettings.setLong("last_calls_sync", 0);
        mSettings.setBoolean("registered", false);
        mAccountItem.setSummary(null);
        mAccountItem.setTitle(null);

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

                        mAccountItem.setTitle(account);
                        ListItem gmail = findItem(R.string.gmail);
                        ListItem gtalk = findItem(R.string.google_talk);
                        gmail.setEnabled(true);
                        gtalk.setEnabled(true);

                        ListItem testMessage = findItem(R.string.test_message);
                        testMessage.setEnabled(true);

                        mSettings.setBoolean("registered", true);
                        mSettings.setLong("last_sms_sync", 0);
                        mSettings.setLong("last_mms_sync", 0);
                        mSettings.setLong("last_calls_sync", 0);
                        Helper.startSyncService(MainActivity.this, "sms");

                        refreshAccount();
                    }
                });
            }
        });
        builder.setCancelable(false);
        builder.create().show();
    }

    private void addDeskSmsContactInfo(final boolean onlyRemove) {
        final ProgressDialog dlg = new ProgressDialog(this);
        dlg.setMessage(getString(R.string.please_wait_contact_list));
        dlg.show();
        
        final String account = mSettings.getString("account");
        new Thread() {
            public void run() {
                try {
                    int deleted = getContentResolver().delete(ContactsContract.Data.CONTENT_URI, String.format("%s = '%s' and %s = 'DeskSMS'", Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE, CommonDataKinds.Email.LABEL), null);
                    if (onlyRemove)
                        return;

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
                            if (!numbers.containsKey(rawContact));
                                numbers.put(rawContact, number);
                        }
                    }
                    c.close();

                    ArrayList<ContentValues> cvs = new ArrayList<ContentValues>();
                    for (long rawContact: numbers.keySet()) {
                        String number = numbers.get(rawContact);
                        number = ServiceHelper.numbersOnly(number, false);
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
        addDeskSmsContactInfo(true);
    }

    ListItem mAccountItem;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String account = mSettings.getString("account");
        String registrationId = mSettings.getString("registration_id");

        if (mSettings.getLong("last_missed_call", 0) == 0) {
            mSettings.setLong("last_missed_call", System.currentTimeMillis());
        }
        
        mAccountItem = addItem(R.string.account, new ListItem(this, account, null) {
            @Override
            public void onClick(View view) {
                super.onClick(view);
                
                String account = mSettings.getString("account");
                String registrationId = mSettings.getString("registration_id");

                if (Helper.isJavaScriptNullOrEmpty(account) || Helper.isJavaScriptNullOrEmpty(registrationId)) {
                    doLogin();
                }
                else {
                    AlertDialog.Builder builder = new Builder(MainActivity.this);
                    builder.setTitle(R.string.account);
                    builder.setItems(new String[] { getString(R.string.buy_desksms), getString(R.string.switch_account) }, new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (which == 0) {
                                startBuy();
                            }
                            else if (which == 1){
                                doLogin();
                            }
                        }
                    });
                    builder.create().show();
                }
            }
        });

        final Runnable updateSettings = new Runnable() {
            @Override
            public void run() {
                final ListItem gmail = findItem(R.string.gmail);
                final ListItem gtalk = findItem(R.string.google_talk);
                final ListItem web = findItem(R.string.web);
                final ProgressDialog dialog = new ProgressDialog(MainActivity.this);
                dialog.setMessage(getString(R.string.updating_settings));
                dialog.show();
                dialog.setCancelable(false);
                ServiceHelper.updateSettings(MainActivity.this, gtalk.getIsChecked(), gmail.getIsChecked(), web.getIsChecked(), new Callback<Boolean>() {
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
                                    web.setIsChecked(mSettings.getBoolean("forward_web", true));
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

        addItem(R.string.notifications, new ListItem(this, R.string.web, 0) {
            {
                CheckboxVisible = true;
                setIsChecked(mSettings.getBoolean("forward_web", true));
            }
            
            @Override
            public void onClick(View view) {
                super.onClick(view);
                if (getIsChecked()) {
                    // reset the sync counter so it resends the sms history
                    mSettings.setLong("last_sms_sync", 0);
                    mSettings.setLong("last_mms_sync", 0);
                    mSettings.setLong("last_calls_sync", 0);
                    Helper.startSyncService(MainActivity.this);
                }
                updateSettings.run();
            }
        });
        
        /*
        addItem(R.string.notifications, new ListItem(this, R.string.disable_notifications, R.string.disable_notifications_summary2) {
            {
                CheckboxVisible = true;
                setIsChecked(mSettings.getBoolean("disable_sms_notifications", false));
            }
            
            @Override
            public void onClick(View view) {
                super.onClick(view);
                
                boolean disable = getIsChecked();
                mSettings.setBoolean("disable_sms_notifications", disable);
            } 
        });
        */
        addItem(R.string.contacts, new ListItem(this, R.string.manage_blacklist,0) {
        	@Override
            public void onClick(View view) {
                super.onClick(view);
                MainActivity.this.startActivity(new Intent(MainActivity.this, BlackListActivity.class));
            }
        });
  
        
        addItem(R.string.contacts, new ListItem(this, R.string.add_desksms_contact_info, R.string.add_desksms_contact_info_summary) {
            @Override
            public void onClick(View view) {
                super.onClick(view);
                addDeskSmsContactInfo(false);
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
        });
        
        addItem(R.string.troubleshooting, new ListItem(this, getString(R.string.adjust_sms_date), getAdjustmentString() + "\n" + getString(R.string.adjust_sms_date_summary)) {
            @Override
            public void onClick(View view) {
                super.onClick(view);
                
                ArrayList<String> adjusts = new ArrayList<String>();
                for (int adjust = -12; adjust < 13; adjust++) {
                    adjusts.add(getAdjustmentString(adjust));
                }
                
                String[] options = new String[adjusts.size()];
                options = adjusts.toArray(options);
                
                AlertDialog.Builder builder = new Builder(MainActivity.this);
                builder.setTitle(R.string.adjust_sms_date);
                builder.setItems(options, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, final int which) {
                        final ProgressDialog dlg = new ProgressDialog(MainActivity.this);
                        dlg.setMessage(getString(R.string.updating_settings));
                        dlg.show();

                        ThreadingRunnable.background(new ThreadingRunnable() {
                            @Override
                            public void run() {
                                try {
                                    AndroidHttpClient client = Helper.getHttpClient(MainActivity.this);
                                    try {
                                        String account = mSettings.getString("account");
                                        HttpDelete delete = new HttpDelete(String.format(ServiceHelper.SMS_URL, account));
                                        ServiceHelper.retryExecute(MainActivity.this, account, client, delete);
                                        foreground(new Runnable() {
                                            @Override
                                            public void run() {
                                                dlg.dismiss();
                                                int adjust = -12 + which;
                                                mSettings.setInt("adjust_sms_date", adjust);
                                                setSummary(getAdjustmentString() + "\n" + getString(R.string.adjust_sms_date_summary));
                                                mSettings.setLong("last_sms_sync", 0);
                                                Helper.startSyncService(MainActivity.this, "sms");
                                            }
                                        });
                                    }
                                    finally {
                                        client.close();
                                    }
                                }
                                catch (Exception ex) {
                                    ex.printStackTrace();
                                    foreground(new Runnable() {
                                        @Override
                                        public void run() {
                                            dlg.dismiss();
                                            Helper.showAlertDialog(MainActivity.this, R.string.updating_settings_error);
                                        }
                                    });
                                }
                            }
                        });
                    }
                });
                builder.create().show();
            }
        });

        Helper.startSyncService(this);

        Intent intent = getIntent();
        if (Helper.isJavaScriptNullOrEmpty(account) || Helper.isJavaScriptNullOrEmpty(registrationId)) {
            doLogin();
        }
        else if (intent != null && intent.getBooleanExtra("relogin", false)) {
            intent.removeExtra("relogin");
            doLogin();
        }
        else {
            refreshAccount();
            ClockworkModBillingClient.getInstance().refreshMarketPurchases();
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
    }

    Callback<Tuple<Integer, Tuple<Integer, Intent>>> mActivityResultCallback;

    public void setOnActivityResultCallback(Callback<Tuple<Integer, Tuple<Integer, Intent>>> callback) {
        mActivityResultCallback = callback;
    }
    
    String getAdjustmentString() {
        int adjust = mSettings.getInt("adjust_sms_date", 0);
        return getAdjustmentString(adjust);
    }
    
    String getAdjustmentString(int adjust) {
        if (adjust == 0)
            return getString(R.string.disabled);

        if (adjust == -1)
            return getString(R.string.adjust_hour, "-1");
        else if (adjust == 1)
            return getString(R.string.adjust_hour, "+1");
        else if (adjust < 0)
            return getString(R.string.adjust_hours, adjust);
        else
            return getString(R.string.adjust_hours, "+" + adjust);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (mActivityResultCallback != null)
            mActivityResultCallback.onCallback(new Tuple<Integer, Tuple<Integer, Intent>>(requestCode, new Tuple<Integer, Intent>(resultCode, data)));

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                refreshAccount();
            }
        }, 5000);
    }
    
    private void refreshAccount(long expiration) {
        String account = mSettings.getString("account");
        if (Helper.isJavaScriptNullOrEmpty(account)) {
            finish();
            return;
        }

        long daysLeft = (expiration - System.currentTimeMillis()) / 1000L / 60L / 60L / 24L;
        daysLeft = Math.max(0, daysLeft);
        mAccountItem.setSummary(getString(R.string.days_left, String.valueOf(daysLeft)));
    }
    
    
    private void refreshAccount() {
        mAccountItem.setSummary(R.string.retrieving_status);
        ThreadingRunnable.background(new ThreadingRunnable() {
            @Override
            public void run() {
                try {
                    final HttpGet get = new HttpGet(ServiceHelper.STATUS_URL);
                    final JSONObject payload = ServiceHelper.retryExecuteAsJSONObject(MainActivity.this, mSettings.getString("account"), get);
                    final long expiration = payload.getLong("subscription_expiration");
                    foreground(new Runnable() {
                        @Override
                        public void run() {
                            refreshAccount(expiration);
                        }
                    });
                }
                catch (Exception ex) {
                    foreground(new Runnable() {
                        @Override
                        public void run() {
                            mAccountItem.setSummary(R.string.status_error);
                        }
                    });
                    ex.printStackTrace();
                }
            }
        });
    }
    
    void startBuy() {
        ClockworkModBillingClient.getInstance().refreshMarketPurchases();
        final ProgressDialog dlg = new ProgressDialog(MainActivity.this);
        dlg.setMessage(getString(R.string.retrieving_status));
        dlg.show();
        ThreadingRunnable.background(new ThreadingRunnable() {
            @Override
            public void run() {
                try {
                    final HttpGet get = new HttpGet(ServiceHelper.STATUS_URL);
                    final JSONObject payload = ServiceHelper.retryExecuteAsJSONObject(MainActivity.this, mSettings.getString("account"), get);
                    final long expiration = payload.getLong("subscription_expiration");
                    foreground(new Runnable() {
                        @Override
                        public void run() {
                            refreshAccount(expiration);
                            dlg.dismiss();
                            Intent i = new Intent(MainActivity.this, BuyActivity.class);
                            i.putExtra("payload", payload.toString());
                            startActivityForResult(i, 2323);
                        }
                    });
                }
                catch (Exception ex) {
                    foreground(new Runnable() {
                        @Override
                        public void run() {
                            dlg.dismiss();
                            Helper.showAlertDialog(MainActivity.this, R.string.status_error);
                        }
                    });
                    ex.printStackTrace();
                }
            }
        });
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem buy = menu.add(R.string.buy_desksms);
        buy.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                startBuy();
                return true;
            }
        });
        return super.onCreateOptionsMenu(menu);
    }
}
