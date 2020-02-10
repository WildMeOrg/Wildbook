/**
 * Social graph global API (used in individuals.jsp)
 * @param {individualId} [string] - Central social node id. Unless otherwise set by the parser, all nodes should connect to this
 * @param {containerId} [string] - Specifies the target HTML element by id for which the social graph will be appended. 
 * 	Defaults to "#socialDiagram". 
 * @param {parser} [obj] - The parser object used to extract node and link data.
 */
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

    /**
     * Wrapper function to gather species data from the Wildbook DB and generate a graph
     */
    applySocialData() {
	this.parser.parseJSON(this.id, (nodes, links) => this.graphSocialData(nodes, links));
    }

    /**
     * Generate a social graph
     * @param {nodes} [obj list] - A list of node objects describing MarkedIndividual data to display
     * @param {links} [obj list] - A list of link objects describing Relationship data to display
     */
    graphSocialData(nodes, links) {
	if (nodes.length > 0) {
	    //Create graph w/ forces
	    this.setupGraph(links, nodes);
	    this.updateGraph(links, nodes);
	}
	else this.showTable("#socialDiagram", "#communityTable");
    }
}
