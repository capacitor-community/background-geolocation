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

import org.json.JSONObject;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@CapacitorPlugin(
        name = "BackgroundGeolocation",
        permissions = {
                @Permission(
                        strings = {
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION
                        },
                        alias = "location"
                )
        }
)
public class BackgroundGeolocation extends Plugin {
    private Boolean stoppedWithoutPermissions = false;

    private CompletableFuture<BackgroundGeolocationService.LocalBinder> serviceConnectionFuture;
    private final ConcurrentHashMap<String, CompletableFuture<Void>> permissionFutures = new ConcurrentHashMap<>();

    private void fetchLastLocation(PluginCall call) {
        try {
            LocationServices.getFusedLocationProviderClient(
                    getContext()
            ).getLastLocation().addOnSuccessListener(
                    getActivity(),
                    location -> {
                        if (location != null) {
                            call.resolve(formatLocation(location));
                        }
                    }
            );
        } catch (SecurityException ignore) {}
    }

    @PluginMethod(returnType = PluginMethod.RETURN_CALLBACK)
    public void addWatcher(final PluginCall call) {

        if (getPermissionState("location") != PermissionState.GRANTED && !call.getBoolean("requestPermissions", true)) {
            call.reject("User denied location permission", "NOT_AUTHORIZED");
            return;
        }

        if (getPermissionState("location") != PermissionState.GRANTED && call.getBoolean("requestPermissions", true)) {
            call.setKeepAlive(true);
            requestLocationPermissions(call).thenRun(() -> {
                proceedWithWatcher(call);
            }).exceptionally(throwable -> {
                call.reject("User denied location permission", "NOT_AUTHORIZED");
                return null;
            });
            return;
        }

        // location permission granted.
        if (!isLocationEnabled(getContext())) {
            call.reject("Location services disabled.", "NOT_AUTHORIZED");
            return;
        }

        // Everything is OK, continuing to adding a watcher
        call.setKeepAlive(true);
        proceedWithWatcher(call);
    }

    private void proceedWithWatcher(PluginCall call) {
        if (call.getBoolean("stale", false)) {
            fetchLastLocation(call);
        }
        getServiceConnection().thenAccept(serviceBinder -> {
            serviceBinder.addWatcher(
                    call.getCallbackId(),
                    createBackgroundNotification(call),
                    call.getFloat("distanceFilter", 0f)
            );
        });
    }

    private CompletableFuture<BackgroundGeolocationService.LocalBinder> getServiceConnection() {
        if (serviceConnectionFuture != null && !serviceConnectionFuture.isCompletedExceptionally()) {
            return serviceConnectionFuture;
        }

        serviceConnectionFuture = new CompletableFuture<>();

        Intent serviceIntent = new Intent(this.getContext(), BackgroundGeolocationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.getContext().startForegroundService(serviceIntent);
        } else {
            this.getContext().startService(serviceIntent);
        }

        this.getContext().bindService(
                serviceIntent,
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        serviceConnectionFuture.complete((BackgroundGeolocationService.LocalBinder) binder);
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        serviceConnectionFuture = null;
                    }
                },
                Context.BIND_AUTO_CREATE
        );

        return serviceConnectionFuture;
    }

    private CompletableFuture<Void> requestLocationPermissions(PluginCall call) {
        String futureKey = call.getCallbackId();
        CompletableFuture<Void> future = new CompletableFuture<>();
        permissionFutures.put(futureKey, future);

        requestPermissionForAlias("location", call, "locationPermissionsCallback");

        return future;
    }

    @PermissionCallback
    private void locationPermissionsCallback(PluginCall call) {
        String futureKey = call.getCallbackId();
        CompletableFuture<Void> future = permissionFutures.remove(futureKey);
        if (future == null) {
            return;
        }

        if (getPermissionState("location") != PermissionState.GRANTED) {
            future.completeExceptionally(new SecurityException("User denied location permission"));
            return;
        }

        getServiceConnection().thenAccept(serviceBinder -> {
            serviceBinder.onPermissionsGranted();
            // The handleOnResume method will now be called, and we don't need it to call
            // service.onPermissionsGranted again so we reset this flag.
            stoppedWithoutPermissions = false;
        });

        future.complete(null);
    }

    private Notification createBackgroundNotification(PluginCall call) {
        String backgroundMessage = call.getString("backgroundMessage", "");

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

    @PluginMethod()
    public void removeWatcher(PluginCall call) {
        String callbackId = call.getString("id");
        if (callbackId == null) {
            call.reject("Missing id.");
            return;
        }

        getServiceConnection().thenAccept(serviceBinder -> {
            serviceBinder.removeWatcher(callbackId);
            PluginCall savedCall = getBridge().getSavedCall(callbackId);
            if (savedCall != null) {
                savedCall.release(getBridge());
            }
            call.resolve();
        }).exceptionally(throwable -> {
            call.reject("Service connection failed: " + throwable.getMessage());
            return null;
        });
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

        LocalBroadcastManager.getInstance(this.getContext()).registerReceiver(
                new ServiceReceiver(),
                new IntentFilter(BackgroundGeolocationService.ACTION_BROADCAST)
        );
    }

    @Override
    protected void handleOnResume() {
        if (serviceConnectionFuture != null &&
            stoppedWithoutPermissions && getPermissionState("location") == PermissionState.GRANTED) {
            serviceConnectionFuture.thenAccept(BackgroundGeolocationService.LocalBinder::onPermissionsGranted);
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
        if (serviceConnectionFuture != null) {
            serviceConnectionFuture.thenAccept(BackgroundGeolocationService.LocalBinder::stopService);
        }

        permissionFutures.values().forEach(future -> {
            if (!future.isDone()) {
                future.cancel(true);
            }
        });
        permissionFutures.clear();

        super.handleOnDestroy();
    }
}
