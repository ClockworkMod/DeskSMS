package com.koushikdutta.tabletsms;

import java.net.URL;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.PhoneLookup;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

public class MainActivity extends Activity {
    private static final String LOGTAG = "TabletSms";
    private static class Message implements Comparable<Message> {
        String key;
        String number;
        long date;
        String type;
        String image;
        String message;
        boolean unread;
        @Override
        public int compareTo(Message another) {
            return new Long(date).compareTo(another.date);
        }
    }
    
    public static class Conversation implements Comparable<Conversation> {
        public Conversation(String number) {
            this.number = number;
        }
        String number;
        LinkedHashMap<String, Message> messages = new LinkedHashMap<String, Message>();

        @Override
        public int compareTo(Conversation another) {
            return new Long(another.last).compareTo(last);
        }
        
        boolean unread = false;
        long last = 0;
        String lastMessageText = "";
    }
    
    Conversation mCurrentConversation;
    long mLastLoaded = 0;

    Settings mSettings;
    SQLiteDatabase mDatabase;
    
    ArrayAdapter<Conversation> mConversations;
    ArrayAdapter<Message> mConversation;
    
    private void merge(LinkedHashMap<String, Message> newMessages) {
        final Handler handler = new Handler();
        for (Entry<String, Message> entry: newMessages.entrySet()) {
            Conversation found = null;
            for (int i = 0; i < mConversations.getCount(); i++) {
                Conversation conversation = mConversations.getItem(i);
                if (!NumberHelper.areSimilar(conversation.number, entry.getValue().number))
                    continue;
                found = conversation;
                break;
            }
            if (found == null) {
                found = new Conversation(entry.getValue().number);
            }
            else {
                mConversations.remove(found);
            }
            mConversations.insert(found, 0);
            Message existing = found.messages.put(entry.getKey(), entry.getValue());
            found.last = Math.max(found.last, entry.getValue().date);
            found.lastMessageText = entry.getValue().message;
            found.unread |= entry.getValue().unread;
            
            if (found == mCurrentConversation) {
                messages.setTranscriptMode(ListView.TRANSCRIPT_MODE_DISABLED);
                if (existing != null) {
                    mConversation.remove(existing);
                }
                mConversation.add(entry.getValue());
                scrollToEnd();
            }
        }
        mConversations.notifyDataSetChanged();
        if (mCurrentConversation != null)
            markRead(mCurrentConversation);
    }
    
    private boolean loading = false;
    private void load() {
        if (loading)
            return;
        loading = true;
        final Handler handler = new Handler();
        new Thread() {
            public void run() {
                try {
                    Cursor c = mDatabase.query("sms", null, "date > ?", new String[] { ((Long)mLastLoaded).toString() }, null, null, "date");
                    final LinkedHashMap<String, Message> newMessages = new LinkedHashMap<String, MainActivity.Message>();
                    int keyIndex = c.getColumnIndex("key");
                    int numberIndex = c.getColumnIndex("number");
                    int dateIndex = c.getColumnIndex("date");
                    int typeIndex = c.getColumnIndex("type");
                    int imageIndex = c.getColumnIndex("image");
                    int messageIndex = c.getColumnIndex("message");
                    int unreadIndex = c.getColumnIndex("unread");
                    while (c.moveToNext()) {
                        Message message = new Message();
                        message.key = c.getString(keyIndex);
                        message.number = c.getString(numberIndex);
                        message.date = c.getLong(dateIndex);
                        message.type = c.getString(typeIndex);
                        message.image = c.getString(imageIndex);
                        message.message = c.getString(messageIndex);
                        message.unread = c.getInt(unreadIndex) != 0;
                        mLastLoaded = Math.max(mLastLoaded, message.date);
                        newMessages.put(message.key, message);
                    }
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            merge(newMessages);
                        }
                    });
                }
                catch (Exception ex) {
                }
                loading = false;
            };
        }.start();
    }
    
    private static final class CachedPhoneLookup {
        String displayName;
        String enteredNumber;
        String photoUri;
    }
    Hashtable<String, CachedPhoneLookup> mLookup = new Hashtable<String, CachedPhoneLookup>();

    CachedPhoneLookup getPhoneLookup(String number) {
        if (number == null)
            return null;
        try {
            CachedPhoneLookup lookup = mLookup.get(number);
            if (lookup != null)
                return lookup;
            Uri curi = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
            Cursor c = getContentResolver().query(curi, null, null, null, null);
            try {
                if (c.moveToNext()) {
                    String displayName = c.getString(c.getColumnIndex(PhoneLookup.DISPLAY_NAME));
                    String enteredNumber = c.getString(c.getColumnIndex(PhoneLookup.NUMBER));
                    long userId = c.getLong(c.getColumnIndex(ContactsContract.Contacts._ID));
                    Uri photoUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, userId);
                    if (!Helper.isJavaScriptNullOrEmpty(displayName)) {
                        c.close();
                        lookup = new CachedPhoneLookup();
                        lookup.displayName = displayName;
                        lookup.enteredNumber = ServiceHelper.numbersOnly(enteredNumber, true);
                        if (photoUri != null)
                            lookup.photoUri = photoUri.toString();
                        mLookup.put(number, lookup);
                        return lookup;
                    }
                }
            }
            finally {
                c.close();
            }
        }
        catch (Exception ex) {
        }
        return null;
    }

    private void markRead(Conversation conversation) {
        conversation.unread = false;
        ContentValues vals = new ContentValues();
        vals.put("unread", 0);
        mDatabase.beginTransaction();
        try {
            for (Message message: conversation.messages.values()) {
                if (!message.unread)
                    continue;
                message.unread = false;
                mDatabase.update("sms", vals, "key = ?", new String[] { message.key });
            }
            mDatabase.setTransactionSuccessful();
        }
        finally {
            mDatabase.endTransaction();
        }
    }

    ViewSwitcher switcher;
    ListView messages;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDatabase = Database.open(this);
        mSettings = Settings.getInstance(MainActivity.this);
        String account = mSettings.getString("account", null);

        final String myPhotoUri;
        if (account != null) {
            Cursor me = getContentResolver().query(ContactsContract.CommonDataKinds.Email.CONTENT_URI, new String[] { Email.CONTACT_ID, Email.DATA1 }, Email.DATA1 + "= ?", new String[] { account }, null);
            if (me.moveToNext()) {
                long userId = me.getLong(me.getColumnIndex(Email.CONTACT_ID));
                Uri uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, userId);
                if (uri != null)
                    myPhotoUri = uri.toString();
                else
                    myPhotoUri = null;
            }
            else {
                myPhotoUri = null;
            }
            
            me.close();
        }
        else {
            myPhotoUri = null;
        }
        
        mConversations = new ArrayAdapter<Conversation>(this, R.id.name) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = (convertView == null) ? getLayoutInflater().inflate(R.layout.contact, null) : convertView;
                
                Conversation conversation = getItem(position);
                ImageView iv = (ImageView)v.findViewById(R.id.image);
                TextView name = (TextView)v.findViewById(R.id.name);
                TextView text = (TextView)v.findViewById(R.id.last_message);
                text.setText(conversation.lastMessageText);
                CachedPhoneLookup lookup = getPhoneLookup(conversation.number);
                if (lookup != null) {
                    name.setText(lookup.displayName);
                    UrlImageViewHelper.setUrlDrawable(iv, lookup.photoUri);
                }
                else {
                    iv.setImageResource(R.drawable.desksms);
                    name.setText(conversation.number);
                }
                if (conversation.number.equals("DeskSMS"))
                    iv.setImageResource(R.drawable.contrast);
                
                View unread = v.findViewById(R.id.unread);
                unread.setVisibility(conversation.unread ? View.VISIBLE : View.INVISIBLE);
                
                return v;
            }
        };
        
        mConversation = new ArrayAdapter<MainActivity.Message>(this, R.id.incoming_message) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = (convertView == null) ? getLayoutInflater().inflate(R.layout.message, null) : convertView;

                Message message = getItem(position);
                CachedPhoneLookup lookup = getPhoneLookup(message.number);

                
                ImageView iv = (ImageView)v.findViewById(R.id.image);
                LinearLayout filler = (LinearLayout)v.findViewById(R.id.filler);

                TextView otext = (TextView)v.findViewById(R.id.outgoing_message);
                TextView itext = (TextView)v.findViewById(R.id.incoming_message);
                ImageView pending = (ImageView)v.findViewById(R.id.pending);
                if ("incoming".equals(message.type)) {
                    filler.setVisibility(View.GONE);
                    itext.setText(message.message);
                    otext.setVisibility(View.GONE);
                    itext.setVisibility(View.VISIBLE);
                    if (lookup != null) {
                        UrlImageViewHelper.setUrlDrawable(iv, lookup.photoUri);
                    }
                    else {
                        iv.setImageResource(R.drawable.desksms);
                    }
                }
                else {
                    filler.setVisibility(View.VISIBLE);
                    otext.setText(message.message);
                    itext.setVisibility(View.GONE);
                    otext.setVisibility(View.VISIBLE);
                    UrlImageViewHelper.setUrlDrawable(iv, myPhotoUri, R.drawable.contact);
                }
                
                if ("pending".equals(message.type) && message.date < System.currentTimeMillis() - 5L * 60L * 1000L) {
                    message.type = "failed";
                }
                pending.setVisibility(View.GONE);
                if ("pending".equals(message.type)) {
                    pending.setVisibility(View.VISIBLE);
                    pending.setImageResource(R.drawable.ic_sms_mms_pending);
                }
                else if ("failed".equals(message.type)) {
                    pending.setVisibility(View.VISIBLE);
                    pending.setImageResource(R.drawable.ic_list_alert_sms_failed);
                }
                else {
                    pending.setVisibility(View.GONE);
                }
                
                return v;
            }
        };
        
        switcher = (ViewSwitcher)findViewById(R.id.switcher);
        final GestureDetector detector = new GestureDetector(this, new SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (Math.abs(velocityX) < Math.abs(velocityY))
                    return false;

                if (switcher.getCurrentView() == switcher.getChildAt(0)) {
                    if (velocityX > 0)
                        return false;
                    forward();
                    return true;
                }
                else {
                    if (velocityX < 0)
                        return false;
                    back();
                    return true;
                }
            }
        });
        
        sendText = (EditText)findViewById(R.id.send_text);
        Button send = (Button)findViewById(R.id.send);
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSms();
            }
        });

        final TextView name = (TextView)findViewById(R.id.name);

        ListView listView = (ListView)findViewById(R.id.list);
        listView.setAdapter(mConversations);
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                mConversation.notifyDataSetChanged();
                mConversation.clear();
                Conversation conversation = mConversations.getItem(position);
                mCurrentConversation = conversation;
                markRead(conversation);
                LinkedHashMap<String, Message> messages = conversation.messages;
                for (Message message: messages.values()) {
                    mConversation.add(message);
                }
                CachedPhoneLookup lookup = getPhoneLookup(conversation.number);
                if (lookup != null)
                    name.setText(lookup.displayName);
                else
                    name.setText(conversation.number);
                forward();
                mConversations.notifyDataSetChanged();
            }
        });
        
        OnTouchListener listener = new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return detector.onTouchEvent(event);
            }
        };
        listView.setOnTouchListener(listener);
        
        messages = (ListView)findViewById(R.id.messages);
        messages.setOnTouchListener(listener);
        messages.setAdapter(mConversation);
        messages.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                final Message message = mConversation.getItem(position);
                AlertDialog.Builder builder = new Builder(MainActivity.this);
                builder.setItems(R.array.message_options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            ClipboardManager cb = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                            cb.setText(message.message);
                        }
                        else {
                            mCurrentConversation.messages.remove(message.key);
                            mConversation.remove(message);
                            mDatabase.delete("sms", "key = ?", new String[] { message.key });
                        }
                    }
                });
                builder.create().show();
                return true;
            }
        });


        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                load();
                System.out.println("synced");
            }
        };
        IntentFilter filter = new IntentFilter("com.koushikdutta.tabletsms.SYNC_COMPLETE");
        registerReceiver(mReceiver, filter);
        
        if (Helper.isJavaScriptNullOrEmpty(account)) {
            doLogin();
            return;
        }
        
        load();
    }
    
    private void back() {
        switcher.setInAnimation(AnimationUtils.loadAnimation(MainActivity.this, R.anim.flipper_out));
        switcher.setOutAnimation(AnimationUtils.loadAnimation(MainActivity.this, R.anim.flipper_out_fast));
        switcher.showPrevious();
    }
    
    private void forward() {
        switcher.setInAnimation(AnimationUtils.loadAnimation(MainActivity.this, R.anim.flipper_in));
        switcher.setOutAnimation(AnimationUtils.loadAnimation(MainActivity.this, R.anim.flipper_in_fast));
        switcher.showNext();
    }
    
    public void onBackPressed() {
        if (switcher.getCurrentView() == switcher.getChildAt(1)) {
            back();
            return;
        }
        
        super.onBackPressed();
    };
    
    BroadcastReceiver mReceiver;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem mi = menu.add(getString(R.string.logout));
        mi.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                mSettings.setString("account", null);
                doLogin();
                return true;
            }
        });
        return true;
    }
    
    private void doLogin() {
        TickleServiceHelper.login(MainActivity.this, new com.koushikdutta.tabletsms.Callback<Boolean>() {
            @Override
            public void onCallback(Boolean result) {
                Helper.startSync(MainActivity.this);
                System.out.println(result);
            }
        });
    }
    
    @Override
    protected void onDestroy() {
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }
    
    private void scrollToEnd() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                messages.smoothScrollToPosition(mConversation.getCount());
                messages.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
            }
        }, 100);
    }
    
    EditText sendText;
    private void sendSms() {
        String text = sendText.getText().toString();
        if (text == null || text.length() == 0)
            return;
        sendText.setText("");
        
        final Message message = new Message();
        message.date = System.currentTimeMillis();
        message.message = text;
        message.type = "pending";
        message.number = mCurrentConversation.number;
        message.key = message.number + "/" + message.date;
        message.unread = false;
        
        mCurrentConversation.messages.put(message.key, message);
        messages.setTranscriptMode(ListView.TRANSCRIPT_MODE_DISABLED);
        mConversation.add(message);
        scrollToEnd();

        ContentValues insert = new ContentValues();
        insert.put("date", message.date);
        insert.put("message", message.message);
        insert.put("key", message.key);
        insert.put("number", message.number);
        insert.put("type", message.type);
        insert.put("unread", 0);

        mDatabase.insert("sms", null, insert);

        ThreadingRunnable.background(new ThreadingRunnable() {
            @Override
            public void run() {
                final String account = mSettings.getString("account");
                try {
                    JSONObject envelope = new JSONObject();
                    JSONArray data = new JSONArray();
                    envelope.put("data", data);
                    JSONObject out = new JSONObject();
                    data.put(out);
                    out.put("date", message.date);
                    out.put("message", message.message);
                    out.put("number", message.number);
                    Log.i(LOGTAG, ServiceHelper.retryExecuteAsString(MainActivity.this, account, new URL(ServiceHelper.OUTBOX_URL), new ServiceHelper.JSONPoster(envelope)));
                }
                catch (Exception e) {
                    e.printStackTrace();
                    foreground(new Runnable() {
                        @Override
                        public void run() {
                            message.type = "failed";
                            mConversation.notifyDataSetChanged();
                        }
                    });
                }
            }
        });
    }
}
