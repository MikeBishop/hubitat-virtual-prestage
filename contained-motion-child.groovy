/*
    Contained Motion
    Copyright 2023 Mike Bishop,  All Rights Reserved
*/
definition (
    parent: "evequefou:Contained Motion",
    name: "Contained Motion Child",
    namespace: "evequefou",
    author: "Mike Bishop",
    description: "Virtual motion detector that never becomes inactive while contact sensors are closed",
    importUrl: "https://raw.githubusercontent.com/MikeBishop/hubitat-virtual-prestage/main/contained-motion-child.groovy",
    category: "Lighting",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

Map mainPage() {
    dynamicPage(name: "mainPage", title: "Contained Motion", install: true, uninstall: true) {
        initialize();
        section() {
            paragraph "If motion is detected in a room and the door is closed, " +
                "the room remains occupied even if motion is no longer detected. " +
                "The room becomes potentially unoccupied when the door is opened, " +
                "at which point the motion sensors are merely aggregated."
        }
        section() {
            input "thisName", "text", title: "Name this instance", submitOnChange: true
            if(thisName) app.updateLabel("$thisName")

            input "motionSensors", "capability.motionSensor", title: "Motion Sensors",
                required: true, multiple: true

            input "contactSensors", "capability.contactSensor", title: "Contact Sensors",
                multiple: true

            input "debugSpew", "bool", title: "Log debug messages?",
                submitOnChange: true, defaultValue: false;
        }
    }
}

void installed() {
    initialize();
}

void uninstalled() {
    parent.getRootDevice().delete(this.id);
}

void updated() {
    initialize();
}

void initialize() {
    if( !thisName ) {
        app.updateSetting("thisName", "Contained Motion");
    }
    unsubscribe();
    subscribe(contactSensors, "contact", handleContact);
    subscribe(motionSensors, "motion", handleMotion);
}

void handleContact(evt) {
    debug "Received ${evt.name} event (${evt.value}) from ${evt.device}"
    if( evt.value == "open" ) {
        subscribe(motionSensors, "motion", handleMotion);
    }
    if( motionSensors.every { it.currentValue("motion") == "inactive" } ) {
        parent.getRootDevice().parse([[id: this.id, zone: thisName, name: "motion", value: "inactive"]])
    }
}

void handleMotion(evt) {
    debug "Received ${evt.name} event (${evt.value}) from ${evt.device}"
    def allClosed = contactSensors?.every { it.currentContact == "closed" }
    if( evt.value == "active" ) {
        parent.getRootDevice().parse([[id: this.id, zone: thisName, name: "motion", value: evt.value]])
        if( allClosed ) {
            debug "Unsubscribing from motion events"
            unsubscribe(motionSensors);
        }
    }
    else if( motionSensors.every { it.currentValue("motion") == "inactive" } ) {
        parent.getRootDevice().parse([[id: this.id, zone: thisName, name: "motion", value: evt.value]])
    }
}

void updateSettings(Map newSettings) {
    log.info "Updating settings: $newSettings"
    newSettings.each{
        app.updateSetting(it.key, it.value);
    }
    initialize();
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

