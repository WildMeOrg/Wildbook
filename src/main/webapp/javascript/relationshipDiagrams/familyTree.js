//TODO: Needs top-level file documentation

//Major Features Checklist

//Pull/parse live data
  //Switch all categorical data to enums
//Add social symbols
//Format text cleanly
//Fix depth normalization
//Improve (smooth) zoom functionality (pan functionality seems good)
  //Click on node to fix zoom
//Add functionality to ensure any graph or reasonable size may be visualized

//TODO: Delete data spec
const data = [
    {
	"name": "Lion 1",
	"gender": "female",
	"role": "alpha",
	"parent": "null",
	"children": [
	    {
		"name": "Lion 2",
		"gender": "female",
		"parent": "Lion 1",
		"children": [
		    {
			"name": "Lion 5",
			"gender": "male",
			"parent": "Lion 2"
		    },
		    {
			"name": "Lion 6",
			"gender": "",
			"parent": "Lion 2"
		    }
		]
	    },
	    {
		"name": "Lion 3",
		"gender": "male",
		"parent": "Lion 1",
		"children": [
		    {
			"name": "Lion 11",
			"gender": "",
			"parent": "Lion 3"
		    }
		]		
	    },
	    {
		"name": "Lion 4",
		"gender": "female",   
		"parent": "Lion 1",
		"children": [
		    {
			"name": "Lion 7",
			"gender": "female",
			"parent": "Lion 4",
			"children": [
			    {
				"name": "Lion 9",
				"gender": "male",
				"parent": "Lion 7"
			    },
			    {
		   		"name": "Lion 10",
				"gender": "female",
				"parent": "Lion 7"
			    },
			    {
		   		"name": "Lion 12",
				"gender": "female",
				"parent": "Lion 7"
			    }
			]
		    }
		]
	    },
	    {
		"name": "Lion 8",
		"gender": "female",
		"parent": "Lion 1"
	    }
	]
    }
];

function setupFamilyTree(individualID) {
    let ft = new FamilyTree(individualID);
    ft.applySocialData();
}

class FamilyTree {
    constructor(individualID) {
	this.id = individualID;
	this.margin = {top: 20, right: 120, bottom: 20, left: 120};

	this.svgWidth = 960;
	this.svgHeight = 500;
	this.width = this.svgWidth - this.margin.right - this.margin.left,
	this.height = this.svgHeight - this.margin.top - this.margin.bottom;

	this.i = 0; //TODO: Rename
	this.duration = 750;
	this.popup = false;

	//Zoom attirbutes
	this.scale_extent = [0.1, 5];
	this.scale_grad = 0.5;
	this.start_scale = 1;
	this.intercept = this.start_scale * (1 - this.scale_grad);
	this.zoom_cp = [this.svgWidth / 2, this.svgHeight / 2];
	this.isZoom = false;
	
	//Set upon data retrieval
	this.numNodes = null;
	this.maxRadius = 40;
	this.scalingFactor = 25; //TODO: Tune this value
	this.radius = null;

	this.tree = null;
	this.diagonal = null;
	this.svg = null;
	this.tooltip = null;
	this.root = null;

	//CSS Attributes
	this.collapsedNodeColor = '#d3d3d3';
	this.nodeColor = '#fffff';
	
	this.maleColor = 'steelblue';
	this.femaleColor = 'palevioletred';
	this.defaultGenderColor = '#939393';
    }

    //TODO: Consider moving this outside the scope of the class.. The obj references are clunky
    applySocialData(individualID, callback) {
	d3.json(wildbookGlobals.baseUrl + "/api/jdoql?" +
		encodeURIComponent("SELECT FROM org.ecocean.social.Relationship WHERE (this.type == \"social grouping\") && " +
				   "(this.markedIndividualName1 == \"" + this.id + "\" || this.markedIndividualName2 == \"" +
				   this.id + "\")"), (error, json) => this.graphSocialData(error, json, this));
    }

    graphSocialData(error, json, self) {
	if (error) {
	    return console.error(error);
	}

	//If there are no familial relationships, default to social relationships table
	if (json.length < 1) { //TODO: Consider defaulting to this...
	    $("#familyDiagram").hide();
	    $("#communityTable").show();
	    $("#familyDiagramTab").removeClass("active");
	    $("#communityTableTab").addClass("active");
	    self.showIncompleteInformationMessage();
	}
	else if (json.length >= 1) {
	    self.tree = d3.layout.tree()
		.size([self.height, self.width]);

	    self.diagonal = d3.svg.diagonal()
		.projection(d => [d.y, d.x]);

	    self.svg = d3.select("#familyDiagram").append("svg")	        
		.attr("width", this.svgWidth)
		.attr("height", this.svgHeight)
		.call(d3.behavior.zoom().on("zoom", () => self.redraw(this.svgWidth, this.svgHeight))) //Handles zoom and pan behavior
		.append("g")
		.attr("transform", "translate(" + self.margin.left + "," + self.margin.top + ")");

	    //Define the div for the tooltip
	    self.tooltip = d3.select("#familyDiagram").append("div")
	    	.attr("transform", "translate(" + self.margin.left + "," + self.margin.top + ")")
		.attr("class", "tooltip")				
		.style("opacity", 0);
	    
	    self.root = data[0]; //TODO: Change to self.data when possible
	    self.root.x0 = self.height / 2;
	    self.root.y0 = self.width / 2;

	    self.updateTree(self.root);
	    d3.select("#familyDiagram").style("height", "500px"); //TODO: Remove?
	}
    }

    redraw(width, height) {
	let scale = d3.event.scale;
	let trans = d3.event.translate;
	
//	let new_scale = Math.max(this.scale_grad * scale + this.intercept, this.scale_extent[0]);
	
//	if (new_scale == this.scale_extent[0]) zoom.scale((this.scale_extent[0] - this.intercept) / scale_grad)
	
//	let scale = Math.pow(d3.event.scale, 0.25);
//	let trans = Math.pow(d3.event.scale, 0.25);
//	let translateY = (height - (height * scale))/2;
//	let translateX = (width - (width * scale))/2;
	this.svg.attr("transform", "translate(" + trans + ")" + " scale(" + scale + ")");
    }

    updateTree(source) {
	//Compute the new tree layout
	let nodes = this.tree.nodes(this.root).reverse(),
	    links = this.tree.links(nodes);

	this.calcNodeSize(nodes);

	//TODO: Re-enable to fix animations, but make centered
	//Normalize depth
	nodes.forEach(d => d.y = d.depth * 200); //TODO: Edit to support arbitrarily long depth

	//Get reference to all nodes
	let all_nodes = this.svg.selectAll("g.node")
	    .data(nodes, d => d.id || (d.id = ++this.i));

	//Handle nodes
	let nodeEnter = this.addNewNodes(all_nodes, source);
	let nodeUpdate = this.updateNodes(all_nodes);
	let nodeExit = this.removeStaleNodes(all_nodes, source);
	
	//Get reference to all links
	let link = this.svg.selectAll("path.link")
	    .data(links, d => d.target.id);

	//Handle links
	this.addNewLinks(link, source);
	this.updateLinks(link);
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
	    let radius = this.maxRadius * Math.pow(Math.E, -1 * (numNodes / this.scalingFactor));

	    this.radius = radius
	    return radius;
	}
	catch(error) {
	    console.error(error);
	}
    }

    addNewNodes(all_nodes, source) {
	//Enter any new nodes at parent's prev. position
	let nodeEnter = all_nodes.enter().append("g")
	    .attr("class", "node")
	    .attr("transform", d => "translate(" + source.y0 + "," + source.x0 + ")")
	    .on("click", d => this.click(d, this))
	    .on("mouseover", d => this.handleMouseOver(d, this))					
            .on("mouseout", d => this.handleMouseOut(d, this));
	
	this.colorNewNodes(nodeEnter);
	this.formatText(nodeEnter);

	return nodeEnter;
    }

    handleMouseOver(d, self) {
	if (!self.popup) {
	    self.tooltip.transition()
		.duration(200)
		.style("opacity", .9);

	    //TODO: Remove this hardcoding
	    self.tooltip
		.style("left", d3.event.layerX + 30 + "px")		
		.style("top", d3.event.layerY - 20 + "px")
		.html("<b>Encounters:</b>\n None");

	    self.popup = true;
	}
    }	

    handleMouseOut(d, self) {
	self.popup = false;
	self.tooltip.transition()		
            .duration(200)		
            .style("opacity", 0);
    }

    colorNewNodes(nodeEnter) {
	//Color collapsed nodes
	nodeEnter.append("circle")
	    .attr("r", 1e-6)
	    .style({fill: this.colorCollapsed,
		    stroke: this.colorGender});

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
	    switch (d.gender.toUpperCase()) {
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
	    .text(d => d.name)
	    .style("fill-opacity", 1e-6);
    }

    updateNodes(updated_nodes) {	
	// Transition nodes to their new position.
	let nodeUpdate = updated_nodes.transition()
	    .duration(this.duration)
	    .attr("transform", d => "translate(" + d.y + "," + d.x + ")");

	nodeUpdate.select("circle")
	    .attr("r", this.radius)
	    .style("fill", this.colorCollapsed); //TODO: Remove?

	nodeUpdate.select("text")
	    .style("fill-opacity", 1);

	return nodeUpdate;
    }
    
    removeStaleNodes(updated_nodes, source) {
	//Transition exiting nodes to the parent's new position.
	let nodeExit = updated_nodes.exit().transition()
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
	link.enter().insert("path", "g")
	    .attr("class", "link")
	    .attr("d", d => {
		let o = {x: source.x0, y: source.y0};
		return this.diagonal({source: o, target: o});
	    });
    }

    updateLinks(link) {
	//Transition links to their new position.
	link.transition()
	    .duration(this.duration)
	    .attr("d", this.diagonal);
    }

    removeStaleLinks(link, source) {
	//Transition exiting nodes to the parent's new position.
	link.exit().transition()
	    .duration(this.duration)
	    .attr("d", d => {
		let o = {x: source.x, y: source.y};
		return this.diagonal({source: o, target: o});
	    })
	    .remove();
    }

    // Toggle children on click.
    click(d, self) {
	if (d.children) {
	    d._children = d.children;
	    d.children = null;
	}
	else {
	    d.children = d._children;
	    d._children = null;
	}
	
	self.updateTree(d);
    }

    showIncompleteInformationMessage() {
	$("#familyDiagram").html("<h4>There are currently no known familial relationships for this Marked Individual</h4>")
    };
}
