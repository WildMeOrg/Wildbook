
wildbook.class.Encounter = wildbook.class.BaseClass.extend({

	idAttribute: 'catalogNumber',   //TODO put this in classDefinitions from java (somehow)

/*
	defaults: _.extend({}, wildbook.class.BaseClass.prototype.defaults, {
		someOther: 'default',
	}),
*/

/*
	url: function() {
	},
*/

///TODO use collection, duh
	getIndividual: function(callback) {
		var iid = this.get('individualID');
		if (this.individual || !iid || (iid == '') || (iid == 'Unassigned')) {
			callback(false);
			return;
		}

console.log(iid);
		var ind = new wildbook.class.MarkedIndividual({individualID: iid});
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

