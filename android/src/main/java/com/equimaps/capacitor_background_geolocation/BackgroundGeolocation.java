package com.equimaps.capacitor_background_geolocation;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.graphics.Color;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;

import com.getcapacitor.JSObject;
import com.getcapacitor.Logger;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import org.json.JSONObject;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

@CapacitorPlugin(
        name = "BackgroundGeolocation",
        permissions = {
                @Permission(
                        strings = {
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION
                        },
                        alias = "location"
                ),
                @Permission(
                        strings = {
                                Manifest.permission.POST_NOTIFICATIONS,
                        },
                        alias = "notifications"
                )

        }
)
public class BackgroundGeolocation extends Plugin {
    private BackgroundGeolocationService.LocalBinder service = null;
    private Boolean stoppedWithoutPermissions = false;

    private void fetchLastLocation(PluginCall call) {
        try {
            LocationServices.getFusedLocationProviderClient(
                    getContext()
            ).getLastLocation().addOnSuccessListener(
                    getActivity(),
                    new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            if (location != null) {
                                call.resolve(formatLocation(location));
                            }
                        }
                    }
            );
        } catch (SecurityException ignore) {}
    }

    @PluginMethod(returnType = PluginMethod.RETURN_CALLBACK)
    public void addWatcher(final PluginCall call) {
        if (service == null) {
            call.reject("Service not running.");
            return;
        }
        if (getPermissionState("location") != PermissionState.GRANTED && !call.getBoolean("requestPermissions", true)) {
            call.reject("Permission denied.", "NOT_AUTHORIZED");
            return;
        }

        if (getPermissionState("location") != PermissionState.GRANTED && call.getBoolean("requestPermissions", true)) {
            call.setKeepAlive(true);
            requestPermissionForAlias("location", call, "locationPermissionsCallback");
            return;
        }

        if (!isLocationEnabled(getContext())) {
            call.reject("Location services disabled.", "NOT_AUTHORIZED");
            return;
        }

        call.setKeepAlive(true);
        addWatcherAfterLocationPermissionGranted(call);
    }

    private Notification createNotification(PluginCall call) {
        String backgroundMessage = call.getString("backgroundMessage");
        if (backgroundMessage == null) {
            return null;
        }
        Notification.Builder builder = new Notification.Builder(getContext())
                .setContentTitle(
                        call.getString(
                                "backgroundTitle",
                                "Using your location"
                        )
                )
                .setContentText(backgroundMessage)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setWhen(System.currentTimeMillis());

        try {
            String name = getAppString(
                    "capacitor_background_geolocation_notification_icon",
                    "mipmap/ic_launcher"
            );
            String[] parts = name.split("/");
            // It is actually necessary to set a valid icon for the notification to behave
            // correctly when tapped. If there is no icon specified, tapping it will open the
            // app's settings, rather than bringing the application to the foreground.
            builder.setSmallIcon(
                    getAppResourceIdentifier(parts[1], parts[0])
            );
        } catch (Exception e) {
            Logger.error("Could not set notification icon", e);
        }

        try {
            String color = getAppString(
                    "capacitor_background_geolocation_notification_color",
                    null
            );
            if (color != null) {
                builder.setColor(Color.parseColor(color));
            }
        } catch (Exception e) {
            Logger.error("Could not set notification color", e);
        }

        Intent launchIntent = getContext().getPackageManager().getLaunchIntentForPackage(
                getContext().getPackageName()
        );
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            builder.setContentIntent(
                    PendingIntent.getActivity(
                            getContext(),
                            0,
                            launchIntent,
                            PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    )
            );
        }

        // Set the Channel ID for Android O.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(BackgroundGeolocationService.class.getPackage().getName());
        }

        return builder.build();
    }

    private void addWatcherAfterLocationPermissionGranted(PluginCall call) {
        if (call.getBoolean("stale", false)) {
            fetchLastLocation(call);
        }
        if (call.getString("backgroundMessage") != null && getPermissionState("notification") != PermissionState.GRANTED) {
            requestPermissionForAlias("notifications", call, "notificationsPermissionsCallback");
            return;
        }
        service.addWatcher(
            call.getCallbackId(),
            createNotification(call),
            call.getFloat("distanceFilter", 0f)
        );
    }

    @PermissionCallback
    private void locationPermissionsCallback(PluginCall call) {

        if (getPermissionState("location") != PermissionState.GRANTED) {
            call.reject("User denied location permission", "NOT_AUTHORIZED");
            return;
        }
        if (service != null) {
            service.onPermissionsGranted();
            // The handleOnResume method will now be called, and we don't need it to call
            // service.onPermissionsGranted again so we reset this flag.
            stoppedWithoutPermissions = false;
        }
        addWatcherAfterLocationPermissionGranted(call);
    }

    @PermissionCallback
    private void notificationsPermissionsCallback(PluginCall call) {
        service.addWatcher(
                call.getCallbackId(),
                createNotification(call),
                call.getFloat("distanceFilter", 0f)
        );
    }

    @PluginMethod()
    public void removeWatcher(PluginCall call) {
        String callbackId = call.getString("id");
        if (callbackId == null) {
            call.reject("Missing id.");
            return;
        }
        service.removeWatcher(callbackId);
        PluginCall savedCall = getBridge().getSavedCall(callbackId);
        if (savedCall != null) {
            savedCall.release(getBridge());
        }
        call.resolve();
    }

    @PluginMethod()
    public void openSettings(PluginCall call) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getContext().getPackageName(), null);
        intent.setData(uri);
        getContext().startActivity(intent);
        call.resolve();
    }

    // Checks if device-wide location services are disabled
    private static Boolean isLocationEnabled(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            return lm != null && lm.isLocationEnabled();
        } else {
            return (
                    Settings.Secure.getInt(
                            context.getContentResolver(),
                            Settings.Secure.LOCATION_MODE,
                            Settings.Secure.LOCATION_MODE_OFF
                    ) != Settings.Secure.LOCATION_MODE_OFF
            );
        }
    }

    private static JSObject formatLocation(Location location) {
        JSObject obj = new JSObject();
        obj.put("latitude", location.getLatitude());
        obj.put("longitude", location.getLongitude());
        // The docs state that all Location objects have an accuracy, but then why is there a
        // hasAccuracy method? Better safe than sorry.
        obj.put("accuracy", location.hasAccuracy() ? location.getAccuracy() : JSONObject.NULL);
        obj.put("altitude", location.hasAltitude() ? location.getAltitude() : JSONObject.NULL);
        if (Build.VERSION.SDK_INT >= 26 && location.hasVerticalAccuracy()) {
            obj.put("altitudeAccuracy", location.getVerticalAccuracyMeters());
        } else {
            obj.put("altitudeAccuracy", JSONObject.NULL);
        }
        // In addition to mocking locations in development, Android allows the
        // installation of apps which have the power to simulate location
        // readings in other apps.
        obj.put("simulated", location.isFromMockProvider());
        obj.put("speed", location.hasSpeed() ? location.getSpeed() : JSONObject.NULL);
        obj.put("bearing", location.hasBearing() ? location.getBearing() : JSONObject.NULL);
        obj.put("time", location.getTime());
        return obj;
    }

    // Receives messages from the service.
    private class ServiceReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String id = intent.getStringExtra("id");
            PluginCall call = getBridge().getSavedCall(id);
            if (call == null) {
                return;
            }
            Location location = intent.getParcelableExtra("location");
            if (location != null) {
                call.resolve(formatLocation(location));
            } else {
                Logger.debug("No locations received");
            }
        }
    }

    // Gets the identifier of the app's resource by name, returning 0 if not found.
    private int getAppResourceIdentifier(String name, String defType) {
        return getContext().getResources().getIdentifier(
                name,
                defType,
                getContext().getPackageName()
        );
    }

    // Gets a string from the app's strings.xml file, resorting to a fallback if it is not defined.
    private String getAppString(String name, String fallback) {
        int id = getAppResourceIdentifier(name, "string");
        return id == 0 ? fallback : getContext().getString(id);
    }

    @Override
    public void load() {
        super.load();

        // Android O requires a Notification Channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getContext().getSystemService(
                    Context.NOTIFICATION_SERVICE
            );
            NotificationChannel channel = new NotificationChannel(
                    BackgroundGeolocationService.class.getPackage().getName(),
                    getAppString(
                            "capacitor_background_geolocation_notification_channel_name",
                            "Background Tracking"
                    ),
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.enableLights(false);
            channel.enableVibration(false);
            channel.setSound(null, null);
            manager.createNotificationChannel(channel);
        }

        this.getContext().bindService(
                new Intent(this.getContext(), BackgroundGeolocationService.class),
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        BackgroundGeolocation.this.service = (BackgroundGeolocationService.LocalBinder) binder;
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                    }
                },
                Context.BIND_AUTO_CREATE
        );

        LocalBroadcastManager.getInstance(this.getContext()).registerReceiver(
                new ServiceReceiver(),
                new IntentFilter(BackgroundGeolocationService.ACTION_BROADCAST)
        );
    }

    @Override
    protected void handleOnResume() {
        if (service != null) {
            if (stoppedWithoutPermissions && getPermissionState("location") == PermissionState.GRANTED) {
                service.onPermissionsGranted();
            }
        }
        super.handleOnResume();
    }

    @Override
    protected void handleOnPause() {
        stoppedWithoutPermissions = getPermissionState("location") != PermissionState.GRANTED;
        super.handleOnPause();
    }

    @Override
    protected void handleOnDestroy() {
        if (service != null) {
            service.stopService();
        }
        super.handleOnDestroy();
    }
}
