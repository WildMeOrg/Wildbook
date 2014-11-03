
wildbook.class.Encounter = augment(wildbook.class.BaseClass, function(uber) {
	this.constructor = function(o) {
		uber.constructor.call(this, o);
	};

	this.enc = function() { console.log('i am an Encounter'); };
});

