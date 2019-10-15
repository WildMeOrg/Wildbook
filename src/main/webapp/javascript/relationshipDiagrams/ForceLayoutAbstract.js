class ForceLayoutAbstract extends GraphAbstract {
    constructor(individualId) {
	super(individualId);
    }

    getForces() {
	return d3.forceSimulation()
	    .force("link", d3.forceLink().id(d => d.id))
	    .force("charge", d3.forceManyBody())
	    .force("collision", d3.forceCollide().radius(this.radius * 2)) 
	    .force("center", d3.forceCenter(this.width/2, this.height/2));
    }

    createGraph() {
	let linkRef = this.createLinks();
	let nodeRef = this.createNodes();
	return [linkRef, nodeRef];
    }
    
    createLinks() {
	return this.svg
	    .selectAll("line")
	    .data(this.links)
	    .enter().append("line")
	    .attr("stroke", d => 
		  (d.type === "familial") ? this.famLinkColor : this.defLinkColor;
		 )
	    .attr("stroke-width", 2);
    }

    createNodes() {
	return this.svg
	    .selectAll("g")
	    .data(this.nodes)
	    .enter().append("g")
	    .attr("class", "node")
	    .on("mouseover", this.handleMouseOver)					
	    .on("mouseout", this.handleMouseOut);
    }

    enableDrag(circles, force) {
	//circles.attr("fill", d => this.color(d.group))
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

    
    color(group) {
	return d3.scaleOrdinal(d3.schemeCategory20)(group);
    }

    ticked(linkRef, nodeRef) {
	linkRef.attr("x1", d => d.source.x)
	    .attr("y1", d => d.source.y)
	    .attr("x2", d => d.target.x)
	    .attr("y2", d => d.target.y);

	nodeRef.attr("transform", d => "translate(" + d.x + "," + d.y + ")");
    }

    dragStarted(d, sim) {
	if (!d3.event.active) sim.alphaTarget(0.3).restart();
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
