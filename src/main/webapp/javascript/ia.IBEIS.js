IA.plugins.push({
    code: 'IBEIS',
    name: 'Wildbook-IA (aka IBEIS)',
    getResult: function(task) {
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

    //can assume task.parameters is set
    isMyTask: function(task) {
        for (var key in task.parameters) {
            if (key.startsWith('ibeis.')) return 'IBEIS';
        }
        return false;
    }
});
