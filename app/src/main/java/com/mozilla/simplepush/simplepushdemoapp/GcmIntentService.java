/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.mozilla.simplepush.simplepushdemoapp;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

/**
 * Created by jconlin on 1/7/2015.
 */

/**
 * Intent handler
 * <p/>
 * This deals with the incoming GCM notifications.
 */
public class GcmIntentService extends IntentService {
    public static final int NOTIFICATION_ID = 1;
    public static final String TAG = "SimplepushDemo-Intent";
    private NotificationManager mNotificationManager;
    public GcmIntentService() {
        super("GcmIntentService");
    }

    /** Handle the new event.
     *
     * @param intent
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        // getExtras contains the data from the remote server.
        Bundle extras = intent.getExtras();
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);
        String messageType = gcm.getMessageType(intent);
        if (!extras.isEmpty()) {
            String msg;
            if (GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR.equals(messageType)) {
                msg = "Send error: " + extras.toString();
                Log.e(TAG, msg);
                displayNotification(extras);
            } else if (GoogleCloudMessaging.MESSAGE_TYPE_DELETED.equals(messageType)) {
                msg = "Deleted messages on server: " + extras.toString();
                Log.e(TAG, msg);
                displayNotification(extras);
            } else if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
                displayNotification(extras);
                msg = "Recv'd" + extras.toString();
                Log.i(TAG, msg);
            }
        }
        GcmBroadcastReceiver.completeWakefulIntent(intent);
    }

    /** Display the notification content via the Notification bar
     *
     * @param bundle
     */
    private void displayNotification(Bundle bundle) {
        Log.d(TAG, "Got GCM notification: " + bundle.toString());
        // Use short identifiers from server.
        String msg = bundle.getString("Msg");
        String ver = bundle.getString("Ver");
        msg = msg.concat(" : " + ver);
        // Currently this displays the notification. One can easily presume that this is not
        // required and that your app could do more interesting things internally.
        mNotificationManager = (NotificationManager)
                this.getSystemService(Context.NOTIFICATION_SERVICE);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_stat_gcm)
                .setContentTitle("SimplePush Demo Notification")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(msg))
                .setContentText(msg);
        mBuilder.setContentIntent(contentIntent);
        mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }
}
