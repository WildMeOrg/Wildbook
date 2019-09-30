function setupSocialGraph() {
    let sg = new SocialGraph();
}

class SocialGraph() {
    constructor() {
	this.nodes = [
	    {},
	    {},
	    {},
	    {},
	    {},
	    {}
	];

	this.width = 960;
	this.height = 500;
    }

    graphSocialData() {
	let simulation = d3.forceSimulation(this.nodes)
	    .force("charge", forceManyBody())
	    .force("center", d3.forceCenter(this.width/2, this.height.2))
	    .on("tick", tick);
    }
}
