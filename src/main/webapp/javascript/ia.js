var IA = {
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
    //  this string is used to find sub-object under this IA, e.g. IA.IBEIS.js
    getPluginType: function(task) {
        if (!task) return false;
console.info('>>>>> %o', task);
        if (typeof task.parameters != 'object') return '__NULL__';

        //TODO fix weakness that earlier plugins can basically hijack this logic (possible fix: let everyone vote etc?)
        for (var i = 0 ; i < IA.plugins.length ; i++) {
console.log(' . . . . getPluginType[%d] trying %s | %s', i, IA.plugins[i].code, IA.plugins[i].name);
            var found = IA.plugins[i].isMyTask(task);
            if (found) return IA.plugins[i].code;
        }
        return false;
    },

    //largely used for filtering tasks on "purpose", for example: detection, identification
    //  this is largely plugin-specific, so it punts there
    getGeneralType: function(task) {
        return IA.callPlugin('getGeneralType', task, [task]);
    },

    getPluginByType: function(type) {
        for (var i = 0 ; i < IA.plugins.length ; i++) {
            if (IA.plugins[i].code == type) return IA.plugins[i];
        }
        return false;
    },

    //basically gracefully attempts to call IA.PLUGIN_TYPE.funcName.apply(argArray) and returns that value
    //  for now, undefined is returned if badness happens.... FIXME?
    callPlugin: function(funcName, task, argArray) {
        if (!funcName || !task) return undefined;
        var type = IA.getPluginType(task);
        if (!type) {
            console.info('IA.callPlugin(): task %s null pluginType, skipping (parameters %o)', task.id, task.parameters);
            return undefined;
        }
        var p = IA.getPluginByType(type);
        if (!p || (typeof p[funcName] != 'function')) {
            console.warn('IA.callPlugin(): task %s of unsupported pluginType %s [no %s() method]; skipping', task.id, type, funcName);
            return undefined;
        }
        //ok, lets try!
        var res = p[funcName].apply(null, argArray);
        console.log('IA.%s.%s(%o) -> %s', type, funcName, argArray, res);
        return res;
    },

    //this will traverse the task, and call resCallback(task, res) with 'res' being the html (div) for results
    //  for all subtasks
    processTask: function(task, resCallback) {
        if (!task && !task.id) return;
        if (typeof resCallback != 'function') {
            console.error('IA.processTask() failed on task %s due to no resCallback function', task.id);
            return;
        }

        var res = IA.callPlugin('getResult', task, [task]);

        if (IA.addDebug) {
            var type = IA.getPluginType(task);
            var gt = IA.getGeneralType(task);
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
                IA.processTask(task.children[i], resCallback);
            }
        }
    },


    //this is a debugging task plugin type which certain tasks get assigned (e.g. nodes in the tree with no content)
    plugins: [{
        code: '__NULL__',
        name: 'default (null)',
        isMyTask: function(task) { return false; },  //gets set specifically only when no parameters (for now)
        getGeneralType: function() { return 'unknown'; },
        getResult: function(task) {
            return '<!-- __NULL__.getResult(' + task.id + ') -->';
        }
    }]
};
