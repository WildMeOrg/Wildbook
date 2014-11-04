
wildbook.class.BaseClass = augment.defclass({

	//gets passed in the obj (hash) from api rest call
//TODO make this populate more wisely! likely based upon class property defn or some such...
	constructor: function(obj) {
		for (var p in obj) {
			this[p] = obj[p];
		}
	},

	apiUrl: function() {
//TODO baseurl!!
		return '/mm/api/org.ecocean.' + this.meta().className;
	},

	fields: function() {
		if (!wildbookGlobals.classDefinitions[this.class]) return;
		var rtn = {};
		for (var fn in wildbookGlobals.classDefinitions[this.class].fields) {
			if (fn.indexOf('jdo') == 0) continue;
			var fh = { javaType: wildbookGlobals.classDefinitions[this.class].fields[fn] };
			fh.value = this[fn];
			fh.settable = !(wildbookGlobals.classDefinitions[this.class].permissions && (wildbookGlobals.classDefinitions[this.class].permissions[fn] == 'deny'));
			rtn[fn] = fh;
		}
		return rtn;
	},

//Encounter/f62e6794-ef5c-4508-a5fb-592f069876d9
	foo: function() { console.log('foo on %o', this); }
});
