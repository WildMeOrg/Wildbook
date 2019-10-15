//TODO List

//Add "freeze" button to stop graph ticks

function setupSocialGraph(individualID) {
    let sg = new SocialGraph(individualID);
    sg.graphSocialData(false, [0,0]); //Dummied method
}

class SocialGraph extends GraphAbstract {
    constructor(individualID) {
	super(individualID);
	
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
		    "isFocus": true
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
	    {"source": 0, "target": 1, "type": "familial"},
	    {"source": 0, "target": 3, "type": "member"},
	    {"source": 0, "target": 5, "type": "familial"},
	    {"source": 3, "target": 4, "type": "familial"},
	    {"source": 4, "target": 5, "type": "member"},
	    {"source": 5, "target": 3, "type": "member"},
	    {"source": 2, "target": 1, "type": "member"}
	];
    }

    graphSocialData(error, json) {
	if (error) {
	    return console.error(error);
	}

	if (json.length >= 1) {
	    this.appendSvg("#socialDiagram");
	    
	    //Calculate node radius
	    this.calcNodeSize(this.nodes);
	    
	    let forces = this.getForces();
	    let [linkRef, nodeRef] = this.createGraph();
	    
	    let circles = this.drawNodeOutlines(nodeRef, false);
	    this.drawNodeSymbols(nodeRef, false);
	    this.addNodeText(nodeRef, false);

	    this.enableDrag(circles, forces);
	    this.addTooltip("#socialDiagram");

	    this.applyForces(forces, linkRef, nodeRef);
	}
    }
}
