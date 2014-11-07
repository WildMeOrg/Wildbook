
wildbook.Model.BaseClass = Backbone.Model.extend({


	defaults: function() {
		var f = this.fields();
		var def = {};
		for (var fn in f) {
			var val = f[fn].defaultValue || this._defaultValueFor(f[fn].javaType);
			def[fn] = val;
		}
		return def;
	},


	url: function() {
		if (!this.id) return false;  //how are you really supposed to handle this??? TODO
		return wildbookGlobals.baseUrl + '/api/' + this.className() + '/' + this.id;
	},

	classNameShort: function() {
		return this.meta().className;
	},
	className: function() {
		return 'org.ecocean.' + this.classNameShort();
	},

	fields: function() {
		if (!wildbookGlobals.classDefinitions[this.className()]) return;
		var rtn = {};
		for (var fn in wildbookGlobals.classDefinitions[this.className()].fields) {
			if (fn.indexOf('jdo') == 0) continue;
			var fh = { javaType: wildbookGlobals.classDefinitions[this.className()].fields[fn] };
			fh.value = this[fn];
			fh.settable = !(wildbookGlobals.classDefinitions[this.className()].permissions && (wildbookGlobals.classDefinitions[this.className()].permissions[fn] == 'deny'));
			rtn[fn] = fh;
		}
		return rtn;
	},

//Encounter/f62e6794-ef5c-4508-a5fb-592f069876d9


	_defaultValueFor: function() { return '' },

});


wildbook.Collection.BaseClass = Backbone.Collection.extend({
	url: function(foo) {
console.log('foo %o', foo);
		return wildbookGlobals.baseUrl + '/api/' + this.model.prototype.className();
	},

});

