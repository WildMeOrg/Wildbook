  var legendData = [
         ["Male Sex"],
         ["Female Sex"],
         ["Unknown Sex"],
         ["Organism"],
         ["Paternal relations"],
         ["Maternal retlations"],
         ["Familal relations"]
       ];

var svg = d3.select("svg"),
    margin = {
        top: 70,
        right: 20,
        bottom: 30,
        left: 40
    },
    width = 500 - margin.left - margin.right,
    height = 500 - margin.top - margin.bottom;

var legend = svg.append("g")
    .attr("class", "legend")
    .attr("height", 0)
    .attr("width", 0)
    .attr('transform', 'translate(20,250)');

svg.selectAll('.legendSymbol')
    .data(legendData)
    .enter()
    .append('path');

function drawArrow(svg, x, y,color){
    svg.append('line').attr('x1', x).attr('x2', x+20)
        .attr('y1', y).attr('y2', y).attr('stroke', color)      
    svg.append('line').attr('x1', x+15).attr('x2', x+20)
        .attr('y1', y-5).attr('y2', y).attr('stroke', color)
        .attr('stroke-width' , 2)
    svg.append('line').attr('x1', x+15).attr('x2', x+20)
        .attr('y1', y+5).attr('y2', y).attr('stroke', color)
        .attr('stroke-width' , 2)
}

drawArrow(svg,175,30,"blue");
drawArrow(svg,175,50,"red");


svg.append('line').attr('x1',175).attr('x2',195)

    .attr('y1', 70).attr('y2', 70).attr('stroke', 'grey');

colors = ['blue', 'pink', 'grey']
var newcount= -1
svg.append('circle')
    .attr('cx', 185)
    .attr('cy',10)
    .attr('r', 10)
    .attr('fill','white')
    .attr('stroke', 'black');

svg.selectAll('.symbol')
    .data(legendData.slice(0,3))
    .enter()
    .append('rect')
    .attr('x', 20)
    .attr('y', (el,ind) => (20*ind)+2)
    .attr('width', 15)
    .attr('height', 15)
    .attr('stroke', 'black')
    .attr('fill', (el, ind) => colors[ind])

svg.selectAll('.label')
    .data(legendData)
    .enter()
    .append('text')
    .attr("x", function(d, i){
        if (i<3)
            return i="40";
        else
            return i= "200";
    })
    .attr("y", function(d, i){ 
        if (i>=3){
            newcount++
            return((newcount*20)+15);
        }
        else
            return((i * 20)+15);
    })

    .text(function(d) {
        return d[0];
    });
