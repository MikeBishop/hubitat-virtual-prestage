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

            def booleanVars = getGlobalVarsByType("boolean").collect{ it.key }
            if( booleanVars.any() ) {
                input "disableType", "bool", submitOnChange: true,
                    title: "Disable using a " +
                        maybeBold("variable", !disableType) + " or " +
                        maybeBold("switch", disableType) + "?",
                    defaultValue: true
            }
            else {
                app.updateSetting("disableType", true);
            }

            if( disableType) {
                input "disableSwitch", "capability.switch", title: "Switch to disable",
                    required: false, multiple: false, submitOnChange: true, width: 5
            }    
            else {
                input "disableVar", "enum", options: booleanVars.sort(),
                    title: "Variable to disable", width: 5, required: false
            }        

            if( disableSwitchState == null ) {
                app.updateSetting("disableSwitchState", true);
            }
            def text = disableType ? ["switch", "off", "on"] : ["variable", "false", "true"];
            if( disableSwitch || disableVar ) {
                input "disableSwitchState", "bool",
                    title: "Disable when ${text[0]} is " +
                        maybeBold(text[1], !disableSwitchState) +" or " +
                        maybeBold(text[2], disableSwitchState) + "?",
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
    removeAllInUseGlobalVar();
    
    subscribe(disableSwitch, "switch", "disableSwitchChanged");
    updateSubscriptions();

    if( !disableType && disableVar ) {
        addInUseGlobalVar(disableVar);
        subscribe(location, "variable:${disableVar}", "disableSwitchChanged");
    }
}

void renameVariable(String oldName, String newName) {
    if( disableVar == oldName ) {
        app.updateSetting("disableVar", newName);
        removeInUseGlobalVar(oldName);
        addInUseGlobalVar(newName);
    }
}

void updateSubscriptions() {
    if( actionsDisabled() ) {
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

Boolean actionsDisabled() {
    if( disableType == null ) {
        app.updateSetting("disableType", true);
    }
    return disableType ?
        // Switch
        disableSwitch?.currentValue("switch") == (disableSwitchState ? "on" : "off") :
        // Variable
        disableVar ? getGlobalVar(disableVar)?.value != disableSwitchState : false;
}

void updateSettings(Map newSettings) {
    log.info "Updating settings: $newSettings"
    newSettings.each{
        app.updateSetting(it.key, it.value);
    }
    initialize();
}

void disableSwitchChanged(event) {
    debug "${event.device} ${event.name} changed to ${event.value} (${actionsDisabled() ? "disabled" : "enabled"})"
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
