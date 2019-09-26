//TODO: Add jest
setupTests() {
  
}

//Assumed tree object dt
describe('calcNode() provides nodes of sizes less than the maxRadius... ', () => {
    test('when treating node lists of length 0', () => {
	let nodes = {'length': 0};

	let radius = dt.calcNodeSize(nodes);
	expect(radius).toBeLessThan(dt.max_radius); //Dummied dt
	expect(radius).toBeGreaterThan(0);
    });

    test('when treating node lists of length 5', () => {
	let nodes = {'length': 5};
	
	let radius = dt.calcNodeSize(nodes);
	expect(radius).toBeLessThan(dt.max_radius); //Dummied dt
	expect(radius).toBeGreaterThan(0);
    });

    test('when treating large node lists of length 1000', () => {
	let nodes = {'length': 1000};

	let radius = dt.calcNodeSize(nodes);
	expect(radius).toBeLessThan(dt.max_radius); //Dummied dt
	expect(radius).toBeGreaterThan(0);
    });

    test('when treating non-list entries', () => {
	let nodes = null;

	let radius = dt.calcNodesSize(nodes);
	expect(radius).toBeLessThan(dt.max_radius); //Dummied dt
	expect(radius).toBeGreaterThan(0);
    });
});

//TODO: Consider checking for just hex value
//Assumed tree object dt
describe('colorCollapsed() provides node fill coloration for... ', () => {
    test('non-collapsed children nodes', () => {
	let d = {'_children': true};
	
	let color = dt.colorCollapsed(d);
	expect(color).toEqual(dt.collapsedNodeColor);
    });

    test('collapsed children nodes', () => {
	let d = {};

	let color = dt.colorCollapsed(d);
	expect(color).toEqual(dt.nodeColor);
    });
});

describe('colorGender() provides node stroke coloration for... ', () => {
    test('male nodes', () => {
	let d = {'gender': 'male'};

	let color = dt.colorGender(d);
	expect(color).toEqual(dt.maleColor);
    });

    test('female nodes', () => {
	let d = {'gender': 'female'};

	let color = dt.colorGender(d);
	expect(color).toEqual(dt.femaleColor);
    });

    test('default gender nodes', () => {
	let d = {};

	let color = dt.colorGender(d);
	expect(color).toEqual(dt.defGenderColor);
    });
});
