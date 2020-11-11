#import <Foundation/Foundation.h>
#import <Capacitor/Capacitor.h>

CAP_PLUGIN(BackgroundGeolocation, "BackgroundGeolocation",
    CAP_PLUGIN_METHOD(addWatcher, CAPPluginReturnCallback);
    CAP_PLUGIN_METHOD(removeWatcher, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(openSettings, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(guess, CAPPluginReturnPromise);
)
