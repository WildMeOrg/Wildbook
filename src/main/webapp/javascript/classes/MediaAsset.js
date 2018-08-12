
wildbook.Model.MediaAsset = wildbook.Model.BaseClass.extend({

	idAttribute: 'id',

        className: function() { return 'org.ecocean.media.MediaAsset'; },


        hasLabel: function(label) {
            var l = this.get('labels');
            if (!l || (l.length < 1)) return false;
            return (l.indexOf(label) > -1);
        },

	labelUrl: function(label, fallback) {
            if (!label || this.hasLabel(label)) return this.get('url') || fallback;
            var kids = this.findChildrenWithLabel(label);
            if (kids.length > 0) return kids[0].labelUrl(label, fallback);
            return fallback;
	},

        findChildrenWithLabel: function(label) {
            var kids = [];
            var c = this.get('children');
            if (!c) return kids;
            for (var i = 0 ; i < c.length ; i++) {
                if (c[i].labels && (c[i].labels.indexOf(label) > -1)) kids.push(new wildbook.Model.MediaAsset(c[i]));
            }
            return kids;
        },

        imgElement: function(label) {
            var url = this.labelUrl(label, this.get('url'));
            if (!url) return '<!-- imgElement() failed -->';
            return '<img src="' + url + '" />';
        },
});


wildbook.Collection.MediaAssets = wildbook.Collection.BaseClass.extend({
	model: wildbook.Model.MediaAsset
});

