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

/**
 * Class of constants used by this Locale plug-in.
 */
public final class Constants
{
    /**
     * Private constructor prevents instantiation
     * 
     * @throws UnsupportedOperationException because this class cannot be instantiated.
     */
    private Constants()
    {
        throw new UnsupportedOperationException("This class is non-instantiable"); //$NON-NLS-1$
    }

    public static final String INTENT_SET_SETTINGS = "com.koushikdutta.desktopsms.SET_SETTINGS";
    
    /**
     * Log tag for logcat messages
     */
    // TODO: Change this to your application's own log tag
    public static final String LOG_TAG = "DesktopSMS"; //$NON-NLS-1$

    /**
     * Flag to enable logcat messages.
     */
    public static final boolean IS_LOGGABLE = true;

    /**
     * Flag to enable runtime checking of method parameters.
     */
    public static final boolean ENABLE_PARAMETER_CHECKING = false;

    /**
     * Flag to enable runtime checking of whether a method is called on the correct thread.
     */
    public static final boolean ENABLE_CORRECT_THREAD_CHECKING = false;
}