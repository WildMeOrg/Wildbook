

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