/**
 *  FortrezZ Flow Meter Interface
 *
 *  Copyright 2016 Daniel Kurin
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
	definition (name: "FortrezZ Flow Meter Interface", namespace: "fortrezz", author: "Daniel Kurin") {
		capability "Battery"
		capability "Energy Meter"
		capability "Image Capture"
		capability "Temperature Measurement"
        capability "Sensor"
        
        attribute "gpm", "number"
        attribute "alarmState", "string"
        attribute "chartMode", "string"
        
        command "chartMode"

	    fingerprint deviceId: "0x2101", inClusters: "0x5E, 0x86, 0x72, 0x5A, 0x73, 0x71, 0x85, 0x59, 0x32, 0x31, 0x70, 0x80, 0x7A"
	}

	simulator {
		// TODO: define status and reply messages here
	}

	tiles(scale: 2) {
    	carouselTile("flowHistory", "device.image", width: 6, height: 3) { }
		valueTile("battery", "device.battery", inactiveLabel: false, width: 2, height: 2) {
			state "battery", label:'${currentValue}%\nBattery', unit:""
		}
		valueTile("temperature", "device.temperature", width: 2, height: 2) {
            state("temperature", label:'${currentValue}°',
                backgroundColors:[
                    [value: 31, color: "#153591"],
                    [value: 44, color: "#1e9cbb"],
                    [value: 59, color: "#90d2a7"],
                    [value: 74, color: "#44b621"],
                    [value: 84, color: "#f1d801"],
                    [value: 95, color: "#d04e00"],
                    [value: 96, color: "#bc2323"]
                ]
            )
        }
        valueTile("gpm", "device.gpm", inactiveLabel: false, width: 2, height: 2) {
			state "gpm", label:'${currentValue}gpm', unit:""
		}
		standardTile("powerState", "device.powerState", width: 2, height: 2) {
			state "reconnected", icon:"http://swiftlet.technology/wp-content/uploads/2016/02/Connected-64.png", backgroundColor:"#cccccc"
			state "disconnected", icon:"http://swiftlet.technology/wp-content/uploads/2016/02/Disconnected-64.png", backgroundColor:"#cc0000"
		}
		standardTile("waterState", "device.waterState", width: 2, height: 2, canChangeIcon: true) {
			state "none", icon:"http://swiftlet.technology/wp-content/uploads/2016/02/Water-64.png", backgroundColor:"#cccccc", label: "No Flow"
			state "flow", icon:"http://swiftlet.technology/wp-content/uploads/2016/02/Water-64.png", backgroundColor:"#53a7c0", label: "Flow"
			state "overflow", icon:"http://swiftlet.technology/wp-content/uploads/2016/02/Water-64.png", backgroundColor:"#cc0000", label: "High Flow"
		}
		standardTile("heatState", "device.heatState", width: 2, height: 2) {
			state "normal", label:'Normal', icon:"st.alarm.temperature.normal", backgroundColor:"#ffffff"
			state "freezing", label:'Freezing', icon:"st.alarm.temperature.freeze", backgroundColor:"#2eb82e"
			state "overheated", label:'Overheated', icon:"st.alarm.temperature.overheat", backgroundColor:"#F80000"
		}
        valueTile("take1", "device.image", width: 2, height: 2, canChangeIcon: false, inactiveLabel: true, canChangeBackground: false, decoration: "flat") {
            state "take", label: "", action: "Image Capture.take", nextState:"taking", icon: "st.secondary.refresh"
        }
		valueTile("chartMode", "device.chartMode", width: 2, height: 2, canChangeIcon: false, canChangeBackground: false, decoration: "flat") {
			state "day", label:'Chart View:\n24 Hours\n(press to change)', nextState: "week", action: 'chartMode'
			state "week", label:'Chart View:\n7 Days\n(press to change)', nextState: "month", action: 'chartMode'
			state "month", label:'Chart View:\n4 Weeks\n(press to change)', nextState: "day", action: 'chartMode'
		}
		main (["waterState"])
		details(["flowHistory", "take1", "battery", "temperature", "gpm", "waterState", "chartMode"])
	}
    
}

// parse events into attributes
def parse(String description) {
	def results = []
	if (description.startsWith("Err")) {
	    results << createEvent(descriptionText:description, displayed:true)
	} else {
		def cmd = zwave.parse(description, [ 0x80: 1, 0x84: 1, 0x71: 2, 0x72: 1 ])
		if (cmd) {
			results << createEvent( zwaveEvent(cmd) )
		}
	}
	//log.debug "\"$description\" parsed to ${results.inspect()}"
	log.debug "zwave parsed to ${results.inspect()}"
	return results
}

def take() {
	def mode = device.currentValue("chartMode")
    if(mode == "day")
    {
    	take1()
    }
    else if(mode == "week")
    {
    	take7()
    }
    else if(mode == "month")
    {
    	take28()
    }
}

def chartMode(string) {
	def state = device.currentValue("chartMode")
    def tempValue = ""
	switch(state)
    {
    	case "day":
        	tempValue = "week"
            break
        
        case "week":
        	tempValue = "month"
            break
            
        case "month":
        	tempValue = "day"
            break
            
        default:
        	tempValue = "day"
            break
    }
	sendEvent(name: "chartMode", value: tempValue)
}

def take1() {
    api("24hrs", "") {
        log.debug("Image captured")

        if(it.headers.'Content-Type'.contains("image/png")) {
            if(it.data) {
                storeImage(getPictureName("24hrs"), it.data)
            }
        }
    }
}

def take7() {
    api("7days", "") {
        log.debug("Image captured")

        if(it.headers.'Content-Type'.contains("image/png")) {
            if(it.data) {
                storeImage(getPictureName("7days"), it.data)
            }
        }
    }
}

def take28() {
    api("4weeks", "") {
        log.debug("Image captured")

        if(it.headers.'Content-Type'.contains("image/png")) {
            if(it.data) {
                storeImage(getPictureName("4weeks"), it.data)
            }
        }
    }
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd)
{
	def map = [:]
	if(cmd.sensorType == 1) {
		map = [name: "temperature"]
        if(cmd.scale == 0) {
        	map.value = getTemperature(cmd.scaledSensorValue)
        } else {
	        map.value = cmd.scaledSensorValue
        }
        map.unit = location.temperatureScale
	} else if(cmd.sensorType == 2) {
    	map = [name: "waterState"]
        if(cmd.sensorValue[0] == 0x80) {
        	map.value = "flow"
        } else if(cmd.sensorValue[0] == 0x00) {
	        map.value = "none"
        } else if(cmd.sensorValue[0] == 0xFF) {
	        map.value = "overflow"
            sendAlarm("waterOverflow")
        }
	}
	map
}

def zwaveEvent(physicalgraph.zwave.commands.meterv3.MeterReport cmd)
{
	def map = [:]
    map.name = "gpm"
    map.value = cmd.scaledMeterValue - cmd.scaledPreviousMeterValue
    map.unit = "gpm"
    sendDataToCloud(cmd.scaledMeterValue - cmd.scaledPreviousMeterValue)
    
	map
}

def zwaveEvent(physicalgraph.zwave.commands.alarmv2.AlarmReport cmd)
{
	def map = [:]
    if (cmd.zwaveAlarmType == 8) // Power Alarm
    {
    	map.name = "powerState"
        if (cmd.zwaveAlarmEvent == 2) // AC Mains Disconnected
        {
            map.value = "disconnected"
            sendAlarm("acMainsDisconnected")
        }
        else if (cmd.zwaveAlarmEvent == 3) // AC Mains Reconnected
        {
            map.value = "reconnected"
            sendAlarm("acMainsReconnected")
        }
        else if (cmd.zwaveAlarmEvent == 0x0B) // Replace Battery Now
        {
            map.value = "reconnected"
            sendAlarm("replaceBatteryNow")
        }
    }
    else if (cmd.zwaveAlarmType == 4) // Heat Alarm
    {
    	map.name = "heatState"
        if (cmd.zwaveAlarmEvent == 0) // Normal
        {
            map.value = "normal"
        }
        else if (cmd.zwaveAlarmEvent == 1) // Overheat
        {
            map.value = "overheated"
            sendAlarm("tempOverheated")
        }
        else if (cmd.zwaveAlarmEvent == 5) // Underheat
        {
            map.value = "freezing"
            sendAlarm("tempFreezing")
        }
    }
    else if (cmd.zwaveAlarmType == 5) // Water Alarm
    {
    	map.name = "waterState"
        if (cmd.zwaveAlarmEvent == 0) // Normal
        {
            map.value = "none"
        }
        else if (cmd.zwaveAlarmEvent == 6) // Flow Detected
        {
        	if(cmd.eventParameter[0] == 2)
            {
                map.value = "flow"
            }
            else if(cmd.eventParameter[0] == 3)
            {
            	map.value = "overflow"
                sendAlarm("waterOverflow")
            }
        }
    }
    //log.debug "alarmV2: $cmd"
    
	map
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	def map = [:]
	if(cmd.batteryLevel == 0xFF) {
		map.name = "battery"
		map.value = 1
		map.descriptionText = "${device.displayName} has a low battery"
		map.displayed = true
	} else {
		map.name = "battery"
		map.value = cmd.batteryLevel > 0 ? cmd.batteryLevel.toString() : 1
		map.unit = "%"
		map.displayed = false
	}
	map
}

def zwaveEvent(physicalgraph.zwave.Command cmd)
{
	log.debug "COMMAND CLASS: $cmd"
}

def sendDataToCloud(double data)
{
    def params = [
        uri: "http://iot.swiftlet.technology:1880",
        path: "/fmi",
        body: [
            id: device.id,
            value: data
        ]
    ]

    try {
        httpPostJson(params) { resp ->
            resp.headers.each {
                //log.debug "${it.name} : ${it.value}"
            }
            log.debug "response contentType: ${resp.    contentType}"
        }
    } catch (e) {
        log.debug "something went wrong: $e"
    }
}

def getTemperature(value) {
	if(location.temperatureScale == "C"){
		return value
    } else {
        return Math.round(celsiusToFahrenheit(value))
    }
}

private getPictureName(category) {
  //def pictureUuid = device.id.toString().replaceAll('-', '')
  def pictureUuid = java.util.UUID.randomUUID().toString().replaceAll('-', '')

  def name = "image" + "_$pictureUuid" + "_" + category + ".png"
  name
}

def api(method, args = [], success = {}) {
  def methods = [
    //"snapshot":        [uri: "http://${ip}:${port}/snapshot.cgi${login()}&${args}",        type: "post"],
    "24hrs":      [uri: "http://iot.swiftlet.technology/fortrezz/chart.php?uuid=${device.id}&tz=${location.timeZone.ID}&type=1", type: "get"],
    "7days":      [uri: "http://iot.swiftlet.technology/fortrezz/chart.php?uuid=${device.id}&tz=${location.timeZone.ID}&type=2", type: "get"],
    "4weeks":     [uri: "http://iot.swiftlet.technology/fortrezz/chart.php?uuid=${device.id}&tz=${location.timeZone.ID}&type=3", type: "get"],
  ]

  def request = methods.getAt(method)

  doRequest(request.uri, request.type, success)
}

private doRequest(uri, type, success) {
  log.debug(uri)

  if(type == "post") {
    httpPost(uri , "", success)
  }

  else if(type == "get") {
    httpGet(uri, success)
  }
}

def sendAlarm(text)
{
	sendEvent(name: "alarmState", value: text, descriptionText: text, displayed: false)
}

