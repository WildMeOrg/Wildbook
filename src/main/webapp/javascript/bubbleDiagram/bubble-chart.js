//TODO - Fix table, currently refs are broken
//TODO - Consider renaming this file
//TODO - Implement or delete zoom/reset buttons

function setupOccurrenceGraph() { //TODO - look into individualID
    let focusedScale = 1.75;
    let occurrences = new OccurrenceGraph(null, focusedScale); //TODO - Remove mock
    occurrences.graphOccurenceData(false, null); //TODO: Remove mock
}

class OccurrenceGraph extends ForceLayoutAbstract {
    constructor(individualId, focusedScale) {
	super(individualId, focusedScale);

	//TODO - Remove ref, use key
	this.sliders = {"temporal": {"ref": "temporal", "prev": 0},
			"spatial": {"ref":  "spatial", "prev": 0}};
	this.filtered['occurrences'] = {};
	
	//TODO: Parse this data
	//It would be really great if some clever heirarchical representation could be used
	//to represent this - that way one format can be used for all graph DATA
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

    graphOccurenceData(error, json) {
	if (error) {
	    return console.error(json);
	}
	else { //if (json.length >= 1) { //TODO
	    this.appendSvg("#bubbleChart");
	    this.addTooltip("#bubbleChart");

	    this.getRangeSliderAttr();
	    this.updateRangeSliders();
	    
	    this.calcNodeSize(this.nodeData);
	    this.setNodeRadius();
	    
	    this.setupGraph();
	    this.updateGraph();
	}
    }

    getRangeSliderAttr() {
	let distArr = [], timeArr = []
	let focusedNode = this.nodeData.find(d => d.data.isFocused);
	this.nodeData.forEach(d => {
	    if (d.id !== focusedNode.id) {
		let dist = this.getMin(focusedNode, d, "distance");
		let time = this.getMin(focusedNode, d, "time");
		distArr.push(dist)
		timeArr.push(time);
	    }
	});

	console.log("DIST", distArr);
	console.log("TIME", timeArr);

	this.sliders.temporal.max = Math.max(...timeArr);
	this.sliders.temporal.mean = timeArr.reduce((a,b) => a + b, 0) / timeArr.length;

	this.sliders.spatial.max = Math.max(...distArr);
	this.sliders.spatial.mean = distArr.reduce((a,b) => a + b, 0) / distArr.length;
    }

    getMin(node1, node2, type) {
	let node1Sightings = node1.data.sightings;
	let node2Sightings = node2.data.sightings;
	return this.getMinBruteForce(node1Sightings, node2Sightings, type);
    }

    //TODO - Consider strip optimizations
    getMinBruteForce(node1Sightings, node2Sightings, type) {
	let val;
	let min = Number.MAX_VALUE;
	node1Sightings.forEach(node1 => {
	    node2Sightings.forEach(node2 => {
		if (type === "distance") 
		    val = this.calculateDist(node1.location, node2.location);
		else if (type === "time")
		    val = this.calculateTime(node1.datetime_ms, node2.datetime_ms)

		if (val < min) min = val;
	    });
	});
	return min;
    }
    
    calculateDist(node1Loc, node2Loc) {
	return Math.pow(Math.pow(node1Loc.lon - node2Loc.lon, 2) -
			Math.pow(node1Loc.lat - node2Loc.lat, 2), 0.5);
    }

    calculateTime(node1Time, node2Time) {
	return Math.abs(node1Time - node2Time)
    }

    updateRangeSliders() {
	Object.values(this.sliders).forEach(slider => {
	    let sliderNode = $("#" + slider.ref);
	    sliderNode.attr("max", slider.max);
	    sliderNode.attr("value", slider.mean);
	    sliderNode.change(() =>
			      this.filterByOccurrence(this, sliderNode.val(), slider.ref));
	});
    }

    filterByOccurrence(self, threshold, occType) {
	let nodeFilter, linkFilter, filterType;
	let focusedNode = self.nodeData.find(d => d.data.isFocused);
	if (occType === "spatial") {
	    nodeFilter = (d) => (self.calculateDist(focusedNode, d) >= threshold)
	    linkFilter = (d) => (self.calculateDist(focusedNode, d.source) >= threshold) &&
		(self.calculateDist(focusedNode, d.target) >= threshold)
	    filterType = (self.sliders.spatial.prev >= threshold) ? "add" : "remove";
	    self.sliders.spatial.prev = threshold;
	}
	else if (occType === "temporal") {
	     nodeFilter = (d) => (self.calculateTime(focusedNode, d) >= threshold)
	     linkFilter = (d) => (self.calculateTime(focusedNode, d.source) >= threshold) &&
		(self.calculateTime(focusedNode, d.target) >= threshold)
	    filterType = (self.sliders.temporal.prev >= threshold) ? "add" : "remove";
	    self.sliders.temporal.prev = threshold;
	}

	self.absoluteFilterGraph(nodeFilter, linkFilter, filterType);
    }
}
