//TODO
//Fix center forces creating odd off-graph nodes
//Add support for x family member distance slider (?)
//Add support for smart initialization of node positions

//Abstract class defining functionality for all d3 forceLayout types
class ForceLayoutAbstract extends GraphAbstract {
    constructor(individualId, focusedScale=1) {
	super(individualId, focusedScale);

	//Assign a unique global id to each force layout graph
	ForceLayoutAbstract.count = ForceLayoutAbstract.count + 1 || 0;
	this.graphId = ForceLayoutAbstract.count;

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

	//Filter attributes
	this.filtered = {
	    'family': {},
	    'inverse_family': {}
	};
    }

    // Setup Methods //

    //Perform all auxiliary functions necessary prior to graphing
    setupGraph(containerId) {
	super.setupGraph(containerId);
	
	//Establish node/link history
	this.prevNodeData = this.nodeData;
	this.prevLinkData = this.linkData;
	
	//Setup force simulation
	this.setForces();

	//Setup link styling
	this.defineArrows();

	//Setup intial focused node
	this.focusedNode = this.nodeData.filter(d => d.data.isFocused)[0];
    }

    //Initialize forces without data context
    setForces() {
	//Define the graph's forces
	this.forces = d3.forceSimulation()
//	    .alphaMin(0.05)
//	    .alphaDecay(0.05)
	    .force("link", d3.forceLink().id(d => d.id))
	    .force("charge", d3.forceManyBody())
	    .force("collision", d3.forceCollide().radius(d => this.getLinkLen(d))) 
	    .force("center", d3.forceCenter(this.width/2, this.height/2));
    }

    //TODO - Rework to style class..?
    //Initialize unique arrow classes for each link category
    defineArrows(svg=this.svg, linkData=this.linkData) {
	//Define a style class for each end marker
	svg.append("defs")
	    .selectAll("marker")
	    .data(linkData.filter(d => d.type != "member")).enter()
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
    
    //Render a graph with updated data
    updateGraph(linkData=this.linkData, nodeData=this.nodeData) {
	//Update render data
	this.updateLinks(linkData);
	this.updateNodes(nodeData);

	//Update render physics
	this.applyForces(linkData);
	this.enableNodeInteraction();
    }

    //Render links with updated data
    updateLinks(linkData=this.linkData) {
	//Define link parent/data
	let links = this.svg.selectAll(".link")
	    .data(linkData, d => d.linkId); //Sets key to linkId

	//Remove links w/ fade out
	links.exit()
	    .transition()
	    .duration(this.transitionDuration)
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

    //Render nodes with updated data
    updateNodes(nodeData=this.nodeData) {
	//Define node parent/data
	let nodes = this.svg.selectAll(".node")
	    .data(nodeData, d => d.id);
	
	//Mark removed nodes as collapsed
	nodes.exit().data().forEach(d => d.collapsed = true);

	//Hide node text
	nodes.exit().selectAll("text")
	    .attr("fill-opacity", 0);

	//Hide node symbols
	nodes.exit().selectAll(".symb")
	    .style("fill-opacity", 0);

	//Collapse node outlines
	nodes.exit().selectAll("circle").transition()
	    .duration(this.transitionDuration)
	    .attr("r", this.startingRadius)
	    .style("fill", d => this.colorGender(d))
	    .style("stroke-width", 0);

	//Mark added nodes as un-collapsed
	nodes.enter().data().forEach(d => d.collapsed = false);
	
	//Update new nodes
	this.nodes = nodes.enter().append("g")
	    .attr("class", "node")
	    .attr("fill-opacity", 0)
	    .on("mouseover", () => this.handleMouseOver())					
	    .on("mouseout", () => this.handleMouseOut())

	//Style nodes
	this.drawNodeOutlines();
	this.drawNodeSymbols();
	this.addNodeText();

	//Join w/ existing nodes
	this.nodes = this.nodes.raise().merge(nodes);
	
	//Grow node radius
	this.nodes.selectAll("circle").transition()
	    .duration(this.transitionDuration)
	    .attr("r", d => this.radius * this.getSizeScalar(d))
	    .style("stroke-width", d => {
		let a = this.strokeWidth * this.getSizeScalar(d)
		console.log(a);
		return a + "px";
	    });

	//Grow node text
	this.nodes.selectAll("text").transition()
	    .duration(this.transitionDuration)
	    .style("font-size", d => (this.fontSize * this.getSizeScalar(d)) + "px")
	    .style("font-weight", d => d.data.isFocused ? "bold" : "normal");

	//Fade node symbol in
	this.nodes.selectAll(".symb").transition()
	    .duration(this.transitionDuration)
	    .style("fill-opacity", 1)
	    .attr("transform", d => {
		let radialPos = Math.cos(Math.PI / 4);
		let pos = this.radius * this.getSizeScalar(d) * radialPos;
		return "translate(" + pos + "," + -pos + ")";
	    })
	    .attr("d", d => {
		return d3.symbol().type(d3.symbolCircle)
		    .size(() => {
			if (d.data.role && d.data.role.toUpperCase() == "ALPHA")
			    return this.alphaSymbSize * this.getSizeScalar(d);
			else return 0;
		    })();
	    });
	
	//Fade nodes in
	this.nodes.transition()
	    .duration(this.transitionDuration)
	    .attr("fill-opacity", 1);

	//Merge exit nodes such that physics may be applied
	this.nodes = this.nodes.merge(nodes.exit())
	
	//Update node starting radius
	if (this.startingRadius === 0) this.startingRadius = 15;
    }

    // Physics Methods //

    //Apply initialized forces to a data context
    applyForces(linkData=this.linkData, nodeData=this.nodeData) {
	//Apply forces to nodes and links
	this.forces.nodes(nodeData)
	    .on("tick", () => this.ticked(this));
	this.forces.force("link")
	    .links(linkData);

	//Reset the force simulation
	this.forces.alpha(this.alpha).restart();

	//Reduce heat for future updates
	this.alpha = 0.2;
    }

    //Update all link and node position based on force interactions
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

    //Attach node listeners for click and drag operations
    enableNodeInteraction() {
	this.nodes.on('dblclick', (d, i, nodeList) => this.releaseNode(d, nodeList[i]))
	    .on('click', d => this.focusNode(d))
	    .call(d3.drag()
		  .on("start", d => this.dragStarted(d))
		  .on("drag", d => this.dragged(d))
		  .on("end", (d, i, nodeList) => this.dragEnded(d, nodeList[i])));
    }

    focusNode(d) {
	//Focus targeted node
	if (this.ctrlKey()) {
	    //Unfocus all nodes
	    this.svg.selectAll(".node").each(d => d.data.isFocused = false);
	    
	    //Focus the target node
	    d.data.isFocused = true;
	    this.focusedNode = d;
	    
	    //Update the graph
	    this.updateGraph();
	}
    }

    //Begin moving node on drag, allow for graph interactions
    dragStarted(d) {
	if (!(this.isAssignedKeyBinding() || d.collapsed)) {
	    if (!d3.event.active) this.forces.alphaTarget(0.5).restart();
	    d.fx = d3.event.x;
	    d.fy = d3.event.y;
	}
    }

    //Update node movement on drag
    dragged(d) {
	if (!(this.isAssignedKeyBinding() || d.collapsed)) {
	    d.fx = d3.event.x;
	    d.fy = d3.event.y;
	}
    }

    //Resolve node movement on drag end, halt graph movements
    dragEnded(d, node) {
	if (!(this.isAssignedKeyBinding() || d.collapsed)) {
	    if (!d3.event.active) this.forces.alphaTarget(0);

	    //Color fixed node
	    d3.select(node).select("circle").style("fill", this.fixedNodeColor);
	}
    }

    //Release the targeted node from its locked position
    releaseNode(d, node) {
	d.fx = null;
	d.fy = null;

	//Recolor node
	d3.select(node).select("circle").style("fill", this.defNodeColor);
    }

    //TODO - Determine if this is a desired feature
    //Handle all general forceLayout node/link filters
    handleFilter(groupNum) {
	if (this.shiftKey()) { //Filter Inverse of Selected Family
	    let filter = (d) => d.group === groupNum;
	    this.filterGraph(groupNum, filter, filter, "inverse_family");
	}
	else if (this.ctrlKey()) { //Filter Selected Family
	    let nodeFilter = (d) => d.group !== groupNum;
	    let linkFilter = (d) => (d.source.group !== groupNum &&
				     d.target.group !== groupNum);
	    this.filterGraph(groupNum, nodeFilter, linkFilter, "family");
	}
    }

    //Reset a graph s.t. all filtered nodes are unfiltered
    resetGraph() {
	for (let filter in this.filtered) this.filtered[filter] = {};
	this.svg.selectAll(".node").filter(d => d.filtered).remove();
	this.prevNodeData = this.nodeData;
	this.prevLinkData = this.linkData;
	this.updateGraph();
    }

    //Apply reversible filters based upon groupNum
    filterGraph(groupNum, nodeFilter, linkFilter, filterType) {
	let nodeData, linkData;
	if (this.filtered[filterType][groupNum]) {
	    this.filtered[filterType][groupNum] = false;
	    this.nodeData.filter(d => !nodeFilter(d))
		.forEach(d => d.filtered = false);
	    
	    this.prevNodeData = this.prevNodeData.concat(
		this.nodeData.filter(d => !nodeFilter(d)));
	    this.prevLinkData = this.prevLinkData.concat(
		this.linkData.filter(d => !linkFilter(d) &&
				     !(d.source.filtered || d.target.filtered)));

	    this.svg.selectAll(".node").filter(d => !nodeFilter(d))
		.remove();
	}
	else {
	    this.filtered[filterType][groupNum] = true;
	    this.nodeData.filter(d => !nodeFilter(d))
		.forEach(d => d.filtered = true);
	    
	    this.prevNodeData = this.prevNodeData.filter(nodeFilter);
	    this.prevLinkData = this.prevLinkData.filter(linkFilter);
	}
	this.updateGraph(this.prevLinkData, this.prevNodeData);
    }

    // Helper Methods //
    
    //Determine if key has other bound function
    isAssignedKeyBinding() {
	return (this.shiftKey() || this.ctrlKey());
    }

    //Returns true if shift key is being held down
    shiftKey() {
	return (d3.event.shiftKey || (d3.event.sourceEvent && d3.event.sourceEvent.shiftKey));
    }

    //Returns true if ctrl key is being held down
    ctrlKey() {
	return (d3.event.ctrlKey || (d3.event.sourceEvent && d3.event.sourceEvent.ctrlKey));
    }

    //Returns the length of a given link
    getLinkLen(link) {
	return Math.min(this.radius * this.maxLenScalar, link.data.r * 2);
    }

    //Returns the target node of a given link
    getLinkTarget(link) {
	return this.nodeData.find(node => node.id === link.target);
    }

    //Returns the source node of a given link
    getLinkSource(link) {
	return this.nodeData.find(node => node.id === link.source);
    }
}
