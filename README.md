# capacitor-background-geolocation
Capacitor plugin which lets you receive geolocation updates even while the app is backgrounded.
Tested with Capacitor v2.

## Installation
```sh
npm install capacitor-background-geolocation
npx cap update
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

Configure permissions in `AndroidManifest.xml`:
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
