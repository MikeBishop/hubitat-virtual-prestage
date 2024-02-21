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

            (SUPPORTED_PROPERTIES + [["Color Mode", "ColorMode"]]).each{
                def name = it[0];
                def capability = it[1];

                if( primaryDevice?.hasCapability(capability) && secondaryDevices?.any { it.hasCapability(capability) } ) {
                    input capability, "bool", title: "Sync ${name}?", defaultValue: true
                }
                else {
                    app.updateSetting(capability, false);
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

            input "meteringDelay", "number", title: "Delay between devices (ms)",
                defaultValue: 0, multiple: false

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
    subscribe(disableSwitch, "switch", "disableSwitchChanged");
    updateSubscriptions();
}

void updateSubscriptions() {
    if( switchDisabled() ) {
        unsubscribe(secondaryDevices);
        unsubscribe(primaryDevice);
        subscribe(disableSwitch, "switch", "disableSwitchChanged");
    }
    else {
        SUPPORTED_PROPERTIES.each{
            def capability = it[1];
            def properties = it[2];
            if( primaryDevice?.hasCapability(capability) && settings[capability] ) {
                properties.each{
                    subscribe(primaryDevice, it, "primaryDeviceChanged");
                };
            }
        }
        subscribe(primaryDevice, "colorMode", "primaryDeviceChanged");
        subscribe(secondaryDevices, "switch.on", "secondaryDeviceOn");
        if( !settings["ColorMode"] ) {
            // If we're not syncing color mode, we need to re-evaluate if it
            // changes on a secondary
            subscribe(secondaryDevices, "colorMode", "secondaryDeviceOn");
        }
        updateDevices(secondaryDevices?.findAll { it.currentValue("switch") == "on" });
    }

}

Boolean switchDisabled() {
    return disableSwitch?.currentValue("switch") == (disableSwitchState ? "on" : "off");
}

void updateSettings(Map newSettings) {
    log.info "Updating settings: $newSettings"
    newSettings.each{
        app.updateSetting(it.key, it.value);
    }
    initialize();
}

void disableSwitchChanged(event) {
    def disabled = event.value == (disableSwitchState ? "on" : "off")
    debug "Switch changed to ${event.value} (${disabled ? "disabled" : "enabled"})"
    updateSubscriptions();
}

void primaryDeviceChanged(event) {
    debug "Primary device changed: ${event.name} to ${event.value}"
    updateDevices(
        secondaryDevices?.findAll { it.currentValue("switch") == "on" } ?: []
    );
}

void secondaryDeviceOn(event) {
    debug "Secondary device changed: ${event.device.name} to ${event.value}"
    updateDevices([event.device])
}

void updateDevices(targets) {
    def primaryColorMode = primaryDevice?.currentValue("colorMode");
    targetProperties = SUPPORTED_PROPERTIES.findAll{ 
        it[3] == primaryColorMode || it[3] == null
    };
    targetProperties = targetProperties.findAll{settings[it[1]]};
    
    targetProperties.each{
        def capability = it[1];
        def colorMode = it[3];

        def applicable = targets?.findAll { it.hasCapability(capability) } ?: [];

        if( colorMode != null && !settings["ColorMode"] ) {
            def notApplicable = applicable.findAll { it.hasCapability("ColorMode") && it.currentValue("colorMode") != colorMode };
            if( notApplicable ) {
                debug("Skipping ${notApplicable} ${capability} because color mode is ${notApplicable*.currentValue("colorMode")} and not ${colorMode}");
                applicable -= notApplicable;
            }
        }

        def level = settings["SwitchLevel"] ? primaryDevice?.currentValue("level") : null;
        switch( colorMode ) {
            case "CT":
                def ct = primaryDevice?.currentValue("colorTemperature");

                debug "Setting ${applicable} to CT ${ct}" + (level ? " at level ${level}" : "");
                applicable.each{
                    if( it.currentValue("switch") == "on" ) {
                        if (level) {
                            it.setColorTemperature(ct, level);                            
                        }
                        else {
                            it.setColorTemperature(ct);
                        }
                        pauseExecution(meteringDelay ?: 0);
                    }
                }
                break;
            case "RGB":
                def colorMap = [
                    hue: primaryDevice?.currentValue("hue"),
                    saturation: primaryDevice?.currentValue("saturation")
                ];
                if( includeLevel ) {
                    colorMap["level"] = primaryDevice?.currentValue("level");
                }

                debug "Setting ${applicable} to RGB ${colorMap}";
                applicable.each{
                    if( it.currentValue("switch") == "on" ) {
                        it.setColor(colorMap);
                        pauseExecution(meteringDelay ?: 0);
                    }
                }

                break;
            case null:
                if( level ) {
                    debug "Setting ${applicable} to level ${level}";
                    applicable.each{
                        if( it.currentValue("switch") == "on" ) {
                            it.setLevel(level);
                            pauseExecution(meteringDelay ?: 0);
                        }
                    }
                }
                break;
        }
        targets -= applicable;
    }
}

@Field static final List SUPPORTED_PROPERTIES = [
    ["Color Temperature", "ColorTemperature", ["colorTemperature"], "CT"],
    ["RGB Color", "ColorControl", ["hue", "saturation"], "RGB"],
    ["Level", "SwitchLevel", ["level"], null]
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
