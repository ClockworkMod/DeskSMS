package com.koushikdutta.tabletsms;

import java.util.Hashtable;
import java.util.Map.Entry;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;

public class MainActivity extends Activity {
    private static class Message {
        String key;
        String number;
        long date;
        String type;
        String image;
        String message;
    }
    
    Hashtable<String, Message> mMessages = new Hashtable<String, MainActivity.Message>();
    long mLastLoaded = 0;

    Settings mSettings;
    SQLiteDatabase mDatabase;
    
    private void load() {
        final Handler handler = new Handler();
        new Thread() {
            public void run() {
                try {
                    Cursor c = mDatabase.query("sms", null, "date > ?", new String[] { ((Long)mLastLoaded).toString() }, null, null, null);
                    final Hashtable<String, Message> newMessages = new Hashtable<String, MainActivity.Message>();
                    int keyIndex = c.getColumnIndex("key");
                    int numberIndex = c.getColumnIndex("number");
                    int dateIndex = c.getColumnIndex("date");
                    int typeIndex = c.getColumnIndex("type");
                    int imageIndex = c.getColumnIndex("image");
                    int messageIndex = c.getColumnIndex("message");
                    while (c.moveToNext()) {
                        Message message = new Message();
                        message.key = c.getString(keyIndex);
                        message.number = c.getString(numberIndex);
                        message.date = c.getLong(dateIndex);
                        message.type = c.getString(typeIndex);
                        message.image = c.getString(imageIndex);
                        message.message = c.getString(messageIndex);
                        newMessages.put(message.key, message);
                    }
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            for (Entry<String, Message> entry: newMessages.entrySet()) {
                                mMessages.put(entry.getKey(), entry.getValue());
                            }
                        }
                    });
                }
                catch (Exception ex) {
                }
            };
        }.start();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        mDatabase = Database.open(this);
        mSettings = Settings.getInstance(MainActivity.this);
        String account = mSettings.getString("account", null);

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
}
