
wildbook.Model.Measurement = wildbook.Model.BaseClass.extend({

	idAttribute: 'DataPointID',

});


wildbook.Collection.Measurements = wildbook.Collection.BaseClass.extend({
	model: wildbook.Model.Measurement
});

