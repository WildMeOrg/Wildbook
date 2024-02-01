
wildbook.Model.SinglePhotoVideo = wildbook.Model.BaseClass.extend({

	idAttribute: 'dataCollectionEventID',

	//note: these assume .encounter is set -- which is only sure if we were loaded via .getImages() on parent encounter  TODO

	url: function() {
		//return this.get('fullFileSystemPath').substr(wildbookGlobals.rootDir.length);
		return wildbookGlobals.dataUrl + '/encounters/' + this.subdir() + '/' + this.get('filename');
	},

	urlSmall: function() {
		return wildbookGlobals.dataUrl + '/encounters/' + this.subdir() + '/' + this.get('dataCollectionEventID') + '.jpg';
	},

	urlMid: function() {
		return wildbookGlobals.dataUrl + '/encounters/' + this.subdir() + '/' + this.get('dataCollectionEventID') + '-mid.jpg';
	},

	subdir: function() { //recycling!  (really "Encounter subdir")
		return wildbook.Model.Encounter.prototype.subdir(this.get('correspondingEncounterNumber'));
	},

});


wildbook.Collection.SinglePhotoVideos = wildbook.Collection.BaseClass.extend({
	model: wildbook.Model.SinglePhotoVideo
});

