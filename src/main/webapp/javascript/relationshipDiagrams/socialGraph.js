//Social graph global API (used in individuals.jsp)
function setupSocialGraph(individualId, containerId="#socialDiagram", parser=null) {
    let focusedScale = 1.25;
    let sg = new SocialGraph(individualId, "#socialDiagram", focusedScale, parser);
    sg.applySocialData();
}

//Tree-like graph displaying social and familial relationships for a species
class SocialGraph extends ForceLayoutAbstract {
    constructor(individualID, containerId, focusedScale, parser=null) {
	super(individualID, containerId, focusedScale, parser);
    }

    //Wrapper function to gather species data from the Wildbook DB and generate a graph
    applySocialData() {
	this.parser.parseJSON(this.id, (nodes, links) => this.graphSocialData(nodes, links));
    }

    //Generate a social graph
    graphSocialData(nodes, links) {
	if (nodes.length > 0) {
	    //Create graph w/ forces
	    this.setupGraph(links, nodes);
	    this.updateGraph(links, nodes);
	}
	else this.showTable("#socialDiagram", "#communityTable");
    }
}
