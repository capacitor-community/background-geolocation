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

class Watcher {
    let callbackId: String
    let locationManager: CLLocationManager = CLLocationManager()
    private var isUpdatingLocation: Bool = false
    init(_ id: String) {
        callbackId = id
    }
    func ensureUpdatingLocation() {
        // Avoid unnecessary calls to startUpdatingLocation, which can
        // result in extraneous invocations of didFailWithError.
        if !isUpdatingLocation {
            locationManager.startUpdatingLocation()
            isUpdatingLocation = true
        }
    }
}

@objc(BackgroundGeolocation)
public class BackgroundGeolocation : CAPPlugin, CLLocationManagerDelegate {
    private var watchers = [Watcher]()

    @objc public override func load() {
        UIDevice.current.isBatteryMonitoringEnabled = true;
    }

    @objc func addWatcher(_ call: CAPPluginCall) {
        call.save()
        
        // CLLocationManager requires main thread
        DispatchQueue.main.async {
            let background = call.getString("backgroundMessage") != nil
            let watcher = Watcher(call.callbackId)
            let manager = watcher.locationManager
            manager.delegate = self
            let externalPower = [
                .full,
                .charging
            ].contains(UIDevice.current.batteryState)
            manager.desiredAccuracy = (
                externalPower
                ? kCLLocationAccuracyBestForNavigation
                : kCLLocationAccuracyBest
            )
            manager.allowsBackgroundLocationUpdates = background
            self.watchers.append(watcher)
            if call.getBool("requestPermissions") != false {
                let status = CLLocationManager.authorizationStatus()
                if [
                    .notDetermined,
                    .denied,
                    .restricted,
                ].contains(status) {
                    return (
                        background
                        ? manager.requestAlwaysAuthorization()
                        : manager.requestWhenInUseAuthorization()
                    )
                }
                if (
                    background && status == .authorizedWhenInUse
                ) {
                    // Attempt to escalate.
                    manager.requestAlwaysAuthorization()
                }
            }
            return watcher.ensureUpdatingLocation()
        }
    }

    @objc func removeWatcher(_ call: CAPPluginCall) {
        // CLLocationManager requires main thread
        DispatchQueue.main.async {
            if let callbackId = call.getString("id") {
                if let index = self.watchers.firstIndex(
                    where: { $0.callbackId == callbackId }
                ) {
                    self.watchers[index].locationManager.stopUpdatingLocation()
                    self.watchers.remove(at: index)
                }
                if let savedCall = self.bridge.getSavedCall(callbackId) {
                    self.bridge.releaseCall(savedCall)
                }
                return call.success()
            }
            return call.error("No callback ID")
        }
    }

//    @objc func approximate(_ call: CAPPluginCall) {
//        call.save();
//        self.guesses.append(call.callbackId);
//
//        // CLLocationManager requires main thread
//        DispatchQueue.main.async {
//            let locationManager = CLLocationManager()
//            self.locationManagers[call.callbackId] = locationManager
//            locationManager.delegate = self
//            locationManager.desiredAccuracy = kCLLocationAccuracyThreeKilometers
//            locationManager.requestLocation()
//
//            // Set a timeout.
//            if let milliseconds = call.getDouble("timeout") {
//                DispatchQueue.main.asyncAfter(
//                    deadline: .now() + (milliseconds / 1000)
//                ) {
//                    if self.guesses.contains(call.callbackId) {
//                        call.success([
//                            "location": null
//                        ])
//                        return self.releaseCall(call.callbackId)
//                    }
//                }
//            }
//        }
//    }

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

    public func locationManager(
        _ manager: CLLocationManager,
        didFailWithError error: Error
    ) {
        if let watcher = self.watchers.first(
            where: { $0.locationManager == manager }
        ) {
            if let call = self.bridge.getSavedCall(watcher.callbackId) {
                if let clErr = error as? CLError {
                    if clErr.code == .locationUnknown {
                        #if DEBUG
                        call.error(error.localizedDescription, error)
                        #endif
                        // This error is sometimes sent by the manager if
                        // it cannot get a fix immediately.
                        return
                    } else if (clErr.code == .denied) {
                        return call.reject(
                            "Permission denied.",
                            "NOT_AUTHORIZED"
                        )
                    }
                }
                return call.error(error.localizedDescription, error)
            }
        }
    }

    public func locationManager(
        _ manager: CLLocationManager,
        didUpdateLocations locations: [CLLocation]
    ) {
        if let location = locations.last {
            if let watcher = self.watchers.first(
                where: { $0.locationManager == manager }
            ) {
                if let call = self.bridge.getSavedCall(watcher.callbackId) {
                    return call.success(formatLocation(location));
                }
            }
        }
    }

    public func locationManager(
        _ manager: CLLocationManager,
        didChangeAuthorization status: CLAuthorizationStatus
    ) {
        if let watcher = self.watchers.first(
            where: { $0.locationManager == manager }
        ) {
            return watcher.ensureUpdatingLocation()
        }
    }
}

