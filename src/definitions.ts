declare module '@capacitor/core' {
  interface PluginRegistry {
    BackgroundGeolocation: BackgroundGeolocationPlugin;
  }
}

export interface BackgroundGeolocationOptions {
  /**
   * On Android, the plugin shows a notification which allows it to
   * continue receiving location updates in the background. If this option
   * is undefined, the notification is not delivered and hence background
   * location updates are not guaranteed.
   */
  backgroundMessage: string,
  /**
   * The title for the notification. Defaults to "Using your location".
   */
  backgroundTitle: string,
  /**
   * Whether permissions should be requested from the user automatically,
   * if they are not already granted. Defaults to "true".
   */
  requestPermissions: boolean,
  /**
   * If "true", stale locations may be delivered while the device
   * obtains a GPS fix. You are responsible for checking the "time"
   * property. If "false", locations are guaranteed to be up to date.
   * Defaults to "false".
   */
  stale: boolean,
  /**
   * The minimum number of metres between subsequent locations. Defaults to 0.
   */
  distanceFilter: number
}

export interface BackgroundGeolocationPlugin {
  /**
   * Starts a background geolocation watcher.
   * @param options Configuration object of type BackgroundGeolocationOptions.
   * @param callback Receives locations or errors.
   * @returns Promise<string> returns watcher id.
   */
  addWatcher(
    options: BackgroundGeolocationOptions,
    callback: (position: GeolocationPosition, error: any) => void
  ): Promise<string>;
  /**
   * Removes a watcher specified by its id.
   * @param id Watcher id from the addWatcher method.
   */
  removeWatcher(id: {id: string}): Promise<void>;
  /**
   * Opens location settings.
   */
  openSettings(): Promise<void>;
}

export interface GeolocationPosition {
  latitude: number;
  longitude: number;
  accuracy: number;
  altitude: number;
  altitudeAccuracy: number;
  bearing: number;
  speed: number;
  time: number;
}
