import Capacitor
import Foundation
import UIKit
import CoreLocation

// Avoids a bewildering type warning.
let null = Optional<Double>.none as Any

func formatLocation(_ location: CLLocation) -> PluginCallResultData {
    var simulated = false
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
        "time": NSNumber(value: Int(location.timestamp.timeIntervalSince1970 * 1000))
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
public class BackgroundGeolocation: CAPPlugin, CAPBridgedPlugin, CLLocationManagerDelegate {
    private var watchers = [Watcher]()

    // Called when the plugin is loaded. This method can be used for initial setup or configuration.
    @objc public override func load() {
        UIDevice.current.isBatteryMonitoringEnabled = true
    }

    /// The unique identifier for the plugin.
    public let identifier = "BackgroundGeolocation"

    /// The name used to reference this plugin in JavaScript.
    public let jsName = "BackgroundGeolocation"
    
    // A list of methods exposed by this plugin. These methods can be called from the JavaScript side.
    // - `addWatcher`: Starts watching for location updates. Accepts an options object and a callback function.
    //   Returns a Promise that resolves to a watcher ID, which can be used to remove the watcher later.
    // - `removeWatcher`: Stops a previously added watcher. Accepts an object containing the watcher ID.
    //   Returns a Promise that resolves when the watcher is removed.
    // - `openSettings`: Opens the device's location settings to allow the user to grant location permissions.
    //   Returns a Promise that resolves when the settings screen is opened.
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "addWatcher", returnType: CAPPluginReturnCallback),
        CAPPluginMethod(name: "removeWatcher", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "openSettings", returnType: CAPPluginReturnPromise)
    ]

    @objc func addWatcher(_ call: CAPPluginCall) {
        call.keepAlive = true
        
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
            var distanceFilter = call.getDouble("distanceFilter")
            // It appears that setting manager.distanceFilter to 0 can prevent
            // subsequent location updates. See issue #88.
            if distanceFilter == nil || distanceFilter == 0 {
                distanceFilter = kCLDistanceFilterNone
            }
            
            manager.distanceFilter = distanceFilter!
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
                if let savedCall = self.bridge?.savedCall(withID: callbackId) {
                    self.bridge?.releaseCall(savedCall)
                }
                return call.resolve()
            }
            return call.reject("No callback ID")
        }
    }

    @objc func openSettings(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            guard let settingsUrl = URL(
                string: UIApplication.openSettingsURLString
            ) else {
                return call.reject("No link to settings available")
            }
            
            if UIApplication.shared.canOpenURL(settingsUrl) {
                UIApplication.shared.open(settingsUrl, completionHandler: {
                    (success) in
                    if (success) {
                        return call.resolve()
                    } else {
                        return call.reject("Failed to open settings")
                    }
                })
            } else {
                return call.reject("Cannot open settings")
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
            if let call = self.bridge?.savedCall(withID: watcher.callbackId) {
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
                return call.reject(error.localizedDescription, nil, error)
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
                    if let call = self.bridge?.savedCall(withID: watcher.callbackId) {
                        return call.resolve(formatLocation(location))
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
