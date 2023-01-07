/*
    Virtual Prestaging
    Copyright 2022 Mike Bishop,  All Rights Reserved
*/
import groovy.transform.Field

definition (
    name: "Virtual Prestaging", namespace: "evequefou", author: "Mike Bishop", description: "Sync CT and RGBW lights to a primary device for pre-staging",
    importUrl: "https://raw.githubusercontent.com/MikeBishop/hubitat-virtual-prestage/main/virtual-prestage.groovy",
    category: "Lighting",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

Map mainPage() {
    dynamicPage(name: "mainPage", title: "Virtual Prestaging", install: true, uninstall: true) {
        initialize();
        section() {
            paragraph "Not all devices support prestaging level, and prestaging support " +
                "for CT and RGB devices is still being defined. This app simulates prestaging " +
                "by setting all secondary devices to match the source device when " +
                "the secondary devices are on."
        }
        section() {
            input "thisName", "text", title: "Name this instance", submitOnChange: true
            if(thisName) app.updateLabel("$thisName")

            input "primaryDevice", "capability.switchLevel", title: "Source Device",
                required: true, multiple: false, submitOnChange: true

            input "secondaryDevices", "capability.switchLevel", title: "Secondary Devices",
                multiple: true, submitOnChange: true

            SUPPORTED_PROPERTIES.each{
                def name = it[0];
                def capability = it[1];

                if( primaryDevice?.hasCapability(capability) && secondaryDevices?.any { it.hasCapability(capability) } ) {
                    input capability, "bool", title: "Sync ${name}?", defaultValue: true
                }
                else {
                    log.debug "Primary device does ${primaryDevice?.hasCapability(capability) ? "" : "not "} support ${capability}"
                    log.debug "Secondary devices do ${secondaryDevices?.any { it.hasCapability(capability) } ? "" : "not "} support ${capability}"
                }
            }

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
    if( !thisName ) {
        app.updateSetting("thisName", "Virtual Prestaging");
    }
    unsubscribe();
    SUPPORTED_PROPERTIES.each{
        def capability = it[1];
        def property = it[2];
        if( primaryDevice?.hasCapability(capability) ) {
            subscribe(primaryDevice, property, "primaryDeviceChanged");
        }
    }
    subscribe(secondaryDevices, "switch.on", "secondaryDeviceOn");

    secondaryDevices?.findAll { it.currentValue("switch") == "on" }?.
        each { updateDevice(it) };
}

void primaryDeviceChanged(event) {
    secondaryDevices?.findAll { it.currentValue("switch") == "on" }?.
        each { updateDevice(it, event.name) };
}

void secondaryDeviceOn(event) {
    updateDevice(event.device)
}

void updateDevice(target, targetProperty = null) {
    def updateAll = targetProperty == null;
    SUPPORTED_PROPERTIES.each{
        def capability = it[1];
        def property = it[2];
        def command = it[3];
        def colorMode = it[4];

        if( colorMode && primaryDevice.currentValue("colorMode") != colorMode ) {
            debug("Skipping ${target} ${property} because color mode is ${primaryDevice.currentValue("colorMode")}");
        }
        else if( property == "level" && target.hasCapability("LevelPreset") ) {
            target.presetLevel = primaryDevice.currentValue("level");
        }
        else if( (updateAll || targetProperty == property) &&
            settings[capability] &&
            primaryDevice.hasCapability(capability) &&
            target.hasCapability(capability)
        ) {
            def value = primaryDevice.currentValue(property);
            if( value != null ) {
                debug("Setting ${target} ${property} to ${value}");
                target."${command}"(value);
            }
        }
    }
}

@Field static final List SUPPORTED_PROPERTIES = [
    ["Color Temperature", "ColorTemperature", "colorTemperature", "setColorTemperature", "CT"],
    ["Hue", "ColorControl", "hue", "setHue", "RGB"],
    ["Saturation", "ColorControl", "saturation", "setSaturation", "RGB"],
    ["Level", "SwitchLevel", "level", "setLevel", null]
]

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
