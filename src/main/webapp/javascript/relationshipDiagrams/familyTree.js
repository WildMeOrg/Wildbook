//TODO: Needs top-level file documentation

//Major Features Checklist

//Add a key (gender coloration, edge coloration, alpha symbols)
//Pull/parse live data
  //Switch all categorical data to enums
//Format text cleanly
//Click on node to fix zoom
//Add functionality to ensure any graph or reasonable size may be visualized
//Add functionality for two parents nodes (difficult)
//Enforce pan boundaries

//TODO: Delete data spec
const DATA = {
    "name": "Lion 1",
    "gender": "female",
    "role": "alpha",
    "isFocus": true,
    "children": [
	{
	    "name": "Lion 2",
	    "gender": "female",
	    "children": [
		{
		    "name": "Lion 5",
		    "gender": "male"
		},
		{
		    "name": "Lion 6",
		    "gender": ""
		}
	    ]
	},
	{
	    "name": "Lion 3",
	    "gender": "male",
	    "children": [
		{
		    "name": "Lion 11",
		    "gender": ""
		}
	    ]		
	},
	{
	    "name": "Lion 4",
	    "gender": "female",   
	    "children": [
		{
		    "name": "Lion 7",
		    "gender": "female",
		    "role": "alpha",
		    "children": [
			{
			    "name": "Lion 9",
			    "gender": "male"
			},
			{
		   	    "name": "Lion 10",
			    "gender": "female"
			},
			{
		   	    "name": "Lion 12",
			    "gender": "female"
			}
		    ]
		}
	    ]
	},
	{
	    "name": "Lion 8",
	    "gender": "female"
	}
    ]
};

function setupFamilyTree(individualID) {
    let ft = new FamilyTree(individualID);
    ft.applySocialData();
}

class FamilyTree extends GraphAbstract {
    constructor(individualID) {
	super(individualID); //Parent constructor
	
	this.i = 0; //TODO: Rename	
	
	//Set upon data retrieval
	this.tree;
	this.root;
    }

    //TODO: Consider moving this outside the scope of the class.. The obj references are clunky
    applySocialData(individualID, callback) {
	d3.json(wildbookGlobals.baseUrl + "/api/jdoql?" +
		encodeURIComponent("SELECT FROM org.ecocean.social.Relationship WHERE (this.type == \"social grouping\") && " +
				   "(this.markedIndividualName1 == \"" + this.id + "\" || this.markedIndividualName2 == \"" +
				   this.id + "\")"), (error, json) => this.graphFamilyData(error, json));
    }

    graphFamilyData(error, json) {
	if (error) {
	    return console.error(error);
	}

	//If there are no familial relationships, default to social relationships table
	if (json.length < 1) { //TODO: Consider defaulting to this...
	    $(".socialVis").hide();
	    $("#communityTable").show();
	    $(".socialVisTab").removeClass("active");
	    $("#communityTableTab").addClass("active");
	    this.showIncompleteInformationMessage();
	}
	else if (json.length >= 1) {
	    this.tree = d3.tree()
		.size([this.gHeight, this.gWidth]);
	    
	    this.svg = d3.select("#familyDiagram").append("svg")	        
		.attr("width", this.width)
		.attr("height", this.height)
	    	.call(this.zoom.transform, d3.zoomIdentity.translate(this.margin.left, this.margin.top))
		.call(this.zoom.on("zoom", () => {
		    this.svg.attr("transform", d3.event.transform)
		}))
		.append("g")
		.attr("transform", "translate(" + this.margin.left + "," + this.margin.top + ")");
	    
	    //Define the tooltip div
	    this.addTooltip("#familyDiagram");
	    
	    this.root = d3.hierarchy(DATA, d => d.children); //TODO: Change to this.data when possible
	    this.root.x0 = this.height / 2;
	    this.root.y0 = 0;

	    this.updateTree(this.root);
	}
    }

    updateTree(source) {
	//Assign node values
	let treeData = this.tree(this.root);
	
	//Compute the new tree layout
	let nodes = treeData.descendants(),
	    links = treeData.descendants().slice(1);

	this.calcNodeSize(nodes);

	//Get reference to all nodes
	let allNodes = this.svg.selectAll("g.node")
	    .data(nodes, d => d.id || (d.id = ++this.i));

	//Handle nodes
	let updateNodes = this.addNewNodes(allNodes, source)
	    .merge(allNodes);
	this.updateNodes(updateNodes);
	this.removeStaleNodes(allNodes, source);
	
	//Get reference to all links
	let link = this.svg.selectAll("path.link")
	    .data(links, d => d.id);

	//Handle links
	let updateLinks = this.addNewLinks(link, source)
	    .merge(link);
	this.updateLinks(updateLinks);
	this.removeStaleLinks(link, source);
	
	// Stash the old positions for transition.
	nodes.forEach(d => {
	    d.x0 = d.x;
	    d.y0 = d.y;
	});
    }

    addNewNodes(newNodes, source) {
	//Enter any new nodes at parent's prev. position
	let nodeEnter = newNodes.enter().append("g")
	    .attr("class", "node")
	    .attr("transform", d => "translate(" + source.y0 + "," + source.x0 + ")")
	    .on("click", d => this.click(d))
	    .on("mouseover", d => this.handleMouseOver(d))					
            .on("mouseout", d => this.handleMouseOut(d));
	
	this.drawNodeOutlines(nodeEnter, true);
	this.drawNodeSymbols(nodeEnter, true);
	this.addNodeText(nodeEnter, true);

	return nodeEnter;
    }

    updateNodes(updatedNodes) {	
	//Transition nodes to their new position.
	let nodeUpdate = updatedNodes.transition()
	    .duration(this.duration)
	    .attr("transform", d => "translate(" + d.y + "," + d.x + ")");

	nodeUpdate.select("circle")
	    .attr("r", this.radius)
	    .style("fill", d => this.colorCollapsed(d)); //Updates color if collapsed

	nodeUpdate.select("text")
	    .style("fill-opacity", 1);

	this.updateSymbols(nodeUpdate, false);
	
	return nodeUpdate;
    }
    
    removeStaleNodes(removedNodes, source) {
	//Transition exiting nodes to the parent's new position.
	let nodeExit = removedNodes.exit().transition()
	    .duration(this.duration)
	    .attr("transform", d => "translate(" + source.y + "," + source.x + ")")
	    .remove();

	//TODO: Consider better solution than shrinking for invisibility
	nodeExit.select("circle")
	    .attr("r", 1e-6);

	nodeExit.select("text")
	    .style("fill-opacity", 1e-6);

	this.updateSymbols(nodeExit, true);

	return nodeExit;
    }

    addNewLinks(link, source) {
	//Enter any new links at the parent's previous position.
	return link.enter().insert("path", "g")
	    .attr("class", "link")
	    .attr("d", d => {
		let o = {x: source.x0, y: source.y0};
		return this.diagonal(o, o);
	    });
    }

    diagonal(s, d) {
	return `M ${s.y} ${s.x}
                C ${(s.y + d.y) / 2} ${s.x},
                  ${(s.y + d.y) / 2} ${d.x},
                  ${d.y} ${d.x}`;
    }

    updateLinks(linkUpdate) {
	//Transition links to their new position.
	linkUpdate.transition()
	    .duration(this.duration)
	    .attr("d", d => this.diagonal(d, d.parent));
    }

    removeStaleLinks(link, source) {
	link.exit().transition()
	    .duration(this.duration)
	    .attr("d", d => {
		let o = {x: source.x, y: source.y};
		return this.diagonal(o, o);
	    })
	    .remove();
    }

    showIncompleteInformationMessage() {
	$("#familyDiagram").html("<h4>There are currently no known familial relationships for this Marked Individual</h4>")
    };
}
