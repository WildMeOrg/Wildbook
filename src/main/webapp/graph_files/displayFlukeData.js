/**
	Main javascript for loading, unloading 
	and displaying the data in the data display
	div when a whale is double clicked.
	
	-- Click Action
	1. Reset the zindex to bring the div to the front
	2. Animate
		a) transparency - 1
		b) size - 200px?
	3. Fetch appropriate data from the data object
	4. Add the data to the display div
		a) enable overflow if necessary
	
	-- Close Action (triggered on click out of box on svg?
	1. Reset the zindex to send div to back
	2. Remove all html from the div
	3. Anitmate 
		a) transparency - .1
		b) size - 50px;
*/
var globals = {
	// toggle dev vs prod
	server_url : "http://dev.flukebook.org/" //"http://flukebook.org/"
	, animation_time:500
	, height: {
		open:250 ,
		closed:50
	}
	, opacity :{
		open:1,
		closed:0
	}
	, zIndex :{
		open:10000,
		closed:-1
	}
	, colors:["gray", "blue", "red" , "yellow", "green", "orange", "purple", "black"]
};

/**
 * Main Display function
 */
function displayFlukeDiv(data){
	
	// Shane's request
	window.open(globals.server_url + 'individuals.jsp?number=' + data.individualID);
	
	/** 
	 * The below code used to draw a display div, but Shane wanted clicks to
	 * only link to individual fluke pages
	var fluke_div = $("#fluke_data_display");
	
	// close div to start 
	closeDiv(1);
	
	// Div actions
	function modifyDiv(html, input_height, zIndex, mOpacity, duration){
		// 1. Set data 
		fluke_div.html(html);
		// 2. Animate back to the resting state
		fluke_div.animate({height:input_height, opacity:mOpacity}, typeof duration === "undefined" ? globals.animation_time : duration , function(){
			// set the opacity back
			$('#fluke_data_display_table').css('opacity', '1')
		});
		// 3. Reset the z index
		fluke_div.css("zIndex", zIndex); // some small number to get us out of the graph's way
	}
	
	// closes the div 
	function closeDiv(time){
		modifyDiv("", globals.height.closed, globals.zIndex.closed, globals.opacity.closed, time);
	}

	// Make the div
	function generateDivHtml(node){
		var html = "<tr><th>Attribute</th><th>Value</th></tr>";
		
		// loop over all of the attributes and add them to the html
		$([{label:"Name", name:"individualID"}, 
		{label:"Living Status", name:"livingStatus"},
		{label:"Sex", name:"sex"},
		{label:"Submitter", name:"submitterID"}]).each(function(i,a){
			var label = a.label;
			var value = node[a.name];
			html += "<tr>";
			$([label, value]).each(function( i,td ){
				html += "<td>" + td + "</td>" ;
			});
			html += "</tr>";
		});
		
		return html;
	}

	// On Open
	// First generate the div html
	var html = '<table id="fluke_data_display_table" ><tbody>' + generateDivHtml(data) + "</tbody></table>";
	// add the individual id page 
	html += '<button id="individual_page" style="margin-left:20px">Page</button>' ;
	// Now add the close button
	html += '<button id="generate_div_close" style="float:right; margin-right:20px" >Close</button>' ;
	// add the html to the div
	modifyDiv(html, globals.height.open, globals.zIndex.open, globals.opacity.open);
	// add the close action 
	$('#generate_div_close').button().click(closeDiv);
	$('#individual_page').button().click(function(){
		window.open('http://flukebook.org/individuals.jsp?number=' + data.individualID);
	});
	*/
}
