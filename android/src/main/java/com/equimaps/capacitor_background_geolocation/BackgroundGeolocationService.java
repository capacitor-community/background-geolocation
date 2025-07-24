package com.equimaps.capacitor_background_geolocation;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.IBinder;

import com.getcapacitor.Logger;
import java.util.HashSet;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

// A bound and started service that is promoted to a foreground service
// (showing a persistent notification) when the first background watcher is
// added, and demoted when the last background watcher is removed.
public class BackgroundGeolocationService extends Service {
    static final String ACTION_BROADCAST = (
            BackgroundGeolocationService.class.getPackage().getName() + ".broadcast"
    );
    private final IBinder binder = new LocalBinder();

    // Must be unique for this application.
    private static final int NOTIFICATION_ID = 28351;

    private static class Watcher {
        public String id;
        public LocationManager client;
        public float distanceFilter;
        public LocationListener locationCallback;
        public Notification backgroundNotification;
    }
    private HashSet<Watcher> watchers = new HashSet<>();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // Some devices allow a foreground service to outlive the application's main
    // activity, leading to nasty crashes as reported in issue #59. If we learn
    // that the application has been killed, all watchers are stopped and the
    // service is terminated immediately.
    @Override
    public boolean onUnbind(Intent intent) {
        for (Watcher watcher : watchers) {
            watcher.client.removeUpdates(watcher.locationCallback);
        }
        watchers = new HashSet<Watcher>();
        stopSelf();
        return false;
    }

    private void requestLocationUpdates(Watcher watcher) {
        try {
            watcher.client.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000,
                    watcher.distanceFilter,
                    watcher.locationCallback
            );
        } catch (SecurityException ignore) { 
            // According to Android Studio, this method can throw a Security Exception if
            // permissions are not yet granted. Rather than check the permissions, which is fiddly,
            // we simply ignore the exception.
        }
    }

    Notification getNotification() {
        for (Watcher watcher : watchers) {
            if (watcher.backgroundNotification != null) {
                return watcher.backgroundNotification;
            }
        }
        return null;
    }

    // Handles requests from the activity.
    public class LocalBinder extends Binder {
        void addWatcher(
                final String id,
                Notification backgroundNotification,
                float distanceFilter
        ) {
            LocationManager locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);

            LocationListener listener = location -> {
                Intent intent = new Intent(ACTION_BROADCAST);
                intent.putExtra("location", location);
                intent.putExtra("id", id);
                LocalBroadcastManager.getInstance(
                        getApplicationContext()
                ).sendBroadcast(intent);
            };

            Watcher watcher = new Watcher();
            watcher.id = id;
            watcher.client = locationManager;
            watcher.distanceFilter = distanceFilter;
            watcher.locationCallback = listener;
            watcher.backgroundNotification = backgroundNotification;
            watchers.add(watcher);

            requestLocationUpdates(watcher);

            // Promote the service to the foreground if necessary.
            // Ideally we would only call 'startForeground' if the service is not already
            // foregrounded. Unfortunately, 'getForegroundServiceType' was only introduced
            // in API level 29 and seems to behave weirdly, as reported in #120. However,
            // it appears that 'startForeground' is idempotent, so we just call it repeatedly
            // each time a background watcher is added.
            if (backgroundNotification != null) {
                try {
                    // This method has been known to fail due to weird
                    // permission bugs, so we prevent any exceptions from
                    // crashing the app. See issue #86.
                    startForeground(NOTIFICATION_ID, backgroundNotification);
                } catch (Exception exception) {
                    Logger.error("Failed to foreground service", exception);
                }
            }
        }

        void removeWatcher(String id) {
            for (Watcher watcher : watchers) {
                if (watcher.id.equals(id)) {
                    watcher.client.removeUpdates(watcher.locationCallback);
                    watchers.remove(watcher);
                    if (getNotification() == null) {
                        stopForeground(true);
                    }
                    return;
                }
            }
        }

        void onPermissionsGranted() {
            // If permissions were granted while the app was in the background, for example in
            // the Settings app, the watchers need restarting.
            for (Watcher watcher : watchers) {
                watcher.client.removeUpdates(watcher.locationCallback);
                requestLocationUpdates(watcher);
            }
        }

        void stopService() {
            BackgroundGeolocationService.this.stopSelf();
        }
    }
}
