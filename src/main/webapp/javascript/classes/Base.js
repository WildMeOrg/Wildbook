
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


/* note: some combinations may return more than one encounter, which should be a collection (e.g /individualID==something)
   however, we still should allow that type of arbitrary field matching to get ONE encounter... maybe return only first?   */
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


	//pass name (of field) and Collection class
	fetchSub: function(name, opts) {
		//a little hacky but refClass.fieldName is only the final string from classname, rather than real reference to class
		if (!this.refClass[name]) return false;
		var cls = wildbook.Collection[this.refClass[name]];
		this[name] = new cls();
		if (!opts) opts = {};
		opts.jdoql = 'SELECT x FROM ' + this.className() + ' WHERE ' + this.idAttribute + '=="' + this.id + '" && ' + name + '.contains(x)';
		this[name].fetch(opts);
		return true;
	},

	_defaultValueFor: function() { return '' },

});


wildbook.Collection.BaseClass = Backbone.Collection.extend({

	//we override to allow passing jdo and fields in addition to standard Backbone options
	fetch: function(options) {
		delete(this._altUrl);
		if (options && options.jdoql) {
			//this allows us to be "lazy" and not have to put "SELECT FROM [classname] WHERE..." but just "WHERE..."
			if (options.jdoql.toLowerCase().indexOf('where') == 0) options.jdoql = 'SELECT FROM ' + this.model.prototype.className() + ' ' + options.jdoql;
			this._altUrl = 'jdoql?' + options.jdoql;  //note this does not need the classname like /api/org.ecocean.Foo
		} else if (options && options.fields) {
			this._altUrl = this.model.prototype.className();
			var arg = [];
			for (var f in options.fields) {
				arg.push(f + '=="' + options.fields[f] + '"');  //TODO probably need some kind of encoding here? or does .ajax take care of it?
			}
			this._altUrl += '?' + arg.join('&&');
		}
    Backbone.Collection.prototype.fetch.apply(this, arguments);
	},

	url: function() {
//console.log('_altUrl => %o', this._altUrl);
		var u = wildbookGlobals.baseUrl + '/api/';
		if (this._altUrl) { 
			u += this._altUrl;
		} else {
			u += this.model.prototype.className();
		}
console.log('url() -> %s', u);
		return u;
	},

});

