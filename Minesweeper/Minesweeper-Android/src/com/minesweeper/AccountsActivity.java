/*
 * Copyright 2010 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.minesweeper;

import java.io.IOException;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;

import com.google.android.c2dm.C2DMessaging;

/**
 * Account selections activity - handles device registration and unregistration.
 */
public class AccountsActivity extends Activity {

    /**
     * Tag for logging.
     */
    private static final String TAG = "AccountsActivity";

    /**
     * Cookie name for authorization.
     */
    private static final String AUTH_COOKIE_NAME = "SACSID";

    /**
     * True if we are waiting for App Engine authorization.
     */
    private boolean mPendingAuth = false;

    /**
     * The current context.
     */
    private Context mContext = this;

    /**
     * Begins the activity.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        setScreenContent(R.layout.connect);
    }

    /**
     * Resumes the activity.
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (mPendingAuth) {
            mPendingAuth = false;
            String regId = C2DMessaging.getRegistrationId(mContext);
            if (regId != null && !"".equals(regId)) {
                DeviceRegistrar.registerOrUnregister(mContext, regId, true);
            } else {
                C2DMessaging.register(mContext, Setup.SENDER_ID);
            }
        }
    }

    // Manage UI Screens

    /**
     * Sets up the 'connect' screen content.
     */
    private void setConnectScreenContent() {
        // Check Internet access

	    // Set "connecting" status
		SharedPreferences prefs = Util.getSharedPreferences(mContext);
		prefs.edit().putString(Util.CONNECTION_STATUS, Util.CONNECTING).commit();
		// Register
		register(Setup.SENDER_ID);
        finish();
    }

    /**
     * Sets the screen content based on the screen id.
     */
    private void setScreenContent(int screenId) {
        setContentView(screenId);
        switch (screenId) {
            case R.layout.connect:
                setConnectScreenContent();
                break;
        }
    }

    // Register and Unregister

    /**
     * Registers for C2DM messaging with the given account name.
     * 
     * @param accountName a String containing a Google account name
     */
    private void register(final String accountName) {
        // Store the account name in shared preferences
        final SharedPreferences prefs = Util.getSharedPreferences(mContext);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(Util.ACCOUNT_NAME, accountName);
        editor.remove(Util.AUTH_COOKIE);
        editor.remove(Util.DEVICE_REGISTRATION_ID);
        editor.commit();

        // Obtain an auth token and register
        final AccountManager mgr = AccountManager.get(mContext);
        Account[] accts = mgr.getAccountsByType("com.google");
        for (Account acct : accts) {
            final Account account = acct;
            if (account.name.equals(accountName)) {
                if (Util.isDebug(mContext)) {
                    // Use a fake cookie for the dev mode app engine server
                    // The cookie has the form email:isAdmin:userId
                    // We set the userId to be the same as the email
                    String authCookie = "dev_appserver_login=" + accountName + ":false:"
                            + accountName;
                    prefs.edit().putString(Util.AUTH_COOKIE, authCookie).commit();
                    C2DMessaging.register(mContext, Setup.SENDER_ID);
                } else {
                    // Get the auth token from the AccountManager and convert
                    // it into a cookie for the appengine server
                    final Activity activity = this;
                    mgr.getAuthToken(account, "ah", null, activity, new AccountManagerCallback<Bundle>() {
                        public void run(AccountManagerFuture<Bundle> future) {
                            String authToken = getAuthToken(future);
                            // Ensure the token is not expired by invalidating it and
                            // obtaining a new one
                            mgr.invalidateAuthToken(account.type, authToken);
                            mgr.getAuthToken(account, "ah", null, activity, new AccountManagerCallback<Bundle>() {
                                public void run(AccountManagerFuture<Bundle> future) {
                                    String authToken = getAuthToken(future);
                                    // Convert the token into a cookie for future use
                                    String authCookie = getAuthCookie(authToken);
                                    Editor editor = prefs.edit();
                                    editor.putString(Util.AUTH_COOKIE, authCookie);
                                    editor.commit();
                                    C2DMessaging.register(mContext, Setup.SENDER_ID);
                                }
                            }, null);
                        }
                    }, null);
                }
                break;
            }
        }
    }

    private String getAuthToken(AccountManagerFuture<Bundle> future) {
        try {
            Bundle authTokenBundle = future.getResult();
            String authToken = authTokenBundle.get(AccountManager.KEY_AUTHTOKEN).toString();
            return authToken;
        } catch (Exception e) {
            Log.w(TAG, "Got Exception " + e);
            return null;
        }
    }

    // Utility Methods

    /**
     * Retrieves the authorization cookie associated with the given token. This
     * method should only be used when running against a production appengine
     * backend (as opposed to a dev mode server).
     */
    private String getAuthCookie(String authToken) {
        DefaultHttpClient httpClient = new DefaultHttpClient();
        try {
            // Get SACSID cookie
            httpClient.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, false);
            String uri = Setup.PROD_URL + "/_ah/login?continue=http://localhost/&auth=" + authToken;
            HttpGet method = new HttpGet(uri);

            HttpResponse res = httpClient.execute(method);
            StatusLine statusLine = res.getStatusLine();
            int statusCode = statusLine.getStatusCode();
            Header[] headers = res.getHeaders("Set-Cookie");
            if (statusCode != 302 || headers.length == 0) {
                return null;
            }

            for (Cookie cookie : httpClient.getCookieStore().getCookies()) {
                if (AUTH_COOKIE_NAME.equals(cookie.getName())) {
                    return AUTH_COOKIE_NAME + "=" + cookie.getValue();
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Got IOException " + e);
            Log.w(TAG, Log.getStackTraceString(e));
        } finally {
            httpClient.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, true);
        }

        return null;
    }

}
