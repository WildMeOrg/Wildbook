// ********* ********* ********* ********* ********* ********* ********* ********* *********
// Special Event function
// This function allows you set events for every weekday or for holidays
//
// VARIABLE				DATA TYPE	DESCRIPTION
// y					number		the 4-digit year (i.e. 2002)
// m					number		the month (1=jan, 2=feb,... 12=dec)
// d					number		the day
// dte					date		the full date
// intWeekday			number		day of the week (0=sun; 1=mon; 2=tue, ..., 6=sat)
// intWeekOfYear		number		week number of the year
// intWeekOfMonth		number		week number of month (1st Sunday, 2nd Sunday, ...)
// blnLast				boolean		is this the Last Weekday of the month
// dteEaster			date		the full date of Easter Sunday for the year
// dteMardiGras			date		the full date of Mardi Gras Tuesday for the year
// dteAshWednesday		date		the full date of Ash Wednesday for the year
// dteGoodFriday		date		the full date of Good Friday for the year
// ********* ********* ********* ********* ********* ********* ********* ********* *********
function scSpecialEvent(dte) {
	var objEvent;
	var arrEvents = new Array();
	dteCurrent = new Date();
	
	var m = dte.getMonth() + 1;
	var d = dte.getDate();
	var y = dte.getFullYear();
	var intWeekday = dte.getDay();
	var intWeekOfYear = dte.weekOfYear();
	var intWeekOfMonth = dte.weekOfMonth();
	var blnLast = ( (new Date(y, m-1, d+7).getMonth() ) == m );

	var dteEaster = fscEaster(y);
	var dteMardiGras = dteEaster.add("d", -47);
	var dteAshWednesday = dteEaster.add("d", -46);
	var dteGoodFriday = dteEaster.add("d", -2);


	// ********* ********* ********* ********* ********* ********* ********* *********
	// *** every day event
	// ********* ********* ********* ********* ********* ********* ********* *********

	var strCalendarDate = fscDateString( dte.getFullYear(), dte.getMonth(), dte.getDate(), true);
	var strCurrentDate = fscDateString( dteCurrent.getFullYear(), dteCurrent.getMonth(), dteCurrent.getDate(), true);


	// event without link
	if (strCalendarDate < strCurrentDate) {
		objEvent = new EventObj(m,d,y, d, null, null, null, null);
		arrEvents[arrEvents.length] = objEvent;
	};

	// event with link
	if (strCalendarDate >= strCurrentDate) {
		objEvent = new EventObj(m,d,y, d, "http://www.scriptcalendar.com/dhtmlcal/cafaq.asp", null, null, null);
		arrEvents[arrEvents.length] = objEvent;
	};
	

		
	return arrEvents;
};






