/*
 * Copyright 2011 two forty four a.m. LLC <http://www.twofortyfouram.com>
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at <http://www.apache.org/licenses/LICENSE-2.0>
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.koushikdutta.desktopsms.plugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * This is the "fire" BroadcastReceiver for a Locale Plug-in setting.
 */
public final class FireReceiver extends BroadcastReceiver
{

    /**
     * @param context {@inheritDoc}.
     * @param intent the incoming {@link com.twofortyfouram.locale.Intent#ACTION_FIRE_SETTING} Intent. This should contain the
     *            {@link com.twofortyfouram.locale.Intent#EXTRA_BUNDLE} that was saved by {@link EditActivity} and later broadcast
     *            by Locale.
     */
    @Override
    public void onReceive(final Context context, final Intent intent)
    {
        /*
         * Always be sure to be strict on input parameters! A malicious third-party app could always send an empty or otherwise
         * malformed Intent. And since Locale applies settings in the background, the plug-in definitely shouldn't crash in the
         * background.
         */

        /*
         * Locale guarantees that the Intent action will be ACTION_FIRE_SETTING
         */
        if (!com.twofortyfouram.locale.Intent.ACTION_FIRE_SETTING.equals(intent.getAction()))
        {
            if (Constants.IS_LOGGABLE)
            {
                Log.e(Constants.LOG_TAG, String.format("Received unexpected Intent action %s", intent.getAction())); //$NON-NLS-1$
            }
            return;
        }

        /*
         * A hack to prevent a private serializable classloader attack
         */
        BundleManager.scrub(intent);
        BundleManager.scrub(intent.getBundleExtra(com.twofortyfouram.locale.Intent.EXTRA_BUNDLE));

        final Bundle bundle = intent.getBundleExtra(com.twofortyfouram.locale.Intent.EXTRA_BUNDLE);

        /*
         * Final verification of the plug-in Bundle before firing the setting.
         */
        if (BundleManager.isBundleValid(bundle))
        {
            Intent i = new Intent(Constants.INTENT_SET_SETTINGS);
        	if(bundle.containsKey(BundleManager.BUNDLE_EXTRA_FORWARD_EMAIL)){
            	i.putExtra(Enums.forward_email.name(), 
            			bundle.getBoolean(BundleManager.BUNDLE_EXTRA_FORWARD_EMAIL));
            }
        	if(bundle.containsKey(BundleManager.BUNDLE_EXTRA_FORWARD_WEB)){
            	i.putExtra(Enums.forward_web.name(), 
            			bundle.getBoolean(BundleManager.BUNDLE_EXTRA_FORWARD_WEB));
            }
        	if(bundle.containsKey(BundleManager.BUNDLE_EXTRA_FORWARD_XMPP)){
            	i.putExtra(Enums.forward_xmpp.name(), 
            			bundle.getBoolean(BundleManager.BUNDLE_EXTRA_FORWARD_XMPP));
            }
        	context.sendBroadcast(i);
        }
    }
}