package com.koushikdutta.tabletsms;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;

import com.google.android.gcm.GCMRegistrar;

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

    static void registerWithServer(final Context context) throws Exception {
        Settings settings = Settings.getInstance(context);
        final String registration = settings.getString("registration_id");
        final String account = settings.getString("account");

        URL url = new URL(ServiceHelper.PUSH_URL + "?type=register-device&data.registration=gcm:" + URLEncoder.encode(registration));
        ServiceHelper.retryExecuteAndDisconnect(context, account, url, null);
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

    public static void login(final Activity context, final Callback<Boolean> callback) {
        final Settings settings = Settings.getInstance(context);
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
                        tryAuth(context, accountName, new Callback<String>() {
                            public void onCallback(String authToken) {
                                try {
                                    if (authToken == null)
                                        throw new Exception();

                                    dlg.setMessage(context.getString(R.string.registering_with_server));
                                    new Thread() {
                                        boolean pushReceived = false;
                                        public void run() {
                                            try {
                                                registerWithServer(context);

                                                context.runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        dlg.setMessage(context.getString(R.string.testing_push));
                                                    }
                                                });
                                                
                                                final Runnable emailSent = new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        Helper.showAlertDialog(context, R.string.signin_complete, new OnClickListener() {
                                                            @Override
                                                            public void onClick(DialogInterface dialog, int which) {
                                                                callback.onCallback(true);
                                                            }
                                                        });
                                                    }
                                                };

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
                                                                emailSent.run();
                                                            }
                                                        });
                                                    }
                                                };
                                                IntentFilter filter = new IntentFilter(GCMIntentService.PING);
                                                context.registerReceiver(pushReceiver, filter);

                                                ServiceHelper.retryExecuteAndDisconnect(context, accountName, new URL(ServiceHelper.PUSH_URL + "?type=echo"), null);

                                                Thread.sleep(10000);
                                                if (!pushReceived) {
                                                    context.runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            dlg.dismiss();
                                                            Helper.showAlertDialog(context, R.string.push_failed, new DialogInterface.OnClickListener() {
                                                                @Override
                                                                public void onClick(DialogInterface dialog, int which) {
                                                                    emailSent.run();
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
                        });
                    }
                });
            }
        });
        builder.setCancelable(true);
        builder.create().show();
    }

    static void registerForPush(final Context context, final Callback<Void> callback) {
        if (callback != null) {
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    context.unregisterReceiver(this);
                    callback.onCallback(null);
                }
            };
        
            IntentFilter filter = new IntentFilter(GCMIntentService.ACTION_REGISTRATION_RECEIVED);
            context.registerReceiver(receiver, filter);
        }
        
        GCMRegistrar.register(context, "960629859371");
    }
    
    static final String AUTH_TOKEN_TYPE = "ah";
    private static void tryAuth(final Activity context, final String accountName, final Callback<String> callback) {
        AccountManager accountManager = AccountManager.get(context);
        Account account = new Account(accountName, "com.google");
        String curAuthToken = Settings.getInstance(context).getString("web_connect_auth_token");
        if (!Helper.isJavaScriptNullOrEmpty(curAuthToken))
            accountManager.invalidateAuthToken(account.type, curAuthToken);
        accountManager.getAuthToken(account, AUTH_TOKEN_TYPE, null, context, new AccountManagerCallback<Bundle>() {
            public void run(AccountManagerFuture<Bundle> future) {
                try {
                    Bundle bundle = future.getResult();
                    final String authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
                    if (authToken != null) {
                        Settings settings = Settings.getInstance(context);
                        settings.setString("web_connect_auth_token", authToken);
                        settings.setString("account", accountName.toLowerCase());
                    }
                    callback.onCallback(authToken);
                }
                catch (Exception ex) {
                    callback.onCallback(null);
                    ex.printStackTrace();
                }
            }
        }, null);
    }

}
