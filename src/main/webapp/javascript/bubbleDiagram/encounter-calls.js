// lang  dictionary contains all language specific text. It is loaded in individuals jsp and contains
// all the necessary keys from individuals.properties with the same syntax.
var dict = {}; 
var languageTable = function(words) {
	dict = words;
}


var makeCooccurrenceChart = function(items) {
  var bubbleChart = new d3.svg.BubbleChart({
    supportResponsive: true,
    container: ".bubbleChart",
    size: 500,
    supportResponsive: true,
      container: ".bubbleChart",
      size: 900,
      viewBoxSize: 400,
      innerRadius: 150,
      outerRadius: 1600,
      radiusMin: 8.5,
      radiusMax: 80,
      intersectDelta: 0,
      intersectInc: 2,
    data: {
      items,
      eval: function (item) {return item.count;},
      classed: function (item) {return item.text.split(" ").join("");}
    },
    plugins: [
      {
        name: "lines",
        options: {
          format: [
            {
              textField: "text",
              classed: {text: true},
              style: {
                "font-size": "18px",
                "font-family": "Source Sans Pro, sans-serif",
                "text-anchor": "middle",
                fill: "white"
              },
              attr: {
                dy: "0px",
                x: function (d) {return d.cx;},
                y: function (d) {return d.cy;}
              }
            },
            {
              textField: "count",
              classed: {count: true},
              style: {
                "font-size": "12px",
                "font-family": "Source Sans Pro, sans-serif",
                "text-anchor": "middle",
                fill: "white"
              },
              attr: {
                dy: "15px",
                x: function (d) {return d.cx;},
                y: function (d) {return d.cy;}
              }
            }
          ],
          centralFormat: [
            {
              style: {"font-size": "50px"},
              attr: {}
            },
            {
              style: {"font-size": "30px"},
              attr: {dy: "40px"}
            }
          ]
        }
      }]
    });
};

var getIndividualIDFromEncounterToString = function(encToString) {
  // return everything between "individualID=" and the next comma after that
console.log('encToString = %o', encToString);
  //var id = encToString.split("individualID=")[1].split(",")[0];
    var id = encToString.individualID;
    if (!id) return false;
  return id;
}

var getData = function(individualID) {
    var occurrenceObjectArray = [];
    var items = [];
    var encounterArray = [];
    var occurrenceArray = [];
    var dataObject = {};

     d3.json(wildbookGlobals.baseUrl + "/api/jdoql?SELECT%20FROM%20org.ecocean.Occurrence%20WHERE%20encounters.contains(enc)%20&&%20enc.individualID%20==%20%22" + individualID + "%22%20VARIABLES%20org.ecocean.Encounter%20enc", function(error, json) {
      if(error) {
        console.log("error")
      }
      var jsonData = json;
      for(var i=0; i < jsonData.length; i++) {
        var thisOcc = jsonData[i];
        var encounterSize = thisOcc.encounters.length;
        // make encounterArray, containing the individualIDs of every encounter in thisOcc;
        for(var j=0; j < encounterSize; j++) {
//console.info('[%d] %o %o', j, thisOcc.encounters, thisOcc.encounters[j]);
          var thisEncIndID = getIndividualIDFromEncounterToString(thisOcc.encounters[j]);
          //var thisEncIndID = jsonData[i].encounters[j].individualID;   ///only when we fix thisOcc.encounters to be real json   :(
//console.info('i=%d, j=%d, -> %o', i, j, thisEncIndID);
          if (!thisEncIndID) continue;  //unknown indiv -> false
          if(encounterArray.includes(thisEncIndID)) {
          } else {
            encounterArray.push(thisEncIndID);
          }
        }
        occurrenceArray = occurrenceArray.concat(encounterArray);
        var occurrenceID = jsonData[i].encounters[0].occurrenceID;
        var index = encounterArray.indexOf(individualID.toString());
        if (~index) {
            encounterArray[index] = "";
        }
        var occurrenceObject = new Object();
        if(encounterArray.length > 0) {
          occurrenceObject = {occurrenceID: occurrenceID, occurringWith: encounterArray.filter(function(e){return e}).join(", ")};
        } else {
          occurrenceObject = {occurrenceID: "", occurringWith: ""};
        }
        occurrenceObjectArray.push(occurrenceObject);
        encounterArray = [];
      }

      for(var i = 0; i < occurrenceArray.length; ++i) {
        if(!dataObject[occurrenceArray[i]])
        dataObject[occurrenceArray[i]] = 0;
        ++dataObject[occurrenceArray[i]];
      }
      for (var prop in dataObject) {
        var whale = new Object();
        whale = {text:prop, count:dataObject[prop], sex: "", haplotype: ""};
        items.push(whale);	
      }
      if (items.length > 0) {
        getSexHaploData(individualID, items);
      }
      getEncounterTableData(occurrenceObjectArray, individualID);
    });
  };

var getSexHaploData = function(individualID, items) {
  d3.json(wildbookGlobals.baseUrl + "/api/jdoql?SELECT%20FROM%20org.ecocean.MarkedIndividual%20WHERE%20encounters.contains(enc)%20&&%20occur.encounters.contains(enc)%20&&%20occur.encounters.contains(enc2)%20&&%20enc2.individualID%20==%20%22" + individualID + "%22%20VARIABLES%20org.ecocean.Encounter%20enc;org.ecocean.Encounter%20enc2;org.ecocean.Occurrence%20occur", function(error, json) {
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
    makeCooccurrenceChart(items);
    makeTable(items, "#coHead", "#coBody",null);
  });
};

var makeTable = function(items, tableHeadLocation, tableBodyLocation, sortOn) {
  var previousSort = null;
  refreshTable(sortOn);

  function refreshTable(sortOn) {
    var thead = d3.select(tableHeadLocation).selectAll("th")
    .data(d3.keys(items[0]))
    .enter().append("th").text(function(d){
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
        return dict['catalogNumber'];
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

    var tr = d3.select(tableBodyLocation).selectAll("tr").data(items);
    tr.enter().append("tr").attr("class", function(d){
      if(d.relationshipID !=null && d.relationshipID != 'undefined') {
        return	 d.relationshipID;
      }
      return d3.values(d)[0];
    });

    var td = tr.selectAll("td").data(function(d){return d3.values(d);});
    td.enter().append("td").html(function(d) {
      if(d == 'TissueSample') {
        return "<img class='encounterImg' src='images/microscope.gif'/>";
      } if(d == 'image') {
        return "<img class='encounterImg' src='images/Crystal_Clear_filesystem_folder_image.png'/>"
      } if(d == 'both') {
        return "<img class='encounterImg' src='images/microscope.gif'/><img class='encounterImg' src='images/Crystal_Clear_filesystem_folder_image.png'/>";
      }
      if(typeof d == "object") {
        if(d.length <= 2) {
          if(d[0] == 'edit'){
            return "<button type='button' name='button' value='" + d[1] + "' class='btn btn-sm btn-block editRelationshipBtn' id='edit" + d[1] + "'>Edit</button>";
          } if(d[0] == 'remove') {
            return "<button type='button' name='button' value='" + d[1] + "' class='btn btn-sm btn-block deleteRelationshipBtn' id='remove" + d[1] + "'>Remove</button><div class='confirmDelete' value='" + d[1] + "'><p>Are you sure you want to delete this relationship?</p><button class='btn btn-sm btn-block yesDelete' type='button' name='button' value='" +d[1]+ "'>Yes</button><button class='btn btn-sm btn-block cancelDelete' type='button' name='button' value='" + d[1] + "'>No</button></div>"
            ;
          }
          return d[0].italics() + "-" + d[1];
        }
        if(d.length > 2) {
          return "<a target='_blank' href='/individuals.jsp?number=" + d[0] + "'>" + d[0] + "</a><br><span>" + dict['nickname'] + " : " + d[1]+ "</span><br><span>" + dict['alternateID'] + ": " + d[2] + "</span><br><span>" + dict['sex'] + ": " + d[3] + "</span><br><span>" + dict['haplotype'] +": " + d[4] + "</span>";
          }
        }
        if(d == "GOS") {
          return "<a target='_blank' href='socialUnit.jsp?name=" + d + "'>" + d + "</a>"
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
        } if(d == 'image') {
          return "<img class='encounterImg' src='images/Crystal_Clear_filesystem_folder_image.png'/>"
        } if(d == 'both') {
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


var getEncounterTableData = function(occurrenceObjectArray, individualID) {
  var encounterData = [];
  var occurringWith = "";
    d3.json(wildbookGlobals.baseUrl + "/api/org.ecocean.MarkedIndividual/" + individualID, function(error, json) {
      if(error) {
        console.log("error")
      }
      jsonData = json;
      for(var i=0; i < jsonData.encounters.length; i++) {
        for(var j = 0; j < occurrenceObjectArray.length; j++) {
          if (occurrenceObjectArray[j].occurrenceID == jsonData.encounters[i].occurrenceID) {
            if(encounterData.includes(jsonData.encounters[i].occurrenceID)) {
            } else {
               var occurringWith = occurrenceObjectArray[j].occurringWith;
            }
          }
        }
        var dateInMilliseconds = new Date(jsonData.encounters[i].dateInMilliseconds);
        if(dateInMilliseconds > 0) {

          date = dateInMilliseconds.toISOString().substring(0, 10);
		  if(jsonData.encounters[i].day<1){date=date.substring(0,7);}
		  if(jsonData.encounters[i].month<0){date=date.substring(0,4);}

        } else {
          date = dict['unknown'];
        }
        if(jsonData.encounters[i].verbatimLocality) {
          var location = jsonData.encounters[i].verbatimLocality;
        } else {
          var location = "";
        }
        var catalogNumber = jsonData.encounters[i].catalogNumber;
        if(jsonData.encounters[i].tissueSamples || jsonData.encounters[i].annotations) {
          if((jsonData.encounters[i].tissueSamples)&&(jsonData.encounters[i].tissueSamples.length > 0)) {
            var dataTypes = jsonData.encounters[i].tissueSamples[0].type;
          } else if((jsonData.encounters[i].annotations)&&(jsonData.encounters[i].annotations.length > 0)) {
            var dataTypes = "image";
          } else if (jsonData.encounters[i].tissueSamples && jsonData.encounters[i].tissueSamples.length > 0 && jsonData.encounters[i].annotations.length > 0){
            var dataTypes = "both"
          } else {
            var dataTypes = "";
          }
        }
        var sex = jsonData.encounters[i].sex;
        var behavior = jsonData.encounters[i].behavior;
        var alternateID = jsonData.encounters[i].alternateid;
        var encounter = new Object();
        if(occurringWith === undefined) {
          var occurringWith = "";
        }
        encounter = {catalogNumber: catalogNumber, date: date, location: location, dataTypes: dataTypes, alternateID: alternateID, sex: sex, occurringWith: occurringWith, behavior: behavior};
        encounterData.push(encounter);
      }
      makeTable(encounterData, "#encountHead", "#encountBody", "date");
    });
  }

  var goToEncounterURL = function(selectedWhale) {
    window.open("/encounters/encounter.jsp?number=" + selectedWhale);
  }

  var goToWhaleURL = function(selectedWhale) {
    window.open("/individuals.jsp?number=" + selectedWhale);
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
  d3.json(wildbookGlobals.baseUrl + "/api/jdoql?SELECT%20FROM%20org.ecocean.social.Relationship%20WHERE%20(this.markedIndividualName1%20==%20%22" + individualID + "%22%20||%20this.markedIndividualName2%20==%20%22" + individualID + "%22)", function(error, json) {
    if(error) {
      console.log("error")
    }
    var relationshipArray = [];
    jsonData = json;
    for(var i = 0; i < jsonData.length; i++) {
      var relationshipID = jsonData[i]._id;
      var startTime = jsonData[i].startTime;
      var endTime = jsonData[i].endTime;
      if (startTime == "-1") {
        startTime = "Start Time";
      } 
      if (endTime == "-1") {
	endTime = "End Time";
      }
		
      if(jsonData[i].markedIndividualName1 != individualID) {
        var whaleID = jsonData[i].markedIndividualName1;
        var markedIndividual = jsonData[i].markedIndividualName2;
        var relationshipWithRole = jsonData[i].markedIndividualRole1;
        var markedIndividualRole = jsonData[i].markedIndividualRole2;
      }
      if(jsonData[i].markedIndividualName2 != individualID) {
        var whaleID = jsonData[i].markedIndividualName2;
        var markedIndividual = jsonData[i].markedIndividualName1;
        var markedIndividualRole = jsonData[i].markedIndividualRole1;
        var relationshipWithRole = jsonData[i].markedIndividualRole2;
      }
      var relatedSocialUnitName = jsonData[i].relatedSocialUnitName;
      var type = jsonData[i].type;
      var relationship = new Object();
      relationship = {roles: [markedIndividualRole, relationshipWithRole], relationshipWith: [whaleID], type: type, socialUnit: relatedSocialUnitName, edit: ["edit", relationshipID], remove: ["remove", relationshipID]};
      relationshipArray.push(relationship);
    }
    getIndividualData(relationshipArray);
  });
}

var getIndividualData = function(relationshipArray) {
  var relationshipTableData = [];
  for(var i=0; i < relationshipArray.length; i++) {
    d3.json(wildbookGlobals.baseUrl + "/api/org.ecocean.MarkedIndividual/" + relationshipArray[i].relationshipWith[0], function(error, json) {
      if(error) {
        console.log("error")
      }
      
      jsonData = json;
      var individualInfo = relationshipArray.filter(function(obj) {
        return obj.relationshipWith[0] === jsonData.individualID;
      })[0];
      console.log(individualInfo.relationshipWith);
      individualInfo.relationshipWith[1] = jsonData.nickName;
      individualInfo.relationshipWith[2] = jsonData.alternateid;
      individualInfo.relationshipWith[3] = jsonData.sex;
      individualInfo.relationshipWith[4] = jsonData.localHaplotypeReflection;
      relationshipTableData.push(individualInfo);

      if(relationshipTableData.length == relationshipArray.length) {
        for(var j = 0; j < relationshipArray.length; j++) {
          if(relationshipArray[j].relationshipWith.length == 1) {
            relationshipArray[j].relationshipWith[1] = jsonData.nickName;
            relationshipArray[j].relationshipWith[1] = jsonData.nickName;
            relationshipArray[j].relationshipWith[2] = jsonData.alternateid;
            relationshipArray[j].relationshipWith[3] = jsonData.sex;
            relationshipArray[j].relationshipWith[4] = jsonData.localHaplotypeReflection;
          }
	}
        makeTable(relationshipArray, "#relationshipHead", "#relationshipBody",null);
      }
    });
  }
}
