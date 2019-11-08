//TODO: Enlarge node on hover

//Abstract class defining funcitonality for all d3 graph types
class GraphAbstract {
    constructor(individualID, focusedScale=1) {
	this.id = individualID;

	//SVG Attributes
	this.svg;

	this.width = 960;
	this.height = 500;
	this.margin = {top: 20, right: 120, bottom: 20, left: 120};

	//Top-level G Attrbiutes
	this.gWidth = this.width - this.margin.right - this.margin.left,
	this.gHeight = this.height - this.margin.top - this.margin.bottom;

	//Node Attributes
	this.numNodes;
	this.radius;
	this.maxRadius = 50;
	this.scalingFactor = 25; //TODO: Tune this value
	this.nodeMargin = 15;
	this.nodeSeparation;
	this.transitionDuration = 750;

	this.strokeWidth = 3.5;
	
	this.fontSize = 12;
	this.focusedScale = focusedScale;

	this.alphaSymbSize = 200; //TODO: Figure out the units on this...

	//Node Style Attributes
	this.defGenderColor = "#7f7f7f"; 
	this.maleColor = "steelblue";
	this.femaleColor = "palevioletred";

	this.alphaColor = "#bf0000";

	this.defNodeColor = "#ffffff";
	this.fixedNodeColor = "#cecece";	
	
	this.defLinkColor = "#a6a6a6";
	this.famLinkColor = "#a6a6a6"; //"#b59eda";
	this.maternalLinkColor = "#f3acd0";
	this.paternalLinkColor = "#8facc6";
	
	//Zoom Attributes
	this.zoomFactor = 1000;
	this.zoom = d3.zoom()
	    .scaleExtent([0.5, 5])
	    .translateExtent([
		[-this.gWidth * 2, -this.gHeight * 2],
		[this.gWidth * 2, this.gHeight * 2]
	    ])
	    .wheelDelta(() => this.wheelDelta());

	//Tooltip Attributes
	this.popup = false;
	this.fadeDuration = 200;

	//Json Parser Attributes
	this.parser = new JSONParser();
    }

    
    // Render Methods //
    
    //Display data table relevant to graph
    showTable(diagramRef, tableRef) {
	//Display tableRef and hide diagramRef
	$(diagramRef).hide();
	$(tableRef).show();
	$(diagramRef).removeClass("active");
	$(tableRef).addClass("active");

	//Report incomplete information
	this.incompleteInfoMessage(diagramRef);
    }

    //Perform all auxiliary functions necessary prior to graphing
    setupGraph(containerId) {
	//Add default elements
	this.addSvg(containerId);
	this.addLegend(containerId);
	this.addTooltip(containerId);

	//Assess graphical sizings
	this.setNodeRadius();
    }
    
    //Append top-level SVG containing all graphical elements
    addSvg(containerId) {
	this.svg = d3.select(containerId).append("svg")
	    .attr("class", "container")
	    .attr("width", this.width)
	    .attr("height", this.height)
	    .call(this.zoom.on("zoom", () => {
		this.svg.attr("transform", d3.event.transform)
	    }))
	    .on("dblclick.zoom", null) //Disable double click zoom
	    .append("g");
    }

    //TODO - FIX
    //Append graph legend to top-level SVG
    addLegend(containerId) {
	d3.select(containerId + " svg").append("g")
	    .attr("class", "legend")
	    .attr("transform", "translate(90%, 10%)")
	    .attr("height", "100px")
	    .attr("width", "100px")
	    .attr("fill", "red");
    }

   //Append a tooltop to the top-level SVG, used to visualize node info on hover
    addTooltip(selector) {
	//Define the tooltip div
	this.tooltip = d3.select(selector).append("div")
	    .attr("class", "tooltip")				
	    .style("opacity", 0);
    }

    //Draw each node with prescribed radius, fill, and outline
    drawNodeOutlines(nodes=this.nodes) {
	nodes.append("circle")
	    .attr("r", this.startingRadius)
	    .style("fill", this.defNodeColor)
	    .style("stroke", d => this.colorGender(d))
	    .style("stroke-width", d => this.strokeWidth * this.getSizeScalar(d));
    }

    //Return a color based upon the given node's geneder
    colorGender(d) {
	try {
	    let gender = d.data.gender || "default";
	    switch (gender.toUpperCase()) {
	        case "FEMALE": return this.femaleColor; //Pink
	        case "MALE": return this.maleColor; //Blue
	        default: return this.defGenderColor; //White
	    }
	}
	catch(error) {
	    console.error(error);
	}
    }

    //Draw alpha symbols for all given nodes which qualify
    drawNodeSymbols(nodes=this.nodes) {
	nodes.append("path")
	    .attr("class", "symb")
	    .attr("d", d => {
		return d3.symbol().type(d3.symbolCircle)
		    .size(() => {
			if (d.data.role && d.data.role.toUpperCase() == "ALPHA")
			    return this.alphaSymbSize * this.getSizeScalar(d);
			else return 0;
		    })();
	    })
	    .attr("transform", d => {
		let radialPos = Math.cos(Math.PI / 4);
		let pos = this.radius * this.getSizeScalar(d) * radialPos;
		return "translate(" + pos + "," + -pos + ")";
	    })
	    .style("fill", this.alphaColor)
	    .style("fill-opacity", 0);
    }

    //Add text to the given nodes
    addNodeText(nodes=this.nodes) {
	//Style node text
//	let boundedLength = 2 * this.radius * Math.cos(Math.PI / 4); //Necessary dimensions of bounding rectangle
	nodes.append("text")
//	    .attr(d => d.x - (boundedLength / 2))
//	    .attr(d => d.y - (boundedLength / 2))
//	    .attr("width", boundedLength)
	//	    .attr("height", boundedLength)
	    .attr("class", "text")
	    .attr("dy", ".5em") //Vertically centered
	    .text(d => d.data.name)
	    .style("font-size", d => (this.fontSize * this.getSizeScalar(d)) + "px")
	    .style("font-weight", d => d.data.isFocused ? "bold" : "normal");
    }

    // Helper Methods //

    //Modify zoom wheel delta to smooth zooming
    wheelDelta() {
	return -d3.event.deltaY * (d3.event.deltaMode ? 120 : 1 ) / this.zoomFactor;
    }
    
    //Display message on missing data
    incompleteInfoMessage(diagramId) {
	$(diagramId).html("<h4 id='incompleteInfoMsg'>There are currently no known " +
			  "relationships for this Marked Individual</h4>")
    }

    //Sets the radius attribute for a given node
    setNodeRadius() {
	this.calcNodeSize(this.nodes);
	this.nodeData.forEach(d => {
	    d.data.r = this.radius * this.getSizeScalar(d);
	});
    }

    //Calculate node size s.t. all nodes can fit in the contextual SVG
    calcNodeSize(nodeData=this.nodeData) {
	try {
	    let numNodes = nodeData.length || 10; //Default node length is 10 
	    this.radius = this.maxRadius * Math.pow(Math.E, -(numNodes / this.scalingFactor));

	    //TODO - Calculate margins?
	    //let margins = this.radius;
	}
	catch(error) {
	    console.error(error);
	}
    }

    //Returns the color for a given link
    getLinkColor(d) {	
	switch(d.type) {
	case "familial":
	    return this.famLinkColor;
	case "paternal":
	    return this.paternalLinkColor;
	case "maternal":
	    return this.maternalLinkColor;
	default:
	    return this.defLinkColor;
	}
    }

    //Return a size multiple if the given node is focused, defaults to 1 
    getSizeScalar(d) {
	if (d.data.isFocused) return this.focusedScale;
	else return 1;
    }

        //Fade the tooltip into view when hovering over a given node
    handleMouseOver(d) {
	if (!this.popup) {
	    //Display opaque tooltip
	    this.tooltip.transition()
		.duration(this.fadeDuration)
		.style("opacity", .9);

	    //TODO: Remove this hardcoding
	    //Place tooltip offset to the upper right of the hovered node
	    this.tooltip
		.style("left", d3.event.layerX + 30 + "px")		
		.style("top", d3.event.layerY - 20 + "px")
		.html("<b>Encounters:</b>\n None");

	    //Prevent future mouseOver events
	    this.popup = true;
	}
    }	

    //Fade the tooltip from view when no longer hovering over a node
    handleMouseOut(d) {
	//Enable future mouseOver events
	this.popup = false;

	//Fade tooltip from view
	this.tooltip.transition()		
            .duration(this.fadeDuration)		
            .style("opacity", 0);
    }
}

