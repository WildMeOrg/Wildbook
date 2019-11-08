QUnit.module('Abstract Force Layout Interface');


let fa;
QUnit.begin(() => {
    let individualID = 'mock';
    fa = new ForceLayoutAbstract(individualID);
});


QUnit.module('getLinkLen()', () => {
  QUnit.test('Reasonable length (3)', t => {
    let link = {'data': {'r': 3 }};
    let len = fa.getLinkLen(link);
    t.test(len, 6);
  });
  
  QUnit.test('Unreasonable length (1000)', t => {
    let link = {'data': {'r': 1000 }};
    let len = fa.getLinkLen(link);
    t.test(len, ga.radius * ga.maxLenScalar);
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
