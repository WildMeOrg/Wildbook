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

    //TODO we need to bust this out to plugin-type based decision making.  but for now i use this really hacky decision of only leaf nodes
    //   note this will skip detection results (which has children) ... boo obvs
    needTaskResults: function(task) {
        return !wildbook.IA.hasChildren(task);
    },

    //utility function for "has 1 or more children"
    hasChildren: function(task) {
        return (task && Array.isArray(task.children) && (task.children.length > 0));
    },

    //this will traverse the task, and call resCallback(task, res) with 'res' being the html (div) for results
    //  note that the recursion will created nested dom elements for all children.  all with have id="task-TASKID"
    getDomResult: function(task, resCallback) {
        if (!task && !task.id) return;
        if (typeof resCallback != 'function') {
            console.error('wildbook.IA.getDomResult() failed on task %s due to no resCallback function', task.id);
            return;
        }
        var res = wildbook.IA._getDomResult(task);
        resCallback(task, res);
    },

    //guts of above, no callback, for recursion...
    _getDomResult: function(task) {
        if (!task && !task.id) return;
        var res = jQuery(wildbook.IA.callPlugin('getDomResult', task, [task]));
        wildbook.IA.appendDebugDom(task, res);

        if (wildbook.IA.hasChildren(task)) {
            //TODO not sure if this whole .parent thing is a good idea, but for now.........
            //var parentTask = Object.assign({}, task);  //a shallow copy, but sufficient since we want to lose the children for this purpose
            //delete(parentTask.children);  //lets cut out the loopiness plz
            for (var i = 0 ; i < task.children.length ; i++) {
console.info('>>> IA._getDomResult() iterating on child %d of task %s: %s', i, task.id, task.children[i].id);
                //task.children[i].parent = parentTask;  //technically modifying the original task... oops?
                var cres = wildbook.IA._getDomResult(task.children[i]);
                if (cres) res.append(cres);
            }
        }
        return res;
    },


    appendDebugDom: function(task, jel) {  //jel is a jquery element to append to
        if (!wildbook.IA.addDebug) return;
        var type = wildbook.IA.getPluginType(task);
        var gt = wildbook.IA.getGeneralType(task);
        var dh = '<div class="ia-debug" style="display:none;" id="task-debug-' + task.id + '">';
        dh += '<i>type:</i> <b>' + type + '</b>\n';
        dh += '<i>general type:</i> <b>' + gt + '</b>\n';
        dh += '<i>task contents:</i>\n\n' + JSON.stringify(task, null, 4);
        dh += '</div>';
        jel.append(dh);
    },

    //now recurse thru the kids

    //this returns (null or) an array of "hamburger menu items", which are basically each ['text', function]
    //  passed in the enh object
    imageMenuItems: function() {
        var items = new Array();
        wildbook.arrayMerge(items, wildbook.IA.callPlugins('imageMenuItems'));
        return items;
    },

    // Wildbooks with a single species or single pipeline don't need to disable menu items if species/genus aint set.  
    requireSpeciesForId: function() { 
        var requireSpecies = wildbookGlobals.iaStatus.map.settings.map.requireSpeciesForId; 
        if (requireSpecies) return requireSpecies; 
        return false;   
    }, 


    //this is a debugging task plugin type which certain tasks get assigned (e.g. nodes in the tree with no content)
    plugins: [{
        code: '__NULL__',
        name: 'default (null)',
        isMyTask: function(task) { return false; },  //gets set specifically only when no parameters (for now)
        getGeneralType: function() { return 'unknown'; },
        getDomResult: function(task) {
            return '<div class="ia-null-task" id=' + task.id + '"></div>';
        }
    }]
};
