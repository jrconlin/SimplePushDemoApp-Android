package com.mozilla.simplepush.simplepushdemoapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.ConnectionResult;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicInteger;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_76;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;


public class MainActivity extends ActionBarActivity {

    String SENDER_ID = "1009375523940";

    static final String TAG = "SimplepushDemoApp";
    static final String APP = "com.mozilla.simplepush.simplepushdemoapp";
    public static final String EXTRA_MESSAGE = "message";
    public static final String PROPERTY_REG_ID = "registration_id";
    private static final String PROPERTY_APP_VERSION = "appVersion";
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    TextView mDisplay;
    EditText editText;
    GoogleCloudMessaging gcm;
    AtomicInteger msgId = new AtomicInteger();
    Context context;

    String regid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mDisplay = (TextView) findViewById(R.id.display);
        editText = (EditText) findViewById(R.id.editText);

        context = getApplicationContext();
        if (checkPlayServices()) {
            gcm = GoogleCloudMessaging.getInstance(this);
            regid = getRegistrationId(context);
            if (regid.isEmpty()) {
                registerInBackground();
            }

        } else {
            Log.i(TAG, "No valid Google Play Services APK found");
        }

        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_SEND ||
                        (actionId == EditorInfo.IME_ACTION_UNSPECIFIED &&
                        event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    registerInBackground();
                    handled = true;
                }
                return handled;
            }
        });
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
                    // TODO: Send Hello with Registration
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
            new AsyncTask<Void, Void, String>() {
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
                    mDisplay.append(msg + "\n");
                }
            }.execute(null, null, null);
        } else if (view == findViewById(R.id.clear)) {
            mDisplay.setText("");
        } else if (view == findViewById(R.id.connect)) {
            Log.i(TAG, "## Connection requested");
            registerInBackground();
        }
    }

    private String getTarget() {
        String target = editText.getText().toString();
        Log.i(TAG, "Setting target to " + target);
        return target;
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "## Destroying");
        super.onDestroy();
        //TODO: Unregister?
    }

    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo =
                    context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException ex) {
            throw new RuntimeException("Could not get package name: " + ex);
        }
    }

    private SharedPreferences getGcmPreferences(Context context) {
        return getSharedPreferences(MainActivity.class.getSimpleName(), Context.MODE_PRIVATE);
    }

    private void sendRegistrationIdToBackend(String regid) {
        //TODO: Send the regId out.
        String target = getTarget();
        Log.i(TAG, "Sending out Registration message for RegId: " + regid + " to " + target);
        try {
            //TODO: FINISH THIS!
            WebSocketClient ws = new WebSocketClient(new URI(target), new Draft_76()) {
                private String TAG = "WEBSOCKET";

                @Override
                public void onMessage(String message) {
                    Log.i(TAG, "message got:" + message + "\n");
                    // On Hello, send register + proping content
                    // On register, store response, close socket, return to regular broadcasting.
                }

                @Override
                public void onOpen(ServerHandshake handshake) {
                    Log.i(TAG, "handshake with: " + getURI());
                    try {
                        JSONObject json = new JSONObject();
                        json.put("messageType", "hello");
                        json.put("uaid", "");
                        json.put("channelIDs", new JSONArray());
                        Log.i(TAG, "Sending object: " + json.toString());
                        // this.send(json.toString());
                    }catch (JSONException ex) {
                        Log.e(TAG, "JSON Exception: " + ex);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.i(TAG, "Disconnected! " + getURI() + " Code:" + code + " " + reason + "\n");
                }

                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "### EXCEPTION: " + ex + "\n");
                }
            };
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

