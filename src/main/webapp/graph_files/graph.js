/**
 * This file does a few different things and may need to be split up. Currently it:
 * 1. Builds the graph
 * 2. Holds graph specific functions
 * 3. Fetches and generates the data for the graph
 */
/**
 * Start the display code
 */
// These come from the width set by the flukebook css
var width = 1500,
height = 800;

var color = d3.scale.linear()
  .domain([0, .5, 1])
  .range(["gray", "blue", "red" , "yellow", "green", "orange", "purple", "black"]);

var force = d3.layout.force()
	.charge(-40)
	.linkDistance(30)
	.size([width, height]);

var zoom = d3.behavior.zoom()
    .scaleExtent([.01, 10])
	.size(height, width)
    .on("zoom", zoomed);

var svg = d3.select("body").select("svg")
	.attr("width", width)
	.attr("height", height)
	.call(zoom)
	.on("dblclick.zoom", null);

// this is what we append all of the nodes/lines to in order to allow the zoom to function properly
var container = svg.append("g");

function zoomed() {
	container.attr("transform", "translate(" + d3.event.translate + ")scale(" + d3.event.scale + ")");
}
	
function displayData(graph, attribute_functions){

	force.nodes(graph.nodes)
	.links(graph.links)
	.start();
	
	// stop the graph after 5 seconds 
	function stopGraph(time){
		setTimeout(force.stop, time);
	}
	stopGraph(5000) // call first time 

	var link = container.selectAll(".link")
		.data(graph.links)
		.enter().append("line")
		.attr("class", "link")
		.style("stroke-width",  attribute_functions["Edge"]["Size"])
		.style("stroke", attribute_functions["Edge"]["Color"])

	var node = container.selectAll(".node")
		.data(graph.nodes)
		.enter().append("g")
		.attr("class", "node")
		.call(force.drag);

	// Makes the graph allow all sorts of different dragging!
	force.drag().on("dragstart", function() {
		d3.event.sourceEvent.stopPropagation()
		// stop the graph motion 
		 stopGraph(1000)
	});
	
	force.drag().on("dragend", function() {stopGraph(1000)});

	// adds the shapes 
	var shapes = node.append("path")
		.attr("d", d3.svg.symbol()
				.size(attribute_functions["Node"]["Size"])
				.type(attribute_functions["Node"]["Shape"]) )
		.on("dblclick", function(d) {
			// imported from displayFlukeData.js
			displayFlukeDiv(d);
		 })
		.style("fill", attribute_functions["Node"]["Color"] ) 
		//.style("stroke", attribute_functions["Node"]["Border_Color"] ) ; 

	// add the title
	node.append("title").text(attribute_functions["Node"]["Label"]);
	
	// this had to be added to move the different shapes around 
	// on the graph otherwise everything just didnt work at all
	force.on("tick", function() {
		
		node.attr("transform", function(d) { return "translate(" + d.x + "," + d.y + ")"; });
		
		link.attr("x1", function(d) { return d.source.x; })
		.attr("y1", function(d) { return d.source.y; })
		.attr("x2", function(d) { return d.target.x; })
		.attr("y2", function(d) { return d.target.y; });

		node.attr("cx", function(d) { return d.x; })
		.attr("cy", function(d) { return d.y; });
	});
}

/**
 * Load the data from the server, this can be replaced with a get to the static
 * saved json object on the test client
 * 
 * Data retrieval process
 * 1. Get all individual names by running a 'getAllIndividuals()' 
 * 2. Get all encounters 
*/
function getFlukeData(call_back_function){
	// once we've loaded the data, don't do it again
	if (globals.all_data) return call_back_function(globals.all_data);
	
	// get all marked individuals
	function getMarkedIndividuals(encounter_data){
		// from the local test dataset
		// var individualQuery = 'testData/all_individuals.json'; 
		var individualQuery = globals.server_url + 'rest/jpql?SELECT+m+FROM+org.ecocean.MarkedIndividual+m';
		$.getJSON(individualQuery, function(individual_response){
			var all_data = {"encounters":encounter_data, "individuals":individual_response };
			globals.all_data = all_data;
			call_back_function(all_data);
		});
	}
	
	// from the local test dataset
	// var encounterQuery = 'testData/all_encounters_valid.json'; 
	// get all occurrences which have encounters with non-null individual IDs
	var encounterQuery = globals.server_url + 'rest/jpql?SELECT+en+from+org.ecocean.Encounter+en+WHERE+en.occurrenceID+IS+NOT+NULL';
	$.getJSON(encounterQuery, function(encounter_response){
		getMarkedIndividuals(encounter_response);
	});

}

/**
	Our normalized data set is made up of 
	three types of objects:
	- Occurrences 
	- Encounters
	- Individuals 
	
	Each is a hashmap based on the key of the object
*/
function getNormalizedData(fluke_data){	
	var encounterData = fluke_data.encounters;
	var individualData = fluke_data.individuals;
	// This object holds all of the occurrence objects indexed by their occurrenceID, and 
	// each object stored at the occurrence object has a list of references to individuals
	// and to it's encounters
	var occurrenceObject = {};
	// easy way to insert if doesn't exist
	function check_for_object(hash_table, name, new_object){
		var obj = hash_table[name];
		if (typeof obj === "undefined"){
			obj = hash_table[name] = new_object;
		}
		return obj;
	}
	// determine all unique occurrenceIDs present, and create a mapping between the occurrences and the encounters
	var occurrences = {};
	$(encounterData).each(function(i, a){
		// check if it exists, and add it if it does not 
		var obj = check_for_object(occurrences, a.occurrenceID, {});
		var occ_encounters = check_for_object(obj, "encounters", []);
		occ_encounters.push(a);
	});
	// make the encounters array an encounters hashmap
	var encounters = {};
	$(encounters).each(function(i,a){
		var obj = check_for_object(encounters, a.catalogID, a);
	});
	// make the individuals array an individuals hashmap
	var individuals = {};
	uniqueIDs = {} ;
	$(individualData).each(function(i,a){
		// check if it exists, and add it if it does not 
		var obj = check_for_object(individuals, a.individualID, a);
		// Also, move any encounter attributes out of the encounter list onto the inviduals object
		obj["name"] = obj.individualID;
		
		for (key in obj){
			if (typeof uniqueIDs[key] === "undefined")
				uniqueIDs[key] = [];
			uniqueIDs[key].push(obj[key]);
		}
	});
	
	// return the final object
	return {"occurrences":occurrences, "encounters":encounters, "individuals":individuals};
}

/**
 * Generates all of the graph display (nodes, links) as well as builds any relevant data 
 * needed to be displayed in the graph.
 * 
 * Inputs:
 *  attributeObject - set of functions called on the nodes and the lines that map the data to attributes
 */
function run(attribute_functions){
	getFlukeData(function(data){
		// generate an object with all of the occurrence data processed
		var normalized_data = getNormalizedData(data);
		// Set Nodes and Links (this is hard coded for now)
		displayObject = attribute_functions["Link"]["Object_Links"](normalized_data);
		// show the graph
		displayData(displayObject, attribute_functions);
	});
}

function transitionAndRemove(){
	// remove the links
	svg.selectAll("g").remove();
	// reset the container reference
	container = svg.append("g");
}
