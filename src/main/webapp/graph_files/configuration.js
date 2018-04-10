/***
 * TODO
 * When generating the list of nodes, 
 * create a set of lists that will have all possible 
 * unique values so that we can quickly determine 
 * how to separate attributes
 */

/**
 * All values for the sex configuration
 */
var sex_options = {
	"male" : "blue"
	, "female" : "pink"
	, "unknown" : "gray"
	,  "undefined" : "black"
	, "as_array" : ["unknown", "undefined", "male", "female"]
	, "getSex" : function(d){
		d = getData(d);
		var sex = typeof d === "undefined" ? d : d.sex;
		return typeof sex === "undefined" ? "undefined" : sex;	
	}
	, "getSexIndex" : function (d){
		var sex = sex_options.getSex(d);
		var idx = $.inArray(sex, sex_options.as_array);
		return idx;
	}
};

/**
 * Generic color selection for shape/lines based on sex
 */
function sexColorSelect(d){
	var sex = sex_options.getSex(d);
	return sex_options[sex];
}

/**
 * Check to see if we're a link and return the source, otherwise
 * return the current data d which is valid
 */
function getData(d){
	if (typeof d === "undefined")
		return undefined;
	else if (typeof d.source === "undefined")
		return d;
	else
		return d.source;
}

/**
 * Using an input index, mod symbol length, this method chooses a valid symbol
 */
function chooseShape(index){
	var symbols = d3.svg.symbolTypes;
	return symbols[index % symbols.length];
}

/**
 * Used to pick a shape for the data passed
 */
function getDefaultShape(key_value, data) {
	var idx = getIndexAndInsert(data, key_value) ; 
	return chooseShape(idx) ;
}

/**
 * shape storage array
 */
var uniques = {
	"occurrence":[]
};

/**
 * checks if element is in an array, inserts it if it's not, otherwise returns the index
 */
function getIndexAndInsert(data, key_value){
	data = getData(data);
	if (typeof data === "undefined" || typeof data[key_value] === "undefined")
		data = "undefined"
	else
		data = data[key_value];
	
	var array = uniques[key_value];
	if (typeof array === "undefined") {
		array = uniques[key_value] = [] ;
	}
	var idx = $.inArray(data, array) ;
	// when the array is missing, add it and return the location
	if (idx < 0){
		idx = array.length;
		array.push(data);
	}
	return idx ;
}

/**
 * Get default color
 */
function getDefaultColor(d, key_value){
	var idx = getIndexAndInsert(d, key_value);
	var c = (idx + 0.0)/uniques[key_value].length;

	return globals.colors[idx % globals.colors.length];
}

/**
 * Get a default submission size from the uniques
 */
function getDefaultSize(data, key_value, change_of_base, multiplier){
	var idx = getIndexAndInsert(data, key_value);
	var size;
	var mult = typeof multiplier === "undefined" ? 30 : multiplier;
	// default to 10 when necessary
	if (typeof change_of_base === "undefined"){
		size = Math.log10(idx+2) * mult;
	}
	else if (change_of_base != 1){
		size = Math.log(idx+2)/Math.log(change_of_base) * mult;
	}
	else if (change_of_base == 1){
		size = idx * 5 + mult ;
	}
		
	return Math.ceil(size) ;
}
 
/** ---------------------------
 * Link Functions 
 * ----------------------------
 */
/**
 * This function generates all of the nodes/links using the individuals as nodes and shared occurrences as links
 * It makes the list of nodes for the d3 program, as well as sorts it for fast index lookup
 */
function basicOccurrenceRelatedLinks(data_set, use_filter) {
	/**
	 * Picks the submitter from the first encounter in the list
	 */
	function getIndividualSubmitterID(encounters){
		for (key in encounters){
			var enc = encounters[key];
			// var subID = enc["recordedBy"]; 
			var subID = enc["submitterEmail"]; 
			if (typeof subID !== "undefined"){
				return subID;
			}
		}
		// default to unknown
		return "unkown";
	}
	
	var displayObject = {
		"nodes":[], 
		"links":[]
	};
	
	var nodes = data_set.individuals;
	
	// array of nodes from the display node object
	node_array = displayObject.nodes;
	var counter = 0;
	for (key in nodes){
		nodes[key]["index"] = counter++;
		node_array.push(nodes[key]);
	}
	
	// generate the occurrence array
	used_nodes = use_filter ? [] : node_array;
	// for each occurrence, get all of the individuals and connect them together by using the encounters
	for (var occ in data_set.occurrences){
		var encounters = data_set.occurrences[occ]["encounters"];
		var firstInd = encounters.pop().individualID;
		var poppedInd = encounters.pop();
		// get all of the extra data from the encounters to be put on the individuals
		var submitterID = getIndividualSubmitterID(encounters);
		// make all of the necessary connections
		while (typeof poppedInd !== "undefined"){
			poppedInd = poppedInd.individualID ;
			if (firstInd != poppedInd) {
				// for some reason when the name is Unassigned, 
				// node value is undefined... so we're just going to skip
				// undefined nodes for now
				var skip_new_object_because_of_undefined = typeof nodes[firstInd] === "undefined" || typeof nodes[poppedInd] === "undefined";
				// Only keep the nodes with links when we're using the filter feature
				if (use_filter && !skip_new_object_because_of_undefined){
					$([firstInd, poppedInd]).each(function(i,a){
						var node = nodes[a];
						node["submitterID"] = submitterID;
						if (used_nodes.indexOf(node) == -1){
							node.index = used_nodes.length
							used_nodes.push(node);
						}
					});
				}
				
				if (!skip_new_object_because_of_undefined){
					var newObject = {"source":nodes[firstInd]["index"], "target":nodes[poppedInd]["index"]};
					displayObject.links.push(newObject);
				}
			}
			poppedInd = encounters.pop();
		}
	}
	
	// Finally set the nodes to the used nodes for now
	displayObject.nodes = used_nodes;
	
	return displayObject;
}

/**
 * This master matrix holds all of the functions that map data from the fluke objects
 * to the attributes of the force directed graph
 */
var attribute_data_mappings = {
	/**
	 * The Links function set creates the main object used in the visualization. It builds the 
	 * links using predefined logic, as well as the appropriate node list for those links.
	 */
	"Link" :{
		// This function returns an object with a set of nodes and a set of links
		"Object_Links":{
			"Whales with Co-Occurrence Only":  function (data_set){ return basicOccurrenceRelatedLinks(data_set, true) }
			,"All Whales Co-Occurrence":  function (data_set){ return basicOccurrenceRelatedLinks(data_set, false) }
		}
	}
	// first create all of the node mappings 
	,"Node" : {
		"Color":{
			"Default (Gray)":function(d){	return "gray";	}
			,"Sex":sexColorSelect
			,"Submitter":function(d) { getDefaultColor(d, "submitterID") }
			,"Individual ID":function(d) { getDefaultColor(d, "individualID") }
			,"Number Encounters":function(d) {  return getDefaultColor(d, "numberEncounters") }
			,"Max Years Between Resightings":function(d) {  return getDefaultColor(d, "maxYearsBetweenResightings") }
			,"Num Unidentifiable Encounters":function(d) {  return getDefaultColor(d, "numUnidentifiableEncounters") }
			,"Time Of Birth":function(d) {  return getDefaultColor(d, "timeOfBirth") }
			,"Nick Name":function(d) {  return getDefaultColor(d, "nickName") }
			,"Individual ID":function(d) {  return getDefaultColor(d, "individualID") }
			,"Time Of Death":function(d) {  return getDefaultColor(d, "timeOfDeath") }
			,"Series Code":function(d) {  return getDefaultColor(d, "seriesCode") }
			,"Alternate ID":function(d) {  return getDefaultColor(d, "alternateid") }
			,"Local Haplotype Reflection":function(d) {  return getDefaultColor(d, "localHaplotypeReflection") }
		}
		, "Shape":{
			"Default (Circle)":function(d){ return "circle"; }
			,"Sex":function(d){
				var idx = sex_options.getSexIndex(d);
				return chooseShape(idx);
			 }
			,"Submitter": function(d) { return getDefaultShape("submitterID", d) }
			,"Number Encounters":function(d) { return getDefaultShape("numberEncounters", d) }
			,"Max Years Between Resightings":function(d) { return getDefaultShape("maxYearsBetweenResightings", d) }
			,"Num Unidentifiable Encounters":function(d) { return getDefaultShape("numUnidentifiableEncounters", d) }
			,"Time Of Birth":function(d) { return getDefaultShape("timeOfBirth", d) }
			,"Nick Name":function(d) { return getDefaultShape("nickName", d) }
			,"Individual ID":function(d) { return getDefaultShape("individualID", d) }
			,"Time Of Death":function(d) { return getDefaultShape("timeOfDeath", d) }
			,"Series Code":function(d) { return getDefaultShape("seriesCode", d) }
			,"Alternate ID":function(d) { return getDefaultShape("alternateid", d) }
			,"Local Haplotype Reflection":function(d) { return getDefaultShape("localHaplotypeReflection", d) }
		}
		, "Size":{
			"Default (Same Size)":function(d){ return 100 }
			,"Sex":function(d){	return (sex_options.getSexIndex(d) + 1 ) * 30}
			,"Submitter":function(d) {  return getDefaultSize(d, "submitterID", 1) }
			,"Encounters":function(d) {  return getDefaultSize(d, "numberEncounters") }
			,"Max Years Between Resightings":function(d) {  return getDefaultSize(d, "maxYearsBetweenResightings") }
			,"Num Unidentifiable Encounters":function(d) {  return getDefaultSize(d, "numUnidentifiableEncounters") }
			,"Time Of Birth":function(d) {  return getDefaultSize(d, "timeOfBirth") }
			,"Nick Name":function(d) {  return getDefaultSize(d, "nickName") }
			,"Individual ID":function(d) {  return getDefaultSize(d, "individualID") }
			,"Time Of Death":function(d) {  return getDefaultSize(d, "timeOfDeath") }
			,"Series Code":function(d) {  return getDefaultSize(d, "seriesCode") }
			,"Alternate ID":function(d) {  return getDefaultSize(d, "alternateid") }
			,"Local Haplotype Reflection":function(d) {  return getDefaultSize(d, "localHaplotypeReflection") }
			}
		, "Label" :{
			// add attribute list here
			"Default (Name)":function(d) {return d.name;} 
			,"Sex": function(d) { return d.sex;}
			,"Submitter":function(d) {return d.submitterID}
			,"Number Encounters":function(d) { return d.numberEncounters }
			,"Max Years Between Resightings":function(d) { return d.maxYearsBetweenResightings }
			,"Num Unidentifiable Encounters":function(d) { return d.numUnidentifiableEncounters }
			,"Time Of Birth":function(d) { return d.timeOfBirth }
			,"Nick Name":function(d) { return d.nickName }
			,"Individual ID":function(d) { return d.individualID }
			,"Time Of Death":function(d) { return d.timeOfDeath }
			,"Series Code":function(d) { return d.seriesCode }
			,"Alternate ID":function(d) { return d.alternateid }
			,"Local Haplotype Reflection":function(d) { return d.localHaplotypeReflection }
			}
		,"Border_Color":{
			"Default (Black)":function(d){return "black"}
			,"Sex":sexColorSelect
			,"Submitter":function(d) { getDefaultColor(d, "submitterID") }
			,"Individual ID":function(d) { getDefaultColor(d, "individualID") }
			,"Number Encounters":function(d) {  return getDefaultColor(d, "numberEncounters") }
			,"Max Years Between Resightings":function(d) {  return getDefaultColor(d, "maxYearsBetweenResightings") }
			,"Num Unidentifiable Encounters":function(d) {  return getDefaultColor(d, "numUnidentifiableEncounters") }
			,"Time Of Birth":function(d) {  return getDefaultColor(d, "timeOfBirth") }
			,"Nick Name":function(d) {  return getDefaultColor(d, "nickName") }
			,"Individual ID":function(d) {  return getDefaultColor(d, "individualID") }
			,"Time Of Death":function(d) {  return getDefaultColor(d, "timeOfDeath") }
			,"Series Code":function(d) {  return getDefaultColor(d, "seriesCode") }
			,"Alternate ID":function(d) {  return getDefaultColor(d, "alternateid") }
			,"Local Haplotype Reflection":function(d) {  return getDefaultColor(d, "localHaplotypeReflection") }
		}
	},
	// now create all of the edge mappings
	"Edge" : {
		"Color":{
			"Default (Gray)":function(d){ return "gray" }
			,"Sex":sexColorSelect
			,"Submitter":function(d){ return getDefaultColor(d, "submitterID") }
			,"Individual ID":function(d){ return getDefaultColor(d, "individualID") }
			,"Number Encounters":function(d) { return getDefaultColor(d, "numberEncounters") }
			,"Max Years Between Resightings":function(d) { return getDefaultColor(d, "maxYearsBetweenResightings") }
			,"Num Unidentifiable Encounters":function(d) { return getDefaultColor(d, "numUnidentifiableEncounters") }
			,"Time Of Birth":function(d) { return getDefaultColor(d, "timeOfBirth") }
			,"Nick Name":function(d) { return getDefaultColor(d, "nickName") }
			,"Individual ID":function(d) { return getDefaultColor(d, "individualID") }
			,"Time Of Death":function(d) { return getDefaultColor(d, "timeOfDeath") }
			,"Series Code":function(d) { return getDefaultColor(d, "seriesCode") }
			,"Alternate ID":function(d) { return getDefaultColor(d, "alternateid") }
			,"Local Haplotype Reflection":function(d) { return getDefaultColor(d, "localHaplotypeReflection") }
		}
		, "Size":{
			"Default (Same Size)":function(d){ return 2 } 
			,"Sex":function(d){ return sex_options.getSexIndex(d) }
			,"Submitter":function(d) { return getDefaultSize(d, "submitterID",1,1) }
			,"Encounters":function(d) {  return getDefaultSize(d, "numberEncounters",5,1) }
			,"Max Years Between Resightings":function(d) {  return getDefaultSize(d, "maxYearsBetweenResightings",1,1) }
			,"Num Unidentifiable Encounters":function(d) {  return getDefaultSize(d, "numUnidentifiableEncounters",1,1) }
			,"Time Of Birth":function(d) {  return getDefaultSize(d, "timeOfBirth",1,1) }
			,"Nick Name":function(d) {  return getDefaultSize(d, "nickName",1,1) }
			,"Individual ID":function(d) {  	 return getDefaultSize(d, "individualID",1,1) }
			,"Time Of Death":function(d) {  return getDefaultSize(d, "timeOfDeath",1,1) }
			,"Series Code":function(d) {  return getDefaultSize(d, "seriesCode",1,1) }
			,"Alternate ID":function(d) {  return getDefaultSize(d, "alternateid",1,1) }
			,"Local Haplotype Reflection":function(d) {  return getDefaultSize(d, "localHaplotypeReflection",1,1) }
		}
	}
}
;
