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
	
	this.linkWidth = 3;
	this.maxLenScalar = 2.5;
	
	this.markerWidth = 6;
	this.markerHeight = 6;

	this.alpha = 1;
	this.transitionDuration = 750; //Duration in Milliseconds
	this.startingRadius = 0;
	this.filtered = false;
    }

    //Setup Methods
    setupGraph() {
	//Setup Force Simulation
	this.setForces();

	//Setup Link Styling
	this.defineArrows();
    }

    setForces() {
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
	console.log(linkData);
	
	//Update Render Data
	this.updateLinks(linkData);
	this.updateNodes(nodeData);

	//Update Render Physics
	this.applyForces(linkData, nodeData);
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
	console.log(nodeData);
	
	//Define Node Parent/Data
	let nodes = this.svg.selectAll(".node")
	    .data(nodeData, d => d.id);

	//Remove Nodes
	nodes.exit().remove();
	
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

	//Grow Node Symbol
	this.nodes.selectAll(".symb").transition()
	    .duration(this.transitionDuration)
	    .attr("fill-opacity", 1);
	
	//Fade Nodes In
	this.nodes.transition()
	    .duration(this.transitionDuration)
	    .attr("fill-opacity", 1);
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
	this.alpha = 0.2; //Reduce Heat for Future Updates
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
	if (!(this.isAssignedKeyBinding(d.collapsed) || d.collapsed)) {
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
	if (this.filtered) this.resetGraph();
	else if (this.shiftKey()) this.filterGraph(groupNum, false);
	else if (this.ctrlKey()) this.filterGraph(groupNum, true);
    }

    resetGraph() {
	this.filtered = false;
	if (this.targetNodes) this.targetNodes.remove();
	this.targetNodes = null;
	this.updateGraph();
    }

    filterGraph(groupNum, inverted) {
	if (!this.filtered) {
	    this.filtered = true;
	    this.targetNodes = this.svg.selectAll(".node")
		.filter(d => {
		    if (!inverted) return d.group !== groupNum;
		    else return d.group === groupNum;
		});

	    let linkData = this.linkData.filter(d => {
		if (!inverted) return d.group === groupNum;
		else return d.group !== groupNum;
	    });
	    
	    this.updateGraph(linkData); //nodeData defaults to this.nodeData
	    this.collapseNodes();
	}
    }

    //Helper Methods
    collapseNodes() {
	this.startingRadius = 15;
	this.targetNodes.data().forEach(d => d.collapsed = true);
	
	this.targetNodes.selectAll("text")
	    .attr("fill-opacity", 0);

	this.targetNodes.selectAll(".symb")
	    .style("fill-opacity", 0);
	    
	this.targetNodes.selectAll("circle")
	    .transition()
	    .duration(this.transitionDuration)
	    .attr("r", this.startingRadius)
	    .style("fill", d => this.colorGender(d))
	    .style("stroke-width", 0);
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
