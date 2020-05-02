
var wbConf = {
    lang: null,
    cache: {},

loadLang: function(code, callback) {
    if (!code) code = 'en';
    $.ajax({
        url: 'json/lang/configuration.' + code + '.json?' + new Date().getTime(),
        type: 'GET',
        dataType: 'json',
        success: function(d) {
            wbConf.debug('log lang.json of length ' + Object.keys(d).length);
            wbConf.lang = d;
            if (typeof callback == 'function') callback();
        },
        error: function(x) {
            console.warn('error loading lang %s %o', code, x);
            if (code == 'en') {
                alert('could not load fallback language=en');
            } else {
                wbConf.loadLang('en', callback);
            }
        }
    });
},

init: function() {
    wbConf.loadLang('en', function() { wbConf.build(''); });
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

debug: function(msg) {
    $('#debug').append('<div>' + msg + '</div>');
    $("#debug").scrollTop($("#debug")[0].scrollHeight);
},

config: function(j) {
    if (!j.settable) return null;
    return '<div class="c-settable"><pre>' + JSON.stringify(j, null, 4) + '</pre></div>';
}


};
