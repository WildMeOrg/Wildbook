/**
 * @filename: tracing.js
 * @purpose: Contains javascript code to control canvas drawing using paper script (paper.js)
 * @author Ecological Software Solutions LLC
 * @version 0.1 Alpha
 * @copyright 2014-2015 
 * @license This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */


var comEcostatsTracing = (function(){
	// private variable and method section
	var hitOptions = {
			segments: true,
			stroke: true,
			fill: true,
			tolerance: 5
		};
	var first_load=true;
	//var node_types={0:'point',1:'tip',2:'notch',3:'nick',4:'gouge_start',5:'gouge_end',6:'scallop_start',7:'scallop_end',8:'wave_start',9:'wave_end',10:'missing_start',11:'missing_end',12:'scar',13:'hole',14:'invisible_start',15:'invisible_end'};
	var node_types={0:'point',1:'tip',2:'notch',3:'nick',4:'gouge_start',5:'gouge_end',6:'scallop_start',7:'scallop_end',8:'wave_start',9:'wave_end',10:'missing_start',11:'missing_end',12:'scar',13:'hole',14:'invisible_start',15:'invisible_end'};

	var segment, path, current_point;
	var movePath=false;
	var is_drawing=false;
	var modified=false;
	var hit_test=false;
	var remove_node=false;
	var node_edit_type=1;
	var fluke_side=0; // 0=left, 1=right
	var trace_type=1; // 1=Feature point nodes defined and mapped, 2=Contour point tracing only
	var imgurl=null;
	var encounter_id=null;
	var photo_id=null;
	var curled_left=false;
	var curled_right=false;
	var node_edit_types={0:'none',1:'addnode',2:'removenode',3:'insertnode',4:'typenode'};
	
	function event_target(evt){
	    var evnt = evt || window.event;
	    var target = evnt.target || evnt.srcElement;
	    if (target.nodeType == 3) { // Safari bug
	        target = target.parentNode;
	    }
	    return target;
	};

	// Adds a div and select list to let the user define the type of structure at each node of the tracing. 
	function show_node_type_select(point){
		var selectnode;
		var selectdiv=document.getElementById('comEcostatsTracingNodeDiv');
		// create the div and select list if it does not exist
		if (selectdiv==undefined || selectdiv==null){
			selectdiv = document.createElement('div');
			selectdiv.setAttribute('id','comEcostatsTracingNodeDiv');
			//selectdiv.setAttribute('width','200px');
			selectdiv.style.position='relative';
			selectdiv.style.visibility='hidden'; 
			selectnode = document.createElement('select');
			selectnode.setAttribute("name", "comEcostatsTracingNodeSelect");
			selectnode.setAttribute("id", "comEcostatsTracingNodeSelect");
			/* setting an onchange event */
			var option;
			for (node_type in node_types){
				option = document.createElement("option");
				option.setAttribute("value", node_type);
				option.innerHTML = node_types[node_type];
				selectnode.appendChild(option);
			}
			selectdiv.appendChild(selectnode);
			paper.project.view.element.parentNode.appendChild(selectdiv);
			selectnode.onchange=comEcostatsTracing.onChange; 
			selectnode.onkeydown=comEcostatsTracing.onKeyDown;
		}else{
			selectnode = document.getElementById('comEcostatsTracingNodeSelect');
		}
		// bring to front of all other elements
		selectdiv.style['z-index']='9999';
		// position the div where the user clicked the mouse
		var y=Math.round(point.y)-paper.project.view.viewSize.height;
		selectdiv.style.top=y+'px';
		selectdiv.style.left=Math.round(point.x)+'px';
		// set the select list to the node type at the selected node 
		var tracing_group = paper.project.activeLayer.children[fluke_side+1];
		var node_types_group = tracing_group.children[2];
		var hit_result = node_types_group.hitTest(point);
		if (hit_result){
			// get the select list
			selectnode=document.getElementById('comEcostatsTracingNodeSelect');
			// set the select list selected item to the node type at the node where the user clicked with the mouse 
			selectnode.selectedIndex=hit_result.item.data.id;
		}
		selectdiv.style.visibility = "visible";
		selectnode.focus();
	};

	// center the div element that will show the image and menu options
	function jqCenter(elem) {
		elem.css("position","absolute");
		elem.css("top", Math.max(0, (($(window).innerHeight() - $(elem).outerHeight()) / 2) + $(window).scrollTop()) + "px");
		elem.css("left", Math.max(0, (($(window).innerWidth() - $(elem).outerWidth()) / 2) + $(window).scrollLeft()) + "px");
	    return elem;
	};

	// method to build the div element that holds any images to trace.
	function make_tracer_div(){
		var div_fluke_tracer=document.createElement('div');
		div_fluke_tracer.setAttribute('id','fluke_tracer');
		div_fluke_tracer.style.position='relative';
		div_fluke_tracer.setAttribute('style','display:none;background-color:#fff;border:1px solid gray;padding:1em;border-radius:7px;position:absolute;z-index:9999;');
		// create a set of menu items to work with the image
		var menus=get_menus();
		div_fluke_tracer.appendChild(menus);
		add_info_bar(div_fluke_tracer);
		document.body.appendChild(div_fluke_tracer);
	};
	
	/**
	 * Menu functions -- used to create or manipulate menus
	 **/

	// create the main menu and drop down menus
	function get_menus(){
		var main_menu=make_menu();
		// right fluke sub menu and options
		var active_fluke_menu=add_menu_option(main_menu,'Select Active Fluke',null);
		var active_fluke_menu_options=make_menu(active_fluke_menu);
		add_menu_option(active_fluke_menu_options,'Left Fluke','fluke_trace_left');
		add_menu_option(active_fluke_menu_options,'Right Fluke','fluke_trace_right');
		// fluke tracing menu item
		var fluke_menu=add_menu_option(main_menu,'Fluke Tracing',null);
		var fluke_menu_options=make_menu(fluke_menu);
		add_menu_option(fluke_menu_options,'Show Active Fluke Tracing','show_active_tracing');
		add_menu_option(fluke_menu_options,'Hide Active Fluke Tracing','hide_active_tracing');
		add_menu_option(fluke_menu_options,'Clear Active Fluke Tracing','clear_active_tracing');
		//add_menu_option(fluke_menu_options,'Reload All From Server','reload_tracing');
		// Image tool
		var edit_menu=add_menu_option(main_menu,'Image Tool',null);
		var edit_menu_options=make_menu(edit_menu);
		add_menu_option(edit_menu_options,'Add Nodes','fluke_trace_add_nodes');
		add_menu_option(edit_menu_options,'Insert Nodes','fluke_trace_insert_nodes');
		add_menu_option(edit_menu_options,'Remove Nodes','fluke_trace_remove_nodes');
		add_menu_option(edit_menu_options,'Edit Node Types','fluke_trace_edit_nodes_type');
		// image adjustment menu item
		//add_menu_option(main_menu,'Adjust Image','fluke_trace_adjust_image');
		// save tracing
		add_menu_option(main_menu,'Save All','fluke_trace_save');
		// save tracing
		//add_menu_option(main_menu,'Try Matching','fluke_trace_match');
		// close window menu item
		add_menu_option(main_menu,'Close','fluke_trace_close');
		var dmenu=document.createElement('div');
		dmenu.setAttribute('class','dropdown');
		dmenu.appendChild(main_menu);
		return dmenu;
	};
	
	// creates a menu, either the main horizontal menu or any drop down menus 
	function make_menu(parent_menu){
		// make a main menu item
		var dul=document.createElement('ul');
		if (parent_menu!=undefined){
			// surround each sub menu with a DIV element which can be used to hide the submenu on click events.
			var menu_div=document.createElement('div');
			menu_div.class = 'dropdown';
			//menu_div.style.visibility='hidden';
			//menu_div.style.display='none';
			menu_div.appendChild(dul);
			parent_menu.appendChild(menu_div);
		}else{
			//dul.setAttribute('id','pmenu');
			dul.setAttribute('class','nav');
			dul.setAttribute('role','navigation');
		}
		return dul;
	};

	// adds options to a menu
	function add_menu_option(parent_menu,caption,id){	
		// add an option to any menu item	 
		var dli=document.createElement('li');
		var ali=document.createElement('a');
		if (id!=null){
			// if the menu has an ID value then assign the global "onclick" event handler.
			ali.setAttribute('id',id);
			ali.setAttribute('onclick','comEcostatsTracing.onMenuSelect(event);');
		}
		if (parent_menu.getAttribute('id')=='pmenu'){
			// the row of visible menu items
			//dli.setAttribute('class','drop');
			//ali.setAttribute('class',"mainmenu");
			//ali.setAttribute('onmouseover','comEcostatsTracing.onMenuShow(event);');
		}else{
			// and drop down menu items below any pmenu item
			//ali.setAttribute('class',"menuitem");
		}  
		var cap=document.createTextNode(caption); // text node to hold the menu item's caption
		ali.appendChild(cap);
		dli.appendChild(ali);
		parent_menu.appendChild(dli);
		return dli;
	};
	
	// this adds a line of information above the image, visible to the user, about which fluke is selected and other information
	function add_info_bar(pdiv){
		var info_bar=document.createElement('div');
		info_bar.setAttribute('style','height:2.4em;width:100%;border-bottom:1px solid silver;position:relative;top:0.7em;float:left;');
		// add a check box if the fluke notch is open or not
		var notch_cb = document.createElement('input');
		notch_cb.setAttribute('id','notch_cb');
		notch_cb.setAttribute('type','checkbox');
		var notch_cb_caption=document.createTextNode('Fluke Notch Open');
		info_bar.appendChild(notch_cb);
		info_bar.appendChild(notch_cb_caption);		
		// add a caption showing which side of the fluke is "activeated" for tracing.
		var active_fluke_caption=document.createTextNode('Active Fluke Side: ');
		var active_fluke=document.createElement('span');
		active_fluke.setAttribute('id','active_fluke');
		var current_fluke=document.createElement('span');
		current_fluke.setAttribute('style','margin-left: 2em;');
		current_fluke.appendChild(active_fluke_caption);
		current_fluke.appendChild(active_fluke);
		info_bar.appendChild(current_fluke);
		// add a check box to select if the active fluke is curled or not
		var curled_cb = document.createElement('input');
		curled_cb.setAttribute('id','curled_cb');
		curled_cb.setAttribute('type','checkbox');
		curled_cb.onchange=comEcostatsTracing.onCurlChange; 
		var curled_cb_caption=document.createTextNode('Is Curled');
		info_bar.appendChild(curled_cb);
		info_bar.appendChild(curled_cb_caption);		
		// add the info bar to the image div
		pdiv.appendChild(info_bar);
	};
	
	// CSS menus do not open/close in respond to clicks, so these two methods show/hide the DIV elements that
	// surround sub menus so the sub menus can be hidden when they are clicked -- useful for AJAX calls.
	function show_child_div(event){
		var test_element=event_target(event);
		while (test_element!=null){
			if (test_element.nextSibling.nodeName.toUpperCase()=='DIV' || test_element.nextSibling.localName.toUpperCase()=='DIV'){
				var showdiv=test_element.nextSibling;
				//showdiv.style.visibility='visible';
				//showdiv.style.display='';
				break;
			}
			test_element=test_element.nextSibling;
		}
	};
	
	function hide_child_div(event){
		var test_element=event_target(event);
		while (test_element!=null){
			if (test_element.parentElement.nodeName.toUpperCase()=='DIV' || test_element.parentElement.localName.toUpperCase()=='DIV'){
				var hidediv=test_element.parentElement;
				if (!hidediv.hasAttribute('class')){ // do not hide the main menu band
					//hidediv.style.visibility='hidden';
					//hidediv.style.display='none';
				}
				break;
			}
			test_element=test_element.parentElement;
		}
	};
	
	/**
	 * Private methods called by menu methods in the below section
	 **/
	
	// returns the currently selected tracing group
	function tracingGroup(){
		var tracing_group = paper.project.activeLayer.children[fluke_side+1];
		return tracing_group;
	};
	
	// hides the div element with the select node type select list
	function hide_node_types_select(){
		var selectdiv=document.getElementById('comEcostatsTracingNodeDiv');
		if (selectdiv!=undefined){
			//selectdiv.style.visibility='hidden';
		}
	};
	
	// method to get the a canvas to insert the image to be traced (and make this canvas if necessary)
	function make_canvas(fluke_tracer){
		var canvas = document.getElementById('myCanvas');
		if (canvas==null){ 
		    canvas=document.createElement('canvas');
			canvas.setAttribute('id','myCanvas');
			canvas.setAttribute('class','canvas');
			//canvas.setAttribute('width','1200');
			//canvas.setAttribute('height','1200');
			//canvas.setAttribute('resize','true');
			var dscroll=document.createElement('div');
			dscroll.setAttribute('style','width:100%;height:87%;overflow:scroll;position:relative;top:1em;');
			dscroll.appendChild(canvas);
			fluke_tracer.context.appendChild(dscroll);
		}
		return canvas;
	};

	// clear a tracing 
	function clear_tracing(index){
		// remove the current path and node type text items from the current group
		paper.project.activeLayer.children[index].removeChildren();
		paper.view.update();
		// create a new empty path
		var a_path = new paper.Path();
		a_path.strokeColor = 'red';
		a_path.strokeWidth = 1;	   
		// create a group for each node point to show to the user
		var node_points=new paper.Group();
		// create a group for each node type list to show to the user
		var node_types=new paper.Group();
		// add it to the current active layer (i.e. left or right fluke that was cleared)
		paper.project.activeLayer.children[index].addChild(a_path);
		paper.project.activeLayer.children[index].addChild(node_points);
		paper.project.activeLayer.children[index].addChild(node_types);
		modified=true;
	};

	// adds the image to the canvas
	function add_image(url){
		// if the current project does not have an image layer, add it and create all the needed paths and groups
		if (paper.project.activeLayer.children.length==0){
			// set the view of the paper canvas to some default size		 	
			// load the image url, and center the image
			var raster=new paper.Raster(url,paper.view.center);
			paper.view.viewSize=new paper.Size(raster.width,raster.height);
			// Create new fluke side paths, when the script is executed:
			var left_path = new paper.Path();
			var right_path = new paper.Path();
			// set some standard colors
			left_path.strokeColor = 'red';
			left_path.strokeWidth = 1;
			right_path.strokeColor = 'red';
			right_path.strokeWidth = 1;
			var left_points = new paper.Group();
			var right_points = new paper.Group();
			// create a group for each node type list to show to the user
			var left_node_types=new paper.Group();
			var right_node_types=new paper.Group();
			// place each path in to a group; each group will also contain node type text and other info for each path
			new paper.Group(left_path,left_points,left_node_types);
			new paper.Group(right_path,right_points,right_node_types);
			paper.view.update();
		}else{
			// replace the current raster image with the new url
			paper.project.activeLayer.children[0].source=url;
			//paper.project.activeLayer.children[0].position=paper.view.center;
			paper.view.update();
		}
	};
	
	// adds a point at each placed clicked on the image and adds the point to the active tracing_point group
	function add_point_path(tracing_points_group,point,index){
		var a_circle = new paper.Path.Circle({
			center: point,
			radius: 3
		});
		a_circle.strokeColor = 'red';
		a_circle.fillColor = 'red';
		if (index!=null){
			tracing_points_group.insertChild(index,a_circle);
		}else{
			tracing_points_group.addChild(a_circle);
		}
	};
	
	// adds a text letter at each placed clicked on the image and adds the text to the active tracing_types group
	function add_point_type(tracing_types_group,point,index){
		var text = new paper.PointText(point);
		text.justification = 'center';
		text.fillColor = 'black';
		if (tracing_types_group.children.length==0){
			text.content = 'T';
			text.data.id=1;
		}else{
			text.content = 'P';
			text.data.id=0;
		}	
		if (index!=null){
			tracing_types_group.insertChild(index,text);
		}else{
			tracing_types_group.addChild(text);
		}
		return text;
	};
	
	/**
	 * Methods used directly by menu or button events 
	 **/
	
	// sets which fluke side is currently active for tracing
	function set_fluke_side(event_id){
		var menu_option=document.getElementById(event_id);
		// get the index of the selected menu item and set that to the fluke_side variable by acessing its parent LI element position in the UL list
		fluke_side = Array.prototype.indexOf.call(menu_option.parentNode.parentNode.childNodes, menu_option.parentNode);
		// show the selected fluke to the user in the info-bar
		var active_fluke=document.getElementById('active_fluke');
		active_fluke.textContent=menu_option.textContent;
		var cb = document.getElementById('curled_cb');
		if (fluke_side==0){
			cb.checked=curled_left;
		}else{
			cb.checked=curled_right;
		}
	};

	// clears the selected fluke drawing
	function clear_fluke(){
        var active_fluke=document.getElementById('active_fluke');
        var option=active_fluke.textContent;
        if (paper.project.activeLayer.children[fluke_side+1]!=undefined){
	        if (confirm('Delete ' + option +'?')!=false){
	            clear_tracing(fluke_side+1);       
	            paper.view.update();
	            node_edit_type=1; 
	            edit_node_change(node_edit_type);
	        }
	    }else{
	        alert("There are no fluke contours recorded for "+option+".");
	    }
	};
	
	// show the currently selected tracing (undoes hideTrace)
	function show_trace(){
		paper.project.activeLayer.children[fluke_side+1].visible=true;
		paper.view.update();
	};
	
	// hide the currently selected tracing
	function hide_trace(){
		paper.project.activeLayer.children[fluke_side+1].visible=false;
		paper.view.update();
	};
	
	function edit_node_change(edit_type){
		paper.project.activeLayer.children[1].selected=false;
		paper.project.activeLayer.children[2].selected=false;
		if (edit_type>1){
			paper.project.activeLayer.children[fluke_side+1].selected=true;
		}
		paper.view.update();
	};
	
	// sends the tracing node data to the server to save.
	function save_tracings(){
		var left_fluke_path = paper.project.activeLayer.children[1].children[0];
		if (left_fluke_path.length==0){
			alert('You must create a left fluke tracing before saving.');
			return;
		}
		right_fluke_path = paper.project.activeLayer.children[2].children[0];
		if (right_fluke_path.length==0){
			alert('You must create a right fluke tracing before saving.');
			return;
		}		
		// convert left fluke group to JSON export string types
		var left_path=get_trace_path(1);
		var left_node_types=get_node_types(1);
		var right_path=get_trace_path(2);
		var right_node_types=get_node_types(2);
		var notch_cb = document.getElementById('notch_cb');
		var notch_open = notch_cb.checked;
		$.post( "../FinTraceServlet", {path_left:left_path, nodes_left:left_node_types, path_right:right_path, nodes_right:right_node_types, encounter_id:encounter_id, photo_id:photo_id, trace_type:trace_type, curled_left:curled_left, curled_right:curled_right, notch_open:notch_open}, aftersave); //, "json");
		// send here to server by AJAX
		modified=false;
	};
	
	function get_trace_path(flukeside){
		var segments=paper.project.activeLayer.children[flukeside].children[0].segments;
		var array_segments_points=[];
		for (var i=0;i<segments.length;i++){
			array_segments_points[array_segments_points.length]=[Math.round(segments[i].point.x),Math.round(segments[i].point.y)];
		}
		// Server side JSONObject requires JSON strings to be encased in braces, so add them before sending
		return '{path:'+JSON.stringify(array_segments_points)+'}';
	};
	
	function get_node_types(flukeside){
		var json_node_types=paper.project.activeLayer.children[flukeside].children[2].exportJSON({asString: false,precision:0})[1].children;
		var array_node_types=[];
		for (var i=0;i<json_node_types.length;i++){
			// return the node type and adjust the index by -2 as the server index range starts at "Point" = -2
			array_node_types[array_node_types.length]=json_node_types[i][1].data.id-2;
		}			
		// Server side JSONObject requires JSON strings to be encased in braces, so add them before sending
		return '{node_types:'+JSON.stringify(array_node_types)+'}';
	};
	
	function aftersave(data){
		alert(data);
	};

	function run_matching(){
		if (modified==true){
			alert('Before you can run a matching, you have to first save all current changes.');
			return;
		}		
		$.post( "../FlukeMatchServlet", {encounter_id:encounter_id, photo_id:photo_id}, aftermatched); //, "json");
	};	

	function aftermatched(data){
		try{
			var jparsed=JSON.parse(data);
		}catch(err){ // just show the data, probably an error message.
			alert(data);
			return;
		}		
		var encounters=JSON.parse(jparsed.encounters);
		var individuals=JSON.parse(jparsed.individuals);
		var dialog = document.getElementById('ecostatscom_match_dialog');
		if (dialog==null){
			dialog = document.createElement('div');
			dialog.setAttribute('style','z-index:9999;');
			dialog.setAttribute('id','ecostatscom_match_dialog');
			dialog.setAttribute('title','Matching Results');
			document.body.appendChild(dialog);
			$("#ecostatscom_match_dialog").dialog({
				 modal: false,
				 buttons: {
					 Ok: function() {
						 $( this ).dialog( "close" );
					 }
				 }
			});
		}
		dialog.innerHTML='';
		if (individuals.length>0){
			var p = document.createElement('p');
			var t = document.createTextNode('Matched Individuals:');
			p.appendChild(t);
			dialog.appendChild(p);
			for (var i=0;i<individuals.length;i++){
				var p = document.createElement('p');
				var t = document.createTextNode(individuals[i]);
				p.appendChild(t);
				dialog.appendChild(p);
			}
		}
		if (encounters.length>0){
			var p = document.createElement('p');
			var t = document.createTextNode('Matched Encounters:');
			p.appendChild(t);
			dialog.appendChild(p);
			for (var i=0;i<encounters.length;i++){
				var p = document.createElement('p');
				var t = document.createTextNode(encounters[i]);
				p.appendChild(t);
				dialog.appendChild(p);
			}
		}
		var win=$("#ecostatscom_match_dialog");
		win.dialog("open");
	};
	
	// AJAX return method that provides any prior fin tracings for the curren fluke image
	function loadtracing(data){
		if (data !== null){
			try{
				var jparsed;
				var cbc = document.getElementById('curled_cb');
				var cbn = document.getElementById('notch_cb');
				if (typeof data === 'object'){
					jparsed = data;
				}else{
					jparsed = JSON.parse(data);
				}
				if (jparsed.left_fluke==null || jparsed.left_fluke==undefined){
					curled_left=false;
					curled_right=false;
					cbc.checked=false;
					cbn.checked=false;
					return;
				}
				// draw the left path if any
				if (jparsed.left_fluke.x.length>0){
					draw_loaded_tracing(jparsed.left_fluke,1);
				}
				// draw the right path if any
				if (jparsed.right_fluke.x.length>0){
					draw_loaded_tracing(jparsed.right_fluke,2);
				}
				// update fluke notch open and curled values
				trace_type=jparsed.left_fluke.traceType;
				curled_left=jparsed.left_fluke.curled;
				curled_right=jparsed.right_fluke.curled;
				cbc.checked=curled_left;
				cbn.checked=jparsed.left_fluke.notchOpen;
			}catch(err){ // just show the data, probably an error message.
				alert(data);
				return;
			}finally{
				paper.view.update();
			}
		}
	};
	
	// draws any previously created fin tracing loaded from the server
	function draw_loaded_tracing(fluke,layer){
		if (fluke.x.length==fluke.types.length){	
			var tracing_group = paper.project.activeLayer.children[layer];
			var tracing_path = tracing_group.children[0];
			var tracing_points = tracing_group.children[1];
			var tracing_nodes = tracing_group.children[2];
			for (var i=0;i<fluke.x.length;i++){
				var point = new paper.Point(fluke.x[i], fluke.y[i]);
				// add the point to the path
				tracing_path.add(point);
				// add the point to the visible point circles
				add_point_path(tracing_points,point);
				// add a node type text element and set its node type
				var node_type = add_point_type(tracing_nodes,point);
				// adjust for local node array range: server value of "Point" starts at -2, local value for "Point" starts at 0.
				var node_id = fluke.types[i]+2;
				node_type.content = node_types[node_id][0].toUpperCase();
				node_type.data.id = node_id;
			}
		}		
	};

	// uses jQuery to close the div with the traced image
	function close_tracer(){
		if (modified == true){
			if (confirm('You have unsaved changes. Closing now will delete your changes. Continue closing?')==false){
				return;
			}
		}
		modified = false;
		var fluke_tracer = $("#fluke_tracer");
		fluke_tracer.hide('fast');
		location.reload();
	};

	// dynamically add needed CSS for the menus
	function addCss(){
		var sheet = document.createElement('style');
		sheet.innerHTML = '#pmenu_container {  width: 100%;  background: #7484ad;  background-color: #7484ad;  height: 26px;} #pmenu li:hover > div > ul {  display: block;  position: absolute;  top: -11px;  left: 80px;  padding: 10px 30px 30px 30px;  width: 120px;  z-index: 99;} #pmenu > li:hover > div > ul {  left: -30px;  top: 16px;  z-index: 99;} .mainmenu, .menuitem {	display: inline-block; 	margin: 0px 0 0px 0px; 	position: relative; 	padding-left: 0.2em;	padding-right: 1.5em; 	font-size: 12pt;	font-weight: bold;	height: 14pt; 	z-index: 100;	cursor: pointer;	border-bottom: } .menuitem, .menuitem{min-width: 190px;} .canvas{cursor:crosshair;}';
		document.body.appendChild(sheet);
	};
	
	// expose public methods below
	return {
		
		// method to build buttons above a source image the user can click on to show the tracing window
		// src_img_class : a class name which has the href path to the image
		// encounter_id : the GUID value for the relevant encounter
		// Note: IF encounter_id is null, pull the encounter_id from the href image path
		addFlukeTrace : function(src_img_class,encounterid){
			addCss();
			var imgs=$(src_img_class);
			for (var i=0;i<imgs.length;i++){
				//alert("addFlukeTrace: "+encounterid);
				// set an ID value for each image, so the same image does not get more than one button
				
				//var id = imgs[i].getAttribute("href").replace(/[://\\.]/g, "");
				
				var id = imgs[i].getAttribute("id");
				console.log(id);
				
				//var button = null;
				//if (button == null || button == undefined){
					// create a button element above each image that will be used to open the tracing window
					var button=document.createElement('input');
					button.setAttribute("id",(id+'-button'));
					button.setAttribute("type","button");
					button.setAttribute("class","fluke_trace");
					button.setAttribute("value","Trace Fluke");
					button.setAttribute("onclick","comEcostatsTracing.onFlukeTraceClick(event);");
					button.setAttribute("imgurl",imgs[i].getAttribute("href"));
					button.setAttribute("encounter_id",encounterid);
					// add the button to a paragraph element to give it some structural offset from the image.
					var p=document.createElement('p');
					p.appendChild(button);
					// insert the button just above the image
					//imgs[i].parentNode.insertBefore(p,imgs[i]);
				//}
			}
		},
		
		// method to call when a user clicks on the option (i.e. button element) to trace a fluke image
		onFlukeTraceClick : function (event) {
			var target = event_target(event);
			imgurl = target.getAttribute('imgurl');
			encounter_id = target.getAttribute('encounter_id');
			photo_id = target.getAttribute('id').replace('-button',''); 
			var fluke_tracer = $("#fluke_tracer");
			if (fluke_tracer.length==0){
				// if the fluke_tracer div (pseudo-window) does not yet exist, create it
				make_tracer_div();
				fluke_tracer = $("#fluke_tracer");
			}
			if (fluke_tracer!=null){
				fluke_tracer.show('fast', comEcostatsTracing.onShowTracer);
			}
		},

		// direct events from a user's menu selections
		onMenuSelect : function(event){
			//hide_node_types_select();
			var event_id=event_target(event).id;
			switch (event_id){
				case 'fluke_trace_close': close_tracer(); break;
				case 'fluke_trace_left':
				case 'fluke_trace_right': set_fluke_side(event_id); break;
				case 'show_active_tracing': show_trace(); break;
				case 'hide_active_tracing': hide_trace(); break;
				case 'clear_active_tracing': clear_fluke(); break;
				case 'fluke_trace_save': save_tracings(); break;
				case 'fluke_trace_match': run_matching(); break;
				case 'fluke_trace_add_nodes': node_edit_type=1; edit_node_change(node_edit_type); break;
				case 'fluke_trace_remove_nodes': node_edit_type=2; edit_node_change(node_edit_type); break;
				case 'fluke_trace_insert_nodes': node_edit_type=3; edit_node_change(node_edit_type); break;
				case 'fluke_trace_edit_nodes_type': node_edit_type=4; edit_node_change(node_edit_type); break;
			} 
			//hide_child_div(event); // hides the sub-menu
		},
		
		// public method for mouse over events to show sub menu DIV elements
		onMenuShow : function(event){
			//show_child_div(event);
		},
		
		// public method to call when the fluke_tracer divide is shown -- use to initialize the div element, image and paperscript
		onShowTracer : function (){
			var fluke_tracer = $(this);
			// size the image to fit within the current visible window space
			var innerwidth = window.innerWidth;
			var innerheight = window.innerHeight;
			fluke_tracer.css('background','white');
			fluke_tracer.css('width',innerwidth-100);
			fluke_tracer.css('height',innerheight-100);
			// 
			var canvas = make_canvas(fluke_tracer);
			// assign the canvas to the paperscript paper object
			paper.setup(canvas);
			// create a new tool to process mouse and keyboard events
			paper.tool = new paper.Tool();
			paper.tool.attach('mousedown',comEcostatsTracing.onImageMouseDown);		
			// center the div element in the current visible window space
			jqCenter(fluke_tracer);
			// get the current image to show from the url
			//imgurl='http://localhost:8080/caribwhale_data_dir/encounters/1/b/1b4dec1b-f100-4bf7-af55-655987b3ad91/gray.png';
			add_image(imgurl);
			if (first_load==true){
				// try to deal with paperscript and jQuery combined problem that otherwise results in not the full image appearing on first load
				paper.setup(canvas);
				add_image(imgurl);
				first_load=false;
			}
			set_fluke_side('fluke_trace_left');
			// load and existing path tracing for this encounter image
			$.post('../FlukeGetTracing', {encounter_id:encounter_id, photo_id:photo_id}, loadtracing);//, "json");
		},
		
		onDraw : function(event){
			var div=document.getElementById('removediv');
			//div.style.visibility='hidden';
			div.style.display='none';
			var div=document.getElementById('drawdiv');
			div.style.visibility='visible';
			div.style.display='';
			var node_edit_select=document.getElementById('node_edit_type');
			node_edit_select.selectedIndex=0;
			node_edit_type=1;
		},
				
		// Shows the tools tab panel
		onImageTools : function(event){
			var div=document.getElementById('drawdiv');
			//div.style.visibility='hidden';
			div.style.display='none';
			div=document.getElementById('removediv');
			div.style.visibility='visible';
			div.style.display='';	
			node_edit_type=0;
			paper.view.update();
		},
		
		// Flip the image horizontally
		onFlip : function(event){
			// flip matrix: -1,0,0,1,0,0
			paper.project.activeLayer.children[0].matrix['a']=-1*paper.project.activeLayer.children[0].matrix['a'];
			paper.project.activeLayer.children[0].applyMatrix=true;
			paper.project.activeLayer.children[0].transformContent=true;
			paper.view.update();
		},	
		
		rangechange : function(event){
			var range=document.getElementById('myRange');
			var items=paper.project.activeLayer.children;
			items[0].scale(1.0);
			items[0].scale(range.value/100); // base raster	
			paper.view.update();
		},
		
		// rotate the image
		rotatechange : function(event){
			var rotate=document.getElementById('myRotate');
			var items=paper.project.activeLayer.children;
			items[0].rotate(-items[0].matrix.rotation);
			items[0].rotate(rotate.value); // base raster	
			paper.view.update();
		},
		
		// on change of select list of marked fluke node types
		onChange : function(event){
			hide_node_types_select();
			var tracing_group = tracingGroup();
			var tracing_types = tracing_group.children[2];
			var hit_result = tracing_types.hitTest(current_point);
			if (hit_result){
				var selectnode = document.getElementById('comEcostatsTracingNodeSelect');
				hit_result.item.content = node_types[selectnode.selectedIndex][0].toUpperCase();
				hit_result.item.data.id = selectnode.selectedIndex;
				paper.view.update();
			};
		},
		
		onCurlChange : function(event){
			var cb = document.getElementById(event_target(event).id);
			if (fluke_side==0){
				curled_left=cb.checked;
			}else{
				curled_right=cb.checked;
			}
		},
		
		// closes the select list of marked fluke node types using the escape key
		onKeyDown : function(event){
		    var x = event.which || event.keyCode; // event.keyCode needed for IE8 or earlier
		    if (x == 27) {  // 27 is the ESC key
		    	hide_node_types_select();
		    }
		},
		
		onImageMouseDown : function (event) {
			hide_node_types_select();
			var hit_result = paper.project.activeLayer.children[fluke_side+1].hitTest(event.point, hitOptions);
			if (node_edit_types[node_edit_type]=='addnode'){
				// Add a segment to the path at the position of the mouse:
				var tracing_group = paper.project.activeLayer.children[fluke_side+1];
				var tracing_path = tracing_group.children[0];
				tracing_path.add(event.point);
				add_point_path(tracing_group.children[1],event.point);
				add_point_type(tracing_group.children[2],event.point);
				paper.view.update();
				show_node_type_select(event.point);
				current_point=event.point;
				modified=true;
			}else if (node_edit_types[node_edit_type]=='insertnode'){
				// insert a new node
				var tracing_group = paper.project.activeLayer.children[fluke_side+1];
				var tracing_path = tracing_group.children[0];
				var np = tracing_path.getNearestPoint(event.point);
				var segment = tracing_path.getLocationOf(np);
				var index = segment._segment2.index;
				tracing_path.insert(index,event.point);
				add_point_path(tracing_group.children[1],event.point,index);
				add_point_type(tracing_group.children[2],event.point,index);
				paper.view.update();
				show_node_type_select(event.point);
				current_point=event.point;
				modified=true;
			}else if (hit_result && node_edit_types[node_edit_type]=='typenode'){
				// change the type definition at the selected node 
				show_node_type_select(event.point);
				current_point=event.point;
			}else if (hit_result && node_edit_types[node_edit_type]=='removenode'){
				// remove the selected node
				segment = path = null;	
				if (hit_result.type == 'fill') {
					var hit_index=hit_result.item.index;
					var tracing_group = paper.project.activeLayer.children[fluke_side+1];
					// remove the item hit from the three tracing elements
					tracing_group.children[0].removeSegment(hit_index); // tracing line
					tracing_group.children[1].removeChildren(hit_index,hit_index+1); // tag point circle group
					tracing_group.children[2].removeChildren(hit_index,hit_index+1); // tag text description
					paper.view.update();
					modified=true;
				};
				return;		
				if (hit_result) {
					path = hit_result.item;
					if (hit_result.type == 'segment') {
						segment = hit_result.segment;
					} else if (hit_result.type == 'stroke') {
						var location = hit_result.location;
						segment = path.insert(location.index + 1, event.point);
						path.smooth();
					}
				}
				if (movePath)
					project.activeLayer.addChild(hit_result.item);
				modified=true;
			}
		}
		
	};
})();



