// lang  dictionary contains all language specific text. It is loaded in individuals jsp and contains
// all the necessary keys from individuals.properties with the same syntax.
var dict = {}; 
var languageTable = function(words) {
    dict = words;
}

var getIndividualIDFromEncounterToString = function(encToString) {
    // return everything between "individualID=" and the next comma after that
    //console.log('encToString = %o', encToString);
    //var id = encToString.split("individualID=")[1].split(",")[0];
    var id = encToString.displayName; // since this is for display, and individualIDs are UUIDs now
    if (!id) return false;
    id = id.concat(" id=", encToString.individualID); // appending UUID, which is needed while forming the co-occurrence table URL
    return id;
}

var getData = function(individualID, displayName) {
    var occurrenceObjectArray = [];
    var items = [];
    var encounterArray = [];
    var encArrWithUUID = [];
    var occurrenceArray = [];
    var dataObject = {};

	console.log("QUERY: "+wildbookGlobals.baseUrl + "/api?useProjectContext=true&query="+encodeURIComponent("SELECT FROM org.ecocean.Occurrence WHERE encounters.contains(enc) && enc.individual.individualID == \"" + individualID + "\" VARIABLES org.ecocean.Encounter enc"));

    //d3.json(wildbookGlobals.baseUrl + "/api?useProjectContext=true&query="+encodeURIComponent("SELECT FROM org.ecocean.Occurrence WHERE encounters.contains(enc) && enc.individual.individualID == \"" + individualID + "\" VARIABLES org.ecocean.Encounter enc"), function(error, json) {
	d3.json(wildbookGlobals.baseUrl+'/encounters/occurrenceGraphJson.jsp?individualID='+individualID, function(error, json) {
	
	if(error) {
            console.log("error")
	}
	var jsonData = json;
	for(var i=0; i < jsonData.length; i++) {
            var thisOcc = jsonData[i];
            console.log("JsonData["+i+"] = "+JSON.stringify(thisOcc));
            var encounterSize = thisOcc.encounters.length;
            // make encounterArray, containing the individualIDs of every encounter in thisOcc;
            for(var j=0; j < encounterSize; j++) {
				console.info('[%d] %o %o', j, thisOcc.encounters, thisOcc.encounters[j]);
				var thisEncIndID = getIndividualIDFromEncounterToString(thisOcc.encounters[j]);
				var sex=thisOcc.encounters[j].sex;
				var haplotype=thisOcc.encounters[j].haplotype;
				var location=thisOcc.encounters[j].locationID;
				//console.log("thisEncIndID="+thisEncIndID);
		
				//var thisEncIndID = jsonData[i].encounters[j].individualID;   ///only when we fix thisOcc.encounters to be real json   :(
				//console.info('i=%d, j=%d, -> %o', i, j, thisEncIndID);
				if (!thisEncIndID) continue;
				var individualName = thisEncIndID.split(" id=")[0];
				if (individualName === displayName) continue;  //unknown indiv -> false
				if(!encounterArray.includes(individualName)) {
					encounterArray.push(individualName);
					encArrWithUUID.push(thisEncIndID);
				}
            }
            occurrenceArray = occurrenceArray.concat(encArrWithUUID);
            var occurrenceID = jsonData[i].occurrenceID;
            var index = encounterArray.indexOf(individualID.toString());
            if (~index) {
				encounterArray[index] = "";
            }
            var occurrenceObject = new Object();
            if(encounterArray.length > 0) {
				occurrenceObject = {occurrenceID: occurrenceID, occurringWith: encounterArray.filter(function(e){return e}).join(", ")};
            } 
			else {
				occurrenceObject = {occurrenceID: "", occurringWith: ""};
            }
			occurrenceObjectArray.push(occurrenceObject);
            encounterArray = [];
            encArrWithUUID = [];
	}

	for(var i = 0; i < occurrenceArray.length; ++i) {
            if(!dataObject[occurrenceArray[i]])
				dataObject[occurrenceArray[i]] = 0;
            ++dataObject[occurrenceArray[i]];
	}
	for (var prop in dataObject) {
            var whale = new Object();
            whale = {text:prop, count:dataObject[prop], sex: sex, location: location};
            items.push(whale);	
	}
	//if (items.length > 0) {
    //        getSexHaploData(individualID, items);
	//}
	
	makeTable(items, "#coHead", "#coBody", null,["occurringWith", "occurrenceNumber","sex","location"]);
	$('#cooccurrenceTable tr').attr("onClick", "return cooccurTableAuxClick(this);")
							  .attr("onAuxClick", "return cooccurTableAuxClick(this);")
							  .each(function() {
								indivUrl = "individuals.jsp?number=" + ($(this).attr("class"));
								$(this).find("td").first().wrapInner("<a href=\""+indivUrl+"\"></a>");
							  });
	
	getEncounterTableData(occurrenceObjectArray, individualID);
    });
};

/*
var getSexHaploData = function(individualID, items) {
    //d3.json(wildbookGlobals.baseUrl + "/api?query="+encodeURIComponent("SELECT FROM org.ecocean.MarkedIndividual WHERE encounters.contains(enc) && occur.encounters.contains(enc) && occur.encounters.contains(enc2) && enc2.individual.individualID == \"" + individualID + "\" VARIABLES org.ecocean.Encounter enc;org.ecocean.Encounter enc2;org.ecocean.Occurrence occur"), function(error, json) {
	d3.json(wildbookGlobals.baseUrl + "/api?query="+encodeURIComponent("SELECT FROM org.ecocean.MarkedIndividual WHERE individualID == '" + individualID + "'"), function(error, json) {
	
	if(error) {
	    console.log("error")
	}
	jsonData = json;
	for(var i=0; i < jsonData.length; i++) {
	    var result = items.filter(function(obj) {
		return obj.text === jsonData[i].individualID
	    })[0];
	    if (!result) continue;
	    result.sex = jsonData[i].sex;
	    result.haplotype = jsonData[i].localHaplotypeReflection;
	}
	
	makeTable(items, "#coHead", "#coBody", null,["occurringWith", "occurrenceNumber","sex","location"]);
	$('#cooccurrenceTable tr').click(function() {
            selectedWhale = ($(this).attr("class"));
            goToWhaleURL(selectedWhale);
	});
    });
};
*/

var makeTable = function(items, tableHeadLocation, tableBodyLocation, sortOn, keys) {
    var previousSort = null;
    refreshTable(sortOn);

    function refreshTable(sortOn) {
	
	if(keys==null) {keys=d3.keys(items[0]);}
	
	if(tableHeadLocation == "#encountHead"){
	    keys.shift();
	}
	var thead = d3.select(tableHeadLocation).selectAll("th")
	    .data(keys).enter()
	    .append("th").text(function(d){
		if(d === "text") {
		    return dict['occurringWith'];
		} if (d === "occurrenceNumber"){
		    return dict['occurrenceNumber'];
		} if (d === "behavior") {
		    return dict['behavior'];
		} if(d === "alternateID") {
		    return dict['alternateID'];
		}if (d === "sex") {
		    return dict['sex'];
		} if (d === "haplotype") {
		    return dict['haplotype'];
		} if (d === "location") {
		    return dict['location'];
		} if (d === "dataTypes") {
		    return dict['dataTypes'];
		} if (d === "date") {
		    return dict['date'];
		} if(d === "occurringWith") {
		    return dict['occurringWith'];
		} if(d === "catalogNumber") {
		    //return dict['catalogNumber'];
		} if(d === "roles") {
		    return dict['roles'];
		} if(d === "relationshipWith") {
		    return dict['relationshipWith'];
		} if(d === "type") {
		    return dict['type'];
		} if(d === "socialUnit") {
		    return dict['socialUnit'];
		} if(d === "edit") {
		    return dict['edit'];
		} if(d === "remove") {
		    return dict['remove'];
		} if(d === "relationshipID") {
		    return  dict['relationshipID'];
		}
	    })
	    .on("click", function(d){
		if(tableHeadLocation != "#relationshipHead") {
		    return refreshTable(d);
		}
	    });
	
	var indivArray = [];
	if(tableBodyLocation == "#coBody") {
		for (var i = 0; i < items.length; i++) {
			var nameIdArr = items[i].text.split(" id=");
			var individual = { name: nameIdArr[0], uuid: nameIdArr[1] }
			indivArray.push(individual);
			items[i].text = nameIdArr[0];
		}
	}
	
	//console.log("ITEMS", items)
	var tr = d3.select(tableBodyLocation).selectAll("tr")
	    .data(items).enter()
	    .append("tr")
	    .attr("class", function(d){
		if(d.relationshipID !=null && d.relationshipID != 'undefined') {
		    return d.relationshipID;
		}
		if(tableBodyLocation == "#coBody") {
			return indivArray.find(indiv => (indiv.name == d3.values(d)[0])).uuid;
		}
		return d3.values(d)[0];
	    });
	
	//console.log("TR", tr.selectAll("td"))
	var td = tr.selectAll("td")
	    .data(function(d){
		//console.log("VALUES", d3.values(d))
		if(tableHeadLocation == "#encountHead"){
	    	    var smaller = d3.values(d);
	    	    smaller.shift();
	    	    return smaller;
		}
		
		return d3.values(d);
    	    })
	    .enter().append("td")
	    .html(function(d) {
		//console.log("D", d);
		
		if(d == 'TissueSample') {
		    return "<img class='encounterImg' src='images/microscope.gif'/>";
		} 
		if(d == 'image') {
		    return "<img class='encounterImg' src='images/Crystal_Clear_filesystem_folder_image.png'/>"
		} 
		if(d == 'youtube-image') {
		    return "<img class='encounterImg' src='images/youtube.png'/>"
		} 
		if(d == 'both') {
		    return "<img class='encounterImg' src='images/microscope.gif'/><img class='encounterImg' src='images/Crystal_Clear_filesystem_folder_image.png'/>";
		}
		if(typeof d == "object") {
		    if(d.length <= 2) {
			if(d[0] == 'edit'){
			    return "<button type='button' name='button' value='" + d[1] + "' class='btn btn-sm btn-block editRelationshipBtn' id='edit" + d[1] + "'>Edit</button>";
			} if(d[0] == 'remove') {
			    return "<button type='button' name='button' value='" + d[1] + "' class='btn btn-sm btn-block deleteRelationshipBtn' id='remove" + d[1] + "'>Remove</button><div class='confirmDelete' value='" + d[1] + "'><p>Are you sure you want to delete this relationship?</p><button class='btn btn-sm btn-block yesDelete' type='button' name='button' value='" +d[1]+ "'>Yes</button><button class='btn btn-sm btn-block cancelDelete' type='button' name='button' value='" + d[1] + "'>No</button></div>";
			}
			return d[0].italics() + "-" + d[1];
		    }
		    if(d.length > 2) {
			return "<a href='individuals.jsp?number=" + d[0] + "'>" + d[5] + "</a><br><span>" + dict['nickname'] + " : " + d[1]+ "</span><br><span>" + dict['alternateID'] + ": " + d[2] + "</span><br><span>" + dict['sex'] + ": " + d[3] + "</span><br><span>" + dict['haplotype'] +": " + d[4] + "</span>";
		    }
		}
		if(d == "GOS") {
		    return "<a href='socialUnit.jsp?name=" + d + "'>" + d + "</a>"
		}
		return d; 
	    });
	
	if(sortOn !== null) {
	    console.log("sorting on: "+sortOn);
	    if(sortOn != previousSort){
		tr.sort(function(a,b){return sort(a[sortOn], b[sortOn]);});
		previousSort = sortOn;
	    } else {
		tr.sort(function(a,b){return sort(b[sortOn], a[sortOn]);});
		previousSort = null;
	    }
	    td.html(function(d) {
		if(d == 'TissueSample') {
		    return "<img class='encounterImg' src='images/microscope.gif'/>";
		} 
		if(d == 'image') {
		    return "<img class='encounterImg' src='images/Crystal_Clear_filesystem_folder_image.png'/>"
		} 
		if(d == 'youtube-image') {
		    return "<img class='encounterImg' src='images/youtube.png'/>"
		} 
		if(d == 'both') {
		    return "<img class='encounterImg' src='images/microscope.gif'/><img class='encounterImg' src='images/Crystal_Clear_filesystem_folder_image.png'/>";
		}
		return d;
	    });
	}
    }

    function sort(a,b) {
	if(typeof a == "string"){
	    if(a === "") {
		a = "0";
	    }if (b === "") {
		b = "0";
	    }
	    var parseA = unixCrunch(a);
	    if(parseA) {
		var whaleA = parseA;
		var whaleB = unixCrunch(b);
		return whaleA < whaleB ? 1 : whaleA == whaleB ? 0 : -1;
	    } else
		return a.localeCompare(b);
	} else if(typeof a == "number") {
	    return a > b ? 1 : a == b ? 0 : -1;
	} else if(typeof a == "boolean") {
	    return b ? 1 : a ? -1 : 0;
	}
    }
    
    function unixCrunch(date) {
	date = date.replace("-","/");
	return new Date(date).getTime()/1000;
    }
    
};

var makeRelTable = function(items, tableHeadLocation, tableBodyLocation, sortOn) {
    //console.log("makeRelTable");
	var previousSort = null;
    refreshRelTable(sortOn);

    function refreshRelTable(sortOn) {
		//console.log("Refreshing rel table")
	
		//var keys=d3.keys(items[0]);
		var keys=["roles", "relationshipWith", "type", "socialUnit", "edit", "remove"];
		//console.log("keys",keys);
		if(tableHeadLocation == "#encountHead"){
	    	keys.shift();
		}
		var thead = d3.select(tableHeadLocation).selectAll("th")
	    	.data(keys).enter()
	    	.append("th").text(function(d){
				//console.log("relTable header");
				if(d === "text") {
		    		return dict['occurringWith'];
				} 
				if (d === "occurrenceNumber"){
		    		return dict['occurrenceNumber'];
				} 
				if (d === "behavior") {
		    		return dict['behavior'];
				} 
				if(d === "alternateID") {
		    		return dict['alternateID'];
				}
				if (d === "sex") {
		    		return dict['sex'];
				} 
				if (d === "haplotype") {
		    		return dict['haplotype'];
				} 
				if (d === "location") {
		    		return dict['location'];
				} 
				if (d === "dataTypes") {
		    		return dict['dataTypes'];
				} 
				if (d === "date") {
		    		return dict['date'];
				} 
				if(d === "occurringWith") {
		    		return dict['occurringWith'];
				} 
				if(d === "catalogNumber") {
		    		//return dict['catalogNumber'];
				} 
				if(d === "roles") {
		    		return dict['roles'];
				} 
				if(d === "relationshipWith") {
		    		return dict['relationshipWith'];
				} 
				if(d === "type") {
		    		return dict['type'];
				} 
				if(d === "socialUnit") {
		    		return dict['socialUnit'];
				} 
				if(d === "edit") {
		    		return dict['edit'];
				} 
				if(d === "remove") {
		    		return dict['remove'];
				} 
				if(d === "relationshipID") {
		    		return  dict['relationshipID'];
				}
	   	 	})
	    	.on("click", function(d){
				if(tableHeadLocation != "#relationshipHead") {
			    	return refreshRelTable(d);
				}
	    	});

		//console.log("REL ITEMS", items);
		var tr = d3.select(tableBodyLocation).selectAll("tr")
	    	.data(items).enter()
	    	.append("tr")
	   		.attr("class", function(d){
				if(d.relationshipID !=null && d.relationshipID != 'undefined') {
			    	return d.relationshipID;
				}
				return d3.values(d)[0];
	    	});

		//console.log("TR", tr.selectAll("td"));
		var td = tr.selectAll("td")
	    	.data(function(d){
				//console.log("relVALUES", d3.values(d));
				if(tableHeadLocation == "#encountHead"){
		    	    var smaller = d3.values(d);
		    	    smaller.shift();
		    	    return smaller;
				}
			
				return d3.values(d);
    	    })
	    	.enter().append("td")
	    	.html(function(d) {
			
				if(whatIsIt(d)==="Array") {
					console.log("AN ARRAY!!!:", d)
			    	//if(d.length <= 2) {
					if(d[0] == "edit"){
						console.log("yo edit!");
				    	return "<button type='button' name='button' value='" + d[1] + "' class='btn btn-sm btn-block editRelationshipBtn' id='edit" + d[1] + "'>Edit</button>";
					} 
					if(d[0] == 'remove') {
				    	return "<button type='button' name='button' value='" + d[1] + "' class='btn btn-sm btn-block deleteRelationshipBtn' id='remove" + d[1] + "'>Remove</button><div class='confirmDelete' value='" + d[1] + "'><p>Are you sure you want to delete this relationship?</p><button class='btn btn-sm btn-block yesDelete' type='button' name='button' value='" +d[1]+ "'>Yes</button><button class='btn btn-sm btn-block cancelDelete' type='button' name='button' value='" + d[1] + "'>No</button></div>";
					}
					if(d.length > 2) {
						return "<a href='individuals.jsp?number=" + d[0] + "'>" + d[5] + "</a><br><span>" + dict['nickname'] + " : " + (d[1] ?? "")+ "</span><br><span>" + dict['alternateID'] + ": " + (d[2] ?? "") + "</span><br><span>" + dict['sex'] + ": " + (d[3] ?? "") + "</span><br><span>" + dict['haplotype'] +": " + (d[4] ?? "") + "</span>";
			    	}
					return d[0].italics() + "-" + d[1];
			    	//}
			    
				}
				if(d == "socialUnit") {
			    	return "<a href='socialUnit.jsp?name=" + d + "'>" + d + "</a>"
				}
			
				//couldn't find it so dump it as text
				return d; 
	   	 	});
	
			if(sortOn !== null && items.length > 0) {
	    		console.log("relSorting on: "+sortOn);
	    		if(sortOn != previousSort){
					tr.sort(function(a,b){return sort(a[sortOn], b[sortOn]);});
					previousSort = sortOn;
	    		} 
				else {
					tr.sort(function(a,b){return sort(b[sortOn], a[sortOn]);});
					previousSort = null;
	   	 		}

			}
    }

    function sort(a,b) {
		if(typeof a == "string"){
	    	if(a === "") {
				a = "0";
	    	}
			if (b === "") {
				b = "0";
	    	}
	    	var parseA = unixCrunch(a);
	    	if(parseA) {
				var whaleA = parseA;
				var whaleB = unixCrunch(b);
				return whaleA < whaleB ? 1 : whaleA == whaleB ? 0 : -1;
	    	} 
			else return a.localeCompare(b);
		} 
		else if(typeof a == "number") {
	    	return a > b ? 1 : a == b ? 0 : -1;
		} 
		else if(typeof a == "boolean") {
	    	return b ? 1 : a ? -1 : 0;
		}
    }
    
    function unixCrunch(date) {
		date = date.replace("-","/");
		return new Date(date).getTime()/1000;
    }
    
};

var getEncounterTableData = function(occurrenceObjectArray, individualID) {
    d3.json(wildbookGlobals.baseUrl + "/api/v3/individuals/info/social-data?id=" + encodeURIComponent(individualID), function(error, json) {
        if (error) { console.log("error"); return; }
        var encounterData = [];
        var encs = (json && json.encounters) ? json.encounters : [];
        for (var i = 0; i < encs.length; i++) {
            var row = encs[i];
            var date;
            var dim = new Date(row.dateInMilliseconds);
            if (dim > 0) {
                date = dim.toISOString().substring(0, 10);
                if (row.day < 1) { date = date.substring(0, 7); }
                if (row.month < 0) { date = date.substring(0, 4); }
            } else if (row.year) {
                date = row.year;
                if (row.month) { date += "-" + row.month; }
                if (row.day) { date += "-" + row.day; }
            } else { date = dict['unknown']; }
            var encounter = {
                catalogNumber: row.catalogNumber,
                date: date,
                location: row.locationID || "",
                dataTypes: row.dataTypes || "",
                alternateID: row.alternateid,
                sex: row.sex,
                occurringWith: row.occurringWith || "",
                behavior: row.behavior
            };
            encounterData.push(encounter);
        }
        makeTable(encounterData, "#encountHead", "#encountBody", "date", null);
        $('#encountTable tr').attr("onClick", "return encountTableAuxClick(this);")
                             .attr("onAuxClick", "return encountTableAuxClick(this);")
                             .each(function() {
                                encountUrl = "encounters/encounter.jsp?number=" + ($(this).attr("class"));
                                $(this).find("td").first().wrapInner("<a href=\"" + encountUrl + "\"></a>");
                             });
    });
}

var goToEncounterURL = function(selectedWhale) {
	var target = (event.metaKey && event.button == 0) || (event.button == 1) ? '_blank' : '_self';
    window.open(wildbookGlobals.baseUrl + "/encounters/encounter.jsp?number=" + selectedWhale, target);
}

var goToWhaleURL = function(selectedWhale) {
	var target = (event.metaKey && event.button == 0) || (event.button == 1) ? '_blank' : '_self';
    window.open(wildbookGlobals.baseUrl + "/individuals.jsp?number=" + selectedWhale, target);
}

var getRelationshipData = function(relationshipID) {
    d3.json(wildbookGlobals.baseUrl + "/api/org.ecocean.social.Relationship/" + relationshipID, function(error, json) {
	if(error) {
            console.log("error")
	}
	jsonData = json;
	console.log("Relationship ID in getRelationshipData: " + relationshipID);
	var type = jsonData.type;
	var individual1 = jsonData.markedIndividualName1;
	var individual2 = jsonData.markedIndividualName2;
	var role1 = jsonData.markedIndividualRole1;
	var role2 = jsonData.markedIndividualRole2;
	var descriptor1 = jsonData.markedIndividual1DirectionalDescriptor;
	var descriptor2 = jsonData.markedIndividual2DirectionalDescriptor
	var socialUnit = jsonData.relatedSocialUnitName;

	var startTime;
	var endTime;
	
	if (jsonData.startTime == "-1") {
    	    startTime = null;;
	} else {
    	    startTime = new Date(jsonData.startTime);
	}
	if (jsonData.endTime == "-1") {
    	    endTime = null;
	} else {
    	    endTime = new Date(jsonData.endTime);
	}

	var bidirectional = jsonData.bidirectional;

	$("#addRelationshipForm").show();
	$("#setRelationship").show();

	$("#individual1set").hide();
	$("#individual1").show();
	$("#individual1").val(individual1);
	$('#role1').val(role1);
	$("#descriptor1").val(descriptor1);
	$("#individual2").val(individual2);
	$("#role2").val(role2);
	$("#descriptor2").val(descriptor2);
	$("#socialUnit").val(socialUnit);
	$("#startTime").val(startTime.toISOString().substr(0,10));
	$("#endTime").val(endTime.toISOString().substr(0,10));
	$("#bidirectional").val(bidirectional);
    });
}

var resetForm = function($form, markedIndividual) {
    $("#addRelationshipForm").show();

    $form.show();
    $form.find('input:text, select').val('');

    $("#type option:selected").prop("selected", false);
    $("#type option:first").prop("selected", "selected");
    $("#role1 option:selected").prop("selected", false);
    $("#role1 option:first").prop("selected", "selected");
    $("#role2 option:selected").prop("selected", false);
    $("#role2 option:first").prop("selected", "selected");

    $("#individual1").val('');
    $("#individual1").val(markedIndividual);
    $("#individual1").hide();
    $("#individual1set").show();
}

var getRelationshipTableData = function(individualID) {
    d3.json(wildbookGlobals.baseUrl + "/api/v3/individuals/info/social-data?id=" + encodeURIComponent(individualID), function(error, json) {
        if (error) { console.log("error"); return; }
        var relationshipTableData = [];
        var rels = (json && json.relationships) ? json.relationships : [];
        for (var i = 0; i < rels.length; i++) {
            var rel = rels[i];
            var relationshipID = rel._id;
            var markedIndividualRole, relationshipWithRole;
            if (rel.markedIndividualName1 != individualID) {
                markedIndividualRole = rel.markedIndividualRole2;
                relationshipWithRole = rel.markedIndividualRole1;
            } else {
                markedIndividualRole = rel.markedIndividualRole1;
                relationshipWithRole = rel.markedIndividualRole2;
            }
            var p = rel.partner || {};
            relationshipTableData.push({
                "roles": [markedIndividualRole, relationshipWithRole],
                "relationshipWith": [p.individualID, p.nickName, p.alternateid, p.sex, p.localHaplotypeReflection, p.displayName],
                "type": rel.type,
                "socialUnit": rel.relatedSocialUnitName,
                "edit": ["edit", relationshipID],
                "remove": ["remove", relationshipID]
            });
        }
        makeRelTable(relationshipTableData, "#relationshipHead", "#relationshipBody", "text");
    });
}
function whatIsIt(object) {
	var stringConstructor = "test".constructor;
	var arrayConstructor = [].constructor;
	var objectConstructor = ({}).constructor;
    if (object === null) {
        return "null";
    }
    if (object === undefined) {
        return "undefined";
    }
    if (object.constructor === stringConstructor) {
        return "String";
    }
    if (object.constructor === arrayConstructor) {
        return "Array";
    }
    if (object.constructor === objectConstructor) {
        return "Object";
    }
    {
        return "don't know";
    }
}