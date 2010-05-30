// ********* ********* ********* ********* ********* ********* ********* ********* *********
// Special Event function
// This function allows you set events for every weekday or for holidays
//
// VARIABLE				DATA TYPE	DESCRIPTION
// y					number		the 4-digit year (i.e. 2002)
// m					number		the month (1=jan, 2=feb,... 12=dec)
// d					number		the day
// dte					date		the full date
// dteEaster			date		the full date of Easter Sunday for the year
// intWeekday			number		day of the week (0=sun; 1=mon; 2=tue, ..., 6=sat)
// intWeekOfYear		number		week number of the year
// intWeekOfMonth		number		week number of month (1st Sunday, 2nd Sunday, ...)
// blnLast				boolean		is this the Last Weekday of the month
// ********* ********* ********* ********* ********* ********* ********* ********* *********
function scSpecialEvent(dte) {
	var objEvent;
	var arrEvents = new Array();
	var dteCurrent = new Date();
	
	var m = dte.getMonth() + 1;
	var d = dte.getDate();
	var y = dte.getFullYear();
	var intWeekday = dte.getDay();
	var intWeekOfYear = dte.weekOfYear();
	var intWeekOfMonth = dte.weekOfMonth();
	var blnLast = ( (new Date(y, m-1, d+7).getMonth() ) == m );


	// ********* ********* ********* ********* ********* ********* ********* *********
	// *** current date
	// ********* ********* ********* ********* ********* ********* ********* *********

	// *** current day event
	if ( dte.equalsTo(dteCurrent) ) {
		objEvent = new EventObj(m,d,y, "TODAY", null, "scToday");
		arrEvents[arrEvents.length] = objEvent;
	};
	
	// ********* ********* ********* ********* ********* ********* ********* *********
	// *** every weekday functions
	// ********* ********* ********* ********* ********* ********* ********* *********

	// every sunday
	if (intWeekday==0) {
		objEvent = new EventObj(m,d,y, "every sunday", null, "scEventBlue");
		arrEvents[arrEvents.length] = objEvent;
	};
	
	// every 2nd saturday 
	if (intWeekday==6 && (intWeekOfMonth==1 || intWeekOfMonth==3) ) {
		objEvent = new EventObj(m,d,y, "1st and 3rd saturday", null, "scEventPurple");
		arrEvents[arrEvents.length] = objEvent;
	};

	// ********* ********* ********* ********* ********* ********* ********* *********
	// *** holidays
	// ********* ********* ********* ********* ********* ********* ********* *********
	
	// New Years Day
	if (m==1 && d==1) {
		objEvent = new EventObj(m,d,y, "New Years Day", null, "scEventBlue");
		arrEvents[arrEvents.length] = objEvent;
	};

	// Martin Luther King Day, third Monday in January. 
	if (m==1 && intWeekday==1 && intWeekOfMonth==3) {
		objEvent = new EventObj(m,d,y, "M.L. King Day", null, "scEventRed");
		arrEvents[arrEvents.length] = objEvent;
	};

	// President's Day, third Monday in February. 
	if (m==2 && intWeekday==1 && intWeekOfMonth==3) {
		objEvent = new EventObj(m,d,y, "Presidents Day", null, "scEventRed");
		arrEvents[arrEvents.length] = objEvent;
	};

	// St. Valentines day
	if (m==2 && d==14) {
		objEvent = new EventObj(m,d,y, "Saint Valentines Day", null, "scEventRed");
		arrEvents[arrEvents.length] = objEvent;
	};

	// St. Patricks day
	if (m==3 && d==17) {
		objEvent = new EventObj(m,d,y, "Saint Patricks Day", null, "scEventGreen");
		arrEvents[arrEvents.length] = objEvent;
	};

	// Daylight Savings time begins, first Sunday in April
	if (m==4 && intWeekday==0 && intWeekOfMonth==1) {
		objEvent = new EventObj(m,d,y, "Begin Daylight Savings", null, "scEventGreen");
		arrEvents[arrEvents.length] = objEvent;
	};
	
	// Mother's Day, second Sunday in May. 
	if (m==5 && intWeekday==0 && intWeekOfMonth==2) {
		objEvent = new EventObj(m,d,y, "Mothers Day", null, "scEventRed");
		arrEvents[arrEvents.length] = objEvent;
	};

	//Memorial Day, Last Monday in May. 
	if (m==5 && intWeekday==1 && blnLast==true) {
		objEvent = new EventObj(m,d,y, "Memorial Day", null, "scEventRed");
		arrEvents[arrEvents.length] = objEvent;
	};

	// Father's Day, third Sunday in June. 
	if (m==6 && intWeekday==0 && intWeekOfMonth==3) {
		objEvent = new EventObj(m,d,y, "Fathers Day", null, "scEventRed");
		arrEvents[arrEvents.length] = objEvent;
	};

	// 4th of July
	if (m==7 && d==4) {
		objEvent = new EventObj(m,d,y, "Fourth of July", null, "scEventBlue");
		arrEvents[arrEvents.length] = objEvent;
	};

	// Labor Day, first Monday in September. 
	if (m==9 && intWeekday==1 && intWeekOfMonth==1) {
		objEvent = new EventObj(m,d,y, "Labor Day", null, "scEventRed");
		arrEvents[arrEvents.length] = objEvent;
	};

	// Columbus Day, second Monday in October 
	if (m==10 && intWeekday==1 && intWeekOfMonth==2) {
		objEvent = new EventObj(m,d,y, "Colombus Day", null, "scEventRed");
		arrEvents[arrEvents.length] = objEvent;
	};

	// Daylight Savings Time ends, blnLast Sunday in October
	if (m==10 && intWeekday==0 && blnLast==true) {
		objEvent = new EventObj(m,d,y, "End Daylight Savings", null, "scEventBlack");
		arrEvents[arrEvents.length] = objEvent;
	};

	// Halloween
	if (m==10 && d==31) {
		objEvent = new EventObj(m,d,y, "Halloween", null, "scEventOrange");
		arrEvents[arrEvents.length] = objEvent;
	};

	// Thanksgiving Day, fourth Thursday in November. 
	if (m==11 && intWeekday==4 && intWeekOfMonth==4) {
		objEvent = new EventObj(m,d,y, "Thanksgiving", null, "scEventRed");
		arrEvents[arrEvents.length] = objEvent;
	};

	// Christmas
	if (m==12 && d==25) {
		objEvent = new EventObj(m,d,y, "Christmas Day", null, "scEventRed");
		arrEvents[arrEvents.length] = objEvent;
	};

	// ********* ********* ********* ********* ********* ********* ********* *********
	// *** Easter holidays
	// ********* ********* ********* ********* ********* ********* ********* *********
	var dteEaster = fscEaster(y);
	var dteMardiGras = dteEaster.add("d", -47);
	var dteAshWednesday = dteEaster.add("d", -46);
	var dteGoodFriday = dteEaster.add("d", -2);

	// Mardi Gras
	if ( dte.equalsTo(dteMardiGras) ) {
		objEvent = new EventObj(m,d,y, "Mardi Gras", null, "scEventPurple");
		arrEvents[arrEvents.length] = objEvent;
	};	

	// Ash Wednesday (46 days before Easter)
	if ( dte.equalsTo(dteAshWednesday) ) {
		objEvent = new EventObj(m,d,y, "Ash Wednesday", null, "scEventBlue");
		arrEvents[arrEvents.length] = objEvent;
	};	

	// Good Friday 
	if ( dte.equalsTo(dteGoodFriday)) {
		objEvent = new EventObj(m,d,y, "Good Friday", null, "scEventRed");
		arrEvents[arrEvents.length] = objEvent;
	};

	// Easter Sunday 
	if ( dte.equalsTo(dteEaster)) {
		objEvent = new EventObj(m,d,y, "Easter Sunday", null, "scEventGreen");
		arrEvents[arrEvents.length] = objEvent;
	};
		
	// ********* ********* ********* ********* ********* ********* ********* *********
	// *** Odd and Even Days
	// ********* ********* ********* ********* ********* ********* ********* *********
	if ( d/2 != parseInt(d/2) ) {
		// odd day		
		objEvent = new EventObj(m,d,y, " ", null, "scEventColor1");
		arrEvents[arrEvents.length] = objEvent;
	} else {
		// even day		 
		objEvent = new EventObj(m,d,y, " ", null, "scEventColor2");
		arrEvents[arrEvents.length] = objEvent;
	};


	return arrEvents;
};






