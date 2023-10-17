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

            input "motionSensors", "capability.motionSensor", title: "Motion Sensors to trigger Active",
                required: true, multiple: true

            input "motionSensorsStay", "capability.motionSensor", title: "Motion Sensors to keep Active",
                required: false, multiple: true

            input "boundaryContactSensors", "capability.contactSensor",
                title: "Boundary Contact Sensors", multiple: true

            input "presenceContactSensors", "capability.contactSensor",
                title: "Contact Sensors where Closed indicates Presence",
                multiple: true

            input "delay", "number", title: "Delay (seconds) before inactive",
                submitOnChange: true, defaultValue: 0, required: true

            input "debugSpew", "bool", title: "Log debug messages?",
                submitOnChange: true, defaultValue: false;
        }
    }
}

void installed() {
    initialize();
    triggerNotPresent();
}

void uninstalled() {
    parent.getRootDevice().delete(this.id);
}

void updated() {
    initialize();
}

void initialize() {
    if( !thisName ) {
        app.updateSetting("thisName", "Contained Motion Zone");
    }
    unsubscribe();
    subscribeAll();
}

void subscribeAll() {
    subscribe(motionSensors, "motion", handlePresenceIndication);
    subscribe(motionSensorsStay, "motion.inactive", handlePresenceIndication);
    subscribe(boundaryContactSensors, "contact", handleBoundary);
    subscribe(presenceContactSensors, "contact", handlePresenceIndication);
}

void handleBoundary(evt) {
    debug "Received ${evt.name} event (${evt.value}) from ${evt.device}"
    if( evt.value == "open" ) {
        subscribeAll();
    }
    if( !presenceIsIndicated() ) {
        delayedTriggerNotPresent();
    }
}

void delayedTriggerNotPresent() {
    if( delay > 0 ) {
        debug "Setting output to inactive in ${delay} seconds"
        runIn(delay, "triggerNotPresent", [overwrite: false]);
    }
    else {
        triggerNotPresent();
    }
}

void triggerPresent() {
    sendMessage("active");
}

void triggerNotPresent() {
    sendMessage("inactive");
}

void sendMessage(value) {
    unschedule("triggerNotPresent");
    debug "Setting output to ${value}"
    parent.getRootDevice().parse([[id: app.id, zone: thisName, name: "motion", value: value]]);
}

void handlePresenceIndication(evt) {
    debug "Received ${evt.name} event (${evt.value}) from ${evt.device}"
    def indicatesPresence = eventIndicatesPresence(evt);
    def allClosed = boundaryContactSensors?.every { it.currentValue("contact") == "closed" }
    if( indicatesPresence ) {
        if( allClosed ) {
            debug "Unsubscribing from presence events"
            unsubscribe(motionSensors);
            unsubscribe(motionSensorsStay);
            unsubscribe(presenceContactSensors);
        }
        triggerPresent();
    }
    else if( !presenceIsIndicated() ) {
        delayedTriggerNotPresent();
    }
}

Boolean presenceIsIndicated() {
    if( presenceContactSensors.any { it.currentValue("contact") == "closed"} ||
        (motionSensors + (motionSensorsStay ?: [])).
            any { it.currentValue("motion") == "active" }
    ) {
        return true;
    }
    else {
        return false;
    }
}

Boolean eventIndicatesPresence(evt) {
    return (evt.name == "motion" && evt.value == "active") ||
        (evt.name == "contact" && evt.value == "closed");
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

