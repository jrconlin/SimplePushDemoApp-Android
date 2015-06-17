/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.mozilla.simplepush.simplepushdemoapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;


/**
 * Main Class for the app.
 * <p/>
 * This uses ActionBarActivity, because that's what I was silly enough to pick at first.
 */
public class MainActivity extends ActionBarActivity {

    public static final String PROPERTY_REG_ID = "registration_id";
    static final String TAG = "SimplepushDemoApp";
    private static final String PROPERTY_APP_VERSION = "appVersion";
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    // The SENDER_ID is the Project Number from Google Developer's Console for this
    // project. 
    String SENDER_ID;
    // ChannelID is the SimplePush channel. This is normally generated as a GUID by the
    // client, but since we're just doing tests and don't really care about the ChannelID...
    String CHANNEL_ID = "abad1dea-0000-0000-0000-000000000000";

    // Various UI controls (TODO: need to add a handler here to allow them to be set from 
    // additional threads)
    TextView mDisplay;
    EditText hostUrl;
    EditText pingData;
    Button sendButton;
    Button connectButton;
    GoogleCloudMessaging gcm;
    Context context;

    // GCM RegistrationId 
    String regid;
    String PushEndpoint;
    String UserAgentID;
    String SharedSecret;

    /** get the app version from the package manifest
     *
     * @param context app Context
     * @return version value
     */
    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo =
                    context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException ex) {
            throw new RuntimeException("Could not get package name: " + ex);
        }
    }

    private Properties loadProperties() throws IOException {
        Properties prop = new Properties();
        try {
            InputStream fileStream = getResources().openRawResource(R.raw.app);
            prop.load(fileStream);
            fileStream.close();
        } catch (FileNotFoundException x) {
            this.err("No properties file found, have you included raw/app.properties?");
        }
        return prop;
    }

    /** Initialize the app from the saved state
     *
     * @param savedInstanceState The previously stored instance
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        Properties config;
        try {
            config = loadProperties();
            SENDER_ID = config.getProperty("sender_id");
            if (SENDER_ID == null) {
                Log.e(TAG, "sender_id not definied in configuration file. Aborting");
                return;
            }
        }catch (IOException x) {
            Log.e(TAG, "Could not load properties");
            return;
        }
        setContentView(R.layout.activity_main);

        // Set the convenience globals.
        mDisplay = (TextView) findViewById(R.id.display);
        hostUrl = (EditText) findViewById(R.id.host_edit);
        pingData = (EditText) findViewById(R.id.message);
        sendButton = (Button) findViewById(R.id.send);
        connectButton = (Button) findViewById(R.id.connect);

        context = getApplicationContext();
        // Check that GCM is available on this device.
        if (checkPlayServices()) {
            gcm = GoogleCloudMessaging.getInstance(this);
            regid = getRegistrationId(context);
        } else {
            Log.i(TAG, "No valid Google Play Services APK found");
        }

        // detect the "enter/submit" key for the editor views.
        hostUrl.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                                              @Override
                                              public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                                                  boolean handled = false;
                                                  // yes, be very careful about this, else you can send multiple actions.
                                                  if (actionId == EditorInfo.IME_ACTION_SEND ||
                                                          (actionId == EditorInfo.IME_ACTION_UNSPECIFIED &&
                                                                  event.getKeyCode() == KeyEvent.KEYCODE_ENTER &&
                                                                  event.getAction() == KeyEvent.ACTION_DOWN)) {
                                                      registerInBackground();
                                                      handled = true;
                                                  }
                                                  return handled;
                                              }
                                          }
        );

        pingData.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                                               @Override
                                               public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                                                   boolean handled = false;
                                                   if (actionId == EditorInfo.IME_ACTION_SEND ||
                                                           (actionId == EditorInfo.IME_ACTION_UNSPECIFIED &&
                                                                   event.getKeyCode() == KeyEvent.KEYCODE_ENTER &&
                                                                   event.getAction() == KeyEvent.ACTION_DOWN)) {
                                                       SendNotification(getMessage());
                                                       handled = true;
                                                   }
                                                   return handled;
                                               }
                                           }
        );
    }

    /** required method
     *
     */
    @Override
    protected void onResume() {
        super.onResume();
        checkPlayServices();
    }

    /** Are we still able to use Play Services?
     *
     * @return boolean
     */
    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
                //toggleConnectToSend(false);

            } else {
                Log.i(TAG, "This device is not supported");
                finish();
            }
            return false;
        }
        return true;
    }

    /** Store the registration ID to shared preferences.
     *
     * @param context Application context
     * @param regId GCM Registration ID
     */
    private void storeRegistrationId(Context context, String regId) {
        final SharedPreferences prefs = getGcmPreferences(context);
        int appVersion = getAppVersion(context);
        Log.i(TAG, "Saving regId on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString(PROPERTY_REG_ID, regId);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        editor.apply();
    }

    private String getRegistrationId(Context context) {
        final SharedPreferences prefs = getGcmPreferences(context);
        String registrationId = prefs.getString(PROPERTY_REG_ID, "");
        if (registrationId == null || registrationId.isEmpty()) {
            Log.i(TAG, "Registration not found.");
            return "";
        }
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if (registeredVersion != currentVersion) {
            Log.i(TAG, "App version changed.");
            return "";
        }
        return registrationId;
    }

    /** Register with GCM in the background, returning the result back to the UI thread

     */
    private void registerInBackground() {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                String msg = "";
                try {
                    if (gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(context);
                    }
                    regid = gcm.register(SENDER_ID);
                    Log.d(TAG, SENDER_ID + " registering " + regid);
                    msg = "Device registered, registration ID = " + regid;
                    // Send the new registration number to SimplePush server
                    sendRegistrationIdToBackend(regid);
                    // And remember it into the preferences.
                    storeRegistrationId(context, regid);
                } catch (IOException ex) {
                    msg = "Error: registerInBackground: doInBackground: " + ex.getMessage();
                    Log.e(TAG, msg);
                } catch (Exception x) {
                    Log.e(TAG, "Unknown exception " + x.toString());
                }
                return msg;
            }

            //Yay! A thing happened. We should let folks know about the thing.
            @Override
            protected void onPostExecute(String msg) {
                mDisplay.append(msg + "\n");
            }
        }.execute(null, null, null);
    }

    /** Button Handler
     *
     * @param view App view
     */
    public void onClick(final View view) {
        if (view == findViewById(R.id.send)) {
            SendNotification(getMessage());
        } else if (view == findViewById(R.id.clear)) {
            mDisplay.setText("");
        } else if (view == findViewById(R.id.connect)) {
            Log.i(TAG, "## Connection requested");
            registerInBackground();
        }
    }

    static char[] hexChars = "0123456789ABCDEF".toCharArray();

    private String bytesToHex(byte[] buff) {
        int len = buff.length;
        char [] chars = new char[len * 2];
        for (int i=0; i<len; i++) {
            int j = buff[i] & 0xFF;
            chars[i*2] = hexChars[j>>>4];
            chars[i*2+1] = hexChars[j & 0x0F];
        }
        return new String(chars);
    }

    private byte[] hexToBytes(String hex) throws IOException{
        int len = hex.length();
        if (len % 2 == 1) {
            hex = "0"+hex;
            len += 1;
        }
        try {
            byte[] buf = new byte[len / 2];
            for (int i = 0; i < len; i += 2) {
                buf[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) +
                        Character.digit(hex.charAt(i + 1), 16));
            }
            return buf;
        }catch (StringIndexOutOfBoundsException x) {
            throw new IOException("Invalid hex string" + hex);
        }
    }

    private String genSignature(UrlEncodedFormEntity body) throws IOException {
        String content = EntityUtils.toString(body);
        SecretKeySpec key = new SecretKeySpec(this.SharedSecret.getBytes("UTF-8"), "HmacSHA256");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] bytes = mac.doFinal(content.getBytes("UTF-8"));
            return bytesToHex(bytes);
        } catch (NoSuchAlgorithmException x) {
            this.err("Invalid hash algo specified, failing " + x.toString());
            throw new IOException("HmacSHA256 unavailable");
        } catch (InvalidKeyException x) {
            this.err("Invalid key specified, failing " + x.toString());
            throw new IOException("Invalid Key");
        }
    }

    /** Send the notification to SimplePush
     *
     * This is normally what the App Server would do. We're handling it ourselves because
     * this app is self-contained.
     *
     * @param data the notification string.
     */
    private void SendNotification(final String data) {
        if (PushEndpoint.length() == 0) {
            return;
        }
        // Run as a background task, passing the data string and getting a success bool.
        new AsyncTask<String, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(String... params) {
                HttpPut req = new HttpPut(PushEndpoint);
                // HttpParams is NOT what you use here.
                // NameValuePairs is just a simple hash.
                List<NameValuePair> rparams = new ArrayList<>(2);
                rparams.add(new BasicNameValuePair("version",
                        String.valueOf(System.currentTimeMillis())));
                // While we're just using a simple string here, there's no reason you couldn't
                // have this be up to 4K of JSON. Just make sure that the GcmIntentService handler
                // knows what to do with it.
                rparams.add(new BasicNameValuePair("data", data));
                Log.i(TAG, "Sending data: " + data);
                try {
                    UrlEncodedFormEntity entity = new UrlEncodedFormEntity(rparams);
                    Log.i(TAG, "params:" + rparams.toString() +
                            " entity: " + entity.toString());
                    entity.setContentType("application/x-www-form-urlencoded");
                    entity.setContentEncoding(new BasicHeader(HTTP.CONTENT_TYPE,
                            "text/plain;charset=UTF-8"));
                    req.setEntity(entity);
                    req.addHeader("Authorization", genSignature(entity));
                    DefaultHttpClient client = new DefaultHttpClient();
                    HttpResponse resp = client.execute(req);
                    int code = resp.getStatusLine().getStatusCode();
                    if (code >= 200 && code < 300) {
                        return true;
                    }
                } catch (ClientProtocolException x) {
                    Log.e(TAG, "Could not send Notification " + x);
                } catch (IOException x) {
                    Log.e(TAG, "Could not send Notification (io) " + x);
                } catch (Exception e) {
                    Log.e(TAG, "flaming crapsticks", e);
                }
                return false;
            }

            /** Report back on what just happened.
             *
             * @param success Status of post execution
             */
            @Override
            protected void onPostExecute(Boolean success) {
                String msg = "was";
                if (!success) {
                    msg = "was not";
                }
                mDisplay.setText("Message " + msg + " sent");
            }
        }.execute(data, null, null);
    }

    /** Fetch the message content from the UI
     *
     * @return The User provided string to use as data
     */
    private String getMessage() {
        return pingData.getText().toString();
    }

    /** Get the Push Host URL
     *
     * @return User provided Push host URL
     */
    private String getTarget() {
        String target = hostUrl.getText().toString();
        Log.i(TAG, "Setting Push Host target to " + target);
        return target;
    }

    /** Connection died. Do whatever cleanup you need to .
     *
     */
    @Override
    protected void onDestroy() {
        Log.i(TAG, "## Destroying");
        super.onDestroy();
        //TODO: Unregister?
    }

    /** Get the GCM Preferences
     *
     * @param context App Context
     * @return shared preferences for the app.
     */
    private SharedPreferences getGcmPreferences(Context context) {
        return getSharedPreferences(MainActivity.class.getSimpleName(), Context.MODE_PRIVATE);
    }

    /*    void ToSend(boolean state) {
            hostUrl.setEnabled(!state);
            connectButton.setEnabled(!state);
            pingData.setEnabled(state);
            sendButton.setEnabled(state);
        }
    */
    private void err(String msg) {
        mDisplay.setText("ERROR " + msg );
        Log.e(TAG, msg);
    }

    /** Send the registration id to the Push Server.
     *
     * Prior versions used a websocket based protocol to exchange this information. This
     * meant that android libraries had to bring in a websocket protocol dependency (see previous
     * versions of this code). This requirement was removed.
     *
     * @param regid GCM Registration ID
     */
    private void sendRegistrationIdToBackend(final String regid) {
        String target = getTarget();
        Log.i(TAG, "Sending out Regid " + regid + " to " + target);
        JSONObject msg = new JSONObject();
        try {
            JSONObject token = new JSONObject();
            msg.put("type", "gcm");
            msg.put("channelID", CHANNEL_ID);
            token.put("token", regid);
            msg.put("data", token);
        } catch (JSONException x) {
            this.err("Could not send registration " + x.toString());
            return;
        }
        HttpPost req = new HttpPost(target);
        try{
            StringEntity body = new StringEntity(msg.toString());
            body.setContentType("application/json");
            body.setContentEncoding(new BasicHeader(HTTP.CONTENT_TYPE,
                    "text/plain; charset=UTF-8"));
            req.setEntity(body);
        } catch (UnsupportedEncodingException x) {
            this.err("Could not format registration message " + x.toString());
            return;
        }
        try {
            DefaultHttpClient client = new DefaultHttpClient();
            HttpResponse resp = client.execute(req);
            int code = resp.getStatusLine().getStatusCode();
            if (code < 200 || code > 299) {
                this.err("Server failed to accept message " + EntityUtils.toString(resp.getEntity()));
                return;
            }
            try {
                JSONObject reply = new JSONObject(new JSONTokener(EntityUtils.toString(resp.getEntity())));
                if (! this.CHANNEL_ID.equals(reply.getString("channelID"))) {
                    this.err("Recieved inappropriate registration info: " +
                            resp.getEntity().toString());
                    return;
                }
                this.PushEndpoint = reply.getString("endpoint");
                this.UserAgentID = reply.getString("uaid");
                this.SharedSecret = reply.getString("secret");
            } catch (JSONException x) {
                this.err("Could not parse registration info " + x.toString());
            }
        } catch (IOException x) {
            this.err("Could not send registration " + x.toString());
        }
    }

    /** Do something with the menu (I currently don't)
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    /** Do something with the something that should be on the menu I've not done things with.
     *
     * @param item Selected menu item
     * @return flag
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}