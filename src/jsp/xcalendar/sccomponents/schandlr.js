// ********* ********* ********* ********* *********
// Handler functions
// edit as you see fit
//
// PARM		DESCRIPTION
// e        a reference to the event intercepted by the event handler
// obj      a reference to the DIV object
// y		year
// m		month
// d		day
// ********* ********* ********* ********* ********* 

// ********* ********* ********* ********* ********* 
// * event handlers for Date Cell DIV
function fscCellOnMouseDown(e, obj, y, m, d) {
	var dte = new Date(y, m, d);
};
function fscCellOnMouseOver(e, obj, y, m, d) {
	var dte = new Date(y, m, d);
};
function fscCellOnMouseOut(e, obj, y, m, d) {
	var dte = new Date(y, m, d);
};
function fscCellOnMouseUp(e, obj, y, m, d) {
	var dte = new Date(y, m, d);
};

// ********* ********* ********* ********* ********* 
// * event handlers for Date Number DIV
function fscNumberOnMouseDown(e, obj, y, m, d) {
	var dte = new Date(y, m, d);
};
function fscNumberOnMouseOver(e, obj, y, m, d) {
	var dte = new Date(y, m, d);
};
function fscNumberOnMouseOut(e, obj, y, m, d) {
	var dte = new Date(y, m, d);
};
function fscNumberOnMouseUp(e, obj, y, m, d) {
	var dte = new Date(y, m, d);
};

// ********* ********* ********* ********* ********* 
// * event handlers for Event DIV
function fscEventOnMouseDown(e, obj, y, m, d) {
	var dte = new Date(y, m, d);
};
function fscEventOnMouseOver(e, obj, y, m, d) {
	var dte = new Date(y, m, d);
};
function fscEventOnMouseOut(e, obj, y, m, d) {
	var dte = new Date(y, m, d);
};
function fscEventOnMouseUp(e, obj, y, m, d) {
	var dte = new Date(y, m, d);
};

// ********* ********* ********* ********* ********* 
// * miscellanous function
function fscCancelBubbling(e)
{
	if (!e) var e = window.event;
	e.cancelBubble = true;
	if (e.stopPropagation) e.stopPropagation();
};
