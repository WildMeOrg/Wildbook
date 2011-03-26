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
  var A = false;
  scheduler.attachEvent("onBeforeLightbox", function() {
    A = true;
    return true
  });
  scheduler.attachEvent("onAfterLightbox", function() {
    A = false;
    return true
  });
  dhtmlxEvent(document, (_isOpera ? "keypress" : "keydown"), function(C) {
    C = C || event;
    if (!A) {
      if (C.keyCode == 37 || C.keyCode == 39) {
        C.cancelBubble = true;
        var B = scheduler.date.add(scheduler._date, (C.keyCode == 37 ? -1 : 1), scheduler._mode);
        scheduler.setCurrentView(B);
        return true
      }
    }
  })
})();