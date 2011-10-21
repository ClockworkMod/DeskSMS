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
    private static final String TOGGLE_WEB = "com.koushikdutta.desktopsms.TOGGLE_WEB";
    public static final String UPDATE = "com.koushikdutta.desktopsms.APPWIDGET_UPDATE";

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        
        Settings settings = Settings.getInstance(context);
        boolean xmpp = settings.getBoolean("forward_xmpp", true);
        boolean email = settings.getBoolean("forward_email", true);
        boolean web = settings.getBoolean("forward_web", true);
        if (TOGGLE_EMAIL.equals(intent.getAction())) {
            AppWidgetManager.getInstance(context).updateAppWidget(new ComponentName(context, WidgetProvider.class), getRemoteViews(context, xmpp, !email, web));
            ServiceHelper.updateSettings(context, xmpp, !email, web, null);
        }
        else if (TOGGLE_XMPP.equals(intent.getAction())) {
            AppWidgetManager.getInstance(context).updateAppWidget(new ComponentName(context, WidgetProvider.class), getRemoteViews(context, !xmpp, email, web));
            ServiceHelper.updateSettings(context, !xmpp, email, web, null);
        }
        else if (TOGGLE_WEB.equals(intent.getAction())) {
            AppWidgetManager.getInstance(context).updateAppWidget(new ComponentName(context, WidgetProvider.class), getRemoteViews(context, xmpp, email, !web));
            ServiceHelper.updateSettings(context, xmpp, email, !web, null);
        }
        else if (UPDATE.equals(intent.getAction())) {
            AppWidgetManager.getInstance(context).updateAppWidget(new ComponentName(context, WidgetProvider.class), getRemoteViews(context, xmpp, email, web));
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);
        
        Settings settings = Settings.getInstance(context);
        boolean xmpp = settings.getBoolean("forward_xmpp", true);
        boolean email = settings.getBoolean("forward_email", true);
        boolean web = settings.getBoolean("forward_web", true);
        for (int appWidgetId: appWidgetIds) {
            appWidgetManager.updateAppWidget(appWidgetId, getRemoteViews(context, xmpp, email, web));
        }
    }

    public RemoteViews getRemoteViews(Context context, boolean forward_xmpp, boolean forward_email, boolean forward_web) {
        RemoteViews rvs = new RemoteViews(context.getPackageName(), R.layout.widget);
        rvs.setImageViewResource(R.id.email_ind, forward_email ? R.drawable.appwidget_settings_ind_on_l : R.drawable.appwidget_settings_ind_mid_red_l);
        rvs.setImageViewResource(R.id.xmpp_ind, forward_xmpp ? R.drawable.appwidget_settings_ind_on_c : R.drawable.appwidget_settings_ind_mid_red_c);
        rvs.setImageViewResource(R.id.web_ind, forward_web ? R.drawable.appwidget_settings_ind_on_r : R.drawable.appwidget_settings_ind_mid_red_r);

        Intent i = new Intent(TOGGLE_EMAIL);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);
        rvs.setOnClickPendingIntent(R.id.forward_email, pi);

        i = new Intent(TOGGLE_XMPP);
        pi = PendingIntent.getBroadcast(context, 0, i, 0);
        rvs.setOnClickPendingIntent(R.id.forward_xmpp, pi);

        i = new Intent(TOGGLE_WEB);
        pi = PendingIntent.getBroadcast(context, 0, i, 0);
        rvs.setOnClickPendingIntent(R.id.forward_web, pi);
        
        return rvs;
    }
}
