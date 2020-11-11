import Capacitor
import Foundation
import UIKit
import CoreLocation

// Avoids a bewildering type warning.
let null = Optional<Double>.none as Any

func formatLocation(_ location: CLLocation) -> PluginResultData {
    return [
        "latitude": location.coordinate.latitude,
        "longitude": location.coordinate.longitude,
        "accuracy": location.horizontalAccuracy,
        "altitude": location.altitude,
        "altitudeAccuracy": location.verticalAccuracy,
        "speed": location.speed < 0 ? null : location.speed,
        "bearing": location.course < 0 ? null : location.course,
        "time": NSNumber(
            value: Int(
                location.timestamp.timeIntervalSince1970 * 1000
            )
        ),
    ];
}

@objc(BackgroundGeolocation)
public class BackgroundGeolocation : CAPPlugin, CLLocationManagerDelegate {
    let lastKnownLocationKey = "background_geolocation_last_known_location";
    var locationManagers = [String:CLLocationManager]()

    @objc public override func load() {
        UIDevice.current.isBatteryMonitoringEnabled = true;
    }

    @objc func addWatcher(_ call: CAPPluginCall) {
        call.save()
        
        // CLLocationManager requires main thread
        DispatchQueue.main.async {
            let locationManager = CLLocationManager()
            self.locationManagers[call.callbackId] = locationManager
            locationManager.delegate = self
            locationManager.desiredAccuracy = [
                .full,
                .charging
            ].contains(UIDevice.current.batteryState)
            ? kCLLocationAccuracyBestForNavigation
            : kCLLocationAccuracyBest;
            locationManager.allowsBackgroundLocationUpdates =
                call.getString("backgroundMessage") != nil

            if self.requestPermissions(locationManager) {
                locationManager.startUpdatingLocation()
            }
        }
    }

    @objc func removeWatcher(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            if let callbackId = call.getString("id") {
                if let savedCall = self.bridge.getSavedCall(callbackId) {
                    self.bridge.releaseCall(savedCall)
                }
                if let locationManager = self.locationManagers[callbackId] {
                    locationManager.stopUpdatingLocation()
                    self.locationManagers.removeValue(forKey: callbackId)
                }
            } else {
                return call.error("No callback ID")
            }
            return call.success()
        }
    }

    @objc func guess(_ call: CAPPluginCall) {
        if let data = UserDefaults.standard.data(
            forKey: lastKnownLocationKey
        ) {
            if let location = NSKeyedUnarchiver.unarchiveObject(
                with: data
            ) as? CLLocation {
                return call.success([
                    "location": formatLocation(location)
                ]);
            }
        }
        return call.success([
            "location": null
        ]);
    }

    @objc func openSettings(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            guard let settingsUrl = URL(
                string: UIApplication.openSettingsURLString
            ) else {
                return call.error("No link to settings available")
            }

            if UIApplication.shared.canOpenURL(settingsUrl) {
                UIApplication.shared.open(settingsUrl, completionHandler: {
                    (success) in
                    if (success) {
                        return call.success()
                    } else {
                        return call.error("Failed to open settings")
                    }
                })
            } else {
                return call.error("Cannot open settings")
            }
        }
    }

    func getCall(_ manager: CLLocationManager) -> CAPPluginCall? {
        for (callbackId, locationManager) in self.locationManagers {
            if manager == locationManager {
                return bridge.getSavedCall(callbackId)
            }
        }
        return nil
    }

    // returns true if the manager can start
    func requestPermissions(_ locationManager: CLLocationManager) -> Bool {
        let status = CLLocationManager.authorizationStatus()
        if [
            CLAuthorizationStatus.notDetermined,
            CLAuthorizationStatus.denied,
            CLAuthorizationStatus.restricted,
        ].contains(status) {
            locationManager.requestAlwaysAuthorization()
            return false
        }
        if status == CLAuthorizationStatus.authorizedWhenInUse {
            // try escalate permissions
            locationManager.requestAlwaysAuthorization()
        }
        return true
    }

    public func locationManager(
        _ manager: CLLocationManager,
        didFailWithError error: Error
    ) {
        if let call = getCall(manager) {
            if let clErr = error as? CLError {
                if clErr.code == CLError.locationUnknown {
                    #if DEBUG
                    call.error(error.localizedDescription, error)
                    #endif
                    return
                } else if (clErr.code == CLError.denied) {
                    // handled above
                    return
                }
            }
            return call.error(error.localizedDescription, error)
        }
    }

    public func locationManager(
        _ manager: CLLocationManager,
        didUpdateLocations locations: [CLLocation]
    ) {
        if let call = getCall(manager) {
            if let location = locations.last {
                call.success(formatLocation(location));
                return UserDefaults.standard.set(
                    NSKeyedArchiver.archivedData(withRootObject: location),
                    forKey: lastKnownLocationKey
                );
            }
        }
    }

    public func locationManager(
        _ manager: CLLocationManager,
        didChangeAuthorization status: CLAuthorizationStatus
    ) {
        if let call = getCall(manager) {
            if let locationManager = self.locationManagers[call.callbackId] {
                if [
                    CLAuthorizationStatus.denied,
                    CLAuthorizationStatus.restricted,
                ].contains(status) {
                    return call.reject(
                        "Access to location services is denied",
                        "NOT_AUTHORIZED"
                    )
                } else {
                    if (status != CLAuthorizationStatus.notDetermined) {
                        locationManager.startUpdatingLocation();
                    }
                }
            }
        }
    }
}

