
wildbook.Model.Encounter = wildbook.Model.BaseClass.extend({

	idAttribute: 'catalogNumber',   //TODO put this in classDefinitions from java (somehow)

	refClass: {
		measurements: 'Measurements',
		images: 'MediaAssets',
	},

/*
	defaults: _.extend({}, wildbook.Model.BaseClass.prototype.defaults, {
		someOther: 'default',
	}),
*/

	// this allows calls to the enc rest api to use the lightweight rest server
	url: function() {
		console.log("Encounter-specific url function called!");
		if (!this.id) return false;  //how are you really supposed to handle this??? TODO
		return wildbookGlobals.baseUrl + '/lightRest/' + this.className() + '/' + this.id;
	},



        //TODO have special way to get which Annotation
	thumbUrl: function() {
                if (!this.get('annotations') || (this.get('annotations').length < 1)) return '';
                var ma = this.get('annotations')[0].mediaAsset;
                if (!ma) return '';
                ma = new wildbook.Model.MediaAsset(ma);
                return ma.labelUrl('_thumb', '');
		//return wildbookGlobals.dataUrl + '/encounters/' + this.subdir() + '/thumb.jpg';
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


	//this is built off encounter sub-date parts (year, month etc) returns true js Date object (see also dateAsString() below)
	date: function() {
		var y = this.get('year');
		if (y < 1) return false;
		var m = this.get('month') - 1;
		if (m < 0) return false;
		var d = this.get('day');
		if (d < 1) return false;
		var H = this.get('hour');
		var M = this.get('minutes');
		if (H < 0) H = 0;
		if (M < 0) M = 0;
//TODO wonder if we should instead trust .dateInMilliseconds ???
//console.log('%o %o %o %o %o', y, m, d, H, M);
		var d = new Date(y, m, d, H, M, 0);
		if (!wildbook.isValidDate(d)) return false;
		return d;
	},

	//a bit like above, but returns numeric value for sake of sorting (and can handle YYYY and YYYY-MM
	dateSortable: function() {
		var y = this.get('year');
		if (y < 1) return 0;
		var m = this.get('month') - 1;
		if ((m < 0) || (m > 11)) m = 0;
		var d = this.get('day');
		if (d < 1) d = 1;  //TODO max day varies per month, grr
		var H = this.get('hour');
		var M = this.get('minutes');
		if (H < 0) H = 0;
		if (M < 0) M = 0;
		var d = new Date(y, m, d, H, M, 0);
		if (!wildbook.isValidDate(d)) return 0;
		return d.getTime();
	},


	//this returns the date (as a string) in a way that allows only year-month or year values.
	dateAsString: function() {
		var dt = this.get('year');
		if (dt < 1) return '';
		dt = new String(dt);
		var m = this.get('month');
		if (m > 0) {
			dt += '-' + ((m < 10) ? '0' : '') + m;
			var d = this.get('day');
			if (d > 0) dt += '-' + ((d < 10) ? '0' : '') + d;
		}
		return wildbook.flexibleDate(dt);
	},


	subdir: function(d) {
		if (!d) d = this.id;
		var r = new RegExp('^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$', 'i');
		if (r.test(d)) d = d.substr(0,1) + '/' + d.substring(1,2) + '/' + d;
		return d;
	}

});


wildbook.Collection.Encounters = wildbook.Collection.BaseClass.extend({
	model: wildbook.Model.Encounter,
	url: function() {
		var u = wildbookGlobals.baseUrl + '/LightRest/';
		if (this._altUrl) { 
			u += this._altUrl;
		} else {
			u += this.model.prototype.className();
		}
		console.log('Encounter-specific url() -> %s', u);
		return u;
	}

});

