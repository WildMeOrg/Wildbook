

scheduler.form_blocks.recurring = {render:function(A) {
  return scheduler.__recurring_template
},set_value:function(L, K, P) {
  var I = {start:P.start_date,end:P._end_date};
  var E = scheduler.date.str_to_date(scheduler.config.repeat_date);
  var G = scheduler.date.date_to_str(scheduler.config.repeat_date);
  var F = L.getElementsByTagName("FORM")[0];
  var B = [];

  function H(S) {
    for (var T = 0; T < S.length; T++) {
      var U = S[T];
      if (U.type == "checkbox" || U.type == "radio") {
        if (!B[U.name]) {
          B[U.name] = []
        }
        B[U.name].push(U)
      } else {
        B[U.name] = U
      }
    }
  }

  H(F.getElementsByTagName("INPUT"));
  H(F.getElementsByTagName("SELECT"));
  var C = function(S) {
    return document.getElementById(S)
  };

  function O(T) {
    var S = B[T];
    for (var U = 0; U < S.length; U++) {
      if (S[U].checked) {
        return S[U].value
      }
    }
  }

  function J() {
    C("dhx_repeat_day").style.display = "none";
    C("dhx_repeat_week").style.display = "none";
    C("dhx_repeat_month").style.display = "none";
    C("dhx_repeat_year").style.display = "none";
    C("dhx_repeat_" + this.value).style.display = "block"
  }

  function D(U) {
    var S = [O("repeat")];
    Q[S[0]](S, U);
    while (S.length < 5) {
      S.push("")
    }
    var T = "";
    if (B.end[0].checked) {
      U.end = new Date(9999, 1, 1);
      T = "no"
    } else {
      if (B.end[2].checked) {
        U.end = E(B.date_of_end.value)
      } else {
        scheduler.transpose_type(S.join("_"));
        T = Math.max(1, B.occurences_count.value);
        U.end = scheduler.date.add(new Date(U.start - 1), T, S.join("_"))
      }
    }
    return S.join("_") + "#" + T
  }

  var Q = {month:function(S, T) {
    if (O("month_type") == "d") {
      S.push(Math.max(1, B.month_count.value));
      T.start.setDate(B.month_day.value)
    } else {
      S.push(Math.max(1, B.month_count2.value));
      S.push(B.month_day2.value);
      S.push(Math.max(1, B.month_week2.value));
      T.start.setDate(1)
    }
    T._start = true
  },week:function(V, W) {
    V.push(Math.max(1, B.week_count.value));
    V.push("");
    V.push("");
    var U = [];
    var S = B.week_day;
    for (var T = 0; T < S.length; T++) {
      if (S[T].checked) {
        U.push(S[T].value)
      }
    }
    if (U.length) {
      W.start = scheduler.date.week_start(W.start);
      W._start = true
    }
    V.push(U.sort().join(","))
  },day:function(S) {
    if (O("day_type") == "d") {
      S.push(Math.max(1, B.day_count.value))
    } else {
      S.push("week");
      S.push(1);
      S.push("");
      S.push("");
      S.push("1,2,3,4,5");
      S.splice(0, 1)
    }
  },year:function(S, T) {
    if (O("year_type") == "d") {
      S.push("1");
      T.start.setMonth(0);
      T.start.setDate(B.year_day.value);
      T.start.setMonth(B.year_month.value)
    } else {
      S.push("1");
      S.push(B.year_day2.value);
      S.push(B.year_week2.value);
      T.start.setDate(1);
      T.start.setMonth(B.year_month2.value)
    }
    T._start = true
  }};
  var M = {week:function(V, W) {
    B.week_count.value = V[1];
    var S = B.week_day;
    var U = V[4].split(",");
    var X = {};
    for (var T = 0; T < U.length; T++) {
      X[U[T]] = true
    }
    for (var T = 0; T < S.length; T++) {
      S[T].checked = (!!X[S[T].value])
    }
  },month:function(S, T) {
    if (S[2] == "") {
      B.month_type[0].checked = true;
      B.month_count.value = S[1];
      B.month_day.value = T.start.getDate()
    } else {
      B.month_type[1].checked = true;
      B.month_count2.value = S[1];
      B.month_week2.value = S[3];
      B.month_day2.value = S[2]
    }
  },day:function(S, T) {
    B.day_type[0].checked = true;
    B.day_count.value = S[1]
  },year:function(S, T) {
    if (S[2] == "") {
      B.year_type[0].checked = true;
      B.year_day.value = T.start.getDate();
      B.year_month.value = T.start.getMonth()
    } else {
      B.year_type[1].checked = true;
      B.year_week2.value = S[3];
      B.year_day2.value = S[2];
      B.year_month2.value = T.start.getMonth()
    }
  }};

  function R(S, V) {
    var T = S.split("#");
    S = T[0].split("_");
    M[S[0]](S, V);
    var U = B.repeat[({day:0,week:1,month:2,year:3})[S[0]]];
    switch (T[1]) {case"no":B.end[0].checked = true;break;case"":B.end[2].checked = true;B.date_of_end.value = G(V.end);break;default:B.end[1].checked = true;B.occurences_count.value = T[1];break
    }
    U.checked = true;
    U.onclick()
  }

  for (var N = 0; N < F.elements.length; N++) {
    var A = F.elements[N];
    switch (A.name) {case"repeat":A.onclick = J;break
    }
  }
  scheduler.form_blocks.recurring.set_value = function(T, U, S) {
    T.open = !S.rec_type;
    if (S.event_pid && S.event_pid != "0") {
      T.blocked = true
    } else {
      T.blocked = false
    }
    I.start = S.start_date;
    scheduler.form_blocks.recurring.button_click(0, T.previousSibling.firstChild.firstChild, T, T);
    if (U) {
      R(U, I)
    }
  };
  scheduler.form_blocks.recurring.get_value = function(T, S) {
    if (T.open) {
      S.rec_type = D(I);
      if (I._start) {
        S._start_date = S.start_date = I.start;
        I._start = false
      } else {
        S._start_date = null
      }
      S._end_date = S.end_date = I.end;
      S.rec_pattern = S.rec_type.split("#")[0]
    } else {
      S.rec_type = S.rec_pattern = "";
      S._end_date = S.end_date
    }
    return S.rec_type
  };
  scheduler.form_blocks.recurring.set_value(L, K, P)
},get_value:function(B, A) {
},focus:function(A) {
},button_click:function(B, C, D, A) {
  if (!A.open && !A.blocked) {
    A.style.height = "115px";
    C.style.backgroundPosition = "-5px 0px";
    C.nextSibling.innerHTML = scheduler.locale.labels.button_recurring_open
  } else {
    A.style.height = "0px";
    C.style.backgroundPosition = "-5px 20px";
    C.nextSibling.innerHTML = scheduler.locale.labels.button_recurring
  }
  A.open = !A.open;
  scheduler.setLightboxSize()
}};
scheduler._rec_markers = {};
scheduler._rec_temp = [];
scheduler.attachEvent("onEventLoading", function(A) {
  if (A.event_pid != 0) {
    scheduler._rec_markers[A.event_length * 1000] = A
  }
  if (A.rec_type) {
    A.rec_pattern = A.rec_type.split("#")[0]
  }
  return true
});
scheduler.attachEvent("onEventIdChange", function(D, A) {
  if (this._ignore_call) {
    return
  }
  this._ignore_call = true;
  for (var C = 0; C < this._rec_temp.length; C++) {
    var B = this._rec_temp[C];
    if (B.event_pid == D) {
      B.event_pid = A;
      this.changeEventId(B.id, A + "#" + B.id.split("#")[1])
    }
  }
  delete this._ignore_call
});
scheduler.attachEvent("onBeforeEventDelete", function(D) {
  var B = this.getEvent(D);
  if (D.toString().indexOf("#") != -1) {
    var D = D.split("#");
    var C = this.uid();
    this.addEvent({id:C,start_date:B.start_date,end_date:B.end_date,event_pid:B.event_pid,event_length:D[1],rec_type:"none",rec_pattern:"none"});
    this._rec_markers[D[1] * 1000] = this.getEvent(C)
  } else {
    if (B.rec_type) {
      this._roll_back_dates(B)
    }
    for (var A in this._rec_markers) {
      if (this._rec_markers[A].event_pid == D) {
        this.deleteEvent(this._rec_markers[A].id, true)
      }
    }
  }
  return true
});
scheduler.attachEvent("onEventChanged", function(D) {
  if (this._loading) {
    return true
  }
  var B = this.getEvent(D);
  if (D.toString().indexOf("#") != -1) {
    var D = D.split("#");
    var C = this.uid();
    this._not_render = true;
    this.addEvent({id:C,start_date:B.start_date,end_date:B.end_date,text:B.text,event_pid:D[0],event_length:D[1]});
    this._not_render = false;
    this._rec_markers[D[1] * 1000] = this.getEvent(C)
  } else {
    if (B.rec_type) {
      this._roll_back_dates(B)
    }
    for (var A in this._rec_markers) {
      if (this._rec_markers[A].event_pid == D) {
        this.deleteEvent(this._rec_markers[A].id, true);
        delete this._rec_markers[A]
      }
    }
  }
  return true
});
scheduler.attachEvent("onEventAdded", function(B) {
  if (!this._loading) {
    var A = this.getEvent(B);
    if (A.rec_type && !A.event_length) {
      this._roll_back_dates(A)
    }
  }
  return true
});
scheduler.attachEvent("onEventCreated", function(B) {
  var A = this.getEvent(B);
  if (!A.rec_type) {
    A.rec_type = A.rec_pattern = ""
  }
  return true
});
scheduler.attachEvent("onEventCancel", function(B) {
  var A = this.getEvent(B);
  if (A.rec_type) {
    this._roll_back_dates(A);
    this.render_view_data(A.id)
  }
});
scheduler._roll_back_dates = function(A) {
  A.event_length = (A.end_date.valueOf() - A.start_date.valueOf()) / 1000;
  A.end_date = A._end_date;
  if (A._start_date) {
    A.start_date.setMonth(0);
    A.start_date.setDate(A._start_date.getDate());
    A.start_date.setMonth(A._start_date.getMonth());
    A.start_date.setFullYear(A._start_date.getFullYear())
  }
};
scheduler.validId = function(A) {
  return A.toString().indexOf("#") == -1
};
scheduler.showLightbox_rec = scheduler.showLightbox;
scheduler.showLightbox = function(B) {
  var A = this.getEvent(B).event_pid;
  if (B.toString().indexOf("#") != -1) {
    A = B.split("#")[0]
  }
  if (!A || A == 0 || !confirm(this.locale.labels.confirm_recurring)) {
    return this.showLightbox_rec(B)
  }
  A = this.getEvent(A);
  A._end_date = A.end_date;
  A.end_date = new Date(A.start_date.valueOf() + A.event_length * 1000);
  return this.showLightbox_rec(A.id)
};
scheduler.get_visible_events_rec = scheduler.get_visible_events;
scheduler.get_visible_events = function() {
  for (var C = 0; C < this._rec_temp.length; C++) {
    delete this._events[this._rec_temp[C].id]
  }
  this._rec_temp = [];
  var A = this.get_visible_events_rec();
  var B = [];
  for (var C = 0; C < A.length; C++) {
    if (A[C].rec_type) {
      if (A[C].rec_pattern != "none") {
        this.repeat_date(A[C], B)
      }
    } else {
      B.push(A[C])
    }
  }
  return B
};
(function() {
  var A = scheduler.is_one_day_event;
  scheduler.is_one_day_event = function(B) {
    if (B.rec_type) {
      return true
    }
    return A.call(this, B)
  }
})();
scheduler.transponse_size = {day:1,week:7,month:1,year:12};
scheduler.date.day_week = function(E, C, D) {
  E.setDate(1);
  D = (D - 1) * 7;
  var B = E.getDay();
  var A = C * 1 + D - B + 1;
  E.setDate(A <= D ? (A + 7) : A)
};
scheduler.transpose_day_week = function(G, D, F, C, E) {
  var A = G.getDay() - F;
  for (var B = 0; B < D.length; B++) {
    if (D[B] > A) {
      return G.setDate(G.getDate() + D[B] * 1 - A - (C ? F : E))
    }
  }
  this.transpose_day_week(G, D, F + C, null, F)
};
scheduler.transpose_type = function(C) {
  var E = "transpose_" + C;
  if (!this.date[E]) {
    var F = C.split("_");
    var A = 60 * 60 * 24 * 1000;
    var B = "add_" + C;
    var D = this.transponse_size[F[0]] * F[1];
    if (F[0] == "day" || F[0] == "week") {
      var G = null;
      if (F[4]) {
        G = F[4].split(",")
      }
      this.date[E] = function(H, J) {
        var I = Math.floor((J.valueOf() - H.valueOf()) / (A * D));
        if (I > 0) {
          H.setDate(H.getDate() + I * D)
        }
        if (G) {
          scheduler.transpose_day_week(H, G, 1, D)
        }
      };
      this.date[B] = function(J, I) {
        var K = new Date(J.valueOf());
        if (G) {
          for (var H = 0; H < I; H++) {
            scheduler.transpose_day_week(K, G, 0, D)
          }
        } else {
          K.setDate(K.getDate() + I * D)
        }
        return K
      }
    } else {
      if (F[0] == "month" || F[0] == "year") {
        this.date[E] = function(H, J) {
          var I = Math.ceil(((J.getFullYear() * 12 + J.getMonth() * 1) - (H.getFullYear() * 12 + H.getMonth() * 1)) / (D));
          if (I >= 0) {
            H.setMonth(H.getMonth() + I * D)
          }
          if (F[3]) {
            scheduler.date.day_week(H, F[2], F[3])
          }
        };
        this.date[B] = function(I, H) {
          var J = new Date(I.valueOf());
          J.setMonth(J.getMonth() + H * D);
          if (F[3]) {
            scheduler.date.day_week(J, F[2], F[3])
          }
          return J
        }
      }
    }
  }
};
scheduler.repeat_date = function(E, F, C, H, I) {
  H = H || this._min_date;
  I = I || this._max_date;
  var D = new Date(E.start_date.valueOf());
  this.transpose_type(E.rec_pattern);
  scheduler.date["transpose_" + E.rec_pattern](D, H);
  while (D < E.start_date || (D.valueOf() + E.event_length * 1000) <= H.valueOf()) {
    D = this.date.add(D, 1, E.rec_pattern)
  }
  while (D < I && D < E.end_date) {
    var A = this._rec_markers[D.valueOf()];
    if (!A || A.event_pid != E.id) {
      var G = new Date(D.valueOf() + E.event_length * 1000);
      var B = this._copy_event(E);
      B.start_date = D;
      B.event_pid = E.id;
      B.id = E.id + "#" + Math.ceil(D.valueOf() / 1000);
      B.end_date = G;
      B._timed = this.is_one_day_event(B);
      if (!B._timed && !this._table_view && !this.config.multi_day) {
        return
      }
      F.push(B);
      if (!C) {
        this._events[B.id] = B;
        this._rec_temp.push(B)
      }
    } else {
      if (C) {
        F.push(A)
      }
    }
    D = this.date.add(D, 1, E.rec_pattern)
  }
};
scheduler.getEvents = function(G, F) {
  var A = [];
  for (var B in this._events) {
    var D = this._events[B];
    if (D && D.start_date < F && D.end_date > G) {
      if (D.rec_pattern) {
        if (D.rec_pattern == "none") {
          continue
        }
        var E = [];
        this.repeat_date(D, E, true, G, F);
        for (var C = 0; C < E.length; C++) {
          if (!E[C].rec_pattern && E[C].start_date < F && E[C].end_date > G) {
            A.push(E[C])
          }
        }
      } else {
        if (D.event_pid == 0) {
          A.push(D)
        }
      }
    }
  }
  return A
};
scheduler.config.repeat_date = "%m.%d.%Y";
scheduler.config.lightbox.sections = [
  {name:"description",height:130,map_to:"text",type:"textarea",focus:true},
  {name:"recurring",height:115,type:"recurring",map_to:"rec_type",button:"recurring"},
  {name:"time",height:72,type:"time",map_to:"auto"}
];
scheduler._copy_dummy = function(A) {
  this.start_date = new Date(this.start_date);
  this.end_date = new Date(this.end_date);
  this.event_lengt = this.event_pid = this.rec_pattern = this.rec_type = this._timed = null
};
scheduler.__recurring_template = '<div class="dhx_form_repeat"> <form> <div class="dhx_repeat_left"> <label><input class="dhx_repeat_radio" type="radio" name="repeat" value="day" />Daily</label><br /> <label><input class="dhx_repeat_radio" type="radio" name="repeat" value="week"/>Weekly</label><br /> <label><input class="dhx_repeat_radio" type="radio" name="repeat" value="month" checked />Monthly</label><br /> <label><input class="dhx_repeat_radio" type="radio" name="repeat" value="year" />Yearly</label> </div> <div class="dhx_repeat_divider"></div> <div class="dhx_repeat_center"> <div style="display:none;" id="dhx_repeat_day"> <label><input class="dhx_repeat_radio" type="radio" name="day_type" value="d"/>Every</label><input class="dhx_repeat_text" type="text" name="day_count" value="1" />day<br /> <label><input class="dhx_repeat_radio" type="radio" name="day_type" checked value="w"/>Every workday</label> </div> <div style="display:none;" id="dhx_repeat_week"> Repeat every<input class="dhx_repeat_text" type="text" name="week_count" value="1" />week next days:<br /> <table class="dhx_repeat_days"> <tr> <td> <label><input class="dhx_repeat_checkbox" type="checkbox" name="week_day" value="1" />Monday</label><br /> <label><input class="dhx_repeat_checkbox" type="checkbox" name="week_day" value="4" />Thursday</label> </td> <td> <label><input class="dhx_repeat_checkbox" type="checkbox" name="week_day" value="2" />Tuesday</label><br /> <label><input class="dhx_repeat_checkbox" type="checkbox" name="week_day" value="5" />Friday</label> </td> <td> <label><input class="dhx_repeat_checkbox" type="checkbox" name="week_day" value="3" />Wednesday</label><br /> <label><input class="dhx_repeat_checkbox" type="checkbox" name="week_day" value="6" />Saturday</label> </td> <td> <label><input class="dhx_repeat_checkbox" type="checkbox" name="week_day" value="0" />Sunday</label><br /><br /> </td> </tr> </table> </div> <div id="dhx_repeat_month"> <label><input class="dhx_repeat_radio" type="radio" name="month_type" value="d"/>Repeat</label><input class="dhx_repeat_text" type="text" name="month_day" value="1" />day every<input class="dhx_repeat_text" type="text" name="month_count" value="1" />month<br /> <label><input class="dhx_repeat_radio" type="radio" name="month_type" checked value="w"/>On</label><input class="dhx_repeat_text" type="text" name="month_week2" value="1" /><select name="month_day2"><option value="1" selected >Monday<option value="2">Tuesday<option value="3">Wednesday<option value="4">Thursday<option value="5">Friday<option value="6">Saturday<option value="0">Sunday</select>every<input class="dhx_repeat_text" type="text" name="month_count2" value="1" />month<br /> </div> <div style="display:none;" id="dhx_repeat_year"> <label><input class="dhx_repeat_radio" type="radio" name="year_type" value="d"/>Every</label><input class="dhx_repeat_text" type="text" name="year_day" value="1" />day<select name="year_month"><option value="0" selected >January<option value="1">February<option value="2">March<option value="3">April<option value="4">May<option value="5">June<option value="6">July<option value="7">August<option value="8">September<option value="9">October<option value="10">November<option value="11">December</select>month<br /> <label><input class="dhx_repeat_radio" type="radio" name="year_type" checked value="w"/>On</label><input class="dhx_repeat_text" type="text" name="year_week2" value="1" /><select name="year_day2"><option value="1" selected >Monday<option value="2">Tuesday<option value="3">Wednesday<option value="4">Thursday<option value="5">Friday<option value="6">Saturday<option value="7">Sunday</select>of<select name="year_month2"><option value="0" selected >January<option value="1">February<option value="2">March<option value="3">April<option value="4">May<option value="5">June<option value="6">July<option value="7">August<option value="8">September<option value="9">October<option value="10">November<option value="11">December</select><br /> </div> </div> <div class="dhx_repeat_divider"></div> <div class="dhx_repeat_right"> <label><input class="dhx_repeat_radio" type="radio" name="end" checked/>No end date</label><br /> <label><input class="dhx_repeat_radio" type="radio" name="end" />After</label><input class="dhx_repeat_text" type="text" name="occurences_count" value="1" />occurrences<br /> <label><input class="dhx_repeat_radio" type="radio" name="end" />End by</label><input class="dhx_repeat_date" type="text" name="date_of_end" value="01.01.2010" /><br /> </div> </form> </div> <div style="clear:both"> </div>';