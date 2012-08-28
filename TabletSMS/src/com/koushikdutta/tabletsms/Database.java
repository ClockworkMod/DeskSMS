package com.koushikdutta.tabletsms;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;


public class Database {
    static public SQLiteDatabase open(Context context) {
        SQLiteOpenHelper helper = new SQLiteOpenHelper(context, "messages.sqlite", null, 1) {
            
            @Override
            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            }
            
            @Override
            public void onCreate(SQLiteDatabase db) {
                db.execSQL("CREATE TABLE sms (key TEXT PRIMARY KEY NOT NULL, number TEXT NOT NULL, date INTEGER NOT NULL, message TEXT, type TEXT NOT NULL, image TEXT)");
            }
        };
        
        return helper.getWritableDatabase();
    }

}
