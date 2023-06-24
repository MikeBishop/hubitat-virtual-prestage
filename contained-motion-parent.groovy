/*
    Contained Motion Parent
    Copyright 2023 Mike Bishop,  All Rights Reserved
*/


definition(
    name: "Contained Motion",
    namespace: "evequefou",
    author: "Mike Bishop",
    description: "Motion zones bounded by contact sensors",
    category: "Lighting",
    importUrl: "https://raw.githubusercontent.com/MikeBishop/hubitat-virtual-prestage/main/contained-motion-parent.groovy",
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

				paragraph "Each child instance monitors a specific set of devices. " +
                    "You can create as many instances as you need for different rooms. " +
                    "Each zone will aggregate motion, with the additional knowledge that " +
                    "a room remains occupied if motion was detected while all doors were closed."

            }
  			section("<b>Zoning Rules:</b>") {
				app(name: "anyOpenApp", appName: "Contained Motion Child", namespace: "evequefou", title: "<b>Create a new contained motion rule</b>", multiple: true)
			}
		}
	}
}

private getRootDevice() {
    def dni = "Contained-" + app.id.toString()
    def rootDevice = getChildDevice(dni)
    def rootLabel = thisName ?: "Contained Motion Zones"
    if (!rootDevice) {
        log.info "Creating container device: ${dni}"
        rootDevice = addChildDevice("evequefou", "Contained Motion Container", dni, null,
             [name: "Contained Motion Zones", label: rootLabel, completedSetup: true ])
    }
    if (rootDevice.getLabel() != rootLabel) {
        rootDevice.setLabel(rootLabel)
    }
    return rootDevice
}

