
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


	fetch: function(cls, arg, callback) {
		if (!cls || !cls.prototype || !cls.prototype.meta) {
			console.error('invalid class %o', cls);
			return;
		}
		var url = cls.prototype.apiUrl();
		if (arg) url += '/' + arg;
console.log('fetch() url = ' + url);

		var ajax = {
			url: url,
			success: function(d) {
				if (!(d instanceof Array)) d = [d];
				var arr = [];
				for (var i = 0 ; i < d.length ; i++) {
					var obj = new cls(d[i]);  //TODO do we need to trap failures?
					arr.push(obj);
				}
				callback(arr);
			},
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

		//this is lame cuz augment doesnt allow class methods to inherit, so superhacky. :(
		if (wildbook.class[cname] && wildbook.class[cname].prototype) {
			wildbook.class[cname].prototype.meta = function() {
				return {
					className: cname
				};
			};
		}
		////// end hackery

		callback();
	});
}


//$.getScript('/mm/javascript/prototype.js', function() { wildbook.init(); });

$(document).ready(function() { wildbook.init(); });

