package com.koushikdutta.desktopsms;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class WidgetProvider extends AppWidgetProvider {
    private static final String TOGGLE_EMAIL = "com.koushikdutta.desktopsms.TOGGLE_EMAIL";
    private static final String TOGGLE_XMPP = "com.koushikdutta.desktopsms.TOGGLE_XMPP";
    public static final String UPDATE = "com.koushikdutta.desktopsms.APPWIDGET_UPDATE";

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        
        Settings settings = Settings.getInstance(context);
        boolean xmpp = settings.getBoolean("forward_xmpp", true);
        boolean email = settings.getBoolean("forward_email", true);
        if (TOGGLE_EMAIL.equals(intent.getAction())) {
            AppWidgetManager.getInstance(context).updateAppWidget(new ComponentName(context, WidgetProvider.class), getRemoteViews(context, xmpp, !email));
            ServiceHelper.updateSettings(context, xmpp, !email, null);
        }
        else if (TOGGLE_XMPP.equals(intent.getAction())) {
            AppWidgetManager.getInstance(context).updateAppWidget(new ComponentName(context, WidgetProvider.class), getRemoteViews(context, !xmpp, email));
            ServiceHelper.updateSettings(context, !xmpp, email, null);
        }
        else if (UPDATE.equals(intent.getAction())) {
            AppWidgetManager.getInstance(context).updateAppWidget(new ComponentName(context, WidgetProvider.class), getRemoteViews(context, xmpp, email));
        }
    }
    
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);
        
        Settings settings = Settings.getInstance(context);
        boolean xmpp = settings.getBoolean("forward_xmpp", true);
        boolean email = settings.getBoolean("forward_email", true);
        for (int appWidgetId: appWidgetIds) {
            appWidgetManager.updateAppWidget(appWidgetId, getRemoteViews(context, xmpp, email));
        }
    }
    
    public RemoteViews getRemoteViews(Context context, boolean forward_xmpp, boolean forward_email) {
        System.out.println(forward_xmpp);
        System.out.println(forward_email);
        RemoteViews rvs = new RemoteViews(context.getPackageName(), R.layout.widget);
        rvs.setImageViewResource(R.id.email_ind, forward_email ? R.drawable.appwidget_settings_ind_mid_l : R.drawable.appwidget_settings_ind_mid_red_l);
        rvs.setImageViewResource(R.id.xmpp_ind, forward_xmpp ? R.drawable.appwidget_settings_ind_mid_r : R.drawable.appwidget_settings_ind_mid_red_r);

        Intent i = new Intent(TOGGLE_EMAIL);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);
        rvs.setOnClickPendingIntent(R.id.forward_email, pi);

        i = new Intent(TOGGLE_XMPP);
        pi = PendingIntent.getBroadcast(context, 0, i, 0);
        rvs.setOnClickPendingIntent(R.id.forward_xmpp, pi);

        return rvs;
    }
}
