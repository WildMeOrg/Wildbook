//Abstract class defining funcitonality for all d3 graph types
class GraphAbstract { //See static attributes below class    
    constructor(individualID, containerId) {
	this.id = individualID;
	this.containerId = containerId;
	
	//Unique global id
	this.graphId = GraphAbstract.numGraphs++;

	//SVG Attributes
	this.svg;
	this.width = 1150;
	this.height = 500;
	this.margin = {top: 20, right: 120, bottom: 20, left: 120};

	//Top-level G Attrbiutes
	this.gWidth = this.width - this.margin.right - this.margin.left,
	this.gHeight = this.height - this.margin.top - this.margin.bottom;

	//Node Attributes
	this.maxRadius = 50;
	this.scalingFactor = 25;
	this.nodeMargin = 15;
	this.transitionDuration = 750;
	this.strokeWidth = 3.5;
	this.fontSize = 9;
	this.focusedScale = 1;
	this.alphaSymbSize = 200;

	//Node Style Attributes
	this.defGenderColor = "#7f7f7f"; 
	this.maleColor = "steelblue";
	this.femaleColor = "palevioletred";
	this.alphaColor = "#bf0000";
	this.defNodeColor = "#ffffff";
	this.fixedNodeColor = "#cccccc";	
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
	this.maxDisplay = 4;
	
	//Legend Attributes
	this.legendMargin = {"top": 25, "right": 25, "bottom": 25, "left": 25}
        this.legendDimensions = {"width": 265, "height": 200};
	this.legendOffset = {"horiz": this.width - (this.legendDimensions.width + this.legendMargin.right),
			     "vert": this.legendMargin.top};
	this.legendNodeColors = [this.maleColor, this.femaleColor, this.defGenderColor];
	this.legendLinkColors = [this.paternalLinkColor, this.maternalLinkColor, this.famLinkColor];
	this.legendLabels = ["Male Sex", "Female Sex", "Unknown Sex", "Alpha Role",
			     "Organism", "Paternal Relationship", "Maternal Relationship",
			     "Familial Relationship", "Member Relationship"];
	this.legendIcons = {"size": 15, "margin": 8, "mSize": 23};
	this.legendStrokeWidth = 2;

	//Filter Attributes
	this.filters = {
	    "selectFamily": {"func": (d) => d.group === this.focusedNode.group, "groups": {},
			     "groupNum": () => this.focusedNode.group},
	    "filterFamily": {"func": (d) => d.group !== this.focusedNode.group, "groups": {},
			    "groupNum": () => this.focusedNode.group},
	    "male": {"func": (d) => d.data.gender !== "male", "groups": {}},
	    "female": {"func": (d) => d.data.gender !== "female", "groups": {}},
	    "unknownGender": {"func": (d) => d.data.gender !== "unknown", "groups": {}},
	    "alpha": {"func": (d) => d.data.role !== "alpha", "groups": {}},
	    "unknownRole": {"func": (d) => d.data.role, "groups": {}}
	};

	this.validFamilyFilters = ["selectFamily", "filterFamily"]
	this.validCheckFilters = ["male", "female", "unknownGender", "alpha", "unknownRole"];
	this.validFilters = this.validFamilyFilters.concat(this.validCheckFilters);
    }

    
    // Render Methods //
    
   /**
     * Display the data table context of this graph
     * @param {diagramRef} [string] - HTML reference to the graph diagram
     * @param {tableRef} [string] - HTML reference to the graph table
     */	
    showTable(diagramRef, tableRef) {
	//Display tableRef and hide diagramRef
	$(diagramRef).hide();
	$(tableRef).show();
	$(diagramRef).removeClass("active");
	$(tableRef).addClass("active");

	//Report incomplete information
	this.incompleteInfoMessage(diagramRef);
    }
	
   /**
     * Perform all auxiliary functions necessary prior to graphing
     * @param {linkData} [Link array] - Relationship link list
     * @param {nodeData} [Node array] - MarkedIndividual node list
     */	
    setupGraph(linkData, nodeData) {
	//Establish link/node context
	this.linkData = linkData;
	this.nodeData = nodeData;

	//Setup intial focused node
	this.focusedNode = this.nodeData.filter(d => d.data.isFocused)[0];
	
	//Add default elements
	this.addSvg();
	this.addLegend();
	this.addTooltip();

	//Assess graphical sizings
	this.setNodeRadius();

	//Initialize filter buttons
	this.updateFilterButtons();
	this.addHideButton();

	//Update sliders
	this.updateRangeSliderAttr();
	this.updateRangeSliders();
    }
    
    /**
     * Append top-level SVG containing all graph elements
     * @param {containerId} [String] - The HTML reference to append this SVG to
     */	
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

    /**
     * Append graph legend to top-level SVG
     * @param {containerId} [String] - The HTML reference to append this SVG to
     * @param {rowsPerCol} [int] - The number of rows to display per legend column
     * @param {rowSpacing} [int] - The spacing in px between rows
     */	
    addLegend(containerId=this.containerId, rowsPerCol=5, rowSpacing=120) {
	//Append the legend group
	let legendRef = d3.select(containerId + " svg").append("g")
	    .attr("class", "legend")
	    .attr("id", "legendRef")
	    .attr("height", this.legendHeight)
	    .attr("width", this.legendWidth)
	    .attr("transform", "translate(" + this.legendOffset.horiz  + "," + this.legendOffset.vert + ")")

	//Initialize position references
	let xIdx = 0, yIdx = 0;

	//Add legend gender color-key
	legendRef.selectAll("rect")
	    .data(this.legendNodeColors).enter()
            .append("rect")
            .attr("x", () => Math.floor(xIdx++ / rowsPerCol) * rowSpacing)
            .attr("y", () => (yIdx++ % rowsPerCol) * this.legendIcons.mSize)
            .attr("width", this.legendIcons.size)
            .attr("height", this.legendIcons.size)
            .attr("fill", d => d);

	//Add alpha symbol example
	legendRef.append("circle")
            .attr("cx", (Math.floor(xIdx++ / rowsPerCol) * rowSpacing) + this.legendIcons.size / 2)
            .attr("cy", ((yIdx++ % rowsPerCol) * this.legendIcons.mSize) + this.legendIcons.size / 2)
            .attr("r", this.legendIcons.size / 2)
            .attr("fill", this.alphaColor)
	
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
    
    /**
     * Append a tooltip to the top-level SVG, used to visualize node info on hover
     * @param {containerId} [String] - The HTML reference to append this SVG to
     */	
    addTooltip(containerId=this.containerId) {
	//Define the tooltip div
	this.tooltip = d3.select(containerId).append("div")
	    .attr("class", "tooltip")				
	    .style("opacity", 0);
    }

    // Helper Methods //

    /**
     * Modify zoom wheel delta to smooth zooming
     */	
    wheelDelta() {
	return -d3.event.deltaY * (d3.event.deltaMode ? 120 : 1 ) / this.zoomFactor;
    }
    
    /**
     * Display message on missing data
     * @param {diagramId} [Node] - An HTML reference dictating where the info message should be appended
     */	
    incompleteInfoMessage(diagramId) {
	$(diagramId).html("<h4 id='incompleteInfoMsg'>There are currently no known " +
			  "relationships for this Marked Individual</h4>")
    }

    /**
     * Sets the radius attribute for a given node
     * @param {nodeData} [Node list] - A list of Node elements
     */	
    setNodeRadius(nodeData=this.nodeData) {
	this.calcNodeSize(nodeData);
	nodeData.forEach(d => {
	    d.data.r = this.radius * this.getSizeScalar(d);
	});
    }

    /**
     * Calculate node size s.t. all nodes can fit in the contextual SVG
     * @param {nodeData} [Node list] - A list of Node elements
     */	
    calcNodeSize(nodeData=this.nodeData) {
	try {
	    let numNodes = nodeData.length || 10; //Default node length is 10 
	    this.radius = this.maxRadius * Math.pow(Math.E, -(numNodes / this.scalingFactor));
	}
	catch(error) {
	    console.error(error);
	}
    }

    /**
     * Returns the color for a given link
     * @param {link} [Link] - A Link element
     */	
    getLinkColor(link) {
	if (link) {
	    switch(link.type) {
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

    /**
     * Return a size multiple if the given node is focused, defaults to 1 
     * @param {node} [Node] - A Node element
     */	
    getSizeScalar(node) {
	if (node && node.data && node.data.isFocused) return this.focusedScale;
	else return 1;
    }

    /**
     * Display the selected tooltip type
     * @param {d} [Node|Link] - A Node or Link element
     * @param {type} [String] - Determines whether a "node" or "link" tooltip should be displayed
     */	
    handleMouseOver(d, type) {
	if (!this.popup) {
	    if (type === "node") this.displayNodeTooltip(d);
	    else if (type === "link") this.displayLinkTooltip(d);
	    else return;

	    //Display opaque tooltip
	    this.tooltip.transition()
		.duration(this.fadeDuration)
		.style("opacity", this.tooltipOpacity);
	    
	    //Prevent future mouseOver events
	    this.popup = true;
	}
    }

    /**
     * Displays the node tooltip
     * @param {d} [Node] - A Node element
     */	
    displayNodeTooltip(d) {
	//Place tooltip offset to the upper right of the hovered node
	let text = this.generateNodeTooltipHtml(d);
	if (text) {
	    let halfRadius = (this.radius * this.getSizeScalar(d)) / 2;
	    this.tooltip
		.style("left", d3.event.layerX +  halfRadius + "px")	
		.style("top", d3.event.layerY - halfRadius + "px")
		.style("background-color", "#808080")
		.html(text);
	}
    }

    /**
     * Generate the tooltip description for a given node
     * @param {d} [Node] - A Node element
     * @return {tooltipHTML} [String] - An HTML string to use as a tooltip display
     */	
    generateNodeTooltipHtml(d) {
	let tooltipHtml = "<b>Name: </b>" + d.data.name + "<br/>";
	if (d.data.gender)
	    tooltipHtml += "<b>Sex: </b>" + d.data.gender + "<br/>";
	if (d.data.role)
	    tooltipHtml += "<b>Role: </b>" + d.data.role + "<br/>";
	if (d.data.lastKnownLifeStage)
	    tooltipHtml += "<b>Last Known Life Stage: </b>" + d.data.lastKnownLifeStage + "<br/>";
	if (d.data.isDead !== null) {
	    let livingStatus = (d.data.isDead ? "dead" : "alive");
	    tooltipHtml += "<b>Living Status: </b>" +  livingStatus + "<br/>";
	}
	if (d.data.firstSighting)
	    tooltipHtml += "<b>First Seen: </b>" + d.data.firstSighting + "<br/>";
	if (d.data.latestSighting)
	    tooltipHtml += "<b>Last Seen: </b>" + d.data.latestSighting + "<br/>";
	if (d.data.timeOfBirth)
	    tooltipHtml += "<b>Birth Date: </b>" + d.data.timeOfBirth + "<br/>";
	if (d.data.timeOfDeath)
	    tooltipHtml += "<b>Death Date: </b>" + d.data.timeOfDeath;
	return tooltipHtml;
    }
    
    /**
     * Display the link tooltip
     * @param {d} [Link] - A Link element
     */	
    displayLinkTooltip(d) {
	//Place tooltip on the hovered link
	let text = this.generateLinkTooltipHtml(d);
	
	if (text) {
	    this.tooltip
		.style("left", d3.event.layerX +  "px")	
		.style("top", d3.event.layerY + "px")
		.style("background-color", "#7997a1")
		.html(text);
	}
    }
    
    /**
     * Generate the tooltip description for a given link
     * @param {link} [Link] - A Link element
     */	
    generateLinkTooltipHtml(link) {
	let target = (link.target.data.individualID === this.id) ? "source" : "target";

	let tooltipHtml = "";
	let totalLinks = link.count, numLinks = 0;
	for (let enc of link.validEncounters) {
	    let time = enc.time;
	    let loc = enc.location;

	    //Limit the total number of links displayed to {this.maxDisplay}
	    if (numLinks >= this.maxDisplay) {
		if (numLinks < totalLinks)
		    tooltipHtml += "... " + (totalLinks - numLinks) + " more ...";
		break;
	    }
	    
	    if (time)
		tooltipHtml += "<b>Date: </b>" + time.day + "/" + time.month + "/" + time.year + " ";
	    if (loc && typeof loc.lat == "number")
		tooltipHtml += "<b>Longitude: </b>" + loc.lon + " ";
	    if (loc && typeof loc.lat == "number")
		tooltipHtml += "<b>Latitude: </b>" + loc.lat + "<br/>";

	    numLinks++;
	}
	
	return tooltipHtml;
    }

    /**
     * Fade the tooltip from view when no longer hovering over a node
     */	
    handleMouseOut() {
	//Enable future mouseOver events
	this.popup = false;

	//Fade tooltip from view
	this.tooltip.transition()		
            .duration(this.fadeDuration)		
            .style("opacity", 0);
    }

    /**
     * Draws a simple arrow (used for the legend arrow icons)
     * @param {legendRef} [String] - The HTML element to append the legend arrow definitions
     * @param {x} [int] - The x position of the legend arrow
     * @param {y} [int] - The y position of the legend arrow
     * @param {color} [String] - The hex color to use for the legend arrow 
     */	
    drawLegendArrow(legendRef, x, y, color){
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
        
    /**
     * Zoom in when button is pressed
     */	
    zoomIn() {
	
	this.zoom.scaleBy(this.svg.transition().duration(750), 1.5);
    }

    /**
     * Zoom out when button is pressed
     */	
    zoomOut() {
	/*let scale = d3.event.transform.k;
	this.svg.transition()
	    .duration(this.transitionDuration)
	    .attr("transform", "scale(" + (k / 1.5)  + ")");*/
	
	this.zoom.scaleBy(this.svg.transition().duration(750), 1 / 1.5);
    }

    /**
     * Append a hide button to the graph
     */	
    addHideButton(){
	var shown = true;
        let hidebutton = document.createElement("button");
        hidebutton.innerText = 'Hide Legend';
	$(this.containerId).find("#graphOptions").append(hidebutton);

	let legend = $(this.containerId).find(".legend")[0];
        hidebutton.addEventListener("click", () => {
            if (shown) {
		legend.style.opacity = "0";
		hidebutton.innerText = "Show Legend";
            }
            else {
		legend.style.opacity = "1";
		hidebutton.innerText = "Hide Legend";
            }
	    shown = !shown;
        });
    }
}

//Static attributes
GraphAbstract.numGraphs = 0;

