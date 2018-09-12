wildbook.IA = {
    addDebug: true,  //should we append debug div(s) to output????

    fetchTaskResponse: function(taskId, callback) {
        $.ajax({
            type: 'GET',
            url: 'ia?v2&includeChildren&taskId=' + taskId,
            dataType: 'json',
            complete: function(jq, status) {
                console.info('fetchTask %s complete: %s', taskId, status);
                callback(jq, status);
            }
        });
    },

    //should return "major" type (e.g. plugin flavor), like IBEIS (for ibeis.identification or ibeis.detection)
    //  this string is used to find sub-object under this IA, e.g. wildbook.IA.IBEIS.js
    getPluginType: function(task) {
        if (!task) return false;
console.info('>>>>> %o', task);
        if (typeof task.parameters != 'object') return '__NULL__';

        //TODO fix weakness that earlier plugins can basically hijack this logic (possible fix: let everyone vote etc?)
        for (var i = 0 ; i < wildbook.IA.plugins.length ; i++) {
console.log(' . . . . getPluginType[%d] trying %s | %s', i, wildbook.IA.plugins[i].code, wildbook.IA.plugins[i].name);
            var found = wildbook.IA.plugins[i].isMyTask(task);
            if (found) return wildbook.IA.plugins[i].code;
        }
        return false;
    },

    //largely used for filtering tasks on "purpose", for example: detection, identification
    //  this is largely plugin-specific, so it punts there
    getGeneralType: function(task) {
        return wildbook.IA.callPlugin('getGeneralType', task, [task]);
    },

    getPluginByType: function(type) {
        for (var i = 0 ; i < wildbook.IA.plugins.length ; i++) {
            if (wildbook.IA.plugins[i].code == type) return wildbook.IA.plugins[i];
        }
        return false;
    },

    //basically gracefully attempts to call wildbook.IA.PLUGIN_TYPE.funcName.apply(argArray) and returns that value
    //  for now, undefined is returned if badness happens.... FIXME?
    callPlugin: function(funcName, task, argArray) {
        if (!funcName || !task) return undefined;
        var type = wildbook.IA.getPluginType(task);
        if (!type) {
            console.info('wildbook.IA.callPlugin(): task %s null pluginType, skipping (parameters %o)', task.id, task.parameters);
            return undefined;
        }
        var p = wildbook.IA.getPluginByType(type);
        if (!p || (typeof p[funcName] != 'function')) {
            console.warn('wildbook.IA.callPlugin(): task %s of unsupported pluginType %s [no %s() method]; skipping', task.id, type, funcName);
            return undefined;
        }
        //ok, lets try!
        var res = p[funcName].apply(p, argArray);
        console.log('wildbook.IA.%s.%s(%o) -> %s', type, funcName, argArray, res);
        return res;
    },

    //calls a function in *all* plugins (if possible, gracefully fails) and returns array of all their results
    callPlugins: function(funcName, argArray) {
        var res = new Array();
        for (var i = 0 ; i < wildbook.IA.plugins.length ; i++) {
            if (typeof wildbook.IA.plugins[i][funcName] != 'function') {
                console.warn('wildbook.IA.callPlugins(): plugin %s does not support %s() method; skipping', wildbook.IA.plugins[i].code, funcName);
                continue;
            }
            wildbook.arrayMerge(res, wildbook.IA.plugins[i][funcName].apply(wildbook.IA.plugins[i], argArray));
        }
        return res;
    },

    //this will traverse the task, and call resCallback(task, res) with 'res' being the html (div) for results
    //  for all subtasks
    processTask: function(task, resCallback) {
        if (!task && !task.id) return;
        if (typeof resCallback != 'function') {
            console.error('wildbook.IA.processTask() failed on task %s due to no resCallback function', task.id);
            return;
        }

        var res = wildbook.IA.callPlugin('getDomResult', task, [task]);

        if (wildbook.IA.addDebug) {
            var type = wildbook.IA.getPluginType(task);
            var gt = wildbook.IA.getGeneralType(task);
            res += '<div class="ia-debug" style="display:none;" id="task-debug-' + task.id + '">';
            res += '<i>type:</i> <b>' + type + '</b>\n';
            res += '<i>general type:</i> <b>' + gt + '</b>\n';
            res += '<i>task contents:</i>\n\n' + JSON.stringify(task, null, 4);
            res += '</div>';
        }

        resCallback(task, res);

        //now recurse thru the kids
        if (task.children && Array.isArray(task.children) && (task.children.length > 0)) {
            //TODO not sure if this whole .parent thing is a good idea, but for now.........
            var parentTask = Object.assign({}, task);  //a shallow copy, but sufficient since we want to lose the children for this purpose
            delete(parentTask.children);  //lets cut out the loopiness plz
            for (var i = 0 ; i < task.children.length ; i++) {
console.info('>>> iterating on child %d of task %s', i, task.id);
                task.children[i].parent = parentTask;  //technically modifying the original task... oops?
                wildbook.IA.processTask(task.children[i], resCallback);
            }
        }
    },

    //this returns (null or) an array of "hamburger menu items", which are basically each ['text', function]
    //  passed in the enh object
    imageMenuItems: function() {
        var items = new Array();
        wildbook.arrayMerge(items, wildbook.IA.callPlugins('imageMenuItems'));
/*
        items.push(['foobar', function(enh) {
            var mid = imageEnhancer.mediaAssetIdFromElement(enh.imgEl);
            var aid = imageEnhancer.annotationIdFromElement(enh.imgEl);
            //var ma = assetByAnnotationId(aid);
alert(mid + ' : ' + aid);
        }]);
*/
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


    //this is a debugging task plugin type which certain tasks get assigned (e.g. nodes in the tree with no content)
    plugins: [{
        code: '__NULL__',
        name: 'default (null)',
        isMyTask: function(task) { return false; },  //gets set specifically only when no parameters (for now)
        getGeneralType: function() { return 'unknown'; },
        getDomResult: function(task) {
            return '<!-- __NULL__.getDomResult(' + task.id + ') -->';
        }
    }]
};
