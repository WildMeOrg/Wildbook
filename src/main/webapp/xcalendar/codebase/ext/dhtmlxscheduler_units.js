/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

scheduler._props = {};
scheduler.createUnitsView = function(B, E, D) {
  scheduler.date[B + "_start"] = scheduler.date.day_start;
  scheduler.templates[B + "_date"] = function(F) {
    return scheduler.templates.day_date(F)
  };
  scheduler.templates[B + "_scale_date"] = function(F) {
    return scheduler.templates.day_scale_date(F)
  };
  scheduler.templates[B + "_scale_date"] = function(F) {
    return D[Math.floor((F.valueOf() - scheduler._min_date.valueOf()) / (60 * 60 * 24 * 1000))].label
  };
  scheduler.date["add_" + B] = function(F, G) {
    return scheduler.date.add(F, G, "day")
  };
  scheduler.date["get_" + B + "_end"] = function(F) {
    return scheduler.date.add(F, D.length, "day")
  };
  var A = {};
  for (var C = 0; C < D.length; C++) {
    A[D[C].key] = C
  }
  scheduler._props[B] = {map_to:E,options:D,order:A}
};
(function() {
  var D = function(K, I) {
    if (K && typeof I[K.map_to] == "undefined") {
      var H = scheduler;
      var G = 24 * 60 * 60 * 1000;
      var J = Math.floor((I.end_date - H._min_date) / G);
      I.end_date = new Date(H.date.time_part(I.end_date) * 1000 + H._min_date.valueOf());
      I.start_date = new Date(H.date.time_part(I.start_date) * 1000 + H._min_date.valueOf());
      I[K.map_to] = K.options[J].key;
      return true
    }
  };
  var B = scheduler._reset_scale;
  scheduler._reset_scale = function() {
    var H = scheduler._props[this._mode];
    var G = B.apply(this, arguments);
    if (H) {
      this._max_date = this.date.add(this._min_date, 1, "day")
    }
    return G
  };
  var C = scheduler._get_event_sday;
  scheduler._get_event_sday = function(G) {
    var H = scheduler._props[this._mode];
    if (H) {
      D(H, G);
      return H.order[G[H.map_to]]
    }
    return C.call(this, G)
  };
  var A = scheduler.locate_holder_day;
  scheduler.locate_holder_day = function(H, G, I) {
    var J = scheduler._props[this._mode];
    if (J) {
      D(J, I);
      return J.order[I[J.map_to]] * 1 + (G ? 1 : 0)
    }
    return A.apply(this, arguments)
  };
  var E = scheduler._mouse_coords;
  scheduler._mouse_coords = function() {
    var I = scheduler._props[this._mode];
    var H = E.apply(this, arguments);
    if (I) {
      var G = this._drag_event;
      if (this._drag_id) {
        G = this.getEvent(this._drag_id);
        this._drag_event.start_date = new Date()
      }
      G[I.map_to] = I.options[H.x].key;
      H.x = 0
    }
    return H
  };
  var F = scheduler._time_order;
  scheduler._time_order = function(G) {
    var H = scheduler._props[this._mode];
    if (H) {
      G.sort(function(J, I) {
        return H.order[J[H.map_to]] > H.order[I[H.map_to]] ? 1 : -1
      })
    } else {
      F.apply(this, arguments)
    }
  };
  scheduler.attachEvent("onEventAdded", function(J, H) {
    if (this._loading) {
      return true
    }
    for (var G in scheduler._props) {
      var I = scheduler._props[G];
      if (typeof H[I.map_to] == "undefined") {
        H[I.map_to] = I.options[0].key
      }
    }
    return true
  });
  scheduler.attachEvent("onEventCreated", function(J, G) {
    var I = scheduler._props[this._mode];
    var H = this.getEvent(J);
    this._mouse_coords(G);
    D(I, H);
    this.event_updated(H);
    return true
  })
})();