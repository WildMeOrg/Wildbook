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
	
	//TODO: Parse this data
	//It would be really great if some clever heirarchical representation could be used
	//to represent this - that way one format can be used for all graph DATA
	this.nodes = [
	    {
		"id": 0,
		"group": 0,
		"data": {
		    "name": "Lion A",
		    "gender": "female",
		    "role": "alpha",
		    "isFocused": true
		}
	    },
	    {
		"id": 1,
		"group": 0,
		"data": {
		    "name": "Lion B",
		    "gender": "female"
		}
	    },
	    {
		"id": 2,
		"group": 1,
		"data": {
		    "name": "Lion C",
		    "gender": "male"
		}
	    },
	    {
		"id": 3,
		"group": 2,
		"data": {
		    "name": "Lion D",
		    "gender": ""
		}
	    },
	    {
		"id": 4,
		"group": 2,
		"data": {
		    "name": "Lion E",
		    "gender": "female"
		}
	    },
	    {
		"id": 5,
		"group": 2,
		"data": {
		    "name": "Lion F",
		    "gender": "male"
		}
	    }
	];

	this.links = [
	    {"source": 0, "target": 1, "type": "maternal"},
	    {"source": 0, "target": 2, "type": "member"},
	    {"source": 0, "target": 3, "type": "familial"},
	    {"source": 0, "target": 4, "type": "paternal"},
	    {"source": 0, "target": 5, "type": "member"},
	];	
    }

    graphOccurenceData(error, json) {
	if (error) {
	    return console.error(json);
	}
	else { //if (json.length >= 1) { //TODO
	    this.appendSvg("#bubbleChart");
	    this.addTooltip("#bubbleChart");

	    this.calcNodeSize(this.nodes);
	    this.setNodeRadius();
	    
	    let forces = this.getForces();
	    let [linkRef, nodeRef] = this.createGraph();
	    
	    let circles = this.drawNodeOutlines(nodeRef, false);
	    this.drawNodeSymbols(nodeRef, false);
	    this.addNodeText(nodeRef, false);

	    this.enableDrag(circles, forces);
	    this.applyForces(forces, linkRef, nodeRef);	    
	}
    }
}
