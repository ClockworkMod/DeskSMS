package com.koushikdutta.desktopsms;

import java.util.Hashtable;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsService extends MessageServiceBase {
    private static final String LOGTAG = SmsReceiver.class.getSimpleName();

    private static class PendingSms {
        public long firstReceived = System.currentTimeMillis();
        public int length;
        public int count = 0;
        public String message;
    }

    void sendPendingMessages() {
        Settings settings = Settings.getInstance(SmsService.this);
        int forwarded = settings.getInt("forwarded", 0);
        // steal the hashtable to prevent any concurrency issues
        for (String number: mPendingMessages.keySet()) {
            PendingSms sms = mPendingMessages.get(number);
            // if we've been waiting a while for the rest, give up
            if (sms.firstReceived + 45000 > System.currentTimeMillis() && sms.count < sms.length)
                continue;
            mPendingMessages.remove(number);
            sendMessage(number, sms.message, R.string.sms_received, "sms");
            forwarded++;
        }
        settings.setInt("forwarded", forwarded);
    }

    Hashtable<String, PendingSms> mPendingMessages = new Hashtable<String, PendingSms>();
    Handler mHandler = new Handler();
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        try {
            Log.i(LOGTAG, "SMS Received");
            final Bundle bundle = intent.getExtras();
            final Object[] pdus = (Object[]) bundle.get("pdus");
            if (bundle != null && pdus.length > 0) {
                // aggregate the multipart messages
                for (Object pdu : pdus) {
                    try {
                        Log.i(LOGTAG, "Forwarding SMS");
                        SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
                        String number = sms.getOriginatingAddress();
                        String message = sms.getMessageBody().toString();
                        Log.i(LOGTAG, "" + number);
                        Log.i(LOGTAG, "" + message);
                        
                        if (message.charAt(0) == '(' && message.charAt(2) == '/' && message.charAt(4) == ')') {
                            int messageCount = message.charAt(3) - '0';
                            message = message.substring(5);
                            PendingSms pending = mPendingMessages.get(number);
                            if (pending != null) {
                                pending.message += message;
                                pending.count++;
                            }
                            else {
                                pending = new PendingSms();
                                pending.message = message;
                                pending.length = messageCount;
                                pending.count = 1;
                                mPendingMessages.put(number, pending);
                            }
                        }
                        else {
                            sendMessage(number, message, R.string.sms_received, "sms");
                        }
                    }
                    catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                
                // send what we can immediately
                sendPendingMessages();
                
                // and schedule a pedantic resend as well for dangling partial messages
                if (mPendingMessages.size() > 0) {
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            sendPendingMessages();
                        }
                    }, 15000);
                }
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        return super.onStartCommand(intent, flags, startId);
    }
}
