
wildbook.Model.Encounter = wildbook.Model.BaseClass.extend({

	idAttribute: 'catalogNumber',   //TODO put this in classDefinitions from java (somehow)

	refClass: {
		measurements: 'Measurements',
		images: 'SinglePhotoVideos',
	},

/*
	defaults: _.extend({}, wildbook.Model.BaseClass.prototype.defaults, {
		someOther: 'default',
	}),
*/


	thumbUrl: function() {
		return wildbookGlobals.dataUrl + '/encounters/' + this.subdir() + '/thumb.jpg';
	},


	getIndividual: function(callback) {
		var iid = this.get('individualID');
		if (this.individual || !iid || (iid == '') || (iid == 'Unassigned')) {
			callback(false);
			return;
		}

		var ind = new wildbook.Model.MarkedIndividual({individualID: iid});
		var me = this;
		ind.fetch({
			success: function(mod, res, opt) {
				me.individual = mod;
				console.info('successfully fetched MarkedIndividual %s', iid);
				callback(true);
			},
			error: function(mod, res, opt) {
				console.error('error fetching MarkedIndividual [id %s]: %o %o %o', iid, m, res, opt);
				callback(false);
			},
		});
	},


	subdir: function(d) {
		if (!d) d = this.id;
		var r = new RegExp('^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$', 'i');
		if (r.test(d)) d = d.substr(0,1) + '/' + d.substring(1,2) + '/' + d;
		return d;
	}

});


wildbook.Collection.Encounters = wildbook.Collection.BaseClass.extend({
	model: wildbook.Model.Encounter
});

