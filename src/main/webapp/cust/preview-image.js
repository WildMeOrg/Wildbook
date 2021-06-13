(function($) {
	$.previewImage = function(options) {
	    var element = $(document);
	    var namespace = '.previewImage';
	    	
		var opts = $.extend({
			/* The following set of options are the ones that should most often be changed 
			   by passing an options object into this method.
			*/
			'xOffset': 20,    // the x offset from the cursor where the image will be overlayed.
			'yOffset': -20,   // the y offset from the cursor where the image will be overlayed.			
			'fadeIn': 'fast', // speed in ms to fade in, 'fast' and 'slow' also supported.
			'css': {          // css to use, may also be set to false.
				'padding': '8px',
				'border': '1px solid gray',
				'background-color': '#fff'
			},
			
			/* The following options should normally not be changed - they are here for 
			   cases where this plugin causes problems with other plugins/javascript.
			*/
			'eventSelector': '[data-preview-image]', // the selector for binding mouse events.
			'dataKey': 'previewImage', // the key to the link data, should match the above value.
			'overlayId': 'preview-image-plugin-overlay', // the id of the overlay that will be created.
		}, options);
		
		// unbind any previous event listeners:
		element.off(namespace);
			
		element.on('mouseover' + namespace, opts.eventSelector, function(e) {
			var p = $('<p>').attr('id', opts.overlayId).css('position', 'absolute')
				.css('display', 'none')
				.append($('<img>').attr('src', $(this).data(opts.dataKey)));
			if (opts.css) p.css(opts.css);
			
			$('body').append(p);
			
			p.css("top", (e.pageY + opts.yOffset) + "px").css("left", 
				(e.pageX + opts.xOffset) + "px").fadeIn(opts.fadeIn);		
		});
		

		element.on('mouseout' + namespace, opts.eventSelector, function() {
			$('#' + opts.overlayId).remove();
		});
		
		element.on('mousemove' + namespace, opts.eventSelector, function(e) {
			$('#' + opts.overlayId).css("top", (e.pageY + opts.yOffset) + "px")
				.css("left", (e.pageX + opts.xOffset) + "px");
		});
		
		return this;
	};
	
	// bind with defaults so that the plugin can be used immediately if defaults are taken:
	$.previewImage();
})(jQuery);