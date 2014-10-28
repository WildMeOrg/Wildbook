/**
 * @filename: tracing.js
 * @purpose: Contains javascript code to control canvas drawing using paper script (paper.js)
 * @author Ecological Software Solutions LLC
 * @version 0.1 Alpha
 * @copyright 2014 
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
	var node_types={0:'point',1:'tip',2:'notch',3:'nick',4:'gouge_start',5:'gouge_end',6:'scallop_start',7:'scallop_end',8:'wave_start',9:'wave_end',10:'missing_start',11:'missing_end',12:'scar',13:'hole',14:'invisible_start',15:'invisible_end'};
	var segment, path, current_point;
	var movePath = false;
	var is_drawing=false;
	var hit_test=false;
	var remove_node=false;
	var node_edit_type=1;
	var nodeEditTypes={0:'none',1:'addnode',2:'typenode',3:'removenode',4:'insertnode'};
	
	// returns the currently selected tracing group
	function tracingGroup(){
		var active_tracing=document.getElementById('finlr').selectedIndex+1
		var tracing_group = paper.project.activeLayer.children[active_tracing];
		return tracing_group;
	};
	
	// Adds an div and select list to let the user define the type of structure at each node of the tracing. 
	function showNodeTypeSelect(point){
		var selectdiv=document.getElementById('comEcostatsTracingNodeDiv')
		// create the div and select list if it does not exist
		if (selectdiv==undefined || selectdiv==null){
			selectdiv = document.createElement('div');
			selectdiv.setAttribute('id','comEcostatsTracingNodeDiv');
			//selectdiv.setAttribute('width','200px');
			selectdiv.style.position='relative'
			selectdiv.style.visibility='hidden'; 
			var selectNode = document.createElement('select');
			selectNode.setAttribute("name", "comEcostatsTracingNodeSelect");
			selectNode.setAttribute("id", "comEcostatsTracingNodeSelect");
			/* setting an onchange event */
			var option;
			for (node_type in node_types){
				option = document.createElement("option");
				option.setAttribute("value", node_type);
				option.innerHTML = node_types[node_type];
				selectNode.appendChild(option);
			}
			selectdiv.appendChild(selectNode);
			paper.project.view.element.parentNode.appendChild(selectdiv);
			selectNode.onchange=comEcostatsTracing.onChange; 
		}
		// bring to front of all other elements
		selectdiv.style['z-index']='9999';
		// position the div where the user clicked the mouse
		var y=Math.round(point.y)-paper.project.view.viewSize.height;
		selectdiv.style.top=y+'px';
		selectdiv.style.left=Math.round(point.x)+'px';
		// set the select list to the node type at the selected node 
		var active_tracing=document.getElementById('finlr').selectedIndex+1;
		var tracing_group = paper.project.activeLayer.children[active_tracing];
		var node_types_group = tracing_group.children[1];
		var hitResult = node_types_group.hitTest(point);
		if (hitResult){
			// get the select list
			var selectNode=document.getElementById('comEcostatsTracingNodeSelect');
			// set the select list selected item to the node type at the node where the user clicked with the mouse 
			selectNode.selectedIndex=hitResult.item.data.id;
		}
		selectdiv.style.visibility = "visible";
	};
	
	// hides the div element with the select node type select list
	function hideNodeTypeSelect(point){
		var selectdiv=document.getElementById('comEcostatsTracingNodeDiv')
		if (selectdiv!=undefined){
			selectdiv.style.visibility='hidden';
		}
	};

	// expose public methods below
	return {
		
		onDraw : function(evt){
			var div=document.getElementById('removediv');
			div.style.visibility='hidden';
			div.style.display='none';
			var div=document.getElementById('drawdiv');
			div.style.visibility='visible';
			div.style.display='';
			var node_edit_select=document.getElementById('node_edit_type');
			node_edit_select.selectedIndex=0;
			node_edit_type=1;
		},
		
		// clears the selected fluke drawing
		onClear : function(evt){
	        var fluke_select=document.getElementById('finlr');
	        var sel=fluke_select.selectedIndex;
	        var option=fluke_select[sel].textContent;
	        if (paper.project.activeLayer.children[sel+1]!=undefined){
		        if (confirm('Delete ' + option +'?')!=false){
		            this.clearPath(sel+1);       
		            paper.view.update();
		        }
		    }else{
		        alert("There are no fluke contours recorded for "+option+".");
		    }
		},
		
		// clear Path
		clearPath : function(index){
			// remove the current path and node type text items from the current group
			paper.project.activeLayer.children[index].removeChildren();
			paper.view.update();
			// create a new empty path
			var aPath = new paper.Path();
			aPath.strokeColor = 'red';
			aPath.strokeWidth = 2;	   
			// create a group for each the node type list to show to the user
			var nodeTypes=new paper.Group();
			// add it to the current group
			paper.project.activeLayer.children[index].addChild(aPath);
			paper.project.activeLayer.children[index].addChild(nodeTypes);
		},
		
		// adds the image to the canvas
		addImage : function(url){
			// if the current project does not have an image layer, add it and create all the needed paths and groups
			if (paper.project.activeLayer.children.length==0){
				// set the view of the paper canvas to some default size
				paper.view.viewSize.width=800;
				paper.view.viewSize.height=800;			 	
				// load the image url, and center the image
				new paper.Raster(url,paper.view.center);
				// Create new fluke side paths, when the script is executed:
				var leftPath = new paper.Path();
				var rightPath = new paper.Path();
				// set some standard colors
				leftPath.strokeColor = 'red';
				leftPath.strokeWidth = 2;
				rightPath.strokeColor = 'red';
				rightPath.strokeWidth = 2;
				// create a group for each node type list to show to the user
				var leftNodeTypes=new paper.Group();
				var rightNodeTypes=new paper.Group();
				// place each path in to a group; each group will also contain node type text and other info for each path
				new paper.Group(leftPath,leftNodeTypes);
				new paper.Group(rightPath,rightNodeTypes);
			}else{
				// replace the current raster image with the new url
				paper.project.activeLayer.children[0].source=url;
				paper.project.activeLayer.children[0].position=paper.view.center;
				paper.view.update();
			}
		},
		
		// Shows the tools tab panel
		onImageTools : function(evt){
			var div=document.getElementById('drawdiv');
			div.style.visibility='hidden';
			div.style.display='none';
			div=document.getElementById('removediv');
			div.style.visibility='visible';
			div.style.display='';	
			node_edit_type=0;
			paper.view.update();
		},
		
		editNodeChange : function(evt){
			var node_edit_select=document.getElementById('node_edit_type');
			node_edit_type=node_edit_select.selectedIndex+1;
			paper.project.activeLayer.children[1].selected=false;
			paper.project.activeLayer.children[2].selected=false;
			var active_tracing=document.getElementById('finlr').selectedIndex+1
			if (node_edit_type>1){
				paper.project.activeLayer.children[active_tracing].selected=true;
			}
			paper.view.update();
		},
		
		zoom : function(evt){
			var pnt;
			var izoom=document.getElementById('iZoom');
			var items=paper.project.activeLayer.children;
			var scale=izoom.value/100.0;
			items[0].scale(1.0);
			items[0].scale(scale); // base raster
			if (items[1].firstSegment){
				items[1].scale(1.0);
				pnt=items[1].lastSegment.point;
				items[1].scale(scale)//,pnt);
			}
			if (items[2].firstSegment){
				items[2].scale(1.0);
				pnt=items[2].lastSegment.point;
				items[2].scale(scale)//,pnt);
			}
			paper.view.update();
		},
		
		zoomIn : function(evt){
			var pnt;
			var items=paper.project.activeLayer.children;
			items[0].scale(1.1); // base raster
			if (items[1].firstSegment){
				pnt=items[1].lastSegment.point;
				items[1].scale(1.1)//,pnt);
			}
			if (items[2].firstSegment){
				pnt=items[2].lastSegment.point;
				items[2].scale(1.1)//,pnt);
			}
			paper.view.update();
		},
		
		zoomOut : function(evt){
			var pnt;
			var items=paper.project.activeLayer.children;
			items[0].scale(0.9); // base raster
			if (items[1].firstSegment){
				pnt=items[1].lastSegment.point;
				items[1].scale(0.9)//,pnt);
			}
			if (items[2].firstSegment){
				pnt=items[2].lastSegment.point;
				items[2].scale(0.9)//,pnt);
			}
			paper.view.update();
		},
		
		// Flip the image horizontally
		onFlip : function(evt){
			// flip matrix: -1,0,0,1,0,0
			paper.project.activeLayer.children[0].matrix['a']=-1*paper.project.activeLayer.children[0].matrix['a'];
			paper.project.activeLayer.children[0].applyMatrix=true;
			paper.project.activeLayer.children[0].transformContent=true;
			paper.view.update();
		},	
		
		rangechange : function(evt){
			var range=document.getElementById('myRange');
			var items=paper.project.activeLayer.children;
			items[0].scale(1.0);
			items[0].scale(range.value/100); // base raster	
			paper.view.update();
		},
		
		// rotate the image
		rotatechange : function(evt){
			var rotate=document.getElementById('myRotate');
			var items=paper.project.activeLayer.children;
			items[0].rotate(-items[0].matrix.rotation);
			items[0].rotate(rotate.value); // base raster	
			paper.view.update();
		},
		
		// show the currently selected tracing (undoes hideTrace)
		showTrace : function(evt){
			var active_tracing=document.getElementById('finlr').selectedIndex+1;
			paper.project.activeLayer.children[active_tracing].visible=true;
			paper.view.update();
		},
		
		// hide the currently selected tracing
		hideTrace : function (evt){
			var active_tracing=document.getElementById('finlr').selectedIndex+1;
			paper.project.activeLayer.children[active_tracing].visible=false;
			paper.view.update();
		},
		
		saveTracings : function(){
			var str=paper.project.activeLayer.children[1].children[0].pathData;
			// convert left fluke group to export string types
			var jso=paper.project.activeLayer.children[1].exportJSON({ asString: true, precision: 2 });
			var svg=paper.project.activeLayer.children[1].exportSVG({ asString: true, precision: 2, matchShapes: false });
			// convert rith fluke group to export string types
			jso=jso+paper.project.activeLayer.children[2].exportJSON({ asString: true, precision: 2 });
			svg=svg+paper.project.activeLayer.children[2].exportSVG({ asString: true, precision: 2, matchShapes: false });
			alert ('Not really saved. Trial version only. ;-)');
			// send here to server by AJAX
		},
		
		onChange : function(event){
			hideNodeTypeSelect();
			var tracing_group = tracingGroup();
			var tracing_types = tracing_group.children[1];
			var hitResult = tracing_types.hitTest(current_point);
			if (hitResult){
				var selectNode = document.getElementById('comEcostatsTracingNodeSelect');
				hitResult.item.content = node_types[selectNode.selectedIndex][0].toUpperCase();
				hitResult.item.data.id = selectNode.selectedIndex;
				paper.view.update();
			}			
		},
		
		onMouseDown : function (event) {
			hideNodeTypeSelect();
			var active_tracing=document.getElementById('finlr').selectedIndex+1;
			var hitResult = paper.project.activeLayer.children[active_tracing].hitTest(event.point, hitOptions);
			if (nodeEditTypes[node_edit_type]=='addnode'){
				// Add a segment to the path at the position of the mouse:
				var active_tracing=document.getElementById('finlr').selectedIndex+1
				var tracing_group = paper.project.activeLayer.children[active_tracing];
				var tracing_path = tracing_group.children[0];
				var tracing_types = tracing_group.children[1];
				tracing_path.add(event.point);
				var text = new paper.PointText(event.point);
				text.justification = 'center';
				text.fillColor = 'black';
				if (tracing_path.segments.length==1){
					text.content = 'T';
					text.data.id=1;
				}else{
					text.content = 'P';
					text.data.id=0;
				}
				tracing_types.addChild(text);
				paper.view.update();
			}else if (hitResult && nodeEditTypes[node_edit_type]=='typenode'){
				// change the type definition at the selected node 
				showNodeTypeSelect(event.point);
				current_point=event.point;
			}else if (hitResult && nodeEditTypes[node_edit_type]=='removenode'){
				// remove the selected node
				segment = path = null;	
				if (hitResult.type == 'segment') {
					hitResult.segment.remove();
				};
				return;		
				if (hitResult) {
					path = hitResult.item;
					if (hitResult.type == 'segment') {
						segment = hitResult.segment;
					} else if (hitResult.type == 'stroke') {
						var location = hitResult.location;
						segment = path.insert(location.index + 1, event.point);
						path.smooth();
					}
				}
				if (movePath)
					project.activeLayer.addChild(hitResult.item);
			}else if (hitResult && nodeEditTypes[node_edit_type]=='insertnode'){
				// insert a new node
				var hitResult = paper.project.hitTest(event.point, {segments:true, tolerance: 20});
				if (!hitResult){
					alert('missed')
					return
				}
				paper.hitResult.item.insert(hitResult.segment.index+1,event.point);			
			}
		},
		
	    // onMouseUp
		onMouseUp : function (event) {
			if (nodeEditTypes[node_edit_type]=='addnode'){
				//var cb=document.getElementById('shownodes');
				//if (cb.checked){
				//	var myCircle = new Path.Circle({
				//		center: event.point,
				//		radius: 5
				//	});
				//	myCircle.strokeColor = 'red';
				//}
			}
		}
		
	}
})();



