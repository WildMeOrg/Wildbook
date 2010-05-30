// ********* ********* ********* ********* *********
// Handler functions
// edit as you see fit
//
// PARM		DESCRIPTION
// obj		the date cell DIV object reference
// y		year
// m		month
// d		day
// ********* ********* ********* ********* ********* 
var mstrPreviousClassName;

// event for MOUSE DOWN in a date cell
function fscCellOnMouseDown(obj, y, m, d) {
	var dte = new Date(y, m, d);
};

// event for MOUSE OVER in a date cell
function fscCellOnMouseOver(obj, y, m, d) {
	var dte = new Date(y, m, d);
	//mstrPreviousClassName = obj.className
	//obj.className = "scWeekday scEventYellow";
};

// event for MOUSE OUT in a date cell
function fscCellOnMouseOut(obj, y, m, d) {
	var dte = new Date(y, m, d);
	//obj.className = mstrPreviousClassName;
};

// event for MOUSE UP in a date cell
function fscCellOnMouseUp(obj, y, m, d) {
	var dte = new Date(y, m, d);
};

