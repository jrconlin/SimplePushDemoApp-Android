# SimplePush
A barebones Android app to test SimplePush with GCM support

In short, we needed something to test SimplePush with Google Cloud Messaging (GCM). SimplePush allows for "proprietary pings", which is a fancy way of saying "use something other than the SimplePush Websocket interface. Android has GCM built in, so it makes sense to use that when possible.

The nice thing is that if you use SimplePush, you don't have to care about the platform or figuring but who's on what. Stuff should "just work". Granted, in order to actually get things "just working" we need to make sure that things "actually work". There in lies this app.

Since Android apps don't have a DOM and the usual array of webby things, it's a bit trickier, but not tremendously so. This app may be useful in figuring out what needs to be done to use SimplePush with added GCM joy.

## Setup.
Chances are very good that you won't have to do much of this, but if you're rolling your own service, then you'll need to do a few things.

First off, follow the steps about creating your GCM account outlined here:
http://developer.android.com/google/gcm/index.html

Once you have done so, you should have the following:

* *Project Number* - This is the numeric Project ID and is used as your MainActivity.SENDER_ID
* *API KEY* - Alphanumeric string from Developer Console:APIs & Auth:Credentials, this is set in your SimplePush Server config.toml
```
    #Sample config.toml entries
    [propping]
    type = "gcm"
    ttl = "24h"
    collapse_key = "simplepush"
    api_key = "YourApiKeyHere"
```
That should be all you need to worry about for configuration.

## Use.
The demo app does play fast and loose with a few things.

* *It uses a hardcoded ChannelID.* - Not really the best idea if you want to do things for real and may cause unusual results if you've got several different devices you're using.
* *There's no user authorization* - That means that you can't associate feeds to a given user.
* *It doesn't send the SimplePush endpoint anywhere* - Normally, you'd send the endpoint to the App Server. This app doesn't do that since it fills the roll of it's own app server.

To use the app, first specify the websocket for the push server you want to use, then press ```[connect]```. NOTE: for local development servers, you can specify the non-secure websocket URI, for example "```ws://192.168.56.10:8080```".
On a successful registration, the TextView will show the Endpoint URL that would normally go to the App Server. Once that's displayed, you can enter whatever text you like into the "Message to send" and be amazed by the GCM notification you get shortly afterwards.
