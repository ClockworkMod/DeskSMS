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
        
        if (TOGGLE_EMAIL.equals(intent.getAction())) {
            Settings settings = Settings.getInstance(context);
            boolean val = settings.getBoolean("forward_email", true);
            settings.setBoolean("forward_email", !val);
            AppWidgetManager.getInstance(context).updateAppWidget(new ComponentName(context, WidgetProvider.class), getRemoteViews(context));
        }
        else if (TOGGLE_XMPP.equals(intent.getAction())) {
            Settings settings = Settings.getInstance(context);
            boolean val = settings.getBoolean("forward_xmpp", true);
            settings.setBoolean("forward_xmpp", !val);
            AppWidgetManager.getInstance(context).updateAppWidget(new ComponentName(context, WidgetProvider.class), getRemoteViews(context));
        }
        else if (UPDATE.equals(intent.getAction())) {
            AppWidgetManager.getInstance(context).updateAppWidget(new ComponentName(context, WidgetProvider.class), getRemoteViews(context));
        }
    }
    
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);
        
        for (int appWidgetId: appWidgetIds) {
            appWidgetManager.updateAppWidget(appWidgetId, getRemoteViews(context));
        }
    }
    
    public RemoteViews getRemoteViews(Context context) {
        RemoteViews rvs = new RemoteViews(context.getPackageName(), R.layout.widget);
        Settings settings = Settings.getInstance(context);
        boolean forward_email = settings.getBoolean("forward_email", true);
        boolean forward_xmpp = settings.getBoolean("forward_xmpp", true);
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
