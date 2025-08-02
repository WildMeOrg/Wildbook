/**
 * Occurence graph global API (used in individuals.jsp)
 * @param {individualId} [string] - Identifies the central node
 * @param {globals} [list] - Global variables passed in to maintain 
 *   compatibility with individuals.jsp
 * @param containerId - Html element the graph will be appended to
 * @param {parser} [JSONParser] - Optional parser specification. Defaults to null
 */	
function setupOccurrenceGraph(individualId, containerId, globals, parser=null) {
    let focusedScale = 1.75;
    let occ = new OccurrenceGraph(individualId, containerId, globals, focusedScale, parser);
    occ.applyOccurrenceData();
}


//Sparse-tree mapping co-occurrence relationships between a focused individual and its species
class OccurrenceGraph extends ForceLayoutAbstract {
    constructor(individualId, containerId, globals, focusedScale, parser=null) {
	super(individualId, containerId, globals);

	this.focusedScale = focusedScale;
	if (parser) this.parser = parser;
	else this.parser = new JSONParser(null, true, 30);

	//Expand upon graphAbstract's {this.sliders} attribute
//	this.sliders = {
//	    ...this.sliders,
//	    "temporal": {
//		"filter": this.filterByOccurrence,
//		"def": 0,
//		"precision": 1
//	    },
//	    "spatial": {
//		"filter": this.filterByOccurrence,
//		"def": 0,
//		"precision": 2
//	    }
//	};
    }

    /**
     * Wrapper function to gather species data and generate a graph
     */	   
    applyOccurrenceData() {
	this.parser.processJSON((nodes, links) => this.graphOccurrenceData(nodes, links),
			        this.id, true);
    }
    
    /**
     * Generate a co-occurrence graph
     * @param {nodes} [Node list] - A list of node objects queried from the 
     *   MarkedIndividual psql table
     * @param {links} [obj list] - A list of link objects queried from the Relationship psql table
     */	
    graphOccurrenceData(nodes, links) {
        //Clear loading icon
	$("#bubbleChart").children(".loadingIcon").empty();
	$("#bubbleChart").children(".loadingIcon").remove();

	//Create graph w/ forces
	if (nodes.length >= 1) { 
	    this.setupGraph(links, nodes);
	    this.updateGraph(links, nodes);
	}
	else this.showTable("#cooccurrenceDiagram", "#cooccurrenceTable");
    }

    /**
     * Update viable occurrence data for each link, prior to updating the graph
     * @param {linkData} [obj list] - A list of link objects queried from the 
     *   Relationship psql table
     * @param {nodeData} [Node list] - A list of Node objects queried from the 
     *   MarkedIndividual psql table
     */	    
    updateGraph(linkData=this.linkData, nodeData=this.nodeData) {
	//Update link data
	this.updateLinkThreshCount(this.focusedNode);	
	super.updateGraph(linkData, nodeData);
    }

    /**
     * Updates the maximum values for the spatial/temporal sliders
     */	
//    updateRangeSliderAttr() {
//	super.updateRangeSliderAttr();
//
//	let [distArr, timeArr] = this.analyzeNodeData(this.focusedNode);
//	this.calcSliderMax(this.sliders.temporal, timeArr);
//	this.calcSliderMax(this.sliders.spatial, distArr);
//    }

    /**
     * Calculates the maximum threshold for a given slider (supporting decimal thresholds)
     * @param {slider} [obj] - The contextual slider being updated
     * @param {thresholdArr} [list] - A list of threshold values
     */
//    calcSliderMax(slider, thresholdArr) {
//	let precision = slider.precision;
//	let rawMin = Math.pow(0.1, precision-1);
//	let buffer = Math.pow(0.1, precision+0.5); //Fixes a jquery bug where decimal sliders cannot reach their max value
//	let rawMax = Math.max(...thresholdArr, rawMin) + buffer;
//	slider.max = this.ceilToFixed(rawMax, precision+1);
//    }

    /**
     * Rounds upwards for a specified decimal precision
     * @param {value} [float] - A float value to be rounded
     * @param {precision} [float] - The number of decimals to include
     * @return {roundedValue} [float] - A rounded version of value
     */
//    ceilToFixed(value, precision){
//	var ceil = Math.pow(10, precision);
//	return parseFloat((Math.round(value * ceil) / ceil).toFixed(precision));
//    }

    /**
     * Calculate the minimum spatial/temporal differences between all nodes and the {focusedNode}
     * @param {focusedNode} [Node] - Central coOccurrence node
     * @return {distArr, timeArr} [2D list] - The spatial/temporal minimum differences 
     *   for each node
     */	
//    analyzeNodeData(focusedNode) {
//	let distArr = [], timeArr = []
//	this.nodeData.forEach(d => {
//	    if (d.id !== focusedNode.id) {
//		let [dist, time] = this.getNodeMin(focusedNode, d);		
//		distArr.push(dist)
//		timeArr.push(time);
//	    }
//	});
//
//	return [distArr, timeArr]
//    }

    /**
     * Returns the minimum spatial/temporal difference between two nodes as specified
     * @param {node1} [Node] - The first node being compared
     * @param {node2} [Node] - The second node being compared
     * @param {type} [String] - Determines whether the minimum spatial or temporal difference 
     *   should be returned
     * @return {min} [int] - The minimum distance or time value
     */
//    getNodeMinType(node1, node2, type) {
//	let [dist, time] = this.getNodeMin(node1, node2);
//	if (type == "spatial") return dist;
//	else if (type == "temporal") return time;
//    }

    /**
     * Finds the minimum spatial/temporal differences between two nodes, 
     *   and updates co-occurrence data
     * @param {node1} [Node] - The first node being compared
     * @param {node2} [Node] - The second node being compared
     * @return {minDist, minTime} [list] - The minimum distance and time values
     */
//    getNodeMin(node1, node2) {
//	let sights1 = this.getSightingsData(node1.data.sightings);
//	let sights2 = this.getSightingsData(node2.data.sightings);
//
//	let [min, minCoordPair] = this.getNodeMinKDTree(sights1, sights2,
//							["lat", "lon", "time"],
//							this.calculateSightingsDiff);
//
//	try {
//	    let minDist = this.calculateDist(minCoordPair[0], minCoordPair[1]);
//	    let minTime = this.calculateTime(minCoordPair[0].time, minCoordPair[1].time);
//
//	    return [minDist, minTime];
//	}
//	catch(err) {
//	    return [Infinity, Infinity]; //Arbitrarily large min values
//	}
//    }

    /**
     * Generates a list of relevant spatial and temporal sightings data objects
     * @param {sightings} [list of objs] - Unfiltered list of sightings data
     * @return {sightingsData} [list of objs] - Returns a list of important spatial/temporal 
     *   sightings data
     */
//    getSightingsData(sightings) {
//	return sightings.map(d => {
//	    let loc = d.location;
//	    let time = d.time.datetime;
//	    return {"lat": loc.lat, "lon": loc.lon, "time": time};
//	});
//    }

    /**
     * Calculates the minimum difference between two arrays in n^2 time
     * @param {arr1} [list] - The first array of numeric values
     * @param {arr2} [list] - The second array of numeric values
     * @param {diffFunc} [lambda] - Calculates the difference between two elements
     * @return {min} [number] - Returns the minimum difference from {arr1} and {arr2}
     */
//    getNodeMinBruteForce(arr1, arr2, diffFunc) {
//	let min = Number.MAX_SAFE_INTEGER;
//	arr1.forEach(el1 => {
//	    arr2.forEach(el2 => {
//		let diff = diffFunc(el1, el2);
//		if (diff < min) min = diff;
//	    });
//	});
//	return min;
//    }

    /**
     * Calculates the minimu difference between two arrays in nlog(n) time. May be slower than
     *   getNodeMinBruteForce() in very small or very sparse node populations
     * @param {arr1} [list] - The first array of numeric values
     * @param {arr2} [list] - The second array of numeric values
     * @param {dimensions} [list of Strings] - Describes the dimensions key 
     *   mappings for {arr1}/{arr2}
     * @param {diffFunc} [lambda] - Calculates the difference between two elements
     * @return {min} [number] - Returns the minimum difference from {arr1} and {arr2}
     */
//    getNodeMinKDTree(arr1, arr2, dimensions, diffFunc) {
//	let tree = new kdTree(arr1, diffFunc, dimensions);
//
//	let minCoordPair = null;
//	let min = Number.MAX_SAFE_INTEGER;
//	arr2.forEach(point => {
//	    let [coords, dist] = tree.nearest(point, 1)[0];
//	    if (dist < min) {
//		minCoordPair = [point, coords];
//		min = dist;
//	    }
//	});
//
//	return [min, minCoordPair];
//    }

    /**
     * Update the occurrence data for each link to satisfy the current spatial/temporal thresholds
     * @param {focusedNode} [Node] - The central occurrence node
     */
    updateLinkThreshCount(focusedNode) {
	let focusedId = focusedNode.id;
//	let spatialThresh = parseFloat($("#spatial").val());
//	let temporalThresh = parseFloat($("#temporal").val());

	this.linkData.forEach(link => {
	    let targetId = link.target.id || link.target;
	    let sourceId = link.source.id || link.source;
	    let linkId = (targetId === focusedId) ? sourceId : targetId;
	    
	    let node = this.nodeData.find(node => node.id === linkId);
	    if (node) {
//		let threshEncounters = this.getLinkThreshEncounters(focusedNode, node,
//								    spatialThresh, temporalThresh);
	    let threshEncounters = node.data.sightings;
	    link.validEncounters = threshEncounters;
		link.count = threshEncounters.length;
		link.explicitOccurrence = node.data.sightings.explicit;
	    }
	});
    }

    /**
     * Get the list of occurrences which satisfy the given spatial/temporal thresholds 
     *   for a given node
     * @param {node1} [Node] - The first node being compared
     * @param {node2} [Node] - The second node being compared
     * @param {spatialThresh} [int] - The maximum spatial difference allowed
     * @param {temporalThresh} [int] - The maximum temporal difference allowed
     * @return {validEncounters} [list] - All occurrences which satisfy the given constraints 
     */
//    getLinkThreshEncounters(node1, node2, spatialThresh, temporalThresh) {
//	let node1Sightings = node1.data.sightings;
//	let node2Sightings = node2.data.sightings;
//	
//	let validEncounters = [], idxSet = new Set();
//	node1Sightings.forEach(sight1 => {
//	    node2Sightings.forEach((sight2, idx) => {
//		let spatialVal = this.calculateDist(sight1.location, sight2.location);
//		let temporalVal = this.calculateTime(sight1.time.datetime, sight2.time.datetime);
//		
//		if (spatialVal <= spatialThresh && temporalVal <= temporalThresh &&
//		    !idxSet.has(idx)) {
//		    idxSet.add(idx);
//		    validEncounters.push(sight2);
//		}
//	    });
//	});
//
//	return validEncounters;
//    }

    /**
     * Calculates an unweighted distance for the sightings space between two given nodes
     * @param {node1} [obj] - Object with lat, lon, and time properties, describing the 
     *   position of node1 within the sightings space
     * @param {node2} [obj] - Object with lat, lon, and time properties, describing the 
     *   position of node2 within the sightings space
     * @return {dist} [int] - Describes the distance between the two node positions. Defaults to -1
     */
//    calculateSightingsDiff(node1, node2) {
//	return Math.pow(node1.lat - node2.lat, 2) + Math.pow(node1.lon - node2.lon, 2) +
//	    Math.pow(node1.time - node2.time, 2);
//    }
    
    /**
     * Calculate the spatial difference between two node sighting locations
     * @param {node1Loc} [obj] - Object with lat and lon properties, describing the 
     *   position of node1
     * @param {node2Loc} [obj] - Object with lat and lon properties, describing the 
     *   position of node2
     * @return {dist} [int] - Describes the distance between the two node positions. Defaults to -1
     */
//    calculateDist(node1Loc, node2Loc) {
//	if (node1Loc && node2Loc) {
//	    return Math.pow(Math.pow(node1Loc.lon - node2Loc.lon, 2) +
//			    Math.pow(node1Loc.lat - node2Loc.lat, 2), 0.5);
//	}
//	return -1;
//    }

    /**
     * Calculate the temporal difference between two node sightings
     * @param {node1Time} [int] - The time at which node1 was observed
     * @param {node2Time} [int] - The time at which node2 was observed
     * @return {time} [int] - Describes the difference between the two node times
     */
//    calculateTime(node1Time, node2Time) {
//	if (typeof node1Time === "number" && typeof node2Time === "number") {
//	    return Math.abs(node1Time - node2Time)
//	}
//	return -1;
//    }
        
    /**
     * Filter nodes by spatial/temporal differences, displaying those less than the set threshold
     * @param {self} [coOccurrenceGraph] - 'this' context of the calling object
     * @param {thresh} [int] - The maximum value for the given data type
     * @param {occType} [String] - Determines whether links should be filtered by 
     *   "temporal" or "spatial" considerations
     */
//    filterByOccurrence(self, thresh, occType) {
//	//Update slider label value
//	$("#" + occType + "Val").text(thresh)
//	
//	let focusedNode = self.nodeData.find(d => d.data.isFocused);
//	let nodeFilter = (d) => (self.getNodeMinType(focusedNode, d, occType) <= thresh || d.data.sightings.explicit)
//	let linkFilter = (d) => ((self.getNodeMinType(focusedNode, d.source, occType) <= thresh) && (self.getNodeMinType(focusedNode, d.target, occType) <= thresh) || d.explicitOccurrence)
//
//	let validFilters = self.validFilters.concat([occType]);
//	self.absoluteFilterGraph(nodeFilter, linkFilter, occType, validFilters);
//    }

    /**
     * Updates link occurrence counts and visibility
     * @param {linkData} [obj list] - A list of link objects queried from the 
     *   Relationship psql table
     */
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
	    .attr("r", d => {
		let length = Math.max(d.count.toString().length - 1, 0);
		let scale = 1 + (0.5 * length);
		return 9 * scale; //8 px default
	    })
	    .style("fill", "white")
	    .on("mouseover", d => this.handleMouseOver(d, "link"))				
	    .on("mouseout", () => this.handleMouseOut());

	newLabels.append("text")
	    .style("dominant-baseline", "central")
	    .style("text-anchor", "middle")
	    .attr("font-size", 15)
	    .attr("font-weight", "normal")
	    .text(d => (d.explicitOccurrence) ? `${d.count}*` : d.count);

	newLabels.transition()
	    .duration(this.transitionDuration)
	    .attr("opacity", 1);

	linkLabels.select("text")
	    .text(d => (d.explicitOccurrence) ? `${d.count}*` : d.count);

	this.linkLabels = newLabels.merge(linkLabels);
	
	//Add link edges
	super.updateLinks(linkData);
    }

    /**
     * Updates the graph's physics each tick
     * @param {self} [coOccurrenceGraph] - 'this' context of the calling object
     */
    ticked(self) {
	super.ticked(self);
	self.linkLabels.attr("transform", d => "translate(" + this.linearInterp(d, "x") +
			     "," + this.linearInterp(d, "y") + ")");
    }

    /**
     * Calculates a point 40 percent between a given link's target and source nodes
     * @param {link} [obj] - Link context for the interpolation
     * @param {axis} [int] - Determines whether the x (0) or y (1) axis should be interpolated
     * @return {pos} [int] - Describes the interpolated position's offset
     */
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

    /**
     * No-op method purposed to disable graphAbstract's focusNode event
    */
    focusNode(node) {}
}

