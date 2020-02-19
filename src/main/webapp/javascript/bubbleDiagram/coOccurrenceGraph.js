/**
 * Occurence graph global API (used in individuals.jsp)
 * @param {individualID} [string] - Identifies the central node
 * @param {globals} [list] - Global variables passed in to maintain 
 *   compatibility with individuals.jsp
 * @param {parser} [JSONParser] - Optional parser specification. Defaults to null
 */	
function setupOccurrenceGraph(individualID, globals, parser=null) {
    let focusedScale = 1.75;
    let occ = new OccurrenceGraph(individualID, "#bubbleChart", globals, focusedScale, parser);
    occ.applyOccurrenceData() //TODO - Shift to some promise passing
}

//Sparse-tree mapping co-occurrence relationships between a focused individual and its species
class OccurrenceGraph extends ForceLayoutAbstract {
    constructor(individualId, containerId, globals, focusedScale, parser=null) {
	super(individualId, containerId);

	this.focusedScale = focusedScale;
	
	if (parser) this.parser = parser;
	else this.parser = new JSONParser(globals, null, true, 30);

	//TODO - Remove ref, use key
	this.sliders = {"temporal": {"ref": "temporal"},
			"spatial": {"ref": "spatial"}};	
    }

    /**
     * Wrapper function to gather species data and generate a graph
     */	   
    applyOccurrenceData() {
	this.parser.parseJSON(this.id, (nodes, links) => this.graphOccurrenceData(nodes, links), true);
    }
    
    /**
     * Generate a co-occurrence graph
     * @param {nodes} [Node list] - A list of node objects queried from the MarkedIndividual psql table
     * @param {links} [obj list] - A list of link objects queried from the Relationship psql table
     */	
    graphOccurrenceData(nodes, links) {
	//Create graph w/ forces
	if (nodes.length >= 1) { 
	    this.setupGraph(links, nodes);
	    this.updateGraph(links, nodes);
	}
	else this.showTable("#cooccurrenceDiagram", "#cooccurrenceTable");
    }

    /**
     * Perform all auxiliary functions necessary prior to graphing
     * @param {linkData} [obj list] - A list of link objects queried from the MarkedIndividual psql table
     * @param {nodeData} [Node list] - A list of Node objects queried from the MarkedIndividual psql table
     */	
    setupGraph(linkData, nodeData) {
	super.setupGraph(linkData, nodeData);

	//Create range sliders
	this.getRangeSliderAttr(this.focusedNode);
	this.updateRangeSliders();
    }

    //TODO - Comment
    updateGraph(linkData=this.linkData, nodeData=this.nodeData) {
	//Update link data
	this.updateLinkThreshCount(this.focusedNode);	
	super.updateGraph(linkData, nodeData);
    }

    /**
     * Calculate the maximum values for the spatial/temporal sliders
     * @param {focusedNode} [Node] - Central coOccurrence node
     */	
    getRangeSliderAttr(focusedNode) {
	let [distArr, timeArr] = this.analyzeNodeData(focusedNode);
	this.sliders.temporal.max = Math.ceil(Math.max(...timeArr, 1));
	this.sliders.spatial.max = Math.ceil(Math.max(...distArr, 1));
    }

    /**
     * Calculate the minimum spatial/temporal differences between all nodes and the {focusedNode}
     * @param {focusedNode} [Node] - Central coOccurrence node
     * @return {distArr, timeArr} [2D list] - The spatial/temporal minimum differences 
     *   for each node
     */	
    analyzeNodeData(focusedNode) {
	let distArr = [], timeArr = []
	this.nodeData.forEach(d => {
	    if (d.id !== focusedNode.id) {
		let [dist, time] = this.getNodeMin(focusedNode, d);		
		distArr.push(dist)
		timeArr.push(time);
	    }
	});

	return [distArr, timeArr]
    }

    /**
     * Returns the minimum spatial/temporal difference between two nodes as specified
     * @param {node1} [Node] - The first node being compared
     * @param {node2} [Node] - The second node being compared
     * @param {type} [String] - Determines whether the minimum spatial or temporal difference 
     *   should be returned
     */
    getNodeMinType(node1, node2, type) {
	let [dist, time] = this.getNodeMin(node1, node2);
	if (type == "spatial") return dist;
	else if (type == "temporal") return time;
    }

    //TODO - Consider strip optimizations
    /**
     * Finds the minimum spatial/temporal differences between two nodes, 
     *   and updates co-occurrence data
     * @param {node1} [Node] - The first node being compared
     * @param {node2} [Node] - The second node being compared
     */
    getNodeMin(node1, node2) {
	let node1Sightings = node1.data.sightings;
	let node2Sightings = node2.data.sightings;	
	
	let timeMin, distMin;
	let minDist = Number.MAX_SAFE_INTEGER, minTime = Number.MAX_SAFE_INTEGER;
	node1Sightings.forEach(node1S => {
	    node2Sightings.forEach((node2S, idx) => {
		let distDiff = this.calculateDist(node1S.location, node2S.location);
		let timeDiff = this.calculateTime(node1S.time.datetime, node2S.time.datetime);

		//Update minimum
		if (distDiff <= minDist && timeDiff <= minTime) {
		    minDist = distDiff;
		    minTime = timeDiff;
		}
	    });
	});

	return [minDist, minTime];
    }

    updateLinkThreshCount(focusedNode) {
	let focusedId = focusedNode.id;
	let spatialThresh = parseInt($("#spatial").val());
	let temporalThresh = parseInt($("#temporal").val());

	this.linkData.forEach(link => {
	    let targetId = link.target.id || link.target;
	    let sourceId = link.source.id || link.source;
	    let linkId = (targetId === focusedId) ? sourceId : targetId;
	    
	    let node = this.nodeData.find(node => node.id === linkId);
	    let threshEncounters = this.getLinkThreshEncounters(focusedNode, node, spatialThresh,
								temporalThresh);
	    link.validEncounters = threshEncounters;
	    link.count = threshEncounters.length;
	});
    }

    getLinkThreshEncounters(node1, node2, spatialThresh, temporalThresh) {
	let node1Sightings = node1.data.sightings;
	let node2Sightings = node2.data.sightings;
	
	let validEncounters = [], idxSet = new Set();
	node1Sightings.forEach(node1 => {
	    node2Sightings.forEach((node2, idx) => {
		let spatialVal = this.calculateDist(node1.location, node2.location);
		let temporalVal = this.calculateTime(node1.time.datetime, node2.time.datetime);
		
		if (spatialVal <= spatialThresh && temporalVal <= temporalThresh &&
		    !idxSet.has(idx)) {
		    idxSet.add(idx);
		    validEncounters.push(node2);
		}
	    });
	});

	return validEncounters;
    }
    
    //Calculate the spatial difference between two node sighting locations
    calculateDist(node1Loc, node2Loc) {
	if (node1Loc && node2Loc) {
	    return Math.pow(Math.pow(node1Loc.lon - node2Loc.lon, 2) +
			    Math.pow(node1Loc.lat - node2Loc.lat, 2), 0.5);
	}
	return -1;
    }

    //Calculate the temporal difference between two node sightings
    calculateTime(node1Time, node2Time) {
	if (typeof node1Time === "number" && typeof node2Time === "number") {
	    return Math.abs(node1Time - node2Time)
	}
	return -1;
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
	let nodeFilter = (d) => (self.getNodeMinType(focusedNode, d, occType) <= thresh)
	let linkFilter = (d) => (self.getNodeMinType(focusedNode, d.source, occType) <= thresh) &&
	    (self.getNodeMinType(focusedNode, d.target, occType) <= thresh)

	let validFilters = this.validFilters.concat([occType]);
	self.absoluteFilterGraph(nodeFilter, linkFilter, occType, validFilters);
    }

    //Reset the graph s.t. no filters are applied
    resetGraph() {
	super.resetGraph();
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
	    .attr("r", 12)
	    .style("fill", "white")
	    .on("mouseover", d => this.handleMouseOver(d, "link"))				
	    .on("mouseout", () => this.handleMouseOut());

	newLabels.append("text")
	    .style("dominant-baseline", "central")
	    .style("text-anchor", "middle")
	    .attr("font-size", 15)
	    .attr("font-weight", "bold")
	    .text(d => d.count);

	newLabels.transition()
	    .duration(this.transitionDuration)
	    .attr("opacity", 1);

	linkLabels.select("text").text(d => d.count);

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
	if (link) {
	    let srcId = link.source.data.individualID
	    let src = (srcId === this.id) ? link.source[axis] : link.target[axis];
	    let target = (srcId === this.id) ? link.target[axis] : link.source[axis];
	    let diff = src - target;
	    return target + (diff * 0.4);
	}

	console.error("Invalid link interpolation: ", link);
	return -1; //Likely cascades errors
    }
}


