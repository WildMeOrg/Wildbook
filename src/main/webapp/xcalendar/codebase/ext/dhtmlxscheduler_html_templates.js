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
  var B = document.body.getElementsByTagName("DIV");
  for (var A = 0; A < B.length; A++) {
    var C = B[A].className || "";
    C = C.split(":");
    if (C.length == 2 && C[0] == "template") {
      var D = 'return "' + (B[A].innerHTML || "").replace(/\"/g, '\\"').replace(/[\n\r]+/g, "") + '";';
      D = unescape(D).replace(/\{event\.([a-z]+)\}/g, function(F, E) {
        return'"+ev.' + E + '+"'
      });
      scheduler.templates[C[1]] = Function("start", "end", "ev", D);
      B[A].style.display = "none"
    }
  }
});