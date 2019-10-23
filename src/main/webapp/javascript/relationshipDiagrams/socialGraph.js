//TODO List
//Fix nodes on drag - https://bl.ocks.org/mbostock/3750558

function setupSocialGraph(individualID) {
    let focusedScale = 1.25;
    let sg = new SocialGraph(individualID, focusedScale);
    sg.applySocialData();
}

class SocialGraph extends ForceLayoutAbstract {
    constructor(individualID, focusedScale) {
	super(individualID, focusedScale);
	
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
	    {"source": 0, "target": 3, "type": "member"},
	    {"source": 3, "target": 4, "type": "familial"},
	    {"source": 4, "target": 5, "type": "maternal"},
	    {"source": 5, "target": 3, "type": "member"},
	    {"source": 2, "target": 1, "type": "member"},
	    {"source": 2, "target": 0, "type": "member"}
	];
    }

    applySocialData() {
	let query = wildbookGlobals.baseUrl + "/api/jdoql?" +
	    encodeURIComponent("SELECT FROM org.ecocean.social.Relationship " +
			       "WHERE (this.type == \"social grouping\") && " +
			       "(this.markedIndividualName1 == \"" + this.id +
			       "\" || this.markedIndividualName2 == \"" + this.id + "\")");
	d3.json(query, (error, json) => this.graphSocialData(error, json));
    }

    graphSocialData(error, json) {
	if (error) {
	    return console.error(error);
	}
	else if (json.length >= 1) {
	    console.log(this.parser.parseJSON(json));
	    
	    this.appendSvg("#socialDiagram");
	    //this.addLegend("#socialDiagram");
	    this.addTooltip("#socialDiagram");	    

	    this.calcNodeSize(this.nodes);
	    this.setNodeRadius();
	    
	    let forces = this.getForces();
	    let [linkRef, nodeRef] = this.createGraph();
	    
	    this.drawNodeOutlines(nodeRef, false);
	    this.drawNodeSymbols(nodeRef, false);
	    this.addNodeText(nodeRef, false);

	    this.enableDrag(nodeRef, forces);
	    this.applyForces(forces, linkRef, nodeRef);
	}
	else this.showTable("#communityTable", ".socialVis");
    }
}
