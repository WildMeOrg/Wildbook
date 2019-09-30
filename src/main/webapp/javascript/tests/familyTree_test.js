//TODO: Blacklist this entire folder
QUnit.module('Family Tree');
QUnit.moduleStart

let ft;
QUnit.begin(() => {
    let individualID = 'mock';
    ft = new FamilyTree(individualID);
});

QUnit.module('calcNode()', {'beforeEach': () => ft.radius = null}, () => {
    function checkRadius(test, ft) {
	test.ok(ft.radius < ft.maxRadius);
	test.ok(ft.radius > 0); //TODO: Consider shifting implementation to have min radius 
    }

    QUnit.test('Empty node lists', t => {
	let nodes = [];
	let radius = ft.calcNodeSize(nodes);
	checkRadius(t, ft);
    });

    QUnit.test('Lists of length 5', t => {
	let nodes = {'length': 5};	
	let radius = ft.calcNodeSize(nodes);
	checkRadius(t, ft);
    });

    QUnit.test('Large node lists of length 1000', t => {
	let nodes = {'length': 1000};
	let radius = ft.calcNodeSize(nodes);
	checkRadius(t, ft);
    });

    QUnit.test('Non-list entries', t => {
	let nodes = null;
	let radius = ft.calcNodeSize(nodes);
	t.notOk(ft.radius)
    });
});

QUnit.module('colorCollapsed()', () => {
    function validateColor(test, color, colorRef) {
	test.strictEqual(color, colorRef);
    }
    
    QUnit.test('Non-collapsed children nodes', t => {
	let d = {'_children': true};
	let color = ft.colorCollapsed(d);
	validateColor(t, color, ft.collapsedNodeColor);
    });

    QUnit.test('Collapsed children nodes', t => {
	let d = {};
	let color = ft.colorCollapsed(d);
	validateColor(t, color, ft.nodeColor);
    });
});

QUnit.module('colorGender()', () => {
    function validateColor(test, color, colorRef) {
	test.strictEqual(color, colorRef);
    }

    QUnit.test('Male nodes', t => {
	let d = {'gender': 'male'};
	let color = ft.colorGender(d);
	validateColor(t, color, ft.maleColor);
    });

    QUnit.test('Female nodes', t => {
	let d = {'gender': 'female'};
	let color = ft.colorGender(d);
	validateColor(t, color, ft.femaleColor);
    });

    QUnit.test('Default gender nodes', t => {
	let d = {};
	let color = ft.colorGender(d);
	validateColor(t, color, ft.defGenderColor);
    });
});

/*QUnit.module('click()', () => {
    QUnit.test('Toggle collapsed nodes', t => {
	let d = {};
	let 
    });
});*/
