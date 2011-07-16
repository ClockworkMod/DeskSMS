package com.koushikdutta.desktopsms;

import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
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
import android.os.Bundle;
import android.util.Log;

public class TickleServiceHelper {
    private static final String LOGTAG = TickleServiceHelper.class.getSimpleName();

    private TickleServiceHelper() {

    }

    private final static String BASE_URL = "https://desksms.appspot.com";
    private final static String REGISTER_URL = BASE_URL + "/register";
    private static final String AUTH_URL = BASE_URL + "/_ah/login";

    private static void registerWebConnect(final Context context) throws Exception {
        Settings settings = Settings.getInstance(context);
        final String registration = settings.getString("registration_id");
        final String authToken = settings.getString("web_connect_auth_token");
        if (authToken == null)
            return;
        Log.i(LOGTAG, authToken);
        Log.i(LOGTAG, "getting cookie");
        // Get ACSID cookie
        DefaultHttpClient client = new DefaultHttpClient();
        String continueURL = BASE_URL;
        URI uri = new URI(AUTH_URL + "?continue=" + URLEncoder.encode(continueURL, "UTF-8") + "&auth=" + authToken);
        HttpGet method = new HttpGet(uri);
        final HttpParams getParams = new BasicHttpParams();
        HttpClientParams.setRedirecting(getParams, false); // continue is not
                                                           // used
        method.setParams(getParams);

        HttpResponse res = client.execute(method);
        Header[] headers = res.getHeaders("Set-Cookie");
        if (res.getStatusLine().getStatusCode() != 302 || headers.length == 0) {
            throw new Exception("failure getting cookie: " + res.getStatusLine().getStatusCode() + " " + res.getStatusLine().getReasonPhrase());
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

        // Make POST request
        uri = new URI(REGISTER_URL);
        HttpPost post = new HttpPost(uri);
        ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("applicationId", "DesktopSms"));
        params.add(new BasicNameValuePair("clientId", Helper.getSafeDeviceId(context)));
        params.add(new BasicNameValuePair("registration_id", registration));
        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params, "UTF-8");
        post.setEntity(entity);
        post.setHeader("Cookie", ascidCookie);
        settings.setString("Cookie", ascidCookie);
        post.setHeader("X-Same-Domain", "1"); // XSRF
        res = client.execute(post);
        Log.i(LOGTAG, "Status code from register: " + res.getStatusLine().getStatusCode());
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
                                        public void run() {
                                            try {
                                                registerWebConnect(context);
                                                context.runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        dlg.dismiss();
                                                        Helper.showAlertDialog(context, R.string.signin_success);
                                                        callback.onCallback(true);
                                                    }
                                                });
                                            }
                                            catch (Exception ex) {
                                                ex.printStackTrace();
                                                context.runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        dlg.dismiss();
                                                        Helper.showAlertDialog(context, R.string.signin_failure);
                                                        callback.onCallback(false);
                                                    }
                                                });
                                            }
                                        };
                                    }.start();
                                }
                                catch (Exception e) {
                                    dlg.dismiss();
                                    e.printStackTrace();
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

    static private void registerForPush(final Context context, final Callback<Void> callback) {
        Intent registrationIntent = new Intent("com.google.android.c2dm.intent.REGISTER");
        registrationIntent.putExtra("app", PendingIntent.getBroadcast(context, 0, new Intent(), 0));
        registrationIntent.putExtra("sender", "koushd@gmail.com");
        
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                context.unregisterReceiver(this);
                callback.onCallback(null);
            }
        };
        
        IntentFilter filter = new IntentFilter(C2DMReceiver.ACTION_REGISTRATION_RECEIVED);
        context.registerReceiver(receiver, filter);
        
        context.startService(registrationIntent);
    }
    
    private static final String AUTH_TOKEN_TYPE = "ah";
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
                        delegate.setOnActivityResultCallback(new Callback<Tuple<Integer, Intent>>() {
                            public void onCallback(Tuple<Integer, Intent> result) {
                                if (result.First == 242424)
                                    tryAuth(context, accountName, callback, delegate);
                            }
                        });
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
