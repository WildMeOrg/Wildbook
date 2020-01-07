class JSONParser {
    constructor() {
	this.graphId = 0; //Id that will increment with each relevant MARKEDINDIVIDUAL	
	this.relevantId = 0; //Only one of these id's is needed, will change later
	this.linkId = 0; //Id for the link
    }

    //Creates a relational object from JSON data
    parseJSON(iId, graphCallback, isCoOccurrence=false) {
	this.getNodeData().then(() => {
	    this.getRelationshipData().then(() => {
		if (isCoOccurrence) {
		    this.getCoOccurrenceData().then(() => {
			let nodes = this.parseNodes(iId);
			let links = this.parseLinks();
			let [modLinks, modNodes] = this.modifyCoOccurrenceLinks(iId, links, nodes);
			graphCallback(modNodes, modLinks);
		    });
		}
		else {
		    let nodes = this.parseNodes(iId);
		    let links = this.parseLinks();
		    graphCallback(nodes, links);
		}
	    });
	});
    }

    getNodeData() {
	let query = wildbookGlobals.baseUrl + "/api/jdoql?" +
	    encodeURIComponent("SELECT FROM org.ecocean.MarkedIndividual"); //Get all individuals
	return this.getData("nodeData", query, this.storeJSONSet);
    }

    getRelationshipData() {
	let query = wildbookGlobals.baseUrl + "/api/jdoql?" +
	    encodeURIComponent("SELECT FROM org.ecocean.social.Relationship " +
			       "WHERE (this.type == \"social grouping\")");
	return this.getData("relationshipData", query);
    }

    getCoOccurrenceData() {
	let query = wildbookGlobals.baseUrl + "/api/jdoql?" +
	    encodeURIComponent("SELECT FROM org.ecocean.Encounter");
	return this.getData("coOccurrenceData", query, this.storeJSONSet);
    }

    storeJSONSet(json, type, resolve) {
	if (json.length >= 1) {
	    JSONParser[type] = {};
	    json.forEach(el => {
		let jsonSet = JSONParser[type];
		if (!jsonSet[el.individualID]) jsonSet[el.individualID] = el;
		else { //Handles array sets
		    if (!Array.isArray(jsonSet[el.individualID])) {
			jsonSet[el.individualID] = [jsonSet[el.individualID]];
		    }
		    jsonSet[el.individualID].push(el);
		}
		JSONParser[type]= jsonSet;
	    });
	}
	else console.log("No " + type + " JSON data found");
	resolve();
    }

    getData(type, query, callback=false) {
	return new Promise((resolve) => {
	    if (!JSONParser[type]) {
		d3.json(query, (error, json) => {
		    if (callback) callback(json, type, resolve);
		    else {
			JSONParser[type] = json;
			resolve();
		    }
		});
	    }
	    else resolve();
	});	
    }
    
    parseNodes(iId) {
	if (!JSONParser.nodeMap) {
	    //TODO - Consider moving this to a query macro on the java backend
	    //Create a graph of markedIndividual relationships
	    let relations = {};
	    JSONParser.relationshipData.forEach(el => {
		let name1 = el.markedIndividualName1;
		let name2 = el.markedIndividualName2;

		//Mapping relations both ways allows for O(V + 2E) DFS traversal
		if (!relations[name1]) relations[name1] = [name2];
		else relations[name1].push(name2);

		if (!relations[name2]) relations[name2] = [name1];
		else relations[name2].push(name1);

	    });

	    JSONParser.nodeMap = {};
	    let relationStack = [iId];
	    while(relationStack.length > 0) {
		let name = relationStack.pop();

		//Add marked individual to node list
		if (!JSONParser.nodeMap[name]) {
		    let relevantId = this.getRelevantId();
		    this.addNodeMapId(name, relevantId);
		}

		//Check if other valid relations exist
		if (relations[name]) {
		    relations[name].forEach(el => relationStack.push(el));
		    delete relations[name]; //Prevents circular loops    
		}
	    }
	}

	let nodes = [];
	for (let key in JSONParser.nodeMap) {
	    let name = JSONParser.nodeData[key].displayName;
	    name = name.substring(name.indexOf(" ") + 1);

	    nodes.push({
		"id": JSONParser.nodeMap[key],
		"individualId": key, 
		"data": {
		    "name": name,
		    "gender": JSONParser.nodeData[key].sex,
		    "genus": JSONParser.nodeData[key].genus,
		    "individualID": JSONParser.nodeData[key].individualID,
		    "numberLocations": JSONParser.nodeData[key].numberLocations,
		    "dateFirstIdentified": JSONParser.nodeData[key].dateFirstIdentified,
		    "numberEncounters": JSONParser.nodeData[key].numberEncounters,
		    "timeOfBirth": JSONParser.nodeData[key].timeOfBirth,
		    "timeOfDeath": JSONParser.nodeData[key].timeOfDeath,
		    "isDead": (JSONParser.nodeData[key].timeOfDeath > 0) ? true : false,
		    "isFocused": this.isFocusNode(JSONParser.nodeData[key].individualID, iId)
		} //TODO - role, sightings (datetime_ms, location)
		//TODO - Add metric concerning how recently an animal has been sighted compared to others
	    });
	}
	
	return nodes;
    }

        parseLinks() {
	let links = [];
	JSONParser.relationshipData.forEach(el => {
	    let linkId = this.getLinkId();
	    let sourceRef = JSONParser.nodeMap[el.markedIndividualName1];
	    let targetRef = JSONParser.nodeMap[el.markedIndividualName2];
	    let type = this.getRelationType(el);

	    if (sourceRef == null || targetRef == null) { //Catch invalid links
		console.error("Invalid Link ", "Src: " + sourceRef, "Target: " + targetRef);
	    }
	    else {
		links.push({
		    "linkId": linkId,
		    "source": sourceRef,
		    "target": targetRef,
		    "type": type
		});
	    }
	});

	return links;
    }

    modifyCoOccurrenceLinks(iId, links, nodes) {
	let modifiedLinks = [], linkedNodes = {};
	let focusedRef = JSONParser.nodeMap[iId];
	links.forEach(link => {
	    if (links.source === focusedRef || links.target === focusedRef) {
		let nodeRef = (links.source === focusedRef) ? links.target : links.source;
		linkedNodes[nodeRef] = true;
		modifiedLinks.push(link);
	    }
	});

	Object.values(JSONParser.nodeMap).forEach(nodeRef => {
	    if (!linkedNodes[nodeRef]) {
		modifiedLinks.push({
		    "linkId": this.getLinkId(),
		    "source": focusedRef,
		    "target": nodeRef,
		    "type": "member"
		});
	    }
	});

	nodes.forEach(node => {
	    let encounters = JSONParser.coOccurrenceData[node.individualId];
	    node.data.sightings = [];

	    if (!Array.isArray(encounters)) encounters = [encounters];
	    encounters.forEach(enc => {
		node.data.sightings.push({
		    "datetime_ms": enc.dateInMilliseconds,
		    "location": {
			"lat": enc.decimalLatitude,
			"lon": enc.decimalLongitude
		    }
		});
	    });
	});

	return [modifiedLinks, nodes];
    }

    isFocusNode(id, focusNodeId){
	return (id === focusNodeId);
    }

    getLinkId(){
	return this.linkId++;
    }

    getGraphId() {
	return this.graphId++;
    }

    getRelevantId() {
	return this.relevantId++;
    }

    getGender(id){
	return JSONParser.nodeData[JSONParser.nodeMap[id]].sex;
    }
    
    getGenus(id){
	return JSONParser.nodeData[JSONParser.nodeMap[id]].genus;
    }

    getDisplayName(id){
	return JSONParser.nodeData[JSONParser.nodeMap[id]].displayName;
    }

    addNodeMapId(nodeRef, id) {
	JSONParser.nodeMap[nodeRef] = id;
    }

    getNodeMapId(nodeRef) {
	return JSONParser.nodeMap[nodeRef];
    }

    addDataDictId(individualID, individualData){
	JSONParser.nodeData[individualID] = individualData;
    }
    
    getDataDictId(individualID){
	return JSONParser.nodeData[individualID];
    }

    getRelationType(entry){
	let role1 = entry.markedIndividualRole1;
	let role2 = entry.markedIndividualRole2;

	if (role1 === "mother" || role2 === "mother") return "maternal";
	else if (role1 === "father" || role2 === "father") return "paternal"; //Not currently supported by WildMe
	else if (role1 === "calf" || role2 === "calf") return "familial";
	else return "member"
    }
}

