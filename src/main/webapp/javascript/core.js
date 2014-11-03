
var wildbook = {
	classNames: [
		'Encounter',
		'MarkedIndividual',
	],

	class: {},

	loadAllClasses: function(callback) {
		this._loadAllClassesCount = this.classNames.length;
		var me = this;
		for (var i = 0 ; i < this.classNames.length ; i++) {
		classInit(this.classNames[i], function() {
			me._loadAllClassesCount--;
console.log('huh??? %o', me._loadAllClassesCount);
			if (me._loadAllClassesCount <= 0) console.log('DONE!');
		});
		}
	},


	fetch: function(cls, callback) {
		if (!cls || !cls.prototype || !cls.prototype.meta) {
			console.error('invalid class %o', cls);
			return;
		}
		var url = cls.prototype.apiUrl();

		var ajax = {
			url: url,
			success: function(d) { callback(d); },
			error: function(x,a,b) { callback({error: a+': '+b}); },
			type: 'GET',
			dataType: 'json'
		};
console.log('is %o', ajax);
		$.ajax(ajax);
	},


	init: function() {
		console.log('GOT IT!');
		defineBaseClass();
	}

};


function defineBaseClass() {
	classInit('Base', function() { wildbook.loadAllClasses(); });
}




function classInit(cname, callback) {
	console.info('attempting to load class %s', cname);
	$.getScript('/mm/javascript/classes/' + cname + '.js', function() {
		console.info('successfully loaded class %s', cname);
		if (wildbook.class[cname] && wildbook.class[cname].prototype) wildbook.class[cname].prototype.meta = function() {
			return {
				className: cname
			};
		};
		callback();
	});
}


//$.getScript('/mm/javascript/prototype.js', function() { wildbook.init(); });

$(document).ready(function() { wildbook.init(); });

