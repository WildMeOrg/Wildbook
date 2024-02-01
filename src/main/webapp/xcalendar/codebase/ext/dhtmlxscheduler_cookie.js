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

(function() {
  function B(E, D, F) {
    var G = E + "=" + F + (D ? ("; " + D) : "");
    document.cookie = G
  }

  function A(E) {
    var F = E + "=";
    if (document.cookie.length > 0) {
      var G = document.cookie.indexOf(F);
      if (G != -1) {
        G += F.length;
        var D = document.cookie.indexOf(";", G);
        if (D == -1) {
          D = document.cookie.length
        }
        return document.cookie.substring(G, D)
      }
    }
    return""
  }

  var C = true;
  scheduler.attachEvent("onBeforeViewChange", function(F, E, D, I) {
    if (C) {
      C = false;
      var G = A("scheduler_settings");
      if (G) {
        G = G.split("@");
        G[0] = this.templates.xml_date(G[0]);
        this.setCurrentView(G[0], G[1]);
        return false
      }
    }
    var H = this.templates.xml_format(I || E) + "@" + (D || F);
    B("scheduler_settings", "expires=Sun, 31 Jan 9999 22:00:00 GMT", H);
    return true
  })
})();