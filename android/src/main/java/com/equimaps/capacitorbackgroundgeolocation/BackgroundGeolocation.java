package com.equimaps.capacitorbackgroundgeolocation;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;

import com.getcapacitor.JSObject;
import com.getcapacitor.Logger;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.util.HashSet;

class Watcher {
  public String callbackId;
  public FusedLocationProviderClient client;
  public LocationRequest locationRequest;
  public LocationCallback locationCallback;
}

@NativePlugin(
    permissions={
      Manifest.permission.ACCESS_FINE_LOCATION
    },
    // A random integer which is hopefully unique to this plugin.
    permissionRequestCode = 28351
)
public class BackgroundGeolocation extends Plugin {
  private HashSet<Watcher> watchers = new HashSet<Watcher>();

  @PluginMethod(returnType=PluginMethod.RETURN_CALLBACK)
  public void addWatcher(final PluginCall call) {
    if (!hasRequiredPermissions()) {
      saveCall(call);
      pluginRequestAllPermissions();
      return;
    }

    String callbackId = call.getCallbackId();
    call.save();
    FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(getContext());
    LocationRequest locationRequest = new LocationRequest();
    locationRequest.setMaxWaitTime(1000);
    locationRequest.setInterval(1000);
    locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

    LocationCallback callback = new LocationCallback(){
      @Override
      public void onLocationResult(LocationResult locationResult) {
        Location location = locationResult.getLastLocation();
        if (location == null) {
          if (BuildConfig.DEBUG) {
            call.error("No locations received");
          }
        } else {
          JSObject obj = new JSObject();
          obj.put("latitude", location.getLatitude());
          obj.put("longitude", location.getLongitude());
          // The docs state that all Location objects have an accuracy, but then why is there a
          // hasAccuracy method? Better safe than sorry.
          obj.put("accuracy", location.hasAccuracy() ? location.getAccuracy() : null);
          obj.put("altitude", location.hasAltitude() ? location.getAltitude() : null);
          if (Build.VERSION.SDK_INT >= 26) {
            obj.put("altitudeAccuracy",
                    location.hasVerticalAccuracy()
                            ? location.getVerticalAccuracyMeters()
                            : null
            );
          }
          obj.put("speed", location.hasSpeed() ? location.getSpeed() : null);
          obj.put("bearing", location.hasBearing() ? location.getBearing() : null);
          obj.put("time", location.getTime());
          call.success(obj);
        }
      }
       @Override
       public void onLocationAvailability(LocationAvailability availability) {
         if (BuildConfig.DEBUG && !availability.isLocationAvailable()) {
           Logger.debug("Location not available");
         }
       }
    };

    Watcher watcher = new Watcher();
    watcher.callbackId = callbackId;
    watcher.client = client;
    watcher.locationRequest = locationRequest;
    watcher.locationCallback = callback;
    watchers.add(watcher);

    startWatcher(watcher);
  }

  @Override
  protected void handleRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.handleRequestPermissionsResult(requestCode, permissions, grantResults);

    PluginCall call = getSavedCall();
    if (call == null) {
      return;
    }

    for(int result : grantResults) {
      if (result == PackageManager.PERMISSION_DENIED) {
        call.reject("User denied location permission", "NOT_AUTHORIZED");
        return;
      }
    }

    addWatcher(call);
  }

  @PluginMethod()
  public void removeWatcher(PluginCall call) {
    String callbackId = call.getString("id");
    if (callbackId == null) {
      call.error("Missing id.");
      return;
    }
    removeWatcher(getWatcher(callbackId));
    call.success();
  }

  @PluginMethod()
  public void resume(PluginCall call) {
    String callbackId = call.getString("id");
    if (callbackId == null) {
      call.error("Missing id.");
      return;
    }
    Watcher watcher = getWatcher(callbackId);
    if (watcher == null) {
      call.error("No watcher found.");
    } else {
      startWatcher(watcher);
      call.success();
    }
  }

  @PluginMethod()
  public void pause(PluginCall call) {
    String callbackId = call.getString("id");
    if (callbackId == null) {
      call.error("Missing id.");
      return;
    }
    Watcher watcher = getWatcher(callbackId);
    if (watcher == null) {
      call.error("No watcher found.");
    } else {
      stopWatcher(watcher);
      call.success();
    }
  }

  private Watcher getWatcher(String callbackId) {
    for (Watcher watcher : watchers) {
      if (watcher.callbackId.equals(callbackId)) {
        return watcher;
      }
    }
    return null;
  }

  private void startWatcher(Watcher watcher) {
    watcher.client.requestLocationUpdates(
            watcher.locationRequest,
            watcher.locationCallback,
            null
    );
  }

  private void stopWatcher(Watcher watcher) {
    bridge.getSavedCall(watcher.callbackId).release(bridge);
    watcher.client.removeLocationUpdates(watcher.locationCallback);
  }

  private void removeWatcher(Watcher watcher) {
    if (watcher != null) {
      stopWatcher(watcher);
      watchers.remove(watcher);
    }
  }
}
