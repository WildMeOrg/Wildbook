
wildbook.class.Encounter = augment(wildbook.class.BaseClass, function(uber) {
	this.constructor = function(o) {
		uber.constructor.call(this, o);
	};

	this.getIndividual = function(callback) {
		if (this.individual || !this.individualID || (this.individualID == '') || (this.individualID == 'Unassigned')) callback(false);
		var me = this;
		wildbook.fetch(wildbook.class.MarkedIndividual, this.individualID, function(d) {
			if (d[0]) me.individual = d[0];
			callback(true);
		});
	};

});

