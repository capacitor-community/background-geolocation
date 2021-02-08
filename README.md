# Background Geolocation
Capacitor plugin which lets you receive geolocation updates even while the app is backgrounded.
Tested with Capacitor v2. iOS and Android platforms only.

## Usage

```javascript
import {Plugins} from "@capacitor/core";
const {BackgroundGeolocation, Modals} = Plugins;

const id = BackgroundGeolocation.addWatcher(
    {
        // On Android, the plugin shows a notification which allows it to
        // continue receiving location updates in the background. If this option
        // is undefined, the notification is not delivered and hence background
        // location updates are not guaranteed.
        backgroundMessage: "Cancel to prevent battery drain.",

        // The title for the notification. Defaults to "Using your location".
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
                Modals.confirm({
                    title: "Location Required",
                    message: (
                        "This app needs your location, " +
                        "but does not have permission.\n\n" +
                        "Open settings now?"
                    )
                }).then(function ({value}) {
                    if (value) {
                        BackgroundGeolocation.openSettings();
                    }
                });
            }
            return console.error(error);
        }

        return console.log(location);
    }
);

// Some time later.
BackgroundGeolocation.removeWatcher({id});

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
    let id = Plugins.BackgroundGeolocation.addWatcher(
        {
            requestPermissions: false,
            stale: true
        },
        function (location) {
            last_location = location || undefined;
        }
    );

    setTimeout(function () {
        callback(last_location);
        Plugins.BackgroundGeolocation.removeWatcher({id});
    }, timeout);
}
```

### Typescript support

```typescript
// Capacitor 2.x
import {
    BackgroundGeolocationPlugin
} from "@capacitor-community/background-geolocation";
const BackgroundGeolocation = Plugins.BackgroundGeolocation as BackgroundGeolocationPlugin;
```

## Installation
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
Import the plugin in `MainActivity.java`:

```java
import com.equimaps.capacitor_background_geolocation.BackgroundGeolocation;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Initializes the Bridge
    this.init(savedInstanceState, new ArrayList<Class<? extends Plugin>>() {{
      // Additional plugins you've installed go here
      // Ex: add(TotallyAwesomePlugin.class);
      add(BackgroundGeolocation.class);
    }});
  }
}
```

Configure `AndroidManifest.xml`:
```xml
<manifest>
    <application>
        <service
            android:name="com.equimaps.capacitor_background_geolocation.BackgroundGeolocationService"
            android:enabled="true"
            android:exported="true"
            android:foregroundServiceType="location" />
    </application>

    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-feature android:name="android.hardware.location.gps" />
</manifest>
```
