/**
 * Social graph global API (used in individuals.jsp)
 * @param {individualId} [string] - Central social node id. Unless otherwise set by the parser, all nodes should connect to this
 * @param {containerId} [string] - Specifies the target HTML element by id for which the social graph will be appended. 
 * 	Defaults to "#socialDiagram". 
 * @param {parser} [obj] - The parser object used to extract node and link data.
 */

function setupSocialGraph(individualId, containerId="#socialDiagram", globals, parser=null) {
    let focusedScale = 1.25;
    let sg = new SocialGraph(individualId, "#familyChart", globals, focusedScale, parser);
    sg.applySocialData();
}

//Tree-like graph displaying social and familial relationships for a species
class SocialGraph extends ForceLayoutAbstract {
    constructor(individualID, containerId, globals, focusedScale, parser=null) {
	super(individualID, containerId);

	this.focusedScale = focusedScale;
	
	if (parser) this.parser = parser;
	else this.parser = new JSONParser(globals);

	//Expand upon graphAbstract's {this.sliders} attribute
	this.sliders = {...this.sliders, "nodeDist": {"filter": this.filterByGeodesic}}
    }

    /**
     * Wrapper function to gather species data from the Wildbook DB and generate a graph
     */
    applySocialData() {
	this.parser.parseJSON((nodes, links) => this.graphSocialData(nodes, links), this.id);
    }

    /**
     * Generate a social graph
     * @param {nodes} [Node list] - A list of Node objects queried from the MarkedIndividual psql table
     * @param {links} [obj list] - A list of link objects queried from the Relationship psql table
     */
    graphSocialData(nodes, links) {
	console.log("LINKS", links);
	console.log("NODES", nodes);
	
	//Create graph w/ forces
	if (nodes.length > 0) {
	    this.setupGraph(links, nodes);
	    this.updateGraph(links, nodes);
	}
	else this.showTable("#socialDiagram", "#communityTable");
    }

    updateRangeSliderAttr() {
	super.updateRangeSliderAttr();

	let maxDepth = 0;
	this.nodeData.forEach(node => {
	    if (node.depth > maxDepth) maxDepth = node.depth;
	});
	this.sliders.nodeDist.max = maxDepth;
    }
    
    filterByGeodesic(self, thresh, occType) {
	//Update slider label value
	let containerRef = $(self.containerId).parent();
	containerRef.find("#nodeDistVal").text(thresh)
	
	let nodeFilter = (d) => (d.depth <= thresh)
	let linkFilter = (d) => (d.source.depth <= thresh) &&
	    (d.target.depth <= thresh)
	let validFilters = self.validFilters.concat([occType]);
	self.absoluteFilterGraph(nodeFilter, linkFilter, occType, validFilters);
    }

    focusNode(node) {
	this.updateNodeDepths(node, this.linkData, this.nodeData);
	super.focusNode(node);
    }

    //TODO - Modularize w/ jsonParser
    updateNodeDepths(rootNode, links, nodes) {
	let nodeDict = listToDict(nodes, "id");
	let relationships = this.mapRelationships(links);

	let seenNodes = new Set();
	let relationStack = [{"id": rootNode.id, "depth": 0}];
	while (relationStack.length > 0) {
	    let node = relationStack.pop();
	    nodeDict[node.id].depth = node.depth;
	    seenNodes.add(node.id);

	    if (relationships[node.id]) {
		relationships[node.id].forEach(id => {
		    if (!seenNodes.has(id)) {
			relationStack.push({"id": id, "depth": node.depth + 1});
		    }
		});
		delete relationships[node.id];
	    }
	}

	this.nodeData = Object.values(nodeDict);
	this.updateRangeSliderAttr();
	this.updateRangeSliders();
    }

    mapRelationships(links) {
	let relationships = {};
	links.forEach(l => {
	    let sourceId = l.source.id;
	    let targetId = l.target.id;
	    if (!relationships[sourceId]) relationships[sourceId] = [targetId];
	    relationships[sourceId].push(targetId);

	    if (!relationships[targetId]) relationships[targetId] = [sourceId];
	    relationships[targetId].push(sourceId);
	});
	return relationships;
    }
}

function listToDict(array, keyField) {
    return array.reduce((obj, item) => {
	obj[item[keyField]] = item
	return obj
    }, {})
}
