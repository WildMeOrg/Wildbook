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

	this.strokeWidth = 3;
	
	this.fontSize = 12;
	this.focusedScale = focusedScale;

	//Node Style Attributes
	this.maleColor = "steelblue";
	this.femaleColor = "palevioletred";
	this.defGenderColor = "#757575";

	this.alphaColor = "#bf0000";

	this.defNodeColor = "#ffffff";
	this.collapsedNodeColor = "#d3d3d3";	
	
	this.defLinkColor = "#a6a6a6";
	this.famLinkColor = "#000000";
	
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
	//this.tooltip;
	this.popup = false;
	this.fadeDuration = 200;
    }

    appendSvg(containerId) {
	this.svg = d3.select(containerId).append("svg")
	    .attr("width", this.width)
	    .attr("height", this.height)
	    .call(this.zoom.on("zoom", () => {
		this.svg.attr("transform", d3.event.transform)
	    }))
	    .append("g");
    }

    wheelDelta () {
	return -d3.event.deltaY * (d3.event.deltaMode ? 120 : 1 ) / this.zoomFactor;
    }

    calcNodeSize(nodes) {
	try {
	    let numNodes = nodes.length || 10; //Default node length is 10 
	    this.radius = this.maxRadius * Math.pow(Math.E, -(numNodes / this.scalingFactor));

	    //TODO - Calculate margins?
	    //let margins = this.radius;
	}
	catch(error) {
	    console.error(error);
	}
    }

    calcNodeDepth(nodes) {
	let nodeDepth = nodes.map(d => d.depth);
	let maxDepth = Math.max(...nodeDepth); //Spread array to extract max value
	this.nodeSeparation = this.gWidth / maxDepth; //Set horizontal separation
    }
    
    addTooltip(selector) {
	//Define the tooltip div
	this.tooltip = d3.select(selector).append("div")
	    .attr("class", "tooltip")				
	    .style("opacity", 0);
    }

    handleMouseOver(d) {
	console.log("this", this);
	if (!this.popup) {
	    this.tooltip.transition()
		.duration(this.fadeDuration)
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
            .duration(this.fadeDuration)		
            .style("opacity", 0);
    }

    drawNodeOutlines(nodes, isHidden) {
	//Color collapsed nodes
	return nodes.append("circle")
	    .attr("r", d => this.radius * this.getSizeScalar(d, isHidden))
	    .style("fill", d => this.colorCollapsed(d))
	    .style("stroke", d => this.colorGender(d))
	    .style("stroke-width", d => this.strokeWidth * this.getSizeScalar(d));
    }

    getSizeScalar(d, isHidden=false) {
	if (isHidden) return 0;
	else if (d.data.isFocused) return this.focusedScale;
	else return 1;
    }

    //TODO: Move this outside the generic class - it doesn't make much sense
    colorCollapsed(d) {
	return (d && d._children) ? this.collapsedNodeColor : this.defNodeColor;
    }

    colorGender(d) {
	try {
	    let gender = d.data.gender || "default";
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

    drawNodeSymbols(nodes, isHidden) {
	nodes.append("path")
	    .attr("class", "symb")
	    .attr("d", d => {
		return d3.symbol().type(d3.symbolCircle)
		    .size(() => {
			if (d.data.role && d.data.role.toUpperCase() == "ALPHA")
			    return 125 * this.getSizeScalar(d);
			else return 0;
		    })();
	    })
	    .attr("transform", d => {
		let radialPos = Math.cos(Math.PI / 4);
		let pos = this.radius * this.getSizeScalar(d) * radialPos;
		return "translate(" + pos + "," + -pos + ")";
	    })
	    .style("fill", this.alphaColor)
	    .style("fill-opacity", () => isHidden ? 0 : 1);
    }

    updateSymbols(nodeUpdate, isHidden) {
	nodeUpdate.select("path.symb")
	    .attr("transform", d => {
		let radialPos = Math.cos(Math.PI / 4);
		let pos = this.radius * this.getSizeScalar(d) * radialPos;
		return "translate(" + pos + "," + -pos + ")";
	    })
	    .style("fill-opacity", () => isHidden ? 0 : 1);
    }

    //TODO: Stub function
    addNodeText(nodeEnter, isHidden) {
	//Style node text
//	let boundedLength = 2 * this.radius * Math.cos(Math.PI / 4); //Necessary dimensions of bounding rectangle
	nodeEnter.append("text")
//	    .attr(d => d.x - (boundedLength / 2))
//	    .attr(d => d.y - (boundedLength / 2))
//	    .attr("width", boundedLength)
	//	    .attr("height", boundedLength)
	    .attr("class", "text")
	    .attr("dy", ".5em") //Vertically centered
	    .text(d => d.data.name)
	    .style("font-size", d => (this.fontSize * this.getSizeScalar(d, isHidden)) + "px")
//	    .style("fill-opacity", () => isHidden ? 0 : 1)
	    .style("font-weight", d => d.data.isFocused ? "bold" : "normal");
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

	if (isNaN(d.y)) d.y0 = d.y = 0; //TODO - Remove?
	this.updateTree(d);
    }

}

