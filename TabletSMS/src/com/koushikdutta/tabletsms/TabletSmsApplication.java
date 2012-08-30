package com.koushikdutta.tabletsms;

import java.util.ArrayList;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.app.Application;
import android.content.Context;

import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper.RequestPropertiesCallback;

public class TabletSmsApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Helper.startSync(this);

        UrlImageViewHelper.setRequestPropertiesCallback(new RequestPropertiesCallback() {
            @Override
            public ArrayList<NameValuePair> getHeadersForRequest(Context context, String url) {
                if (url.startsWith("https://desksms.appspot.com/")) {
                    ArrayList<NameValuePair> props = new ArrayList<NameValuePair>();
                    Settings settings = Settings.getInstance(context);
                    String ascidCookie = settings.getString("Cookie");
                    props.add(new BasicNameValuePair("Cookie", ascidCookie));
                    props.add(new BasicNameValuePair("X-Same-Domain", "1"));
                    return props;
                }
                return null;
            }
        });
    }
}
