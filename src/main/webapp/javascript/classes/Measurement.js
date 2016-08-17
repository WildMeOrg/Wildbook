
wildbook.Model.Measurement = wildbook.Model.BaseClass.extend({

	idAttribute: 'DataCollectionEventID',

});


wildbook.Collection.Measurements = wildbook.Collection.BaseClass.extend({
	model: wildbook.Model.Measurement
});

