
wildbook.class.MarkedIndividual = augment(wildbook.class.BaseClass, function(uber) {
	this.constructor = function(o) {
		uber.constructor.call(this, o);
	};

	this.ind = function() { console.log('i am an Individual'); };
});

