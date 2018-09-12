wildbook.IA.plugins.push({
    code: 'IBEIS',
    name: 'Wildbook-IA (aka IBEIS)',
    getDomResult: function(task) {
	var h = '<div class="task-content" id="task-' + task.id + '">';
        h += '<div class="task-title" onDblClick="$(\'#task-debug-' + task.id + '\').show();"><span class="task-title-id"><b>Task ' + task.id + '</b></span></div>';
        h += '<div class="task-summary"><div class="summary-column col0" /><div class="summary-column col1" /><div class="summary-column col2" /></div>';
        h += '</div>';
        return h;
    },

    getGeneralType: function(task) {
        for (var key in task.parameters) {
            if (key.startsWith('ibeis.')) return key.substring(6);
        }
        return 'unknown';
    },

    //TODO handled passed in things (e.g. 'detection' or ... whatever!!!)
    isEnabled: function() {
        return (wildbookGlobals && wildbookGlobals.iaStatus && wildbookGlobals.iaStatus.map && wildbookGlobals.iaStatus.map.iaEnabled);
    },

    imageMenuItems: function() {
        if (!this.isEnabled()) {
            console.info('%s.imageMenuItems() claims IA functionality disabled', this.code);
            return;
        }
        var items = new Array();
        items.push([
            function(enh) {
                var mid = imageEnhancer.mediaAssetIdFromElement(enh.imgEl);
                var aid = imageEnhancer.annotationIdFromElement(enh.imgEl);
                //var ma = assetByAnnotationId(aid);
                return 'HELLO <b style="color: red;">' + aid + '</b>';
            },
            function(enh) {
                var mid = imageEnhancer.mediaAssetIdFromElement(enh.imgEl);
                var aid = imageEnhancer.annotationIdFromElement(enh.imgEl);
                //var ma = assetByAnnotationId(aid);
                alert(mid + ' : ' + aid);
            }
        ]);
        return items;


/*  COPIED OVER FOR PROSPERITY
	if (wildbook.iaEnabled()) {  //TODO (the usual) needs to be genericized for IA plugin support (which doesnt yet exist)
		opt.menu.push(['start new matching scan', function(enh) {
		    var mid = imageEnhancer.mediaAssetIdFromElement(enh.imgEl);
		    var aid = imageEnhancer.annotationIdFromElement(enh.imgEl);
                    var ma = assetByAnnotationId(aid);
      		    if (!isGenusSpeciesSet(ma)) {
        		imageEnhancer.popup("You need full taxonomic classification to start identification!");
        		return;
      		    }
		    imageEnhancer.message(jQuery('#image-enhancer-wrapper-' + mid + ':' + aid), '<p>starting matching; please wait...</p>');
		    startIdentify(ma, enh.imgEl);  //this asset should now be annotationly correct
		}]);
	}


        opt.menu.push(['use visual matcher', function(enh) {
	    var mid = imageEnhancer.mediaAssetIdFromElement(enh.imgEl);
	    var aid = imageEnhancer.annotationIdFromElement(enh.imgEl);
            var ma = assetByAnnotationId(aid);
      	    if (!isGenusSpeciesSet(ma)) {
                imageEnhancer.popup("You need full taxonomic classification to use Visual Matcher!");
                return;
            }
            window.location.href = 'encounterVM.jsp?number=' + encounterNumberFromElement(enh.imgEl) + '&mediaAssetId=' + mid;
        }]);

/*   we dont really like the old tasks showing up in menu. so there.
	var ct = 1;
	for (var annId in iaTasks) {
		//we really only care about first tid now (most recent)
		var tid = iaTasks[annId][0];
		opt.menu.push([
			//'- previous scan results ' + ct,
			'- previous scan results',
			function(enh, tid) {
				console.log('enh(%o) tid(%o)', enh, tid);
				wildbook.openInTab('matchResults.jsp?taskId=' + tid);
			},
			tid
		]);
	}
*/

    },

    //can assume task.parameters is set
    isMyTask: function(task) {
        for (var key in task.parameters) {
            if (key.startsWith('ibeis.')) return 'IBEIS';
        }
        return false;
    }
});
