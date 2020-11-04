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
import com.equimaps.capacitorbackgroundgeolocation.BackgroundGeolocation;

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

