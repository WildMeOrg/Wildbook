IA.plugins.push({
    code: 'IBEIS',
    name: 'Wildbook-IA (aka IBEIS)',
    getResult: function(task) {
        return '<div><b>getResult:</b> ' + JSON.stringify(task) + '</div>';
    },

    getGeneralType: function(task) {
    },

    //can assume task.parameters is set
    isMyTask: function(task) {
        for (var key in task.parameters) {
            if (key.startsWith('ibeis.')) return 'IBEIS';
        }
        return false;
    }
});
