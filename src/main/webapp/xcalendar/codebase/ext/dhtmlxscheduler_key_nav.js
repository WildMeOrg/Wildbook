

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