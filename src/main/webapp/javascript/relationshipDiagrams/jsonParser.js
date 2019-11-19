class JSONParser {
    constructor() {
	this.graphId = 0;//id that will increment with each relevant MARKEDINDIVIDUAL
	this.nodeMap = {};//map of the MARKEDINDIVIDUALS to be displayed on the graph
	this.dataDict = {};//maps individualID to the rest of the individual's data
	this.relevantId = 0;//only one of these id's is needed, will change later
	this.linkId = 0;//id for the link
    }
    
    //Creates a relational object from JSON data
    parseJSON(iId, json, graphCallback) {
	this.getNodeRefs(json, () => this.parseJSONCallback(iId, json, graphCallback));
    }

    parseJSONCallback(iId, json, graphCallback) {
	let nodes = this.parseNodes(iId, json);
	let links = this.parseLinks(json);
	console.log("before print returned nodes and links");
	console.log([nodes, links]);
	console.log("after print nodes and links");
	graphCallback(nodes, links);
    }
    
    parseNodes(iId, json) {
	var rId = this.getRelevantId();//numerical id that increments
	this.addNodeMapId(iId, rId);

	//right now if there are relationships with duplicates they will overwrite in the dict
	for(var i = 0; i < json.length; i++){
	    let nameOne = json[i].markedIndividualName1;
	    let nameTwo = json[i].markedIndividualName2;
	    let relevantId = this.getRelevantId();
	    if(nameOne != iId && nameTwo == iId){
		this.addNodeMapId(nameOne, relevantId); 
	    }
	    else if(nameTwo != iId && nameOne == iId){
		this.addNodeMapId(nameTwo, relevantId);
	    }
	}

	var nodes = new Array();
	
	for(let key in this.nodeMap){
	    var name = this.dataDict[key].displayName;
	    name = name.substring(name.indexOf(" ") + 1);
	    console.log(key);
	    nodes.push({"id": this.nodeMap[key], "data": {"name": name, "gender": this.dataDict[key].sex, "genus": this.dataDict[key].genus, "dateFirstIdentified": this.dataDict[key].dateFirstIdentified, "numberEncounters": this.dataDict[key].numberEncounters, "timeOfBirth": this.dataDict[key].timeOfBirth}});
	}
	    
	console.log(nodes);
	
	return nodes;
    }

    //TODO:  query for all markedindivudial data and create a dictionary with the ids
    getNodeRefs(json, callback) {
	let query = wildbookGlobals.baseUrl + "/api/jdoql?" +
	    encodeURIComponent("SELECT FROM org.ecocean.MarkedIndividual");//get all individuals
	    d3.json(query, (error, json) => this.createNodeDictionary(json, callback));
    }

    //creates the dictionary
    //TODO: remove debugging stuff
    createNodeDictionary(json, callback) {
	var sizeOfJSON = json.length;//number of MARKEDINDIVIDUALS
	
	if(sizeOfJSON >= 1){//if there is anything in the query result
	    for (var i = 0; i < sizeOfJSON; i++) {//iterate over all MARKEDINDIVIDUALS
		this.dataDict[json[i].individualID] = json[i];//adding to dictionary of data referencable by individualID
	    }
	}
	else{
	    console.log("there is no json data");//if there is nothing in the array
	}
	
	//printing contents of dictionaries for testing
	console.log(this.dataDict);

	//Return to main
	callback();
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
	return this.dataDict[this.nodeMap[id]].sex;
    }
    
    getGenus(id){
	return this.dataDict[this.nodeMap[id]].genus;
    }

    getDisplayName(id){
	return this.dataDict[this.nodeMap[id]].displayName;
    }

    addNodeMapId(nodeRef, id) {
	this.nodeMap[nodeRef] = id;
    }

    getNodeMapId(nodeRef) {
	return this.nodeMap[nodeRef];
    }

    addDataDictId(individualID, individualData){
	this.dataDict[individualID] = individualData;
    }
    
    getDataDictId(individualID){
	return this.dataDict[individualID];
    }

    getRelationType(entry){
	return entry.type;
    }
    
    parseLinks(json) {

	var links = new Array();
	for(var i = 0; i < json.length; i++) {
	    console.log("in links creation loop");
	    let linkId = this.getLinkId();
	    let sourceRef = this.nodeMap[json[i].markedIndividualName1];
	    let targetRef = this.nodeMap[json[i].markedIndividualName2];
	    
	    let type = json[i].type;

	    links.push({"linkId": linkId, "source": sourceRef, "target": targetRef, "type": type});
	}

	console.log(links);

	return links;
    }

    
}//end
