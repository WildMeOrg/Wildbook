//Helper class pruposed to represent discrete MarkedIndividual data
class Node {
    constructor(key, mData, iId) {
	this.id = mData.id;
	this.group = mData.group;
	this.depth = mData.depth;

	let name = mData.displayName;
	name = name.substring(name.indexOf(" ") + 1);

	this.data = {
	    "name": name,
	    "gender": this.getGender(mData),
	    "genus": mData.genus,
	    "individualID": key,
	    "firstSighting": mData.dateFirstIdentified,
	    "latestSighting": mData.dateTimeLatestSighting,
	    "numberEncounters": mData.numberEncounters,
	    "timeOfBirth": mData.timeOfBirth,
	    "timeOfDeath": mData.timeOfDeath,
	    "isDead": this.getIsDead(mData),
	    "isFocused": this.getIsFocused(key, iId),
	    "lastKnownLifeStage": this.getLifeStage(mData),
	    "encounters": mData.encounters
	}
    }

    /**
     * Determine the gender of the current node. Assumes consistency of gender
     * @param {mData} [obj] - The data attribute of the current MarkedIndividual
     * @return {gender} [string] - Gender of the node
     */
    getGender(mData) {
	if (mData.sex) return mData.sex;
	else if (mData.encounters.length > 0) {
	    mData.encounters.forEach(enc => {
		if (enc.sex && enc.sex != "unknown") return enc.sex;
	    });
	}

	return "unknown";
    }

    /**
     * Determine whether the current node is dead
     * @param {mData} [obj] - The data attribute of the current MarkedIndividual
     * @return {isDead} [string] - Whether the node data is dead
     */
    getIsDead(mData) {
	return (mData.timeOfDeath > 0) ? true : false;
    }

    /**
     * Determine whether the current node is focused
     * @param {key} [string] - The id of the current MarkedIndividual
     * @param {iId} [string] - The id of the central node
     * @return {isFocused} [string] - Whether the node data is focused
     */
    getIsFocused(key, iId) {
	return (key === iId);
    }

    /**
     * Determine the most recent life stage of the given node
     * @param {mData} [obj] - The data attribute of the current MarkedIndividual
     * @return {lifeStage} [string] - The current life stage of the contextual node.
     *   Defaults to null if missing data
     */
    getLifeStage(mData) {
	if (mData.encounters.length > 0) {
	    switch (mData.encounters[0].lifeStage) {
	        case 'A':
		    return "Adult";
	        case 'C':
		    return "Child";
	        default:
		    return mData.encounters[0].lifeStage;
	    }
	}
	return null;
    }
}

class JSONQuerier {
    constructor(globals, localFiles=false) {
	this.globals = globals;
	this.localFiles = localFiles;
    }

    /**
     * Static function used to populate the {nodeData} and {relationshipData} fields prior to
     * generating graphs s.t. data is guaranteed to only be queried once
     * @param {iId} [String] - The id of the central node
     * @param {genus} [String] - The genus of the central node
     * @param {callbacks} [List] - Graphing functions to call
     * @param {diagramIds} [List] - Diagrams to appends graphs to
     */
    async preFetchData(iId, genus, epithet, callbacks, diagramIds, parsers=[]) {
	await this.queryNodeData(genus, epithet);
	await this.queryRelationshipData(genus);
	await this.queryOccurrences(genus);
	
	//Graph data
	for (let i = 0; i < callbacks.length; i ++) {
	    let call = callbacks[i];
	    if (parsers[i]) call(iId, diagramIds[i], this.globals, parsers[i]);
	    else call(iId, diagramIds[i], this.globals);
	}
    }

    /**
     * Query wrapper for the storage of MarkedIndividual data
     * @param {genus} [String] - The genus of the central node being graphed
     * @return {queryData} [array] - All MarkedIndividual data in the Wildbook DB
     */
    queryNodeData(genus, epithet) {
	let query;
	if (!this.localFiles) {
	    let hostname = window.location.host;

	    //Localhost compatability
	    if (hostname.includes("localhost") && !hostname.includes("wildbook"))
		hostname += "/wildbook";

	    query = wildbookGlobals.baseUrl  + "/encounters/socialJson.jsp?";
	    if (genus) query += "genus=" + genus + "&";
	    if (epithet) query += "specificEpithet=" + epithet + "&";
	}
	else query = "./MarkedIndividual.json";
	return this.queryData("nodeData", query, this.storeQueryAsDict);
    }

    /**
     * Query wrapper for the storage of Relationship data
     * @param {genus} [String] - The genus of the central node being graphed
     * @returns {queryData} [array] - All Relationship data in the Wildbook DB
     */
    queryRelationshipData(genus) {
	let query;
	if (!this.localFiles) {
	    let hostname = window.location.host;

	    //Localhost compatability
	    if (hostname.includes("localhost") && !hostname.includes("wildbook"))
		hostname += "/wildbook"

	    query = wildbookGlobals.baseUrl + "/encounters/relationshipJSON.jsp?"
	    if (genus) query += "genus=" + genus;
	}
	else query = "./Relationship.json";
	return this.queryData("relationshipData", query);
    }

    /**
     * Query wrapper for the storage of Occurrence data
     * @param {genus} [String] - The genus of the central node being graphed
     * @returns {queryData} [array] - All Relationship data in the Wildbook DB
     */
    queryOccurrences(genus) {
	let hostname = window.location.host;
	
	//Localhost compatability
	if (hostname.includes("localhost") && !hostname.includes("wildbook"))
	    hostname += "/wildbook"

	let query = wildbookGlobals.baseUrl+'/encounters/occurrenceGraphJson.jsp?genus='+genus;
	return this.queryData("occurrenceData", query);
    }

    /**
     * Retrieve JSON data from the Wildbook DB
     * @param {type} [string] - The static attribute key used to store the queried data
     * @param {query} [string] - Determines the query ran
     * @param {callback} [function] - If not null, passes query data into the specified {callback} 
     *   function for further processing. Defaults to null
     */
    queryData(type, query, callback=null) {
	return new Promise((resolve, reject) => {
	    if (!JSONParser[type]) { //Memoize the result
		d3.json(query, (error, json) => {
		    if (error) reject(error);
		    else if (callback) callback(json, type, resolve);
		    else {
			JSONParser[type] = json;
			resolve(); //Handle the encapsulating promise
		    }
		});
	    }
	    else resolve(); //Handle the encapsulating promise
	});
    }

    /**
     * Convert JSON query result from an array to a dictionary
     * @param {json} [array] - JSON data to be converted
     * @param {type} [string] - The static attribute key used to store the queried data
     * @param {resolve} [function] - Resolves the queryData promise
     */
    storeQueryAsDict(json, type, resolve) {
	if (json.length >= 1) {
	    JSONParser[type] = {};
	    json.forEach(el => {
		if (!JSONParser[type][el.individualID]) JSONParser[type][el.individualID] = el;
		else { //Handle multiple elements mapping to the same key
		    if (!Array.isArray(JSONParser[type][el.individualID])) {
			JSONParser[type][el.individualID] = [JSONParser[type][el.individualID]];
		    }
		    JSONParser[type][el.individualID].push(el);
		}
	    });
	}
	else console.error("No " + type + " JSON data found");
	resolve(); //Handle the encapsulating promise
    }
}

//Helper class purposed to parse JSON node/link data for social and coOccurrence graphs
class JSONParser {
    /**
     * Creates a JSONParse instance
     * @param {globals} [object] - Global data from Wildbook .jsp pages
     * @param {selectedNodes} [string|iterable] - Nodes included when generating JSON data.
     *   When null, all nodes are included. Defaults to null.
     * @param {disjointNodes} [boolean] - Whether nodes disconnected from the central node (iId) should be included when
     *   generating JSON data. Defaults to false
     * @param {maxNumNodes} [int] - Determines the total number of nodes to graph
     * @param {localFiles} [boolean] - Whether requests should be made to local MarkedIndividual/Relationship data
     */
    constructor(selectedNodes=null, disjointNodes=false, maxNumNodes=-1) {
	//Keep track of a unique id for each node and link
	this.nodeId = 0;
	this.linkId = 0;

	//Generate a set of selected nodes (if provided) to be used as a mask
	if (selectedNodes) {
	    if (typeof selectedNodes === "string")
		selectedNodes = selectedNodes.substr(1, selectedNodes.length - 2).split(", ");
	    this.selectedNodes = new Set(selectedNodes);
	}

	this.disjointNodes = disjointNodes;
	this.maxNumNodes = maxNumNodes;
    }

    /**
     * Transform queried MarkedIndividual and Relationship data to graphable node and link objects
     * @param {graphCallback} [function] - Handles generated node and link data arrays
     * @param {iId} [string] - The id of the central node. Defaults to null
     * @param {isCoOccurrence} [boolean] - Determines whether node/link data should feature 
     *   additional coOccurrence modifications
     */
    processJSON(graphCallback, iId, isCoOccurrence) {
	let [nodeDict, nodeList] = this.parseNodes(iId);
	let [linkDict, linkList] = this.parseLinks(nodeDict);

	if (isCoOccurrence) { //Extract temporal and latitude/longitude encounter data
	    [nodeList, linkList] = this.modifyOccurrenceData(iId, nodeDict, linkList);
	}
	graphCallback(nodeList, linkList);
    }

    /**
     * Extract node data from the MarkedIndividual and Relationship query data
     * @param {iId} [string] - The id of the central node
     * @return {nodes} [obj] - Extracted data relevant to graphing nodes
     */
    parseNodes(iId) {
	//Assign unique ids and groupings to the queried node data
	let graphNodes = this.processNodeData(iId);

	//Extract meaningful node data
	let nodes = {};
	for (let [key, data] of Object.entries(graphNodes)) {
	    //Add node to list if disjoint nodes are allowed, or if a link exists to the central node {iId}
	    if (this.disjointNodes || data.iIdLinked) {
		nodes[key] = new Node(key, data, iId);
	    }
	}

	return [nodes, Object.values(nodes)];
    }

    /**
     * Assign unique ids and label connected groups for all nodes
     * @param {iId} [string] - The id of the central node
     * @return {graphNodes} [obj list] - List of raw node data to be parsed and graphed
     */
    processNodeData(iId) {
	//Generate a two way mapping of all node relationships
	let nodes = this.getNodeSelection();
	let relationships = this.mapRelationships(nodes);
	let [graphNodes, groupNum] = this.traverseRelationshipTree(iId, nodes, relationships);

	//Ensure iId is in graphNodes
	if (iId && !graphNodes[iId]) graphNodes[iId] = this.updateNodeData(nodes[iId], ++groupNum, this.getNodeId(), 0, true);
	
	//Update id and group attributes for all disconnected nodes
	let numNodes = Object.keys(graphNodes).length;
	for (name in nodes) {
	    //Exit loop early if max number of nodes have been found
	    if (this.maxNumNodes > 0 && numNodes >= this.maxNumNodes) return graphNodes;

	    if (!graphNodes[name]) {
		graphNodes[name] = this.updateNodeData(nodes[name], ++groupNum, this.getNodeId(), 0);
		numNodes++;
	    }
	}

	return graphNodes;
    }

    /**
     * Group and assign ids to nodes by traversing the node relationship tree,
     *   starting with the central node {iId} (if supplied)
     * @param {iId} [string] - The id of the central node
     * @param {nodes} [dict] Dict of nodes
     * @param {relationships} [dict] Map of node relationship data
     * @return {graphNodes, groupNum, numNodes} [dict, int] Dict of nodes to be graphed.
     *   Last group number found
     */
    traverseRelationshipTree(iId, nodes, relationships) {
	//Update id and group attributes for all nodes with some relationship
	let graphNodes = {};
	let groupNum = 0, numNodes = 0;
	while (Object.keys(relationships).length > 0) { //Treat disjoint groups of nodes
	    let startingNode = (iId) ? iId : Object.keys(relationships)[0];
	    let relationStack = [{"name": startingNode, "group": groupNum, "depth": 0}];

	    while (relationStack.length > 0) { //Handle nodes connected to the "startingNode"
		let node = relationStack.pop();
		let name = node.name;
		let group = node.group;
		let depth = node.depth;
		let iIdLinked = (startingNode === iId);

		//Exit loop early if max number of nodes have been found
		if (this.maxNumNodes > 0 && numNodes >= this.maxNumNodes) return [graphNodes, groupNum];

		//Update the node
		graphNodes[name] = this.updateNodeData(nodes[name], group, this.getNodeId(), depth, iIdLinked);
		numNodes++;

		if (relationships[name]) { //Check if other valid relationships exist
		    relationships[name].forEach(el => {
			let currGroup = (el.type !== "member") ? group : ++groupNum;
			if (!graphNodes[el.name]) {
			    relationStack.push({"name": el.name, "group": currGroup, "depth": depth + 1, "type": el.type});
			}
			else if (el.type !== "member") {
			    graphNodes[name].group = currGroup;
			}
		    });
		    delete relationships[name]; //Prevent circular loops
		}
	    }

	    if (!this.disjointNodes) break;
	}

	return [graphNodes, groupNum];
    }

    /**
     * Create a bi-directional dictionary of all node relationships
     * @param {nodes} [array] - Data relevant to graphing nodes
     * @return {relationships} [dict] - A bi-directional dictionary of node relationships
     */
    mapRelationships(nodes) {
	let relationships = {};
	let relationshipData = JSONParser.relationshipData;

	//Create a two way mapping for each relationship
	for (let el of relationshipData) {
	    let name1 = el.markedIndividualName1;
	    let name2 = el.markedIndividualName2;
	    let [type, _] = this.getRelationType(el);

	    //Skip relationships which do not connect valid nodes
	    if (!nodes[name1] || !nodes[name2]) continue;

	    //Mapping relations both ways allows for O(V + 2E) DFS traversal
	    let node2 = {"name": name2, "type": type};
	    if (!relationships[name1]) relationships[name1] = [node2];
	    else relationships[name1].push(node2);

	    let node1 = {"name": name1, "type": type};
	    if (!relationships[name2]) relationships[name2] = [node1];
	    else relationships[name2].push(node1);
	}

	return relationships;
    }

    /**
     * Extract link data from the MarkedIndvidiual and Relationship query data
     * @return {links} [array] - Extracted data relevant to graphing links
     */
    parseLinks(nodes) {
	let links = {};
	let relationshipData = this.getRelationshipData();
	relationshipData.forEach(el => {
	    let node1 = el.markedIndividualName1;
	    let node2 = el.markedIndividualName2;

	    //Ensure node references are non-circular and represent valid nodes
	    if (nodes[node1] && nodes[node2] && node1 !== node2) {
		let linkId = this.getLinkId();
		let [type, order] = this.getRelationType(el);
		let sourceRef = nodes[(order > 0) ? node1 : node2].id;
		let targetRef = nodes[(order > 0) ? node2 : node1].id;

		let linkRef = sourceRef + ":" + targetRef;
		if (!links[linkRef]) {
		    links[linkRef] = {
			"linkId": linkId,
			"source": sourceRef,
			"target": targetRef,
			"type": type
		    };
		}
		else if (links[linkRef].type == "member" ||
			 type == "maternal" || type == "paternal") {
		    links[linkRef].type = type;
		}
	    }
	});

	return [links, Object.values(links)];
    }

    /**
     * Modify node and link data to fit a coOccurrence graph format
     * @param {iId} [string] - The id of the central node
     * @param {nodeDict} [Node dict] - Data relevant to graphing nodes
     * @param {links} [array] - Data relevant to graphing links
     * @return {modifiedNodes} [array] - {nodes} data with added temporal/spatial occurrence information
     * @return {modifiedLinks} [array] - Represents one link from each node to the {iId} central node
     */
    modifyOccurrenceData(iId, nodeDict, links) {
	let occIds = this.getOccurrenceIds(iId);
	
	//Add sightings data to each existing node
	let nodes = Object.values(nodeDict);
	nodes.forEach(node => {
	    node.data.sightings = [];

	    //Extract temporal/spatial encounter data
	    let encounters = node.data.encounters;
	    encounters.forEach(enc => {
		let millis = enc.dateInMilliseconds;
		let lat = enc.decimalLatitude;
		let lon = enc.decimalLongitude;

		if (this.isNumeric(lat) && this.isNumeric(lon) &&
		     this.isNumeric(millis)) {
		    let hours = (parseInt(millis) / 1000) / 3600;
		    node.data.sightings.push({
			"time": {
			    "datetime": hours,
			    "year": parseInt(enc.year),
			    "month": parseInt(enc.month),
			    "day": parseInt(enc.day)
			},
			"location": {
			    "lat": parseInt(lat) * 1000,
			    "lon": parseInt(lon) * 1000
			}
		    });
		}
	    });

	    //Include explicit occurrences
	    if (occIds.has(node.data.individualID)) {
		node.data.sightings.explicit = true;
	    }
	});

	//Remove nodes with no valid sightings
	let modifiedNodes = nodes.filter(node => node.data.sightings.length > 0 ||
					 node.data.sightings.explicit);
	let modifiedNodeMap = new Set(modifiedNodes.map(node => node.id));

	//Record all links connected to the central focusedNode
	let modifiedLinks = [], linkedNodes = new Set();
	let focusedNodeId = nodeDict[iId].id;
	links.forEach(link => {
	    let focusRef = (link.source === focusedNodeId) ? link.source : link.target;
	    let nodeRef = (link.source === focusedNodeId) ? link.target : link.source;

	    if (focusRef === focusedNodeId && modifiedNodeMap.has(nodeRef) &&
		link.target !== link.source) {

		linkedNodes.add(nodeRef);
		modifiedLinks.push(link);
	    }
	});

	//Create new links for any nodes not connected to the focusedNode
	modifiedNodes.forEach(node => {
	    if (!linkedNodes.has(node.id)) {
		modifiedLinks.push({
		    "linkId": this.getLinkId(),
		    "source": focusedNodeId,
		    "target": node.id,
		    "type": "member"
		});
	    }
	});

	return [modifiedNodes, modifiedLinks];
    }

    /**
     * Returns a set of nodes (by id) which explicitly occur with the central node {iId}
     * @param {iId} [string] - The id of the central node
     * @return {idSet} [set] - All nodes (by id) which explicitly occur with the central node {iId} 
     */
    getOccurrenceIds(iId) {
	var idSet = new Set();
	JSONParser.occurrenceData.forEach(occ => {
	    var occIds = occ.encounters.map(enc => enc.individualID);
	    if (occIds.includes(iId)) {
		occIds.forEach(id => idSet.add(id));
	    }
	});
	return idSet;
    }

    /**
     * Return and post-increment the current link id
     * @return {linkId} [int] - A unique id for links
     */
    getLinkId(){
	return this.linkId++;
    }

    /**
     * Return and post-increment the current node id
     * @return {nodeId} [int] - A unique id for nodes
     */
    getNodeId() {
	return this.nodeId++;
    }

    /**
     * Update node group and id attributes
     * @return {node} [obj] - The node data corresponding to the given node id
     * @param {group} [int] - A unique integer assigned to each grouping of connected, familial nodes
     * @param {id} [int] - A unique id assigned to each graph node
     * @param {iIdLinked} [boolean] - Whether the current node is linked to the {iId} central node.
     *   Defaults to false
     */
    updateNodeData(node, group, id, depth, iIdLinked=false) {
	node.id = id;
	node.group = group;
	node.depth = depth;
	node.iIdLinked = iIdLinked;
	return node;
    }

    /**
     * Return node data as filtered by selected nodes
     * @return {nodeData} [array] - Any node data matching {selectedNodes} ids
     */
    getNodeSelection() {
	if (this.selectedNodes) {
	    let nodes = {};
	    Object.entries(JSONParser.nodeData).forEach(([key, data]) => {
		if (this.selectedNodes.has(key)) { //Filter out any keys not in {selectedNodes}
		    nodes[key] = data;
		}
	    });
	    return nodes;
	}
	return JSONParser.nodeData;
    }

    /**
     * Returns relationship data as filtered by selected nodes
     * @return {relationshipData} [Link list] - Any links with both node references in {selectedNodes}
     */
    getRelationshipData() {
	if (this.selectedNodes) {
	    if (!this.relationshipData) { //Memoize relationship data selection
		this.relationshipData = [];
		JSONParser.relationshipData.forEach(link => {
		    let role1 = link.markedIndividualName1;
		    let role2 = link.markedIndividualName2;

		    //Filter out any links referencing nodes outside of {selectedNodes}
		    if (this.selectedNodes.has(role1) && this.selectedNodes.has(role2)) {
			this.relationshipData.push(link);
		    }
		});
	    }
	    return this.relationshipData;
	}
	return JSONParser.relationshipData;
    }

    /**
     * Returns the relationship type of a given link
     * @param {link} [Link] - A link object from {relationshipData}
     * @return {type} [string] - Describes the type of {link} passed in
     */
    getRelationType(link){
	let defaultOrder = 1;
	if (link) {
	    let role1 = link.markedIndividualRole1;
	    let role2 = link.markedIndividualRole2;
	    let roles = [[role1, 1], [role2, -1]];

	    for (let [role, order] of roles) {
		if (role === "mother") return ["maternal", order];
		else if (role === "father") return ["paternal", order];
	    }

	    if (role1 === "calf" || role2 === "calf") return ["familial", defaultOrder];
	}

	return ["member", defaultOrder];
    }

    /**
     * Determines if {num} represents a numeric value
     * @param {num} [obj] - An object of unknown value/type
     * @return {type} [boolean] - Whether {num} represents a numeric value
     */
    isNumeric(num) {
	return !isNaN(num);
    }
}
