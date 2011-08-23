package com.koushikdutta.desktopsms;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.http.AndroidHttpClient;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

public class TickleServiceHelper {
    private static final String LOGTAG = TickleServiceHelper.class.getSimpleName();

    private TickleServiceHelper() {
    }
    
    static String getCookie(final Context context) throws ClientProtocolException, IOException, URISyntaxException {
        Settings settings = Settings.getInstance(context);
        final String authToken = settings.getString("web_connect_auth_token");
        if (authToken == null)
            return null;
        Log.i(LOGTAG, authToken);
        Log.i(LOGTAG, "getting cookie");
        // Get ACSID cookie
        DefaultHttpClient client = new DefaultHttpClient();
        String continueURL = ServiceHelper.BASE_URL;
        URI uri = new URI(ServiceHelper.AUTH_URL + "?continue=" + URLEncoder.encode(continueURL, "UTF-8") + "&auth=" + authToken);
        HttpGet method = new HttpGet(uri);
        final HttpParams getParams = new BasicHttpParams();
        HttpClientParams.setRedirecting(getParams, false); // continue is not
                                                           // used
        method.setParams(getParams);

        HttpResponse res = client.execute(method);
        Header[] headers = res.getHeaders("Set-Cookie");
        if (res.getStatusLine().getStatusCode() != 302 || headers.length == 0) {
            //throw new Exception("failure getting cookie: " + res.getStatusLine().getStatusCode() + " " + res.getStatusLine().getReasonPhrase());
            return null;
        }

        String ascidCookie = null;
        for (Header header : headers) {
            if (header.getValue().indexOf("ACSID=") >= 0) {
                // let's parse it
                String value = header.getValue();
                String[] pairs = value.split(";");
                ascidCookie = pairs[0];
            }
        }
        settings.setString("Cookie", ascidCookie);
        return ascidCookie;
    }

    static void registerWebConnect(final Context context) throws Exception {
        String ascidCookie = getCookie(context);
        Settings settings = Settings.getInstance(context);
        settings.setBoolean("registered", false);
        final String registration = settings.getString("registration_id");
        DefaultHttpClient client = new DefaultHttpClient();

        // Make POST request
        URI uri = new URI(ServiceHelper.REGISTER_URL);
        HttpPost post = new HttpPost(uri);
        ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("device_id", Helper.getSafeDeviceId(context)));
        params.add(new BasicNameValuePair("registration_id", registration));
        params.add(new BasicNameValuePair("version_code", String.valueOf(DesktopSMSApplication.mVersionCode)));
        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params, "UTF-8");
        post.setEntity(entity);
        post.setHeader("X-Same-Domain", "1"); // XSRF
        post.setHeader("Cookie", ascidCookie);
        try {
            HttpResponse res = client.execute(post);
            Log.i(LOGTAG, "Status code from register: " + res.getStatusLine().getStatusCode());
            if (res.getStatusLine().getStatusCode() != 200)
                throw new Exception("status from server: " + res.getStatusLine().getStatusCode());
            settings.setBoolean("registered", true);
        }
        catch (Exception ex) {
            settings.setBoolean("registered", false);
            settings.setString("account", null);
            settings.setString("registration_id", null);
            throw ex;
        }
    }

    public static String[] getGoogleAccounts(Context context) {
        ArrayList<String> googleAccounts = new ArrayList<String>();
        Account[] accounts = AccountManager.get(context).getAccounts();
        for (Account account : accounts) {
            if (account.type.equals("com.google")) {
                googleAccounts.add(account.name);
            }
        }

        String[] result = new String[googleAccounts.size()];
        googleAccounts.toArray(result);
        return result;
    }

    public static void login(final Activity context, final ActivityResultDelegate delegate, final Callback<Boolean> callback) {
        final String[] accounts = getGoogleAccounts(context);
        AlertDialog.Builder builder = new Builder(context);
        builder.setCancelable(false);
        builder.setTitle(R.string.accounts);
        builder.setItems(accounts, new OnClickListener() {
            public void onClick(DialogInterface dialog, final int which) {
                final String accountName = accounts[which];
                Log.i(LOGTAG, accountName);

                final ProgressDialog dlg = new ProgressDialog(context);
                dlg.setMessage(context.getString(R.string.setting_up_push));
                dlg.show();

                registerForPush(context, new Callback<Void>() {
                    @Override
                    public void onCallback(Void result) {
                        dlg.setMessage(context.getString(R.string.retrieving_authentication));
                        tryAuth(context, accountName, new Callback<Bundle>() {
                            public void onCallback(Bundle bundle) {
                                try {
                                    if (bundle == null)
                                        throw new Exception();
                                    String authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
                                    if (authToken == null)
                                        throw new Exception();

                                    dlg.setMessage(context.getString(R.string.registering_with_server));
                                    new Thread() {
                                        boolean pushReceived = false;
                                        public void run() {
                                            try {
                                                registerWebConnect(context);

                                                context.runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        dlg.setMessage(context.getString(R.string.testing_push));
                                                    }
                                                });

                                                final BroadcastReceiver pushReceiver = new BroadcastReceiver() {
                                                    @Override
                                                    public void onReceive(Context context, Intent intent) {
                                                        try {
                                                            context.unregisterReceiver(this);
                                                        }
                                                        catch (Exception ex) {
                                                            ex.printStackTrace();
                                                        }
                                                        pushReceived = true;
                                                        dlg.dismiss();
                                                        Helper.showAlertDialog(context, R.string.signin_success, new DialogInterface.OnClickListener() {
                                                            @Override
                                                            public void onClick(DialogInterface dialog, int which) {
                                                                callback.onCallback(true);
                                                            }
                                                        });
                                                    }
                                                };
                                                IntentFilter filter = new IntentFilter(C2DMReceiver.PING);
                                                context.registerReceiver(pushReceiver, filter);

                                                {
                                                    AndroidHttpClient client = Helper.getHttpClient(context);
                                                    try {
                                                        HttpGet get = new HttpGet(ServiceHelper.PING_URL);
                                                        HttpResponse res = ServiceHelper.retryExecute(context, accountName, client, get);
                                                        if (res != null) {
                                                            Log.i(LOGTAG, "Response: " + res.getStatusLine().getStatusCode());
                                                        }
                                                    }
                                                    finally {
                                                        client.close();
                                                    }
                                                }

                                                Thread.sleep(10000);
                                                if (!pushReceived) {
                                                    context.runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            dlg.dismiss();
                                                            Helper.showAlertDialog(context, R.string.push_failed, new DialogInterface.OnClickListener() {
                                                                @Override
                                                                public void onClick(DialogInterface dialog, int which) {
                                                                    callback.onCallback(true);
                                                                }
                                                            });
                                                        }
                                                    });
                                                }
                                            }
                                            catch (Exception ex) {
                                                ex.printStackTrace();
                                                context.runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        dlg.dismiss();
                                                        Helper.showAlertDialog(context, R.string.signin_failure, new DialogInterface.OnClickListener() {
                                                            @Override
                                                            public void onClick(DialogInterface dialog, int which) {
                                                                callback.onCallback(false);
                                                            }
                                                        });
                                                    }
                                                });
                                            }
                                        };
                                    }.start();
                                }
                                catch (Exception e) {
                                    e.printStackTrace();
                                    dlg.dismiss();
                                    Helper.showAlertDialog(context, R.string.signin_failure);
                                    callback.onCallback(false);
                                }
                            }
                        }, delegate);
                    }
                });
            }
        });
        builder.setCancelable(true);
        builder.create().show();
    }

    static void registerForPush(final Context context, final Callback<Void> callback) {
//        Intent unregIntent = new Intent("com.google.android.c2dm.intent.UNREGISTER");
//        unregIntent.putExtra("app", PendingIntent.getBroadcast(context, 0, new Intent(), 0));
//        context.startService(unregIntent);
//
        Intent registrationIntent = new Intent("com.google.android.c2dm.intent.REGISTER");
        registrationIntent.putExtra("app", PendingIntent.getBroadcast(context, 0, new Intent(), 0));
        registrationIntent.putExtra("sender", "koushd@gmail.com");
        
        if (callback != null) {
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    context.unregisterReceiver(this);
                    callback.onCallback(null);
                }
            };
        
            IntentFilter filter = new IntentFilter(C2DMReceiver.ACTION_REGISTRATION_RECEIVED);
            context.registerReceiver(receiver, filter);
        }
        
        context.startService(registrationIntent);
    }
    
    static final String AUTH_TOKEN_TYPE = "ah";
    private static void tryAuth(final Activity context, final String accountName, final Callback<Bundle> callback, final ActivityResultDelegate delegate) {
        AccountManager accountManager = AccountManager.get(context);
        Account account = new Account(accountName, "com.google");
        String curAuthToken = Settings.getInstance(context).getString("web_connect_auth_token");
        if (!Helper.isJavaScriptNullOrEmpty(curAuthToken))
            accountManager.invalidateAuthToken(account.type, curAuthToken);
        accountManager.getAuthToken(account, AUTH_TOKEN_TYPE, false, new AccountManagerCallback<Bundle>() {
            public void run(AccountManagerFuture<Bundle> future) {
                try {
                    Bundle bundle = future.getResult();
                    final String authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
                    if (authToken == null) {
                        if (delegate == null)
                            throw new Exception();
                        Intent authIntent = (Intent) bundle.get(AccountManager.KEY_INTENT);
                        delegate.setOnActivityResultCallback(new Callback<Tuple<Integer, Tuple<Integer, Intent>>>() {
                            public void onCallback(Tuple<Integer, Tuple<Integer, Intent>> result) {
                                if (result.First != 242424)
                                    return;
                                if (result.Second.First == Activity.RESULT_OK)
                                    tryAuth(context, accountName, callback, null);
                                else
                                    callback.onCallback(null);
                            }
                        });
                        // for some reason it sets the intent flag with FLAG_ACTIVITY_NEW_TASK
                        // This ends up sending an immediatae Activity.RESULT_CANCELLED event to
                        // the calling activity.
                        authIntent.setFlags(0);
                        context.startActivityForResult(authIntent, 242424);
                    }
                    else {
                        Settings settings = Settings.getInstance(context);
                        settings.setString("web_connect_auth_token", authToken);
                        settings.setString("account", accountName.toLowerCase());
                        try {
                            callback.onCallback(future.getResult());
                        }
                        catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
                catch (Exception ex) {
                    callback.onCallback(null);
                    ex.printStackTrace();
                }
            }
        }, null);
    }

}
