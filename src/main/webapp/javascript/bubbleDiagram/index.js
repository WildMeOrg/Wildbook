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
          text: "(See more detail)",
          style: {
            "font-size": "12px",
            "font-style": "italic",
            "font-family": "Source Sans Pro, sans-serif",
            //"font-weight": "700",
            "text-anchor": "middle",
            "fill": "white"
          },
          attr: {dy: "65px"},
          centralClick: function() {
            alert("Here is more details!!");
          }
        }
      },
      {
        name: "lines",
        options: {
          format: [
            {// Line #0
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
            {// Line #1
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
            {// Line #0
              style: {"font-size": "50px"},
              attr: {}
            },
            {// Line #1
              style: {"font-size": "30px"},
              attr: {dy: "40px"}
            }
          ]
        }
      }]
    });
}

var getData = function() {
    var items = [];
    var encounterArray = [];
    var occurrenceArray = [];
    var dataObject = {};
    var individualID = 5727;

     d3.json("http://www.flukebook.org/api/jdoql?SELECT%20FROM%20org.ecocean.Occurrence%20WHERE%20encounters.contains(enc)%20&&%20enc.individualID%20==%20%22" + individualID + "%22%20VARIABLES%20org.ecocean.Encounter%20enc", function(error, json) {
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
      console.log(items.length);
      return makeChart(items);
    });
  };


$(document).ready(function() {
  getData();
  $("#cooccurrenceTable").hide();

  $("#cooccurrenceDiagramTab").click(function (e) {
    e.preventDefault()
    $("#cooccurrenceDiagram").show();
    $("#cooccurrenceTable").hide();
    $("#cooccurrenceDiagramTab").addClass("active");
    $("#cooccurrenceTableTab").removeClass("active");
  });

  $("#cooccurrenceTableTab").click(function (e) {
    e.preventDefault()
    $("#cooccurrenceTable").show();
    $("#cooccurrenceDiagram").hide();
    $("#cooccurrenceTableTab").addClass("active");
    $("#cooccurrenceDiagramTab").removeClass("active");
  });
});
