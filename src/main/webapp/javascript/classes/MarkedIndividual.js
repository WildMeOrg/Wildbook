
wildbook.Model.MarkedIndividual = wildbook.Model.BaseClass.extend({

	idAttribute: 'individualID',

	getEncounters: function(callback) {
		var iid = this.get('individualID');
		if (this.encounters || !iid || (iid == '') || (iid == 'Unassigned')) {
			callback(false);
			return;
		}

		var encs = new wildbook.Collection.Encounters();
		var me = this;
		encs.fetch({
			fields: {individualID: iid},
			success: function(mod, res, opt) {
				me.encounters = mod;
				console.info('successfully fetched Encounters for %s', iid);
				callback(true);
			},
			error: function(mod, res, opt) {
				console.error('error fetching Encounters for %s: %o %o %o', iid, m, res, opt);
				callback(false);
			},
		});
	},

});



wildbook.Collection.MarkedIndividuals = wildbook.Collection.BaseClass.extend({
	model: wildbook.Model.MarkedIndividual
});
