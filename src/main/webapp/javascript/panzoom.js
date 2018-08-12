

var PZ = {
    shiftHeld: false,

    init: function(sel) {
        $(sel).panzoom({
        });

        $(sel).on('panzoomend', function(ev, panzoom, matrix, changed) {
            if (!changed) return $(ev.currentTarget).panzoom('zoom', PZ.shiftHeld);
        });

        $('body').on('keydown', function(ev) {
            if (ev.keyCode == 16) {
                PZ.shiftHeld = true;
		$('#click-mode-shift-false').removeClass('click-mode');
		$('#click-mode-shift-true').addClass('click-mode');
            }
        });
        $('body').on('keyup', function(ev) {
            if (ev.keyCode == 16) {
                PZ.shiftHeld = false;
		$('#click-mode-shift-true').removeClass('click-mode');
		$('#click-mode-shift-false').addClass('click-mode');
            }
        });
    }


}


$(document).ready(function() {
    PZ.init('img');
});
