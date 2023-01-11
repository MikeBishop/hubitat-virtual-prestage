/*
    Confirm On
    Copyright 2022 Mike Bishop,  All Rights Reserved
*/

definition (
    name: "Confirm On", namespace: "evequefou", author: "Mike Bishop", description: "Help bulbs recover after power loss by re-sending on() commands",
    importUrl: "https://raw.githubusercontent.com/MikeBishop/hubitat-virtual-prestage/main/confirm-on.groovy",
    category: "Lighting",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

Map mainPage() {
    dynamicPage(name: "mainPage", title: "Confirm On", install: true, uninstall: true) {
        initialize();
        section() {
            paragraph "Certain Zigbee bulbs will report themselves as on after " +
                "power loss, but actually turn back off. This app sends an on() " +
                "command to the bulb when it reports itself as on."
        }
        section() {
            input "monitored", "capability.switch", title: "Monitored Devices",
                required: true, multiple: true

            input "debugSpew", "bool", title: "Log debug messages?",
                submitOnChange: true, defaultValue: false;
        }
    }
}

void installed() {
    initialize();
}

void updated() {
    initialize();
}

void initialize() {
    unsubscribe();
    subscribe(monitored, "switch.on", "deviceOn");
}

void deviceOn(event) {
    event.device.on();
}

void debug(String msg) {
    if( debugSpew ) {
        log.debug(msg)
    }
}

void warn(String msg) {
    log.warn(msg)
}

void error(Exception ex) {
    log.error "${ex} at ${ex.getStackTrace()}"
}

void error(String msg) {
    log.error msg
}
