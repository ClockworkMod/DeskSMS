package com.koushikdutta.desktopsms;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
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
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory.Options;
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
import android.util.Base64;
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
    
    static class MmsImageType {
    }

    Hashtable<Class, CursorGetter> mapper = new Hashtable<Class, SyncService.CursorGetter>() {
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
            put(MmsImageType.class, new CursorGetter() {
                @Override
                public void get(Cursor c, JSONObject j, String name, int index) throws JSONException {
                    try {
                        j.put("skip", true);
                        int mmsId = c.getInt(c.getColumnIndex("_id"));

                        Cursor convo = getContentResolver().query(Uri.parse("content://mms/" + mmsId + "/addr/"), null, null, null, null);
                        if (!convo.moveToNext()) {
                            return;
                        }
                        j.put("number", convo.getString(convo.getColumnIndex("address")));
                        convo.close();

                        String selectionPart = "mid=" + mmsId;
                        Uri uri = Uri.parse("content://mms/part");
                        Cursor cPart = getContentResolver().query(uri, null,
                            selectionPart, null, null);
                        try {
                            while (cPart.moveToNext()) {
                                String partId = cPart.getString(cPart.getColumnIndex("_id"));
                                String type = cPart.getString(cPart.getColumnIndex("ct"));
                                if ("image/jpeg".equals(type) || "image/bmp".equals(type) || "image/gif".equals(type) || "image/jpg".equals(type)
                                        || "image/png".equals(type)) {
                                    String image = getMmsImage(partId);
                                    if (image != null) {
                                        j.put("image", image);
                                        j.remove("skip");
                                        return;
                                    }
                                }
                            }
                        }
                        finally {
                            cPart.close();
                        }
                    }
                    catch (Exception ex) {
                        ex.printStackTrace();
                    }

                }
            });
        }
    };
    
    String getMmsImage(String _id) {
        Uri partURI = Uri.parse("content://mms/part/" + _id);
        InputStream is = null;
        Bitmap bitmap = null;
        try {
            is = getContentResolver().openInputStream(partURI);
            BitmapFactory.Options options = new Options();
            options.inSampleSize = 6;
            bitmap = BitmapFactory.decodeStream(is, null, options);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(CompressFormat.PNG, 50, out);
            byte[] bytes = out.toByteArray();
            String imageString = Base64.encodeToString(bytes, 0);
            return imageString;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            if (is != null) {
                try {
                    is.close();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    Hashtable<String, Tuple<String, CursorGetter>> mmsmapper = new Hashtable<String, Tuple<String, CursorGetter>>() {
        {
            put("m_type", new Tuple<String, CursorGetter>("message", mapper.get(MmsImageType.class)));
            //put("seen", new Tuple<String, CursorGetter>("seen", mapper.get(int.class)));
            put("msg_box", new Tuple<String, CursorGetter>("type", mapper.get(SmsTypeMapper.class)));
            put("_id", new Tuple<String, CursorGetter>("id", mapper.get(long.class)));
            put("address", new Tuple<String, CursorGetter>("number", mapper.get(String.class)));
            put("read", new Tuple<String, CursorGetter>("read", mapper.get(boolean.class)));
            put("thread_id", new Tuple<String, CursorGetter>("thread_id", mapper.get(long.class)));
        }
    };

    Hashtable<String, Tuple<String, CursorGetter>> smsmapper = new Hashtable<String, Tuple<String, CursorGetter>>() {
        {
            put("body", new Tuple<String, CursorGetter>("message", mapper.get(String.class)));
            //put("seen", new Tuple<String, CursorGetter>("seen", mapper.get(int.class)));
            put("type", new Tuple<String, CursorGetter>("type", mapper.get(SmsTypeMapper.class)));
            put("_id", new Tuple<String, CursorGetter>("id", mapper.get(long.class)));
            put("address", new Tuple<String, CursorGetter>("number", mapper.get(String.class)));
            put("read", new Tuple<String, CursorGetter>("read", mapper.get(boolean.class)));
            put("thread_id", new Tuple<String, CursorGetter>("thread_id", mapper.get(long.class)));
        }
    };
    
    Hashtable<String, Tuple<String, CursorGetter>> callmapper = new Hashtable<String, Tuple<String, CursorGetter>>() {
        {
            put(Calls.TYPE, new Tuple<String, CursorGetter>("type", mapper.get(CallTypeMapper.class)));
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

        //if (!mAccount.equals("koush@koushikdutta.com"))
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
                Log.i(LOGTAG, sms.toString(4));
                maxOutboxSync = Math.max(maxOutboxSync, date);
                sendUsingSmsManager(this, number, message, date);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        mLastOutboxSync = maxOutboxSync;
        mSettings.setLong("last_outbox_sync", maxOutboxSync);
    }

    private void syncOutbox(String outbox) throws ClientProtocolException, OperationCanceledException, AuthenticatorException, IOException, URISyntaxException, JSONException {
        Log.i(LOGTAG, "================Checking outbox================");
        if (outbox == null) {
            AndroidHttpClient client = Helper.getHttpClient(this);
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
        Hashtable<String, Tuple<String, CursorGetter>> mapper;
        long dateScale = 1L;
        
        abstract void setSubject(JSONObject event, String displayName, Cursor cursor) throws JSONException;
        abstract void setMessage(JSONObject event, String displayName, Cursor cursor) throws JSONException;

        protected void logEnvelope(JSONObject envelope) throws JSONException {
            JSONArray data = envelope.getJSONArray("data");
            Log.i(LOGTAG, "Forwarding " + data.length() + " messages.");
            System.out.println(envelope.toString(4));
        }

        public void sync() throws Exception {
            long lastSync = mSettings.getLong(lastSyncSetting, 0);
            boolean isInitialSync = false;
            // i dont know if i want to do this, sync loop?
            //if (lastSync > System.currentTimeMillis())
            //    lastSync = 0;

            if (lastSync > 1309478400000L)
                lastSync = 0;

            Cursor c;
            if (lastSync == 0) {
                isInitialSync = true;
                // only grab 3 days worth
                //lastSync = System.currentTimeMillis() - 3L * 24L * 60L * 60L * 1000L;
                c = getContentResolver().query(contentProviderUri, null, "date > ?", new String[] { String.valueOf((System.currentTimeMillis() - 3L * 24L * 60L * 60L * 1000L) / dateScale) }, null);
            }
            else {
                c = getContentResolver().query(contentProviderUri, null, "_id > ?", new String[] { String.valueOf(lastSync) }, null);
                Cursor sanityCursor = getContentResolver().query(contentProviderUri, new String[] { "_id" }, null, null, "_id DESC");
                try {
                    if (sanityCursor.moveToNext()) {
                        long sanityId = sanityCursor.getLong(sanityCursor.getColumnIndex("_id"));
                        if (sanityId < lastSync) {
                            lastSync = sanityId;
                        }
                    }
                }
                finally {
                    sanityCursor.close();
                }
            }
            
            Log.i(LOGTAG, getClass().getSimpleName());
            Log.i(LOGTAG, String.valueOf(lastSync));

            String[] columnNames = c.getColumnNames();
            JSONArray eventArray = new JSONArray();
            long latestEvent = lastSync;
            int dateColumn = c.getColumnIndex("date");
            int idColumn = c.getColumnIndex("_id");
            while (c.moveToNext()) {
                try {
                    long date = c.getLong(dateColumn) * dateScale;
                    JSONObject event = new JSONObject();
                    event.put("date", date);
                    for (int i = 0; i < c.getColumnCount(); i++) {
                        String name = columnNames[i];
                        Tuple<String, CursorGetter> tuple = mapper.get(name);
                        if (tuple == null)
                            continue;
                        tuple.Second.get(c, event, tuple.First, i);
                    }
                    
                    if (event.optBoolean("skip", false))
                        continue;

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
                    if (event.getString("type").equals("incoming")) {
                        setSubject(event, displayName, c);
                        setMessage(event, displayName, c);
                    }
                    eventArray.put(event);
                    
                    long id = c.getLong(idColumn);
                    latestEvent = Math.max(id, latestEvent);
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
            envelope.put("this_last_sync", lastSync);
            envelope.put("next_last_sync", latestEvent);

            logEnvelope(envelope);

            StringEntity entity = new StringEntity(envelope.toString(), "utf-8");
            HttpPost post = ServiceHelper.getAuthenticatedPost(SyncService.this, String.format(postUrl, mAccount));
            post.setEntity(entity);
            AndroidHttpClient client = Helper.getHttpClient(SyncService.this);
            try {
                HttpResponse res = ServiceHelper.retryExecute(SyncService.this, mAccount, client, post);
                if (res == null)
                    throw new Exception("Unable to authenticate");
                String results = StreamUtility.readToEnd(res.getEntity().getContent());
                JSONObject sr = new JSONObject(results);
                if (!sr.optBoolean("registered", true)) {
                    mSettings.setBoolean("registered", false);
                    mSettings.setString("account", null);
                    mSettings.setString("registration_id", null);
                    throw new Exception("not registered");
                }
                System.out.println(results);
                mSettings.setLong(lastSyncSetting, latestEvent);
            }
            finally {
                client.close();
                c.close();
            }
        }

    }

    class SmsSync extends SyncBase {
        public SmsSync() {
            contentProviderUri = Uri.parse("content://sms");
            postUrl = ServiceHelper.SMS_URL;
            lastSyncSetting = "last_sms_sync";
            mapper = smsmapper;
        }

        @Override
        void setSubject(JSONObject event, String displayName, Cursor cursor) throws JSONException {
            event.put("subject", getString(R.string.sms_received, displayName));
        }

        @Override
        void setMessage(JSONObject event, String displayName, Cursor cursor) {
        }
    }

    class MmsSync extends SyncBase {
        public MmsSync() {
            dateScale = 1000;
            contentProviderUri = Uri.parse("content://mms/");
            postUrl = ServiceHelper.SMS_URL;
            lastSyncSetting = "last_mms_sync";
            mapper = mmsmapper;
        }

        @Override
        void setSubject(JSONObject event, String displayName, Cursor cursor) throws JSONException {
            event.put("subject", getString(R.string.mms_received, displayName));
        }

        @Override
        void setMessage(JSONObject event, String displayName, Cursor cursor) throws JSONException {
            event.put("message", getString(R.string.mms_received, displayName));
        }

        @Override
        protected void logEnvelope(JSONObject envelope) throws JSONException {
            JSONArray data = envelope.getJSONArray("data");
            Log.i(LOGTAG, "Forwarding MMS:");
            Log.i(LOGTAG, "Forwarding " + data.length() + " messages.");
        }
    }

    class CallSync extends SyncBase {
        public CallSync() {
            contentProviderUri = CallLog.Calls.CONTENT_URI;
            postUrl = ServiceHelper.CALL_URL;
            lastSyncSetting = "last_calls_sync";
            mapper = callmapper;
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

    long mLastOutboxSync;
    String mAccount;
    Handler mHandler = new Handler();
    Thread mSyncThread = null;
    String mPendingOutbox;
    long mSyncStart = 0;
    boolean mPendingOutboxSync;
    private void sync(final Intent intent) {
        Log.i(LOGTAG, "Version: " + DesktopSMSApplication.mVersionCode);
        
        // for the very first startup of the service, we set the first start as sms, to flush anything pending.
        final String reason = mFirstStart ? "sms" : intent.getStringExtra("reason");
        mFirstStart = false;
        
        // no reason? this is just a 15 min repeating wakeup call then.
        if (reason == null) {
            Log.i(LOGTAG, "No reaosn for sync");
            return;
        }
        Log.i(LOGTAG, "============= Sync Reason " + reason + "=============");
        
        boolean xmpp = mSettings.getBoolean("forward_xmpp", true);
        boolean email = mSettings.getBoolean("forward_email", true);
        boolean web = mSettings.getBoolean("forward_web", true);
        if (!xmpp && !email && !web) {
            Log.i(LOGTAG, "All forwarding options are disabled.");
            return;
        }

        mPendingOutbox = intent.getStringExtra("outbox");
        mPendingOutboxSync = "outbox".equals(reason);

        mSyncStart = System.currentTimeMillis();

        if (mSyncThread != null)
            return;

        String registrationId = mSettings.getString("registration_id");
        mAccount = mSettings.getString("account");
        boolean registered = mSettings.getBoolean("registered", true);
        if (mAccount == null || registrationId == null || !registered)
            return;
        mLastOutboxSync = mSettings.getLong("last_outbox_sync", 0);

        mSyncThread = new Thread() {
            @Override
            public void run() {
                try {
                    // if we are starting for the outbox, do that immediately
                    boolean startedForOutbox = mPendingOutboxSync;
                    boolean startedForPhoneState = "phone".equals(reason);
                    boolean startedForSms = "sms".equals(reason);
                    if (mPendingOutboxSync) {
                        mPendingOutboxSync = false;
                        syncOutbox(mPendingOutbox);
                        mPendingOutbox = null;
                    }

                    while (mSyncStart + 15000L > System.currentTimeMillis()) {
                        mSmsSyncer.sync();
                        mCallSyncer.sync();
                        mMmsSyncer.sync();

                        // however, if an outbox message comes in while we are polling,
                        // let's send it
                        if (mPendingOutboxSync) {
                            Log.i(LOGTAG, "================Outbox ping received================");
                            mPendingOutboxSync = false;
                            syncOutbox(mPendingOutbox);
                            mPendingOutbox = null;
                        }

                        Thread.sleep(3000);
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
    MmsSync mMmsSyncer = new MmsSync();
    CallSync mCallSyncer = new CallSync();
    
    ContentObserver mMmsObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            Intent intent = new Intent();
            intent.putExtra("reason", "sms");
            sync(intent);
        }
    };

    ContentObserver mSmsObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            Intent intent = new Intent();
            intent.putExtra("reason", "sms");
            sync(intent);
        }
    };
    
    ContentObserver mCallsObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            Intent intent = new Intent();
            intent.putExtra("reason", "phone");
            sync(intent);
        }
    };

    public void onDestroy() {
        getContentResolver().unregisterContentObserver(mSmsObserver);
        getContentResolver().unregisterContentObserver(mCallsObserver);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setForeground(true);
        mSettings = Settings.getInstance(this);
        
        getContentResolver().registerContentObserver(mSmsSyncer.contentProviderUri, true, mSmsObserver);
        getContentResolver().registerContentObserver(mCallSyncer.contentProviderUri, true, mCallsObserver);
    }

    boolean mFirstStart = true;
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(LOGTAG, "Service starting");
        if (intent != null)
            sync(intent);
        return super.onStartCommand(intent, flags, startId);
    }
}
