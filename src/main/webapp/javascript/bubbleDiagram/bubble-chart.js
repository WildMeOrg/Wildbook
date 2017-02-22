  //d3-transform
  (function() {
    d3.svg.transform = function(chain) {
      var transforms = [];
      if (chain !== undefined) { transforms.push(chain) }

      function push(kind, args) {
        var n = args.length;

        transforms.push(function() {
          return kind + '(' + (n == 1 && typeof args[0] == 'function'
              ? args[0].apply(this, arr(arguments)) : args) + ')';
        });
      };

      function arr(args) {
        return Array.prototype.slice.call(args);
      }

      var my = function() {
        var that = this,
            args = arr(arguments);

        return transforms.map(function(f) {
          return f.apply(that, args);
        }).join(' ');
      };

      ['translate', 'rotate', 'scale', 'matrix', 'skewX', 'skewY'].forEach(function(t) {
        my[t] = function() {
          push(t, arr(arguments));
          return my;
        };
      });

      return my;
    };
  })();

  //bubble-chart
  (function (root, factory) {
    if (typeof define === 'function' && define.amd) {
      define(['microplugin'], factory);
    }
    else if (typeof exports === 'object') {
      module.exports = factory(require('microplugin'));
    }
    else {
      root.BubbleChart = factory(root.MicroPlugin);
    }
  }(this, function (MicroPlugin) {
    var pi2 = Math.PI * 2;

    d3.svg.BubbleChart = function (settings) {
      var self = this;
      var defaultViewBoxSize = settings.size;
      var defaultInnerRadius = 125;
      var defaultOuterRadius = 1600;
      var defaultRadiusMin = settings.size / 10;
      self.options = {};
      $.extend(self.options, {
        plugins: [],
        container: ".bubbleChart",
        viewBoxSize: defaultViewBoxSize,
        innerRadius: defaultInnerRadius,
        outerRadius: defaultOuterRadius,
        radiusMin: defaultRadiusMin,
        intersectDelta: 0,
        transitDuration: 1000
      }, settings);

      $.extend(self.options, {
        radiusMax: (self.options.outerRadius - self.options.innerRadius) / 2,
        intersectInc: self.options.intersectDelta
      }, settings);

      self.initializePlugins(self.options.plugins);
      self.setup();
      self.registerClickEvent(self.getNodes());
      self.moveToCentral(d3.select(".node"));
      function resetDiagram() {
        d3.selectAll("svg").remove();
        self.setup();
        self.registerClickEvent(self.getNodes());
        self.moveToCentral(d3.select(".node"));
      }
      d3.select("#reset").on('click', resetDiagram);

    };


    $.extend(d3.svg.BubbleChart.prototype, {
      getTransition: function() {
        return this.transition;
      },

      getClickedNode: function () {
        return this.clickedNode;
      },

      getCentralNode: function () {
        return this.centralNode;
      },

      getOptions: function () {
        return this.options;
      },

      randomCirclesPositions: function (delta) {
        var self = this;
        var circles = [];
        var interval = 0;
        var options = self.options;
        while (circles.length < self.options.data.items.length && ++interval < self.intervalMax) {
          var val = self.values[circles.length];
          var rad = 10 + Math.max((val * options.radiusMax) / self.valueMax, options.radiusMin);
          var dist = 5 + self.innerRadius + rad + 0 * (self.outerRadius - self.innerRadius - rad * 2);
          var angle = Math.random() * pi2 ;
          var cx = self.centralPoint + dist * Math.cos(angle);
          var cy = self.centralPoint + dist * Math.sin(angle);

          var hit = false;

          $.each(circles, function (i, circle) {
            var dx = circle.cx - cx;
            var dy = circle.cy - cy;
            var r = circle.r + rad;
            if (dx * dx + dy * dy < (r - delta * r - delta)) {
              console.log( r - delta );
              hit = true;
              return false;
            }
          });
          if (!hit) {
            circles.push({cx: cx, cy: cy, r: rad, item: self.items[circles.length]});
          }
        }
        if (circles.length < self.items.length) {
          if (delta === options.radiusMin) {
            throw {
              message: console.log("Not enough space for all bubbles. Please change the options."),
              options: options
            }
          }
          return self.randomCirclesPositions(delta + options.intersectInc);
        }
        return circles;
      },

      getValues: function () {
        var values = [];
        var self = this;
        $.each(self.items, function (i, item) {values.push(self.options.data.eval(item));});
        return values;
      },

      setup: function () {
          var self = this;
          var options = self.options;
          self.innerRadius = options.innerRadius;
          self.outerRadius = options.outerRadius;
          self.centralPoint = options.size / 2;
          self.intervalMax = options.size * options.size;
          self.items = options.data.items;
          self.values = self.getValues();
          self.valueMax = Math.max.apply(null, self.values);
          var width = 900,
              height = 900;

          var zoom = d3.behavior.zoom().scaleExtent([0.6, 10]).on("zoom", zoomed);

          self.svg = d3.select(options.container).append("svg")
          .attr({preserveAspectRatio: "xMidYMid", width: options.size, height: options.size, class: "bubbleChart"})
          .attr("viewBox", function (d) {return [options.viewBoxSize/2 + 10, options.viewBoxSize/2 + 55, options.viewBoxSize+100, options.viewBoxSize].join(" ")})
          .call(zoom)
          .append("g");

        function zoomed() {
          self.svg.attr("transform",
              "translate(" + zoom.translate() + ")" +
              "scale(" + zoom.scale() + ")"
          );
        }

        function interpolateZoom (translate, scale) {

            var self = this;
            return d3.transition().duration(350).tween("zoom", function () {
                var iTranslate = d3.interpolate(zoom.translate(), translate),
                    iScale = d3.interpolate(zoom.scale(), scale);
                return function (t) {
                    zoom
                        .scale(iScale(t))
                        .translate(iTranslate(t));
                    zoomed();
                };
            });
        }

        function zoomClick() {
          var clicked = d3.event.target,
              direction = 0.5,
              factor = 0.2,
              target_zoom = 1,
              center = [width / 2, height / 2],
              extent = zoom.scaleExtent(),
              translate = zoom.translate(),
              translate0 = [],
              l = [],
              view = {x: translate[0], y: translate[1], k: zoom.scale()};

          d3.event.preventDefault();
          direction = (this.id === 'zoomIn') ? 1 : -1;
          target_zoom = zoom.scale() * (1 + factor * direction);

          if (target_zoom < extent[0] || target_zoom > extent[1]) { return false; }

          translate0 = [(center[0] - view.x) / view.k, (center[1] - view.y) / view.k];
          view.k = target_zoom;
          l = [translate0[0] * view.k + view.x, translate0[1] * view.k + view.y];

          view.x += center[0] - l[0];
          view.y += center[1] - l[1];

          interpolateZoom([view.x, view.y], view.k);
        }

        d3.select('#zoomIn').on('click', zoomClick);
        d3.select('#zoomOut').on('click', zoomClick);

        self.circlePositions = self.randomCirclesPositions(options.intersectDelta);

        var node = self.svg.selectAll(".node")
          .data(self.circlePositions)
          .enter().append("g")
          .attr("class", function (d) {return ["node", options.data.classed(d.item)].join(" w");});

        var fnColor = d3.scale.category20();

        node.append("circle")
          .attr({r: function (d) {return d.r;}, cx: function (d) {return d.cx;}, cy: function (d) {return d.cy;}})
          .style("fill", function (d) {
                if(d.item.sex == "female") {
                  return "pink";
                } else if (d.item.sex == "male") {
                  return "deepskyblue";
                } else {
                  return "lightgray";
                }
            })
          .attr("opacity", "0.8");
        node.sort(function (a, b) {return options.data.eval(b.item) - options.data.eval(a.item);});

        self.transition = {};
        self.event = $.microObserver.get($.misc.uuid());

        if (options.supportResponsive) {
          $(window).resize(function() {
            var width = $(options.container).width();
            self.svg.attr("width", width);
            self.svg.attr("height", width);
          });
          $(window).resize();
        }
      },

      getCirclePositions: function () {
        return this.circlePositions;
      },

      moveToCentral: function (node) {
        var self = this;
        var toCentralPoint = d3.svg.transform()
          .translate(function (d) {
            var cx = node.select('circle').attr("cx");
            var dx = self.centralPoint - d.cx;
            var dy = self.centralPoint - d.cy;
            return [dx, dy];
          });
        self.centralNode = node;
        self.transition.centralNode = node.classed({active: true})
          .transition().duration(self.options.transitDuration);
        self.transition.centralNode.attr('transform', toCentralPoint)
          .select("circle")
          .style("fill", function (d) {
                if(d.item.sex == "female") {
                  return "pink";
                } else if (d.item.sex == "male") {
                  return "deepskyblue";
                } else {
                  return "lightgray";
                }
            })
          .style("stroke", "white")
          .style("stroke-width", "5px")
          .attr('r', function (d) {return self.options.innerRadius;});
      },

      moveToReflection: function (node, swapped) {
        var self = this;
        var toReflectionPoint = d3.svg.transform()
          .translate(function (d) {
            var dx = 2 * (self.centralPoint - d.cx);
            var dy = 2 * (self.centralPoint - d.cy);
            return [dx, dy];
          });

        node.transition()
          .duration(self.options.transitDuration)
          .delay(function (d, i) {return i * 10;})
          .attr('transform', swapped ? "" : toReflectionPoint)
          .select("circle")
          .attr('r', function (d) {return d.r;});
      },

      reset: function (node) {
        var self = this;
        var fnColor = d3.scale.category20();
        node.classed({active: false});
        d3.selectAll(".node:not(.active) circle").style("fill", function (d) {
                if(d.item.sex == "female") {
                  return "pink";
                } else if (d.item.sex == "male") {
                  return "deepskyblue";
                } else {
                  return "lightgray";
                }
            }).style("stroke", "none");
        d3.selectAll("circle").filter(function(d) {return d.r == 90;}).style("fill", function (d) {
                if(d.item.sex == "female") {
                  return "pink";
                } else if (d.item.sex == "male") {
                  return "deepskyblue";
                } else {
                  return "lightgray";
                }
            })
            .style("stroke", "white").style("stroke-width", "5px");
      },

      registerClickEvent: function (node) {
        var self = this;
        var swapped = false;
        node.style("cursor", "pointer").on("click", function (d) {
          self.clickedNode = d3.select(this);
          self.event.send("click", self.clickedNode);
          self.reset(self.centralNode);
          self.moveToCentral(self.clickedNode);
          self.moveToReflection(self.svg.selectAll(".node:not(.active)"), swapped);
          swapped = !swapped;

        });
      },

      getNodes: function () {
        return this.svg.selectAll(".node");
      },

      get: function () {
        return this.svg;
      }
    });

    MicroPlugin.mixin(d3.svg.BubbleChart);

    return d3.svg.BubbleChart;
  }));


  //lines
  d3.svg.BubbleChart.define("lines", function (options) {
    var self = this;

    self.setup = (function () {
      var original = self.setup;
      return function () {
        var fn = original.apply(this, arguments);
        var node = self.getNodes();
        $.each(options.format, function (i, f) {
          node.append("text")
            .classed(f.classed)
            .style(f.style)
            .attr(f.attr)
            .text(function (d) {return d.item[f.textField];});
        });
        return fn;
      };
    })();

    self.reset = (function (node) {
      var original = self.reset;
      return function (node) {
        var fn = original.apply(this, arguments);
        $.each(options.format, function (i, f) {
          var tNode = d3.select(node.selectAll("text")[0][i]);
          tNode.classed(f.classed).text(function (d) {return d.item[f.textField];})
            .transition().duration(self.getOptions().transitDuration)
            .style(f.style)
            .attr(f.attr);
        });
        return fn;
      };
    })();

    self.moveToCentral = (function (node) {
      var original = self.moveToCentral;
      return function (node) {
        var fn = original.apply(this, arguments);
        $.each(options.centralFormat, function (i, f) {
          var tNode = d3.select(node.selectAll("text")[0][i]);
          tNode.transition().duration(self.getOptions().transitDuration)
            .style(f.style)
            .attr(f.attr);
          f.classed !== undefined && tNode.classed(f.classed);
          f.textField !== undefined && tNode.text(function (d) {return d.item[f.textField];});
        });
        return fn;
      };
    })();
  });