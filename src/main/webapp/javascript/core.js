
var wildbook = {
    classNames: [
        'Encounter',
        'MarkedIndividual',
        'SinglePhotoVideo',
        'MediaAsset',
        'Measurement',
        'Occurrence'
    ],

    Model: {},
    Collection: {},

    loadAllClasses: function(callback) {
        this._loadAllClassesCount = this.classNames.length;
        var me = this;
        for (var i = 0 ; i < this.classNames.length ; i++) {
        classInit(this.classNames[i], function() {
            me._loadAllClassesCount--;
//console.log('huh??? %o', me._loadAllClassesCount);
            if (me._loadAllClassesCount <= 0) {
                console.info('wildbook.loadAllClasses(): DONE loading all classes');
                if (callback) callback();
            }
        });
        }
    },

    // h/t http://stackoverflow.com/questions/1353684/detecting-an-invalid-date-date-instance-in-javascript
    isValidDate: function(d) {
        if (Object.prototype.toString.call(d) !== "[object Date]") return false;
        return !isNaN(d.getTime());
    },

    //oh the joys of human representation of time (and the inconsistency of browsers implementation of new Date() )
    //note, this returns an actual js Date object.  see below for one which handles a string input and output a little better (e.g. "2001-01" will work)
    parseDate: function(s) {
        s = s.trim();  //some stuff had trailing spaces, ff fails.
        //we need to allow things like just a year (!) or just year-month(!?) ... for sorting purposes.  so:
        if (s.length == 4) s += '-01';  //YYYY -> YYYY-01
        if (s.length == 7) s += '-01';  //YYYY-MM -> YYYY-MM-01
        if (s.length == 10) s += 'T00:00:00';
        //hope we have the right string now -- but wildbook does not put "T" between date/time, which chokes ff and ie(?).
        s = s.substr(0,10) + 'T' + s.substr(11);
        var d = new Date(s);
        if (!this.isValidDate(d)) return false;
        return d;
    },

    //can handle case where is only year or year-month
    flexibleDate: function(s) {
        s = s.trim();
        if (s.length == 4) return s;  //year only
        if (s.length == 7) return s.substr(0,4) + '-' + s.substr(5);  //there is no toLocaleFoo for just year-month.  :(  sorry.
        //now we (should?) have at least y-m-d, with possible time

        var d = this.parseDate(s);
        if (!d) return '';
        return s;
        //i dont think we need to do this, if we "trust" we are y-m-d already!
        return d.toISOString().substring(0,10);
    },


    init: function(callback) {
        classInit('Base', function() { wildbook.loadAllClasses(callback); });  //define base class first - rest can happen any order
    },

    errorDialog: false,

    showError: function(ex) {
        var message;
        var details;
        if (ex.status === 500) {
            message = ex.responseJSON.message;
            details = ex.responseJSON.totalStackTrace;
        } else {
            message = "Error " + ex.status + ": " + ex.statusText;
            details = null;
        }
        this.showAlert(message, details, 'Error');
    },

    showAlert: function(message, details, title) {
				if (!title) title = 'Info';
        var dialog;
        if (! this.errorDialog) {
            dialog = $('<div id="alertdialog" style="display: none;">')
            .append (
                '<div style="overflow-y:auto;">' +
                '<pre id="alertmessage"></pre></div>' +
                '<br/>' +
                '<button id="detailsbutton">Details &gt;&gt;</button>' +
                '<div id="detailscontainer" style="width: 100%; height: 400px; overflow: auto; display: none;">' +
                '<pre id="detailscontent"></pre>' +
                '</div>'
            );

            this.errorDialog = true;
        } else {
            dialog = $("#alertdialog");
        }

        //
        //   Positioning at top so that when the details are clicked we can
        //   expand the form to show the whole details without it going off
        //   the screen and the user having to move the form with the mouse.
        //
        dialog.dialog( {
            autoOpen: true,
            //dialogClass: "alertdialog",
            modal: false,
            title: title,
            closeOnEscape: true,
            buttons: { "OK": function() { $(this).dialog("close"); } },
            open: function() {
                $("#detailsbutton").button();
                $("#detailsbutton").click( function(e) {
                    $("#detailscontainer").toggle();
                } );

                if (details) {
                    $("#detailsbutton").show();
                } else {
                    $("#detailsbutton").hide();
                }

                $("#alertmessage").html( message );

                $("#detailscontainer").hide();
                $("#detailscontent").html(details);
            },
            width: 600,
            appendTo: "body",
            resizable: false } );
    },

		//making a whole social sub-object here, just cuz it seems like things might get busy
		social: {
			SERVICE_NOT_SUPPORTED: 'SERVICE_NOT_SUPPORTED',
			enabled: function(svc) {  //svc is optional, but performs additional check that we have wildbookGlobals.social.FOO for that service
				if (!wildbookGlobals || !wildbookGlobals.social) return false;
				if (svc && !wildbookGlobals.social[svc]) return false;
				return true;
			},
			allServices: function() {  //all possible supported by system
				if (!wildbook.social.enabled()) return [];
				return ['facebook', 'google', 'flickr'];
			},
			myServices: function() {  //based on what we have defined in socialAuth.properties file (various aspects may be en/disabled)
				if (!wildbook.social.enabled()) return [];
				return Object.keys(wildbookGlobals.social);
			},
			featureEnabled: function(svc, feature) {
				if (!wildbook.social.enabled(svc)) return false;
				if (wildbookGlobals.social[svc][feature] && wildbookGlobals.social[svc][feature].allow && (wildbookGlobals.social[svc][feature].allow != 'false')) return true;
				return false;
			},
			//note: this is the public api key. secret keys are never made public (i.e. in js), so if you need that, talk to the backend.
			apiKey: function(svc) {  //maps varying property name for each api key (from properties file)
				if (svc == undefined) return wildbook.social.SERVICE_NOT_SUPPORTED;
				if (!wildbook.social.enabled(svc)) return wildbook.social.SERVICE_NOT_SUPPORTED;
				var keyMap = {
					facebook: 'appid',
					flickr: 'key',
					google: 'FOO',  // we dont have support for this yet
				};
				return wildbookGlobals.social[svc].auth[keyMap[svc]] || wildbook.social.SERVICE_NOT_SUPPORTED;
			}
		}, //end social.

    cleanUrl: function (url) {
        return encodeURI(url).replace(new RegExp('#', 'g'), '%23').replace(new RegExp('\\?', 'g'), '%3F');
    },

// h/t http://stackoverflow.com/a/105074
    uuid: function() {
        function s4() {
            return Math.floor((1 + Math.random()) * 0x10000).toString(16).substring(1);
        }
        return s4() + s4() + '-' + s4() + '-' + s4() + '-' + s4() + '-' + s4() + s4() + s4();
    },

    // h/t https://davidwalsh.name/merge-arrays-javascript
    arrayMerge: function(arr, append) {
        Array.prototype.push.apply(arr, append);
    },

    isValidEmailAddress: function(addr) {
        var regex = new RegExp(wildbookGlobals.validEmailRegexPattern);
        return regex.test(addr);
    },

    openInTab: function(url) {
        var win = window.open(url, '_blank');
        if (win) win.focus();
    },

    //this is its own mess of stuff related to the logged in user
    user: {
        getUsername: function() {
            return wildbookGlobals.username || null;
        },
        isAnonymous: function() {
            return !wildbookGlobals.username;
        }
    },



    /*
        locationIds will be ignored if it contains and empty string '' (as this is assumed to represent "all" in a pulldown).
        callback receives the xhr from ajax.complete
    */
    sendMediaAssetsToIA: function(assetIds, locationIds, skipIdent, callback) {
        var data = {
            v2: true,
            taskParameters: { skipIdent: skipIdent || false },
            mediaAssetIds: assetIds
        };
        if (!skipIdent && locationIds && (locationIds.indexOf('') < 0)) data.taskParameters.matchingSetFilter = { locationIds: locationIds };
        console.log('locationIds=%o assetIds=%o data=%o', locationIds, assetIds, data);
        $.ajax({
            url: wildbookGlobals.baseUrl + '/ia',
            dataType: 'json',
            data: JSON.stringify(data),
            type: 'POST',
            contentType: 'application/javascript',
            complete: function(x) {
                console.log('sendToIA() response: %o', x);
                if (typeof callback == 'function') callback(x);
            }
        });
    },


    /*
        'args' can be pretty extensive here, so check out autocomplete() docs.
        some useful(?) examples:
            * select: function(ev, ui) {}   // item is selected
            * appendTo: $('#some-element')
            * resMap: (optional) custom function to map /SiteSearch?term= results to {label:, value:, type:}
        if nothing passed, required ones are generated
    */
    makeAutocomplete: function(el, args) {
        if (typeof args != 'object') args = {};

        //this is "default behavior"
        //  item has:  item.species, item.label, item.type, item.value
        if (!args.resMap) args.resMap = function(data) {
            var res = $.map(data, function(item) {
                var label = item.label;
                if (item.type == 'user') {
                    label = 'User: ' + label;
                } 
                return { label: label, type: item.type, value: item.value };
            });
            return res;
        }

        if (!args.source) args.source = function(request, response) {
//console.info('autocomplete.request %o', request);
            $.ajax({
                url: wildbookGlobals.baseUrl + '/SiteSearch',
                dataType: "json",
                data: {
                    term: request.term
                },
                success: function( data ) {
                    var res = args.resMap(data);
//console.info('autocomplete.res->%o', res);
                    response(res);
                }
            });
        };

        $(el).autocomplete(args);
    }
};


function classInit(cname, callback) {
    console.info('attempting to load class %s', cname);
    $.getScript(wildbookGlobals.baseUrl + '/javascript/classes/' + cname + '.js', function() {
        console.info('successfully loaded class %s', cname);

        //just a way to get actual name... hacky, but cant figure out the elegant way??
        if (wildbook.Model[cname] && wildbook.Model[cname].prototype) {
            wildbook.Model[cname].prototype.meta = function() {
                return {
                    className: cname
                };
            };
        }
        ////// end hackery

        callback();
    });
}


//$.getScript('/mm/javascript/prototype.js', function() { wildbook.init(); });

//$(document).ready(function() { wildbook.init(); });
