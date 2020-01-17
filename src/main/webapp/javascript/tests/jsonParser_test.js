QUnit.module('JSON Link/Node Parser');

let jp;
QUnit.begin(() => {
    jp = new JSONParser();
});

//TODO parseJSON()

//TODO queryNodeData()

//TODO queryRelationshipData()

//TODO queryData()

//TODO storeQueryAsDict()

//TODO parseNodes()

//TODO processNodeData()

//TODO mapRelationships()

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

//TODO updateNodeData()

//TODO getNodeData()

//TODO getNodeById()

//TODO getRelationshipData()

QUnit.module('getRelationType()', () => {
    QUnit.test('Maternal relation', t => {
	let link = {"markedIndividualRole1": "mother", "markedIndivdiualRole2": null}
	t.equal(jp.getRelationType(link), "maternal")
    });

    QUnit.test('Paternal relation', t => {
	let link = {"markedIndividualRole1": null, "markedIndividualRole2": "father"}
	t.equal(jp.getRelationType(link), "paternal")
    });

    QUnit.test('Calf relation', t => {
	let link = {"markedIndividualRole1": "calf", "markedIndivdiualRole2": "calf"}
	t.equal(jp.getRelationType(link), "familial")
    });

    QUnit.test('Member relation', t => {
	let link = {"markedIndividualRole1": null, "markedIndivdiualRole2": null}
	t.equal(jp.getRelationType(link), "member")
    });

    QUnit.test('Null relation', t => {
	t.equal(jp.getRelationType(null), "member")
    });
});
