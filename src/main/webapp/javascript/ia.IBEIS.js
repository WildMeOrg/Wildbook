wildbook.IA.plugins.push({
    code: 'IBEIS',
    name: 'Wildbook-IA (aka IBEIS)',
    getDomResult: function(task) {
        var gt = this.getGeneralType(task);
    //var h = '<hr class="task-divider" />'
    var h = '';
	    h += '<div class="task-content task-type-' + gt + '" id="task-' + task.id + '">';
        h += '<div class="task-title task-type-' + gt + '" onDblClick="$(\'#task-debug-' + task.id + '\').show();"><span class="task-title-id"><b>Task ' + task.id + '</b></span></div>';
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
        items.push([
            function(enh) {  //the menu text for an already-started job
                var iaStatus = wildbook.IA.getPluginByType('IBEIS').iaStatus(enh);
                var menuText = '';
                if (iaStatus && iaStatus.status) {
                    menuText += 'matching already initiated, status: <span title="task ' + iaStatus.taskId;
                    menuText += '" class="image-enhancer-menu-item-iastatus-';
                    menuText += iaStatus.status + '">' + iaStatus.statusText + '</span>';
                    // here we want to add another item to start another matching job?
                } else {
	            var mid = imageEnhancer.mediaAssetIdFromElement(enh.imgEl);
                    var ma = assetById(mid);
                    var requireSpecies = wildbook.IA.requireSpeciesForId();
                    if (requireSpecies=="false"||ma.taxonomyString) {
                        menuText = 'start matching';
                        alreadyLinked = true;
                    } else {
                        menuText = '<i class="error">you must have <b>genus and specific epithet</b> set to match</i>';
                    }
                }
                return menuText;
            },
            function(enh) {  //the menu action for an already-started job
                var iaStatus = wildbook.IA.getPluginByType('IBEIS').iaStatus(enh);
                if (iaStatus && iaStatus.taskId) {
                    wildbook.openInTab('../iaResults.jsp?taskId=' + iaStatus.taskId);
                } else {
	            var mid = imageEnhancer.mediaAssetIdFromElement(enh.imgEl);
                    var aid = imageEnhancer.annotationIdFromElement(enh.imgEl);
                    var ma = assetById(mid);
                    var requireSpecies = wildbook.IA.requireSpeciesForId();
                    if (requireSpecies=="false"||ma.taxonomyString) {
                        var data = {
                            annotationIds: [ aid ]
                        };
                        imageEnhancer.popup('<h2>Starting matching....</h2>');
                        wildbook.IA.getPluginByType('IBEIS').restCall(data, function(xhr, textStatus) {
                            if (textStatus == 'success') {
                                if (!xhr || !xhr.responseJSON || !xhr.responseJSON.success || !xhr.responseJSON.taskId) {
                                    imageEnhancer.popup('<h2 class="error">Error starting matching</h2><p>Invalid response</p>');
                                    console.log(xhr);
                                    return;
                                }
                                //i think we at least got a task sent off!
                                imageEnhancer.popupClose();
                                wildbook.openInTab('../iaResults.jsp?taskId=' + xhr.responseJSON.taskId);
                            } else {
                                imageEnhancer.popup('<h2 class="error">Error starting matching</h2><p>Reported: <b class="error">' + textStatus + ' ' + xhr.status + ' / ' + xhr.statusText + '</b></p>');
                                console.log(xhr);
                            }
                        });
                    } else {
                        imageEnhancer.popup('Set <b>genus</b> and <b>specific epithet</b> on this encounter before trying to run any matching attempts.');
                        return;
                    }
                }
            }
        ]);

        // this tuple is for the "start another match job" function (after one job has been started)
        items.push([
            function(enh) {
                var iaStatus = wildbook.IA.getPluginByType('IBEIS').iaStatus(enh);
                var menuText = '';
                if (iaStatus && iaStatus.status) { // corresponds to "matching already initiated" above

                    var mid = imageEnhancer.mediaAssetIdFromElement(enh.imgEl);
                    var ma = assetById(mid);
                    var requireSpecies = wildbook.IA.requireSpeciesForId();
                    if (requireSpecies=="false"||ma.taxonomyString) {

                        menuText = 'start another matching job';
                    } else {
                        menuText = '<i class="error">you must have <b>genus and specific epithet</b> set to match</i>';
                    }
                }
                return menuText;
            },
            function(enh) {  //the menu action for an already-started job
                var iaStatus = wildbook.IA.getPluginByType('IBEIS').iaStatus(enh);
                // don't need this logic bc this button will always start a new job
                // if (iaStatus && iaStatus.taskId) {
                //     wildbook.openInTab('../iaResults.jsp?taskId=' + iaStatus.taskId);
                // } else {
                var mid = imageEnhancer.mediaAssetIdFromElement(enh.imgEl);
                var aid = imageEnhancer.annotationIdFromElement(enh.imgEl);
                var ma = assetById(mid);
                var requireSpecies = wildbook.IA.requireSpeciesForId();
                if (requireSpecies=="false"||ma.taxonomyString) {
                    var data = {
                        annotationIds: [ aid ]
                    };
                    imageEnhancer.popup('<h2>Starting matching....</h2>');
                    wildbook.IA.getPluginByType('IBEIS').restCall(data, function(xhr, textStatus) {
                        if (textStatus == 'success') {
                            if (!xhr || !xhr.responseJSON || !xhr.responseJSON.success || !xhr.responseJSON.taskId) {
                                imageEnhancer.popup('<h2 class="error">Error starting matching</h2><p>Invalid response</p>');
                                console.log(xhr);
                                return;
                            }
                            //i think we at least got a task sent off!
                            imageEnhancer.popupClose();
                            wildbook.openInTab('../iaResults.jsp?taskId=' + xhr.responseJSON.taskId);
                        } else {
                            imageEnhancer.popup('<h2 class="error">Error starting matching</h2><p>Reported: <b class="error">' + textStatus + ' ' + xhr.status + ' / ' + xhr.statusText + '</b></p>');
                            console.log(xhr);
                        }
                    });
                } else {
                    imageEnhancer.popup('Set <b>genus</b> and <b>specific epithet</b> on this encounter before trying to run any matching attempts.');
                    return;
                }
                //} // end else
            }
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

    //can assume task.parameters is set
    isMyTask: function(task) {
        for (var key in task.parameters) {
            if (key.startsWith('ibeis.')) return 'IBEIS';
        }
        return false;
    }
});
