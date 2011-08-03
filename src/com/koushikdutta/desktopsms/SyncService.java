package com.koushikdutta.desktopsms;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
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
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.PhoneLookup;
import android.telephony.SmsManager;
import android.text.format.DateFormat;
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
    
    public static final int INCOMING_CALL = 1;
    public static final int OUTGOING_CALL = 2;
    public static final int MISSED_CALL = 3;
    
    static class SmsTypeMapper extends Hashtable<Integer, String> {
        {
            put(OUTGOING_SMS, "outgoing");
            put(INCOMING_SMS, "incoming");
        }
        
        public static SmsTypeMapper Instance = new SmsTypeMapper();
    }
    
    static class CallTypeMapper extends Hashtable<Integer, String> {
        {
            put(MISSED_CALL, "missed");
            put(OUTGOING_CALL, "outgoing");
            put(INCOMING_CALL, "incoming");
        }
        
        public static CallTypeMapper Instance = new CallTypeMapper();
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
            put(SmsTypeMapper.class, new CursorGetter() {
                @Override
                public void get(Cursor c, JSONObject j, String name, int index) throws JSONException {
                    j.put(name, SmsTypeMapper.Instance.get(c.getInt(index)));
                }
            });
            put(CallTypeMapper.class, new CursorGetter() {
                @Override
                public void get(Cursor c, JSONObject j, String name, int index) throws JSONException {
                    j.put(name, CallTypeMapper.Instance.get(c.getInt(index)));
                }
            });
        }
    };
    
    
    static Hashtable<String, Tuple<String, CursorGetter>> smsmapper = new Hashtable<String, Tuple<String, CursorGetter>>() {
        {
            put("body", new Tuple<String, CursorGetter>("message", mapper.get(String.class)));
            //put("seen", new Tuple<String, CursorGetter>("seen", mapper.get(int.class)));
            put("type", new Tuple<String, CursorGetter>("type", mapper.get(SmsTypeMapper.class)));
            put("date", new Tuple<String, CursorGetter>("date", mapper.get(long.class)));
            put("_id", new Tuple<String, CursorGetter>("id", mapper.get(long.class)));
            put("address", new Tuple<String, CursorGetter>("number", mapper.get(String.class)));
            put("read", new Tuple<String, CursorGetter>("read", mapper.get(boolean.class)));
            put("thread_id", new Tuple<String, CursorGetter>("thread_id", mapper.get(long.class)));
        }
    };
    
    static Hashtable<String, Tuple<String, CursorGetter>> callmapper = new Hashtable<String, Tuple<String, CursorGetter>>() {
        {
            put(Calls.TYPE, new Tuple<String, CursorGetter>("type", mapper.get(CallTypeMapper.class)));
            put(Calls.DATE, new Tuple<String, CursorGetter>("date", mapper.get(long.class)));
            put(Calls._ID, new Tuple<String, CursorGetter>("id", mapper.get(long.class)));
            put(Calls.NUMBER, new Tuple<String, CursorGetter>("number", mapper.get(String.class)));
            put(Calls.DURATION, new Tuple<String, CursorGetter>("duration", mapper.get(int.class)));
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
                        lookup.enteredNumber = ServiceHelper.numbersOnly(enteredNumber, true);
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

    void sendUsingSmsManager(Context context, String number, String message, long date) {
        SmsManager sm = SmsManager.getDefault();
        ArrayList<String> messages = sm.divideMessage(message);
        int messageCount = messages.size();
        if (messageCount == 0)
            return;

        sm.sendMultipartTextMessage(number, null, messages, null, null);
        ContentValues values = new ContentValues();
        values.put("address", number);
        values.put("body", message);
        values.put("type", SyncService.OUTGOING_SMS);
        values.put("date", date);
        values.put("read", 1);
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
                sendUsingSmsManager(this, number, message, date);
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
            AndroidHttpClient client = AndroidHttpClient.newInstance(getString(R.string.app_name) + "/" + DesktopSMSApplication.mVersionCode);
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
    
    private abstract class SyncBase {
        Uri contentProviderUri;
        String postUrl;
        String lastSyncSetting;
        boolean sync;
        Hashtable<String, Tuple<String, CursorGetter>> mapper;
        int messageEventType;
        
        abstract void setSubject(JSONObject event, String displayName, Cursor cursor) throws JSONException;
        abstract void setMessage(JSONObject event, String displayName, Cursor cursor) throws JSONException;
        
        public void sync() throws Exception {
            long lastSync = mSettings.getLong(lastSyncSetting, 0);
            boolean isInitialSync = false;
            if (lastSync == 0) {
                isInitialSync = true;
                // only grab 3 days worth
                lastSync = System.currentTimeMillis() - 3L * 24L * 60L * 60L * 1000L;
            }
            Cursor c = getContentResolver().query(contentProviderUri, null, "date > ?", new String[] { String.valueOf(lastSync) }, null);

            String[] columnNames = c.getColumnNames();
            JSONArray eventArray = new JSONArray();
            long latestEvent = lastSync;
            int dateColumn = c.getColumnIndex("date");
            int typeColumn = c.getColumnIndex("type");
            while (c.moveToNext()) {
                try {
                    JSONObject event = new JSONObject();
                    for (int i = 0; i < c.getColumnCount(); i++) {
                        String name = columnNames[i];
                        Tuple<String, CursorGetter> tuple = mapper.get(name);
                        if (tuple == null)
                            continue;
                        tuple.Second.get(c, event, tuple.First, i);
                    }
                    
                    String number = event.getString("number");
                    CachedPhoneLookup lookup = getPhoneLookup(number);
                    String displayName;
                    if (lookup != null) {
                        displayName = lookup.displayName;
                        event.put("name", lookup.displayName);
                        event.put("entered_number", lookup.enteredNumber);
                        event.put("has_desksms_contact", lookup.hasDeskSMSContact);
                    }
                    else {
                        displayName = number;
                    }

                    // only incoming events needs to be marked up with the subject and optionally a message (no-op for sms)
                    if (c.getInt(typeColumn) == messageEventType) {
                        setSubject(event, displayName, c);
                        setMessage(event, displayName, c);
                    }
                    else {
                        // if we are not syncing, do not bother sending the outgoing message history
                        if (!sync)
                            continue;
                    }
                    eventArray.put(event);
                    long date = c.getLong(dateColumn);
                    latestEvent = Math.max(date, latestEvent);
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            if (eventArray.length() == 0) {
                Log.i(LOGTAG, "================No new messages================");
                return;
            }
            Log.i(LOGTAG, "================Forwarding inbox================");

            JSONObject envelope = new JSONObject();
            envelope.put("is_initial_sync", isInitialSync);
            envelope.put("data", eventArray);
            envelope.put("version_code", DesktopSMSApplication.mVersionCode);
            envelope.put("sync", sync);
            System.out.println(envelope.toString(4));
            StringEntity entity = new StringEntity(envelope.toString(), "utf-8");
            HttpPost post = ServiceHelper.getAuthenticatedPost(SyncService.this, String.format(postUrl, mAccount));
            post.setEntity(entity);
            AndroidHttpClient client = AndroidHttpClient.newInstance(getString(R.string.app_name) + "/" + DesktopSMSApplication.mVersionCode);
            try {
                HttpResponse res = ServiceHelper.retryExecute(SyncService.this, mAccount, client, post);
                if (res == null)
                    throw new Exception("Unable to authenticate");
                String results = StreamUtility.readToEnd(res.getEntity().getContent());
                System.out.println(results);
                mSettings.setLong(lastSyncSetting, latestEvent);
            }
            finally {
                client.close();
            }
        }

    }
    
    class SmsSync extends SyncBase {
        public SmsSync() {
            contentProviderUri = Uri.parse("content://sms");
            postUrl = ServiceHelper.SMS_URL;
            lastSyncSetting = "last_sms_sync";
            mapper = smsmapper;
            messageEventType = INCOMING_SMS;
        }
        
        @Override
        public void sync() throws Exception {
            sync = mSyncSms;
            super.sync();
        }

        @Override
        void setSubject(JSONObject event, String displayName, Cursor cursor) throws JSONException {
            event.put("subject", getString(R.string.sms_received, displayName));
        }

        @Override
        void setMessage(JSONObject event, String displayName, Cursor cursor) {
        }
    }

    class CallSync extends SyncBase {
        public CallSync() {
            contentProviderUri = CallLog.Calls.CONTENT_URI;
            postUrl = ServiceHelper.CALL_URL;
            lastSyncSetting = "last_calls_sync";
            mapper = callmapper;
            messageEventType = MISSED_CALL;
        }
        
        @Override
        public void sync() throws Exception {
            sync = mSyncCalls;
            super.sync();
        }

        @Override
        void setSubject(JSONObject event, String displayName, Cursor cursor) throws JSONException {
            event.put("subject", getString(R.string.missed_call_from, displayName));
        }

        @Override
        void setMessage(JSONObject event, String displayName, Cursor cursor) throws JSONException {
            long date = event.getLong("date");
            java.text.DateFormat df = DateFormat.getTimeFormat(SyncService.this);
            String dateString = df.format(new Date(date));
            event.put("message", getString(R.string.missed_call_at, dateString));
        }
        
    }

    boolean mSyncSms;
    boolean mSyncCalls;
    long mLastOutboxSync;
    String mAccount;
    Handler mHandler = new Handler();
    Thread mSyncThread = null;
    String mPendingOutbox;
    boolean mPendingOutboxSync;
    private void sync(final Intent intent) {
        if (intent != null) {
            mPendingOutbox = intent.getStringExtra("outbox");
            mPendingOutboxSync = "outbox".equals(intent.getStringExtra("reason"));
        }
        else {
            Log.e(LOGTAG, "!!!!!!!!!!!!!!!!!!!!!!Intent to SyncService is null???!!!!!!!!!!!!!!!!!!!!!!");
            return;
        }
        if (mSyncThread != null)
            return;

        mAccount = mSettings.getString("account");
        if (mAccount == null)
            return;
        mLastOutboxSync = mSettings.getLong("last_outbox_sync", 0);
        mSyncSms = mSettings.getBoolean("sync_sms", false);
        mSyncCalls = mSettings.getBoolean("sync_calls", false);

        mSyncThread = new Thread() {
            @Override
            public void run() {
                try {
                    // if we are starting for the outbox, do that immediately
                    boolean startedForOutbox = mPendingOutboxSync;
                    boolean startedForPhoneState = "phone".equals(intent.getStringExtra("reason"));
                    boolean startedForSms = "sms".equals(intent.getStringExtra("reason"));
                    if (mPendingOutboxSync) {
                        mPendingOutboxSync = false;
                        syncOutbox(mPendingOutbox);
                        mPendingOutbox = null;
                    }

                    // and poll against the sms content provider 10 times
                    for (int i = 0; i < 5; i++) {
                        mSmsSyncer.sync();
                        mCallSyncer.sync();

                        // however, if an outbox message comes in while we are polling,
                        // let's send it
                        if (mPendingOutboxSync) {
                            Log.i(LOGTAG, "================Outbox ping received================");
                            mPendingOutboxSync = false;
                            syncOutbox(mPendingOutbox);
                            mPendingOutbox = null;
                        }
                        Thread.sleep(2000);
                    }

                    // if we did not start for the outbox, sync it now just in case
                    // but don't do it on phone state change.
                    if (startedForSms) {
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
    
    SmsSync mSmsSyncer = new SmsSync();
    CallSync mCallSyncer = new CallSync();
    
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
