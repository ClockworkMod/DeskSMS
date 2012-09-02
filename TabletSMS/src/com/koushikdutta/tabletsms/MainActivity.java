package com.koushikdutta.tabletsms;

import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.text.ClipboardManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.MenuItem.OnMenuItemClickListener;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

public class MainActivity extends SherlockFragmentActivity {
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
        Spannable spannable;
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
    long mLastLoaded = System.currentTimeMillis() - 14L * 24L * 60L * 60L * 1000L;

    Settings mSettings;
    SQLiteDatabase mDatabase;
    
    ArrayAdapter<Conversation> mConversations;
    ArrayAdapter<Message> mConversation;
    
    private Conversation findConversation(String number) {
        for (int i = 0; i < mConversations.getCount(); i++) {
            Conversation conversation = mConversations.getItem(i);
            if (!NumberHelper.areSimilar(conversation.number, number))
                continue;
            return conversation;
        }
        return null;
    }
    
    private Conversation findOrStartConversation(String number) {
        Conversation found = findConversation(number);
        if (found == null) {
            found = new Conversation(number);
        }
        else {
            mConversations.remove(found);
        }
        mConversations.insert(found, 0);
        return found;
    }
    
    private void merge(LinkedHashMap<String, Message> newMessages) {
        boolean addedToCurrent = false;
        for (Entry<String, Message> entry: newMessages.entrySet()) {
            String key = entry.getKey();
            Message message = entry.getValue();
            Conversation found = findOrStartConversation(message.number);
            Message existing = found.messages.put(key, message);
            found.last = Math.max(found.last, message.date);
            found.lastMessageText = message.message;
            found.unread |= entry.getValue().unread;
            
            if (found == mCurrentConversation) {
                addedToCurrent = true;
                if (existing != null) {
                    mConversation.remove(existing);
                }
                mConversation.add(message);
            }
        }
        if (addedToCurrent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                scrollToEnd();
            }
        }
        mConversations.notifyDataSetChanged();
        // if the conversation is currently being viewed at the time the messages come in
        // mark them as read
        if (isConversationShowing())
            markRead(mCurrentConversation);
        
        if (mCurrentConversation == null && mConversations.getCount() > 0 && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setCurrentConversation(mConversations.getItem(0));
        }
        
        if (mConversations.getCount() == 0)
            setEmptyText(R.string.no_messages);
        else
            clearEmptyText();
        
        if (newMessages.size() == 1) {
            ratingPester();
        }
    }
    
    private boolean isConversationShowing() {
        if (mSwitcher.getCurrentView() == null)
            return true;
        return mSwitcher.getCurrentView() == mSwitcher.getChildAt(1);
    }
    
    private boolean loading = false;
    private void load() {
        if (loading)
            return;
        loading = true;
        ThreadingRunnable.background(new ThreadingRunnable() {
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
                    foreground(new Runnable() {
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
        });
    }

    Hashtable<String, CachedPhoneLookup> mLookup = new Hashtable<String, CachedPhoneLookup>();

    private void markRead(Conversation conversation) {
        if (conversation == null)
            return;
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

    ViewSwitcher mSwitcher;
    ListView mMessages;
    String mAccount;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        
        clearNotifications();
    }
    
    private void clearNotifications() {
        if (!mVisible)
            return;

        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(SyncService.NOTIFICATION_ID);
        mSettings.setInt("new_message_count", 0);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDatabase = Database.open(this);
        mSettings = Settings.getInstance(MainActivity.this);
        mAccount = mSettings.getString("account", null);
        mVisible = true;

        clearNotifications();

        final String myPhotoUri;
        if (mAccount != null) {
            Cursor me = getContentResolver().query(ContactsContract.CommonDataKinds.Email.CONTENT_URI, new String[] { Email.CONTACT_ID, Email.DATA1 }, Email.DATA1 + "= ?", new String[] { mAccount }, null);
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
                CachedPhoneLookup lookup = Helper.getPhoneLookup(MainActivity.this, mLookup, conversation.number);
                if (lookup != null) {
                    name.setText(lookup.displayName);
                    UrlImageViewHelper.setUrlDrawable(iv, lookup.photoUri, R.drawable.desksms);
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
                CachedPhoneLookup lookup = Helper.getPhoneLookup(MainActivity.this, mLookup, message.number);

                ImageView iv = (ImageView)v.findViewById(R.id.image);
                ImageView ipic = (ImageView)v.findViewById(R.id.incoming_image);
                ImageView opic = (ImageView)v.findViewById(R.id.outgoing_image);
                TextView otext = (TextView)v.findViewById(R.id.outgoing_message);
                TextView itext = (TextView)v.findViewById(R.id.incoming_message);
                ImageView pending = (ImageView)v.findViewById(R.id.pending);
                DateFormat df;
                if (message.date < System.currentTimeMillis() - 24L * 60L * 60L * 1000L)
                    df = android.text.format.DateFormat.getMediumDateFormat(MainActivity.this);
                else
                    df = android.text.format.DateFormat.getTimeFormat(MainActivity.this);
                String date = df.format(new Date(message.date));
                if (message.spannable == null) {
                    SpannableStringBuilder builder = new SpannableStringBuilder(message.message);
                    builder.append("\n");
                    int start = builder.length();
                    builder.append(date);
                    int end = builder.length();
                    builder.setSpan(new TextAppearanceSpan(MainActivity.this, android.R.style.TextAppearance_DeviceDefault), start, end, 0);
                    builder.setSpan(new ForegroundColorSpan(getResources().getColor(android.R.color.tertiary_text_dark)), start, end, 0);
                    message.spannable = builder;
                }
                if ("incoming".equals(message.type)) {
//                    filler.setVisibility(View.GONE);
                    itext.setText(message.spannable);
                    otext.setVisibility(View.GONE);
                    itext.setVisibility(View.VISIBLE);
                    if (lookup != null) {
                        UrlImageViewHelper.setUrlDrawable(iv, lookup.photoUri, R.drawable.desksms);
                    }
                    else {
                        iv.setImageResource(R.drawable.desksms);
                    }
                }
                else {
//                    filler.setVisibility(View.VISIBLE);
                    otext.setText(message.spannable);
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
                
                ipic.setVisibility(View.GONE);
                opic.setVisibility(View.GONE);
                if ("true".equals(message.image)) {
                    otext.setVisibility(View.GONE);
                    itext.setVisibility(View.GONE);
                    if ("incoming".equals(message.type)) {
                        ipic.setVisibility(View.VISIBLE);
                        UrlImageViewHelper.setUrlDrawable(ipic, ServiceHelper.IMAGE_URL + "/" + URLEncoder.encode(message.key), R.drawable.placeholder);
                    }
                    else {
                        opic.setVisibility(View.VISIBLE);
                        UrlImageViewHelper.setUrlDrawable(opic, ServiceHelper.IMAGE_URL + "/" + message.key, R.drawable.placeholder);
                    }
                }

                if (message.number.equals("DeskSMS"))
                    iv.setImageResource(R.drawable.contrast);

                return v;
            }
        };
        
        mSwitcher = (ViewSwitcher)findViewById(R.id.switcher);
        final GestureDetector detector = new GestureDetector(this, new SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (Math.abs(velocityX) < Math.abs(velocityY))
                    return false;

                if (!isConversationShowing()) {
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
        ImageButton send = (ImageButton)findViewById(R.id.send);
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSms();
            }
        });
        sendText.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                sendSms();
                return true;
            }
        });

        mCurrentConversationName = (TextView)findViewById(R.id.name);

        ListView listView = (ListView)findViewById(R.id.list);
        listView.setAdapter(mConversations);
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                Conversation conversation = mConversations.getItem(position);
                setCurrentConversation(conversation);
            }
        });
        listView.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                final Conversation conversation = mConversations.getItem(position);
                AlertDialog.Builder builder = new Builder(MainActivity.this);
                builder.setItems(new CharSequence[] { getString(R.string.delete) }, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            deleteConversation(conversation);
                        }
                    }
                });
                builder.create().show();
                return true;
            }
        });
        
        OnTouchListener listener = new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return detector.onTouchEvent(event);
            }
        };
        listView.setOnTouchListener(listener);
        
        mMessages = (ListView)findViewById(R.id.messages);
        mMessages.setOnTouchListener(listener);
        mMessages.setAdapter(mConversation);
        mMessages.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                final Message message = mConversation.getItem(position);
                AlertDialog.Builder builder = new Builder(MainActivity.this);
                builder.setItems(new CharSequence[] { getText(R.string.copy_text), getString(R.string.delete) }, new DialogInterface.OnClickListener() {
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
                            ThreadingRunnable.background(new ThreadingRunnable() {
                                @Override
                                public void run() {
                                    try {
                                        String delete = ServiceHelper.SMS_URL + "?operation=DELETE&key=" + URLEncoder.encode(message.key);
                                        ServiceHelper.retryExecuteAndDisconnect(MainActivity.this, mAccount, new URL(delete), null);
                                    }
                                    catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                           });
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
                if (mAccount == null)
                    return;
                load();
                clearNotifications();
            }
        };
        IntentFilter filter = new IntentFilter("com.koushikdutta.tabletsms.SYNC_COMPLETE");
        registerReceiver(mReceiver, filter);
        
        
        View c = findViewById(android.R.id.empty);
        mEmptyView = (TextView)c.findViewById(R.id.empty);
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mMessages.setEmptyView(c);
        }
        else {
            listView.setEmptyView(c);
        }
        c.setId(android.R.id.empty);
        
        TileableBackgroundDrawable tile = new TileableBackgroundDrawable(getResources(), R.drawable.stitch);
        mCurrentConversationName.setBackgroundDrawable(tile);
        findViewById(R.id.send_container).setBackgroundDrawable(tile);
        findViewById(R.id.messages_header).setBackgroundDrawable(tile);
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            listView.setBackgroundDrawable(tile);
        }

        if (Helper.isJavaScriptNullOrEmpty(mAccount)) {
            doLogin();
        }
        else {
            load();
        }
    }
    
    private void back() {
        if (mMenuTrash != null)
            mMenuTrash.setVisible(false);
        if (!isConversationShowing())
            return;
        mSwitcher.setInAnimation(AnimationUtils.loadAnimation(MainActivity.this, R.anim.flipper_out));
        mSwitcher.setOutAnimation(AnimationUtils.loadAnimation(MainActivity.this, R.anim.flipper_out_fast));
        mSwitcher.showPrevious();
    }
    
    private void forward() {
        if (mMenuTrash != null)
            mMenuTrash.setVisible(true);
        if (isConversationShowing())
            return;
        mSwitcher.setInAnimation(AnimationUtils.loadAnimation(MainActivity.this, R.anim.flipper_in));
        mSwitcher.setOutAnimation(AnimationUtils.loadAnimation(MainActivity.this, R.anim.flipper_in_fast));
        mSwitcher.showNext();
    }
    
    public void onBackPressed() {
        if (isConversationShowing() && getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
            back();
            return;
        }
        
        super.onBackPressed();
    };
    
    static boolean mVisible = false;
    protected void onResume() {
        super.onResume();
        mVisible = true;
    };
    protected void onPause() {
        super.onPause();
        mVisible = false;
    };
    
    BroadcastReceiver mReceiver;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            back();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.activity_main, menu);

        mMenuMarkAsRead = menu.getItem(0);
        mMenuMarkAsRead.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                for (int i = 0; i < mConversations.getCount(); i++) {
                    Conversation conversation = mConversations.getItem(i);
                    markRead(conversation);
                    mConversations.notifyDataSetChanged();
                }
                Toast.makeText(MainActivity.this, R.string.all_messages_read, Toast.LENGTH_SHORT).show();
                ThreadingRunnable.background(new ThreadingRunnable() {
                    @Override
                    public void run() {
                        try {
                            ServiceHelper.retryExecuteAndDisconnect(MainActivity.this, mAccount, new URL(ServiceHelper.READ_URL), null);
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                return true;
            }
        });


        menu.getItem(3).setOnMenuItemClickListener(new OnMenuItemClickListener() {
            boolean[] options = new boolean[] { mSettings.getBoolean("notifications", true) };
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setMultiChoiceItems(new CharSequence[] { getString(R.string.notifications) }, options, new OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        options[which] = isChecked;
                    }
                });
                builder.setTitle(R.string.settings);
                builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mSettings.setBoolean("notifications", options[0]);
                    }
                });
                builder.create().show();
                return true;
            }
        });

        menu.getItem(4).setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                mAccount = null;
                mSettings.setString("account", null);
                mDatabase.delete("sms", null, null);
                mSettings.setLong("last_sync_timestamp", 0);
                mLastLoaded = System.currentTimeMillis() - 14L * 24L * 60L * 60L * 1000L;
                mConversations.clear();
                mConversation.clear();
                setCurrentConversation(null);
                doLogin();
                return true;
            }
        });

        menu.getItem(1).setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
                startActivityForResult(intent, PICK_CONTACT);
                return true;
            }
        });

        mMenuTrash = menu.getItem(2);
        mMenuTrash.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                back();
                if (mCurrentConversation != null)
                    deleteConversation(mCurrentConversation);
                setCurrentConversation(null);
                return true;
            }
        });
        
        mMenuTrash.setVisible(isConversationShowing());

        return super.onCreateOptionsMenu(menu);
    }
    
    MenuItem mMenuMarkAsRead;
    MenuItem mMenuTrash;
    
    private static final int PICK_CONTACT = 10004;

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);

        if (res == RESULT_OK && data != null) {
            if (req == PICK_CONTACT) {
                Uri uri = data.getData();

                if (uri != null) {
                    Cursor c = null;
                    try {
                        c = getContentResolver().query(uri,
                                new String[] { ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.TYPE }, null, null, null);

                        if (c != null && c.moveToFirst()) {
                            String number = c.getString(0);
                            setCurrentConversation(findOrStartConversation(number));
                        }
                    }
                    finally {
                        if (c != null) {
                            c.close();
                        }
                    }
                }
            }
        }
    }
    
    
    private void doLogin() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.welcome);
        builder.setIcon(R.drawable.icon);
        builder.setMessage(R.string.welcome_info);
        builder.setCancelable(false);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                TickleServiceHelper.login(MainActivity.this, new com.koushikdutta.tabletsms.Callback<Boolean>() {
                    @Override
                    public void onCallback(Boolean result) {
                        if (!result) {
                            doLogin();
                            return;
                        }
                        setEmptyText(R.string.messages_loading);
                        mAccount = mSettings.getString("account", null);
                        Helper.startSync(MainActivity.this);
                        System.out.println(result);
                    }
                });
            }
        });
        builder.create().show();
    }
    
    @Override
    protected void onDestroy() {
        mVisible = false;
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }
    
    private void scrollToEnd() {
        mMessages.setTranscriptMode(ListView.TRANSCRIPT_MODE_DISABLED);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                mMessages.smoothScrollToPosition(mConversation.getCount() - 1);
//                mMessages.setSelected(mConversation.getCount() - 1);
//                mMessages.smoothScrollToPosition(mConversation.getCount() + 1);
                mMessages.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
            }
        }, 100);
    }

    private void ratingPester() {
        boolean pestered = mSettings.getBoolean("ratings_pestered", false);
        if (pestered)
            return;
        int o = mSettings.getInt("usage_single_outgoing", 0);
        int i = mSettings.getInt("usage_single_incoming", 0);
        if (o < 3 || i < 3)
            return;
        pestered = true;
        mSettings.setBoolean("ratings_pestered", true);
        Message message = new Message();
        message.number = "DeskSMS";
        message.date = System.currentTimeMillis();
        message.key = message.number + "/" + message.date;
        message.type = "incoming";
        message.unread = true;
        message.message = getString(R.string.rate_me);
        LinkedHashMap<String, Message> newMessages = new LinkedHashMap<String, MainActivity.Message>();
        newMessages.put(message.key, message);
        merge(newMessages);
    }
    
    EditText sendText;
    private void sendSms() {
        String text = sendText.getText().toString();
        if (text == null || text.length() == 0)
            return;
        
        int prevCounter = mSettings.getInt("usage_single_outgoing", 0);
        mSettings.setInt("usage_single_outgoing", prevCounter + 1);

        ratingPester();
        
        sendText.setText("");
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(this.getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        
        final Message message = new Message();
        message.date = System.currentTimeMillis();
        message.message = text;
        message.type = "pending";
        message.number = mCurrentConversation.number;
        message.key = message.number + "/" + message.date;
        message.unread = false;
        
        mCurrentConversation.messages.put(message.key, message);

        mConversation.add(message);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            scrollToEnd();
        }


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
    
    private void setCurrentConversation(Conversation conversation) {
        mConversation.clear();
        mCurrentConversation = conversation;
        if (conversation == null) {
            mCurrentConversationName.setText(null);
            return;
        }
        markRead(conversation);
        LinkedHashMap<String, Message> messages = conversation.messages;
        for (Message message: messages.values()) {
            mConversation.add(message);
        }
        CachedPhoneLookup lookup = Helper.getPhoneLookup(MainActivity.this, mLookup, conversation.number);
        if (lookup != null)
            mCurrentConversationName.setText(lookup.displayName);
        else
            mCurrentConversationName.setText(conversation.number);
        forward();
        mConversations.notifyDataSetChanged();
    }
    
    TextView mCurrentConversationName;

    private void deleteConversation(Conversation conversation) {
        final HashMap<String, ArrayList<Long>> numbers = new HashMap<String, ArrayList<Long>>();
        mConversations.remove(conversation);
        try {
            mDatabase.beginTransaction();
            for (Message message: conversation.messages.values()) {
                ArrayList<Long> dates = numbers.get(message.number);
                if (dates == null) {
                    dates = new ArrayList<Long>();
                    numbers.put(message.number, dates);
                }
                dates.add(message.date);
                mDatabase.delete("sms", "key = ?", new String[] { message.key });
            }
            mDatabase.setTransactionSuccessful();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        finally {
            mDatabase.endTransaction();
        }
        ThreadingRunnable.background(new ThreadingRunnable() {
            String delete;
            
            void doDelete(String u) {
                try {
                    delete += "0]"; // append a dummy zero to fix the trailing comma
                    ServiceHelper.retryExecuteAndDisconnect(MainActivity.this, mAccount, new URL(delete), null);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                finally {
                    delete = u;
                }
            }
            @Override
            public void run() {
                for (Entry<String, ArrayList<Long>> entry: numbers.entrySet()) {
                    String number = entry.getKey();
                    final String u = ServiceHelper.DELETE_CONVERSATION_URL + "?number=" + URLEncoder.encode(number) + "&dates=[";
                    delete = u;
                    ArrayList<Long> dates = entry.getValue();
                    int count = 0;
                    while (dates.size() > 0) {
                        long date = dates.remove(dates.size() - 1);
                        delete += date + ",";
                        count++;
                        if (count == 10) {
                            doDelete(u);
                            count = 0;
                        }
                    }
                    doDelete(u);
                }
            }
        });
    }
    
    TextView mEmptyView;
    private void setEmptyText(int resid) {
        mEmptyView.setText(resid);
    }
    
    private void clearEmptyText() {
        mEmptyView.setText(null);
    }
}
