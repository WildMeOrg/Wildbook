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

	this.sliders = [
	    {
		"name": "Temporal Slider",
		"type": "temporal"
	    },
	    {
		"name": "Spatial Slider",
		"type":  "spatial"
	    }
	];
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
	    this.appendRangeSliders();
	    
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
		let dist = this.calculateDist(focusedNode, d);
		let time = this.calculateTime(focusedNode, d);
		distArr.push(dist)
		timeArr.push(time);
	    }
	});

	this.sliders[0].max = Math.max(...timeArr);
	this.sliders[0].middle = timeArr.reduce((a,b) => a + b, 0) / timeArr.length;

	this.sliders[1].max = Math.max(...distArr);
	this.sliders[1].middle = distArr.reduce((a,b) => a + b, 0) / distArr.length;
    }

    calculateDist(node1, node2) {
	let node1Dist = Math.pow(Math.pow(node1.data.sightings.lat, 2) +
				 Math.pow(node1.data.sightings.lon, 2), 0.5)
	let node2Dist = Math.pow(Math.pow(node2.data.sightings.lat, 2) +
				 Math.pow(node2.data.sightings.lon, 2), 0.5)
	return Math.abs(node1Dist - node2Dist);
    }

    calculateTime(node1, node2) {
	return Math.abs(node1.data.sightings.datetime_ms - node2.data.sightings.datetime_ms)
    }

    appendRangeSliders() {
	let targetNode = $("#cooccurrenceSliders");
	this.sliders.forEach(sliderObj => {
		let sliderStr = "<label for='" + sliderObj.type + "'>" + sliderObj.name +
		"</label> <div class='sliderWrapper'>" +
		"<input type='range' min='1' max='" + sliderObj.max +
		"' value='" + sliderObj.middle +
		"' class='slider' id='" + sliderObj.type +
		"' onchange='this.filterByOccurrence(this.value, " + sliderObj.type + ")'>" +
		"</div>";
	    console.log(sliderStr);
	    targetNode.append(sliderStr);
	});
    }

    filterByOccurrence(threshold, occType) {
	let focusedNode = this.nodeData.find(d => d.data.isFocused);
	if (occType === "spatial") {
	    let nodeFilter = (d) => (this.calculateDist(focusedNode, d) < threshold)
	    let linkFilter = (d) => (this.calculateDist(focusedNode, d.source) < threshold) &&
		(this.calculateDist(focusedNode, d.target) < threshold)
	}
	else if (occType === "temporal") {
	    let nodeFilter = (d) => (this.calculateTime(focusedNode, d) < threshold)
	    let linkFilter = (d) => (this.calculateTime(focusedNode, d.source) < threshold) &&
		(this.calculateTime(focusedNode, d.target) < threshold)
	}

	this.filterGraph(occType, nodeFilter, linkFilter, 'occurrences');
    }
}
