/*
    Contained Motion
    Copyright 2023-2024 Mike Bishop,  All Rights Reserved
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
                title: "Boundary contact sensors", multiple: true

            input "presenceContactSensors", "capability.contactSensor",
                title: "Contact Sensors where closed indicates ongoing presence",
                multiple: true

            input "interiorContactSensors", "capability.contactSensor",
                title: "Contact Sensors where activity indicates Presence",
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
    subscribe(interiorContactSensors, "contact", handlePresenceIndication);
}

void handleBoundary(evt) {
    if( evt.value == "open" ) {
        subscribeAll();
        handlePresenceIndication(evt);
    }
    else if( !presenceIsIndicated() ) {
        delayedTriggerNotPresent();
    }
    else {
        debug "Received ${evt.name} event (${evt.value}) from ${evt.device}, but presence is still indicated"
    }
}

void delayedTriggerNotPresent() {
    if( state.currentValue == "inactive" ) {
        return;
    }
    if( delay > 0 ) {
        debug "Setting output to inactive in ${delay} seconds"
        runIn(delay, "triggerNotPresent", [overwrite: false]);
        subscribe(motionSensorsStay, "motion.active", handlePresenceIndication);
    }
    else {
        triggerNotPresent();
    }
}

void triggerPresent() {
    unschedule("triggerNotPresent");
    unsubscribe(motionSensorsStay, "motion.active");
    if( state.currentValue == "active" ) {
        return;
    }
    sendMessage("active");
}

void triggerNotPresent() {
    if( state.currentValue == "inactive" ) {
        return;
    }
    unsubscribe(motionSensorsStay, "motion.active");
    sendMessage("inactive");
}

void sendMessage(value) {
    unschedule("triggerNotPresent");
    debug "Setting output to ${value}"
    parent.getRootDevice().parse([[id: app.id, zone: thisName, name: "motion", value: value]]);
    state.currentValue = value;
}

void handlePresenceIndication(evt) {
    def indicatesPresence = eventIndicatesPresence(evt);
    def allClosed = boundaryContactSensors?.every { it.currentValue("contact") == "closed" }

    debug "Received ${evt.name} event (${evt.value}) from ${evt.device} (${indicatesPresence ? "indicates " : "does not indicate "} presence)"

    if( indicatesPresence ) {
        if( allClosed ) {
            debug "Unsubscribing from interior events"
            unsubscribe(motionSensors);
            unsubscribe(motionSensorsStay);
            unsubscribe(presenceContactSensors);
            unsubscribe(interiorContactSensors);
            subscribe(boundaryContactSensors, "contact", handleBoundary);
        }

        if (allClosed || delay > 0 || presenceIsIndicated()) {
            triggerPresent();
        }
    }

    if( (!indicatesPresence || !allClosed) && !presenceIsIndicated() ) {
        delayedTriggerNotPresent();
    }
}

Boolean presenceIsIndicated() {
    if( presenceContactSensors.any { it.currentValue("contact") == "closed"} ||
        (motionSensors + (motionSensorsStay ?: [])).
            any { it.currentValue("motion") == "active" }
    ) {
        def activeSensors = (presenceContactSensors ?: []).findAll({ it.currentValue("contact") == "closed" }) +
            ((motionSensors ?: []) + (motionSensorsStay ?: [])).
                findAll({ it.currentValue("motion") == "active" });
        debug("Still waiting for ${activeSensors}");
        return true;
    }
    else {
        return false;
    }
}

Boolean eventIndicatesPresence(evt) {
    return (evt.name == "motion" && evt.value == "active") ||
        (evt.name == "contact");
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

