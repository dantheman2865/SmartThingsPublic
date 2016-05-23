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
    log.debug "GPM Handler: [gpm: ${gpm}, cumulative: ${evt.value}]"
    def rules = state.rules
    rules.each { it ->
        def r = it.rules
        def childAppID = it.id
    	log.debug("Rule: ${r}")
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
                        	log.debug("Child App: ${childAppID}")
                        	def activityApp = getChildById(childAppID)
                            activityApp.devAction(r.command)
                        }
                    }
                }
                break

            case "Time Period":
            	def trigger = []
            	log.debug("Time Period Test: ${r}")
            	if(timeOfDayIsBetween(r.startTime, r.endTime, new Date(), location.timeZone))
                {
                	trigger << true
                	log.debug("Time is included")
                    if(r.days)
                    {
                    	log.debug("Today: ${dowName}")
                        if(findIn(r.days, dowName))
                        {
                        	log.debug("Today included")
                            trigger << true
                        }
                        else
                        {
                        	log.debug("Today not included")
                            trigger << false
                        }
                    }
                    if(r.modes)
                    {
                    	log.debug("Mode(s) are selected")
                        if (findIn(r.modes, location.currentMode))
                        {
                        	log.debug("Current mode included")
                            trigger << true
                        }
                        else
                        {
                        	log.debug("Current mode not included")
                            trigger << false
                        }
                    }
	                log.debug("Result is: ${findIn(trigger, false)}")
                    if(!findIn(trigger, false)) // If all selected options are met
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
                }
            	break

            case "Accumulated Flow":
            	break

            case "Continuous Flow":
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
	def name = getChildById(device).name()
	def msg = "Water Flow Warning: \"${name}\" is over threshold at ${gpm}gpm"
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