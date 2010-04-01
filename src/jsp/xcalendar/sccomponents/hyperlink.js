
// ********* ********* ********* ********* ********* ********* ********* ********* *********
// Define Events
// call the fscEvent function
// 
// #  PARMS		DATA TYPE	DESCRIPTION
// 1  m			number		2 digit month (1=jan, 2=feb, 3=mar,... 12=dec)
// 2  d			number		2 digit day
// 3  y			number		4 digit year
// 4  text		date		HTML event text
// 5  link		string		URL for popup window
// 6  style		string		CSS class for the event (in-line style is invalid)
// 7  tooltip		string		text for hover over tooltip
// 8  script		string		javascript to execute during onMouseDown
// 9  filter		string		keyword to allow users to filter events
// ********* ********* ********* ********* ********* ********* ********* ********* *********

fscEvent( 9, 1, 2005, "pre <a href='javascript:myPopup(\"http://www.yahoo.com\");'>link</a> post" );

fscEvent( 9, 2, 2005, "pre <a href='javascript:myPopup(&quot;http://www.yahoo.com&quot;);'>link</a> post" );

fscEvent( 9, 3, 2005, "pre <a href='javascript:myPopup(&#34;http://www.yahoo.com&#34;);'>link</a> post" );


function myPopup(strUrl) {
	var strProp = "width=600,height=400,scrollbars=yes,resizable=yes,titlebar=yes,toolbar=yes,menubar=yes,location=yes,status=yes";
	window.open( strUrl, "_blank", strProp );
};