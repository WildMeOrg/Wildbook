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
            function(enh) {  //the menu text
                var iaStatus = wildbook.IA.getPluginByType('IBEIS').iaStatus(enh);
                var menuText = '';
                if (iaStatus && iaStatus.status) {
                    menuText += 'matching already initiated, status: <span title="task ' + iaStatus.taskId;
                    menuText += '" class="image-enhancer-menu-item-iastatus-';
                    menuText += iaStatus.status + '">' + iaStatus.statusText + '</span>';
                } else {
	            var mid = imageEnhancer.mediaAssetIdFromElement(enh.imgEl);
                    var ma = assetById(mid);
                    if (ma.taxonomyString) {
                        menuText = 'start matching';
                    } else {
                        menuText = '<i class="error">you must have <b>genus and specific epithet</b> set to match</i>';
                    }
                }
                return menuText;
            },
            function(enh) {  //the menu action
                var iaStatus = wildbook.IA.getPluginByType('IBEIS').iaStatus(enh);
                if (iaStatus && iaStatus.taskId) {
                    wildbook.openInTab('../iaResults.jsp?taskId=' + iaStatus.taskId);
                } else {
	            var mid = imageEnhancer.mediaAssetIdFromElement(enh.imgEl);
                    var ma = assetById(mid);
                    if (ma.taxonomyString) {
	                ma.annotationId;
alert('fake starting!');
                        /////TODO do the actual rest call for matching... yay!
                    } else {
                        imageEnhancer.popup('Set <b>genus</b> and <b>specific epithet</b> on this encounter before trying to run any matching attempts.');
                        return;
                    }
                }
            }
        ]);

        //TODO could have conditional etc to turn on/off visual matcher i guess
        items.push([
            function(enh) {  //the menu text
	        var mid = imageEnhancer.mediaAssetIdFromElement(enh.imgEl);
                var ma = assetById(mid);
                if (!ma.taxonomyString) return '<i class="error">you must have <b>genus and specific epithet</b> set to use visual matcher</i>';
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

        return items;
    },

    iaStatus: function(enh) {
        var mid = imageEnhancer.mediaAssetIdFromElement(enh.imgEl);
        var ma = assetById(mid);
        if (!ma || !ma.tasks || (ma.tasks.length < 1)) return null;
        if (!ma || !ma.detectionStatus) return null;
        var rtn = {
            status: ma.detectionStatus,
            statusText: ma.detectionStatus,
            taskId: ma.tasks[ma.tasks.length - 1].id
        };
        if (ma.annotationIdentificationStatus) {
            rtn.status = ma.annotationIdentificationStatus;
            rtn.statusText += '/' + ma.annotationIdentificationStatus;
        }
        if (!rtn.status) {  //no old-world props on objs
            rtn.status = 'pending';
            rtn.statusText = 'active (see results)';
        }
        return rtn;
    },

    //can assume task.parameters is set
    isMyTask: function(task) {
        for (var key in task.parameters) {
            if (key.startsWith('ibeis.')) return 'IBEIS';
        }
        return false;
    }
});
