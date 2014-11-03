
wildbook.class.BaseClass = augment.defclass({
	constructor: function(a) {
		this.val = a;
	},

	apiUrl: function() {
//TODO baseurl!!
		return '/mm/api/org.ecocean.' + this.meta().className;
	},

//Encounter/f62e6794-ef5c-4508-a5fb-592f069876d9
	foo: function() { console.log('foo on %o', this); }
});
