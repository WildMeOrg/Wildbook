//Occurence graph global API (used in individuals.jsp)
function setupOccurrenceGraph(individualID) { //TODO - look into individualID
    let focusedScale = 1.75;
    let occ = new OccurrenceGraph(individualID, "#bubbleChart", focusedScale); 
    occ.applyOccurrenceData();
}

//Sparse-tree mapping co-occurrence relationships between a focused individual and its species
class OccurrenceGraph extends ForceLayoutAbstract {
    constructor(individualId, containerId, focusedScale) {
	super(individualId, containerId, focusedScale);

	//TODO - Remove ref, use key
	this.sliders = {"temporal": {"ref": "temporal"},
			"spatial": {"ref": "spatial"}};	
    }

    //Wrapper function to gather species data and generate a graph
    applyOccurrenceData() {
	this.parser.parseJSON(this.id, (nodes, links) => this.graphOccurrenceData(nodes, links), true);
    }
    
    //Generate a co-occurrence graph
    graphOccurrenceData(nodes, links) {
	if (nodes.length >= 1) { 
	    //Create graph w/ forces
	    this.setupGraph(links, nodes);
	    this.updateGraph(links, nodes);
	}
	else this.showTable("#cooccurrenceDiagram", "#cooccurrenceTable");
    }

    //Perform all auxiliary functions necessary prior to graphing
    setupGraph(linkData, nodeData) {
	super.setupGraph(linkData, nodeData);

	//Create range sliders
	this.getRangeSliderAttr(this.focusedNode);
	this.updateRangeSliders();
    }

    updateGraph(linkData=this.linkData, nodeData=this.nodeData) {
	//Update link data
	this.updateLinkThreshCount(this.focusedNode);
	
	super.updateGraph(linkData, nodeData);
    }

    //Calculate the maximum and average node differences for the spatial/temporal sliders
    getRangeSliderAttr(refNode) {
	let [distArr, timeArr] = this.analyzeNodeData(refNode);
	this.sliders.temporal.max = Math.ceil(Math.max(...timeArr));
	this.sliders.spatial.max = Math.ceil(Math.max(...distArr));
    }

    analyzeNodeData(refNode) {
	let distArr = [], timeArr = []
	this.nodeData.forEach(d => {
	    if (d.id !== refNode.id) {
		let dist = this.getNodeMin(refNode, d, "spatial");
		let time = this.getNodeMin(refNode, d, "temporal");
		distArr.push(dist)
		timeArr.push(time);
	    }
	});

	return [distArr, timeArr]
    }

    //Wrapper for finding the minimum spatial/temporal differences between two nodes
    getNodeMin(node1, node2, type) {
	let node1Sightings = node1.data.sightings;
	let node2Sightings = node2.data.sightings;
	return this.getNodeMinBruteForce(node1Sightings, node2Sightings, type);
    }

    //TODO - Update combine w/ thresh
    //TODO - Consider strip optimizations
    //Find the minimum spatial/temporal difference between two node sightings
    getNodeMinBruteForce(node1Sightings, node2Sightings, type) {
	let val, min = Number.MAX_SAFE_INTEGER;
	node1Sightings.forEach(node1 => {
	    node2Sightings.forEach(node2 => {
		if (type === "spatial")
		    val = this.calculateDist(node1.location, node2.location);
		else if (type === "temporal")
		    val = this.calculateTime(node1.time.datetime, node2.time.datetime);

		if (val < min) min = val;
	    });
	});

	return min;
    }

    //TODO - Cleanup
    //TODO - Consider switching where link/node filtration occurs
    updateLinkThreshCount(refNode) {
	let spatialThresh = parseInt($("#spatial").val());
	let temporalThresh = parseInt($("#temporal").val());
	
	this.linkData.forEach(link => {
	    let linkTarget = link.target.id || link.target;
	    let node = this.nodeData.find(node => node.id === linkTarget);
	    let count = this.getLinkThreshCount(refNode, node, spatialThresh, temporalThresh);
	    link.count = count;
	});
    }

    //TODO - Cleanup
    getLinkThreshCount(node1, node2, spatialThresh, temporalThresh) {
	let node1Sightings = node1.data.sightings;
	let node2Sightings = node2.data.sightings;

	let count = 0;
	node1Sightings.forEach(node1 => {
	    node2Sightings.forEach(node2 => {
		let spatialVal = this.calculateDist(node1.location, node2.location);
		let temporalVal = this.calculateTime(node1.time.datetime, node2.time.datetime);

		if (spatialVal <= spatialThresh && temporalVal <= temporalThresh) count++;
	    });
	});

	return count;
    }
    
    //Calculate the spatial difference between two node sighting locations
    calculateDist(node1Loc, node2Loc) {
	return Math.pow(Math.abs(Math.pow(node1Loc.lon - node2Loc.lon, 2) -
				 Math.pow(node1Loc.lat - node2Loc.lat, 2)), 0.5);
    }

    //Calculate the temporal difference between two node sightings
    calculateTime(node1Time, node2Time) {
	return Math.abs(node1Time - node2Time)
    }
    
    //Update known range sliders (this.sliders) with contextual ranges/values
    updateRangeSliders() {
	Object.values(this.sliders).forEach(slider => {
	    //Update html slider attributes
	    let sliderNode = $("#" + slider.ref);
	    sliderNode.attr("max", slider.max);
	    sliderNode.val(slider.max);
	    sliderNode.change(() => {
		this.filterByOccurrence(this, parseInt(sliderNode.val()), slider.ref)
	    });
	    sliderNode.on("click", (e) => e.preventDefault()); //Prevent default scroll-to-focus

	    //Update slider label value
	    $("#" + slider.ref + "Val").text(slider.max)
	});
    }

    //Filter nodes by spatial/temporal differences, displaying those less than the set threshold
    filterByOccurrence(self, thresh, occType) {
	//Update slider label value
	$("#" + occType + "Val").text(thresh)
	
	let focusedNode = self.nodeData.find(d => d.data.isFocused);
	let nodeFilter = (d) => (self.getNodeMin(focusedNode, d, occType) <= thresh)
	let linkFilter = (d) => (self.getNodeMin(focusedNode, d.source, occType) <= thresh) &&
	    (self.getNodeMin(focusedNode, d.target, occType) <= thresh)

	let validFilters = this.validFilters.concat([occType]);
	self.absoluteFilterGraph(nodeFilter, linkFilter, occType, validFilters);
    }

    //Reset the graph s.t. no filters are applied
    resetGraph() {
	//Reset all filtered nodes
	super.resetGraph();

	//Reset sliders
	this.resetSliders();
    }

    //Reset each slider's text and value
    resetSliders() {
	Object.values(this.sliders).forEach(slider => {
	    $("#" + slider.ref + "Val").text(slider.max)
	    $("#" + slider.ref).attr("max", slider.max);
	    $("#" + slider.ref).val(slider.max)
	});
    }

    updateLinks(linkData=this.linkData) {	
	//Add link labels
	let linkLabels = this.svg.selectAll('.linkLabel')
	    .data(linkData, d => d.linkId);

	linkLabels.exit()
	    .transition().duration(this.transitionDuration)
	    .attrTween("transform", d => {
		let self = this;
		return () => "translate(" + self.linearInterp(d, "x") +
		    "," + self.linearInterp(d, "y") + ")"
	    })
	    .attr("opacity", 0)
	    .remove();
	
	let newLabels = linkLabels.enter().append("g")
	    .attr("class", "linkLabel")
	    .attr("opacity", 0)
	    .lower();
	
	newLabels.append("circle")
	    .attr("r", 9)
	    .style("fill", "white")
	    .on("mouseover", d => this.handleMouseOver(d, "link"))				
	    .on("mouseout", () => this.handleMouseOut());

	newLabels.append("text")
	    .attr("dy", "0.4em")
	    .attr("dx", "-0.2em")
	    .text(d => d.count)
	    .attr("font-size", 15)
	    .attr("font-weight", "bold");

	newLabels.transition()
	    .duration(this.transitionDuration)
	    .attr("opacity", 1);

	this.linkLabels = newLabels.merge(linkLabels);
	
	//Add link edges
	super.updateLinks(linkData);
    }

    ticked(self) {
	super.ticked(self);

	self.linkLabels.attr("transform", d => "translate(" + this.linearInterp(d, "x") +
			     "," + this.linearInterp(d, "y") + ")");
    }

    //Calculates a point 40 percent between a given link's target and source nodes
    linearInterp(link, axis) {
	let srcId = link.source.data.individualID
	let src = (srcId === this.id) ? link.source[axis] : link.target[axis];
	let target = (srcId === this.id) ? link.target[axis] : link.source[axis];
	let diff = src - target;
	return target + (diff * 0.4);
    }
}


