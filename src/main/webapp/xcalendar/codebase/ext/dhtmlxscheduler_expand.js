

scheduler.attachEvent("onTemplatesReady", function() {
  var A = document.createElement("DIV");
  A.className = "dhx_expand_icon";
  scheduler._obj.appendChild(A);
  function B(F) {
    var D = F;
    do{
      F._position = F.style.position || "";
      F.style.position = "static"
    } while ((F = F.parentNode) && F.style);
    D.style.position = "absolute";
    D.style.zIndex = 9998;
    D._width = D.style.width;
    D._height = D.style.height;
    D.style.width = D.style.height = "100%";
    D.style.top = D.style.left = "0px";
    var E = document.body;
    E.scrollTop = 0;
    E = E.parentNode;
    if (E) {
      E.scrollTop = 0
    }
    document.body._overflow = document.body.style.overflow || "";
    document.body.style.overflow = "hidden"
  }

  function C(E) {
    var D = E;
    do{
      E.style.position = E._position
    } while ((E = E.parentNode) && E.style);
    D.style.width = D._width;
    D.style.height = D._height;
    document.body.style.overflow = document.body._overflow
  }

  A.onclick = function() {
    if (!this._expand) {
      B(scheduler._obj)
    } else {
      C(scheduler._obj)
    }
    this._expand = !this._expand;
    this.style.backgroundPosition = "0px " + (this._expand ? "0" : "18") + "px";
    if (scheduler.callEvent("onSchedulerResize", [])) {
      scheduler.update_view()
    }
  };
  scheduler.show_cover = function() {
    this._cover = document.createElement("DIV");
    this._cover.className = "dhx_cal_cover";
    this._obj.appendChild(this._cover)
  }
});