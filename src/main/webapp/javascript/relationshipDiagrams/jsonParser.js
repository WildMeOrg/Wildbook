class JSONParser {
    constructor() {
	this.currId = 0;
	this.nodeMap = {};
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

    getNodeRefs(json) {
	
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
