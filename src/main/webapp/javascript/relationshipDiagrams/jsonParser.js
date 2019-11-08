class JSONParser {
    constructor() {
	this.currId = 0;//id that will increment with each relevant MARKEDINDIVIDUAL
	this.nodeMap = {};//map of the MARKEDINDIVIDUALS to be displayed on the graph
	this.nodeDict = {};//dictionary where each numerical id maps to individualID
	this.dataDict = {};//maps individualID to the rest of the individual's data
    }
    
    //Creates a relational object from JSON data
    parseJSON(json) {
	let nodes = this.parseNodes(json);
	let links = this.parseLinks(json);
	return [nodes, links];
    }
    
    parseNodes(json) {
	let nodeRefs = this.getNodeRefs(json);
	return json.map(entry => {
	    let id = this.getId();
	    return {
		"id": id,
		//"group": //Not in use currently
		"data": {
		    "name": this.getName(id, json), //TODO
		    "gender": "PLACEHOLDER" //TODO
		}
	    }
	});
    }

    //TODO:  query for all markedindivudial data and create a dictionary with the ids
    getNodeRefs(json) {
	let query = wildbookGlobals.baseUrl + "/api/jdoql?" +
	    encodeURIComponent("SELECT FROM org.ecocean.MarkedIndividual");//get all individuals
	    d3.json(query, (error, json) => this.createNodeDictionary(json));
    }

    //creates the dictionary
    //TODO: remove debugging stuff
    createNodeDictionary(json) {

	var sizeOfJSON = json.length;//number of MARKEDINDIVIDUALS
	
	if(sizeOfJSON >= 1){//if there is anything in the query result
	    console.log(json);	    
	    var nodeID;//to store the current id
	    for (var i = 0; i < sizeOfJSON; i++) {//iterate over all MARKEDINDIVIDUALS
		console.log("inside the loop that creates the dict");
		nodeID = this.getId();//each MARKEDINDIVIDUAL has a unique id
		var indID = json[i].individualID;//retrieve individualID
		this.nodeDict[nodeID] = indID;//adding to dictionary of unique id and wildbook individualID
		this.dataDict[json[i].individualID] = json[i];//adding to dictionary of data referencable by individualID
	    }
	}
	else{
	    console.log("there is no json data");//if there is nothing in the array
	}
	
	//printing contents of dictionaries for testing
	console.log(this.nodeDict);
	console.log(this.dataDict);
	var temp = this.nodeDict[10];
	console.log(this.dataDict[temp].displayName);//to test dictionaries
    }

    getId() {
	return this.currId++;
    }

    //TODO: Get actual name
    getName(id, json) {
	let nodeRef = json.markedIndividualName1;
	this.addNodeMapId(id, nodeRef);
	return nodeRef;
    }

    addNodeMapId(id, nodeRef) {
	this.nodeMap[nodeRef] = id;
    }

    getNodeMapId(nodeRef) {
	return this.nodeMap[nodeRef];
    }

    addNodeDictId(nodeID, individualID){
	this.nodeDict[nodeID] = individualID;
    }

    getNodeDictId(nodeID){
	return this.nodeDict[nodeID];
    }
    
    addDataDictId(individualID, individualData){
	this.dataDict[individualID] = individualData;
    }
    
    getDataDictId(individualID){
	return this.dataDict[individualID];
    }
    
    parseLinks(json) {
	return json
	    .filter(entry => {
		let srcRef = entry.markedIndividualName1;
		let targetRef = entry.markedIndividualName2;

		return this.getNodeMapId(srcRef) &&
		    this.getNodeMapId(targetref);
	    })
	    .map(entry => {
		let srcRef = entry.markedIndividualName1;
		let targetRef = entry.markedIndividualName2;
		
		return {
		    "source": this.getNodeMapId(srcRef),
		    "target": this.getNodeMapId(targetRef),
		    "type": this.getRelationType(entry)
		}
	    });
    }

    //TODO
    getRelationType(entry) {
	return "PLACEHOLDER"
    }
}
