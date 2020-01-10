class JSONParser {
    constructor(selectedNodes=null, disjointNodes=false) {
	//Keep track of a unique id for each node and link
	this.nodeId = 0;
	this.linkId = 0;

	//Generate a set of selected nodes (if provided) to be used as a mask
	if (selectedNodes !== null) {
	    if (typeof selectedNodes === "string") 
		selectedNodes = selectedNodes.substr(1, selectedNodes.length - 2).split(", ");
	    this.selectedNodes = new Set(selectedNodes);
	}

	this.disjointNodes = disjointNodes;
    }

    //Parse links and nodes to generate a graph via graphCallback
    parseJSON(iId, graphCallback, isCoOccurrence=false) {
	this.queryNodeData().then(() => {
	    this.queryRelationshipData().then(() => {
		let nodes = this.parseNodes(iId);
		let links = this.parseLinks();

		if (isCoOccurrence) {
		    [nodes, links] = this.modifyOccurrenceData(iId, nodes, links);
		}
		
		graphCallback(nodes, links);
	    });
	}); 
    }

    //Query and store all Marked Individual data
    queryNodeData() {
	let query = wildbookGlobals.baseUrl + "/api/jdoql?" +
	    encodeURIComponent("SELECT FROM org.ecocean.MarkedIndividual"); //Get all individuals
	    return this.queryData("nodeData", query, this.storeQueryAsDict);
    }

    //Query and store all Relationship data
    queryRelationshipData() {
	let query = wildbookGlobals.baseUrl + "/api/jdoql?" +
	    encodeURIComponent("SELECT FROM org.ecocean.social.Relationship " +
			       "WHERE (this.type == \"social grouping\")");
	return this.queryData("relationshipData", query);
    }

    //Retrieve JSON data from the Wildbook DB
    queryData(type, query, callback=false) {
	return new Promise((resolve) => {
	    if (!JSONParser[type]) { //Memoize the result
		d3.json(query, (error, json) => {
		    if (callback) callback(this, json, type, resolve);
		    else {
			JSONParser[type] = json;
			resolve(); //Handle the encapsulating promise
		    }
		});
	    }
	    else resolve(); //Handle the encapsulating promise
	});	
    }
    
    //Convert JSON query result from an array to a dictionary
    storeQueryAsDict(self, json, type, resolve) {
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

    //Extract node data from the Marked Individual and Relationship queries
    parseNodes(iId) {
	//Assign unique ids and groupings to the queried node data
	if (!JSONParser.isNodeDataProcessed) this.processNodeData(iId);

	//Extract meaningful node data
	let nodes = [];
	let nodeData = this.getNodeData();
	Object.keys(nodeData).forEach(key => {
	    let data = nodeData[key];
	    let name = data.displayName;
	    name = name.substring(name.indexOf(" ") + 1);
	    
	    if (this.disjointNodes || data.iIdLinked) {
		nodes.push({
		    "id": data.id,
		    "group": data.group,
		    "data": {
			"name": name,
			"gender": data.sex,
			"genus": data.genus, //TODO - Remove?
			"individualID": key,
			"dateFirstIdentified": data.dateFirstIdentified,
			"latestSighting": data.dateTimeLatestSighting,
			"numberEncounters": data.numberEncounters,
			"timeOfBirth": data.timeOfBirth,
			"timeOfDeath": data.timeOfDeath,
			"isDead": (data.timeOfDeath > 0) ? true : false,
			"isFocused": (key === iId),
			"encounters": data.encounters
		    } //TODO - role
		    //TODO - Add metric concerning how recently an animal has been sighted compared to others
		});
	    }
	});

	return nodes;
    }

    //Assign unique ids and label connected groups for all nodes
    processNodeData(iId) {
	let relationships = this.mapRelationships();
	
	//Update id and group attributes for all nodes with some relationship
	let groupNum = 0;
	while (Object.keys(relationships).length > 0) { //Treat disjoint groups of nodes
	    let startingNode = (iId) ? iId : Object.keys(relationships)[0];
	    let relationStack = [{"name": startingNode, "group": groupNum}];

	    while (relationStack.length > 0) { //Handle nodes connected to the "startingNode"
		let link = relationStack.pop();
		let name = link.name;
		let group = link.group;
		
		//Update node id and group
		let iIdLinked = (startingNode === iId);
		if (!JSONParser.nodeData[name].id || link.type !== "member") {
		    this.updateNodeData(name, group, this.getNodeId(), iIdLinked);
		}

		//Check if other valid relationships exist
		if (relationships[name]) {
		    relationships[name].forEach(el => {
			let currGroup = (el.type !== "member") ? group : ++groupNum;
			relationStack.push({"name": el.name, "group": currGroup, "type": el.type});
		    });
		    delete relationships[name]; //Prevent circular loops    
		}
	    }
	}

	//Update id and group attributes for all disconnected nodes
	Object.keys(JSONParser.nodeData).forEach(key => {
	    if (!JSONParser.nodeData[key].id) {
		this.updateNodeData(key, ++groupNum, this.getNodeId());
	    }
	});

	JSONParser.isNodeDataProcessed = true;
    }
	
    //Create a bi-directional dictionary of all node relationships
    mapRelationships() {
	let relationships = {};
	let relationshipData = JSONParser.relationshipData;
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

    //Extract link data from the Marked Indvidiual and Relationship queries
    parseLinks() {
	let links = [];
	let nodeData = this.getNodeData();
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

    //Modify node and link data to fit a coOccurrence graph format
    modifyOccurrenceData(iId, nodes, links) {
	//Add sightings data to each existing node
	nodes.forEach(node => {
	    node.data.sightings = [];
	    let encounters = node.data.encounters;
	    encounters.forEach(enc => {
		let time = enc.dateInMilliseconds;
		let lat = enc.decimalLatitude;
		let lon = enc.decimalLongitude;
		
		if (typeof lat === "number" && typeof lon === "number" &&
		    typeof time === "number") {
		    node.data.sightings.push({
			"datetime_ms": time,
			"location": {
			    "lat": lat,
			    "lon": lon
			}
		    });
		}
	    });
	});

	//Remove nodes with no valid sightings
	let modifiedNodes = nodes.filter(node => node.data.sightings.length > 0);
	let modifiedNodeMap = new Set(modifiedNodes.map(node => node.id));

	debugger;
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

    //Return and post-increment the current link id
    getLinkId(){
	return this.linkId++;
    }

    //Return and post-increment the current node id
    getNodeId() {
	return this.nodeId++;
    }

    //Update node group and id attributes
    updateNodeData(nodeRef, group, id, iIdLinked) {
	let node = JSONParser.nodeData[nodeRef];
	if (node) {
	    node.id = id;
	    node.group = group;
	    node.iIdLinked = iIdLinked;
	}
	else node = {"id": id, "group": group};
	JSONParser.nodeData[nodeRef] = node;
    }

    //Return node data filtered by selected nodes
    getNodeData() {
	if (this.selectedNodes) {
	    if (!this.nodeData) { //Memoize node data selection
		this.nodeData = {};
		Object.keys(JSONParser.nodeData).forEach(key => {
		    let data = JSONParser.nodeData[key];
		    if (this.selectedNodes.has(key)) {
			this.nodeData[key] = data;
		    }
		});
	    }
	    return this.nodeData;
	}
	return JSONParser.nodeData;
    }

    //Returns id referenced element from node data
    getNodeDataById(id) {
	return this.getNodeData()[id];
    }

    //Returns relationship data filtered by selected nodes
    getRelationshipData() { 
	if (this.selectedNodes) {
	    if (!this.relationshipData) { //Memoize relationship data selection
		this.relationshipData = [];
		JSONParser.relationshipData.forEach(link => {
		    let role1 = link.markedIndividualName1;
		    let role2 = link.markedIndividualName2;

		    if (this.selectedNodes.has(role1) && this.selectedNodes.has(role2)) {
			this.relationshipData.push(link);
		    }
		});
	    }
	    return this.relationshipData;
	}
	return JSONParser.relationshipData;
    }

    //Returns the relationship type of a given link
    getRelationType(link){
	let role1 = link.markedIndividualRole1;
	let role2 = link.markedIndividualRole2;

	if (role1 === "mother" || role2 === "mother") return "maternal";
	else if (role1 === "father" || role2 === "father") return "paternal"; //Not currently supported by WildMe
	else if (role1 === "calf" || role2 === "calf") return "familial";
	else return "member"
    }
}

