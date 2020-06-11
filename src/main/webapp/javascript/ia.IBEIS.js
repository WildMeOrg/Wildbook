wildbook.IA.plugins.push({
    code: 'IBEIS',
    name: 'Wildbook-IA (aka IBEIS)',
    getDomResult: function(task) {
        var gt = this.getGeneralType(task);
    //var h = '<hr class="task-divider" />'
    var h = '';
	    h += '<div class="task-content task-type-' + gt + '" id="task-' + task.id + '">';
        h += '<div class="task-title accordion task-type-' + gt + '" onDblClick="$(\'#task-debug-' + task.id + '\').show();"><span class="task-title-id"><b>Task ' + task.id + '</b></span></div>';
        h += '<div class="task-summary task-type-' + gt + '"><div class="summary-column col0" /><div class="summary-column col1" /><div class="summary-column col2" /></div>';
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
        // items is an array of two-element arrays. In each tuple is 1. what to display, 2. the click action
        // first elem: executes function if function, or just displays if string
        var items = new Array(); 

        //this is the "start (new) job"
        items.push([
            function(enh) { return wildbook.IA.getPluginByType('IBEIS')._iaMenuHelper(enh, 'textStart'); },
            function(enh) { return wildbook.IA.getPluginByType('IBEIS')._iaMenuHelper(enh, 'funcStart'); }
        ]);
        //this is the "start *another* job
        items.push([
            function(enh) { return wildbook.IA.getPluginByType('IBEIS')._iaMenuHelper(enh, 'textAnother'); },
            function(enh) { return wildbook.IA.getPluginByType('IBEIS')._iaMenuHelper(enh, 'funcAnother'); }
        ]);

        //TODO could have conditional etc to turn on/off visual matcher i guess
        items.push([
            function(enh) {  //the menu text
            	var mid = imageEnhancer.mediaAssetIdFromElement(enh.imgEl);
                var ma = assetById(mid);
                if (!ma.taxonomyString) return false;  //this just skips this item
                return 'visual matcher';
            },
            function(enh) {  //the menu action
            	var mid = imageEnhancer.mediaAssetIdFromElement(enh.imgEl);
                var ma = assetById(mid);
                if (!ma.taxonomyString) {
                    imageEnhancer.popup('Set <b>genus</b> and <b>specific epithet</b> on this encounter before using visual matcher.');
                    return;
                }
                //TODO how should we *really* get encounter number!
                wildbook.openInTab('encounterVM.jsp?number=' + encounterNumberFromElement(enh.imgEl) + '&mediaAssetId=' + mid);
            }
        ]);
        
        //manual annotation.jsp
        items.push([
            function(enh) {  //the menu text
            	return 'add annotation';
            },
            function(enh) {  //the menu action
            	var mid = imageEnhancer.mediaAssetIdFromElement(enh.imgEl);
                var ma = assetById(mid);
                wildbook.openInTab('manualAnnotation.jsp?encounterId=' + encounterNumberFromElement(enh.imgEl) + '&assetId=' + mid);
            }
        ]);

        return items;
    },


    //ok, this gets ugly.  you have been warned.
    _iaMenuHelper: function(enh, mode) {
        var mid = imageEnhancer.mediaAssetIdFromElement(enh.imgEl);
        var aid = imageEnhancer.annotationIdFromElement(enh.imgEl);
        var ma = assetByAnnotationId(aid);
        if (!mid || !aid || !ma || typeof iaMatchFilterAnnotationIds == 'undefined') return false;
        var iaStatus = wildbook.IA.getPluginByType('IBEIS').iaStatus(ma);
        var identActive = wildbook.IA.getPluginByType('IBEIS').iaStatusIdentActive(iaStatus);
        var requireSpecies = !(wildbook.IA.requireSpeciesForId() == 'false');
console.log('_iaMenuHelper: mode=%o, mid=%o, aid=%o, ma=%o, iaStatus=%o, identActive=%o, requireSpecies=%o', mode, mid, aid, ma, iaStatus, identActive, requireSpecies);

	if (identActive && (ma.detectionStatus == 'complete') && ma.annotation && !ma.annotation.identificationStatus) {
            if (mode == 'textStart') {
                return '<span class="disabled">no matchable detection</span>';
            } else if (mode == 'funcStart') {
                //registerTaskId(iaStatus.taskId);
                //wildbook.openInTab('../iaResults.jsp?taskId=' + iaStatus.taskId);
                return;
            }
	} else if (identActive) {
            if (mode == 'textStart') {
                return 'match results';
            } else if (mode == 'funcStart') {
                registerTaskId(iaStatus.taskId);
                wildbook.openInTab('../iaResults.jsp?taskId=' + iaStatus.taskId);
                return;
            }
        }

        //this should be the only thing we see until detection is done
        if (!identActive && ma.detectionStatus && (ma.detectionStatus != 'complete') && !(iaStatus && iaStatus.task && iaStatus.task.parameters && iaStatus.task.parameters.skipIdent)) {
            if (mode == 'textStart') {
                return '<span class="disabled">Still waiting for detection. Refresh to see updates.</span>';
            } else {
                return false;
            }
        }

        //this should also catch "run another match" case
        if (ma.annotation && !ma.annotation.matchAgainst) {  //no matchAgainst, no nothing
            if (mode == 'textStart') {
                return '<span class="disabled" title="cannot match against this annotation">cannot start match</span>';
            } else {
                return false;
            }
        }

        //we need to allow it to start (or re-start!)
        if (!requireSpecies || ma.taxonomyString) {
            if (!identActive && mode.endsWith('Another')) return false;
            if (mode == 'textStart') {
                return 'start match';
            } else if (mode == 'textAnother') {
                return 'start another match';
            } else if (mode.startsWith('func')) {
                wildbook.IA.getPluginByType('IBEIS').matchFilter(aid, ma);
            }

        } else {
            if (mode == 'textStart') {
                return '<i class="error">you must have <b>species</b> set to match</i>';
            } else {
                return false;
            }
        }
        console.warn('_iaMenuHelper: fell thru!');
        return false;
    },


    iaStatus: function(ma) {
        if (!ma || !ma.tasks || (ma.tasks.length < 1)) return null;
        var rtn = {
            status: ma.detectionStatus,
            statusText: ma.detectionStatus,
            task: ma.tasks[0],
            taskId: ma.tasks[0].id
        };
        if (ma.annotation && ma.annotation.identificationStatus) {
            rtn.status = ma.annotation.identificationStatus;
            rtn.statusText += '/' + ma.annotation.identificationStatus;
        }
        if (!rtn.status) {  //no old-world props on objs
            rtn.status = 'pending';
            rtn.statusText = 'active (see results)';
        }
        return rtn;
    },

    iaStatusIdentActive: function(ias) {
        if (!ias || !ias.task) return false;
        if (ias.task.parameters && ias.task.parameters.skipIdent) return false;  //detection-only job; not good enough
console.warn('ias => %o', ias);
        return true;
    },

    restCall: function(data, callback) {
        data.v2 = true;  //should always have this (for now)
        var url = wildbookGlobals.baseUrl + '/ia';
        jQuery.ajax({
            data: JSON.stringify(data),
            contentType: 'application/javascript',
            url: url,
            complete: function(xhr, textStatus) {
                if (typeof callback == 'function') {
                    callback(xhr, textStatus);
                } else {
                    console.info('IA.IBEIS.restCall() given no callback, status=%s, %o', textStatus, xhr);
                }
            },
            dataType: 'json',
            type: 'POST'
        });
    },

    //this is now handled by a div in encounters.jsp
    matchFilter: function(aid, ma) {
        iaMatchFilterAnnotationIds.push(aid);
        $('.ia-match-filter-dialog').show();
    },

    //can assume task.parameters is set
    isMyTask: function(task) {
        for (var key in task.parameters) {
            if (key.startsWith('ibeis.')) return 'IBEIS';
        }
        return false;
    }
});



/*
    this is for our cypress auto-testing only!   it sets a dom element for the sake of retrieving taskId when new tab opens.
*/
function registerTaskId(taskId) {
    $('#activeTaskId').remove();
    $('body').append('<p id="activeTaskId" style="display: none;">' + taskId + '</p>');
}

