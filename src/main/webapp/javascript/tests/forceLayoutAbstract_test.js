QUnit.module('Abstract Force Layout Interface');

//Useful for converting DOM supplied RGBs back to object hex values
function rgbToHex(rgb) {
    rgb = rgb.match(/^rgb\((\d+),\s*(\d+),\s*(\d+)\)$/);
    function hex(x) {
        return ("0" + parseInt(x).toString(16)).slice(-2);
    }
    return "#" + hex(rgb[1]) + hex(rgb[2]) + hex(rgb[3]);
}

let fa;
QUnit.begin(() => {
    let individualID = 'mock';
    fa = new ForceLayoutAbstract(individualID);
});

//TODO - setupGraph()

QUnit.module('setForces()', () => {
    QUnit.test('Default forces', t => {
	fa.setForces();
	t.ok(fa.forces.force('link'));
	t.ok(fa.forces.force('charge'));
	t.ok(fa.forces.force('collision'));
	t.ok(fa.forces.force('center'));
    });
});

//TODO - defineArrows()

//TODO - updateGraph()

//TODO - updateLinks()

//TODO - updateNodes()

//TODO - applyForces()

//TODO - ticked()

//TODO - enableNodeInteraction()

//TODO - dragStarted()

//TODO - dragged()

let releaseNodeEventHooks = {
    'beforeEach': () => $('#test').empty(),
    'after': () => $('#test').empty(),
};
QUnit.module('dragEnded()', releaseNodeEventHooks, () => {
    //Note - Heat refers to the energy (movement) of the graph
    QUnit.test('Viable low-heat drag', t => {
	d3.event = {};
	let d = {'collapsed': false};
	let node = d3.select('#test').append('circle')
	    .style('fill', 'red');
	fa.forces = d3.forceSimulation().alphaTarget(0.5);
	fa.dragEnded(d, '#test');
	t.equal(rgbToHex($('#test circle').css('fill')), fa.fixedNodeColor);
	t.equal(fa.forces.alphaTarget(), 0);
    });

    QUnit.test('Viable high-heat drag', t => {
	d3.event = {'active': true};
	let d = {'collapsed': false};
	let node = d3.select('#test').append('circle')
	    .style('fill', 'red');
	fa.forces = d3.forceSimulation().alphaTarget(0.5);
	fa.dragEnded(d, '#test');
	t.equal(rgbToHex($('#test circle').css('fill')), fa.fixedNodeColor);
	t.equal(fa.forces.alphaTarget(), 0.5);
    });
    
    QUnit.test('Key binding detected', t => {
	d3.event = {'ctrlKey': true};
	let d = {'collapsed': false};
	let node = d3.select('#test').append('circle')
	    .style('fill', '#ff0000');
	fa.forces = d3.forceSimulation().alphaTarget(0.5);
	fa.dragEnded(d, '#test');
	t.equal(rgbToHex($('#test circle').css('fill')), '#ff0000')
	t.equal(fa.forces.alphaTarget(), 0.5);
    });

    QUnit.test('Collapsed node', t => {
	d3.event = {};
	let d = {'collapsed': true};
	let node = d3.select('#test').append('circle')
	    .style('fill', '#ff0000');
	fa.forces = d3.forceSimulation().alphaTarget(0.5);
	fa.dragEnded(d, '#test');
	t.equal(rgbToHex($('#test circle').css('fill')), '#ff0000')
	t.equal(fa.forces.alphaTarget(), 0.5);	
    });
});

QUnit.module('releaseNode()', releaseNodeEventHooks, () => {
    QUnit.test('Fixed node', t => {
	let d = {'fx': 1, 'fy': 2}
	let node = d3.select('#test').append('circle')
	    .style('fill', 'red');
	fa.releaseNode(d, '#test');
	t.equal(d.fx, null);
	t.equal(d.fy, null);
	t.equal(rgbToHex($('#test circle').css('fill')), fa.defNodeColor);
    });
});

//TODO - handleFilter()

//TODO - resetGraph()

//TODO - filterGraph()

QUnit.module('isAssignedKeyBinding()', {'beforeEach': () => d3.event = {} }, () => {
    QUnit.test('shiftKey', t => {
	d3.event.shiftKey = true;
	t.ok(fa.isAssignedKeyBinding());
    });
    
    QUnit.test('ctrlKey', t => {
	d3.event.sourceEvent = {'ctrlKey': true };
	t.ok(fa.isAssignedKeyBinding());
    });

    QUnit.test('No key', t => {
	t.notOk(fa.isAssignedKeyBinding());
    });
});

QUnit.module('shiftKey()', {'beforeEach': () => d3.event = {} }, () => {
    QUnit.test('Event shiftKey', t => {
	d3.event.shiftKey = true;
	t.ok(fa.shiftKey());
    });
    
    QUnit.test('Source event shiftKey', t => {
	d3.event.sourceEvent = {'shiftKey': true };
	t.ok(fa.shiftKey());
    });

    QUnit.test('No shiftKey', t => {
	t.notOk(fa.shiftKey());
    });
});

QUnit.module('ctrlKey()', {'beforeEach': () => d3.event = {} }, () => {
    QUnit.test('Event ctrlKey', t => {
	d3.event.ctrlKey = true;
	t.ok(fa.ctrlKey());
    });
    
    QUnit.test('Source event ctrlKey', t => {
	d3.event.sourceEvent = {'ctrlKey': true };
	t.ok(fa.ctrlKey());
    });

    QUnit.test('No ctrlKey', t => {
	t.notOk(fa.ctrlKey());
    });
});

QUnit.module('getLinkLen()', {'before': () => fa.radius = 5 }, () => {
    QUnit.test('Reasonable length (3)', t => {
	let link = {'data': {'r': 3 }};
	let len = fa.getLinkLen(link);
	t.equal(len, 6);
    });
    
    QUnit.test('Unreasonable length (1000)', t => {
	let link = {'data': {'r': 1000 }};
	let len = fa.getLinkLen(link);
	t.equal(len, fa.radius * fa.maxLenScalar);
    });
});

QUnit.module('getLinkTarget()', () => {
    QUnit.test('Valid target', t => {
	let link = {'target': 'c'};
	fa.nodeData = [{'id': 'a'}, {'id': 'b'}, {'id': 'c'}];
	let targetNode = fa.getLinkTarget(link);
	t.equal(targetNode, fa.nodeData[2]);
    });
    
    QUnit.test('Invalid target', t => {
	let link = {'target': 'b'};
	fa.nodeData = [{'id': '1'}, {'id': '2'}, {'id': '3'}];
	let targetNode = fa.getLinkTarget(link);
	t.equal(targetNode, undefined);
    });
});

QUnit.module('getLinkSource()', () => {
    QUnit.test('Valid source', t => {
	let link = {'source': 'b'};
	fa.nodeData = [{'id': 'a'}, {'id': 'b'}, {'id': 'c'}];
	let srcNode = fa.getLinkSource(link);
	t.equal(srcNode, fa.nodeData[1]);
    });
    
    QUnit.test('Invalid source', t => {
	let link = {'source': 'a'};
	fa.nodeData = [{'id': '1'}, {'id': '2'}, {'id': '3'}];
	let srcNode = fa.getLinkSource(link);
	t.equal(srcNode, undefined);
    });
});
