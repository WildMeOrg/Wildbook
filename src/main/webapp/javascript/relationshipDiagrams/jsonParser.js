class JSONParser {
    constructor() {
	this.currId = 0;
	this.nodeMap = {};
	this.nodeDict = [];
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
	    d3.json(query, (error, json) => this.createDictionary(json));
    }

    //creates the dictionary
    //TODO: remove debugging stuff
    createDictionary(json) {
	var sizeOfJSON = json.length;//number of MARKEDINDIVIDUALS
	console.log("size of json is " + sizeOfJSON);
	
	if(sizeOfJSON >= 1){//if there is anything in the query result
	    console.log(json);
	    
	    var nodeID;//to store the current id

	    for (var i = 0; i < sizeOfJSON; i++) {//iterate over all MARKEDINDIVIDUALS

		console.log("inside the loop that creates the dict");

		nodeID = this.getId();//each MARKEDINDIVIDUAL has a unique id
		
		this.nodeDict.push({//creating a new dictionary pair with unique incrementing node id
		    key: nodeID,
		    value: json[i].individualID
		});

	    }
	}
	else{
	    console.log("there is no json data");//if there is nothing in the array
	}
	

	console.log("before print");
	console.log(this.nodeDict);
	
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
