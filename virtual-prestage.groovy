/*
    Virtual Prestage Parent
    Copyright 2022 Mike Bishop,  All Rights Reserved
*/


definition(
    name: "Virtual Prestaging",
    namespace: "evequefou",
    author: "Mike Bishop",
    description: "Collection of Virtual Prestaging rules",
    category: "Lighting",
    importUrl: "https://raw.githubusercontent.com/MikeBishop/hubitat-virtual-prestage/main/virtual-prestage.groovy",
	iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
     page name: "mainPage", title: "", install: true, uninstall: true
}


def installed() {
    log.info "Installed with settings: ${settings}"
    initialize()
}

def uninstalled() {
    log.info "Uninstalled"
}

def updated() {
    log.info "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

def initialize() {
    log.info "There are ${childApps.size()} child apps"
    childApps.each { child ->
    	log.info "Child app: ${child.label}"
        child.initialize();
    }

    if( primaryDevice != null && childApps.size() == 0 ) {
        // Upgrade from non-parent/child version
        def newChild = addChildApp("evequefou", "Virtual Prestaging Child", thisName);
        final List childSettingKeys = [
            "primaryDevice",
            "secondaryDevices",
            "disableSwitch",
            "disableSwitchState",
            "ColorTemperature",
            "ColorControl",
            "ColorControl",
            "SwitchLevel",
            "debugSpew"
        ];
        def childSettings = childSettingKeys.collectEntries { [it, settings[it]] };
        newChild.updateSettings(childSettings);
        log.info "New child app: ${newChild.label}"
        childSettingKeys.each { app.removeSetting(it) };
    }
}

def getFormat(type, myText=""){
	if(type == "header-green") return "<div style='color:#ffffff;font-weight: bold;background-color:#81BC00;border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
    if(type == "line") return "\n<hr style='background-color:#1A77C9; height: 1px; border: 0;'></hr>"
	if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
}

def mainPage() {
    dynamicPage(name: "mainPage", install: true, uninstall: true) {
        def appInstalled = app.getInstallationState();

        if (appInstalled != 'COMPLETE') {
    		section{paragraph "Please hit 'Done' to install '${app.label}' parent app "}
  	    }
        else {
			section() {
                input "thisName", "text", title: "Name this instance", submitOnChange: true
                if(thisName) {
                    app.updateLabel("$thisName")
                }

				paragraph "Each child instance can be configured to sync a specific set of devices. " +
                    "You can create as many instances as you need to cover all of your devices. " +
                    "For example, you might have a set of RGBW lights that you want to sync to a " +
                    "primary device, and a set of CT lights that you want to sync to a different " +
                    "primary device."

            }
  			section("<b>Prestaging Rules:</b>") {
				app(name: "anyOpenApp", appName: "Virtual Prestaging Child", namespace: "evequefou", title: "<b>Create a new prestaging rule</b>", multiple: true)
			}
		}
	}
}
