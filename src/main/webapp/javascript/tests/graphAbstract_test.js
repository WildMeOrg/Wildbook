//TODO: Blacklist this entire folder
QUnit.module('Abstract Graph Interface');

let ga;
QUnit.begin(() => {
    let individualID = 'mock';
    ga = new GraphAbstract(individualID);
});

QUnit.module('wheelDelta()', {'before': () => d3.event = {'deltaY': 0, 'deltaMode': 1}}, () => {
    QUnit.test('Zoom unchanged', t => {
	d3.event.deltaY = 0;
	let delta = ga.wheelDelta();
	t.equal(delta, 0);
    });

    QUnit.test('Zooming in', t => {
	d3.event.deltaY = 1;
	let delta = ga.wheelDelta();
	t.ok(delta < 0); 
    });

    QUnit.test('Zooming out', t => {
	d3.event.deltaY = -1;
	let delta = ga.wheelDelta();
	t.ok(delta > 0); 
    });
});

QUnit.module('calcNodeSize()', {'beforeEach': () => ga.radius = null}, () => {
    function checkRadius(test, ga) {
	test.ok(ga.radius < ga.maxRadius);
	test.ok(ga.radius > 0); //TODO: Consider shifting implementation to have min radius 
    }

    QUnit.test('Empty node lists', t => {
	let nodes = [];
	let radius = ga.calcNodeSize(nodes);
	checkRadius(t, ga);
    });
	
	QUnit.test('List of length 1', t => {
	let nodes = {'length': 1};
	let radius = ga.calcNodeSize(nodes);
	checkRadius(t, ga);
    });
	
	QUnit.test('List of length 0', t => {
	let nodes = {'length': 0};
	let radius = ga.calcNodeSize(nodes);
	checkRadius(t, ga);
    });

    QUnit.test('Lists of length 5', t => {
	let nodes = {'length': 5};	
	let radius = ga.calcNodeSize(nodes);
	checkRadius(t, ga);
    });

    QUnit.test('Large node lists of length 1000', t => {
	let nodes = {'length': 1000};
	let radius = ga.calcNodeSize(nodes);
	checkRadius(t, ga);
    });

    QUnit.test('Non-list entries', t => {
	let nodes = null;
	let radius = ga.calcNodeSize(nodes);
	t.notOk(ga.radius)
    });
});

QUnit.module('addTooltip()', () => {
    QUnit.test('Valid selector', t => {
	ga.addTooltip("body");
	t.ok(ga.tooltip);
	t.equal($('.tooltip').css('opacity'), 0);
    });
});

//TODO: Add functionality
QUnit.module('handleMouseOver()', () => {
    QUnit.test('hover makes tooltip visible', t => {
	ga.addTooltip("body");
	t.equal($('.tooltip').css('opacity'), .9);
    });
});

//TODO: Add functionality
QUnit.module('handleMouseOut()', () => {
    QUnit.test('', t => {
	ga.addTooltip("body");
	t.equal($('.tooltip').css('opacity'), 0);
    });
});

QUnit.module('colorCollapsed()', () => {
    function validateColor(test, color, colorRef) {
	test.strictEqual(color, colorRef);
    }
    
    QUnit.test('Non-collapsed children nodes', t => {
	let d = {'_children': true};
	let color = ga.colorCollapsed(d);
	validateColor(t, color, ga.collapsedNodeColor);
    });

    QUnit.test('Collapsed children nodes', t => {
	let d = {};
	let color = ga.colorCollapsed(d);
	validateColor(t, color, ga.defNodeColor);
    });
});

QUnit.module('colorGender()', () => {
    function validateColor(test, color, colorRef) {
	test.strictEqual(color, colorRef);
    }

    QUnit.test('Male nodes', t => {
	let d = {'data': {'gender': 'male'}};
	let color = ga.colorGender(d);
	validateColor(t, color, ga.maleColor);
    });

    QUnit.test('Female nodes', t => {
	let d = {'data': {'gender': 'female'}};
	let color = ga.colorGender(d);
	validateColor(t, color, ga.femaleColor);
    });

    QUnit.test('Default gender nodes', t => {
	let d = {'data': {}};
	let color = ga.colorGender(d);
	validateColor(t, color, ga.defGenderColor);
    });
});

let mockedNode;
QUnit.module('addNodeText()', {'beforeEach': mockedNode = d3.select("body")}, () => {
    QUnit.test('Add hidden text', t => {
	let data = [{'data': {'name': 'a'}}];
	let mockedDataNode = mockedNode.data(data).enter();
	let isHidden = true;
	ga.addNodeText(mockedDataNode, isHidden);
	t.ok($('body.text'));
	console.log($('body.text'));
	t.ok($('body.text').css('fill-opacity') === 0); 
    });
});

let mockedClick;
QUnit.module('click()', {
    'before': () => mockedClick = ga.click.bind({'updateTree': d => true})
}, () => {
    QUnit.test('Collapsed node', t => {
	let d = {'children': false, '_children': true};
	mockedClick(d);
	t.ok(d.children);
	t.notOk(d._children);
    });
		 
    QUnit.test('Non-collapsed node', t => {
	let d = {'children': true, '_children': false};
	mockedClick(d);
	t.ok(d._children);
	t.notOk(d.children);
    });

    QUnit.test('Child-less node', t => {
	let d = {};
	mockedClick(d);
	t.notOk(d.children);
	t.notOk(d._children);
    });
});
