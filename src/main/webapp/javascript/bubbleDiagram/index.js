$(document).ready(function() {

  var bubbleChart = new d3.svg.BubbleChart({
    supportResponsive: true,
    container: ".bubbleChart",
    size: 900,
    viewBoxSize: 500,
    innerRadius: 170,
    outerRadius: 1600,
    radiusMin: 8.5,
    radiusMax: 80,
    intersectDelta: 0,
    intersectInc: 2,

    data: {
      items: [
        {text: "SCAR", count: "100"},
        {text: "5560", count: "91"},
        {text: "5561", count: "75"},
        {text: "5722", count: "63"},
        {text: "6070", count: "45"},
        {text: "6068", count: "35"},
        {text: "5703", count: "33"},
        {text: "5562", count: "19"},
        {text: "5988", count: "13"},
        {text: "5151", count: "13"},
        {text: "6035", count: "12"},
        {text: "5563", count: "6"},
        {text: "5130", count: "4"},
        {text: "6094", count: "2"},
        {text: "5940", count: "1"},
        {text: "5944", count: "1"},
        {text: "5981", count: "1"},
        {text: "5733", count: "1"},
        {text: "5759", count: "1"},
        {text: "5742", count: "1"},
        {text: "5707", count: "1"},
        {text: "5706", count: "1"},
        {text: "5732", count: "1"},
      ],
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
});
