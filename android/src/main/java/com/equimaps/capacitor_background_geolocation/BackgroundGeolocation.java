package com.equimaps.capacitor_background_geolocation;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;

import com.getcapacitor.android.BuildConfig;
import com.getcapacitor.JSObject;
import com.getcapacitor.Logger;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import org.json.JSONObject;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

@NativePlugin(
        permissions={
                Manifest.permission.ACCESS_FINE_LOCATION
        },
        // A random integer which is hopefully unique to this plugin.
        permissionRequestCode = 28351
)
public class BackgroundGeolocation extends Plugin {
    private PluginCall callPendingPermissions = null;

    @PluginMethod(returnType=PluginMethod.RETURN_CALLBACK)
    public void addWatcher(final PluginCall call) {
        if (service == null) {
            call.error("Service not running.");
            return;
        }
        call.save();
        if (!hasRequiredPermissions()) {
            if (call.getBoolean("requestPermissions", true)) {
                callPendingPermissions = call;
                pluginRequestAllPermissions();
                return;
            } else {
                call.reject("Permission denied.", "NOT_AUTHORIZED");
            }
        }
        callPendingPermissions = null;
        if (call.getBoolean("stale", false)) {
            LocationServices.getFusedLocationProviderClient(
                    getContext()
            ).getLastLocation().addOnSuccessListener(
                    getActivity(),
                    new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            if (location != null) {
                                call.success(formatLocation(location));
                            }
                        }
                    }
            );
        }
        Notification backgroundNotification = null;
        String backgroundMessage = call.getString("backgroundMessage");
        if (backgroundMessage != null) {
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
                builder.setSmallIcon(
                        getContext().getPackageManager().getApplicationInfo(
                                getContext().getPackageName(),
                                PackageManager.GET_META_DATA
                        ).icon
                );
            } catch (Exception e) {
                Logger.error("Could not set notification icon", e);
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
                                PendingIntent.FLAG_CANCEL_CURRENT
                        )
                );
            }

            // Set the Channel ID for Android O.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setChannelId(BackgroundGeolocationService.class.getPackage().getName());
            }

            backgroundNotification = builder.build();
        }
        service.addWatcher(
                call.getCallbackId(),
                backgroundNotification
        );
    }

    @Override
    protected void handleRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.handleRequestPermissionsResult(requestCode, permissions, grantResults);

        if (callPendingPermissions == null) {
            return;
        }

        for(int result : grantResults) {
            if (result == PackageManager.PERMISSION_DENIED) {
                callPendingPermissions.reject("User denied location permission", "NOT_AUTHORIZED");
                callPendingPermissions.release(bridge);
                callPendingPermissions = null;
                return;
            }
        }

        addWatcher(callPendingPermissions);
    }

    @PluginMethod()
    public void removeWatcher(PluginCall call) {
        String callbackId = call.getString("id");
        if (callbackId == null) {
            call.error("Missing id.");
            return;
        }
        service.removeWatcher(callbackId);
        PluginCall savedCall = bridge.getSavedCall(callbackId);
        if (savedCall != null) {
            savedCall.release(bridge);
        }
        call.success();
    }

    @PluginMethod()
    public void openSettings(PluginCall call) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getContext().getPackageName(), null);
        intent.setData(uri);
        getContext().startActivity(intent);
        call.success();
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
        obj.put("speed", location.hasSpeed() ? location.getSpeed() : JSONObject.NULL);
        obj.put("bearing", location.hasBearing() ? location.getBearing() : JSONObject.NULL);
        obj.put("time", location.getTime());
        return obj;
    }

    // Sends messages to the service.
    private BackgroundGeolocationService.LocalBinder service = null;

    // Receives messages from the service.
    private class ServiceReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String id = intent.getStringExtra("id");
            PluginCall call = bridge.getSavedCall(id);
            if (call == null) {
                return;
            }
            Location location = intent.getParcelableExtra("location");
            if (location == null) {
                if (BuildConfig.DEBUG) {
                    call.error("No locations received");
                }
                return;
            }
            call.success(formatLocation(location));
        }
    }

    @Override
    public void load() {
        super.load();

        // Android O requires a Notification Channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getContext().getSystemService(
                    Context.NOTIFICATION_SERVICE
            );
            manager.createNotificationChannel(
                    new NotificationChannel(
                            BackgroundGeolocationService.class.getPackage().getName(),
                            "Location Updates",
                            NotificationManager.IMPORTANCE_DEFAULT
                    )
            );
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
    protected void handleOnStart() {
        if (service != null) {
            service.onActivityStarted();
        }
        super.handleOnStart();
    }

    @Override
    protected void handleOnStop() {
        if (service != null) {
            service.onActivityStopped();
        }
        super.handleOnStop();
    }

    @Override
    protected void handleOnDestroy() {
        if (service != null) {
            service.stopService();
        }
        super.handleOnDestroy();
    }
}
