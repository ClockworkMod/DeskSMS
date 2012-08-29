package com.koushikdutta.tabletsms;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.provider.ContactsContract;
import android.util.Log;

import com.google.android.gcm.GCMRegistrar;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper.UrlDownloader;

public class TabletSmsApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Helper.startSync(this);

        final Handler handler = new Handler();
        final UrlDownloader downloader = UrlImageViewHelper.getDefaultDownloader();
        UrlDownloader photoDownloader = new UrlDownloader() {
            @Override
            public void download(final Context context, final String url, final String filename, final Runnable loader, final Runnable completion) {
                if (!url.startsWith("content")) {
                    downloader.download(context, url, filename, loader, completion);
                    return;
                }
                new Thread() {
                    public void run() {
                        ContentResolver cr = context.getContentResolver();
                        InputStream input = ContactsContract.Contacts.openContactPhotoInputStream(cr, Uri.parse(url));
                        try {
                            FileOutputStream output = new FileOutputStream(filename);
                            StreamUtility.copyStream(input, output);
                            output.close();
                        }
                        catch (IOException e) {
                        }
                        finally {
                            try {
                                input.close();
                            }
                            catch (IOException e) {
                            }
                        }
                        loader.run();
                        handler.post(completion);
                    };
                }.start();

            }
        };
        UrlImageViewHelper.useDownloader(photoDownloader);
    }
}
