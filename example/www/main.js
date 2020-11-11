/*jslint browser, devel */
/*global capacitorExports */
const {Plugins} = capacitorExports;
const {BackgroundGeolocation, Modals} = Plugins;

const started = Date.now();
const watcher_colours = {};
const colours = [
    "red",
    "green",
    "blue",
    "yellow",
    "pink",
    "orange",
    "purple",
    "cyan"
];

function log_for_watcher(text, time, colour = "gray") {
    const li = document.createElement("li");
    li.style.color = colour;
    li.innerText = (
        String(
            Math.floor((time - started) / 1000)
        ).padStart(3, "0") +
        ":" +
        text
    );
    const container = document.getElementById("log");
    return container.insertBefore(li, container.firstChild);
}

function log_error(error, colour = "gray") {
    console.error(error);
    return log_for_watcher(
        error.name + ": " + error.message,
        Date.now(),
        colour
    );
}

function log_location(location, watcher_ID) {
    return log_for_watcher(
        location.latitude + ":" + location.longitude,
        location.time,
        watcher_colours[watcher_ID]
    );
}

function add_watcher(background) {
    let id = Plugins.BackgroundGeolocation.addWatcher(
        (
            background
            ? {
                backgroundTitle: "Tracking your location, senõr.",
                backgroundMessage: "Cancel to prevent battery drain."
            }
            : {}
        ),
        function callback(location, error) {
            if (error) {
                if (error.code === "NOT_AUTHORIZED") {
                    Modals.confirm({
                        title: "Location Required",
                        message: (
                            "Example App needs your location, " +
                            "but does not have permission.\n\n" +
                            "Open settings now?"
                        )
                    }).then(function ({value}) {
                        if (value) {
                            BackgroundGeolocation.openSettings().catch(
                                (error) => log_error(error, watcher_colours[id])
                            );
                        }
                    }).catch((error) => log_error(error, watcher_colours[id]));
                }
                return log_error(error, watcher_colours[id]);
            }

            return log_location(location, id);
        }
    );

    const watcher_nr = Object.keys(watcher_colours).length;
    watcher_colours[id] = colours[watcher_nr];

    const container = document.getElementById("watchers");
    const li = document.createElement("li");
    li.style.backgroundColor = watcher_colours[id];
    li.innerText = (
        background
        ? "BG"
        : "FG"
    );

    const remove_btn = document.createElement("button");
    remove_btn.innerText = "Remove";
    remove_btn.onclick = function () {
        return Plugins.BackgroundGeolocation.removeWatcher({id}).then(
            function () {
                container.removeChild(
                    container.children.item(
                        Object.keys(watcher_colours).indexOf(id)
                    )
                );
                delete watcher_colours[id];
            }
        ).catch(
            (error) => log_error(error, watcher_colours[id])
        );
    };

    li.appendChild(remove_btn);

    return container.appendChild(li);
}

function approximate() {
    return Plugins.BackgroundGeolocation.approximate({
        timeout: 500
    }).then(
        function ({location}) {
            return (
                location === null
                ? log_for_watcher("null", Date.now())
                : log_for_watcher(
                    [
                        location.latitude,
                        location.longitude
                    ].map(String).join(":"),
                    location.time
                )
            );
        }
    ).catch(log_error);
}
