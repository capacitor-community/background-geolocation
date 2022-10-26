/**
 * The add watcher options
 */
export interface WatcherOptions {
    /**
     * The message to be displayed in the notification bar
     */
    backgroundMessage?: string;
    /**
     * The title of the notification bar
     */
    backgroundTitle?: string;
    /**
     * Whether or not to request permission if there is no permission
     */
    requestPermissions?: boolean;
    /**
     * if "true", stale locations may be delivered while the device obtains a GPS fix. 
     * You are responsible for checking the "time"
     */
    stale?: boolean;
    /**
     * The minimum number of metres between subsequent locations
     */
    distanceFilter?: number;
    /**
     * A file URL to use to log the locations received, can be good for app crashes and debug
     * @example file://path/to/file.txt
     */
    file?: string;
}

/**
 * The location object
 */
export interface Location {
    /**
     * Latitude in degrees.
     */
    latitude: number;
    /**
     * Longitude in degrees.
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
     * True if the location was simulated by software, rather than GPS.
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

/**
 * The plugin's main interface
 */
export interface BackgroundGeolocationPlugin {
    /**
     * Add a watcher to the GPS according to the given options
     * @param options the add watcher options
     * @param callback the locaiton callback when a new location is received
     * @returns a promise with the watcher ID in order to allow removing it
     */
    addWatcher(
        options: WatcherOptions,
        callback: (
            position?: Location,
            error?: CallbackError
        ) => void
    ): Promise<string>;
    /**
     * Removes a watcher by ID
     * @param options the remove watcher options
     */
    removeWatcher(options: {
        id: string
    }): Promise<void>;
    /**
     * Opens the OS app settings screen to allow changing permissions
     */
    openSettings(): Promise<void>;
}
