
var wildbook = {
	classNames: [
		'Encounter',
		'MarkedIndividual',
		'SinglePhotoVideo',
	],

	Model: {},
	Collection: {},

	loadAllClasses: function(callback) {
		this._loadAllClassesCount = this.classNames.length;
		var me = this;
		for (var i = 0 ; i < this.classNames.length ; i++) {
		classInit(this.classNames[i], function() {
			me._loadAllClassesCount--;
//console.log('huh??? %o', me._loadAllClassesCount);
			if (me._loadAllClassesCount <= 0) console.log('DONE!');
		});
		}
	},


/*
	fetch: function(cls, arg, callback) {
		if (!cls || !cls.prototype || !cls.prototype.meta) {
			console.error('invalid class %o', cls);
			return;
		}
		var url = cls.prototype.url();
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
*/



	init: function() {
		classInit('Base', function() { wildbook.loadAllClasses(); });  //define base class first - rest can happen any order
	}

};




function classInit(cname, callback) {
	console.info('attempting to load class %s', cname);
	$.getScript('/mm/javascript/classes/' + cname + '.js', function() {
		console.info('successfully loaded class %s', cname);

		//just a way to get actual name... hacky, but cant figure out the elegant way??
		if (wildbook.Model[cname] && wildbook.Model[cname].prototype) {
			wildbook.Model[cname].prototype.meta = function() {
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

