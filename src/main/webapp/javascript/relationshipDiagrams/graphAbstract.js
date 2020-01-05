//Abstract class defining funcitonality for all d3 graph types
class GraphAbstract {
    constructor(individualID, containerId, focusedScale=1) {
	this.id = individualID;
	this.containerId = containerId;

	//SVG Attributes
	this.svg;

	this.width = 1150;
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

	this.defNodeColor = "#fff";
	this.fixedNodeColor = "#ccc";	
	
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
	this.tooltipOpacity = 0.9;

	//Legend Attributes
	this.legendMargin = {"top": 25, "right": 25, "bottom": 25, "left": 25}
        this.legendDimensions = {"width": 265, "height": 200};
	this.legendOffset = {"horiz": this.width - (this.legendDimensions.width + this.legendMargin.right),
			     "vert": this.legendMargin.top};
	this.legendNodeColors = [this.maleColor, this.femaleColor, this.defGenderColor];
	this.legendLinkColors = [this.paternalLinkColor, this.maternalLinkColor, this.famLinkColor];
	this.legendLabels = ["Male Sex", "Female Sex", "Unknown Sex", "Organism",
			     "Paternal Relationship", "Maternal Relationship",
			     "Familial Relationship", "Member Relationship"];
	this.legendIcons = {"size": 15, "margin": 8, "mSize": 23};
	this.legendStrokeWidth = 2;
	this.legendRowsPerCol = 4;

	//Filter Attributes
	this.validFamilyFilters = ["selectFamily", "filterFamily"]
	this.validCheckFilters = ["male", "female", "unknownGender", "alpha", "unknownRole"];
	this.validFilters = this.validFamilyFilters.concat(this.validCheckFilters);
	
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
    setupGraph(linkData, nodeData) {
	//Establish link/node context
	this.linkData = linkData;
	this.nodeData = nodeData;

	console.log("LINK", this.linkData);
	console.log("NODE", this.nodeData);
	
	//Add default elements
	this.addSvg();
	this.addLegend();
	this.addTooltip();

	//Assess graphical sizings
	this.setNodeRadius();

	//Initialize filter button functionalities
	this.updateFilterButtons();
    }
    
    //Append top-level SVG containing all graphical elements
    addSvg(containerId=this.containerId) {
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

    //Append graph legend to top-level SVG
    addLegend(containerId=this.containerId, rowsPerCol=4, rowSpacing=120) {
	//Append the legend group
	let legendRef = d3.select(containerId + " svg").append("g")
	    .attr("class", "legend")
	    .attr("height", this.legendHeight)
	    .attr("width", this.legendWidth)
	    .attr("transform", "translate(" + this.legendOffset.horiz  + "," + this.legendOffset.vert + ")")

	//Initialize position references
	let xIdx = 0, yIdx = 0;

	//TODO - Consider adding strong repulsive force to legend
	//Add legend gender color-key
	legendRef.selectAll("rect")
	    .data(this.legendNodeColors).enter()
            .append("rect")
            .attr("x", () => Math.floor(xIdx++ / rowsPerCol) * rowSpacing)
            .attr("y", () => (yIdx++ % rowsPerCol) * this.legendIcons.mSize)
            .attr("width", this.legendIcons.size)
            .attr("height", this.legendIcons.size)
            .attr("fill", d => d);

	//Add organism circle example
	legendRef.append("circle")
            .attr("cx", (Math.floor(xIdx++ / rowsPerCol) * rowSpacing) + this.legendIcons.size / 2)
            .attr("cy", ((yIdx++ % rowsPerCol) * this.legendIcons.mSize) + this.legendIcons.size / 2)
            .attr("r", this.legendIcons.size / 2)
            .attr("fill", this.defNodeColor)
            .attr("stroke", this.defGenderColor)
	    .attr('stroke-width', this.legendStrokeWidth);
	
	//Add familial relationship links
	this.legendLinkColors.forEach(color => {
	    let x = Math.floor(xIdx++ / rowsPerCol) * rowSpacing;
	    let y = (yIdx++ % rowsPerCol) * this.legendIcons.mSize;
	    	this.drawLegendArrow(legendRef, x, y, color);
	});

	//Add member-member relationship link
	legendRef.append("line")
	    .attr("x1", Math.floor(xIdx / rowsPerCol) * rowSpacing)
	    .attr("x2", (Math.floor(xIdx++ / rowsPerCol) * rowSpacing) + this.legendIcons.size)
	    .attr("y1", (yIdx % rowsPerCol) * this.legendIcons.mSize + (this.legendIcons.size / 2))
	    .attr("y2", (yIdx++ % rowsPerCol) * this.legendIcons.mSize + (this.legendIcons.size / 2))
	    .attr("stroke", this.defLinkColor)
	    .attr("stroke-width", this.legendStrokeWidth);

	//Add legend labels
	legendRef.selectAll("text")
            .data(this.legendLabels).enter()
            .append("text")
            .attr("x", (d, i) => (Math.floor(i / rowsPerCol) * rowSpacing) + this.legendIcons.mSize)
            .attr("y", (d, i) => ((i % rowsPerCol) * this.legendIcons.mSize) + this.legendIcons.size)
            .text(d => d);
    }
    
   //Append a tooltop to the top-level SVG, used to visualize node info on hover
    addTooltip(selector) {
	//Define the tooltip div
	this.tooltip = d3.select(selector).append("div")
	    .attr("class", "tooltip")				
	    .style("opacity", 0);
    }

    //Draw each node with prescribed radius, fill, and outline
    updateNodeOutlines(newNodes, activeNodes) {
	//Create new node outlines
	newNodes.append("circle")
	    .attr("r", this.startingRadius)
	    .style("fill", this.defNodeColor)
	    .style("stroke", d => this.colorGender(d))
	    .style("stroke-width", d => this.strokeWidth * this.getSizeScalar(d));

	//Scale node radius and stroke width
	activeNodes.selectAll("circle").transition()
	    .duration(this.transitionDuration)
	    .attr("r", d => this.radius * this.getSizeScalar(d))
	    .style("stroke-width", d => this.strokeWidth * this.getSizeScalar(d) + "px");
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
    updateNodeSymbols(newNodes, activeNodes) {
	//Add new node symbols
	newNodes.append("path")
	    .attr("class", "symb")
	    .attr("d", d => {
		return d3.symbol().type(d3.symbolCircle)
		    .size(() => {
			if (d.data.role && d.data.role.toUpperCase() == "ALPHA")
			    return this.alphaSymbSize * this.getSizeScalar(d);
			else return 0;
		    })();
	    })
	    .style("fill", this.alphaColor)
	    .style("fill-opacity", 0);

	//Update node symbols
	activeNodes.selectAll(".symb").transition()
	    .duration(this.transitionDuration)
	    .style("fill-opacity", 1)
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
    }

    //Add text to the given nodes
    updateNodeText(newNodes, activeNodes) {
	//Add new node text
//	let boundedLength = 2 * this.radius * Math.cos(Math.PI / 4); //Necessary dimensions of bounding rectangle
	newNodes.append("text")
//	    .attr(d => d.x - (boundedLength / 2))
//	    .attr(d => d.y - (boundedLength / 2))
//	    .attr("width", boundedLength)
	//	    .attr("height", boundedLength)
	    .attr("class", "text")
	    .attr("dy", ".5em") //Vertically centered
	    .text(d => d.data.name)
	    .style("font-size", d => (this.fontSize * this.getSizeScalar(d)) + "px")
	    .style("font-weight", d => d.data.isFocused ? "bold" : "normal");

	//Update node text
	activeNodes.selectAll("text").transition()
	    .duration(this.transitionDuration)
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
    setNodeRadius(nodeData=this.nodeData) {
	this.calcNodeSize(nodeData);
	console.log(nodeData);
	nodeData.forEach(d => {
	    d.data.r = this.radius * this.getSizeScalar(d);
	});
    }

    //Calculate node size s.t. all nodes can fit in the contextual SVG
    calcNodeSize(nodeData=this.nodeData) {
	try {
	    let numNodes = nodeData.length || 10; //Default node length is 10 
	    this.radius = this.maxRadius * Math.pow(Math.E, -(numNodes / this.scalingFactor));
	}
	catch(error) {
	    console.error(error);
	}
    }

    //Returns the color for a given link
    getLinkColor(d) {
	if (d) {
	    switch(d.type) {
	    case "familial":
		return this.famLinkColor;
	    case "paternal":
		return this.paternalLinkColor;
	    case "maternal":
		return this.maternalLinkColor;
	    }
	}
	
	return this.defLinkColor;
    }

    //Return a size multiple if the given node is focused, defaults to 1 
    getSizeScalar(d) {
	if (d && d.data && d.data.isFocused) return this.focusedScale;
	else return 1;
    }

    //Fade the tooltip into view when hovering over a given node
    handleMouseOver(d) {
	if (!this.popup) {
	    //Display opaque tooltip
	    this.tooltip.transition()
		.duration(this.fadeDuration)
		.style("opacity", this.tooltipOpacity);

	    //TODO: Remove this hardcoding
	    //Place tooltip offset to the upper right of the hovered node
	    this.tooltip
		.style("left", d3.event.layerX + 30 + "px")		
		.style("top", d3.event.layerY - 20 + "px")
	    //		.html("<b>Encounters:</b>\n None");
		.html("<b>" + d.filtered + "</b>");

	    //Prevent future mouseOver events
	    this.popup = true;
	}
    }	

    //Fade the tooltip from view when no longer hovering over a node
    handleMouseOut() {
	//Enable future mouseOver events
	this.popup = false;

	//Fade tooltip from view
	this.tooltip.transition()		
            .duration(this.fadeDuration)		
            .style("opacity", 0);
    }

    //Draws a simple arrow (used for the legend arrow icons)
    drawLegendArrow(legendRef, x, y,color){
	//Calculate median line
	let yCenter = y + (this.legendIcons.size / 2)

	//Draw a horizontal line component
	legendRef.append("line")
	    .attr("x1", x)
	    .attr("x2", x + this.legendIcons.size)
	    .attr("y1", yCenter)
	    .attr("y2", yCenter)
	    .attr("stroke", color)
	    .attr('stroke-width', this.legendStrokeWidth);

	//Draw the top half of the chevron arrowhead
	legendRef.append("line")
	    .attr("x1", x + (this.legendIcons.size * 3 / 4))
	    .attr("x2", x + this.legendIcons.size)
	    .attr("y1", yCenter - 5)
	    .attr("y2", yCenter).attr("stroke", color)
	    .attr('stroke-width', this.legendStrokeWidth);

	//Draw the bottom half of the chevron arrowhead
	legendRef.append("line")
	    .attr("x1", x + (this.legendIcons.size * 3 / 4))
	    .attr("x2", x + this.legendIcons.size)
	    .attr("y1", yCenter + 5)
	    .attr("y2", yCenter)
	    .attr("stroke", color)
	    .attr('stroke-width', this.legendStrokeWidth);
    }
    
    //Abstract funciton serving to update known filter buttons with relevant filters
    updateFilterButtons(containerId=this.containerId) {
	//Select family filter
	$(containerId).find("#selectFamily").on("click", () => {
	    let groupNum = this.focusedNode.group;
	    let filter = (d) => d.group === groupNum;
	    this.filterGraph(groupNum, filter, filter, "selectFamily",
			     this.validFamilyFilters);
	});

	//Filter family filter
	$(containerId).find("#filterFamily").on("click", () => {
	    let groupNum = this.focusedNode.group;
	    let nodeFilter = (d) => d.group !== groupNum;
	    let linkFilter = (d) => (d.source.group !== groupNum &&
				     d.target.group !== groupNum);
	    this.filterGraph(groupNum, nodeFilter, linkFilter, "filterFamily",
			     this.validFamilyFilters);
	});

	//Reset filter
	$(containerId).find("#reset").on("click", () => this.resetGraph() && false);

	//Male filter
	this.createCheckBoxFilter(containerId, "male",
				  (d) => d.data.gender !== "male");

	//Female filter
	this.createCheckBoxFilter(containerId, "female",
				  (d) => d.data.gender !== "female");

	//Unknown gender filter
	this.createCheckBoxFilter(containerId, "unknownGender",
				  (d) => d.data.gender);

	//Alpha role filter
	this.createCheckBoxFilter(containerId, "alpha", (d) => d.data.role !== "alpha");

	//Unknown role filter
	this.createCheckBoxFilter(containerId, "unknownRole", (d) => d.data.role);
    }

    createCheckBoxFilter(containerId, filterRef, filter) {
	$(containerId).find("#" + filterRef + "Box").on("click", (e) => {
	    let nodeRef = $(containerId).find("#" + filterRef + "Box");
	    if (nodeRef.is(":checked")) {
		nodeRef.closest("label").css("background", this.fixedNodeColor);
	    }
	    else {
		nodeRef.closest("label").css("background", this.defNodeColor);
	    }

	    this.filterGraph(0, filter, (d) => true, filterRef, this.validFilters);
	});
    }

    uncheckBoxFilters(containerId) {
	this.validCheckFilters.forEach(filterRef => {
	    let nodeRef = $(containerId).find("#" + filterRef + "Box");
	    nodeRef.closest("label").css("background", this.defNodeColor);
	    nodeRef.prop("checked", false);
	});
    }
}

