//Occurence graph global API (used in individuals.jsp)
function setupOccurrenceGraph(individualID) { //TODO - look into individualID
    let focusedScale = 1.75;
    let occurrences = new OccurrenceGraph(individualID, focusedScale); //TODO - Remove mock
    occurrences.graphOccurenceData(false, ['a', 'b']); //TODO: Remove mock
}

//Sparse-tree mapping co-occurrence relationships between a focused individual and its species
class OccurrenceGraph extends ForceLayoutAbstract {
    constructor(individualId, focusedScale) {
	super(individualId, focusedScale);

	//TODO - Remove ref, use key
	this.sliders = {"temporal": {"ref": "temporal"},
			"spatial": {"ref": "spatial"}};
	
	//TODO: Parse this data
	this.nodeData = [
	    {
		"id": 0,
		"group": 0,
		"data": {
		    "name": "Lion A",
		    "gender": "female",
		    "sightings": [
			{
			    "datetime_ms": 2000,
			    "location": {"lat": 1, "lon": 0}
			}
		    ],
		    "role": "alpha",
		    "isFocused": true
		}
	    },
	    {
		"id": 1,
		"group": 0,
		"data": {
		    "name": "Lion B",
		    "gender": "female",
		    "sightings": [
			{
			    "datetime_ms": 2200,
			    "location": {"lat": 2, "lon": 1}
			}
		    ]
		}
	    },
	    {
		"id": 2,
		"group": 1,
		"data": {
		    "name": "Lion C",
		    "gender": "male",
		    "sightings": [
			{
			    "datetime_ms": 1900,
			    "location": {"lat": 2, "lon": 0}
			}
		    ]
		}
	    },
	    {
		"id": 3,
		"group": 0,
		"data": {
		    "name": "Lion D",
		    "gender": "",
		    "sightings": [
			{
			    "datetime_ms": 2100,
			    "location": {"lat": 1, "lon": 1}
			}
		    ],
		}
	    },
	    {
		"id": 4,
		"group": 0,
		"data": {
		    "name": "Lion E",
		    "gender": "female",
		    "sightings": [
			{
			    "datetime_ms": 1600,
			    "location": {"lat": 0, "lon": 0}
			}
		    ],
		}
	    },
	    {
		"id": 5,
		"group": 2,
		"data": {
		    "name": "Lion F",
		    "gender": "male",
		    "sightings": [
			{
			    "datetime_ms": 2500,
			    "location": {"lat": -1, "lon": 0}
			}
		    ],
		}
	    }
	];

	this.linkData = [
	    {"linkId": 0, "source": 0, "target": 1, "type": "maternal", "group": 0},
	    {"linkId": 1, "source": 0, "target": 2, "type": "member", "group": -1},
	    {"linkId": 2, "source": 0, "target": 3, "type": "maternal", "group": 0},
	    {"linkId": 3, "source": 0, "target": 4, "type": "maternal", "group": 0},
	    {"linkId": 4, "source": 0, "target": 5, "type": "member", "group": -1}
	];	
    }

    //Generate a co-occurrence graph
    graphOccurenceData(error, json) {
	if (error) return console.error(json);
	else if (json.length >= 1) { 
	    //Create graph w/ forces
	    this.setupGraph("#bubbleChart", this.linkData, this.nodeData);
	    this.updateGraph();
	}
	else this.showTable("#cooccurrenceDiagram", "#cooccurrenceTable");
    }

    //Perform all auxiliary functions necessary prior to graphing
    setupGraph(containerId, linkData, nodeData) {
	super.setupGraph(containerId, linkData, nodeData);

	//Create range sliders
	this.getRangeSliderAttr();
	this.updateRangeSliders();

	//Initialize filter button functionalities
	this.updateFilterButtons("#bubbleChart");
    }


    //Calculate the maximum and average node differences for the spatial/temporal sliders
    getRangeSliderAttr() {
	let distArr = [], timeArr = []
	this.nodeData.forEach(d => {
	    if (d.id !== this.focusedNode.id) {
		let dist = this.getNodeMin(this.focusedNode, d, "spatial");
		let time = this.getNodeMin(this.focusedNode, d, "temporal");
		distArr.push(dist)
		timeArr.push(time);
	    }
	});

	this.sliders.temporal.max = Math.ceil(Math.max(...timeArr));
	this.sliders.temporal.mean = timeArr.reduce((a,b) => a + b, 0) / timeArr.length;

	this.sliders.spatial.max = Math.max(...distArr);
	this.sliders.spatial.mean = distArr.reduce((a,b) => a + b, 0) / distArr.length;
    }

    //Wrapper for finding the minimum spatial/temporal differences between two nodes
    getNodeMin(node1, node2, type) {
	let node1Sightings = node1.data.sightings;
	let node2Sightings = node2.data.sightings;
	return this.getNodeMinBruteForce(node1Sightings, node2Sightings, type);
    }

    //TODO - Consider strip optimizations
    //Find the minimum spatial/temporal difference between two node sightings
    getNodeMinBruteForce(node1Sightings, node2Sightings, type) {
	let val, min = Number.MAX_VALUE;
	node1Sightings.forEach(node1 => {
	    node2Sightings.forEach(node2 => {
		if (type === "spatial")
		    val = this.calculateDist(node1.location, node2.location);
		else if (type === "temporal")
		    val = this.calculateTime(node1.datetime_ms, node2.datetime_ms);

		if (val < min) min = val;
	    });
	});
	return min;
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
	
	self.absoluteFilterGraph(nodeFilter, linkFilter, occType);
    }

    //TODO - Add support for saved local family filters
    //Apply absolute filters (i.e. thresholding)
    absoluteFilterGraph(nodeFilter, linkFilter, type) {
	//Remove any nodes who no longer qualify to be filtered
	this.svg.selectAll(".node").filter(d => nodeFilter(d) && d.filtered === type)
	    .remove();
	
	//Mark nodes concerning whether they should be filtered
	this.nodeData.forEach(d => {
	    if (nodeFilter(d) && d.filtered === type) d.filtered = false;
	    else if (!nodeFilter(d) && (!d.filtered || d.filtered === "family_filter"))
		d.filtered = type;
	});
		
	//Identify link data which should be rendered
	this.prevLinkData = this.linkData.filter(d => !d.source.filtered && !d.target.filtered);

	//Update the graph with filtered data
	this.updateGraph(this.prevLinkData, this.nodeData);
    }

    //Swap node focus w/ contextual co-occurence slider updates
    focusNode(d) {
	if (super.focusNode(d)) {
	    //Re-calculate slider values
	    this.getRangeSliderAttr();

	    //Reset sliders
	    this.resetSliders();
	}
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
}


