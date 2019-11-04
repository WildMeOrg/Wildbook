//TODO
//Fix center forces creating odd off-graph nodes
//Add support for non-family button toggle
//Add support for x family member distance slider (?)
//Add support for smart initialization of node positions
//Fix jerky drag/zoom during node updates -- Possibly fixed?

class ForceLayoutAbstract extends GraphAbstract {
    constructor(individualId, focusedScale=1) {
	super(individualId, focusedScale);

	//Assign a Unique Global Id to each Force Layout Graph
	ForceLayoutAbstract.count = ForceLayoutAbstract.count + 1 || 0;
	this.graphId = ForceLayoutAbstract.count;

	//Link Attributes
	this.linkWidth = 3;
	this.maxLenScalar = 2.5;

	//Link Marker Attributes
	this.markerWidth = 6;
	this.markerHeight = 6;

	//Physics Attributes
	this.alpha = 1;

	//Transition Attributes
	this.transitionDuration = 750; //Duration in Milliseconds
	this.startingRadius = 0;

	//Filter Attributes
	this.filtered = {
	    'family': {},
	    'inverse_family': {}
	};
    }

    //Setup Methods
    setupGraph() {
	//Establish Node/Link History
	this.prevNodeData = this.nodeData;
	this.prevLinkData = this.linkData;
	
	//Setup Force Simulation
	this.setForces();

	//Setup Link Styling
	this.defineArrows();
    }

    setForces() {
	//Define the Graph's Forces
	this.forces = d3.forceSimulation()
//	    .alphaMin(0.05)
//	    .alphaDecay(0.05)
	    .force("link", d3.forceLink().id(d => d.id))
	    .force("charge", d3.forceManyBody())
	    .force("collision", d3.forceCollide().radius(d => this.getLinkLen(d))) 
	    .force("center", d3.forceCenter(this.width/2, this.height/2));
    }

    //TODO - Rework to style class..?
    defineArrows() {
	//Define a Style Class for each End Marker
	this.svg.append("defs")
	    .selectAll("marker")
	    .data(this.linkData.filter(d => d.type != "member")).enter()
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

    //Dynamic Render Methods
    updateGraph(linkData=this.linkData, nodeData=this.nodeData) {
	//Update Render Data
	this.updateLinks(linkData);
	this.updateNodes(nodeData);

	//Update Render Physics
	this.applyForces(linkData);
	this.enableNodeInteraction();
    }

    updateLinks(linkData=this.linkData) {
	//Define Link Parent/Data
	let links = this.svg.selectAll(".link")
	    .data(linkData, d => d.linkId); //Sets Key to linkId

	//Remove Links w/ Fade Out
	links.exit()
	    .transition()
	    .duration(this.transitionDuration)
	    .attr("opacity", 0)
	    .attrTween("x1", d => function() { return d.source.x; })
	    .attrTween("x2", d => function() { return d.target.x; })
	    .attrTween("y1", d => function() { return d.source.y; })
	    .attrTween("y2", d => function() { return d.target.y; })
	    .remove();

	//Define Link Attributes
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

	//Fade Links In
	this.links.transition()
	    .duration(this.transitionDuration)
	    .attr("opacity", 1);
    }
    
    updateNodes(nodeData=this.nodeData) {
	//Define Node Parent/Data
	let nodes = this.svg.selectAll(".node")
	    .data(nodeData, d => d.id);
	
	//Mark Removed Nodes as Collapsed
	nodes.exit().data().forEach(d => d.collapsed = true);

	//Hide Node Text
	nodes.exit().selectAll("text")
	    .attr("fill-opacity", 0);

	//Hide Node Symbols
	nodes.exit().selectAll(".symb")
	    .style("fill-opacity", 0);

	//Collapse Node Outlines
	nodes.exit().selectAll("circle").transition()
	    .duration(this.transitionDuration)
	    .attr("r", this.startingRadius)
	    .style("fill", d => this.colorGender(d))
	    .style("stroke-width", 0);
	
	//Update New Nodes
	this.nodes = nodes.enter().append("g")
	    .attr("class", "node")
	    .attr("fill-opacity", 0)
	    .on("mouseover", d => this.handleMouseOver(d))					
	    .on("mouseout", d => this.handleMouseOut(d))

	//Style Nodes
	this.drawNodeOutlines();
	this.drawNodeSymbols();
	this.addNodeText();

	//Join w/ Existing Nodes
	this.nodes = this.nodes.raise().merge(nodes);

	//Grow Node Radius
	this.nodes.selectAll("circle").transition()
	    .duration(this.transitionDuration)
	    .attr("r", d => this.radius * this.getSizeScalar(d));

	//Fade Node Symbol In
	this.nodes.selectAll(".symb").transition()
	    .duration(this.transitionDuration)
	    .attr("fill-opacity", 1);
	
	//Fade Nodes In
	this.nodes.transition()
	    .duration(this.transitionDuration)
	    .attr("fill-opacity", 1);

	//Merge Exit Nodes such that Physics may be Applied
	this.nodes = this.nodes.merge(nodes.exit())
	
	//Update Node Starting Radius
	if (this.startingRadius === 0) this.startingRadius = 15;
    }

    //Static Render Methods
    addLegend(containerId) {
	d3.select(containerId + " svg").append("g")
	    .attr("class", "legend")
//	    .attr("transform", "translate(90%, 10%)")
	    .style("font-size", "12px")
	    .call(d3.legend);
    }

    //Physics Methods
    applyForces(linkData=this.linkData, nodeData=this.nodeData) {
	//Apply Forces to Nodes and Links
	this.forces.nodes(nodeData)
	    .on("tick", () => this.ticked(this));
	this.forces.force("link")
	    .links(linkData);

	//Reset the Force Simulation
	this.forces.alpha(this.alpha).restart();

	//Reduce Heat for Future Updates
	this.alpha = 0.2;
    }
    
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
    
    enableNodeInteraction() {
	this.nodes.on('dblclick', (d, i, nodeList) => this.releaseNode(d, nodeList[i]))
	    .on('click', d => this.handleFilter(d.group))
	    .call(d3.drag()
		  .on("start", d => this.dragStarted(d))
		  .on("drag", d => this.dragged(d))
		  .on("end", (d, i, nodeList) => this.dragEnded(d, nodeList[i])));
    }

    dragStarted(d) {
	if (!(this.isAssignedKeyBinding() || d.collapsed)) {
	    if (!d3.event.active) this.forces.alphaTarget(0.5).restart();
	    d.fx = d3.event.x;
	    d.fy = d3.event.y;
	}
    }

    dragged(d) {
	if (!(this.isAssignedKeyBinding() || d.collapsed)) {
	    d.fx = d3.event.x;
	    d.fy = d3.event.y;
	}
    }

    dragEnded(d, node) {
	if (!(this.isAssignedKeyBinding() || d.collapsed)) {
	    if (!d3.event.active) this.forces.alphaTarget(0);

	    //Color fixed node
	    d3.select(node).select("circle").style("fill", this.fixedNodeColor);
	}
    }

    releaseNode(d, node) {
	d.fx = null;
	d.fy = null;

	//Recolor node
	d3.select(node).select("circle").style("fill", this.defNodeColor);
    }
    
    handleFilter(groupNum) {
	if (this.shiftKey() && this.ctrlKey()) this.resetGraph(); 
	else if (this.shiftKey()) { //Filter Inverse of Selected Family
	    let filter = (d) => d.group === groupNum;
	    this.filterGraph(groupNum, filter, filter, 'inverse_family');
	}
	else if (this.ctrlKey()) { //Filter Selected Family
	    let nodeFilter = (d) => d.group !== groupNum;
	    let linkFilter = (d) => (d.source.group !== groupNum &&
				     d.target.group !== groupNum);
	    this.filterGraph(groupNum, nodeFilter, linkFilter, 'family');
	}
    }

    resetGraph() {
	for (filter in this.filtered) filter = {}
	this.svg.selectAll(".node").filter(d => d.filtered).remove();
	this.updateGraph();
    }

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

    //TODO - Rename
    isAssignedKeyBinding() {
	return (this.shiftKey() || this.ctrlKey());
    }
    
    shiftKey() {
	return (d3.event.shiftKey || (d3.event.sourceEvent && d3.event.sourceEvent.shiftKey));
    }

    ctrlKey() {
	return (d3.event.ctrlKey || (d3.event.sourceEvent && d3.event.sourceEvent.ctrlKey));
    }
    
    setNodeRadius() {
	this.nodeData.forEach(d => {
	    d.data.r = this.radius * this.getSizeScalar(d);
	});
    }

    getLinkLen(link) {
	return Math.min(this.radius * this.maxLenScalar, link.data.r * 2);
    }

    
    getLinkTarget(link) {
	return this.nodeData.find(node => node.id === link.target);
    }

    //Currently unused
    getLinkSource(link) {
	let srcId = link.source;
	return this.nodeData.find(node => node.id === srcId);
    }

    //TODO: Switch to enum
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
}
