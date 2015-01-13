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
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
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
import java.util.concurrent.atomic.AtomicInteger;


public class MainActivity extends ActionBarActivity {

    public static final String EXTRA_MESSAGE = "message";
    public static final String PROPERTY_REG_ID = "registration_id";
    static final String TAG = "SimplepushDemoApp";
    static final String APP = "com.mozilla.simplepush.simplepushdemoapp";
    private static final String PROPERTY_APP_VERSION = "appVersion";
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    String SENDER_ID = "1009375523940";
    String CHANNEL_ID = "abad1dea-0000-0000-0000-000000000000";
    TextView mDisplay;
    EditText host_url;
    EditText ping_data;
    Button send_button;
    Button connect_button;
    GoogleCloudMessaging gcm;
    AtomicInteger msgId = new AtomicInteger();
    Context context;

    String regid;
    String PushEndpoint;

    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo =
                    context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException ex) {
            throw new RuntimeException("Could not get package name: " + ex);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mDisplay = (TextView) findViewById(R.id.display);
        host_url = (EditText) findViewById(R.id.host_edit);
        ping_data = (EditText) findViewById(R.id.message);
        send_button = (Button) findViewById(R.id.send);
        connect_button = (Button) findViewById(R.id.connect);

        context = getApplicationContext();
        if (checkPlayServices()) {
            gcm = GoogleCloudMessaging.getInstance(this);
            regid = getRegistrationId(context);
        } else {
            Log.i(TAG, "No valid Google Play Services APK found");
        }

        host_url.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                                               @Override
                                               public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                                                   boolean handled = false;
                                                   if (actionId == EditorInfo.IME_ACTION_SEND ||
                                                           (actionId == EditorInfo.IME_ACTION_UNSPECIFIED &&
                                                                   event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                                                       handled = true;
                                                   }
                                                   return handled;
                                               }
                                           }
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPlayServices();
    }

    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
                toggleConnectToSend(false);

            } else {
                Log.i(TAG, "This device is not supported");
                finish();
            }
            return false;
        }
        return true;
    }

    private void storeRegistrationId(Context context, String regId) {
        final SharedPreferences prefs = getGcmPreferences(context);
        int appVersion = getAppVersion(context);
        Log.i(TAG, "Saving regId on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString(PROPERTY_REG_ID, regId);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        editor.commit();
    }

    private String getRegistrationId(Context context) {
        final SharedPreferences prefs = getGcmPreferences(context);
        String registrationId = prefs.getString(PROPERTY_REG_ID, "");
        if (registrationId.isEmpty()) {
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
                    sendRegistrationIdToBackend(regid);
                    storeRegistrationId(context, regid);
                } catch (IOException ex) {
                    msg = "Error: registerInBackground: doInBackground: " + ex.getMessage();
                    Log.e(TAG, msg);
                }
                return msg;
            }

            @Override
            protected void onPostExecute(String msg) {
                mDisplay.append(msg + "\n");
            }
        }.execute(null, null, null);
    }

    public void onClick(final View view) {
        if (view == findViewById(R.id.send)) {
            String data = getMessage();
            if (data.length() > 0) {
                SendNotification(data);
            }

/*            new AsyncTask<Void, Void, String>() {
                @Override
            protected String doInBackground(Void... params) {
                    String msg="";
                    try {
                        Bundle data = new Bundle();
                        data.putString("my_message", "Hello World");
                        data.putString("my_action", APP + ".ECHO_NOW");
                        String id = Integer.toString(msgId.incrementAndGet());
                        gcm.send(SENDER_ID + "@gcm.googleapis.com", id, data);
                        msg = "Sent message";
                    } catch (IOException ex) {
                        msg = "Error : onClick: " + ex.getMessage();
                        Log.e(TAG, msg);
                    }
                    return msg;
                }
                @Override
            protected void onPostExecute(String msg) {
                    mDisplay.setText(msg + "\n");
                }
            }.execute(null, null, null);
            */
        } else if (view == findViewById(R.id.clear)) {
            mDisplay.setText("");
        } else if (view == findViewById(R.id.connect)) {
            Log.i(TAG, "## Connection requested");
            registerInBackground();
        }
    }

    private void SendNotification(final String data) {
        if (PushEndpoint.length() == 0) {
            return;
        }
        new AsyncTask<String, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(String... params) {
                HttpPut req = new HttpPut(PushEndpoint);
                HttpParams rparams = new BasicHttpParams();
                rparams.setLongParameter("version", System.currentTimeMillis());
                rparams.setParameter("data", data);
                try {
                    StringEntity entity = new StringEntity(params.toString());
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

    private String getMessage() {
        String target = ping_data.getText().toString();
        if (target.length() > 0) {
            Log.i(TAG, "Ping data set to " + target);
        }
        return target;
    }

    private String getTarget() {
        String target = host_url.getText().toString();
        Log.i(TAG, "Setting target to " + target);
        return target;
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "## Destroying");
        super.onDestroy();
        //TODO: Unregister?
    }

    private SharedPreferences getGcmPreferences(Context context) {
        return getSharedPreferences(MainActivity.class.getSimpleName(), Context.MODE_PRIVATE);
    }

    void toggleConnectToSend(boolean state) {
        host_url.setEnabled(!state);
        connect_button.setEnabled(!state);
        ping_data.setEnabled(state);
        send_button.setEnabled(state);
    }

    private void sendRegistrationIdToBackend(final String regid) {
        String target = getTarget();
        Log.i(TAG, "Sending out Registration message for RegId: " + regid + " to " + target);
        //TODO: Put this in an async task?
        try {
            URI uri = new URI(target);
            WebSocketClient ws = new WebSocketClient(uri, new Draft_17()) {
                private String TAG = "WEBSOCKET";

                @Override
                public void onMessage(String message) {
                    Log.i(TAG, "got message:" + message + "\n");
                    if (message.equals("{}")) {
                        Log.i(TAG, "Pong...");
                        return;
                    }
                    try {
                        JSONObject msg = new JSONObject(new JSONTokener(message));
                        String msgType = msg.getString("messageType");
                        switch (msgType) {
                            case "hello":
                                Log.i(TAG, "Sending registration message");
                                JSONObject regObj = new JSONObject();
                                regObj.put("channelID", CHANNEL_ID);
                                regObj.put("messageType", "register");
                                this.send(regObj.toString());
                                break;
                            case "register":
                                PushEndpoint = msg.getString("pushEndpoint");
                                String txt = "Registration successful: " +
                                        PushEndpoint;
                                mDisplay.setText(txt);
                                //toggleConnectToSend(true);
                                break;
                            default:
                                Log.e(TAG, "Unknown message type " + msgType);
                        }
                    }catch (JSONException x) {
                        Log.e(TAG, "Could not parse message " + x);
                    }
                    // On register, store response, close socket, return to regular broadcasting.
                }

                @Override
                public void onOpen(ServerHandshake handshake) {
                    Log.i(TAG, "handshake with: " + getURI());
                    try {
                        JSONObject json = new JSONObject();
                        JSONObject connect = new JSONObject();
                        connect.put("regid", regid);
                        json.put("messageType", "hello");
                        json.put("uaid", "");
                        json.put("channelIDs", new JSONArray());
                        json.put("connect", connect);
                        Log.i(TAG, "Sending object: " + json.toString());
                        this.send(json.toString());
                    }catch (JSONException ex) {
                        Log.e(TAG, "JSON Exception: " + ex);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.i(TAG, "Disconnected! " + getURI() + " Code:" + code + " " + reason + "\n");
                    mDisplay.setText("Disconnected from server");
                    toggleConnectToSend(false);
                }

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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

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

