package com.koushikdutta.tabletsms;

import android.os.Handler;

public abstract class ThreadingRunnable {
    public static void background(final ThreadingRunnable runnable) {
        new Thread() {
            public void run() {
                runnable.run();
            };
        }.start();
    }

    public abstract void run();
    private Handler mHandler = new Handler();
    public Handler getHandler() {
        return mHandler;
    }
    
    public void foreground(Runnable runnable) {
        mHandler.post(runnable);
    }
    
    public void background(Runnable runnable) {
        new Thread(runnable).start();
    }
}
