
var wildbook = {
    classNames: [
        'Encounter',
        'MarkedIndividual',
        'SinglePhotoVideo',
        'MediaAsset',
        'Measurement',
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


/*
    fetch: function(cls, arg, callback) {
        if (!cls || !cls.prototype || !cls.prototype.meta) {
            console.error('invalid class %o', cls);
            return;
        }
        var url = cls.prototype.url();
        if (arg) url += '/' + arg;
console.log('fetch() url = ' + url);

        var ajax = {
            url: url,
            success: function(d) {
                if (!(d instanceof Array)) d = [d];
                var arr = [];
                for (var i = 0 ; i < d.length ; i++) {
                    var obj = new cls(d[i]);  //TODO do we need to trap failures?
                    arr.push(obj);
                }
                callback(arr);
            },
            error: function(x,a,b) { callback({error: a+': '+b}); },
            type: 'GET',
            dataType: 'json'
        };
console.log('is %o', ajax);
        $.ajax(ajax);
    },
*/


    iaEnabled: function() {
        //FIXME when IBEISIA is caught up to current
        return (wildbookGlobals && wildbookGlobals.iaStatus && wildbookGlobals.iaStatus.map && wildbookGlobals.iaStatus.map.iaEnabled);
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
					google: 'FOO',  //TODO we dont have support for this yet
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

    isValidEmailAddress: function(addr) {
        var regex = new RegExp(wildbookGlobals.validEmailRegexPattern);
        return regex.test(addr);
    },

    openInTab: function(url) {
        var win = window.open(url, '_blank');
        win.focus();
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
