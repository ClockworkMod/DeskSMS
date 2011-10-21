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

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.ContextThemeWrapper;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.koushikdutta.desktopsms.R;
import com.twofortyfouram.locale.BreadCrumber;

/**
 * This is the "Edit" activity for a Locale Plug-in.
 */
public final class EditActivity extends FragmentActivity implements OnCheckedChangeListener
{
	CheckBox mChkEmail, mChkWeb, mChkXMPP;
	ToggleButton mToggleEmail, mToggleWeb, mToggleXMPP;

    /**
     * Help URL, used for the {@link com.twofortyfouram.locale.platform.R.id#twofortyfouram_locale_menu_help} menu item.
     */
    // TODO: Place a real help URL here
    private static final String HELP_URL = "http://www.clockworkmod.com"; //$NON-NLS-1$

    /**
     * Flag boolean that can only be set to true via the "Don't Save"
     * {@link com.twofortyfouram.locale.platform.R.id#twofortyfouram_locale_menu_dontsave} menu item in
     * {@link #onMenuItemSelected(int, MenuItem)}.
     * <p>
     * If true, then this {@code Activity} should return {@link Activity#RESULT_CANCELED} in {@link #finish()}.
     * <p>
     * If false, then this {@code Activity} should generally return {@link Activity#RESULT_OK} with extras
     * {@link com.twofortyfouram.locale.Intent#EXTRA_BUNDLE} and {@link com.twofortyfouram.locale.Intent#EXTRA_STRING_BLURB}.
     * <p>
     * There is no need to save/restore this field's state when the {@code Activity} is paused.
     */
    /* package */boolean mIsCancelled = false;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(final Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        /*
         * A hack to prevent a private serializable classloader attack
         */
        BundleManager.scrub(getIntent());
        BundleManager.scrub(getIntent().getBundleExtra(com.twofortyfouram.locale.Intent.EXTRA_BUNDLE));

        setContentView(R.layout.plugin);

        setTitle(BreadCrumber.generateBreadcrumb(getApplicationContext(), getIntent(), getString(R.string.plugin_name)));

        final FrameLayout frame = (FrameLayout) findViewById(R.id.frame);
        frame.addView(getLayoutInflater().cloneInContext(new ContextThemeWrapper(this, R.style.Theme_Locale_Light)).inflate(R.layout.frame, frame, false));

        bindUIElements();
        /*
         * if savedInstanceState == null, then then this is a new Activity instance and a check for EXTRA_BUNDLE is needed
         */
        if (savedInstanceState == null)
        {
            final Bundle forwardedBundle = getIntent().getBundleExtra(com.twofortyfouram.locale.Intent.EXTRA_BUNDLE);

            if (BundleManager.isBundleValid(forwardedBundle))
            {
                if(forwardedBundle.containsKey(BundleManager.BUNDLE_EXTRA_FORWARD_EMAIL)){
                	boolean enabled = forwardedBundle.getBoolean
                			(BundleManager.BUNDLE_EXTRA_FORWARD_EMAIL);
                	mChkEmail.setChecked(true);
                	mToggleEmail.setChecked(enabled);
                }else {
                	mChkEmail.setChecked(false);
                	mToggleEmail.setEnabled(false);
                }
                
                if(forwardedBundle.containsKey(BundleManager.BUNDLE_EXTRA_FORWARD_WEB)){
                	boolean enabled = forwardedBundle.getBoolean
                			(BundleManager.BUNDLE_EXTRA_FORWARD_WEB);
                	mChkWeb.setChecked(true);
                	mToggleWeb.setChecked(enabled);
                }else {
                	mChkWeb.setChecked(false);
                	mToggleWeb.setEnabled(false);
                }
                
                if(forwardedBundle.containsKey(BundleManager.BUNDLE_EXTRA_FORWARD_XMPP)){
                	boolean enabled = forwardedBundle.getBoolean
                			(BundleManager.BUNDLE_EXTRA_FORWARD_XMPP);
                	mChkXMPP.setChecked(true);
                	mToggleXMPP.setChecked(enabled);
                }else {
                	mChkXMPP.setChecked(false);
                	mToggleXMPP.setEnabled(false);
                }
            }else{
            	setDefaults();
            }
        }
    }
    
    private void setDefaults(){
    	mChkEmail.setChecked(false);
    	mChkWeb.setChecked(false);
    	mChkXMPP.setChecked(false);
    	
    	mToggleEmail.setEnabled(false);
    	mToggleWeb.setEnabled(false);
    	mToggleXMPP.setEnabled(false);
    }
    
    private void bindUIElements(){
    	mChkEmail = ((CheckBox) findViewById(R.id.chkEmail));
    	mToggleEmail = ((ToggleButton)findViewById(R.id.toggleEmail));
    	mChkWeb = ((CheckBox) findViewById(R.id.chkWeb));
    	mToggleWeb = ((ToggleButton)findViewById(R.id.toggleWeb));
    	mChkXMPP = ((CheckBox) findViewById(R.id.chkXMPP));
    	mToggleXMPP = ((ToggleButton)findViewById(R.id.toggleXMPP));
    	
    	mChkEmail.setOnCheckedChangeListener(this);
    	mChkWeb.setOnCheckedChangeListener(this);
    	mChkXMPP.setOnCheckedChangeListener(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void finish()
    {
        if (mIsCancelled)
        {
            setResult(RESULT_CANCELED);
        }
        else
        {
            if(!mChkEmail.isChecked() && !mChkWeb.isChecked() && !mChkXMPP.isChecked()){
            	Toast.makeText(getBaseContext(), "Please select at least one setting before proceeding.", Toast.LENGTH_LONG).show();
                return;
            }
            else
            {
                /*
                 * This is the return Intent, into which we'll put all the required extras
                 */
                final Intent returnIntent = new Intent();

                /*
                 * This extra is the data to ourselves: either for the Activity or the BroadcastReceiver. Note that anything
                 * placed in this Bundle must be available to Locale's class loader. So storing String, int, and other standard
                 * objects will work just fine. However Parcelable objects must also be Serializable. And Serializable objects
                 * must be standard Java objects (e.g. a private subclass to this plug-in cannot be stored in the Bundle, as
                 * Locale's classloader will not recognize it).
                 */
                final Bundle returnBundle = new Bundle();
                
                if(mChkEmail.isChecked()){
                	returnBundle.putBoolean(BundleManager.BUNDLE_EXTRA_FORWARD_EMAIL,
                			mToggleEmail.isChecked());
                } 
                if(mChkWeb.isChecked()){
                	returnBundle.putBoolean(BundleManager.BUNDLE_EXTRA_FORWARD_WEB,
                			mToggleWeb.isChecked());
                }
                if(mChkXMPP.isChecked()){
                	returnBundle.putBoolean(BundleManager.BUNDLE_EXTRA_FORWARD_XMPP,
                			mToggleXMPP.isChecked());
                }
                

                returnIntent.putExtra(com.twofortyfouram.locale.Intent.EXTRA_BUNDLE, returnBundle);

                /*
                 * This is the blurb concisely describing what your setting's state is. This is simply used for display in the UI.
                 */
                returnIntent.putExtra(com.twofortyfouram.locale.Intent.EXTRA_STRING_BLURB, "DeskSMS Update");

                setResult(RESULT_OK, returnIntent);
            }
        }

        super.finish();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onCreateOptionsMenu(final Menu menu)
    {
        super.onCreateOptionsMenu(menu);

        /*
         * inflate the default menu layout from XML
         */
        getMenuInflater().inflate(R.menu.twofortyfouram_locale_help_save_dontsave, menu);

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onMenuItemSelected(final int featureId, final MenuItem item)
    {

        /*
         * Royal pain in the butt to support the home button in SDK 11's ActionBar
         */
        if (Build.VERSION.SDK_INT >= 11)
        {
            try
            {
                if (item.getItemId() == android.R.id.class.getField("home").getInt(null)) //$NON-NLS-1$
                {
                    // app icon in Action Bar clicked; go home
                    final Intent intent = new Intent(getPackageManager().getLaunchIntentForPackage(getCallingPackage()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    finish();
                    return true;
                }
            }
            catch (final NoSuchFieldException e)
            {
                // this should never happen on SDK 11 or greater
                throw new RuntimeException(e);
            }
            catch (final IllegalAccessException e)
            {
                // this should never happen on SDK 11 or greater
                throw new RuntimeException(e);
            }
        }

        switch (item.getItemId())
        {
            case R.id.twofortyfouram_locale_menu_help:
            {
                try
                {
                    startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(HELP_URL)));
                }
                catch (final Exception e)
                {
                    Toast.makeText(getApplicationContext(), R.string.twofortyfouram_locale_application_not_available, Toast.LENGTH_LONG).show();
                }

                return true;
            }
            case R.id.twofortyfouram_locale_menu_dontsave:
            {
                mIsCancelled = true;
                finish();
                return true;
            }
            case R.id.twofortyfouram_locale_menu_save:
            {
                finish();
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		if(buttonView.getId() == mChkEmail.getId()){
			mToggleEmail.setEnabled(isChecked);
			if(!isChecked) mToggleEmail.setChecked(false);
		}else if(buttonView.getId() == mChkWeb.getId()){
			mToggleWeb.setEnabled(isChecked);
			if(!isChecked) mToggleWeb.setChecked(false);
		}else if(buttonView.getId() == mChkXMPP.getId()){
			mToggleXMPP.setEnabled(isChecked);
			if(!isChecked) mToggleXMPP.setChecked(false);
		}
	}
}