import Capacitor
import Foundation
import UIKit
import CoreLocation

// Avoids a bewildering type warning.
let null = Optional<Double>.none as Any

func formatLocation(_ location: CLLocation) -> PluginResultData {
    var simulated = false;
    if #available(iOS 15, *) {
        // Prior to iOS 15, it was not possible to detect simulated locations.
        // But in general, it is very difficult to simulate locations on iOS in
        // production.
        if location.sourceInformation != nil {
            simulated = location.sourceInformation!.isSimulatedBySoftware;
        }
    }
    return [
        "latitude": location.coordinate.latitude,
        "longitude": location.coordinate.longitude,
        "accuracy": location.horizontalAccuracy,
        "altitude": location.altitude,
        "altitudeAccuracy": location.verticalAccuracy,
        "simulated": simulated,
        "speed": location.speed < 0 ? null : location.speed,
        "bearing": location.course < 0 ? null : location.course,
        "time": NSNumber(
            value: Int(
                location.timestamp.timeIntervalSince1970 * 1000
            )
        ),
    ]
}

class Watcher {
    let callbackId: String
    let locationManager: CLLocationManager = CLLocationManager()
    private let created = Date()
    private let allowStale: Bool
    private var isUpdatingLocation: Bool = false
    init(_ id: String, stale: Bool) {
        callbackId = id
        allowStale = stale
    }
    func start() {
        // Avoid unnecessary calls to startUpdatingLocation, which can
        // result in extraneous invocations of didFailWithError.
        if !isUpdatingLocation {
            locationManager.startUpdatingLocation()
            isUpdatingLocation = true
        }
    }
    func stop() {
        if isUpdatingLocation {
            locationManager.stopUpdatingLocation()
            isUpdatingLocation = false
        }
    }
    func isLocationValid(_ location: CLLocation) -> Bool {
        return (
            allowStale ||
            location.timestamp >= created
        )
    }
}

@objc(BackgroundGeolocation)
public class BackgroundGeolocation : CAPPlugin, CLLocationManagerDelegate {
    private var watchers = [Watcher]()

    @objc public override func load() {
        UIDevice.current.isBatteryMonitoringEnabled = true
    }

    @objc func addWatcher(_ call: CAPPluginCall) {
        call.save()

        // CLLocationManager requires main thread
        DispatchQueue.main.async {
            let background = call.getString("backgroundMessage") != nil
            let watcher = Watcher(
                call.callbackId,
                stale: call.getBool("stale") ?? false
            )
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
            manager.distanceFilter = call.getDouble(
                "distanceFilter"
            ) ?? kCLDistanceFilterNone;
            manager.allowsBackgroundLocationUpdates = background
            manager.showsBackgroundLocationIndicator = background
            manager.pausesLocationUpdatesAutomatically = false
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
            return watcher.start()
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
                        // This error is sometimes sent by the manager if
                        // it cannot get a fix immediately.
                        return
                    } else if (clErr.code == .denied) {
                        watcher.stop()
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
                if watcher.isLocationValid(location) {
                    if let call = self.bridge.getSavedCall(watcher.callbackId) {
                        return call.success(formatLocation(location))
                    }
                }
            }
        }
    }

    public func locationManager(
        _ manager: CLLocationManager,
        didChangeAuthorization status: CLAuthorizationStatus
    ) {
        // If this method is called before the user decides on a permission, as
        // it is on iOS 14 when the permissions dialog is presented, we ignore
        // it.
        if status != .notDetermined {
            if let watcher = self.watchers.first(
                where: { $0.locationManager == manager }
            ) {
                return watcher.start()
            }
        }
    }
}

