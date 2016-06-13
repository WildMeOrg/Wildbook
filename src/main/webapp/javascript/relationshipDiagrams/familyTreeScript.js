//data for tree
var treeData = [
  {
    "name": "Big Whale",
    "parent": "null",
    "children": [
      {
        "name": "Big Whale's Baby",
        "parent": "Big Whale",
        "children": [
          {
            "name": "Baby's Son",
            "parent": "Big Whale's Baby"
          },
          {
            "name": "Baby's Daughter",
            "parent": "Big Whale's Baby"
          }
        ]
      },
      {
        "name": "Big Whale's Other Baby",
        "parent": "Big Whale"
      }
    ]
  }
];
//margin for svg canvas
var margin = {top: 0, right: 120, bottom: 0, left: 120},
  width = 960 - margin.right - margin.left,
  height = 350 - margin.top - margin.bottom;

var i = 0,
  duration = 400,
  root;

var tree = d3.layout.tree()
  .size([height, width]);

var diagonal = d3.svg.diagonal()
  .projection(function(d) { return [d.y, d.x]; });

var svg = d3.select("body")
  .append("svg")
    .attr("width", width + margin.right + margin.left)
    .attr("height", height + margin.top + margin.bottom)
  .append("g")
    .attr("transform", "translate(" + margin.left + "," + margin.top + ")");

var update = function(source) {
  // Compute the new tree layout.
  var nodes = tree.nodes(root).reverse(),
    links = tree.links(nodes);
  // Normalize for fixed-depth.
  nodes.forEach(function(d) { d.y = d.depth * 180; });
  // Update the nodes…
  var node = svg.selectAll("g.node")
    .data(nodes, function(d) { return d.id || (d.id = ++i); });
  // Enter any new nodes at the parent's previous position.
  var nodeEnter = node.enter().append("g")
    .attr("class", "node")
    .attr("transform", function(d) { return "translate(" + source.y0 + "," + source.x0 + ")"; })
    .on("click", click)
    .on("mouseover", mouseover)
    .on("mouseout", tip.hide);

  nodeEnter.append("circle")
    .attr("r", 1e-6)
    .style("fill", function(d) { return d._children ? "lightsteelblue" : "#fff"; });
  // Set text above or below nodes
  nodeEnter.append("text")
    .attr("y", function(d) { return d.children || d._children ? -22 : 22; })
    .attr("dy", ".35em")
    .attr("text-anchor", "middle")
    .text(function(d) { return d.name; })
    .style("fill-opacity", 1e-6);
  // Transition nodes to their new position.
  var nodeUpdate = node.transition()
    .duration(duration)
    .attr("transform", function(d) { return "translate(" + d.y + "," + d.x + ")"; })
    ;
  nodeUpdate.select("circle")
    .attr("r", 12)
    .style("fill", function(d) { return d._children ? "lightsteelblue" : "#fff"; });

  nodeUpdate.select("text")
    .style("fill-opacity", 1);
  // Transition exiting nodes to the parent's new position.
  var nodeExit = node.exit().transition()
    .duration(duration)
    .attr("transform", function(d) { return "translate(" + source.y + "," + source.x + ")"; })
    .remove();
  nodeExit.select("circle")
    .attr("r", 1e-6);
  nodeExit.select("text")
    .style("fill-opacity", 1e-6);
  // Update the links…
  var link = svg.selectAll("path.link")
    .data(links, function(d) { return d.target.id; });
  // Enter any new links at the parent's previous position.
  link.enter().insert("path", "g")
    .attr("class", "link")
    .attr("d", function(d) {
    var o = {x: source.x0, y: source.y0};
    return diagonal({source: o, target: o});
    });
  // Transition links to their new position.
  link.transition()
    .duration(duration)
    .attr("d", diagonal);
  // Transition exiting nodes to the parent's new position.
  link.exit().transition()
    .duration(duration)
    .attr("d", function(d) {
    var o = {x: source.x, y: source.y};
    return diagonal({source: o, target: o});
    })
    .remove();
  // Stash the old positions for transition.
  nodes.forEach(function(d) {
  d.x0 = d.x;
  d.y0 = d.y;
  });
  svg.call(tip);
}
//click function
var click = function(d) {
  if (d.children) {
  d._children = d.children;
  d.children = null;
  } else {
  d.children = d._children;
  d._children = null;
  }
  update(d);
}
//mouseover tip
var tip = d3.tip()
  .direction('n')
  .attr('class', 'd3-tip')
  .offset([-20, 0])
  .html(function(d) {
    return "Name: " + d.name + ". How much content can be inside this box before it starts getting all weird and stuff?";
  })
//mouseover function
var mouseover = function(d) {
  tip.show;
  //change circle color
  console.log("mouseover for " + d.name);
}

//feeds data into root
root = treeData[0];
root.x0 = height / 2;
root.y0 = 0;

d3.select(self.frameElement).style("height", "500px");


$(document).ready(function() {
  //loads tree with root data
  update(root);
});
