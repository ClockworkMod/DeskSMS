package com.koushikdutta.tabletsms;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class Settings
{
    SQLiteDatabase mDatabase;
    Context mContext;
    private Settings(Context context)
    {
        mContext = context.getApplicationContext();
        openDatabase();
    }

    private void openDatabase() {
        SQLiteOpenHelper helper = new SQLiteOpenHelper(mContext, "settings.db", null, 1)
        {
            private final static String mDDL = "CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT);";
            @Override
            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                onCreate(db);
            }

            @Override
            public void onCreate(SQLiteDatabase db) {
                db.execSQL(mDDL);
            }
        };
        mDatabase = helper.getWritableDatabase();
    }

    private static Settings mInstance; 
    public static Settings getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new Settings(context);
        }
        return mInstance;
    }
    
    public void setString(String name, String value)
    {
        ContentValues cv = new ContentValues();
        cv.put("key", name);
        cv.put("value", value);
        mDatabase.delete("settings", "key='" + name + "'", null);
        mDatabase.replace("settings", null, cv);
    }
    
    public String getString(String name)
    {
        return getString(name, null);
    }

    public String getString(String name, String defaultValue)
    {
        Cursor cursor = mDatabase.query("settings", new String[] { "value" }, "key='" + name + "'", null, null, null, null);
        try
        {
            if (cursor.moveToNext())
                return cursor.getString(0);
        }
        finally
        {
            cursor.close();
        }
        return defaultValue;
    }

    public void setInt(String name, int value)
    {
        setString(name, ((Integer)value).toString());
    }
    
    public int getInt(String name, int defaultValue)
    {
        try
        {
            return Integer.parseInt(getString(name, null));
        }
        catch(Exception ex)
        {
            return defaultValue;
        }
    }    
    public void setLong(String name, long value)
    {
        setString(name, ((Long)value).toString());
    }
    
    public long getLong(String name, long defaultValue)
    {
        try
        {
            return Long.parseLong(getString(name, null));
        }
        catch(Exception ex)
        {
            return defaultValue;
        }
    }    
    
    public void setBoolean(String name, boolean value)
    {
        setString(name, ((Boolean)value).toString());
    }
    
    public boolean getBoolean(String name, boolean defaultValue)
    {
        try
        {
            return Boolean.parseBoolean(getString(name, ((Boolean)defaultValue).toString()));
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
            return defaultValue;
        }
    }
}
