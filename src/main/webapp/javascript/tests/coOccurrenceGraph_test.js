QUnit.module('Co-Occurrence Graph Interface');

let cg, cgCopy;
QUnit.begin(() => {
    let individualId = "mock"
    cg = new OccurrenceGraph(individualId);
});

cloneCG = () => Object.assign(Object.create(Object.getPrototypeOf(cg)), cg);

let nodeMinTypeHook = {
    "beforeEach": () => {
	cgCopy = cloneCG();
	cgCopy.getNodeMin = (node1, node2) => [0, 1];
    }
}
QUnit.module('getNodeMinType()', nodeMinTypeHook, () => {
    QUnit.test('Spatial', t => {
	let dist = cgCopy.getNodeMinType({}, {}, "spatial");
	t.equal(dist, 0);
    });
    QUnit.test('Temporal', t => {
	let time = cgCopy.getNodeMinType({}, {}, "temporal");
	t.equal(time, 1);
    });
    QUnit.test('Invalid type', t => {
	let val = cgCopy.getNodeMinType({}, {}, null);
	t.notOk(val);
    });
});

QUnit.module('getSightingsData()', () => {
    QUnit.test('Empty data', t => {
	let data = cg.getSightingsData([]);
	t.equal(data.length, 0);
    });

    QUnit.test('Valid data', t => {
	let sightings = [{"location": {"lat": 0, "lon": 1}, "time": {"datetime": 2}}];
	let data = cg.getSightingsData(sightings);
	t.equal(data.length, 1);
	t.equal(data[0].lat, 0);
    });
});

QUnit.module('getNodeMinBruteForce()', () => {
    QUnit.test('Valid data', t => {
	let arr1 = [{"a": 1, "b": 2}, {"a": 3, "b": 4}];
	let arr2 = [{"a": 5, "b": 8}, {"a": 4, "b": 7}];
	let diffFunc = (x, y) => Math.abs(x.a - y.a) + Math.abs(x.b - y.b);
	let min = cg.getNodeMinBruteForce(arr1, arr2, diffFunc);
	t.equal(min, 4);
    });
});

QUnit.module('getNodeMinKDTree()', () => {
    QUnit.test('Valid data', t => {
	let arr1 = [{"a": 1, "b": 2}, {"a": 3, "b": 4}];
	let arr2 = [{"a": 5, "b": 8}, {"a": 4, "b": 7}];
	let diffFunc = (x, y) => Math.abs(x.a - y.a) + Math.abs(x.b - y.b);
	let [min, coordPair] = cg.getNodeMinKDTree(arr1, arr2, ["a", "b"], diffFunc);
	t.equal(min, 4);
    });
});

QUnit.module('calculateDist()', () => {
    QUnit.test('Valid distances', t => {
	let node1Loc = {
	    "lon": 10,
	    "lat": 0
	}
	let node2Loc = {
	    "lon": 0,
	    "lat": 0
	}
	t.equal(cg.calculateDist(node1Loc, node2Loc), 10)
    });

    QUnit.test('Invalid distances', t => {
	t.equal(cg.calculateDist(null, 10), -1)
    });
});

QUnit.module('calculateTime()', () => {
    QUnit.test('Valid times', t => {
	t.equal(cg.calculateTime(10, 0), 10)
    });

    QUnit.test('Invalid times', t => {
	t.equal(cg.calculateTime(10, null), -1)
    });
});

let linearInterpEventHooks = {
    'before': () => cg.id = "a",
    'after': () => cg.id = null
}
QUnit.module('linearInterp()', linearInterpEventHooks, () => {
    QUnit.test('Valid x-axis interpolation', t => {
	let link = {
	    'source': {
		'data': {
		    'individualID': "a"
		},
		'x': 10
	    },
	    'target': {
		'x': 0
	    }
	};
	t.equal(cg.linearInterp(link, "x"), 4);
    });

    QUnit.test('Valid y-axis interpolation', t => {
	let link = {
	    'source': {
		'data': {
		    'individualID': "a"
		},
		'y': 0
	    },
	    'target': {
		'y': 10
	    }
	};
	t.equal(cg.linearInterp(link, "y"), 6);
    });
    
    QUnit.test('Invalid link data', t => {
	t.equal(cg.linearInterp(null, "z"), -1)
    });
});

QUnit.module('focusedNode()', () => {
    QUnit.test('Verify no-op', t => {
	let val = cg.focusNode();
	t.notOk(val);
    });
});
