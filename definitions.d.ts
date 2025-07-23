/**
 * The options for configuring a watcher that listens for location updates.
 */
export interface WatcherOptions {
    /**
     * If the "backgroundMessage" option is defined, the watcher will
     * provide location updates whether the app is in the background or the
     * foreground. If it is not defined, location updates are only
     * guaranteed in the foreground. This is true on both platforms.
     * 
     * On Android, a notification must be shown to continue receiving
     * location updates in the background. This option specifies the text of
     * that notification.
     */
    backgroundMessage?: string;
    /**
     * The title of the notification mentioned above.
     * @default "Using your location"
     */
    backgroundTitle?: string;
    /**
     * Whether permissions should be requested from the user automatically,
     * if they are not already granted.
     * @default true
     */
    requestPermissions?: boolean;
    /**
     * If "true", stale locations may be delivered while the device
     * obtains a GPS fix. You are responsible for checking the "time"
     * property. If "false", locations are guaranteed to be up to date.
     * @default false
     */
    stale?: boolean;
    /**
     * The distance in meters that the device must move before a new location update is triggered.
     * This is used to filter out small movements and reduce the number of updates.
     * @default 0
     */
    distanceFilter?: number;
}

/**
 * Represents a geographical location with various attributes.
 */
export interface Location {
    /**
     * Longitude in degrees.
     */
    latitude: number;
    /**
     * Latitude in degrees.
     */
    longitude: number;
    /**
     * Radius of horizontal uncertainty in metres, with 68% confidence.
     */
    accuracy: number;
    /**
     * Metres above sea level (or null).
     */
    altitude: number | null;
    /**
     * Vertical uncertainty in metres, with 68% confidence (or null).
     */
    altitudeAccuracy: number | null;
    /**
     * `true` if the location was simulated by software, rather than GPS.
     */
    simulated: boolean;
    /**
     * Deviation from true north in degrees (or null).
     */
    bearing: number | null;
    /**
     * Speed in metres per second (or null).
     */
    speed: number | null;
    /**
     * Time the location was produced, in milliseconds since the unix epoch.
     */
    time: number | null;
}

export interface CallbackError extends Error {
    code?: string;
}

export interface BackgroundGeolocationPlugin {
    /**
     * Adds a watcher for location updates.
     * The watcher will be invoked with the latest location whenever it is available.
     * If an error occurs, the callback will be invoked with the error.
     * 
     * @param options the watcher options
     * @param callback the callback to be invoked when a new location is available or an error occurs
     * @returns a promise that resolves to a unique identifier for the watcher ID
     */
    addWatcher(
        options: WatcherOptions,
        callback: (
            position?: Location,
            error?: CallbackError
        ) => void
    ): Promise<string>;
    /**
     * Removes a watcher by its unique identifier.
     * @param options the options for removing the watcher
     * @returns a promise that resolves when the watcher is successfully removed
     */
    removeWatcher(options: {
        id: string
    }): Promise<void>;
    /**
     * Opens the settings page of the app.
     */
    openSettings(): Promise<void>;
}
