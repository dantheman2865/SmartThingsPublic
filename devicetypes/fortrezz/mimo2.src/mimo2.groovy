/**
 *  MIMO2 Device Handler
 *
 *  Copyright 2016 FortrezZ, LLC
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
 */
metadata {
	definition (name: "MIMO2+", namespace: "fortrezz", author: "FortrezZ, LLC") {
		capability "Alarm"
		capability "Contact Sensor"
		capability "Relay Switch"
		capability "Voltage Measurement"
        capability "Configuration"
        capability "Refresh"
        
        attribute "powered", "string"
        attribute "relay2", "string"
        attribute "contact2", "string"
        attribute "voltage2", "string"
        
		command "on"
		command "off"
        command "on2"
        command "off2"
        
        fingerprint deviceId: "0x2100", inClusters: "0x5E,0x86,0x72,0x5A,0x59,0x71,0x98,0x7A"
	}
    
    
	tiles {
        standardTile("relay", "device.switch", width: 2, height: 2, canChangeIcon: true) {
            state "on", label: "Relay 1 On", action: "off", icon: "http://cdn.device-icons.smartthings.com/Lighting/light11-icn@2x.png", backgroundColor: "#53a7c0"
			state "off", label: 'Relay 1 Off', action: "on", icon: "st.switches.switch.off", backgroundColor: "#ffffff"
        }
         standardTile("relay2", "device.switch2", width: 2, height: 2, canChangeIcon: true) {
            state "on2", label: "Relay 2 On", action: "off2", icon: "http://cdn.device-icons.smartthings.com/Lighting/light11-icn@2x.png", backgroundColor: "#53a7c0"
			state "off2", label: 'Relay 2 Off', action: "on2", icon: "st.switches.switch.off", backgroundColor: "#ffffff"
        }
        standardTile("contact", "device.contact", inactiveLabel: false) {
			state "open", label: '${name}', icon: "st.contact.contact.open", backgroundColor: "#ffa81e"
			state "closed", label: '${name}', icon: "st.contact.contact.closed", backgroundColor: "#79b821"
		}
        standardTile("contact2", "device.contact2", inactiveLabel: false) {
			state "open", label: '${name}', icon: "st.contact.contact.open", backgroundColor: "#ffa81e"
			state "closed", label: '${name}', icon: "st.contact.contact.closed", backgroundColor: "#79b821"
		}
        standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat") {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}
        standardTile("powered", "device.powered", inactiveLabel: false) {
			state "powerOn", label: "Power On", icon: "st.switches.switch.on", backgroundColor: "#79b821"
			state "powerOff", label: "Power Off", icon: "st.switches.switch.off", backgroundColor: "#ffa81e"
		}
		standardTile("configure", "device.configure", inactiveLabel: false, decoration: "flat") {
			state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
		}
        valueTile("voltage", "device.voltage") {
            state "val", label:'${currentValue}v', unit:"", defaultState: true
    	}
        valueTile("voltage2", "device.voltage2") {
            state "val", label:'${currentValue}v', unit:"", defaultState: true
    	}
		main (["relay"])
		details(["relay", "contact", "voltage", "relay2", "contact2", "voltage2", "powered", "refresh","configure"])
	}
}

// parse events into attributes
def parse(String description) {
	def result = null
	def cmd = zwave.parse(description)
    	//def cmd = zwave.parse(description, [0x20: 1, 0x25: 1,0x84: 1, 0x30: 1, 0x70: 1, 0x31: 5, 0x60: 3, 0x98: 1])
    //[0x20: BasicSet, 0x25: BinarySwitch 1,0x84: WakeUp , 0x30: sensorBinary, 0x70: configuration, 0x31: sensorMultiLevel, 0x60: multiChannel, 0x98: security])
    //log.debug "command value is: $cmd.CMD"
    
    if (cmd.CMD == "7105") {				//Mimo sent a power loss report
    	log.debug "Device lost power"
    	sendEvent(name: "powered", value: "powerOff", descriptionText: "$device.displayName lost power")
    } else {
    	sendEvent(name: "powered", value: "powerOn", descriptionText: "$device.displayName regained power")
    }
    //log.debug "${device.currentValue('contact')}" // debug message to make sure the contact tile is working
	if (cmd) {
		result = createEvent(zwaveEvent(cmd))
	}
    log.debug "Parse returned ${result?.descriptionText} $cmd.CMD"
	return result
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) // basic set is essentially our digital sensor for SIG1
{
	log.debug "sent a BasicSet command"
    //refresh()  
    delayBetween([zwave.sensorMultilevelV5.sensorMultilevelGet().format()])// requests a report of the anologue input voltage
	return [name: "contact", value: cmd.value ? "open" : "closed"]
    //return [name: "contact", value: cmd.value ? "open" : "closed", type: "digital"]
}

def zwaveEvent(physicalgraph.zwave.commands.sensorbinaryv1.SensorBinaryReport cmd)
{
	log.debug "sent a sensorBinaryReport command"
	refresh()    
	return [name: "contact", value: cmd.value ? "open" : "closed"]
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd)
{
	log.debug "sent a BasicReport command"
	refresh()    
	return [name: "contact", value: cmd.value ? "open" : "closed"]
}
   
def zwaveEvent (physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) // sensorMultilevelReport is used to report the value of the analog voltage for SIG1
{
	log.debug "sent a SensorMultilevelReport"
	def ADCvalue = cmd.scaledSensorValue
   
    return CalculateVoltage(cmd.scaledSensorValue)
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    //def encapsulatedCommand = cmd.encapsulatedCommand()
    def encapsulatedCommand = cmd.encapsulatedCommand()
    // can specify command class versions here like in zwave.parse
    if (encapsulatedCommand) {
        return zwaveEvent(encapsulatedCommand)
    }
}

// MultiChannelCmdEncap and MultiInstanceCmdEncap are ways that devices
// can indicate that a message is coming from one of multiple subdevices
// or "endpoints" that would otherwise be indistinguishable
def zwaveEvent(physicalgraph.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
    //def encapsulatedCommand = cmd.encapsulatedCommand()
    def encapsulatedCommand = cmd.encapsulatedCommand()

    // can specify command class versions here like in zwave.parse
    log.debug ("Command from endpoint ${cmd.sourceEndPoint}: ${encapsulatedCommand}")

    if (encapsulatedCommand) {
        return zwaveEvent(encapsulatedCommand)
    }
}
/*
def zwaveEvent(physicalgraph.zwave.commands.multichannelv3.MultiInstanceCmdEncap cmd) {
        //def encapsulatedCommand = cmd.encapsulatedCommand()
        def encapsulatedCommand = cmd.encapsulatedCommand([0x20: 1, 0x25: 1,0x84: 1, 0x30: 1, 0x70: 1, 0x31: 5, 0x60: 3, 0x98: 1])

        // can specify command class versions here like in zwave.parse
        log.debug ("Command from instance ${cmd.instance}: ${encapsulatedCommand}")

        if (encapsulatedCommand) {
                return zwaveEvent(encapsulatedCommand)
        }
}
*/
def zwaveEvent(physicalgraph.zwave.Command cmd) {
	// Handles all Z-Wave commands we aren't interested in
     log.debug("Un-parsed Z-Wave message ${cmd}")
	return [:]
}

def zwaveEvent(physicalgraph.zwave.commands.multichannelv3.MultiChannelCapabilityReport cmd)
{
	//log.debug "$cmd.endPoint"
    log.debug "mccr"
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityCommandsSupportedReport cmd)
{
    log.debug "SCSR"
}

def CalculateVoltage(ADCvalue)
{
	 def map = [:]
     
     def volt = (((6.60*(10**-17))*(ADCvalue**5)) - ((5.46*(10**-13))*(ADCvalue**4)) + ((1.77*(10**-9))*(ADCvalue**3)) - ((2.07*(10**-6))*(ADCvalue**2)) + ((1.57*(10**-3))*(ADCvalue)) - (5.53*(10**-3)))

    //def volt = (((3.19*(10**-16))*(ADCvalue**5)) - ((2.18*(10**-12))*(ADCvalue**4)) + ((5.47*(10**-9))*(ADCvalue**3)) - ((5.68*(10**-6))*(ADCvalue**2)) + (0.0028*ADCvalue) - (0.0293))
	//log.debug "$cmd.scale $cmd.precision $cmd.size $cmd.sensorType $cmd.sensorValue $cmd.scaledSensorValue"
	def voltResult = volt.round(1)// + "v"
    
	map.name = "voltage"
    map.value = voltResult
    map.unit = "v"
    return map
}
	

def configure() {
	log.debug "Configuring...." //setting up to monitor power alarm and actuator duration
    //log.debug ("Configuration Complete!")
    return delayBetween([
    	encap(zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:[zwaveHubNodeId]), 0),
    	encap(zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:[zwaveHubNodeId]), 1),
    	encap(zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:[zwaveHubNodeId]), 2),
        encap(zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:[zwaveHubNodeId]), 3),
        encap(zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:[zwaveHubNodeId]), 4),
        secure(zwave.configurationV1.configurationSet(configurationValue: [0x00], parameterNumber: 1, size: 1)),
        secure(zwave.configurationV1.configurationSet(configurationValue: [0x00], parameterNumber: 2, size: 1)),
        secure(zwave.configurationV1.configurationSet(configurationValue: [0x01], parameterNumber: 3, size: 1)),
        secure(zwave.configurationV1.configurationSet(configurationValue: [0x01], parameterNumber: 9, size: 1))
    ], 200)
    
	//delayBetween([
	//	zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:[zwaveHubNodeId]).format(), // 	FYI: Group 3: If a power dropout occurs, the MIMOlite will send an Alarm Command Class report 
    //    																							//	(if there is enough available residual power)
    //    zwave.associationV1.associationSet(groupingIdentifier:2, nodeId:[zwaveHubNodeId]).format(), // periodically send a multilevel sensor report of the ADC analog voltage to the input
    //    zwave.associationV1.associationSet(groupingIdentifier:4, nodeId:[zwaveHubNodeId]).format(), // when the input is digitally triggered or untriggered, snd a binary sensor report
    //    zwave.configurationV1.configurationSet(configurationValue: [0], parameterNumber: 11, size: 1).format() // configurationValue for parameterNumber means how many 100ms do you want the relay
    //    																										// to wait before it cycles again / size should just be 1 (for 1 byte.)
    //    //zwave.configurationV1.configurationGet(parameterNumber: 11).format() // gets the new parameter changes. not currently needed. (forces a null return value without a zwaveEvent funciton
	//])
}

def on() {
	//delayBetween([
	//	sourceEndPoinzwave.multiChannelV3.multiChannelCmdEncap(t:3, destinationEndPoint: 3, commandClass:37, command:1, parameter:[255]).format(),
	//	zwave.multiChannelV3.multiChannelCmdEncap(sourceEndPoint:3, destinationEndPoint: 3, commandClass:37, command:2).format()
	//])
        	// physically changes the relay from on to off and requests a report of the relay
    log.debug "on1"
    delayBetween([
        	//zwave.basicV1.basicSet(value: 0xFF).format(),
        	//zwave.basicV1.basicGet().format()
        encap(zwave.basicV1.basicSet(value: 0xFF), 3)
    ],200)
}

def off() {
	//delayBetween([
	//	zwave.multiChannelV3.multiChannelCmdEncap(sourceEndPoint:3, destinationEndPoint: 3, commandClass:37, command:1, parameter:[0]).format(),
	//	zwave.multiChannelV3.multiChannelCmdEncap(sourceEndPoint:3, destinationEndPoint: 3, commandClass:37, command:2).format()
	//])
    encap(zwave.basicV1.basicSet(value: 0x00), 3)	// physically changes the relay from on to off and requests a report of the relay
    log.debug "off1"
    return delayBetween([
        zwave.basicV1.basicSet(value: 0x00).format(),
        zwave.basicV1.basicGet().format()
    ],200)
}

def on2() {
		//command(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: 4).encapsulate(zwave.basicV1.basicSet(value: 0xFF)))
		log.debug "on2"
		//encap(zwave.basicV1.basicSet(value: 0xFF), 4)	// physically changes the relay from on to off and requests a report of the relay
       // refresh()// to make sure that it changed (the report is used elsewhere, look for switchBinaryReport()
       //sendEvent(name: "relay2", value: "on2")
    return delayBetween([
		secure(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: 4, commandClass:0x20, command:1, parameter:[255])),
		secure(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: 4, commandClass:0x20, command:2))
	],200)
}

def off2() {
		//command(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: 4).encapsulate(zwave.basicV1.basicSet(value: 0x00)))
		//log.debug "off2"
		//encap(zwave.basicV1.basicSet(value: 0x00), 4)	// physically changes the relay from on to off and requests a report of the relay
       // refresh()// to make sure that it changed (the report is used elsewhere, look for switchBinaryReport()
       //sendEvent(name: "relay2", value: "off2")
    log.debug "off2"
    return delayBetween([
		secure(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: 4, commandClass:0x20, command:1, parameter:[0])),
		secure(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: 4, commandClass:0x20, command:2))
	],200)
}

def refresh() {
//log.debug "REFRESH!"
    log.debug "REFRESH!"
	return secureSequence([
		zwave.securityV1.securityCommandsSupportedGet(),
		zwave.securityV1.securityCommandsSupportedGet(),
        zwave.sensorMultilevelV5.sensorMultilevelGet(),
        zwave.switchBinaryV1.switchBinaryGet(), //requests a report of the relay to make sure that it changed (the report is used elsewhere, look for switchBinaryReport()
        zwave.switchBinaryV1.switchBinaryGet(), //requests a report of the relay to make sure that it changed (the report is used elsewhere, look for switchBinaryReport()

        zwave.sensorMultilevelV5.sensorMultilevelGet(),// requests a report of the anologue input voltage
        zwave.sensorMultilevelV5.sensorMultilevelGet(),
        zwave.multiChannelV3.multiChannelEndPointGet(),
        zwave.multiChannelV3.multiChannelEndPointGet(),
        ]) + ["delay 2000", zwave.wakeUpV1.wakeUpNoMoreInformation().format()]
}


def both() {
	log.debug "Executing 'both'"
	// TODO: handle 'both' command
}

def enableEpEvents() {
	log.debug "Executing 'enableEpEvents'"
	// TODO: handle 'enableEpEvents' command
}

def epCmd() {
	log.debug "Executing 'epCmd'"
	// TODO: handle 'epCmd' command
}

private secure(physicalgraph.zwave.Command cmd) {
	return zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
}

private secureSequence(commands, delay=200) {
	return delayBetween(commands.collect{ secure(it) }, delay)
}

private encap(cmd, endpoint) {
	//log.debug "before encapsulate"
	if (endpoint) {
		//command(zwave.multiChannelV3.multiChannelCmdEncap(sourceEndPoint: endpoint, destinationEndPoint: endpoint).encapsulate(cmd))
		return secure(zwave.multiChannelV3.multiChannelCmdEncap(bitAddress: false, sourceEndPoint:0, destinationEndPoint: endpoint).encapsulate(cmd))

	} else {
		return secure(cmd)
	}
}