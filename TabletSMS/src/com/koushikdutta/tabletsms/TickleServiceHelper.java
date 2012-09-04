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
import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gcm.GCMRegistrar;

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

        URL url = new URL(ServiceHelper.PUSH_URL + "?type=register-device&data.registration=" + URLEncoder.encode("gcm:" + registration) + "&data.device=" + Helper.getSafeDeviceId(context));
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
        if (accounts.length == 0) {
            Helper.showAlertDialog(context, R.string.no_accounts, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    context.startActivity(new Intent(android.provider.Settings.ACTION_SYNC_SETTINGS));
                    callback.onCallback(false);
                }
            });
            return;
        }
        
        
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

                                    dlg.setMessage(context.getString(R.string.checking_desksms));
                                    ThreadingRunnable.background(new ThreadingRunnable() {
                                        boolean mOutDated;
                                        public void run() {
                                            try {
                                                // check to see what version of the client app is running... if push fails,
                                                // we can provide this as a possible reason.
                                                JSONObject whoami = ServiceHelper.retryExecuteAsJSONObject(context, accountName, new URL(ServiceHelper.WHOAMI_URL), null);
                                                mOutDated = whoami.optInt("version_code", 0) < 1110;
                                                Log.i(LOGTAG, "DeskSMS may be outdated... " + mOutDated);
                                                
                                                try {
                                                    // check to see if web forwarding is enabled, if not, enable it and force a sync.
                                                    JSONObject settings = ServiceHelper.retryExecuteAsJSONObject(context, accountName, new URL(ServiceHelper.SETTINGS_URL), null);
                                                    if (!settings.optBoolean("forward_web", false)) {
                                                        // enable web forwarding
                                                        JSONObject forwardResult = ServiceHelper.retryExecuteAsJSONObject(context, accountName, new URL(ServiceHelper.SETTINGS_URL), new ServiceHelper.StringPoster("forward_web=true&tickle=true"));
                                                        Log.i(LOGTAG, "forward result:");
                                                        Log.i(LOGTAG, forwardResult.toString());
//                                                        // this will reset the sync counter
                                                        JSONObject resetResult = ServiceHelper.retryExecuteAsJSONObject(context, accountName, new URL(ServiceHelper.PUSH_URL + "?type=settings&data.last_sms_sync=0&data.last_mms_sync=0&forward_web=true"), null);
                                                        Log.i(LOGTAG, "reset result:");
                                                        Log.i(LOGTAG, resetResult.toString());
                                                        // and this will trigger the sync
                                                        JSONObject syncResult = ServiceHelper.retryExecuteAsJSONObject(context, accountName, new URL(ServiceHelper.PUSH_URL + "?type=outbox"), null);
                                                        Log.i(LOGTAG, "forced sync result:");
                                                        Log.i(LOGTAG, syncResult.toString());
                                                    }
                                                }
                                                catch (Exception ex) {
                                                    ex.printStackTrace();
                                                }
                                                
                                                foreground(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        dlg.setMessage(context.getString(R.string.registering_with_server));
                                                        background(new Runnable() {
                                                            boolean pushReceived = false;
                                                            @Override
                                                            public void run() {
                                                                try {
                                                                    registerWithServer(context);

                                                                    foreground(new Runnable() {
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
                                                                            Helper.showAlertDialog(context, R.string.signin_success,
                                                                                    new DialogInterface.OnClickListener() {
                                                                                        @Override
                                                                                        public void onClick(DialogInterface dialog, int which) {
                                                                                            emailSent.run();
                                                                                        }
                                                                                    });
                                                                        }
                                                                    };
                                                                    IntentFilter filter = new IntentFilter(GCMIntentService.PING);
                                                                    context.registerReceiver(pushReceiver, filter);

                                                                    ServiceHelper.retryExecuteAndDisconnect(context, accountName, new URL(
                                                                            ServiceHelper.PUSH_URL + "?type=echo"), null);

                                                                    Thread.sleep(15000);
                                                                    if (!pushReceived) {
                                                                        settings.setString("account", null);
                                                                        foreground(new Runnable() {
                                                                            @Override
                                                                            public void run() {
                                                                                dlg.dismiss();
                                                                                Helper.showAlertDialog(context, R.string.push_failed,
                                                                                        new DialogInterface.OnClickListener() {
                                                                                            @Override
                                                                                            public void onClick(DialogInterface dialog, int which) {
                                                                                                callback.onCallback(false);
                                                                                            }
                                                                                        });
                                                                            }
                                                                        });
                                                                    }
                                                                }
                                                                catch (Exception ex) {
                                                                    settings.setString("account", null);
                                                                    ex.printStackTrace();
                                                                    foreground(new Runnable() {
                                                                        @Override
                                                                        public void run() {
                                                                            dlg.dismiss();
                                                                            Helper.showAlertDialog(context, R.string.signin_failure,
                                                                                    new DialogInterface.OnClickListener() {
                                                                                        @Override
                                                                                        public void onClick(DialogInterface dialog, int which) {
                                                                                            callback.onCallback(false);
                                                                                        }
                                                                                    });
                                                                        }
                                                                    });
                                                                }
                                                            }
                                                        });
                                                    }
                                                });
                                            }
                                            catch (Exception ex) {
                                                settings.setString("account", null);
                                                ex.printStackTrace();
                                                foreground(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        dlg.dismiss();
                                                        Helper.showAlertDialog(context, R.string.signin_failure);
                                                        callback.onCallback(false);
                                                    }
                                                });
                                            }
                                        };
                                    });
                                }
                                catch (Exception e) {
                                    settings.setString("account", null);
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
        
        GCMRegistrar.unregister(context);
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
