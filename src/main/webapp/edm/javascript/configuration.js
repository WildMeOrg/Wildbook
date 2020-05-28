
var wbConf = {
    lang: {},
    cache: {},

loadLang: function(type, code, callback) {
    if (!code) code = 'en';
    $.ajax({
        url: 'json/lang/' + type + '.' + code + '.json?' + new Date().getTime(),
        type: 'GET',
        dataType: 'json',
        success: function(d) {
            wbConf.debug('log lang.json of length ' + Object.keys(d).length);
            Object.assign(wbConf.lang, d);
            if (typeof callback == 'function') callback();
        },
        error: function(x) {
            console.warn('error loading lang %s %o', code, x);
            if (code == 'en') {
                alert('could not load fallback language=en');
            } else {
                wbConf.loadLang(type, 'en', callback);
            }
        }
    });
},

init: function() {
    wbConf.loadLang('configuration', 'en', function() {
        wbConf.loadLang('message', 'en', function() {
            wbConf.build('');
        });
    });
},

linkClicked: function(id) {
    console.log('click! %o', id);
    $('#menu').html('');
    wbConf.build(id);
},

build: function(id, el) {
    console.info('building id=%s in el=%o', id, el);
    if (!el) el = $('#menu');
    wbConf.debug('building id=[' + id + '] in el id=' + el.attr('id'));
if (wbConf.cache[id]) console.log('CACHE xxx id=[%s] kids=%o', id, wbConf.cache[id].childrenKeys);
/*
    if (wbConf.cache[id]) {
        wbConf.display(wbConf.cache[id], el);
        return;
    }
*/
    $.ajax({
        url: '?id=' + id,
        type: 'GET',
        dataType: 'json',
        complete: function(x, d) {
//console.log('x %o', x);
//console.log('d %o', d);
            if ((x.status != 200) || (!x.responseJSON)) {
                console.log('id=%s x=>%o', id, x);
                console.log('d=>%o', d);
                wbConf.debug('error fetching; see console. status=' + x.status);
            } else {
                wbConf.cache[id] = x.responseJSON;
                wbConf.display(x.responseJSON, el);
            }
        }
    });
},

//we either display in the #menu div, or we are a candidate either for a link or panel
display: function(j, el) {
    el.html('');
    var topLevel = (el.attr('id') == 'menu');

console.log('xxxxxyxy %o', j);
    var isPanel = j && j.schema && j.schema.panel;
    if (j.schema && (j.schema.displayOrder != undefined)) {
        el.addClass('c-display-order-' + j.schema.displayOrder);
        el.attr('displayorder', j.schema.displayOrder);
    }
    if (topLevel || isPanel) {
        if (isPanel) {
            el.addClass('c-panel');
        } else if (j.configurationId) {
            el.append('<div class="up-arrow" onClick="return wbConf.linkClicked(\'' + (j.parentConfigurationId || '') + '\');">&#8593;</div>');
        }
        //label is kinda special cuz we want _something_
        var l = wbConf.trlang(j, 'label') || '[' + j.name + ']';
        el.append('<div id="c_' + j.name + '_label" class="c-label" title="' + wbConf.trlang(j, 'alt') + '">' + l + '</div>');
        el.append(wbConf.plainDiv(j, 'description'));
        el.append(wbConf.plainDiv(j, 'help'));
        el.append(wbConf.config(j));  //the guts to set stuff!!
console.log('xxxx %s %s %o', j.configurationId, j.translationId, j.childrenKeys);
        if (!j.childrenKeys) {  //no kids, we are done
            el.addClass('c-loaded');
            wbConf.checkSort(el.parent());
            return;
        }
        //now these become divs for either panels or links depending...
        var kids = $('<div class="c-children" />');
        for (var i = 0 ; i < j.childrenKeys.length ; i++) {
            var kel = $('<div id="c_' + j.name + '_' + j.childrenKeys[i] + '" />');
            kids.append(kel);
            var kkey = j.childrenKeys[i];
            if (j.configurationId) kkey = j.configurationId + '.' + kkey;
            wbConf.build(kkey, kel);
        }
        el.append(kids);

    } else {
        el.addClass('c-link');
        el.attr('title', wbConf.trlang(j, 'alt'));
        var l = wbConf.trlang(j, 'menulabel') || wbConf.trlang(j, 'label') || '[' + j.name + ']';
        el.append('<div id="c_' + j.name + '_menulabel" class="c-menulabel">' + l + '</div>');
        el.attr('onclick', "return wbConf.linkClicked('" + j.configurationId + "');");
        el.append(wbConf.plainDiv(j, 'menudescription'));
    }
    el.addClass('c-loaded');
    wbConf.checkSort(el.parent());
},

checkSort: function(par) {
//console.log('PARENT %o', par);
    var pending = 0;
    par.children().each(function(i, el) {
        if (!el.classList.contains('c-loaded')) pending++;
//console.log("ORD %d >> %o", i, el.getAttribute('displayorder'));
//console.log(el);
    });
    console.log('PENDING = %d', pending);
    if (pending < 1) wbConf.sortKids(par);
},

sortKids: function(par) {
    var kids = par.children();
    kids.sort(function(a, b) {
        var oa = parseFloat(a.getAttribute('displayorder'));
        var ob = parseFloat(b.getAttribute('displayorder'));
//console.log('oa:ob %o:%o', oa, ob);
        if (isNaN(oa) && isNaN(ob)) return 0;
        if (isNaN(oa)) return 1;
        if (isNaN(ob)) return -1;
        if (oa > ob) return 1;
        if (ob > oa) return -1;
        return 0;
    });
    kids.detach().appendTo(par);
},

plainDiv: function(j, key) {
    var l = wbConf.trlang(j, key);
    if (!l) return null;
    return '<div class="c-' + key + '" id="c_' + j.name + '_' + key + '">' + l + '</div>';
},

trlang: function(j, key) {
    return wbConf.lang[j.translationId + '_' + key.toUpperCase()] || '';
},

idPath: function(id) {
    if (!id) return [];
    return id.split('.');
},

_traverse: function(idPath, obj) {
console.log('idPath=%o, obj=%o', idPath, obj);
    if (!Array.isArray(idPath)) return false;
    if (!obj) return false;
    if (typeof obj != 'object') return obj;
    if (idPath.length < 1) return obj;
    var k = idPath.shift();
    if (!obj[k]) return false;
    return wbConf._traverse(idPath, obj[k]);
},

debug: function(msg) {
    $('#debug').append('<div>' + msg + '</div>');
    $("#debug").scrollTop($("#debug")[0].scrollHeight);
},

config: function(j) {
    if (!j.settable) return null;
    if (typeof wbConf.makeUI[j.name] == 'function') return wbConf.makeUI[j.name](j);
    if (typeof wbConf.makeUI[j.fieldType] != 'function') {
        return '<div class="c-settable"><b>no makeUI function</b><pre>' + JSON.stringify(j, null, 4) + '</pre></div>';
    }
    return wbConf.makeUI[j.fieldType](j);
},

//when user clicks SET button
clickSet: function(id) {
    console.info('clickSet id=%o, cache=%o', id, wbConf.cache[id]);
    if (!wbConf.cache[id]) return false;  //snh cuz we got here post-build
    $('#c_set_' + wbConf.cache[id].name + ' .set-button').hide().after('<i class="set-message">saving</i>');
    var d = {};
    d[id] = $('#c_' + wbConf.cache[id].name + ' .c-settable input').val(); //TODO handle multiple?
console.log('DATA TO SAVE d=%o', d);
    $.ajax({
        url: '../api/v0/configuration',
        type: 'POST',
        dataType: 'json',
        data: JSON.stringify(d),
        contentType: 'application/javascript',
        complete: function(x) {
            wbConf.handleResponse(x,
            function() {
                $('#c_set_' + wbConf.cache[id].name + ' .set-message').html('OK!');
            },
            function() {
                $('#c_set_' + wbConf.cache[id].name + ' .set-message').html('failed.  :(');
            });
        }
    });
    return true;
},

handleResponse: function(xhr, successCallback, failureCallback) {
console.info('RESPONSE === %o', xhr);
    if (!xhr || !xhr.status) xhr = { status: 999 };
    if ((xhr.status == 200) && xhr.responseJSON && xhr.responseJSON.success) {
        console.info('wbConf.handleResponse got 200 OK, message=%o', xhr.responseJSON.message);
        if (typeof successCallback == 'function') successCallback(xhr.responseJSON);
        return;
    }
    var message = (xhr.responseJSON && xhr.responseJSON.message) || { key: 'error' };
    if (!message.key) message.key = 'error';
    message._status = { code: xhr.status, text: xhr.statusText };
    message._lang = wbConf.lang['MESSAGE_' + message.key.toUpperCase()];
    //since we dont really have way of handling messages.... oh well!
    alert('FAKE MESSAGE HANDLER! 😃\n' + JSON.stringify(message, null, 4));
    if (typeof failureCallback == 'function') failureCallback(xhr);
},

makeUI: {

    color: function(j) {
        var h = '<div class="c-settable" id="c_set_' + j.name + '">';
        h += '<input type="color" name="' + j.name + '" />';
        h += '<div><input class="set-button" type="button" value="set" onClick="return wbConf.clickSet(\'' + j.configurationId + '\');" /></div>';
        h += '</div>';
        return h;
    },
    string: function(j) {
        var h = '<div class="c-settable" id="c_set_' + j.name + '">';
        h += '<input name="' + j.name + '" />';
        h += '<div><input class="set-button" type="button" value="set" onClick="return wbConf.clickSet(\'' + j.configurationId + '\');" /></div>';
        h += '</div>';
        return h;
    },

    configuration_site_species: function(j) {
        var h = '<div id="configuration_site_species_itis">';
        h += '<div id="configuration_site_species_itis_results">';
        h += '</div>';
        h += '<input id="configuration_site_species_itis_term" />';
        h += '<input type="button" value="search" onClick="wbConf.itisSearch();" />';
        h += '</div>';
        return h;
    }
},

onUpdate: {
    configuration_site_look_textColor: function(d) {
        //change site colors?
    }
},

itisSearch: function() {
    var term = $('#configuration_site_species_itis_term').val();
    if (!term) return;
    wbConf.askITIS(term, function(res) {
        var el = $('#configuration_site_species_itis_results');
        if (!res || (res.length < 1)) {
            el.append('<i>no results</i>');
        } else {
            for (var i = 0 ; i < res.length ; i++) {
                var h = '<div><i>' + res[i].sciName + '</i>';
                h += ' [<b>' + res[i].tsn + '</b>]';
                var cn = [];
                if (res[i].commonNameList && res[i].commonNameList.commonNames) for (var j = 0 ; j < res[i].commonNameList.commonNames.length ; j++) {
                    cn.push(res[i].commonNameList.commonNames[j].commonName);
                }
                if (cn.length) h += ' (' + cn.join(', ') + ')';
                h += '</div>';
                el.append(h);
            }
        }
    });
},


/* data looks like below.  we return [] at .anyMatchList ... useful(?) field denoted with >>

{
    anyMatchList: [
        {
            author: "Gray, 1846",
            class: "gov.usgs.itis.itis_service.data.SvcAnyMatch",
            commonNameList: {
                class: "gov.usgs.itis.itis_service.data.SvcCommonNameList",
                commonNames: [
                    {
                        class: "gov.usgs.itis.itis_service.data.SvcCommonName",
>>                      commonName: "humpback whales",
>> ?                    language: "English",
                        tsn: "180529"
                    }
                ],
                tsn: "180530"
            },
            matchType: "COMMON",
>>          sciName: "Megaptera novaeangliae",
>>          tsn: "180530"
},

*/
askITIS: function(term, callback) {
    // cuz this is jsonp we need a way to get to our callback so we generate an on-the-fly function for jsonp to callback to (!)
    var fname = 'askITISjsonp' + Math.random().toString().substr(2);
    wbConf[fname] = function(d) {
        console.log('askITIS callback => %o', d);
        if (!d.anyMatchList) {
            console.info('askITIS returned null anyMatchList');
            d.anyMatchList = [];
        }
        callback(d.anyMatchList);
    };
    $.ajax({
        url: 'https://www.itis.gov/ITISWebService/jsonservice/searchForAnyMatch?srchKey=' + term,
        type: 'GET',
        cache: true,
        dataType: 'jsonp',
        jsonp: 'jsonp',
        jsonpCallback: 'wbConf.' + fname,
        crossDomain: true,
        error: function(x) {  //might be offline btw
        }
    });
}


};
