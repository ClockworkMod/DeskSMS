package com.koushikdutta.tabletsms;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;


public class Database {
    private static final int VERSION = 2;
    static public SQLiteDatabase open(Context context) {
        SQLiteOpenHelper helper = new SQLiteOpenHelper(context, "messages.sqlite", null, VERSION) {
            
            @Override
            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                if (oldVersion == 0) {
                    db.execSQL("CREATE TABLE sms (key TEXT PRIMARY KEY NOT NULL, number TEXT NOT NULL, date INTEGER NOT NULL, message TEXT, type TEXT NOT NULL, image TEXT, unread INTEGER)");
                    oldVersion = 1;
                }
                if (oldVersion == 1) {
                    db.execSQL("CREATE INDEX IF NOT EXISTS dateIndex on sms (date)");
                    oldVersion = 2;
                }
            }
            
            @Override
            public void onCreate(SQLiteDatabase db) {
                onUpgrade(db, 0, VERSION);
            }
        };
        
        return helper.getWritableDatabase();
    }

}
