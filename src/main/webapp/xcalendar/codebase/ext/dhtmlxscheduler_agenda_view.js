

scheduler.date.add_agenda = function(A) {
  return(new Date(A.valueOf()))
};
scheduler.dblclick_dhx_agenda_area = function() {
  if (!this.config.readonly && this.config.dblclick_create) {
    this.addEventNow()
  }
};
scheduler.templates.agenda_time = function(C, A, B) {
  if (B._timed) {
    return this.day_date(B.start_date, B.end_date, B) + " " + this.event_date(C)
  } else {
    return this.week_date(B.start_date, B.end_date, B)
  }
};
scheduler.templates.agenda_text = function(A) {
  return A.text
};
scheduler.attachEvent("onTemplatesReady", function() {
  scheduler.attachEvent("onSchedulerResize", function() {
    if (this._mode == "agenda") {
      this.agenda_view(true);
      return false
    }
    return true
  });
  var A = scheduler.render_data;
  scheduler.render_data = function(D) {
    if (this._mode == "agenda") {
      B()
    } else {
      return A.apply(this, arguments)
    }
  };
  function C(E) {
    if (E) {
      var D = scheduler.locale.labels;
      scheduler._els.dhx_cal_header[0].innerHTML = "<div class='dhx_agenda_line'><div>" + D.date + "</div><span style='padding-left:25px'>" + D.description + "</span></div>";
      scheduler._table_view = true;
      scheduler.set_sizes()
    }
  }

  function B() {
    var D = scheduler._date;
    var H = scheduler.get_visible_events();
    H.sort(function(J, I) {
      return J.start_date > I.start_date ? 1 : -1
    });
    var G = "<div class='dhx_agenda_area'>";
    for (var F = 0; F < H.length; F++) {
      G += "<div class='dhx_agenda_line' event_id='" + H[F].id + "' style='" + (H[F]._text_style || "") + "'><div>" + scheduler.templates.agenda_time(H[F].start_date, H[F].end_date, H[F]) + "</div>";
      G += "<div class='dhx_event_icon icon_details'>&nbsp</div>";
      G += "<span>" + scheduler.templates.agenda_text(H[F]) + "</span></div>"
    }
    G += "<div class='dhx_v_border'></div></div>";
    scheduler._els.dhx_cal_data[0].innerHTML = G;
    var E = scheduler._els.dhx_cal_data[0].firstChild.childNodes;
    for (var F = 0; F < E.length - 1; F++) {
      scheduler._rendered[F] = E[F]
    }
  }

  scheduler.agenda_view = function(D) {
    scheduler._min_date = scheduler.config.agenda_start || (new Date());
    scheduler._max_date = new Date(9999, 1, 1);
    scheduler._table_view = true;
    C(D);
    if (D) {
      B()
    } else {
    }
  }
});