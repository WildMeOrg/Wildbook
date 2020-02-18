//Helper class purposed to query and parse JSON node/link data for social and coOccurrence graphs
class JSONParser {
    //TODO - Logically couple disjointNodes and iId	
   /**
    * Creates a JSONParse instance
    * @param {globals} [object] - Global data from Wildbook .jsp pages
    * @param {selectedNodes} [string|iterable] - Nodes included when generating JSON data. When null, all nodes are included. 
    *   Defaults to null.
    * @param {disjointNodes} [boolean] - Whether nodes disconnected from the central node (iId) should be included when 
    *   generating JSON data. Defaults to false  
    * @param {numNodes} [int] - Determines the total number of nodes to graph
    * @param {localFiles} [boolean] - Whether requests should be made to local MarkedIndividual/Relationship data
    */
    constructor(globals, selectedNodes=null, disjointNodes=false, numNodes=-1, localFiles=false) {	
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
	this.numNodes = numNodes;
	this.localFiles = localFiles;
	this.globals = globals;
    }

    //TODO - Turn iId into an optional parameter
    /**
     * Parse link and node data to generate a graph via {graphCallback}
     * @param {iId} [string] - The id of the central node
     * @param {graphCallback} [function] - Handles generated node and link data arrays
     * @param {isCoOccurrence} [boolean] - Determines whether node/link data should feature additional coOccurrence modifications
     */
    parseJSON(iId, graphCallback, isCoOccurrence=false) {
	this.queryNodeData().then(() => {
	    this.queryRelationshipData().then(() => {
		let nodes = this.parseNodes(iId);
		let links = this.parseLinks(nodes);

		if (isCoOccurrence) { //Extract temporal and latitude/longitude encounter data
		    [nodes, links] = this.modifyOccurrenceData(iId, nodes, links);
		}
		
		graphCallback(nodes, links);
	    }).catch(error => console.error(error));
	}).catch(error => console.error(error)); 
    }

    /**
     * Query wrapper for the storage of MarkedIndividual data
     * @return {queryData} [array] - All MarkedIndividual data in the Wildbook DB
     */
    queryNodeData() {
	let query;
	if (!this.localFiles) {
	    query = this.globals.baseUrl + "/api/jdoql?" +
		encodeURIComponent("SELECT FROM org.ecocean.MarkedIndividual"); //Get all individuals
	}
	else query = "MarkedIndividual.json";
	return this.queryData("nodeData", query, this.storeQueryAsDict);
    }

    /**
     * Query wrapper for the storage of Relationship data
     * @returns {queryData} [array] - All Relationship data in the Wildbook DB
     */
    queryRelationshipData() {
	let query;
	if (!this.localFiles) {
	    query = this.globals.baseUrl + "/api/jdoql?" +
		encodeURIComponent("SELECT FROM org.ecocean.social.Relationship " +
				   "WHERE (this.type == \"social grouping\")");
	}
	else query = "Relationship.json";
	return this.queryData("relationshipData", query);
    }

    /**
     * Retrieve JSON data from the Wildbook DB
     * @param {type} [string] - The static attribute key used to store the queried data
     * @param {query} [string] - Determines the query ran
     * @param {callback} [function] - If not null, passes query data into the specified {callback} function for 
     *   further processing. Defaults to null 
     */
    queryData(type, query, callback=null) {
	return new Promise((resolve, reject) => {
	    if (!JSONParser[type]) { //Memoize the result
		/*$.ajax({
		    "url": query,
		    "type": "GET",
		    "dataType": "json",
		    "success": (json) => {
			if (callback) callback(json, type, resolve);
			else {
			    console.log(json);
			    JSONParser[type] = json;
			    resolve(); //Handle the encapsulating promise
			}
		    },
		    "error": (req, error) => reject(error)
		});*/

		
		d3.json(query, (error, json) => {
		    if (error) {
			reject(error);
		    }
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

    /**
     * Extract node data from the MarkedIndividual and Relationship query data
     * @param {iId} [string] - The id of the central node
     * @return {nodes} - Extracted data relevant to graphing nodes
     */
    parseNodes(iId) {
	//Assign unique ids and groupings to the queried node data
	this.processNodeData(iId); //TODO - Memoize this process

	//Extract meaningful node data
	let nodes = [];
	let nodeData = this.getNodeData();
	for (let key in nodeData) {
	    let data = nodeData[key];
	    let name = data.displayName;
	    name = name.substring(name.indexOf(" ") + 1);

	    //TODO - Remove
	    //Exit loop early if max number of nodes have been found
	    if (this.numNodes > 0 && nodes.length >= this.numNodes) break;
	    
	    //Add node to list if disjoint nodes are allowed, or if a link exists to the central node {iId}
	    if (this.disjointNodes || data.iIdLinked) {
		nodes.push({
		    "id": data.id,
		    "group": data.group,
		    "data": {
			"name": name,
			"gender": data.sex,
			"genus": data.genus, //TODO - Remove?
			"individualID": key,
			"firstSighting": data.dateFirstIdentified,
			"latestSighting": data.dateTimeLatestSighting,
			"numberEncounters": data.numberEncounters,
			"timeOfBirth": data.timeOfBirth,
			"timeOfDeath": data.timeOfDeath,
			"isDead": (data.timeOfDeath > 0) ? true : false,
			"isFocused": (key === iId),
			"currLifeStage": this.getLifeStage(data),
			"encounters": data.encounters
		    } //TODO - role
		});
	    }
	}

	return nodes;
    }

    /**
     * Assign unique ids and label connected groups for all nodes
     * @param {iId} [string] - The id of the central node
     */
    processNodeData(iId) {
	//Generate a two way mapping of all node relationships
	let relationships = this.mapRelationships();
	
	//Update id and group attributes for all nodes with some relationship
	let groupNum = 0, numNodes = 0;
	while (Object.keys(relationships).length > 0) { //Treat disjoint groups of nodes
	    let startingNode = (iId) ? iId : Object.keys(relationships)[0];
	    let relationStack = [{"name": startingNode, "group": groupNum}];

	    //Exit loop early if max number of nodes have been found
	    if (this.numNodes > 0 && numNodes >= this.numNodes) break;
	    
	    while (relationStack.length > 0) { //Handle nodes connected to the "startingNode"
		let link = relationStack.pop();
		let name = link.name;
		let group = link.group;
		
		//Update node id and group
		let iIdLinked = (startingNode === iId);
		    
		//Update the node if it does not have an id, or has a familial relation
		if (!JSONParser.nodeData[name].id || link.type !== "member") { 
		    this.updateNodeData(name, group, this.getNodeId(), iIdLinked);
		}

		if (relationships[name]) { //Check if other valid relationships exist
		    relationships[name].forEach(el => {
			let currGroup = (el.type !== "member") ? group : ++groupNum;
			relationStack.push({"name": el.name, "group": currGroup, "type": el.type});
		    });
		    delete relationships[name]; //Prevent circular loops    
		}
	    }

	    if (!this.disjointNodes) break;
	    numNodes++;
	}

	//Update id and group attributes for all disconnected nodes
	Object.keys(JSONParser.nodeData).forEach(key => {
	    if (!JSONParser.nodeData[key].id) {
		this.updateNodeData(key, ++groupNum, this.getNodeId());
	    }
	});

	JSONParser.isNodeDataProcessed = true;
    }
	
    /**
     * Create a bi-directional dictionary of all node relationships
     * @return {relationships} [dict] - A bi-directional dictionary of node relationships 
     */
    mapRelationships() {
	let relationships = {};
	let relationshipData = JSONParser.relationshipData;
	    
	//Create a two way mapping for each relationship
	relationshipData.forEach(el => {
	    let name1 = el.markedIndividualName1;
	    let name2 = el.markedIndividualName2;
	    let type = this.getRelationType(el);
	    
	    //Mapping relations both ways allows for O(V + 2E) DFS traversal
	    let link2 = {"name": name2, "type": type};
	    if (!relationships[name1]) relationships[name1] = [link2];
	    else relationships[name1].push(link2);
	    
	    let link1 = {"name": name1, "type": type};
	    if (!relationships[name2]) relationships[name2] = [link1];
	    else relationships[name2].push(link1);    
	});
	
	return relationships;
    }

    /**
     * Determine the most recent life stage of the given individual
     * @param {data} [obj] - The data attribute of the current node
     * @return {lifeStage} [string] - The current life stage of the contextual node. 
     *   Defaults to null if missing data
     */
    getLifeStage(data) {
	if (data.encounters && data.encounters.length > 0) 
	    return data.encounters[0].lifeStage;
	else return null;
    }


    /**
     * Extract link data from the MarkedIndvidiual and Relationship query data
     * @return {links} [array] - Extracted data relevant to graphing links
     */
    parseLinks(nodes) {
	let links = [];
	let nodeData = listToDict(nodes, ["data", "individualID"]);
	let relationshipData = this.getRelationshipData();
	relationshipData.forEach(el => {
	    let node1 = el.markedIndividualName1;
	    let node2 = el.markedIndividualName2;

	    //Ensure node references are non-circular and represent valid nodes
	    if (nodeData[node1] && nodeData[node2] && node1 !== node2) {
		let linkId = this.getLinkId();
		let sourceRef = nodeData[node1].id;
		let targetRef = nodeData[node2].id;
		let type = this.getRelationType(el);
	
		links.push({
		    "linkId": linkId,
		    "source": sourceRef,
		    "target": targetRef,
		    "type": type
		});
	    }
	    else console.error("Invalid Link ", "Src: " + node1, "Target: " + node2);
	});

	return links;
    }

    /**
     * Modify node and link data to fit a coOccurrence graph format
     * @param {iId} [string] - The id of the central node
     * @param {nodes} [array] - Data relevant to graphing nodes
     * @param {links} [array] - Data relevant to graphing links
     * @return {modifiedNodes} [array] - {nodes} data with added temporal/spatial occurrence information
     * @return {modifiedLinks} [array] - Represents one link from each node to the {iId} central node
     */
    modifyOccurrenceData(iId, nodes, links) {
	//Add sightings data to each existing node
	nodes.forEach(node => {
	    node.data.sightings = [];
		
	    //Extract temporal/spatial encounter data
	    let encounters = node.data.encounters;
	    encounters.forEach(enc => {
		let millis = enc.dateInMilliseconds;
		let lat = enc.decimalLatitude;
		let lon = enc.decimalLongitude;

		console.log(lat, lon);
		
		if (typeof lat === "number" && typeof lon === "number" &&
		    typeof millis === "number") {
		    let minutes = (millis / 1000) / 60;
		    node.data.sightings.push({
			"time": {
			    "datetime": minutes,
			    "year": enc.year,
			    "month": enc.month,
			    "day": enc.day
			},
			"location": {
			    "lat": lat * 1000,
			    "lon": lon * 1000
			}
		    });
		}
	    });
	});

	//Remove nodes with no valid sightings
	let modifiedNodes = nodes.filter(node => node.data.sightings.length > 0);
	let modifiedNodeMap = new Set(modifiedNodes.map(node => node.id));
	
	//Record all links connected to the central focusedNode
	let modifiedLinks = [], linkedNodes = new Set();
	let focusedNodeId = this.getNodeDataById(iId).id;
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
     * @param {nodeRef} [string] - A unique string identifying the given node
     * @param {group} [int] - A unique integer assigned to each grouping of connected, familial nodes
     * @param {id} [int] - A unique id assigned to each graph node
     * @param {iIdLinked} [boolean] - Whether the current node is linked to the {iId} central node
     */
    updateNodeData(nodeRef, group, id, iIdLinked) {
	let node = JSONParser.nodeData[nodeRef];
	if (node) {
	    node.id = id;
	    node.group = group;
	    node.iIdLinked = iIdLinked;
	}
	else node = {"id": id, "group": group, "iIdLinked": iIdLinked};
	JSONParser.nodeData[nodeRef] = node;
    }

    /**
     * Return node data as filtered by selected nodes
     * @return {nodeData} [array] - Any node data matching {selectedNodes} ids
     */
    getNodeData() {
	if (this.selectedNodes) {
	    if (!this.nodeData) { //Memoize node data selection
		this.nodeData = {};
		Object.keys(JSONParser.nodeData).forEach(key => {
		    let data = JSONParser.nodeData[key];
		    if (this.selectedNodes.has(key)) { //Filter out any keys not in {selectedNodes}
			this.nodeData[key] = data;
		    }
		});
	    }
	    return this.nodeData;
	}
	return JSONParser.nodeData;
    }

    /**
     * Returns id referenced element from node data
     * @param {id} [int] - A unique int identifying the given node
     * @return {node} [obj] - The node data corresponding to the given node id
     */
    getNodeDataById(id) {
	return this.getNodeData()[id];
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
	if (link) { 
	    let role1 = link.markedIndividualRole1;
	    let role2 = link.markedIndividualRole2;

	    if (role1 === "mother" || role2 === "mother") return "maternal";
	    else if (role1 === "father" || role2 === "father") return "paternal"; //Not currently supported by WildMe
	    else if (role1 === "calf" || role2 === "calf") return "familial";
	    else return "member";
	}
	
	console.error("Link relation not recognized: ", link);
	return "member"; //Ensures the graph renders gracefully
    }
}

/**
 * Returns a dictionary indexing a list of objects based on the specified key
 * @param {list} [list] - A list of objects
 * @return {key} [string|string list] - An object property within each element of the list. 
 *   When specified as a list of strings, keys may be used to access nested object properties
 */
function listToDict(list, key) {
    dict = {};
    for (el of list) {
	let dictKey;
	if (Array.isArray(key)) {
	    dictKey = el;
	    for (k of key) {
		dictKey = dictKey[k];
	    }
	}
	else dictKey = el[key];
	
	dict[dictKey] = el;
    }
    return dict;
}
