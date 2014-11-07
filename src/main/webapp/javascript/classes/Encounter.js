
wildbook.Model.Encounter = wildbook.Model.BaseClass.extend({

	idAttribute: 'catalogNumber',   //TODO put this in classDefinitions from java (somehow)

/*
	defaults: _.extend({}, wildbook.Model.BaseClass.prototype.defaults, {
		someOther: 'default',
	}),
*/

/*
	url: function() {
	},
*/

	getImages: function() {
	},

///TODO use collection, duh
	getIndividual: function(callback) {
		var iid = this.get('individualID');
		if (this.individual || !iid || (iid == '') || (iid == 'Unassigned')) {
			callback(false);
			return;
		}

console.log(iid);
		var ind = new wildbook.Model.MarkedIndividual({individualID: iid});
console.log(ind);
		var me = this;
		ind.fetch({
			success: function(mod, res, opt) {
				me.individual = mod;
				callback(true);
			},
			error: function(mod, res, opt) {
				console.error('%o %o %o', m, res, opt);
				callback(false);
			},
		});
	}


});


wildbook.Collection.Encounters = wildbook.Collection.BaseClass.extend({
	model: wildbook.Model.Encounter,

});

