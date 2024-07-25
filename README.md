# Background Geolocation
A Capacitor plugin that lets you receive geolocation updates even while the app is backgrounded. Only iOS and Android platforms are supported.

## Usage

```javascript
import {registerPlugin} from "@capacitor/core";
const BackgroundGeolocation = registerPlugin("BackgroundGeolocation");

// To start listening for changes in the device's location, add a new watcher.
// You do this by calling 'addWatcher' with an options object and a callback. A
// Promise is returned, which resolves to the callback ID used to remove the
// watcher in the future. The callback will be called every time a new location
// is available. Watchers can not be paused, only removed. Multiple watchers may
// exist simultaneously.
BackgroundGeolocation.addWatcher(
    {
        // If the "backgroundMessage" option is defined, the watcher will
        // provide location updates whether the app is in the background or the
        // foreground. If it is not defined, location updates are only
        // guaranteed in the foreground. This is true on both platforms.

        // On Android, a notification must be shown to continue receiving
        // location updates in the background. This option specifies the text of
        // that notification.
        backgroundMessage: "Cancel to prevent battery drain.",

        // The title of the notification mentioned above. Defaults to "Using
        // your location".
        backgroundTitle: "Tracking You.",

        // Whether permissions should be requested from the user automatically,
        // if they are not already granted. Defaults to "true".
        requestPermissions: true,

        // If "true", stale locations may be delivered while the device
        // obtains a GPS fix. You are responsible for checking the "time"
        // property. If "false", locations are guaranteed to be up to date.
        // Defaults to "false".
        stale: false,

        // The minimum number of metres between subsequent locations. Defaults
        // to 0.
        distanceFilter: 50
    },
    function callback(location, error) {
        if (error) {
            if (error.code === "NOT_AUTHORIZED") {
                if (window.confirm(
                    "This app needs your location, " +
                    "but does not have permission.\n\n" +
                    "Open settings now?"
                )) {
                    // It can be useful to direct the user to their device's
                    // settings when location permissions have been denied. The
                    // plugin provides the 'openSettings' method to do exactly
                    // this.
                    BackgroundGeolocation.openSettings();
                }
            }
            return console.error(error);
        }

        return console.log(location);
    }
).then(function after_the_watcher_has_been_added(watcher_id) {
    // When a watcher is no longer needed, it should be removed by calling
    // 'removeWatcher' with an object containing its ID.
    BackgroundGeolocation.removeWatcher({
        id: watcher_id
    });
});

// The location object.
{
    // Longitude in degrees.
    longitude: 131.723423719132,
    // Latitude in degrees.
    latitude: -22.40106297456,
    // Radius of horizontal uncertainty in metres, with 68% confidence.
    accuracy: 11,
    // Metres above sea level (or null).
    altitude: 65,
    // Vertical uncertainty in metres, with 68% confidence (or null).
    altitudeAccuracy: 4,
    // Deviation from true north in degrees (or null).
    bearing: 159.60000610351562,
    // True if the location was simulated by software, rather than GPS.
    simulated: false,
    // Speed in metres per second (or null).
    speed: 23.51068878173828,
    // Time the location was produced, in milliseconds since the unix epoch.
    time: 1562731602000
}

// If you just want the current location, try something like this. The longer
// the timeout, the more accurate the guess will be. I wouldn't go below about
// 100ms.
function guess_location(callback, timeout) {
    let last_location;
    BackgroundGeolocation.addWatcher(
        {
            requestPermissions: false,
            stale: true
        },
        function (location) {
            last_location = location || undefined;
        }
    ).then(function (id) {
        setTimeout(function () {
            callback(last_location);
            Plugins.BackgroundGeolocation.removeWatcher({id});
        }, timeout);
    });
}
```

### Typescript support

```typescript
import {BackgroundGeolocationPlugin} from "@capacitor-community/background-geolocation";
const BackgroundGeolocation = registerPlugin<BackgroundGeolocationPlugin>("BackgroundGeolocation");
```

## Installation

Different versions of the plugin support different versions of Capacitor:

| Capacitor  | Plugin |
|------------|--------|
| v2         | v0.3   |
| v3         | v1     |
| v4         | v1     |
| v5         | v1     |
| v6         | v1     |

Read the documentation for v0.3 [here](https://github.com/capacitor-community/background-geolocation/tree/0.3.x).

```sh
npm install @capacitor-community/background-geolocation
npx cap update
```

### iOS
Add the following keys to `Info.plist.`:

```xml
<dict>
  ...
  <key>NSLocationWhenInUseUsageDescription</key>
  <string>We need to track your location</string>
  <key>NSLocationAlwaysAndWhenInUseUsageDescription</key>
  <string>We need to track your location while your device is locked.</string>
  <key>UIBackgroundModes</key>
  <array>
    <string>location</string>
  </array>
  ...
</dict>
```

### Android

Set the the `android.useLegacyBridge` option to `true` in your Capacitor configuration. This prevents location updates halting after 5 minutes in the background. See https://capacitorjs.com/docs/config and https://github.com/capacitor-community/background-geolocation/issues/89.

On Android 13+, the app needs the `POST_NOTIFICATIONS` runtime permission to show the persistent notification informing the user that their location is being used in the background. You may need to [request this permission](https://developer.android.com/develop/ui/views/notifications/notification-permission) from the user, this can be accomplished [using the `@capacitor/local-notifications` plugin](https://capacitorjs.com/docs/apis/local-notifications#checkpermissions).

If your app forwards location updates to a server in real time, be aware that after 5 minutes in the background Android will throttle HTTP requests initiated from the WebView. The solution is to use a native HTTP plugin such as [CapacitorHttp](https://capacitorjs.com/docs/apis/http). See https://github.com/capacitor-community/background-geolocation/issues/14.

Configration specific to Android can be made in `strings.xml`:
```xml
<resources>
    <!--
        The channel name for the background notification. This will be visible
        when the user presses & holds the notification. It defaults to
        "Background Tracking".
    -->
    <string name="capacitor_background_geolocation_notification_channel_name">
        Background Tracking
    </string>

    <!--
        The icon to use for the background notification. Note the absence of a
        leading "@". It defaults to "mipmap/ic_launcher", the app's launch icon.

        If a raster image is used to generate the icon (as opposed to a vector
        image), it must have a transparent background. To make sure your image
        is compatible, select "Notification Icons" as the Icon Type when
        creating the image asset in Android Studio.
    -->
    <string name="capacitor_background_geolocation_notification_icon">
        drawable/ic_tracking
    </string>
</resources>

```

## Changelog

### v1.2.18
- Always show the notification when a background watcher exists, improving the reliability of location updates on Android.

### v1.2.17
- Adds support for Capacitor v6.

### v1.2.16
- Fixes background location updates for Android 14.

### v1.2.14
- Adds support for Capacitor v5.

### v1.2.3
- Adds support for Capacitor v4.

### v1.2.2
- Prevents location updates from halting on iOS due to extended inactivity.

### v1.2.1
- Fixes background location updates for some devices running Android 12.

### v1.2.0
- On iOS, the status bar now turns blue whilst the location is being watched in the background. This provides the user a straightforward way to return to the app.

### v1.0.4
- Adds the `ACCESS_COARSE_LOCATION` permission. This is required for apps that target Android 12 (API level 31). A preceeding example shows how to add this permission to your app's manifest.

### v1.0.0
- BREAKING: `addWatcher` now returns a Promise that resolves to the callback ID, rather than the callback ID itself.
- BREAKING: The plugin is imported via Capacitor's `registerPlugin` function, rather than from the `Plugins` object.
- BREAKING: Drops support for iOS v11 and Capacitor v2.
- Adds support for Capacitor v3.
