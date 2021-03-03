import { CallbackID } from "@capacitor/core/dist/esm/core-plugin-definitions";

declare module "@capacitor/core" {
    interface PluginRegistry {
        BackgroundGeolocation: BackgroundGeolocationPlugin;
    }
}

export interface WatcherOptions {
    backgroundMessage?: string;
    backgroundTitle?: string;
    requestPermissions?: boolean;
    iconName?: string;
    iconType?: string;
    silentNotifications?: boolean;
    notificationChannelName? :string;
    stale?: boolean;
    distanceFilter?: number;
}

export interface Location {
    latitude: number;
    longitude: number;
    accuracy: number;
    altitude: number | null;
    altitudeAccuracy: number | null;
    bearing: number | null;
    speed: number | null;
    time: number | null;
}

export interface CallbackError extends Error {
    code?: string;
}

export interface BackgroundGeolocationPlugin {
    addWatcher(
        options: WatcherOptions,
        callback: (
            position?: Location,
            error?: CallbackError
        ) => void
    ): CallbackID;
    removeWatcher(options: {
        id: CallbackID
    }): Promise<void>;
    openSettings(): Promise<void>;
}
