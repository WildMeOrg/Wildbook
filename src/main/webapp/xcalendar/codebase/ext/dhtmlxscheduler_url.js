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

scheduler.attachEvent("onTemplatesReady", function() {
  var C = true;
  var A = scheduler.date.str_to_date("%Y-%m-%d");
  var B = scheduler.date.date_to_str("%Y-%m-%d");
  scheduler.attachEvent("onBeforeViewChange", function(J, D, F, I) {
    if (C) {
      C = false;
      var E = {};
      var G = (document.location.hash || "").replace("#", "").split(",");
      for (var H = 0; H < G.length; H++) {
        var L = G[H].split("=");
        if (L.length == 2) {
          E[L[0]] = L[1]
        }
      }
      if (E.date || E.mode) {
        this.setCurrentView((E.date ? A(E.date) : null), (E.mode || null));
        return false
      }
    }
    var K = "#date=" + B(I || D) + ",mode=" + (F || J);
    document.location.hash = K;
    return true
  })
});