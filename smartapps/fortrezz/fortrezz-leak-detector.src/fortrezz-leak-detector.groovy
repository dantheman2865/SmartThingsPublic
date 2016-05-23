/**
 *  Leak Detector for FortrezZ Water Meter
 *
 *  Copyright 2016 Daniel Kurin
 *
 */
definition(
    name: "FortrezZ Leak Detector",
    namespace: "fortrezz",
    author: "Daniel Kurin",
    description: "Use the FortrezZ Water Meter to identify leaks in your home's water system.",
    category: "Green Living",
    iconUrl: "http://swiftlet.technology/wp-content/uploads/2016/05/logo-square-200-1.png",
    iconX2Url: "http://swiftlet.technology/wp-content/uploads/2016/05/logo-square-500.png",
    iconX3Url: "http://swiftlet.technology/wp-content/uploads/2016/05/logo-square.png")


preferences {
	page(name: "page2", title: "Select device and actions", install: true, uninstall: true)
}

def page2() {
    dynamicPage(name: "page2") {
        section("Choose a water meter to monitor:") {
            input(name: "meter", type: "capability.energyMeter", title: "Water Meter", description: null, required: true, submitOnChange: true)
        }

        if (meter) {
            section {
                app(name: "childRules", appName: "Leak Detector", namespace: "fortrezz", title: "Create New Leak Detector...", multiple: true)
            }
        }
        
        section("Send notifications through...") {
        	input(name: "pushNotification", type: "bool", title: "SmartThings App", required: false)
        	input(name: "smsNotification", type: "bool", title: "Text Message (SMS)", submitOnChange: true, required: false)
            if (smsNotification)
            {
            	input(name: "phone", type: "phone", title: "Phone number?", required: true)
            }
            input(name: "hoursBetweenNotifications", type: "number", title: "Hours between notifications", required: false)
        }

		log.debug "there are ${childApps.size()} child smartapps"
        def childRules = []
        childApps.each {child ->
            //log.debug "child ${child.id}: ${child.settings()}"
            childRules << [id: child.id, rules: child.settings()]
        }
        state.rules = childRules
        //log.debug("Child Rules: ${state.rules} w/ length ${state.rules.toString().length()}")
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
	subscribe(meter, "cumulative", gpmHandler)
    log.debug("Subscribing to events")
}

def gpmHandler(evt) {
	//Date Stuff
   	def daysOfTheWeek = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"]
    def today = new Date()
    today.clearTime()
    Calendar c = Calendar.getInstance();
    c.setTime(today);
    int dow = c.get(Calendar.DAY_OF_WEEK);
    def dowName = daysOfTheWeek[dow-1]
    
	def gpm = meter.latestValue("gpm")
    def cumulative = new BigDecimal(evt.value)
    log.debug "GPM Handler: [gpm: ${gpm}, cumulative: ${cumulative}]"
    def rules = state.rules
    rules.each { it ->
        def r = it.rules
        def childAppID = it.id
    	//log.debug("Rule: ${r}")
    	switch (r.type) {
            case "Mode":
            	log.debug("Mode Test: ${location.currentMode} in ${r.modes}... ${findIn(r.modes, location.currentMode)}")
                if (findIn(r.modes, location.currentMode))
                {
                	log.debug("Threshold:${r.gpm}, Value:${gpm}")
                	if(gpm > r.gpm)
                    {
                    	sendNotification(childAppID, gpm)
                        if(r.dev)
                        {
                        	//log.debug("Child App: ${childAppID}")
                        	def activityApp = getChildById(childAppID)
                            activityApp.devAction(r.command)
                        }
                    }
                }
                break

            case "Time Period":
            	def trigger = []
            	log.debug("Time Period Test: ${r}")
                def boolTime = timeOfDayIsBetween(r.startTime, r.endTime, new Date(), location.timeZone)
                def boolDay = !r.days || findIn(r.days, dowName) // Truth Table of this mess: http://swiftlet.technology/wp-content/uploads/2016/05/IMG_20160523_150600.jpg
                def boolMode = !r.modes || findIn(r.modes, location.currentMode)
                
            	if(boolTime && boolDay && boolMode)
                {
                    if(gpm > r.gpm)
                    {
                        sendNotification(childAppID, gpm)
                        if(r.dev)
                        {
                            def activityApp = getChildById(childAppID)
                            activityApp.devAction(r.command)
                        }
                    }
                }
            	break

            case "Accumulated Flow":
            	def trigger = []
            	log.debug("Accumulated Flow Test: ${r}")
                def boolTime = timeOfDayIsBetween(r.startTime, r.endTime, new Date(), location.timeZone)
                def boolDay = !r.days || findIn(r.days, dowName) // Truth Table of this mess: http://swiftlet.technology/wp-content/uploads/2016/05/IMG_20160523_150600.jpg
                def boolMode = !r.modes || findIn(r.modes, location.currentMode)
                
            	if(boolTime && boolDay && boolMode)
                {
                	def delta = 0
                    if(state["accHistory${childAppID}"] != null)
                    {
                    	delta = cumulative - state["accHistory${childAppID}"]
                    }
                    else
                    {
                    	state["accHistory${childAppID}"] = cumulative
                    }
                	log.debug("Currently in specified time, delta from beginning of time period: ${delta}")
                    
                    if(delta > r.gallons)
                    {
                        sendNotification(childAppID, delta)
                        if(r.dev)
                        {
                            def activityApp = getChildById(childAppID)
                            activityApp.devAction(r.command)
                        }
                    }
                }
                else
                {
                	log.debug("Outside specified time, saving value")
                    state["accHistory${childAppID}"] = cumulative
                }
            	break

            case "Continuous Flow":
                def events = meter.statesBetween("cumulative", timeToday(r.startTime, location.timeZone), timeToday(r.endTime, location.timeZone), [max: 100])
                events.each { e ->
                    //log.debug("Event ${e.name}: ${e.value} - ${e.date}")
                }
            	break

            case "Water Valve Status":
            	log.debug("Water Valve Test: ${r}")
            	def child = getChildById(childAppID)
                log.debug("Water Valve Child App: ${child.id}")
                if(child.isValveStatus(r.valveStatus))
                {
                    if(gpm > r.gpm)
                    {
                        sendNotification(childAppID, gpm)
                   }
                }
                break

            case "Switch Status":
            	break

            default:
                break
        }
    }
}

def sendNotification(device, gpm)
{
	def set = getChildById(device).settings()
	def msg = ""
    if(set.type == "Accumulated Flow")
    {
    	msg = "Water Flow Warning: \"${set.ruleName}\" is over threshold at ${gpm}gal"
    }
    else
    {
    	msg = "Water Flow Warning: \"${set.ruleName}\" is over threshold at ${gpm}gpm"
    }
    log.debug(msg)
    if (pushNotification)
    {
		sendPush(msg)
    }
    if (smsNotification) {
        sendSms(phone, msg)
    }
}

def getChildById(app)
{
	return childApps.find{ it.id == app }
}

def findIn(haystack, needle)
{
	def result = false
	haystack.each { it ->
    	//log.debug("findIn: ${it} <- ${needle}")
    	if (needle == it)
        {
        	//log.debug("Found needle in haystack")
        	result = true
        }
    }
    return result
}
// TODO: implement event handlers