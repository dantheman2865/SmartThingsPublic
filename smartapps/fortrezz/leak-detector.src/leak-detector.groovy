/**
 *  Leak Detector
 *
 *  Copyright 2016 Daniel Kurin
 *
 */
definition(
    name: "Leak Detector",
    namespace: "fortrezz",
    author: "Daniel Kurin",
    description: "Child SmartApp for leak detector rules",
    category: "Green Living",
    parent: "fortrezz:Smart for FortrezZ Water Meter",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	page(name: "prefsPage", title: "Choose the detector behavior", install: true, uninstall: true)

    // Do something here like update a message on the screen,
    // or introduce more inputs. submitOnChange will refresh
    // the page and allow the user to see the changes immediately.
    // For example, you could prompt for the level of the dimmers
    // if dimmers have been selected:
    //log.debug "Child Settings: ${settings}"
}

def prefsPage() {
    dynamicPage(name: "prefsPage") {
        section("Set Leak Threshold by...") {
            input(name: "type", type: "enum", title: "Type...", submitOnChange: true, options: ruleTypes())
        }

        if(type)
        {
            switch (type) {
                case "Mode":
                    section("Threshold settings") {
                        input(name: "ruleName", type: "text", title: "Rule Name", required: false)
                        input(name: "gpm", type: "decimal", title: "GPM exceeds", required: true)
                    }
                    section ("During") {
                        input(name: "modes", type: "mode", title: "select a mode(s)", multiple: true, required: true)
                    }
                    break

                case "Time Period":
                    section("Threshold settings") {
                        input(name: "ruleName", type: "text", title: "Rule Name", required: false)
                        input(name: "gpm", type: "decimal", title: "GPM exceeds", required: true)
                    }
                    section("Between...") {
                    	input(name: "startTime", type: "time", title: "Start Time", required: true)
                    }
                    section("...and...") {
                    	input(name: "endTime", type: "time", title: "End Time", required: true)
                    }
                    break

                case "Accumulated Flow":
                    section("Threshold settings") {
                        input(name: "ruleName", type: "text", title: "Rule Name", required: false)
                        input(name: "gallons", type: "number", title: "Total Gallons exceeds", required: true)
                    }
                    section("Between...") {
                    	input(name: "startTime", type: "time", title: "Start Time", required: true)
                    }
                    section("...and...") {
                    	input(name: "endTime", type: "time", title: "End Time", required: true)
                    }
                    break

                case "Constant Flow":
                    section("Threshold settings") {
                        input(name: "ruleName", type: "text", title: "Rule Name", required: false)
                    }
                    section () {
	                    input(name: "flowHours", type: "number", title: "Hours of constant flow", required: true, defaultValue: 2)
                    }
                    break

                case "Valve Status":
                    section("Threshold settings") {
                        input(name: "ruleName", type: "text", title: "Rule Name", required: false)
                        input(name: "gpm", type: "decimal", title: "GPM exceeds", required: true, defaultValue: 0.1)
                    }
                    section ("If...") {
                        input(name: "valve", type: "capability.valve", title: "Choose a valve", required: true)
                    }
                    section ("...is...") {
                        input(name: "valveStatus", type: "enum", title: "Status", options: ["Open","Closed"], required: true)
                    }
                    break

                case "Switch Status":
                    section("Threshold settings") {
                        input(name: "ruleName", type: "text", title: "Rule Name", required: false)
                        input(name: "gpm", type: "decimal", title: "GPM exceeds", required: true, defaultValue: 0.1)
                    }
                    section ("If...") {
                        input(name: "valve", type: "capability.switch", title: "Choose a switch", required: true)
                    }
                    section ("...is...") {
                        input(name: "switchStatus", type: "enum", title: "Status", options: ["On","Off"], required: true)
                    }
                    break

                default:
                    break
            }
        }
    }
}

def ruleTypes() {
	def types = []
    types << "Mode"
    types << "Time Period"
    types << "Accumulated Flow"
    types << "Constant Flow"
    types << "Valve Status"
    types << "Switch Status"
    
    return types
}

def installed() {
	log.debug "Installed with settings: ${settings}"
	app.updateLabel("${type}: ${ruleName ? ruleName : ""}")
    
	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"
    app.updateLabel("${type}: ${ruleName ? ruleName : ""}")

	unsubscribe()
	initialize()
}

def settings() {
	return settings
}

def initialize() {
	// TODO: subscribe to attributes, devices, locations, etc.
}

// TODO: implement event handlers