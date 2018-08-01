var IA = {
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

    getType: function(task) {
        if (!task) return false;
        if (typeof task.parameters != 'object') return '__NULL__';
        if (task.parameters.test || task.parameters.debug) return '__NULL__';
        return false;
    },

    //this will traverse the task, and call resCallback(task, res) with 'res' being the html (div) for results
    //  for all subtasks
    processTask: function(task, resCallback) {
        if (!task && !task.id) return;
        if (typeof resCallback != 'function') {
            console.error('IA.processTask() failed on task %s due to no resCallback function', task.id);
            return;
        }
        var type = IA.getType(task);
        if (!type) {
            console.info('IA.processTask(): task %s null type, skipping', task.id);
        } else if (!IA[type] || (typeof IA[type].getResult != 'function')) {
            console.warn('IA.processTask(): task %s of unsupported type %s; skipping', task.id, type);
        } else {
            var res = IA[type].getResult(task);
    console.log('IA.%s.getResult(%s) -> %s', type, task.id, res);
            resCallback(task, res);
        }

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


    //this is a debugging task type which certain tasks get assigned (e.g. nodes in the tree with no content)
    __NULL__: {
        getResult: function(task) {
            return '<!-- __NULL__ : ' + task.id + ' -->';
        }
    }
};
