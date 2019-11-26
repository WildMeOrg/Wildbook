//Social graph global API (used in individuals.jsp)
function setupSocialGraph(individualID) {
    let focusedScale = 1.25;
    let sg = new SocialGraph(individualID, focusedScale);
    sg.applySocialData();
}

//Tree-like graph displaying social and familial relationships for a species
class SocialGraph extends ForceLayoutAbstract {
    constructor(individualID, focusedScale) {
	super(individualID, focusedScale);
	
	this.nodeData = [
	    {
		"id": 0,
		"group": 0,
		"data": {
		    "name": "MUFASA",
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

	this.linkData = [
	    {"linkId": 0, "source": 0, "target": 1, "type": "maternal", "group": 0},
	    {"linkId": 1, "source": 0, "target": 3, "type": "member", "group": -1},
	    {"linkId": 2, "source": 3, "target": 4, "type": "familial", "group": 2},
	    {"linkId": 3, "source": 4, "target": 5, "type": "maternal", "group": 2},
	    {"linkId": 4, "source": 5, "target": 3, "type": "member", "group": -1},
	    {"linkId": 5, "source": 2, "target": 1, "type": "member", "group": -1},
	    {"linkId": 6, "source": 2, "target": 0, "type": "member", "group": -1}
	];
    }

    //Wrapper function to gather species data from the Wildbook DB and generate a graph
    applySocialData() {
	let query = wildbookGlobals.baseUrl + "/api/jdoql?" +
	    encodeURIComponent("SELECT FROM org.ecocean.social.Relationship " +
			       "WHERE (this.type == \"social grouping\") && " +
			       "(this.markedIndividualName1 == \"" + this.id +
			       "\" || this.markedIndividualName2 == \"" + this.id + "\")");
	d3.json(query, (error, json) => {
	    if (error) console.error(error);
	    else this.parser.parseJSON(this.id, json, (nodes, links) =>
	    			       this.graphSocialData(nodes, links));
	});	
    }

    //Generate a social graph
    graphSocialData(nodes, links) {
	if(nodes.length > 0) {
	    //Create graph w/ forces
	    this.setupGraph("#socialDiagram", links, nodes);
	    this.updateFilterButtons("#socialDiagram");
	    this.updateGraph(links, nodes);
	}
	else this.showTable("#socialDiagram", "#communityTable");
    }
}
