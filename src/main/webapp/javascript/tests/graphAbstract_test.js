//TODO: Blacklist this entire folder
QUnit.module('Abstract Graph Interface');

let ga;
QUnit.begin(() => {
    let individualID = 'mock';
    ga = new GraphAbstract(individualID);
});

let terracedTestEventHooks = {
    'before': () => {
	$('#test').append('<div id="testA" class="active"></div>' +
			  '<div id="testB" style="display: none"></div>');
    },
    'after': () => $('#test').empty()
};
QUnit.module('showTable()', terracedTestEventHooks, () => {
    QUnit.test('Display table', t => {
	ga.showTable('#testA', '#testB');
	t.ok($('#testA').is(':hidden'));
	t.ok(!$('#testA').hasClass('active'));
	t.ok($('#testB').is(':visible'));
	t.ok($('#testB').hasClass('active'));
    });
});

//setupGraph() - Wrapper function, not tested

QUnit.module('addSvg()', {'after': () => $('#test').empty() }, () => {
    QUnit.test('Append SVG', t => {
	ga.addSvg('#test');
	t.equal($('.container').attr('width'), ga.width);
	t.equal($('.container').attr('height'), ga.height);
	t.ok($('.container').has('g'))
    });
});

//TODO - addLegend(), first this function needs to be implemented

QUnit.module('addTooltip()', {'after': () => $('#test').empty() }, () => {
    QUnit.test('Append tooltip', t => {
	ga.addTooltip('#test');
	t.ok(ga.tooltip);
	t.equal($('.tooltip').css('opacity'), 0);
    });
});

//TODO - Needs work
/*QUnit.module('drawNodeOutlines()', terracedTestEventHooks, () => {
    QUnit.test('Create Node', t => {
	ga.drawNodeOutlines("#test");
	
    });
    });*/

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

//TODO - drawNodeSymbols()

//TODO - addNodeText()
/*let mockedNode;
QUnit.module('addNodeText()', {'beforeEach': mockedNode = d3.select('body')}, () => {
    QUnit.test('Add hidden text', t => {
	let data = [{'data': {'name': 'a'}}];
	let mockedDataNode = mockedNode.data(data).enter();
	let isHidden = true;
	ga.addNodeText(mockedDataNode, isHidden);
	t.ok($('body.text'));
	console.log($('body.text'));
	t.ok($('body.text').css('fill-opacity') === 0); 
    });
});*/

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

QUnit.module('incompleteInfoMessage()', {'after': () => $('#test').empty() }, () => {
    QUnit.test('Display message', t => {
	ga.incompleteInfoMessage('#test');
	t.equal($('#incompleteInfoMsg').text(), 'There are currently no known relationships ' +
		'for this Marked Individual');
    });
});

QUnit.module('setNodeRadius()', () => {
    QUnit.test('Empyt node list', t => {
	let nodes = [];
	ga.setNodeRadius(nodes, nodes);
	t.equal(nodes.length, 0);
    });

    QUnit.test('List of length 2', t => {
	let nodes = [{'data': {} }, {'data': {} }];
	ga.setNodeRadius(nodes, nodes);
	nodes.forEach(d => t.equal(d.data.r, ga.radius));
    });
});

QUnit.module('calcNodeSize()', {'beforeEach': () => ga.radius = null}, () => {
    function checkRadius(test, ga) {
	test.ok(ga.radius < ga.maxRadius);
	test.ok(ga.radius > 0); //TODO: Consider shifting implementation to have min radius 
    }

    QUnit.test('Empty node list', t => {
	let nodes = [];
	let radius = ga.calcNodeSize(nodes);
	checkRadius(t, ga);
    });

    QUnit.test('List of length 5', t => {
	let nodes = {'length': 5};	
	let radius = ga.calcNodeSize(nodes);
	checkRadius(t, ga);
    });

    QUnit.test('Large node list of length 1000', t => {
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

QUnit.module('getLinkColor()', () => {
    QUnit.test('Familial link', t => {
	let d = {'type': 'familial'};
	let color = ga.getLinkColor(d);
	t.equal(color, ga.famLinkColor);
    });
    
    QUnit.test('Paternal link', t => {
	let d = {'type': 'paternal'};
	let color = ga.getLinkColor(d);
	t.equal(color, ga.paternalLinkColor);
    });

    QUnit.test('Maternal link', t => {
	let d = {'type': 'maternal'};
	let color = ga.getLinkColor(d);
	t.equal(color, ga.maternalLinkColor);
    });

    QUnit.test('Default link', t => {
	let d = {'type': 'default'};
	let color = ga.getLinkColor(d);
	t.equal(color, ga.defLinkColor);
    });

    QUnit.test('Null entry', t => {
	let d = null;
	let color = ga.getLinkColor(d);
	t.equal(color, ga.defLinkColor);
    });
});

QUnit.module('getSizeScalar()', () => {
    QUnit.test('Focused node', t => {
	let d = {'data': {'isFocused': true }};
	let scalar = ga.getSizeScalar(d);
	t.equal(scalar, ga.focusedScale);
    });
    
    QUnit.test('Unfocused node', t => {
	let d = {'data': {'isFocused': false }};
	let scalar = ga.getSizeScalar(d);
	t.equal(scalar, ga.focusedScale);
    });
    
    QUnit.test('Null entry', t => {
	let d = null;
	let scalar = ga.getSizeScalar(d);
	t.equal(scalar, ga.focusedScale);
    });
});

let handleMouseOverEventHooks = {
    'before': () => {
	ga.addTooltip('#test');
	d3.event = {'layerX': 0, 'layerY': 0};
    },
    'after': () => $('#test').empty()
}

//TODO - Currently broken due to async issues
/*QUnit.module('handleMouseOver()', handleMouseOverEventHooks, () => {
    QUnit.test('Entering Node', t => {
	ga.popup = false;
	ga.handleMouseOver();
	t.equal($('.tooltip').html(), '<b>Encounters:</b>\n None');
	t.ok(ga.popup);
    });

    QUnit.test('Inside Node', t => {
	ga.popup = true;
	ga.handleMouseOver();
	t.ok(ga.popup);
	t.equal($('.tooltip').css('opacity'), 0)
    });
});*/

//TODO - handleMouseOut()
