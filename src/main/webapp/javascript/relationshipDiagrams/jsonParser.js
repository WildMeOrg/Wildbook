class JSONParser {
    constructor() {
	this.graphId = 0;//id that will increment with each relevant MARKEDINDIVIDUAL
	this.nodeMap = {};//map of the MARKEDINDIVIDUALS to be displayed on the graph
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
	    let id = this.getGraphId();
	    let nodeRef = json.markedIndividualName1;
	    this.addNodeMapId(id, nodeRef);
	    return {
		"id": id,
		//"group": //Not in use currently
		"data": {
		    "name": this.getDisplayName(id),
		    "gender": this.getGender(id),
		    "genus": this.getGenus(id)
		    
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
	    for (var i = 0; i < sizeOfJSON; i++) {//iterate over all MARKEDINDIVIDUALS
		console.log("inside the loop that creates the dict");
		this.dataDict[json[i].individualID] = json[i];//adding to dictionary of data referencable by individualID
	    }
	}
	else{
	    console.log("there is no json data");//if there is nothing in the array
	}
	
	//printing contents of dictionaries for testing
	console.log(this.dataDict);
    }

    getGraphId() {
	return this.graphId++;
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

    addNodeMapId(id, nodeRef) {
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
