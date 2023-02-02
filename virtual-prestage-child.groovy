/*
    Virtual Prestaging
    Copyright 2022 Mike Bishop,  All Rights Reserved
*/
import groovy.transform.Field

definition (
    parent: "evequefou:Virtual Prestaging",
    name: "Virtual Prestaging Child",
    namespace: "evequefou",
    author: "Mike Bishop",
    description: "Sync CT and RGBW lights to a primary device for pre-staging",
    importUrl: "https://raw.githubusercontent.com/MikeBishop/hubitat-virtual-prestage/main/virtual-prestage-child.groovy",
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
            }

            input "disableSwitch", "capability.switch", title: "Switch to disable",
                required: false, multiple: false, submitOnChange: true, width: 5

            if( disableSwitch ) {
                if( disableSwitchState == null ) {
                    app.updateSetting("disableSwitchState", true);
                }
                input "disableSwitchState", "bool",
                    title: "Disable when switch is " +
                        maybeBold("on", disableSwitchState) + " or " +
                        maybeBold("off", !disableSwitchState) + "?",
                    defaultValue: true, submitOnChange: true, width: 5
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
        if( primaryDevice?.hasCapability(capability) && settings[capability] ) {
            subscribe(primaryDevice, property, "primaryDeviceChanged");
        }
    }
    subscribe(secondaryDevices, "switch.on", "secondaryDeviceOn");

    updateDevices(secondaryDevices?.findAll { it.currentValue("switch") == "on" });
}

void updateSettings(Map newSettings) {
    log.info "Updating settings: $newSettings"
    newSettings.each{
        app.updateSetting(it.key, it.value);
    }
    initialize();
}

void primaryDeviceChanged(event) {
    updateDevices(
        secondaryDevices?.findAll { it.currentValue("switch") == "on" } ?: [],
        event.name
    );
}

void secondaryDeviceOn(event) {
    updateDevices([event.device])
}

void updateDevices(targets, targetProperty = null) {
    if( disableSwitch && disableSwitch.currentValue("switch") == (disableSwitchState ? "on" : "off") ) {
        debug("Not updating devices because ${disableSwitch} is ${disableSwitch.currentValue("switch")}");
        return;
    }

    def updateAll = targetProperty == null;
    SUPPORTED_PROPERTIES.each{
        def capability = it[1];
        def property = it[2];
        def command = it[3];
        def colorMode = it[4];

        if( (updateAll || targetProperty == property) &&
            settings[capability] &&
            primaryDevice?.hasCapability(capability) )
        {
            def applicable = targets?.findAll { it.hasCapability(capability) } ?: [];

            def skip =
                (colorMode && primaryDevice.currentValue("colorMode") != colorMode) ?
                applicable.findAll { it.hasCapability("ColorMode") } : [];

            if( skip ) {
                debug("Skipping ${skip} ${property} because ${primaryDevice} color mode is ${primaryDevice.currentValue("colorMode")} and not ${colorMode}");
            }

            if( property == "level" ) {
                def presettable = targets.findAll { it.hasCapability("LevelPreset") } - skip;
                presettable*.presetLevel(primaryDevice.currentValue("level"));
                skip += presettable;
            }

            def value = primaryDevice.currentValue(property);
            applicable -= skip;
            if( value != null  && applicable ) {
                debug("Setting ${applicable} ${property} to ${value}");
                applicable*."${command}"(value);
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

private String maybeBold(String text, Boolean bold) {
    if (bold) {
        return "<b>${text}</b>"
    } else {
        return text
    }
}
