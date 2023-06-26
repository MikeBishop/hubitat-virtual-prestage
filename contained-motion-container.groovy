/*
    Filtered Device Mirror
    Copyright 2022 Mike Bishop,  All Rights Reserved
*/

metadata {
    definition (
        name: "Contained Motion Container",
        namespace: "evequefou",
        author: "Mike Bishop",
        importUrl: "https://raw.githubusercontent.com/MikeBishop/hubitat-virtual-prestage/main/contained-motion-container.groovy"
    ) {
        capability "Initialize"
    }
    preferences {
        input(
            name: "debugLogging",
            type: "bool",
            title: "Enable debug logging",
            required: false,
            default: false
        )
    }
}

void initialize() {}

void parse(String description) { log.warn "parse(String description) not implemented" }

void parse(List description) {
    description.each {
        debug "Processing ${it}"
        def rootId = device.deviceNetworkId
        def childId = "${rootId}-${it.id}"
        def childLabel = "${it.zone} Occupied"
        def cd = getChildDevice(childId)
        if (!cd) {
            // Child device doesn't exist; need to create it.
            debug "Creating ${childLabel} (${childId})"
            cd = addChildDevice("hubitat", "Generic Component Motion Sensor", childId, [isComponent: true])
        }
        else {
            debug "${childId} is ${cd}"
        }
        if (cd.getLabel() != childLabel ) {
            cd.setLabel(childLabel)
        }
        cd.parse([it])
    }
}

void delete(String id) {
    deleteChildDevice(device.deviceNetworkId + "-" + id);
}

def debug(msg) {
	if (debugLogging) {
    	log.debug msg
    }
}
