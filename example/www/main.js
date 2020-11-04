/*jslint browser */
/*global capacitorExports */
const {Plugins} = capacitorExports;
const {BackgroundGeolocation, Modals} = Plugins;

let id = Plugins.BackgroundGeolocation.addWatcher(
    {
        background: true,
        notification: {
            title: "Title",
            message: "Message"
        }
    },
    function callback(location, error) {
        if (error) {
            if (error.code === "NOT_AUTHORIZED") {
                return Modals.confirm({
                    title: "Location Required",
                    message: (
                        "Example App needs your location, " +
                        "but does not have permission.\n\n" +
                        "Open settings now?"
                    )
                }).then(function ({ value }) {
                    if (value) {
                        BackgroundGeolocation.openSettings().catch(
                            (error) => console.error(error)
                        );
                    }
                }).catch(
                    (error) => console.error(error)
                );
            }
            return console.error(error);
        }

        return console.log(location);
    }
);
