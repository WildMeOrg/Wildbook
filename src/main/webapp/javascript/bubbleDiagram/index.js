var makeChart = function(items) {

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
        name: "central-click",
        options: {
          style: {
            "font-size": "12px",
            "font-style": "italic",
            "font-family": "Source Sans Pro, sans-serif",
            "text-anchor": "middle",
            "fill": "white"
          },
          attr: {dy: "65px"},
          centralClick: function() {
          }
        }
      },
      {
        name: "lines",
        options: {
          format: [
            {
              textField: "count",
              classed: {count: true},
              style: {
                "font-size": "20px",
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
              textField: "text",
              classed: {text: true},
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
}

var items = [];
var getData = function(individualID) {
    var encounterArray = [];
    var occurrenceArray = [];
    var dataObject = {};

     d3.json('http://www.flukebook.org/api/jdoql?SELECT%20FROM%20org.ecocean.Occurrence%20WHERE%20encounters.contains(enc)%20&&%20enc.individualID%20==%20"' + individualID + '"%20VARIABLES%20org.ecocean.Encounter%20enc', function(error, json) {
      if(error) {
        console.log("error")
      }
      jsonData = json;
      for(var i=0; i < jsonData.length; i++) {
        var encounterSize = jsonData[i].encounters.length;
        for(var j=0; j < encounterSize; j++) {
          if(encounterArray.includes(jsonData[i].encounters[j].individualID)) {
          } else {
            encounterArray.push(jsonData[i].encounters[j].individualID);
          }
        }
        occurrenceArray = occurrenceArray.concat(encounterArray);
        encounterArray = [];
      }

      for(var i = 0; i < occurrenceArray.length; ++i) {
        if(!dataObject[occurrenceArray[i]])
        dataObject[occurrenceArray[i]] = 0;
        ++dataObject[occurrenceArray[i]];
      }
      for (var prop in dataObject) {
        var whale = new Object();
        whale = {text:prop, count:dataObject[prop]};
        items.push(whale);
      }
      makeChart(items);
    });
  };

  var getTableData = function(individualID) {
    d3.json("http://www.flukebook.org/api/jdoql?SELECT%20FROM%20org.ecocean.MarkedIndividual%20WHERE%20encounters.contains(enc)%20&&%20occur.encounters.contains(enc)%20&&%20occur.encounters.contains(enc2)%20&&%20enc2.individualID%20==%20%22" + individualID + "%22%20VARIABLES%20org.ecocean.Encounter%20enc;org.ecocean.Encounter%20enc2;org.ecocean.Occurrence%20occur", function(error, json) {
      if(error) {
        console.log("error")
      }
      jsonData = json;
      for(var i=0; i < jsonData.length; i++) {
        var result = items.filter(function(obj) {
          return obj.text === jsonData[i].individualID
        })[0];
        result.sex = jsonData[i].sex;
        result.haplotype = jsonData[i].localHaplotypeReflection;
      }
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
        return "Occuring With";
      } if (d === "count"){
        return "# Co-occurrences";
      } if (d === "sex") {
        return "Sex";
      } if (d === "haplotype") {
        return "Haplotype";
      } if (d === "location") {
        return "Location";
      } if (d === "dataTypes") {
        return "Data Types";
      } if (d === "date"); {
        return "Date";
      };
    })
    .on("click", function(d){ return refreshTable(d) ;});

    var tr = d3.select(tableBodyLocation).selectAll("tr").data(items);
    tr.enter().append("tr");

    var td = tr.selectAll("td").data(function(d){return d3.values(d);});
    td.enter().append("td")
    .text(function(d) {return d;});

    if(sortOn !== null) {
      if(sortOn != previousSort){
        tr.sort(function(a,b){return sort(a[sortOn], b[sortOn]);});
        previousSort = sortOn;
      } else {
        tr.sort(function(a,b){return sort(b[sortOn], a[sortOn]);});
        previousSort = null;
      }
      td.text(function(d) {return d;});
    }
  }

  function sort(a,b) {
    if(typeof a == "string"){
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

var getTableData = function(individualID) {
    d3.json("http://www.flukebook.org/api/jdoql?SELECT%20FROM%20org.ecocean.MarkedIndividual%20WHERE%20encounters.contains(enc)%20&&%20occur.encounters.contains(enc)%20&&%20occur.encounters.contains(enc2)%20&&%20enc2.individualID%20==%20%22" + individualID + "%22%20VARIABLES%20org.ecocean.Encounter%20enc;org.ecocean.Encounter%20enc2;org.ecocean.Occurrence%20occur", function(error, json) {
      if(error) {
        console.log("error")
      }
      jsonData = json;
      for(var i=0; i < jsonData.length; i++) {
        var result = items.filter(function(obj) {
          return obj.text === jsonData[i].individualID
        })[0];
        result.sex = jsonData[i].sex;
        result.haplotype = jsonData[i].localHaplotypeReflection;
      }
      makeTable(items, "#coHead", "#coBody");
    });
  };

  var encounterData = [];
  var occuringWith = [];
  var date;

  var getEncounterTableData = function(individualID) {
    d3.json("http://www.flukebook.org/api/org.ecocean.MarkedIndividual/" + individualID + "", function(error, json) {
      if(error) {
        console.log("error")
      }
      jsonData = json;
      for(var i=0; i < jsonData.encounters.length; i++) {
        var dateInMilliseconds = new Date(jsonData.encounters[i].dateInMilliseconds);
        if(dateInMilliseconds > 0) {
          date = dateInMilliseconds.toISOString().substring(0, 10);
        } else {
          date = "Unknown";
        };
        var location = jsonData.encounters[i].verbatimLocality;

        var dataTypes = jsonData.encounters[i].catalogNumber;
        var sex = jsonData.encounters[i].sex;

        var encounter = new Object();
        encounter = {date: date, location: location, dataTypes: dataTypes, sex: sex};
        encounterData.push(encounter);
      }
      makeTable(encounterData, "#encountHead", "#encountBody");
    });
  };
