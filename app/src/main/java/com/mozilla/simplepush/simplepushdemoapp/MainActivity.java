/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.mozilla.simplepush.simplepushdemoapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
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
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;


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
    // project. This is a default value and will be replaced by the value stored in the Manifest.
    String SENDER_ID = "1009375523940";
    // ChannelID is the SimplePush channel. This is normally generated as a GUID by the
    // client, but since we're just doing tests and don't really care about the ChannelID...
    // This is a default value and will be replaced by the value stored in the Manifest.
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

    /** get the app version from the package manifest
     *
     * @param context app Context
     * @return appVersion version of the app
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


    /** Initialize the app from the saved state
     *
     * @param savedInstanceState Previous state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = getApplicationContext();

        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(context.getPackageName(),
                    PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            if (bundle.getString("SenderId") != null) {
                SENDER_ID = bundle.getString("SenderId");
            }
            if (bundle.getString("ChannelId") != null) {
                CHANNEL_ID = bundle.getString("ChannelId");
            }
        } catch (PackageManager.NameNotFoundException x) {
            throw new RuntimeException("Could not get config info: " + x);
        } catch (NullPointerException x) {
            Log.e(TAG, "Couldn't get info " + x);
        }

        // Set the convenience globals.
        mDisplay = (TextView) findViewById(R.id.display);
        hostUrl = (EditText) findViewById(R.id.host_edit);
        pingData = (EditText) findViewById(R.id.message);
        sendButton = (Button) findViewById(R.id.send);
        connectButton = (Button) findViewById(R.id.connect);

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
        if (registrationId != null && registrationId.isEmpty()) {
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
                String msg;
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
     * @param view current view
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
             * @param success boolean
             */
            @Override
            protected void onPostExecute(Boolean success) {
                String msg = "was";
                if (!success) {
                    msg = "was not";
                }
                mDisplay.setText("Message " + msg + " sent\n");
            }
        }.execute(data, null, null);
    }

    /** Fetch the message content from the UI
     *
     * @return pingData as string
     */
    private String getMessage() {
        return pingData.getText().toString();
    }

    /** Get the Push Host URL
     *
     * @return Host URL string
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

    private SharedPreferences getGcmPreferences(Context ignored) {
        return getSharedPreferences(MainActivity.class.getSimpleName(), Context.MODE_PRIVATE);
    }

/*    void ToSend(boolean state) {
        hostUrl.setEnabled(!state);
        connectButton.setEnabled(!state);
        pingData.setEnabled(state);
        sendButton.setEnabled(state);
    }
*/

    /** Create a websocket connection and send the registration id to the Push Server.
     *
     * A real app could drop the WebSocket connection after the "registration" response.
     * This app keeps the connection open for debugging reasons. If a GCM notification fails
     * (which happens, I was getting a string of 401 errors from GCM until the service
     * decided to just let messages come through.), SimplePush falls back to "traditional"
     * mechanisms and will try to send the message via the WebSocket connection. Granted,
     * for real apps, having two socket connections open burns battery life, so don't do
     * that.
     * @param regid Registration ID
     */
    private void sendRegistrationIdToBackend(final String regid) {
        String target = getTarget();
        Log.i(TAG, "Sending out Registration message for RegId: " + regid + " to " + target);
        //TODO: Put this in an async task?
        try {
            URI uri = new URI(target);
            // Draft_17 is the final, production draft. Be sure to use that one only.
            WebSocketClient ws = new WebSocketClient(uri, new Draft_17()) {
                private String TAG = "WEBSOCKET";

                @Override
                public void onMessage(String message) {
                    Log.i(TAG, "got message:" + message + "\n");
                    // Handle pongs specially, since the json parser will choke on them.
                    if (message.equals("{}")) {
                        Log.i(TAG, "Pong...");
                        return;
                    }
                    try {
                        // Parse the object.
                        JSONObject msg = new JSONObject(new JSONTokener(message));
                        String msgType = msg.getString("messageType");
                        switch (msgType) {
                            case "hello":
                                Log.i(TAG, "Sending registration message");
                                JSONObject regObj = new JSONObject();
                                // If this app were "real", we would only send a
                                // registration if we wanted a new Channel. If we had
                                // already registered (and were reconnecting) we would
                                // not need to send a new registration message.
                                // Each endpoint is tied to a UserAgentID + ChannelID, so
                                // if you get a new UAID or ChannelID, any old Endpoint
                                // becomes invalid.
                                regObj.put("channelID", CHANNEL_ID);
                                regObj.put("messageType", "register");
                                this.send(regObj.toString());
                                break;
                            case "register":
                                // The ChannelID is registered, so get the new endpoint.
                                PushEndpoint = msg.getString("pushEndpoint");
                                String txt = "Registration successful: " +
                                        PushEndpoint;
                                mDisplay.setText(txt + "\n");
                                //toggleConnectToSend(true);
                                // In theory, the WebSocket is no longer required at
                                // this point.
                                break;
                            case "notification":
                                // A notification has arrived via SimplePush.
                                mDisplay.append("\nGot SimplePush notification..." + message + "\n");
                                // TODO: I should ack this message.
                                break;
                            default:
                                // There are a few other messages possible, it's safe to
                                // ignore them.
                                Log.e(TAG, "Unknown message type " + msgType);
                        }
                    }catch (JSONException x) {
                        Log.e(TAG, "Could not parse message " + x);
                    }
                }

                /** A new connection has opened.
                 *
                 * @param ignored is ignored
                 */
                @Override
                public void onOpen(ServerHandshake ignored) {
                    Log.i(TAG, "handshake with: " + getURI());
                    try {
                        // Send a "hello" object
                        JSONObject json = new JSONObject();
                        JSONObject connect = new JSONObject();
                        connect.put("type", "gcm");
                        connect.put("token", regid);
                        json.put("messageType", "hello");
                        // Generate a new UserAgentID. This *should* be unique per device
                        // but since this is a demo app, we can just get a new one each
                        // time the app is restarted. Mind you, older Push Endpoints are
                        // instantly invalid in that case.
                        json.put("uaid", "");
                        // Were this a real app, we'd include the list of registered
                        // ChannelIDs here.
                        json.put("channelIDs", new JSONArray());
                        // "connect" is the proprietary ping content used by the server.
                        json.put("connect", connect);
                        Log.i(TAG, "Sending object: " + json.toString());
                        this.send(json.toString());
                    }catch (JSONException ex) {
                        Log.e(TAG, "JSON Exception: " + ex);
                    }
                }

                /** Connection just died.
                 *
                 * @param code code for closure
                 * @param reason reason for closure
                 * @param remote was triggered by remote
                 */
                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.i(TAG, "Disconnected! " + getURI() + " Code:" + code + " " + reason + "\n");
                    mDisplay.setText("Disconnected from server");
                    //toggleConnectToSend(false);
                }

                /** Error reporting.
                 *
                 * @param ex Exception
                 */
                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "### EXCEPTION: " + ex + "\n");
                }
            };
            ws.connect();
        }catch (URISyntaxException ex) {
            Log.e(TAG, "Bad URL for websocket.");
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
     * @param item Menu item selected
     * @return boolean
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

