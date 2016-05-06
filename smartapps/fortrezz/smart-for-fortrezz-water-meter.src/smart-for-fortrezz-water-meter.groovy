/**
 *  Leak Detector for FortrezZ Water Meter
 *
 *  Copyright 2016 Daniel Kurin
 *
 */
definition(
    name: "Smart for FortrezZ Water Meter",
    namespace: "fortrezz",
    author: "Daniel Kurin",
    description: "Use the FortrezZ Water Meter to identify leaks in your home's water system.",
    category: "Green Living",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	page(name: "page2", title: "Select device and actions", install: true, uninstall: true)
}

def page2() {
    dynamicPage(name: "page2") {
        section("Choose a water meter to monitor:") {
            input(name: "meter", type: "capability.energyMeter", title: "Water Meter", description: null, required: false, submitOnChange: true)
        }

        if (meter) {
            section {
                app(name: "childRules", appName: "Leak Detector", namespace: "fortrezz", title: "Create New Leak Detector...", multiple: true)
            }
        }
        
        log.debug "there are ${childApps.size()} child smartapps"
        childApps.each {child ->
            log.debug "child app: ${child.settings()}"
        }
        log.debug "Parent Settings: ${settings}"
    }
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
	// TODO: subscribe to attributes, devices, locations, etc.
}

// TODO: implement event handlers