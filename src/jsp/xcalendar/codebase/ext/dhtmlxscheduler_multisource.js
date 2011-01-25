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
  function B(D) {
    var C = function() {
    };
    C.prototype = D;
    return C
  }

  var A = scheduler._load;
  scheduler._load = function(C, F) {
    C = C || this._load_url;
    if (typeof C == "object") {
      var E = B(this._loaded);
      for (var D = 0; D < C.length; D++) {
        this._loaded = new E();
        A.call(this, C[D], F)
      }
    } else {
      A.apply(this, arguments)
    }
  }
})();