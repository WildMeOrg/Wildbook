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

scheduler.config.year_x = 4;
scheduler.config.year_y = 3;
scheduler.templates.year_date = scheduler.date.date_to_str(scheduler.locale.labels.year_tab + " %Y");
scheduler.templates.year_month = scheduler.date.date_to_str("%F");
scheduler.templates.year_scale_date = scheduler.date.date_to_str("%D");
scheduler.templates.year_tooltip = function(A, C, B) {
  return B.text
};
(function() {
  scheduler.dblclick_dhx_month_head = function(I) {
    if (this._mode != "year") {
      return
    }
    var H = (I.target || I.srcElement);
    if (H.parentNode.className.indexOf("dhx_before") != -1 || H.parentNode.className.indexOf("dhx_after") != -1) {
      return false
    }
    var J = this.templates.xml_date(H.parentNode.parentNode.parentNode.parentNode.parentNode.parentNode.getAttribute("date"));
    J.setDate(parseInt(H.innerHTML, 10));
    var G = this.date.add(J, 1, "day");
    if (!this.config.readonly && this.config.dblclick_create) {
      this.addEventNow(J.valueOf(), G.valueOf(), I)
    }
  };
  var C = scheduler.changeEventId;
  scheduler.changeEventId = function() {
    C.apply(this, arguments);
    if (this._mode == "year") {
      this.year_view(true)
    }
  };
  var B = scheduler.render_data;
  var F = scheduler.date.date_to_str("%Y/%m/%d");
  var E = scheduler.date.str_to_date("%Y/%m/%d");
  scheduler.render_data = function(G) {
    if (this._mode != "year") {
      return B.apply(this, arguments)
    }
    for (var H = 0; H < G.length; H++) {
      this._year_render_event(G[H])
    }
  };
  var A = scheduler.clear_view;
  scheduler.clear_view = function() {
    if (this._mode != "year") {
      return A.apply(this, arguments)
    }
    for (var G = 0; G < D.length; G++) {
      D[G].className = "dhx_month_head";
      D[G].setAttribute("date", "")
    }
    D = []
  };
  scheduler.hideToolTip = function() {
    if (this._tooltip) {
      this._tooltip.style.display = "none";
      this._tooltip.date = new Date(9999, 1, 1)
    }
  };
  scheduler.showToolTip = function(H, N, L, M) {
    if (this._tooltip) {
      if (this._tooltip.date.valueOf() == H.valueOf()) {
        return
      }
      this._tooltip.innerHTML = ""
    } else {
      var K = this._tooltip = document.createElement("DIV");
      K.className = "dhx_tooltip";
      document.body.appendChild(K);
      K.onclick = scheduler._click.dhx_cal_data
    }
    var G = this.getEvents(H, this.date.add(H, 1, "day"));
    var J = "";
    for (var I = 0; I < G.length; I++) {
      J += "<div class='dhx_tooltip_line' event_id='" + G[I].id + "'>";
      J += "<div class='dhx_tooltip_date'>" + (G[I]._timed ? this.templates.event_date(G[I].start_date) : "") + "</div>";
      J += "<div class='dhx_event_icon icon_details'>&nbsp;</div>";
      J += this.templates.year_tooltip(G[I].start_date, G[I].end_date, G[I]) + "</div>"
    }
    this._tooltip.style.display = "";
    this._tooltip.style.top = "0px";
    if (document.body.offsetWidth - N.left - this._tooltip.offsetWidth < 0) {
      this._tooltip.style.left = N.left - this._tooltip.offsetWidth + "px"
    } else {
      this._tooltip.style.left = N.left + M.offsetWidth + "px"
    }
    this._tooltip.date = H;
    this._tooltip.innerHTML = J;
    if (document.body.offsetHeight - N.top - this._tooltip.offsetHeight < 0) {
      this._tooltip.style.top = N.top - this._tooltip.offsetHeight + M.offsetHeight + "px"
    } else {
      this._tooltip.style.top = N.top + "px"
    }
  };
  scheduler._init_year_tooltip = function() {
    dhtmlxEvent(scheduler._els.dhx_cal_data[0], "mouseover", function(G) {
      if (scheduler._mode != "year") {
        return
      }
      var G = G || event;
      var H = G.target || G.srcElement;
      if ((H.className || "").indexOf("dhx_year_event") != -1) {
        scheduler.showToolTip(E(H.getAttribute("date")), getOffset(H), G, H)
      } else {
        scheduler.hideToolTip()
      }
    });
    this._init_year_tooltip = function() {
    }
  };
  scheduler.attachEvent("onSchedulerResize", function() {
    if (this._mode == "year") {
      this.year_view(true);
      return false
    }
    return true
  });
  scheduler._get_year_cell = function(I) {
    var G = I.getMonth() + 12 * (I.getFullYear() - this._min_date.getFullYear());
    var H = this._els.dhx_cal_data[0].childNodes[G];
    var I = this.week_starts[G] + I.getDate() - 1;
    return H.childNodes[2].firstChild.rows[Math.floor(I / 7)].cells[I % 7].firstChild
  };
  var D = [];
  scheduler._mark_year_date = function(G) {
    var H = this._get_year_cell(G);
    H.className = "dhx_month_head dhx_year_event";
    H.setAttribute("date", F(G));
    D.push(H)
  };
  scheduler._unmark_year_date = function(G) {
    this._get_year_cell(G).className = "dhx_month_head"
  };
  scheduler._year_render_event = function(G) {
    var H = G.start_date;
    while (H < G.end_date) {
      this._mark_year_date(H);
      H = this.date.add(H, 1, "day")
    }
  };
  scheduler.year_view = function(G) {
    scheduler.xy.nav_height = G ? 1 : 22;
    scheduler._els.dhx_cal_header[0].style.display = G ? "none" : "";
    scheduler.set_sizes();
    scheduler._table_view = G;
    if (this._load_mode && this._load()) {
      return
    }
    if (G) {
      scheduler._init_year_tooltip();
      scheduler._reset_year_scale();
      scheduler.render_view_data()
    } else {
      scheduler.hideToolTip()
    }
  };
  scheduler._reset_year_scale = function() {
    this._cols = [];
    this._colsS = {};
    var Q = [];
    var X = this._els.dhx_cal_data[0];
    var V = this.config;
    X.innerHTML = "";
    var K = Math.floor(parseInt(X.style.width) / V.year_x);
    var J = Math.floor(parseInt(X.style.height) / V.year_y);
    if (J < 190) {
      J = 190;
      K = Math.floor((parseInt(X.style.width) - scheduler.xy.scroll_width) / V.year_x)
    }
    var O = K - 11;
    var H = 0;
    var I = document.createElement("div");
    var Y = this.date.week_start(new Date());
    for (var T = 0; T < 7; T++) {
      this._cols[T] = Math.floor(O / (7 - T));
      this._render_x_header(T, H, Y, I);
      Y = this.date.add(Y, 1, "day");
      O -= this._cols[T];
      H += this._cols[T]
    }
    I.lastChild.className += " dhx_scale_bar_last";
    var G = this.date[this._mode + "_start"](this.date.copy(this._date));
    var P = G;
    for (var T = 0; T < V.year_y; T++) {
      for (var S = 0; S < V.year_x; S++) {
        var U = document.createElement("DIV");
        U.style.cssText = "position:absolute;";
        U.setAttribute("date", this.templates.xml_format(G));
        U.innerHTML = "<div class='dhx_year_month'></div><div class='dhx_year_week'>" + I.innerHTML + "</div><div class='dhx_year_body'></div>";
        U.childNodes[0].innerHTML = this.templates.year_month(G);
        var W = this.date.week_start(G);
        this._reset_month_scale(U.childNodes[2], G, W);
        var N = U.childNodes[2].firstChild.rows;
        for (var R = N.length; R < 6; R++) {
          N[0].parentNode.appendChild(N[0].cloneNode(true));
          for (var M = 0; M < N[R].childNodes.length; M++) {
            N[R].childNodes[M].className = "dhx_after"
          }
        }
        X.appendChild(U);
        var L = Math.round((J - 190) / 2);
        U.style.marginTop = L + "px";
        this.set_xy(U, K - 10, J - L - 10, K * S + 5, J * T + 5);
        Q[T * V.year_x + S] = (G.getDay() - (this.config.start_on_monday ? 1 : 0) + 7) % 7;
        G = this.date.add(G, 1, "month")
      }
    }
    this._els.dhx_cal_date[0].innerHTML = this.templates[this._mode + "_date"](P, G, this._mode);
    this.week_starts = Q;
    this._min_date = P;
    this._max_date = G
  }
})();