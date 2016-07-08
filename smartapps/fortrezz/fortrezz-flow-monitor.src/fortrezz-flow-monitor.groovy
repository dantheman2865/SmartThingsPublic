/**
 *  Flow Monitor
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
definition(
    name: "FortrezZ Flow Monitor",
    namespace: "fortrezz",
    author: "Daniel Kurin",
    description: "Watches a simple Water meter for the cumulative gallons and calculates Gallons Per Hour/Day/Week/Month",
    category: "Green Living",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	page(name: "main", title: "Main", nextPage: "thingspeakPage", uninstall: true) {
        section("Choose a meter") {
            input(name: "meter", type: "capability.powerMeter", title: "Monitor This Water Meter...", required: true, multiple: false, description: null)
        }
    }

	page(name: "thingspeakPage", title: "Database Settings", nextPage: "notificationsPage", uninstall: true)
    
    page(name: "notificationsPage", title: "Notifications", install: true)
}

def thingspeakPage() {
	dynamicPage(name: "thingspeakPage") {
        section ("ThinkSpeak User Information") {
        	href(name: "hrefWithImage", title: "This plugin uses the Thingspeak service for data logging.",
             description: "Click here to log in or create a free account. Retrieve the API key from \"My Account\"",
             style: "external",
             required: false,
             url: "https://thingspeak.com/account/")
            input("userKey", "text", title: "User's API Key", required: true)
        }
        section ("Channel Information") {
            paragraph "The App will create a new Channel by default. If you would like to use an existing Channel, enter the ID and key here:"
            input(name: "channelID", type: "number", title: "Channel ID", required: false, description: state.channelID)
            input(name: "channelKey", type: "text", title: "Channel key", required: false, description: state.channelKey)
        }
    }
}

def notificationsPage() {
	dynamicPage(name: "notificationsPage") {
        section ("Notify me when...") {
            input(name: "minuteNotify", type: "bool", title: "Gallons per minute goes above...", submitOnChange: true)
            if (minuteNotify) {
                input(name: "minuteThresh", title: "", type: "decimal", description: "number of Gallons")
            }
            input(name: "hourNotify", type: "bool", title: "Gallons per hour goes above...", submitOnChange: true)
            if (hourNotify) {
                input(name: "hourThresh", title: "", type: "decimal", description: "number of Gallons")
            }
            input(name: "dayNotify", type: "bool", title: "Gallons per day goes above...", submitOnChange: true)
            if (dayNotify) {
                input(name: "dayThresh", title: "", type: "decimal", description: "number of Gallons")
            }
            input(name: "weekNotify", type: "bool", title: "Gallons per week goes above...", submitOnChange: true)
            if (weekNotify) {
                input(name: "weekThresh", title: "", type: "decimal", description: "number of Gallons")
            }
            input(name: "monthNotify", type: "bool", title: "Gallons per month goes above...", submitOnChange: true)
            if (monthNotify) {
                input(name: "monthThresh", title: "", type: "decimal", description: "number of Gallons")
            }
        }
    }
}

def installed() {
	log.debug "Installed with settings: ${settings}"
    log.debug "Channel ID: ${channelID} and Key: ${channelKey}"
    
    if (channelID != null || channelKey != null) {
    	state.channelID = channelID
        state.channelKey = channelKey.toUpperCase()
    } else {
    	createChannel()
    }
    
	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"
    
    if (channelID != null || channelKey != null) {
    	state.channelID = channelID
        state.channelKey = channelKey.toUpperCase()
    }
	
    unsubscribe()
	initialize()
}

def initialize() {
	// TODO: subscribe to attributes, devices, locations, etc.
    subscribe(meter, "gallons", handleGallonEvent)
    //subscribe(meter, "gpm", handleGPMEvent)

	updateChannelInfo()
}

// Call every 10 minutes to check the totals
def checkThreshholds() {
	if(hourNotify) {
    	def hourTotal = getSum('h')
        if(hourTotal > hourThresh)
        {
        	sendPush("The ${door.displayName} is open!") // DeviceName is over the hourly threshhold. The value is ${hourTotal}
        }
    }
}

def handleGallonEvent(evt) {
    log.debug "Logging Gallons event" 
	//logField(evt,"Gallons") { it.toString() }
    logMoreFields([["Gallons", evt.value],["GPM", meter.currentGpm]]) { it.toString() }
}

def handleGPMEvent(evt) {
    log.debug "Logging GPM event" 
	logField(evt,"GPM") { it.toString() }
}

private createChannel() {
    def params = [
        uri: "https://api.thingspeak.com",
        path: "/channels.json",
        query: [
            api_key: userKey,
            description: "Channel auto-created by the FortrezZ Flow Monitor SmartApp",
            name: "Gallons over time",
            field1: "GPM",
            field2: "Gallons"
        ]
    ]

    try {
        httpPost(params) { resp ->
            resp.headers.each {
               //log.debug "${it.name} : ${it.value}"
            }
            log.debug "response contentType: ${resp.contentType}"
            log.debug "response data: ${resp.data}"
            log.debug "Channel ID: ${resp.data.id}"
            state.channelID = resp.data.id
            resp.data.api_keys.each {
            	log.debug "${it.api_key}, writable: ${it.write_flag}"
                if (it.write_flag) {
                	state.channelKey = it.api_key
                }
            }
        }
    } catch (e) {
        log.error "something went wrong: $e"
    }
}

private getFieldMap(channelInfo) {
    def fieldMap = [:]
    channelInfo?.findAll { it.key?.startsWith("field") }.each { fieldMap[it.value?.trim()] = it.key }
    log.debug "Channel info is ${fieldMap}"
    return fieldMap
}

private updateChannelInfo() {
    log.debug "Retrieving channel info for ${state.channelID}"

    def url = "http://api.thingspeak.com/channels/${state.channelID}/feed.json?key=${state.channelKey}&results=0"
    httpGet(url) {
        response ->
        if (response.status != 200 ) {
            log.debug "ThingSpeak data retrieval failed, status = ${response.status}"
        } else {
            state.channelInfo = response.data?.channel
        }
    }

    state.fieldMap = getFieldMap(state.channelInfo)
}

private logField(evt, field, Closure c) {
    def deviceName = evt.displayName.trim() + '.' + field
    def fieldNum = state.fieldMap[field]
    log.debug "fieldNum: ${fieldNum}"

    if (!fieldNum) {
        log.debug "Device '${deviceName}' has no field"
        return
    }
    def value = c(evt.value)
    log.debug "Logging to channel ${state.channelID}, ${fieldNum}, value ${value}"

    def url = "http://api.thingspeak.com/update?key=${state.channelKey}&${fieldNum}=${value}"
    httpGet(url) { 
        response -> 
        if (response.status != 200 ) {
            log.debug "ThingSpeak logging failed, status = ${response.status}"
        }
    }
}

private logMoreFields(data, Closure c) {
    def url = "http://api.thingspeak.com/update?key=${state.channelKey}"
    def logString = "Logging to channel ${state.channelID}"
    
    data.each {
    	def fieldNum = state.fieldMap[it[0]]
        def value = it[1]
        if (!fieldNum) {
            log.debug "Device '${deviceName}' has no field"
            return
        } else {
            url += "&${fieldNum}=${value}"
            logString += ", ${fieldNum}: value ${value}"
        }
    }
    log.debug "${logString}"

    httpGet(url) { 
        response -> 
        if (response.status != 200 ) {
            log.debug "ThingSpeak logging failed, status = ${response.status}"
        }
    }
}

// Use ThingSpeak API parameters: result=1 and end=date/time
private getSum(range) {
    def lastValue
    def params1 = [
        uri: "https://api.thingspeak.com",
        path: "/channels/${state.channelID}/fields/${state.fieldMap["Gallons"].reverse().take(1)}/last.json",
        query: [
            api_key: state.channelKey
        ]
    ]

    try {
        httpPost(params1) { resp ->
            lastValue = resp.data[state.fieldMap["Gallons"]]
            log.debug "Most recent gallon value: ${lastValue}"
        }
    } catch (e) {
        log.error "something went wrong getting last value: $e"
    }

	def apiPath = "/channels/${state.channelID}/fields/${state.fieldMap["Gallons"].reverse().take(1)}.json"
    def params2 = [
        uri: "https://api.thingspeak.com",
        path: apiPath,
        query: [
            api_key: state.channelKey,
            description: "Channel auto-created by the FortrezZ Flow Monitor SmartApp",
            name: "Gallons over time",
            field1: "GPM",
            field2: "Gallons"
        ]
    ]

    try {
        httpPost(params2) { resp ->
            resp.headers.each {
               //log.debug "${it.name} : ${it.value}"
            }
            log.debug "response contentType: ${resp.contentType}"
            log.debug "response data: ${resp.data}"
            log.debug "Channel ID: ${resp.data.id}"
            state.channelID = resp.data.id
            resp.data.api_keys.each {
            	log.debug "${it.api_key}, writable: ${it.write_flag}"
                if (it.write_flag) {
                	state.channelKey = it.api_key
                }
            }
        }
    } catch (e) {
        log.error "something went wrong: $e"
    }
}