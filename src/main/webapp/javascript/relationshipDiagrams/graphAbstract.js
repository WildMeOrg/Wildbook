//TODO: Enlarge node on hover

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

    showTable(contentRef, tableRef) {
	//Display tableRef, hide contentRef
	$(contentRef).hide();
	$(tableRef).show();
	$(contentRef).removeClass("active");
	$(tableRef).addClass("active");

	//Report incomplete info
	this.showIncompleteInformationMessage();
    }

    showIncompleteInformationMessage() {
	$("#familyDiagram").html("<h4>There are currently no known relationships" +
				 " for this Marked Individual</h4>")
    }

    appendSvg(containerId) {
	this.svg = d3.select(containerId).append("svg")
	    .attr("width", this.width)
	    .attr("height", this.height)
	    .call(this.zoom.on("zoom", () => {
		this.svg.attr("transform", d3.event.transform)
	    }))
	    .on("dblclick.zoom", null) //Disable double click zoom
	    .append("g")
	    .attr("class", "container");
    }

    //TODO - FIX
    //Static Render Methods
    addLegend(containerId) {
	d3.select(containerId + " svg").append("g")
	    .attr("class", "legend")
	    .attr("transform", "translate(90%, 10%)")
	    .attr("height", "100px")
	    .attr("width", "100px")
	    .attr("fill", "red");
    }


    wheelDelta() {
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

    addTooltip(selector) {
	//Define the tooltip div
	this.tooltip = d3.select(selector).append("div")
	    .attr("class", "tooltip")				
	    .style("opacity", 0);
    }

    handleMouseOver(d) {
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

    drawNodeOutlines(nodes=this.nodes) {
	//Color collapsed nodes
	nodes.append("circle")
	    .attr("r", this.startingRadius)
	    .style("fill", this.defNodeColor)
	    .style("stroke", d => this.colorGender(d))
	    .style("stroke-width", d => this.strokeWidth * this.getSizeScalar(d));
    }

    getSizeScalar(d) {
	if (d.data.isFocused) return this.focusedScale;
	else return 1;
    }

    colorGender(d) {
		//this will asign a node to a certain color depending on if its gender is found 
		// Also assiginging a default value of color assuming the found genders are not done.
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

    //TODO: Stub function
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
}

