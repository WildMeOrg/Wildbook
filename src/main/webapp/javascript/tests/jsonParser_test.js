QUnit.module('JSON Link/Node Parser');

let jp, jpCopy;
QUnit.begin(() => {
    jp = new JSONParser(null, false, -1, false, true);
});

cloneJP = () => Object.assign(Object.create(Object.getPrototypeOf(jp)), jp);

//parseJSON() - Wrapper function, not tested

querySetup = {
    'beforeEach': () => {
	jpCopy = cloneJP();
	jpCopy.queryData = (name, query, isDict) => query;
	jpCopy.globals = {"baseUrl": ""};
    }
};
QUnit.module('queryNodeData()', querySetup, () => {
    QUnit.test('Local files', t => {
	jpCopy.localFiles = true;
	query = jpCopy.queryNodeData();
	t.ok(query.includes(".json"))
    });
    QUnit.test('Non-local files', t => {
	jpCopy.localFiles = false;
	query = jpCopy.queryNodeData();
	t.ok(query.includes("SELECT"))	
    });
});

QUnit.module('queryRelationshipData()', querySetup, () => {
    QUnit.test('Local files', t => {
	jpCopy.globals = {"baseUrl": ""};
	jpCopy.localFiles = true;
	query = jpCopy.queryNodeData();
	t.ok(query.includes(".json"))
    });
    QUnit.test('Non-local Files', t => {
	jpCopy.globals = {"baseUrl": ""};
	jpCopy.localFiles = false;
	query = jpCopy.queryNodeData();
	t.ok(query.includes("SELECT"))	
    });
});


//queryData() - d3.js wrapper, not tested

QUnit.module('storeQueryAsDict()', {"beforeEach": () => jpCopy = cloneJP() }, () => {
    QUnit.test('Empty JSON', t => {
	jpCopy.storeQueryAsDict({}, "A", () => true);
	t.equal(JSONParser["A"], undefined)
    });
    QUnit.test('Valid JSON', t => {
	jpCopy.storeQueryAsDict([{"individualID": "b"}], "B", () => true);
	t.equal(JSONParser["B"]["b"]["individualID"], "b")
    });
});

//TODO parseNodes()

//TODO processNodeData()

setupMap = {
    "beforeEach": () => {
	JSONParser.relationshipData = [{"markedIndividualName1": "a", "markedIndividualName2": "b"}]
	jpCopy = cloneJP();
	jpCopy.getRelationType = (el) => "type";
    }
}
QUnit.module('mapRelationships()', setupMap, () => {
    QUnit.test('Empty relationships', t => {
	JSONParser.relationshipData = [];
	let relations = jpCopy.mapRelationships();
	t.deepEqual(relations, {});
    });
    QUnit.test('Valid relationships', t => {
	let nodes = {"a": {}, "b": {}};
	let relations = jpCopy.mapRelationships(nodes);
	t.equal(relations["a"][0]["name"], "b");
	t.equal(relations["b"][0]["name"], "a");
    });
    QUnit.test('Multi-mapped relationships', t => {
	let nodes = {"a": {}, "b": {}};
	JSONParser.relationshipData.push({"markedIndividualName1": "a",
					  "markedIndividualName2": "b"});
	let relations = jpCopy.mapRelationships(nodes);
	t.equal(relations["a"].length, 2);
	t.equal(relations["b"].length, 2);
	t.equal(relations["a"][0]["name"], "b");
	t.equal(relations["b"][1]["name"], "a");
    });

});

//TODO parseLinks()

//TODO modifyOccurrenceData()

QUnit.module('getLinkId()', () => {
    QUnit.test('Unique IDs', t => {
	let ids = [];
	for (let i = 0; i < 5; i++) {
	    ids[i] = jp.getLinkId();
	}
	t.ok(ids.length === (new Set(ids)).size)
    });
});

QUnit.module('getNodeId()', () => {
    QUnit.test('Unique IDs', t => {
	let ids = [];
	for (let i = 0; i < 5; i++) {
	    ids[i] = jp.getNodeId();
	}
	t.ok(ids.length === (new Set(ids)).size)
    });
});

QUnit.module('updateNodeData()', () => {
    QUnit.test('Valid input', t => {
	let node = {};
	let updatedNode = jp.updateNodeData(node, 1, 2, 3);
	t.equal(updatedNode.id, 2);
	t.equal(updatedNode.group, 1);
	t.equal(updatedNode.depth, 3);
    });
});

//TODO getRelationshipData()

QUnit.module('getRelationType()', () => {
    QUnit.test('Maternal relation', t => {
	let link = {"markedIndividualRole1": "mother", "markedIndivdiualRole2": null}
	t.equal(jp.getRelationType(link)[0], "maternal")
    });

    QUnit.test('Paternal relation', t => {
	let link = {"markedIndividualRole1": null, "markedIndividualRole2": "father"}
	t.equal(jp.getRelationType(link)[0], "paternal")
    });

    QUnit.test('Calf relation', t => {
	let link = {"markedIndividualRole1": "calf", "markedIndivdiualRole2": "calf"}
	t.equal(jp.getRelationType(link)[0], "familial")
    });

    QUnit.test('Member relation', t => {
	let link = {"markedIndividualRole1": null, "markedIndivdiualRole2": null}
	t.equal(jp.getRelationType(link)[0], "member")
    });

    QUnit.test('Null relation', t => {
	t.equal(jp.getRelationType(null)[0], "member")
    });
});
