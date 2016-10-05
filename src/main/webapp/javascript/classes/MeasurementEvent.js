
wildbook.Model.MeasurementEvent = wildbook.Model.BaseClass.extend({

	idAttribute: 'DataCollectionEventID',

});


wildbook.Collection.MeasurementEvents = wildbook.Collection.BaseClass.extend({
	model: wildbook.Model.MeasurementEvent
});

