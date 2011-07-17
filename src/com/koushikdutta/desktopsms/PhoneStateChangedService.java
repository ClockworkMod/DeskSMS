package com.koushikdutta.desktopsms;

import java.util.Date;

import android.content.Intent;
import android.database.Cursor;
import android.os.Handler;
import android.os.IBinder;
import android.provider.CallLog.Calls;
import android.text.format.DateFormat;

public class PhoneStateChangedService extends MessageServiceBase {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    Handler mHandler = new Handler();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Runnable runner = new Runnable() {
            @Override
            public void run() {
                try {
                    Settings settings = Settings.getInstance(PhoneStateChangedService.this);
                    long lastMissedCall = settings.getLong("last_missed_call", 0);
                    Cursor cursor = getContentResolver().query(Calls.CONTENT_URI, null, Calls.TYPE + " = ? AND " + Calls.NEW + " = ?",
                            new String[] { Integer.toString(Calls.MISSED_TYPE), "1" }, Calls.DATE + " DESC ");
 
                    int dateColumn = cursor.getColumnIndex(Calls.DATE);
                    int numberColumn = cursor.getColumnIndex(Calls.NUMBER);
                    int nameColumn = cursor.getColumnIndex(Calls.CACHED_NAME);
                    if (!cursor.moveToNext())
                        return;
                    long date = cursor.getLong(dateColumn);
                    
                    if (date <= lastMissedCall)
                        return;
                    
                    settings.setLong("last_missed_call", date);
                    if (lastMissedCall == 0)
                        return;

                    String name = cursor.getString(nameColumn);
                    String number = cursor.getString(numberColumn);
                    
                    java.text.DateFormat df = DateFormat.getTimeFormat(PhoneStateChangedService.this);
                    String dateString = df.format(new Date(date));
                    
                    if (Helper.isJavaScriptNullOrEmpty(name)) {
                        name = number;
                    }
                    String message = getString(R.string.missed_call_at, dateString);
                    
                    sendMessage(number, message, R.string.missed_call_from, "missed_call");
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };
        
        // let's wait a bit for the system to actually stick it in the provider.
        mHandler.postDelayed(runner, 10000);

        return super.onStartCommand(intent, flags, startId);
    }
}
