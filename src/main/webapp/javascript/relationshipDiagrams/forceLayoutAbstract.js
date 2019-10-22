class ForceLayoutAbstract extends GraphAbstract {
    constructor(individualId, focusedScale=1) {
	super(individualId, focusedScale);

	//Assign a unique global id to each force layout graph
	ForceLayoutAbstract.count = ForceLayoutAbstract.count + 1 || 0;
	this.graphId = ForceLayoutAbstract.count;
	
	this.linkWidth = 3;
	this.maxLenScalar = 2.5;
	
	this.markerWidth = 6;
	this.markerHeight = 6;
    }

    setNodeRadius() {
	this.nodes.forEach(d => {
	    d.data.r = this.radius * this.getSizeScalar(d);
	});
    }
    
    getForces() {
	return d3.forceSimulation()
	    .force("link", d3.forceLink().id(d => d.id))
	    .force("charge", d3.forceManyBody())
	    .force("collision", d3.forceCollide().radius(d => this.getLinkLen(d))) 
	    .force("center", d3.forceCenter(this.width/2, this.height/2));
    }

    getLinkLen(d) {
	return Math.min(this.radius * this.maxLenScalar, d.data.r * 2);
    }

    createGraph() {
	let linkRef = this.createLinks();
	let nodeRef = this.createNodes();
	return [linkRef, nodeRef];
    }

    createLinks() {
	//Define arrow heads
	this.defineArrows();

	//Create line links w/ arrows
	return this.svg.selectAll("line")
	    .data(this.links)
	    .enter()
	    .append("line")
	    .attr("stroke", d => this.getLinkColor(d))
	    .attr("stroke-width", this.linkWidth)
	    .attr("marker-end", d => {
		return "url(#arrow" + this.getLinkRef(d) + ")"
	    });
    }

    getLinkRef(d) {
	return this.graphId + "-" + d.source + ":" + d.target; 
    }

    //TODO: Add support for unique colors
    defineArrows() {	
	this.svg.append("defs")
	    .selectAll("marker")
	    .data(this.links.filter(d => d.type != "member"))
	    .enter()
	    .append("marker")
	    .attr("id", d => "arrow" + this.getLinkRef(d))
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

    getLinkTarget(link) {
	let targetId = link.target;
	return this.nodes.find(node => node.id === targetId);
    }

    //Currently unused
    getLinkSource(link) {
	let srcId = link.source;
	return this.nodes.find(node => node.id === srcId);
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

    createNodes() {
	return this.svg
	    .selectAll("g")
	    .data(this.nodes)
	    .enter().append("g")
	    .attr("class", "node")
	    .on("mouseover", d => this.handleMouseOver(d))					
	    .on("mouseout", d => this.handleMouseOut(d));
    }
    
    enableDrag(circles, force) {
	circles.call(d3.drag()
		     .on("start", d => this.dragStarted(d, force))
		     .on("drag", d => this.dragged(d, force))
		     .on("end", d => this.dragEnded(d, force)));
    }

    applyForces(forces, linkRef, nodeRef) {
	forces.nodes(this.nodes)
	    .on("tick", () => this.ticked(linkRef, nodeRef));
	
	forces.force("link")
	    .links(this.links);
    }
    
    ticked(linkRef, nodeRef) {
	linkRef.attr("x1", d => d.source.x)
	    .attr("y1", d => d.source.y)
	    .attr("x2", d => d.target.x)
	    .attr("y2", d => d.target.y);

	nodeRef.attr("transform", d => "translate(" + d.x + "," + d.y + ")");
    }

    dragStarted(d, sim) {
	if (!d3.event.active) sim.alphaTarget(0.5).restart();
	d.fx = d3.event.x;
	d.fy = d3.event.y;
    }

    dragged(d, sim) {
	d.fx = d3.event.x;
	d.fy = d3.event.y;
    }

    dragEnded(d, sim) {
	if (!d3.event.active) sim.alphaTarget(0);
	d.fx = null;
	d.fy = null;
    }
}
