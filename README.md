# capacitor-background-geolocation
Capacitor plugin which lets you receive geolocation updates even while the app is backgrounded.
Tested with Capacitor v2. iOS and Android platforms only.

## Usage

On Android, a notification must be shown while the app is backgrounded, and so necessitates the `backgroundMessage` option. If this is not provided, location updates may cease when the app moves to the background.

```javascript
import {Plugins} from "@capacitor/core";
const {BackgroundGeolocation, Modals} = Plugins;

const id = BackgroundGeolocation.addWatcher(
    {
        backgroundTitle: "Tracking You.",
        backgroundMessage: "Cancel to prevent battery drain."
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
```

## Installation
```sh
npm install @diachedelic/capacitor-background-geolocation
npx cap update
```

### iOS
Specify the following keys in `Info.plist.`:

```xml
<dict>
  ...
  <key>NSLocationWhenInUseUsageDescription</key>
  <string>We need to track your location</string>
  <key>NSLocationAlwaysAndWhenInUseUsageDescription</key>
  <string>We need to track your location while your device is locked.</string>
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
