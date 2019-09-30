//TODO: Needs top-level file documentation

//Major Features Checklist

//Pull/parse live data
  //Switch all categorical data to enums
//Add social symbols
//Format text cleanly
//Improve (smooth) zoom functionality (pan functionality seems good)
  //Click on node to fix zoom
//Add functionality to ensure any graph or reasonable size may be visualized
//Add functionality for two parents nodes (difficult)

//TODO: Delete data spec
const DATA = {
    "name": "Lion 1",
    "gender": "female",
    "role": "alpha",
    "children": [
	{
	    "name": "Lion 2",
	    "gender": "female",
	    "children": [
		{
		    "name": "Lion 5",
		    "gender": "male"
		},
		{
		    "name": "Lion 6",
		    "gender": ""
		}
	    ]
	},
	{
	    "name": "Lion 3",
	    "gender": "male",
	    "children": [
		{
		    "name": "Lion 11",
		    "gender": ""
		}
	    ]		
	},
	{
	    "name": "Lion 4",
	    "gender": "female",   
	    "children": [
		{
		    "name": "Lion 7",
		    "gender": "female",
		    "children": [
			{
			    "name": "Lion 9",
			    "gender": "male"
			},
			{
		   	    "name": "Lion 10",
			    "gender": "female"
			},
			{
		   	    "name": "Lion 12",
			    "gender": "female"
			}
		    ]
		}
	    ]
	},
	{
	    "name": "Lion 8",
	    "gender": "female"
	}
    ]
};

function setupFamilyTree(individualID) {
    let ft = new FamilyTree(individualID);
    ft.applySocialData();
}

class FamilyTree {
    constructor(individualID) {
	this.id = individualID;

	//SVG dimensions
	this.svgWidth = 960;
	this.svgHeight = 500;

	//G dimensions
	this.margin = {top: 20, right: 120, bottom: 20, left: 120};
	this.width = this.svgWidth - this.margin.right - this.margin.left,
	this.height = this.svgHeight - this.margin.top - this.margin.bottom;

	this.i = 0; //TODO: Rename
	this.duration = 750;
	this.popup = false;

	//Pan Attributes
	this.prevPos = [0, 0];
	
	//Zoom attirbutes
	this.zoomFactor = 1000;
	this.zoom = d3.zoom()
	    .scaleExtent([0.5, 5])
	    .translateExtent([
		[-this.width * 2, -this.height * 2],
		[this.width * 2, this.height * 2]
	    ])
	    .wheelDelta(() => this.wheelDelta());
	
	//Set upon data retrieval
	this.numNodes;
	this.radius;
	this.maxRadius = 40;
	this.scalingFactor = 25; //TODO: Tune this value

	this.tree;
	this.root;
	this.svg;
	this.tooltip;

	//Node style attributes
	this.nodeColor = "#ffffff";
	this.collapsedNodeColor = "#d3d3d3";
	
	this.maleColor = "steelblue";
	this.femaleColor = "palevioletred";
	this.defGenderColor = "#939393";
    }

    //TODO: Consider moving this outside the scope of the class.. The obj references are clunky
    applySocialData(individualID, callback) {
	d3.json(wildbookGlobals.baseUrl + "/api/jdoql?" +
		encodeURIComponent("SELECT FROM org.ecocean.social.Relationship WHERE (this.type == \"social grouping\") && " +
				   "(this.markedIndividualName1 == \"" + this.id + "\" || this.markedIndividualName2 == \"" +
				   this.id + "\")"), (error, json) => this.graphSocialData(error, json));
    }

    graphSocialData(error, json) {
	if (error) {
	    return console.error(error);
	}

	//If there are no familial relationships, default to social relationships table
	if (json.length < 1) { //TODO: Consider defaulting to this...
	    $("#familyDiagram").hide();
	    $("#communityTable").show();
	    $("#familyDiagramTab").removeClass("active");
	    $("#communityTableTab").addClass("active");
	    this.showIncompleteInformationMessage();
	}
	else if (json.length >= 1) {
	    this.tree = d3.tree()
		.size([this.height, this.width]);
	    
	    this.svg = d3.select("#familyDiagram").append("svg")	        
		.attr("width", this.svgWidth)
		.attr("height", this.svgHeight)
	    	.call(this.zoom.transform, d3.zoomIdentity.translate(this.margin.left, this.margin.top))
		.call(this.zoom.on("zoom", () => {
		    this.svg.attr("transform", d3.event.transform)
		}))
		.append("g")
		.attr("transform", "translate(" + this.margin.left + "," + this.margin.top + ")");
	    
	    //Define the tooltip div
	    this.tooltip = d3.select("#familyDiagram").append("div")
	    	.attr("transform", "translate(" + this.margin.left + "," + this.margin.top + ")")
		.attr("class", "tooltip")				
		.style("opacity", 0);
	    
	    this.root = d3.hierarchy(DATA, d => d.children); //TODO: Change to this.data when possible
	    this.root.x0 = this.height / 2;
	    this.root.y0 = 0;

	    this.updateTree(this.root);
	}
    }

    wheelDelta () {
	return -d3.event.deltaY * (d3.event.deltaMode ? 120 : 1 ) / this.zoomFactor;
    }

    updateTree(source) {
	//Assign node values
	let treeData = this.tree(this.root);
	console.log(treeData);
	
	//Compute the new tree layout
	let nodes = treeData.descendants(),
	    links = treeData.descendants().slice(1);

	this.calcNodeSize(nodes);

	//Get reference to all nodes
	let allNodes = this.svg.selectAll("g.node")
	    .data(nodes, d => d.id || (d.id = ++this.i));

	//Handle nodes
	let updateNodes = this.addNewNodes(allNodes, source)
	    .merge(allNodes);
	this.updateNodes(updateNodes);
	this.removeStaleNodes(allNodes, source);
	
	//Get reference to all links
	let link = this.svg.selectAll("path.link")
	    .data(links, d => d.id);

	//Handle links
	let updateLinks = this.addNewLinks(link, source)
	    .merge(link);
	this.updateLinks(updateLinks);
	this.removeStaleLinks(link, source);
	
	// Stash the old positions for transition.
	nodes.forEach(d => {
	    d.x0 = d.x;
	    d.y0 = d.y;
	});
    }

    calcNodeSize(nodes) {
	try {
	    let defaultNodeLen = 10;
	    let numNodes = nodes.length || defaultNodeLen; //Defaults to 10 
	    this.radius = this.maxRadius * Math.pow(Math.E, -1 * (numNodes / this.scalingFactor));
	}
	catch(error) {
	    console.error(error);
	}
    }

    addNewNodes(newNodes, source) {
	//Enter any new nodes at parent's prev. position
	let nodeEnter = newNodes.enter().append("g")
	    .attr("class", "node")
	    .attr("transform", d => "translate(" + source.y0 + "," + source.x0 + ")")
	    .on("click", d => this.click(d))
	    .on("mouseover", d => this.handleMouseOver(d))					
            .on("mouseout", d => this.handleMouseOut(d));
	
	this.colorNewNodes(nodeEnter);
	this.formatText(nodeEnter);

	return nodeEnter;
    }

    handleMouseOver(d) {
	if (!this.popup) {
	    this.tooltip.transition()
		.duration(200)
		.style("opacity", .9);

	    //TODO: Remove this hardcoding
	    this.tooltip
		.style("left", d3.event.layerX + 30 + "px")		
		.style("top", d3.event.layerY - 20 + "px")
		.html("<b>Encounters:</b>\n None");

	    this.popup = true;
	}
    }	

    handleMouseOut(d) {
	this.popup = false;
	this.tooltip.transition()		
            .duration(200)		
            .style("opacity", 0);
    }

    colorNewNodes(nodeEnter) {
	//Color collapsed nodes
	nodeEnter.append("circle")
	    .attr("r", 1e-6)
	    .style("fill", d => this.colorCollapsed(d))
	    .style("stroke", d => this.colorGender(d));

	//TODO: Add in symbols
	//nodeEnter.append("svg:image")
	//    .attr("xlink:href", "http://lorempixel.com/200/200/")
	//    .attr("width", 20)
	//    .attr("height", 20)
    }

    colorCollapsed(d) {
	return (d && d._children) ? this.collapsedNodeColor : this.nodeColor;
    }

    colorGender(d) {
	try {
	    let gender = d.data.gender;
	    switch (gender.toUpperCase()) {
	        case "FEMALE": return this.femaleColor;
	        case "MALE": return this.maleColor;
	        default: return this.defGenderColor; //Grey
	    }
	}
	catch(error) {
	    console.error(error);
	}
    }

    //TODO: Stub function
    formatText(nodeEnter) {
	//Style node text
//	let boundedLength = 2 * this.radius * Math.cos(Math.PI / 4); //Necessary dimensions of bounding rectangle
	nodeEnter.append("text")
//	    .attr(d => d.x - (boundedLength / 2))
//	    .attr(d => d.y - (boundedLength / 2))
//	    .attr("width", boundedLength)
//	    .attr("height", boundedLength)
	    .attr("dy", ".5em") //Vertically centered
	    .text(d => d.data.name)
	    .style("fill-opacity", 1e-6);
    }

    updateNodes(updatedNodes) {	
	//Transition nodes to their new position.
	let nodeUpdate = updatedNodes.transition()
	    .duration(this.duration)
	    .attr("transform", d => "translate(" + d.y + "," + d.x + ")");

	nodeUpdate.select("circle")
	    .attr("r", this.radius)
	    .style("fill", d => this.colorCollapsed(d)); //Updates color if collapsed

	nodeUpdate.select("text")
	    .style("fill-opacity", 1);

	return nodeUpdate;
    }
    
    removeStaleNodes(removedNodes, source) {
	//Transition exiting nodes to the parent's new position.
	let nodeExit = removedNodes.exit().transition()
	    .duration(this.duration)
	    .attr("transform", d => "translate(" + source.y + "," + source.x + ")")
	    .remove();

	//TODO: Consider better solution than shrinking for invisibility
	nodeExit.select("circle")
	    .attr("r", 1e-6);

	nodeExit.select("text")
	    .style("fill-opacity", 1e-6);

	return nodeExit;
    }

    addNewLinks(link, source) {
	//Enter any new links at the parent's previous position.
	return link.enter().insert("path", "g")
	    .attr("class", "link")
	    .attr("d", d => {
		let o = {x: source.x0, y: source.y0};
		return this.diagonal(o, o);
	    });
    }

    diagonal(s, d) {
	return `M ${s.y} ${s.x}
                C ${(s.y + d.y) / 2} ${s.x},
                  ${(s.y + d.y) / 2} ${d.x},
                  ${d.y} ${d.x}`;
    }

    updateLinks(linkUpdate) {
	//Transition links to their new position.
	linkUpdate.transition()
	    .duration(this.duration)
	    .attr("d", d => this.diagonal(d, d.parent));
    }

    removeStaleLinks(link, source) {
	//Transition exiting nodes to the parent'ns new position.
	link.exit().transition()
	    .duration(this.duration)
	    .attr("d", d => {
		let o = {x: source.x, y: source.y};
		return this.diagonal(o, o);
	    })
	    .remove();
    }

    //Toggle children on click.
    click(d) {
	if (d.children) { //Collapse child nodes
	    d._children = d.children;
	    d.children = null;
	}
	else {//Expand child nodes
	    d.children = d._children;
	    d._children = null;
	}
	
	this.updateTree(d);
    }

    showIncompleteInformationMessage() {
	$("#familyDiagram").html("<h4>There are currently no known familial relationships for this Marked Individual</h4>")
    };
}
