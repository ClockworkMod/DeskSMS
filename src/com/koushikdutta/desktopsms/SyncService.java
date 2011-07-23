package com.koushikdutta.desktopsms;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Hashtable;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.os.Handler;
import android.os.IBinder;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.PhoneLookup;
import android.telephony.SmsManager;
import android.util.Log;

public class SyncService extends Service {
    private Settings mSettings;
    private static final String LOGTAG = SyncService.class.getSimpleName();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    static interface CursorGetter {
        void get(Cursor c, JSONObject j, String name, int index) throws JSONException;
    }
    
    public static final int INCOMING_SMS = 1;
    public static final int OUTGOING_SMS = 2;
    
    static class TypeMapper extends Hashtable<Integer, String> {
        {
            put(OUTGOING_SMS, "outgoing");
            put(INCOMING_SMS, "incoming");
        }
        
        public static TypeMapper Instance = new TypeMapper();
    }
    
    static Hashtable<Class, CursorGetter> mapper = new Hashtable<Class, SyncService.CursorGetter>() {
        {
            put(int.class, new CursorGetter() {
                @Override
                public void get(Cursor c, JSONObject j, String name, int index) throws JSONException {
                    j.put(name, c.getInt(index));
                }
            });
            put(long.class, new CursorGetter() {
                @Override
                public void get(Cursor c, JSONObject j, String name, int index) throws JSONException {
                    j.put(name, c.getLong(index));
                }
            });
            put(String.class, new CursorGetter() {
                @Override
                public void get(Cursor c, JSONObject j, String name, int index) throws JSONException {
                    j.put(name, c.getString(index));
                }
            });
            put(boolean.class, new CursorGetter() {
                @Override
                public void get(Cursor c, JSONObject j, String name, int index) throws JSONException {
                    j.put(name, c.getInt(index) != 0);
                }
            });
            put(TypeMapper.class, new CursorGetter() {
                @Override
                public void get(Cursor c, JSONObject j, String name, int index) throws JSONException {
                    j.put(name, TypeMapper.Instance.get(c.getInt(index)));
                }
            });
        }
    };
    
    static Hashtable<String, Tuple<String, CursorGetter>> smsmapper = new Hashtable<String, Tuple<String, CursorGetter>>() {
        {
            put("body", new Tuple<String, CursorGetter>("message", mapper.get(String.class)));
            //put("seen", new Tuple<String, CursorGetter>("seen", mapper.get(int.class)));
            put("type", new Tuple<String, CursorGetter>("type", mapper.get(TypeMapper.class)));
            put("date", new Tuple<String, CursorGetter>("date", mapper.get(long.class)));
            put("_id", new Tuple<String, CursorGetter>("id", mapper.get(long.class)));
            put("address", new Tuple<String, CursorGetter>("number", mapper.get(String.class)));
            put("read", new Tuple<String, CursorGetter>("read", mapper.get(boolean.class)));
            put("thread_id", new Tuple<String, CursorGetter>("thread_id", mapper.get(long.class)));
        }
    };
    
    CachedPhoneLookup getPhoneLookup(String number) {
        try {
            CachedPhoneLookup lookup = mLookup.get(number);
            if (lookup != null)
                return lookup;
            Uri curi = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
            Cursor c = getContentResolver().query(curi, null, null, null, null);
            
            if (c != null) {
                if (c.moveToNext()) {
                    String displayName = c.getString(c.getColumnIndex(PhoneLookup.DISPLAY_NAME));
                    String enteredNumber = c.getString(c.getColumnIndex(PhoneLookup.NUMBER));
                    if (!Helper.isJavaScriptNullOrEmpty(displayName)) {
                        c.close();
                        lookup = new CachedPhoneLookup();
                        lookup.displayName = displayName;
                        lookup.enteredNumber = ServiceHelper.numbersOnly(enteredNumber);
                        // see if the user has a jabber contact for this address
                        String jid = String.format("%s@desksms.appspotchat.com", lookup.enteredNumber);
                        c = getContentResolver().query(Data.CONTENT_URI, new String[] { Data._ID }, String.format("%s = '%s' and %s = '%s'", Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE, CommonDataKinds.Email.DATA, jid), null, null);
                        lookup.hasDeskSMSContact = c.moveToNext();
                        c.close();
                        mLookup.put(number, lookup);
                        return lookup;
                    }
                }
                c.close();
            }
        }
        catch (Exception ex) {
        }
        return null;
    }
    
    private static final class CachedPhoneLookup {
        public String displayName;
        public String enteredNumber;
        public boolean hasDeskSMSContact;
    }
    
    Hashtable<String, CachedPhoneLookup> mLookup = new Hashtable<String, CachedPhoneLookup>();

    void sendUsingSmsManager(Context context, String number, String message) {
        SmsManager sm = SmsManager.getDefault();
        ArrayList<String> messages = sm.divideMessage(message);
        int messageCount = messages.size();
        if (messageCount == 0)
            return;

        sm.sendMultipartTextMessage(number, null, messages, null, null);
        ContentValues values = new ContentValues();
        values.put("address", number);
        values.put("body", message);
        context.getContentResolver().insert(Uri.parse("content://sms/sent"), values);
    }
    
    void sendUsingContentProvider(Context context, String number, String message) throws Exception {
        ContentResolver r = context.getContentResolver();
        //ContentProviderClient client = r.acquireContentProviderClient(Uri.parse("content://sms/queued"));
        ContentValues sv = new ContentValues();
        sv.put("address", number);
        sv.put("date", System.currentTimeMillis());
        sv.put("read", 1);
        sv.put("body", message);
        sv.put("type", SyncService.OUTGOING_SMS);
        String n = null;
        sv.put("subject", n);
        //sv.put("status", 32);
        Uri u = r.insert(Uri.parse("content://sms/queued"), sv);
        if (u == null) {
            throw new Exception();
        }
        Intent bcast = new Intent("com.android.mms.transaction.SEND_MESSAGE");
        bcast.setClassName("com.android.mms", "com.android.mms.transaction.SmsReceiverService");
        context.startService(bcast);
    }

    private void sendOutbox(String outboxData) throws ClientProtocolException, OperationCanceledException, AuthenticatorException, IOException, URISyntaxException, JSONException {
        long maxOutboxSync = mLastOutboxSync;
        // the outbox MUST come in order, from the oldest to the newest.
        // TODO: this should be sorted just to sanity check I guess.
        JSONArray outbox;
        // LEGACY: we should always expect an envelope
        try {
            outbox = new JSONArray(outboxData);
        }
        catch (Exception ex){
            JSONObject envelope = new JSONObject(outboxData);
            outbox = envelope.getJSONArray("data");
        }
        
        if (outbox.length() == 0) {
            Log.i(LOGTAG, "Empty outbox");
            return;
        }

        Log.i(LOGTAG, "================Sending outbox================");
        //Log.i(LOGTAG, outbox.toString(4));
        for (int i = 0; i < outbox.length(); i++) {
            try {
                JSONObject sms = outbox.getJSONObject(i);
                String number = sms.getString("number");
                String message = sms.getString("message");
                // make sure that any messages we get are new messages
                long date = sms.getLong("date");
                if (date <= mLastOutboxSync)
                    continue;
                //Log.i(LOGTAG, sms.toString(4));
                maxOutboxSync = Math.max(maxOutboxSync, date);
                //sendUsingSmsManager(this, number, message);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        mLastOutboxSync = maxOutboxSync;
        mSettings.setLong("last_outbox_sync", maxOutboxSync);

        final long max = maxOutboxSync;
        AndroidHttpClient client = AndroidHttpClient.newInstance(getString(R.string.app_name) + "." + DesktopSMSApplication.mVersionCode);
        try {
            HttpDelete delete = new HttpDelete(String.format(ServiceHelper.OUTBOX_URL, mAccount) + "?max_date=" + max);
            ServiceHelper.retryExecute(this, mAccount, client, delete);
        }
        finally {
            client.close();
        }
    }

    private void syncOutbox(String outbox) throws ClientProtocolException, OperationCanceledException, AuthenticatorException, IOException, URISyntaxException, JSONException {
        if (outbox == null) {
            AndroidHttpClient client = AndroidHttpClient.newInstance(getString(R.string.app_name) + "." + DesktopSMSApplication.mVersionCode);
            try {
                HttpGet get = new HttpGet(String.format(ServiceHelper.OUTBOX_URL, mAccount) + "?min_date=" + mLastOutboxSync);
                ServiceHelper.addAuthentication(SyncService.this, get);
                HttpResponse res = client.execute(get);
                outbox = StreamUtility.readToEnd(res.getEntity().getContent());
                sendOutbox(outbox);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
            finally {
                client.close();
            }
        }
        else {
            sendOutbox(outbox);
        }
    }

    private void syncSms() throws Exception {
        long lastSmsSync = mSettings.getLong("last_sms_sync", 0);
        boolean isInitialSync = false;
        if (lastSmsSync == 0) {
            isInitialSync = true;
            // only grab 3 days worth
            lastSmsSync = System.currentTimeMillis() - 3L * 24L * 60L * 60L * 1000L;
        }
        Cursor c = getContentResolver().query(Uri.parse("content://sms"), null, "date > ?", new String[] { String.valueOf(lastSmsSync) }, null);

        String[] columnNames = c.getColumnNames();
        JSONArray smsArray = new JSONArray();
        long latestSms = lastSmsSync;
        int dateColumn = c.getColumnIndex("date");
        int addressColumn = c.getColumnIndex("address");
        int typeColumn = c.getColumnIndex("type");
        while (c.moveToNext()) {
            try {
                JSONObject sms = new JSONObject();
                for (int i = 0; i < c.getColumnCount(); i++) {
                    String name = columnNames[i];
                    Tuple<String, CursorGetter> tuple = smsmapper.get(name);
                    if (tuple == null)
                        continue;
                    tuple.Second.get(c, sms, tuple.First, i);
                }
                // only incoming SMS needs to be marked up with the display name and subject
                if (c.getInt(typeColumn) == INCOMING_SMS) {
                    String number = c.getString(addressColumn);
                    CachedPhoneLookup lookup = getPhoneLookup(number);
                    String displayName;
                    if (lookup != null) {
                        displayName = lookup.displayName;
                        sms.put("name", lookup.displayName);
                        sms.put("entered_number", lookup.enteredNumber);
                        sms.put("has_desksms_contact", lookup.hasDeskSMSContact);
                    }
                    else {
                        displayName = number;
                    }
                    sms.put("subject", getString(R.string.sms_received, displayName));
                }
                else {
                    // if we are not syncing, do not bother sending the outgoing message history
                    if (!mSyncSms)
                        continue;
                }
                smsArray.put(sms);
                long date = c.getLong(dateColumn);
                latestSms = Math.max(date, latestSms);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        if (smsArray.length() == 0) {
            Log.i(LOGTAG, "No new messages");
            return;
        }

        JSONObject envelope = new JSONObject();
        envelope.put("is_initial_sync", isInitialSync);
        envelope.put("data", smsArray);
        envelope.put("version_code", DesktopSMSApplication.mVersionCode);
        envelope.put("sync", mSyncSms);
        System.out.println(envelope.toString(4));
        StringEntity entity = new StringEntity(envelope.toString(), "utf-8");
        HttpPost post = ServiceHelper.getAuthenticatedPost(this, String.format(ServiceHelper.SMS_URL, mAccount));
        post.setEntity(entity);
        AndroidHttpClient client = AndroidHttpClient.newInstance(getString(R.string.app_name) + "." + DesktopSMSApplication.mVersionCode);
        try {
            HttpResponse res = ServiceHelper.retryExecute(this, mAccount, client, post);
            if (res == null)
                throw new Exception("Unable to authenticate");
            String results = StreamUtility.readToEnd(res.getEntity().getContent());
            System.out.println(results);
            mSettings.setLong("last_sms_sync", latestSms);
        }
        finally {
            client.close();
        }
    }

    boolean mSyncSms;
    long mLastOutboxSync;
    String mAccount;
    Handler mHandler = new Handler();
    Thread mSyncThread = null;
    String mPendingOutbox;
    boolean mPendingOutboxSync;
    private void sync(Intent intent) {
        mPendingOutbox = intent.getStringExtra("outbox");
        mPendingOutboxSync = "outbox".equals(intent.getStringExtra("reason"));
        if (mSyncThread != null)
            return;

        mAccount = mSettings.getString("account");
        if (mAccount == null)
            return;
        mLastOutboxSync = mSettings.getLong("last_outbox_sync", 0);
        mSyncSms = mSettings.getBoolean("sync_sms", false);

        mSyncThread = new Thread() {
            @Override
            public void run() {
                try {
                    // if we are starting for the outbox, do that immediately
                    boolean startedForOutbox = mPendingOutboxSync;
                    if (mPendingOutboxSync) {
                        mPendingOutboxSync = false;
                        syncOutbox(mPendingOutbox);
                        mPendingOutbox = null;
                    }

                    // and poll against the sms content provider 10 times
                    for (int i = 0; i < 10; i++) {
                        syncSms();

                        // however, if an outbox message comes in while we are polling,
                        // let's send it
                        if (mPendingOutboxSync) {
                            Log.i(LOGTAG, "================Outbox ping received================");
                            mPendingOutboxSync = false;
                            syncOutbox(mPendingOutbox);
                            mPendingOutbox = null;
                        }
                        Thread.sleep(1000);
                    }

                    // if we did not start for the outbox, sync it now just in case
                    if (!startedForOutbox) {
                        syncOutbox(mPendingOutbox);
                        mPendingOutbox = null;
                    }
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
                finally {
                    mHandler.post(new Runnable() {
                       @Override
                        public void run() {
                           mSyncThread = null;
                        }
                    });
                }
            }
        };
        mSyncThread.start();
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        mSettings = Settings.getInstance(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        sync(intent);
        return super.onStartCommand(intent, flags, startId);
    }
}
