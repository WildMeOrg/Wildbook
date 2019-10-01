class GraphAbstract {
    constructor(individualID) {
	this.id = individualID;
	
	this.width = 960;
	this.height = 500;
	this.margin = {top: 20, right: 120, bottom: 20, left: 120};

	//G dimensions
	this.gWidth = this.width - this.margin.right - this.margin.left,
	this.gHeight = this.height - this.margin.top - this.margin.bottom;

	//Scaled node size
	this.numNodes;
	this.radius;
	this.maxRadius = 40;
	this.scalingFactor = 25; //TODO: Tune this value

	//Pan Attributes
	this.prevPos = [0, 0];
	
	//Zoom attirbutes
	this.zoomFactor = 1000;
	this.zoom = d3.zoom()
	    .scaleExtent([0.5, 5])
	    .translateExtent([
		[-this.gWidth * 2, -this.gHeight * 2],
		[this.gWidth * 2, this.gHeight * 2]
	    ])
	    .wheelDelta(() => this.wheelDelta());

	this.svg;
	this.tooltip;
	this.popup = false;
	this.duration = 750;

	this.maleColor = "steelblue";
	this.femaleColor = "palevioletred";
	this.defGenderColor = "#757575";

	this.alphaColor = "#bf0000";

	this.defLinkColor = "#a6a6a6";
	this.famLinkColor = "#000000";

	//Node style attributes
	this.defNodeColor = "#ffffff";
	this.collapsedNodeColor = "#d3d3d3";	
    }

    wheelDelta () {
	return -d3.event.deltaY * (d3.event.deltaMode ? 120 : 1 ) / this.zoomFactor;
    }

    calcNodeSize(nodes) {
	try {
	    let defaultNodeLen = 10;
	    let numNodes = nodes.length || defaultNodeLen; //Defaults to 10 
	    this.radius = this.maxRadius * Math.pow(Math.E, -(numNodes / this.scalingFactor));
	}
	catch(error) {
	    console.error(error);
	}
    }

    addTooltip(selector) {
	//Define the tooltip div
	this.tooltip = d3.select(selector).append("div")
	    .attr("transform", "translate(" + this.margin.left + "," + this.margin.top + ")")
	    .attr("class", "tooltip")				
	    .style("opacity", 0);
	return this.tooltip;
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

    drawNodeOutlines(nodes, isHidden) {
	//Color collapsed nodes
	return nodes.append("circle")
	    .attr("r", () => isHidden ? 1e-6 : this.radius)
	    .style("fill", d => this.colorCollapsed(d))
	    .style("stroke", d => this.colorGender(d))
	    .style("stroke-width", 3);
    }

    //TODO: Move this outside the generic class - it doesn't make much sense
    colorCollapsed(d) {
	return (d && d._children) ? this.collapsedNodeColor : this.defNodeColor;
    }

    colorGender(d) {
	try {
	    let gender = d.data.gender || "def";
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
		    .size((d.data.role && d.data.role.toUpperCase() == "ALPHA") ? 125 : 0)()
	    })
	    .attr("transform", d => {
		let x = this.radius * Math.cos(Math.PI / 4);
		let y = this.radius * Math.sin(Math.PI / 4);
		return "translate(" + x + "," + -y + ")";
	    })
	    .style("fill", this.alphaColor)
	    .style("fill-opacity", () => isHidden ? 1e-6 : 1);
    }

    updateSymbols(nodeUpdate, isHidden) {
	nodeUpdate.select("path.symb")
	    .attr("transform", d => {
		let x = this.radius * Math.cos(Math.PI / 4);
		let y = this.radius * Math.sin(Math.PI / 4);
		return "translate(" + x + "," + -y + ")";
	    })
	    .style("fill-opacity", () => isHidden ? 1e-6 : 1);
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
	    .attr("dy", ".5em") //Vertically centered
	    .text(d => d.data.name)
	    .style("fill-opacity", () => isHidden ? 1e-6 : 1)
	    .style("font-weight", d => d.data.isFocus ? "bold" : "normal");
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

}

