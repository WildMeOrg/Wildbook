//Abstract class defining functionality for all d3 forceLayout types
class ForceLayoutAbstract extends GraphAbstract {
    constructor(individualId, containerId, globals) {
	super(individualId, containerId);

	//Link attributes
	this.linkWidth = 3;
	this.maxLenScalar = 2.5;

	//Link marker attributes
	this.markerWidth = 6;
	this.markerHeight = 6;

	//Physics attributes
	this.alpha = 1;

	//Transition attributes
	this.transitionDuration = 750; //Duration in Milliseconds
	this.startingRadius = 0;

	//Global attributes
	if (globals) this.baseUrl = globals.baseUrl;

	//Slider Attributes
	this.sliders = {"nodeCount": {"filter": this.filterByNodeCount}};
    }

    // Setup Methods //

    /**
     * Perform all auxiliary functions necessary prior to graphing
     * @param {linkData} [obj list] - A list of link objects describing Relationship data to display
     * @param {nodeData} [obj list] - A list of node objects describing MarkedIndividual data to display
     */
    setupGraph(linkData, nodeData) {	
	super.setupGraph(linkData, nodeData);

	//Establish node/link history
	this.prevLinkData = this.linkData;
	
	//Setup force simulation
	this.setForces();

	//Setup link styling
	this.defineArrows();
    }

    /**
     * Initialize forces without data context
     */
    setForces() {
	//Define the graph's forces
	this.forces = d3.forceSimulation()
	    .force("link", d3.forceLink().id(d => d.id))
	    .force("charge", d3.forceManyBody().strength(300))
	    .force("collision", d3.forceCollide().radius(d => this.getNodeMargin(d))) 
	    .force("center", d3.forceCenter(this.width/2, this.height/2))
	    .alphaDecay(0.05)
	    .velocityDecay(0.8);
    }

    /**
     * Initialize unique arrow classes for each link category
     * @param {svg} [Svg] - The Svg where arrow definitions will be stored 
     * @param {linkData} [Link list] - A list of link objects describing Relationship data to display
     */
    defineArrows(svg=this.svg, linkData=this.linkData) {
	//Define a style class for each end marker
	svg.append("defs")
	    .selectAll("marker")
	    .data(linkData.filter(d => d.type != "member"), d => d.linkId).enter()
	    .append("marker")
	    .attr("id", d => "arrow" + d.linkId  + ":" + this.graphId)
	    .attr("viewBox", "0 -5 14 14")
	    .attr("refX", d => this.getLinkTarget(d).data.r)
	    .attr("refY", 0)
	    .attr("markerWidth", this.markerWidth)
	    .attr("markerHeight", this.markerHeight)
	    .attr("orient", "auto")
	    .append("path")
	    .attr("d", "M0,-5L10,0L0,5")
	    .attr("fill", d => this.getLinkColor(d));
    }

    // Render Methods //
    
    /**
     * Render the graph with updated data
     * @param {linkData} [Link list] - A list of link objects describing Relationship data to display
     * @param {nodeData} [Node list] - A list of link objects describing Relationship data to display
     */
    updateGraph(linkData=this.linkData, nodeData=this.nodeData) {
	//Update arrow offsets
	this.updateArrows(linkData);
	
	//Update render data
	this.updateLinks(linkData);
	this.updateNodes(nodeData);

	//Update render physics
	this.applyForces(linkData);
	this.enableNodeInteraction();
    }

    /**
     * Update arrow positioning
     * @param {linkData} [Link list] - A list of link objects describing Relationship data to display
     */
    updateArrows(linkData=this.linkData) {
	this.svg.select("defs")
	    .selectAll("marker").data(linkData.filter(d => d.type != "member"), d => d.linkId)
	    .transition().duration(this.transitionDuration)
	    .attr("refX", d => {
		let node = this.getLinkTarget(d);
		return this.radius * this.getSizeScalar(node);
	    });
    }

    /**
     * Render links with updated data
     * @param {linkData} [Link list] - A list of link objects describing Relationship data to display
     */
    updateLinks(linkData=this.linkData) {
	//Define link parent/data
	let links = this.svg.selectAll(".link")
	    .data(linkData, d => d.linkId); //Sets key to linkId

	//Remove links w/ fade out
	links.exit()
	    .transition().duration(this.transitionDuration)
	    .attr("opacity", 0)
	    .attrTween("x1", d => function() { return d.source.x; })
	    .attrTween("x2", d => function() { return d.target.x; })
	    .attrTween("y1", d => function() { return d.source.y; })
	    .attrTween("y2", d => function() { return d.target.y; })
	    .remove();
	
	//Define link attributes
	this.links = links.enter().append("line")
	    .attr("class", "link")
	    .attr("opacity", 0.25)
	    .attr("stroke", d => this.getLinkColor(d))
	    .attr("stroke-width", this.linkWidth)
	    .attr("marker-end", d => {
		return "url(#arrow" + d.linkId + ":" + this.graphId + ")"
	    })
	    .lower()
	    .merge(links);

	//Fade links in
	this.links.transition()
	    .duration(this.transitionDuration)
	    .attr("opacity", 1);
    }

    /**
     * Render nodes with updated data
     * @param {nodeData} [Node list] - A list of node objects describing MarkedIndividual data to display
     */
    updateNodes(nodeData=this.nodeData) {	
	//Collapse all filtered nodes
	this.filterNodes();

	//Update/unfilter all nodes
	this.drawNodes();

	//Center the focused node
	if (this.focusedNode) this.centerNode(this.focusedNode);
				
	//Update node starting radius
	if (this.startingRadius === 0) this.startingRadius = 15;
    }

    /**
     * Hide node text, symbols, and outlines for all filtered nodes
     * @param {nodeData} [Node list] - A list of node objects describing MarkedIndividual data to display
     * @return {filteredNodes} [Node list] - The list of nodes which have been filtered and qualify to be drawn collapsed
     */
    filterNodes(nodeData=this.nodeData) {
	let filteredNodes = this.svg.selectAll(".node")
	    .filter(d => d.filtered);

	//Hide node text
	filteredNodes.selectAll("text")
	    .attr("fill-opacity", 0);

	//Hide node symbols
	filteredNodes.selectAll(".symb")
	    .style("fill-opacity", 0);

	//Collapse node outlines
	filteredNodes.selectAll("circle").transition()
	    .duration(this.transitionDuration)
	    .attr("r", this.radius * 0.5)
	    .style("fill", d => this.colorGender(d))
	    .style("stroke-width", 0)

	return filteredNodes;
    }

    /**
     * Unfilter and update node characteristics
     * @param {nodeData} [Node list] - A list of node objects describing MarkedIndividual data to display
     * @return {activeNodes} [Node list] - The list of nodes which have not been filtered and qualify to be drawn expanded
     */
    drawNodes(nodeData=this.nodeData) {
	//Define node parent/data
	let nodes = this.svg.selectAll(".node")
	    .data(nodeData, d => d.id);
	
	//Update new nodes
	let newNodes = nodes.enter().append("g")
	    .attr("class", "node")
	    .attr("id", d => "node_" + d.id)
	    .attr("fill-opacity", 0)
	    .on("mouseover", d => this.handleMouseOver(d, "node"))
	    .on("mouseout", () => this.handleMouseOut());
	
	//Join w/ existing nodes
	this.nodes = nodes.merge(newNodes)
	let activeNodes = this.nodes.filter(d => !d.filtered);
	
	//Style nodes
	this.updateNodeOutlines(newNodes, activeNodes);
	this.updateNodeSymbols(newNodes, activeNodes);
	this.updateNodeText(newNodes, activeNodes);

	//Fade nodes in
	activeNodes.transition()
	    .duration(this.transitionDuration)
	    .attr("fill-opacity", 1);

	return activeNodes;
    }
    
    /**
     * Draw each node with prescribed radius, fill, and outline
     * @param {newNodes} [Node list] - A list of newly created Node elements
     * @param {activeNodes} [Node list] - A list of previously existing, non-filtered Node elements
     */	
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

    /**
     * Draw alpha symbols for all given nodes with isAlpha attribute
     * @param {newNodes} [Node list] - A list of newly created Node elements
     * @param {activeNodes} [Node list] - A list of previously existing, non-filtered Node elements
     */	
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

    /**
     * Add text to the given nodes
     * @param {newNodes} [Node list] - A list of newly created Node elements
     * @param {activeNodes} [Node list] - A list of previously existing, non-filtered Node elements
     */	
    updateNodeText(newNodes, activeNodes) {
	//Add new node text
	newNodes.append("text")
	    .attr("class", "text")
	    .attr("dy", ".5em") //Vertically centered
	    .text(d => this.truncateText(d))
	    .style("font-size", d => (this.fontSize * this.getSizeScalar(d)) + "px")
	    .style("font-weight", d => d.data.isFocused ? "bold" : "normal")
	    .style("stroke-opacity", 0);

	//Update node text
	activeNodes.selectAll("text").transition()
	    .duration(this.transitionDuration)
	    .style("font-size", d => (this.fontSize * this.getSizeScalar(d)) + "px")
	    .style("font-weight", d => d.data.isFocused ? "bold" : "normal")
	    .style("stroke-opacity", 1);

    }

    /**
     * Update known range sliders {this.sliders} attributes
     */
    updateRangeSliderAttr() {
	this.sliders.nodeCount.max = this.nodeData.length;
    }

    /**
     * Update known range sliders {this.sliders} with contextual ranges/values
     */
    updateRangeSliders() {
	Object.entries(this.sliders).forEach(([key, slider]) => {
	    //Update html slider attributes
	    let containerRef = $(this.containerId).parent();
	    let sliderNode = containerRef.find("#" + key);
	    sliderNode.attr("max", slider.max);
	    sliderNode.val(slider.max);
	    sliderNode.change(() => {
		slider.filter(this, parseInt(sliderNode.val()), key);
	    });
	    sliderNode.on("click", (e) => e.preventDefault()); //Prevent default scroll-to-focus

	    //Update slider label value
	    containerRef.find("#" + key + "Val").text(slider.max)
	});
    }

    /**
     * Wrapper funciton serving to update known filter buttons with relevant filters
     * @param {containerId} [String] - The HTML element to append the filter buttons
     */	
    updateFilterButtons(containerId=this.containerId) {	
	//Reset filter
	$(containerId).find("#reset").on("click", () => this.resetGraph());

	for (let filterName in this.filters) {
	    let filter = this.filters[filterName];
	    this.createCheckBoxFilter(containerId, filterName, filter.func, filter.groupNum);
	}

	//Zoom in
	$(containerId).find("#gZoomIn").on("click", () => this.zoomIn());

	//Zoom out
	$(containerId).find("#gZoomOut").on("click", () => this.zoomOut());
    }

    /**
     * Reset the graph s.t. all filtered nodes are unfiltered
     */
    resetGraph() {
	//Reset filters
	for (let filterName in this.filters) this.filters[filterName].groups = {};
	this.svg.selectAll(".node").filter(d => d.filtered).remove();

	//Reset checkbox filters
	this.uncheckBoxFilters(this.containerId);

	//Reset sliders
	this.updateRangeSliders();
	
	//Reset data
	this.nodeData.forEach(d => d.filtered = false);
	this.prevLinkData = this.linkData;

	//Update graph
	this.updateGraph();
    }

    // Physics/Node-interaction Methods //

    /**
     * Apply initialized forces to a data context
     * @param {links} [obj list] - A list of link objects describing Relationship data to display
     * @param {nodeData} [Node list] - A list of node objects describing MarkedIndividual data to display
     */
    applyForces(linkData=this.linkData, nodeData=this.nodeData) {
	//Apply forces to nodes and links
	this.forces.nodes(nodeData)
	    .on("tick", () => this.ticked(this));
	this.forces.force("link")
	    .links(linkData);

	//Reset the force simulation
	this.forces.alpha(this.alpha).restart();

	//Reduce heat for future updates
	this.alpha = 0.75;
    }

    /**
     * Update all link and node position based on force interactions
     * @param {self} [ForceLayoutAbstract] - The contextual this of the current ForceLayoutAbstract (passed in for use in lambdas)
     */
    ticked(self) {
	try {
	    self.links.attr("x1", d => d.source.x)
		.attr("y1", d => d.source.y)
		.attr("x2", d => d.target.x)
		.attr("y2", d => d.target.y);
	    
	    self.nodes.attr("transform", d => "translate(" + d.x + "," + d.y + ")");
	}
	catch(error) {
	    console.log(error);
	}
    }

    /**
     * Attach node listeners for click and drag operations
     */	
    enableNodeInteraction() {
	this.nodes.on('dblclick', (d, i, nodeList) => this.releaseNode(d, nodeList[i]))
	    .on('click', d => {
		if (this.ctrlKey() && !d.filtered) this.focusNode(d);
		else if (this.shiftKey() && !d.filtered) this.visitNodePage(d);
	    })
	    .call(d3.drag()
		  .on('start', d => this.dragStarted(d))
		  .on('drag', d => this.dragged(d))
		  .on('end', (d, i, nodeList) => this.dragEnded(d, nodeList[i])));
    }

    /**
     * Visit the relevant page for the selected node
     * @param {node} [Node] - The node whose page should be visited
     */
    visitNodePage(node) {
	if (this.baseUrl) {
	    let nodeId = node.data.individualID;
	    let baseURL = this.baseUrl + "/individuals.jsp?number=";
	    window.location.href = baseURL + nodeId;
	}
	else console.warning("visitNode() is disabled as this.baseUrl is not set");
    }

    /**
     * Focus the targeted node
     * @param {node} [Node] - The node which should be targeted
     */
    focusNode(node) {
	//Unfocus all nodes
	this.svg.selectAll(".node").each(d => d.data.isFocused = false);
	
	//Focus the target node
	node.data.isFocused = true;
	this.focusedNode = node;
	
	//Update the graph
	this.updateGraph(this.prevLinkData, this.nodeData);
    }

    /**
     * Center the given node
     * @param {node} [Node] - The node for which the graph should be centered around
     */
    centerNode(node) {
	if (node.x && node.y) {
	    this.svg.transition()
		.duration(this.transitionDuration + 250) //Delay slightly for stability
		.attr("transform", "translate(" + ((this.width/2) - node.x) + "," +
		      ((this.height/2) - node.y) + ")");
	}
    }

    /**
     * Begin moving node on drag, allow for graph interactions
     * @param {node} [Node] - The node which the drag event is being applied to
     */
    dragStarted(node) {
	if (!this.isAssignedKeyBinding() && !node.filtered) {
	    if (!d3.event.active) this.forces.alphaTarget(0.5).restart();
	    node.fx = d3.event.x;
	    node.fy = d3.event.y;
	}
    }

    /**
     * Update node movement on drag
     * @param {node} [Node] - The node which the drag event is being applied to
     */
    dragged(node) {
	if (!this.isAssignedKeyBinding() && !node.filtered) {
	    node.fx = d3.event.x;
	    node.fy = d3.event.y;
	}
    }

    /**
     * Resolve node movement on drag end, halt graph movements
     * @param {nodeData} [NodeData] - The contextual node drag is being applied to
     * @param {node} [Node] - D3.js's SVG instance of the NodeData object
     */
    dragEnded(nodeData, node) {
	if (!this.isAssignedKeyBinding() && !nodeData.filtered) {
	    if (!d3.event.active) this.forces.alphaTarget(0);

	    //Color fixed node
	    d3.select(node).select("circle").style("fill", this.fixedNodeColor);
	}
    }

    /**
     * Release the targeted node from its locked position
     * @param {nodeData} [NodeData] - The contextual node drag is being applied to
     * @param {node} [Node] - D3.js's SVG instance of the NodeData object
     */
    releaseNode(nodeData, node) {
	if (!nodeData.filtered) {
	    nodeData.fx = null;
	    nodeData.fy = null;
	    
	    //Recolor node
	    d3.select(node).select("circle").style("fill", this.defNodeColor);
	}
    }

    /**
     * Apply reversible filters based upon groupNum
     * @param {nodeFilter} [lambda] - Determines which nodes should be filtered
     * @param {linkFilter} [lambda] - Determines which links should be filtered
     * @param {type} [string] - The type of absolute filter being applied
     * @param {validFilters} [string list] - Specifies filters which this filter may override
     */
    filterGraph(groupNum, nodeFilter, linkFilter, filterType, validFilters) {	
	if (this.filters[filterType].groups[groupNum]) { //Reset filter
	    this.filters[filterType].groups[groupNum] = false;

	    //Remove any nodes who no longer qualify to be filtered
	    this.svg.selectAll(".node").filter(d => {
		return !nodeFilter(d) && validFilters.includes(d.filtered);
	    }).remove();
	    
	    //Mark nodes and links which are being unfiltered
	    this.nodeData.filter(d => !nodeFilter(d) && validFilters.includes(d.filtered))
		.forEach(d => d.filtered = false);
	    
	    let linkData = this.linkData.filter(d => !d.source.filtered && !d.target.filtered);
	    this.prevLinkData = this.getUnique(this.prevLinkData.concat(linkData));
	}
	else { //Apply filter
	    this.filters[filterType].groups[groupNum] = true;

	    //Mark nodes and links which are being filtered
	    this.nodeData.filter(d => !nodeFilter(d) && !d.filtered)
		.forEach(d => d.filtered = filterType);

	    this.prevLinkData = this.prevLinkData.filter(d => !d.source.filtered &&
							 !d.target.filtered);
	}
	
	this.updateGraph(this.prevLinkData, this.nodeData);
    }

    /**
     * Apply absolute filters (i.e. thresholding)
     * @param {nodeFilter} [lambda] - Determines which nodes should be filtered
     * @param {linkFilter} [lambda] - Determines which links should be filtered
     * @param {type} [string] - The type of absolute filter being applied
     * @param {validFilters} [string list] - Specifies filters which this filter may override
     */
    absoluteFilterGraph(nodeFilter, linkFilter, type, validFilters) {
	//Remove any nodes who no longer qualify to be filtered
	this.svg.selectAll(".node").filter(d => nodeFilter(d) &&
					   validFilters.includes(d.filtered)).remove();
	
	//Mark nodes concerning whether they should be filtered
	this.nodeData.forEach(d => {
	    if (nodeFilter(d) && validFilters.includes(d.filtered)) d.filtered = false;
	    else if (!nodeFilter(d) && !d.filtered) d.filtered = type;
	});
		
	//Identify link data which should be rendered
	this.prevLinkData = this.linkData.filter(d => !d.source.filtered && !d.target.filtered);

	//Update the graph with filtered data
	this.updateGraph(this.prevLinkData, this.nodeData);
    }

    /**
     * Abstract funciton serving to update known filter buttons with relevant filters
     * @param {containerId} [String] - The HTML element to use as the root search node
     * @param {filterRef} [String] - The desired check box for which the onclick filter will be declared
     * @param {filter} [function] - The filter function to be applied
     */	
    createCheckBoxFilter(containerId, filterRef, filter, groupNum) {
	$(containerId).find("#" + filterRef + "Box").on("click", (e) => {
	    let nodeRef = $(containerId).find("#" + filterRef + "Box");
	    if (nodeRef.is(":checked")) {
		nodeRef.closest("label").css("background", this.fixedNodeColor);
	    }
	    else nodeRef.closest("label").css("background", this.defNodeColor);

	    if (!groupNum) groupNum = 0;
	    this.filterGraph(groupNum, filter, (d) => true, filterRef, this.validFilters);
	});
    }

    
    /**
     * Filters the displayed nodes to ensure only {thresh} nodes exist
     * @param {self} [SocialGraph] - A reference to this class for use in lambdas
     * @param {thresh} [int] - The maximum number of nodes allowed
     * @param {occType} [String] - The type of filter being applied
     */
    filterByNodeCount(self, thresh, occType) {
	//Update slider label value
	let sliderLabel = $(self.containerId).parent().find("#nodeCountVal");
	sliderLabel.text(thresh);
	
	let nodeFilter = (d) => (d.index < thresh)
	let linkFilter = (d) => (d.index < thresh) && (d.index < thresh)
	let validFilters = self.validFilters.concat([occType]);
	self.absoluteFilterGraph(nodeFilter, linkFilter, occType, validFilters);
    }

    
    // Helper Methods //

    /**
     * Return a color based upon the given node's gender
     * @param {node} [Node] - A Node element
     * @return {gender} - The gender of the contextual Node element
     */	
    colorGender(node) {
	try {
	    let gender = node.data.gender || "default";
	    switch (gender.toUpperCase()) {
	        case "FEMALE": return this.femaleColor; 
	        case "MALE": return this.maleColor; 
	        default: return this.defGenderColor; 
	    }
	}
	catch(error) {
	    console.error(error);
	}
    }

    /**
     * Truncate text to fit inside a given node
     * @param {node} [Node] - A Node element
     * @return {truncatedText} [String] - A truncated string, guarenteed to fit within the Node element
     */	
    truncateText(node) {
	let nodeLen = (this.radius * 2) / 5;
	let words = node.data.name.split(" ");

	let text = "";
	for (let word of words) {
	    text += word + " ";
	    if (text.length > nodeLen) {
		text = text.slice(0, nodeLen - 3) + "...";
		break;
	    }
	}

	return text.trim();
    }
    
    /**
     * Reset all filtered checkboxes
     * @param {containerId} [String] - The HTML element to use as the root search node
     */	
    uncheckBoxFilters(containerId) {
	this.validCheckFilters.forEach(filterRef => {
	    let nodeRef = $(containerId).find("#" + filterRef + "Box");
	    nodeRef.closest("label").css("background", this.defNodeColor);
	    nodeRef.prop("checked", false);
	});
    }
    
    /**
     * Determine if a key with bound functions is being pressed down
     * @return {isAssignedKey} [boolean] - Whether an assigned key is down
     */
    isAssignedKeyBinding() {
	return (this.shiftKey() || this.ctrlKey());
    }

    /**
     * Returns true if shift key is being held down
     * @return {shiftDown} [boolean] - Whether the shift key is down
     */
    shiftKey() {
	return (d3.event.shiftKey || (d3.event.sourceEvent && d3.event.sourceEvent.shiftKey));
    }

    /**
     * Returns true if ctrl key is being held down
     * @return {ctrlDown} [boolean] - Whether the ctrl key is down
     */
    ctrlKey() {
	return (d3.event.ctrlKey || (d3.event.sourceEvent && d3.event.sourceEvent.ctrlKey));
    }

    /**
     * Returns the necessary margin to prevent overlap for a given node
     * @param {nodeData} [NodeData] - Provides node attributes for the margin calculation 
     * @return {nodeMargin} [int] - Approx. distance by which nodes should be separated 
     */
    getNodeMargin(nodeData) {
	return this.radius * Math.min(this.maxLenScalar, this.getSizeScalar(nodeData) * 2);
    }

    /**
     * Returns the target node of a given link
     * @param {link} [Link] - The link for which the target node will be found
     * @return {nodeData} [NodeData] - The relevant nodeData target 
     */
    getLinkTarget(link) {
	return this.nodeData.find(node => node.id === link.target.id || node.id === link.target);
    }

    /**
     * Returns the source node of a given link
     * @param {link} [Link] - The link for which the source node will be found
     * @return {nodeData} [NodeData] - The relevant nodeData source 
     */
    getLinkSource(link) {
	return this.nodeData.find(node => node.id === link.source);
    }

    /**
     * Remove duplicates from array
     * @param {arr} [list] - The array to be filtered
     * @return {filteredArr} [list] - The original array with all duplicates removed
     */
    getUnique(arr) {
	return arr.filter((el, pos, arr) => {
	    return arr.indexOf(el) == pos;
	});
    }
}
