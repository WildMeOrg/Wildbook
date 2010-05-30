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
var mstrOriginalClassName;

// event for MOUSE DOWN in a date cell
function fscCellOnMouseDown(obj, y, m, d) {
	var dte = new Date(y, m, d);

	//var strQueryString = "?scDate=" + (m+1) + "/" + d + "/" + y;
	//strQueryString = fscUrlEncode(strQueryString)
	//var objWindow = window.open("smallpop.htm" + strQueryString, "_blank", mstrPopupProp);
};

// event for MOUSE OVER in a date cell
function fscCellOnMouseOver(obj, y, m, d) {
	var dte = new Date(y, m, d);
};

// event for MOUSE OUT in a date cell
function fscCellOnMouseOut(obj, y, m, d) {
	var dte = new Date(y, m, d);
};

// event for MOUSE UP in a date cell
function fscCellOnMouseUp(obj, y, m, d) {
	var dte = new Date(y, m, d);
};

