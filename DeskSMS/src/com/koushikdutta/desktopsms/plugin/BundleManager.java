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

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;


/**
 * Class for managing the {@link com.twofortyfouram.locale.Intent#EXTRA_BUNDLE} for this plug-in.
 */
public final class BundleManager
{
    /**
     * Private constructor prevents instantiation
     * 
     * @throws UnsupportedOperationException because this class cannot be instantiated.
     */
    private BundleManager()
    {
        throw new UnsupportedOperationException("This class is non-instantiable"); //$NON-NLS-1$
    }

    /**
     * Type: {@code String}
     * <p>
     * String message to display in a Toast message.
     */
    public static final String BUNDLE_EXTRA_FORWARD_WEB = "com.koushikdutta.desktopsms.extra.FORWARD_WEB"; //$NON-NLS-1$
    public static final String BUNDLE_EXTRA_FORWARD_EMAIL = "com.koushikdutta.desktopsms.extra.FORWARD_EMAIL"; //$NON-NLS-1$
    public static final String BUNDLE_EXTRA_FORWARD_XMPP = "com.koushikdutta.desktopsms.extra.FORWARD_XMPP"; //$NON-NLS-1$

    /**
     * Scrubs Intents for private serializable subclasses in the Intent extras. If the Intent's extras contain a private
     * serializable subclass, the Bundle is cleared. The Bundle will not be set to null. If the Bundle is null, has no extras, or
     * the extras do not contain a private serializable subclass, the Bundle is not mutated.
     * 
     * @param intent {@code Intent} to scrub. This parameter may be mutated if scrubbing is necessary. This parameter may be null.
     * @return true if the Intent was scrubbed, false if the Intent was not modified.
     */
    public static boolean scrub(final Intent intent)
    {
        if (intent == null)
        {
            return false;
        }

        return scrub(intent.getExtras());
    }

    /**
     * Scrubs Bundles for private serializable subclasses in the extras. If the Bundle's extras contain a private serializable
     * subclass, the Bundle is cleared. If the Bundle is null, has no extras, or the extras do not contain a private serializable
     * subclass, the Bundle is not mutated.
     * 
     * @param bundle {@code Bundle} to scrub. This parameter may be mutated if scrubbing is necessary. This parameter may be null.
     * @return true if the Bundle was scrubbed, false if the Bundle was not modified.
     */
    public static boolean scrub(final Bundle bundle)
    {
        if (bundle == null)
        {
            return false;
        }

        /*
         * Note: This is a hack to work around a private serializable classloader attack
         */
        try
        {
            // if a private serializable exists, this will throw an exception
            bundle.containsKey(null);
        }
        catch (final Exception e)
        {
            bundle.clear();
            return true;
        }

        return false;
    }
    
    /**
     * Method to verify the content of the bundle are correct.
     * <p>
     * This method will not mutate {@code bundle}.
     * 
     * @param bundle bundle to verify. May be null, which will always return false.
     * @return true if the Bundle is valid, false if the bundle is invalid.
     */
    public static boolean isBundleValid(final Bundle bundle)
    {
        if (bundle == null)
        {
            return false;
        }

        /*
         * Make sure the expected extras exist
         */
        if (!bundle.containsKey(BUNDLE_EXTRA_FORWARD_EMAIL) &&
        		!bundle.containsKey(BUNDLE_EXTRA_FORWARD_WEB) &&
        		!bundle.containsKey(BUNDLE_EXTRA_FORWARD_XMPP))
        {
            if (Constants.IS_LOGGABLE)
            {
                Log.e(Constants.LOG_TAG, String.format("bundle must contain at least one expected extra")); //$NON-NLS-1$
            }
            return false;
        }

        /*
         * Make sure the correct number of extras exist. Run this test after checking for specific Bundle extras above so that the
         * error message is more useful. (E.g. the caller will see what extras are missing, rather than just a message that there
         * is the wrong number).
         */
        if (bundle.keySet().size() < 1 || bundle.keySet().size() > 3)
        {
            if (Constants.IS_LOGGABLE)
            {
                Log.e(Constants.LOG_TAG, String.format("bundle must contain between 1-3 keys, but currently contains %d keys: %s", Integer.valueOf(bundle.keySet().size()), bundle.keySet() //$NON-NLS-1$
                                                                                                                                                                       .toString()));
            }
            return false;
        }

        return true;
    }
}