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
  var B = scheduler.date.str_to_date(scheduler.config.api_date);
  var C = scheduler.date.date_to_str(scheduler.config.api_date);
  var D = scheduler.templates.month_day;
  scheduler.templates.month_day = function(E) {
    return"<a jump_to='" + C(E) + "' href='#'>" + D(E) + "</a>"
  };
  var A = scheduler.templates.week_scale_date;
  scheduler.templates.week_scale_date = function(E) {
    return"<a jump_to='" + C(E) + "' href='#'>" + A(E) + "</a>"
  };
  dhtmlxEvent(this._obj, "click", function(E) {
    var G = E.target || event.srcElement;
    var F = G.getAttribute("jump_to");
    if (F) {
      scheduler.setCurrentView(B(F), "day")
    }
  })
});