/**
 *  Leak Detector for FortrezZ Water Meter
 *
 *  Copyright 2016 Daniel Kurin
 *
 */
definition(
    name: "Leak Detector for FortrezZ Water Meter",
    namespace: "fortrezz",
    author: "Daniel Kurin",
    description: "Use the FortrezZ Water Meter to identify leaks in your home's water system.",
    category: "Green Living",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
    page(name: "examplePage")
}

def examplePage() {
    dynamicPage(name: "examplePage", title: "", install: true, uninstall: true) {

        section {
            input(name: "meter", type: "capability.energyMeter", title: "Water Meter", description: null, required: false, submitOnChange: true)
        }

        if (meter) {
        	log.debug state.numRules
        	if(state.rules == null)
            {
               state.rules = [:]
            }
            
            for (rule in state.rules) {
            	if (rule == null)
                {
					rule = [value: settings["rule${rule.name}"]]
                }
                else
                {
                	rule = [name: Math.abs(new Random().nextInt() % 1000) + 1, value: settings["rule${rule.name}"]]
                }
            }
            log.debug "Rules: ${state.rules}, Size: ${state.rules.size}"
            
            for (rule in state.rules) {
            	log.debug rule
                section("Rule: ${rule}") {
                    input(name: "rule${rule}", type: "enum", title: "Color", submitOnChange: true, options: ["Red","Green","Blue","Yellow"], defaultValue: rule.value.name)
                }
            }
            
            if(color0)
            {
            	state.numRules = state.numRules + 1
            }
        }

        // Do something here like update a message on the screen,
        // or introduce more inputs. submitOnChange will refresh
        // the page and allow the user to see the changes immediately.
        // For example, you could prompt for the level of the dimmers
        // if dimmers have been selected:
        log.debug "Settings: ${settings}"
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