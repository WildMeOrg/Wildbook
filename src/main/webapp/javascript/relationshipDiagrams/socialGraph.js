//TODO List

//Add "freeze" button to stop graph ticks

function setupSocialGraph(individualID) {
    let sg = new SocialGraph(individualID);
    sg.graphSocialData(false, [0,0]); //Dummied method
}

class SocialGraph extends GraphAbstract {
    constructor(individualID) {
	super(individualID);
	
	//TODO: Parse this data
	//It would be really great if some clever heirarchical representation could be used
	//to represent this - that way one format can be used for all graph DATA
	this.nodes = [
	    {
		"id": 0,
		"group": 0,
		"data": {
		    "name": "Lion A",
		    "gender": "female",
		    "role": "alpha",
		    "isFocus": true
		}
	    },
	    {
		"id": 1,
		"group": 0,
		"data": {
		    "name": "Lion B",
		    "gender": "female"
		}
	    },
	    {
		"id": 2,
		"group": 1,
		"data": {
		    "name": "Lion C",
		    "gender": "male"
		}
	    },
	    {
		"id": 3,
		"group": 2,
		"data": {
		    "name": "Lion D",
		    "gender": ""
		}
	    },
	    {
		"id": 4,
		"group": 2,
		"data": {
		    "name": "Lion E",
		    "gender": "female"
		}
	    },
	    {
		"id": 5,
		"group": 2,
		"data": {
		    "name": "Lion F",
		    "gender": "male"
		}
	    }
	];

	this.links = [
	    {"source": 0, "target": 1, "type": "familial"},
	    {"source": 0, "target": 3, "type": "member"},
	    {"source": 0, "target": 5, "type": "familial"},
	    {"source": 3, "target": 4, "type": "familial"},
	    {"source": 4, "target": 5, "type": "member"},
	    {"source": 5, "target": 3, "type": "member"},
	    {"source": 2, "target": 1, "type": "member"}
	];
    }

    graphSocialData(error, json) {
	if (error) {
	    return console.error(error);
	}

	if (json.length >= 1) {
	    this.svg = d3.select("#socialDiagram").append("svg")
		.attr("width", this.width)
		.attr("height", this.height)
		.call(this.zoom.on("zoom", () => {
		    this.svg.attr("transform", d3.event.transform)
		}))
		.append("g");

	    //Calculate node radius
	    this.calcNodeSize(this.nodes);
	    
	    let sim = d3.forceSimulation()
		.force("link", d3.forceLink().id(d => d.id))
		.force("charge", d3.forceManyBody())
		.force("collision", d3.forceCollide().radius(this.radius * 2)) 
		.force("center", d3.forceCenter(this.width/2, this.height/2));
	    
	    let linkRef = this.svg
		//.append("g")
		//
		.selectAll("line")
		.data(this.links)
		.enter().append("line")
		.attr("stroke", d => {
		    console.log(d);
		    return (d.type === "familial") ? this.famLinkColor : this.defLinkColor;
		})
		.attr("stroke-width", 2);
	    
	    let nodeRef = this.svg
		//.append("g")
		//
		.selectAll("g")
		.data(this.nodes)
		.enter().append("g")
		.attr("class", "node")
	    	.on("mouseover", d => this.handleMouseOver(d))					
		.on("mouseout", d => this.handleMouseOut(d));

	    let circles = this.drawNodeOutlines(nodeRef, false);
	    this.drawNodeSymbols(nodeRef, false);
	    this.addNodeText(nodeRef, false);

	    //circles.attr("fill", d => this.color(d.group))
	    circles.call(d3.drag()
			 .on("start", d => this.dragStarted(d, sim))
			 .on("drag", d => this.dragged(d, sim))
			 .on("end", d => this.dragEnded(d, sim)));

	    this.addTooltip("#socialDiagram");

	    sim.nodes(this.nodes)
		.on("tick", () => this.ticked(linkRef, nodeRef));

	    sim.force("link")
		.links(this.links);
	}
    }

    color(group) {
	return d3.scaleOrdinal(d3.schemeCategory20)(group);
    }

    ticked(linkRef, nodeRef) {
	linkRef.attr("x1", d => d.source.x)
	    .attr("y1", d => d.source.y)
	    .attr("x2", d => d.target.x)
	    .attr("y2", d => d.target.y);

	nodeRef.attr("transform", d => "translate(" + d.x + "," + d.y + ")");
    }

    dragStarted(d, sim) {
	if (!d3.event.active) sim.alphaTarget(0.3).restart();
	d.fx = d3.event.x;
	d.fy = d3.event.y;
    }

    dragged(d, sim) {
	d.fx = d3.event.x;
	d.fy = d3.event.y;
    }

    dragEnded(d, sim) {
	if (!d3.event.active) sim.alphaTarget(0);
	d.fx = null;
	d.fy = null;
    }
}
