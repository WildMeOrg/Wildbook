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
  return encToString.individualID;
  //  THE BELOW VERSION OF THIS FUNCTION WORKS IF ON A BRANCH WHERE,
  //  a RESTed Occurrence's Encounters are stored as Enc.toString(), rather than RESTed Encounters themselves.
  // return everything between "individualID=" and the next comma after that
  // return encToString.split("individualID=")[1].split(",")[0];
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
          var thisEnc = thisOcc.encounters[j];
          var thisEncIndID = getIndividualIDFromEncounterToString(thisEnc);
          if(encounterArray.includes(thisEncIndID)) {
          } else {
            encounterArray.push(thisEncIndID);
          }
        }
        occurrenceArray = occurrenceArray.concat(encounterArray);
        var occurrenceID = thisOcc.encounters[0].occurrenceID;
        var index = encounterArray.indexOf(individualID.toString());
        if (~index) {
            encounterArray[index] = "";
        }
        var occurrenceObject = new Object();
        if(encounterArray.length > 0) {
          occurrenceObject = {occurrenceID: occurrenceID, occurringWith: encounterArray.filter(function(e){return e}).join(", ")};
        } else {
          occurrenceObject = { occurrenceID: "", occurringWith: "" };
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
    //console.log("jsonData: "+JSON.stringify(jsonData));
    for(var i=0; i < jsonData.length; i++) {
      var result = items.filter(function(obj) {
        return obj.text === jsonData[i].individualID;
      })[0];
      if (!result) continue;
      result.sex = jsonData[i].sex;
      result.haplotype = jsonData[i].localHaplotypeReflection;
    }
    makeCooccurrenceChart(items);
    makeTable(items, "#coHead", "#coBody");
  });
};

var makeTable = function(items, tableHeadLocation, tableBodyLocation) {
  var previousSort = null;
  refreshTable(null);

  function refreshTable(sortOn) {
    var thead = d3.select(tableHeadLocation).selectAll("th")
    .data(d3.keys(items[0]))
    .enter().append("th").text(function(d){
      if(d === "text") {
      return "Occurring With";
      } if (d === "count"){
        return "# Co-occurrences";
      } if (d === "behavior") {
        return "Behavior";
      } if(d === "alternateID") {
        return "Alt ID";
      }if (d === "sex") {
        return "Sex";
      } if (d === "haplotype") {
        return "Haplotype";
      } if (d === "location") {
        return "Location";
      } if (d === "dataTypes") {
        return "Data Types";
      } if (d === "date") {
        return "Date";
      } if(d === "occurringWith") {
        return "Occurring With";
      } if(d === "catalogNumber") {
        return "Catalog Number";
      }
    })
    .on("click", function(d){ return refreshTable(d) ;});

    var tr = d3.select(tableBodyLocation).selectAll("tr").data(items);
    tr.enter().append("tr").attr("class", function(d){return d3.values(d)[0];});

    var td = tr.selectAll("td").data(function(d){return d3.values(d);});
    td.enter().append("td").text(function(d) {return d;});

    if(sortOn !== null) {
      if(sortOn != previousSort){
        tr.sort(function(a,b){return sort(a[sortOn], b[sortOn]);});
        previousSort = sortOn;
      } else {
        tr.sort(function(a,b){return sort(b[sortOn], a[sortOn]);});
        previousSort = null;
      }
      td.text(function(d) {return d;});
      $("td:contains('TissueSample')").html("<img class='encounterSample' src='images/microscope.gif'/>");
      $("td:contains('image')").html("<img class='encounterImg' src='images/Crystal_Clear_filesystem_folder_image.png'/>");
      $("td:contains('both')").html("<img class='encounterImg' src='images/Crystal_Clear_filesystem_folder_image.png'/>").append("<img class='encounterSample' src='images/microscope.gif'/>");
    }
  }

  function sort(a,b) {
    if(typeof a == "string"){
      if(a === "") {
      a = "0";
      }if (b === "") {
        b = "0";
      }
      var parseA = parseInt(a);
      if(parseA) {
        var whaleA = parseA;
        var whaleB = parseInt(b);
        return whaleA > whaleB ? 1 : whaleA == whaleB ? 0 : -1;
      } else
        return a.localeCompare(b);
    } else if(typeof a == "number") {
      return a > b ? 1 : a == b ? 0 : -1;
    } else if(typeof a == "boolean") {
      return b ? 1 : a ? -1 : 0;
    }
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
          date = "Unknown";
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
          } else if(jsonData.encounters[i].annotations.length > 0) {
            var dataTypes = "image";
          } else if (jsonData.encounters[i].tissueSamples.length > 0 && jsonData.encounters[i].annotations.length > 0){
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
      makeTable(encounterData, "#encountHead", "#encountBody");
    });
  }

  var goToEncounterURL = function(selectedWhale) {
    window.open(wildbookGlobals.baseUrl+"/encounters/encounter.jsp?number=" + selectedWhale);
  }

  var goToWhaleURL = function(selectedWhale) {
    window.open(wildbookGlobals.baseUrl+"/individuals.jsp?number=" + selectedWhale);
  }
