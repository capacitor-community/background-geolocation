# capacitor-background-location
Capacitor plugin which lets you receive geolocation updates even while the app is backgrounded.
Tested with Capacitor v2.

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
```

## Installation
```sh
npm install capacitor-background-location
npx cap update
```

### iOS
Specify both the `NSLocationAlwaysUsageDescription` and `NSLocationAlwaysAndWhenInUseUsageDescription` in `Info.plist.`.

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
