QUnit.module('Abstract Force Layout Interface');

//Useful for converting DOM supplied RGBs back to object hex values
function rgbToHex(rgb) {
    rgb = rgb.match(/^rgb\((\d+),\s*(\d+),\s*(\d+)\)$/);
    function hex(x) {
        return ("0" + parseInt(x).toString(16)).slice(-2);
    }
    return "#" + hex(rgb[1]) + hex(rgb[2]) + hex(rgb[3]);
}

let fa, faCopy;
QUnit.begin(() => {
    let individualID = 'mock';
    fa = new ForceLayoutAbstract(individualID);
});

//setupGraph() - Wrapper function, not tested

QUnit.module('setForces()', () => {
    QUnit.test('Default forces', t => {
	fa.setForces();
	t.ok(fa.forces.force('link'));
	t.ok(fa.forces.force('charge'));
	t.ok(fa.forces.force('collision'));
	t.ok(fa.forces.force('center'));
    });
});

let copyFaObj = {'beforeEach': () =>
		 faCopy = Object.assign(Object.create(Object.getPrototypeOf(fa)), fa) };
let defineArrowsEventHooks = Object.assign(copyFaObj, {'afterEach': () => $('#test').empty()});
QUnit.module('defineArrows()', defineArrowsEventHooks, () => {
    QUnit.test('Familial Link', t => {
	let svg = d3.select('#test');
	let linkData = [{'linkId': 0, 'type': 'familial'}];
	let mockRadius = 1;
	faCopy.getLinkTarget = () => {
	    let data = {'data': {'r': mockRadius }};
	    return data;
	}
	let mockColor = '#ff0000'
	faCopy.getLinkColor = () => mockColor;
	faCopy.defineArrows(svg, linkData);
	t.equal($('#test defs marker').attr('id'),
		'arrow' + linkData[0].linkId + ':' + faCopy.graphId);
	t.equal($('#test defs marker').attr('refX'), mockRadius);
	t.equal($('#test defs marker').attr('refY'), 0);
	t.equal($('#test defs marker').attr('markerWidth'), faCopy.markerWidth);
	t.equal($('#test defs marker').attr('markerHeight'), faCopy.markerHeight);
	t.equal($('#test defs marker').attr('orient'), 'auto');
	t.equal($('#test defs marker path').attr('fill'), mockColor);
    });

    QUnit.test('Member Link', t => {
	let svg = d3.select('#test');
	let linkData = [{'type': 'member'}];
	faCopy.defineArrows(svg, linkData);
	t.equal($('#test defs').children().length, 0);
    });
});

//updateGraph() - Wrapper function, not tested

//TODO - updateLinks()

//TODO - updateNodes()

QUnit.module('applyForces()', () => {
    QUnit.test('Default forces', t => {
	let linkData = [];
	let nodeData = [];
	fa.forces = d3.forceSimulation().force('link', d3.forceLink());
	fa.applyForces(linkData, nodeData);
	t.ok(fa.forces.on('tick'));
	t.ok(fa.forces.alpha());
	t.equal(fa.alpha, 0.2);
    });
});

QUnit.module('ticked()', () => {
    QUnit.test('Valid tick', t => {
	let source = {'x': 0, 'y': 0};
	let target = {'x': 1, 'y': 1};
	let links = {'x1': 0, 'y1': 0, 'x2': 0, 'y2': 0,
		     'source': source, 'target': target };
	let nodes = {'x': 0, 'y': 0 };
	fa.links = links;
	fa.links.attr = (field, valueFunc) => {
	    fa.links[field] = valueFunc(fa.links);
	    return fa.links;
	}
	fa.nodes = nodes;
	fa.nodes.attr = (field, valueFunc) => {
	    fa.nodes[field] = valueFunc(fa.nodes);
	    return fa.nodes;
	}
	fa.ticked(fa);
	t.equal(fa.links.x1, source.x);
	t.equal(fa.links.y1, source.y);
	t.equal(fa.links.x2, target.x);
	t.equal(fa.links.y2, target.y);
    });
});

//enableNodeInteraction() - Wrapper function, not tested

QUnit.module('dragStarted()', {'beforeEach': () => d3.event = {} }, () => {
    //Note - Heat refers to the energy (movement) of the graph
    QUnit.test('Viable low-heat drag', t => {
	d3.event = {'x': 1, 'y': 1};
	let d = {'collapsed': false};
	fa.forces = d3.forceSimulation().alphaTarget(0);
	fa.dragStarted(d);
	t.ok(fa.forces.alphaTarget() > 0);
	t.ok(d.fx, d3.event.x);
	t.ok(d.fy, d3.event.y);
    });

    QUnit.test('Viable high-heat drag', t => {
	d3.event = {'x': 1, 'y': 1, 'active': true};
	let d = {'collapsed': false};
	fa.forces = d3.forceSimulation().alphaTarget(0);
	fa.dragStarted(d);
	t.equal(fa.forces.alphaTarget(), 0);
	t.ok(d.fx, d3.event.x);
	t.ok(d.fy, d3.event.y);
    });
    
    QUnit.test('Key binding detected', t => {
	d3.event = {'x': 1, 'y': 1, 'ctrlKey': true};
	let d = {'collapsed': false};
	fa.forces = d3.forceSimulation().alphaTarget(0);
	fa.dragStarted(d);
	t.equal(fa.forces.alphaTarget(), 0);
	t.notOk(d.fx);
	t.notOk(d.fy);
    });

    QUnit.test('Collapsed node', t => {
	d3.event = {'x': 1, 'y': 1};
	let d = {'collapsed': true};
	fa.forces = d3.forceSimulation().alphaTarget(0);
	fa.dragStarted(d);
	t.equal(fa.forces.alphaTarget(), 0);
	t.notOk(d.fx);
	t.notOk(d.fy);
    });
});

QUnit.module('dragged()', {'beforeEach': () => d3.event = {} }, () => {
    //Note - Heat refers to the energy (movement) of the graph
    QUnit.test('Viable low-heat drag', t => {
	d3.event = {'x': 1, 'y': 1};
	let d = {'collapsed': false};
	fa.dragged(d);
	t.ok(d.fx, d3.event.x);
	t.ok(d.fy, d3.event.y);
    });

    QUnit.test('Viable high-heat drag', t => {
	d3.event = {'x': 1, 'y': 1, 'active': true};
	let d = {'collapsed': false};
	fa.dragged(d);
	t.ok(d.fx, d3.event.x);
	t.ok(d.fy, d3.event.y);
    });

    QUnit.test('Key binding detected', t => {
	d3.event = {'x': 1, 'y': 1, 'ctrlKey': true};
	let d = {'collapsed': false};
	fa.dragged(d);
	t.notOk(d.fx);
	t.notOk(d.fy);
    });

    QUnit.test('Collapsed node', t => {
	d3.event = {'x': 1, 'y': 1};
	let d = {'collapsed': true};
	fa.dragged(d);
	t.notOk(d.fx);
	t.notOk(d.fy);
    });
});

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

QUnit.module('handleFilter()', copyFaObj, () => {
    QUnit.test('Reset graph', t => {
	faCopy.shiftKey = () => true;
	faCopy.ctrlKey = () => true;
	faCopy.resetGraph = () => faCopy.reset = true;
	faCopy.handleFilter();
	t.ok(faCopy.reset);
    });

    QUnit.test('Filter inverse family', t => {
	faCopy.shiftKey = () => true;
	faCopy.ctrlKey = () => false;
	faCopy.filterGraph = (groupNum, nodeFilter, linkFilter, type) => {
	    if (type === 'inverse_family') faCopy.filter = 'inverse_family';
	};
	faCopy.handleFilter();
	t.equal(faCopy.filter, 'inverse_family');
    });

    QUnit.test('Filter family', t => {
	faCopy.shiftKey = () => false;
	faCopy.ctrlKey = () => true;
	faCopy.filterGraph = (groupNum, nodeFilter, linkFilter, type) => {
	    if (type === 'family') faCopy.filter = 'family';
	};
	faCopy.handleFilter();
	t.equal(faCopy.filter, 'family');
    });
});

let resetGraphEventHooks = Object.assign(copyFaObj, {'afterEach': () => $('#test').empty()});
QUnit.module('resetGraph()', resetGraphEventHooks, () => {
    QUnit.test('Full reset', t => {
	faCopy.filtered = {'test': true};
	let nodes = [{'filtered': true}];
	faCopy.svg = d3.select('#test');
	faCopy.svg.data(nodes).append('g')
	    .attr('class', 'node');
	faCopy.updateGraph = () => faCopy.updated = true;
	faCopy.resetGraph();
	t.equal(Object.keys(faCopy.filtered.test), 0);
	t.equal($('.node').length, 0);
	t.ok(faCopy.updated);
    });
});

Object.assign(copyFaObj, {'afterEach': () => {
    $('#test').empty();
    d3.selectAll('.node').remove();
}});
QUnit.module('filterGraph()', resetGraphEventHooks, () => {
    QUnit.test('Reversing filter', t => {
	faCopy.nodeData = [{'val': 0}, {'val': 1}, {'val': 2}];
	faCopy.prevNodeData = [];
	let filteredNode = {'filtered': true};
	let unfilteredNode = {'filtered': false};
	faCopy.linkData = [{'val': 0, 'source': unfilteredNode, 'target': unfilteredNode },
			   {'val': 1, 'source': unfilteredNode, 'target': unfilteredNode },
			   {'val': 2, 'source': filteredNode, 'target': filteredNode }];
	faCopy.prevLinkData = [];
	let nodeFilter = d => d.val < 1;
	let linkFilter = d => d.val < 1;
	faCopy.filtered = {'test': {1: true }}
	faCopy.svg = d3.select('#test')
	    .data(faCopy.nodeData).append('g')
	    .attr('class', 'node');
	faCopy.updateGraph = (linkData, nodeData) => faCopy.updated = true;
	faCopy.filterGraph(1, nodeFilter, linkFilter, 'test');
	faCopy.nodeData.forEach((d, idx) => {
	    if (idx === 0) t.equal(d.filtered, undefined);
	    else t.notOk(d.filtered);
	});
	t.equal(faCopy.prevNodeData.length, 2);
	t.equal(faCopy.prevNodeData[0].val, 1);
	t.equal(faCopy.prevNodeData[1].val, 2);
	t.equal(faCopy.prevLinkData.length, 1);
	t.equal(faCopy.prevLinkData[0].val, 1);
	t.equal($('.node').length, 1);
	t.ok(faCopy.updated);
    });

    QUnit.test('Applying filter', t => {
	faCopy.nodeData = [{'val': 0}, {'val': 1}, {'val': 2}];
	faCopy.prevNodeData = faCopy.nodeData;
	let filteredNode = {'filtered': true};
	let unfilteredNode = {'filtered': false};
	faCopy.linkData = [{'val': 0, 'source': unfilteredNode, 'target': unfilteredNode },
			   {'val': 1, 'source': unfilteredNode, 'target': unfilteredNode },
			   {'val': 2, 'source': filteredNode, 'target': filteredNode }];
	faCopy.prevLinkData = faCopy.linkData;
	let nodeFilter = d => d.val < 1;
	let linkFilter = d => d.val < 1;
	faCopy.filtered = {'test': {}}
	faCopy.updateGraph = (linkData, nodeData) => faCopy.updated = true;
	faCopy.filterGraph(1, nodeFilter, linkFilter, 'test');
	t.ok(faCopy.filtered.test[1]);
	faCopy.nodeData.forEach((d, idx) => {
	    if (idx >= 1) t.ok(d.filtered);
	    else t.notOk(d.filtered);
	});
	t.equal(faCopy.prevNodeData.length, 1);
	t.equal(faCopy.prevNodeData[0].val, 0);
	t.equal(faCopy.prevLinkData.length, 1);
	t.equal(faCopy.prevLinkData[0].val, 0);
	t.ok(faCopy.updated);
	
    });
});

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
