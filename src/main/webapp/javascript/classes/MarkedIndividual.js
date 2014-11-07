
wildbook.Model.MarkedIndividual = wildbook.Model.BaseClass.extend({

	idAttribute: 'individualID',

});



wildbook.Collection.MarkedIndividuals = wildbook.Collection.BaseClass.extend({
	model: wildbook.Model.MarkedIndividual
});
