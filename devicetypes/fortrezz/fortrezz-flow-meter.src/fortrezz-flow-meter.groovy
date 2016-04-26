/**
 *  Copyright 2015 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  FortrezZ Flow Meter Device Type Handler
 *
 *  Author: FortrezZ
 *
 *  Date: 2015-08-14
 */
metadata {
	definition (name: "FortrezZ Flow Meter", namespace: "fortrezz", author: "FortrezZ") {
		//capability "Energy Meter"
		capability "Power Meter"
		capability "Configuration"
		capability "Sensor"
        capability "Polling"
        
        attribute "gallons", "number"
        
        attribute "gpm", "number"
        /*attribute "hourgallons", "number"
        attribute "daygallons", "number"
        attribute "weekgallons", "number"*/

        command "zero"

		fingerprint deviceId: "0x1000", inClusters: "0x72,0x86,0x71,0x30,0x31,0x35,0x70,0x85,0x25" //TODO http://docs.smartthings.com/en/latest/device-type-developers-guide/definition-metadata.html#z-wave-fingerprinting
	}

	// simulator metadata
	simulator {
		for (int i = 0; i <= 100; i += 1) {
			status "Pulse:  ${i}": new physicalgraph.zwave.commands.meterpulsev1.MeterPulseReport(
				pulseCount: i).incomingMessage()
		}
		/*for (int i = 0; i <= 100; i += 10) {
			status "energy  ${i} kWh": new physicalgraph.zwave.commands.meterpulsev1.MeterPulseReport(
				scaledMeterValue: i, precision: 3, meterType: 0, scale: 0, size: 4).incomingMessage()
		}*/
	}

	// tile definitions
	tiles {
		valueTile("GPM", "device.gpm", decoration: "flat") {
			state "default", label:'${currentValue} GPM'
		}
		valueTile("Gallons", "device.gallons", decoration: "flat") {
			state "default", label:'${currentValue} Gal'
		}

		main (["GPM"])
		details(["GPM","Gallons", "zero","refresh", "configure"])
		//details(["Gallons", "zero","refresh", "configure"])
	}
    
    preferences {
       input "gallonsPerPulse", "decimal", title: "Gallons Per Pulse",
              description: "Number of Gallons for each meter pulse", defaultValue: 0.1,
              required: false, displayDuringSetup: true
    }
}

def install() {
    //subscribe(theSwitch, "gallons", updateGallonsPerMinute)
    log.debug "Setting up event listeners (install)"
    zero
}

def parse(String description) {
	def result = []
	def cmd = zwave.parse(description)//, [0x31: 1, 0x32: 1, 0x60: 3])
	if (cmd) {
		result = createEvent(zwaveEvent(cmd))
	}
	log.debug "Parse returned ${result?.descriptionText}"
	return result
}

def zwaveEvent(physicalgraph.zwave.commands.meterpulsev1.MeterPulseReport cmd) {
    def lastTime = (state.lastGallonTime ?: 0)
    def lastGallon = (state.lastGallon ?: 0)
    def nowTime = now()
    double gallonsTemp
    def v = cmd.pulseCount as int
    def gpp = Math.round((settings.gallonsPerPulse ?: 1).toFloat() * 1000) / 1000
    def precision = 1 / gpp

	gallonsTemp = ((v * gpp) * precision) / precision

	double gpm = Math.round((gallonsTemp - lastGallon) / ((nowTime - lastTime) / 60000) * 10) / 10
    log.debug "${gpm} Gallons per minute"
	
    state.lastGallonTime = nowTime
    state.lastGallon = gallonsTemp

	//[name: "gallons", value: gallonsTemp, unit: "Gallons"] // Shorthand, apparently
    def evt1 = createEvent(name: "gallons", value: gallonsTemp, unit: "Gallons")
    def evt2 = sendEvent(name: "gpm", value: gpm, unit: "GPM")
    def evt3 = sendEvent(name: "power", value: gpm, unit: "W")
    return evt1
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	// Handles all Z-Wave commands we aren't interested in
    //log.debug cmd
	[:]
}

def configure() {
	log.debug "Running Configure"
	delayBetween([
    	zwave.associationV2.associationSet(groupingIdentifier:5, nodeId:zwaveHubNodeId).format(),
        zwave.configurationV1.configurationSet(parameterNumber: 2, size: 1, scaledConfigurationValue: 0xFF).format()   // Set Pulse to 0
    ])
}

def updated() {
    //subscribe(theSwitch, "gallons", updateGallonsPerMinute)
    log.debug "Setting up event listeners (updated)"
}

def refresh() {
	delayBetween([
		zwave.meterV2.meterGet(scale: 0).format(),
		zwave.meterV2.meterGet(scale: 2).format()
	])
}

def zero() {
	log.debug "Resetting Pulse count"
	return zwave.configurationV1.configurationSet(parameterNumber: 2, size: 1, scaledConfigurationValue: 0xFF).format()   // Set Pulse to 0
}

def poll() {
	log.debug "Polling..."
	zwave.meterPulseV1.MeterPulseGet().format()
}
