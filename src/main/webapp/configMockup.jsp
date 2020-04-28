<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.List,
java.util.ArrayList,
java.util.Arrays,
java.util.Map,
java.util.HashMap,
java.io.File,
org.json.JSONObject,

org.ecocean.configuration.*

              "
%><%

Shepherd myShepherd = new Shepherd("context0");
myShepherd.beginDBTransaction();

ConfigurationUtil.init();   //not required, but clears cache for us (for testing)

String id = request.getParameter("id");
if (id != null) {
    Configuration conf = ConfigurationUtil.getConfiguration(myShepherd, id);
    response.setContentType("text/plain");
    out.println(conf.toFrontEndJSONObject(myShepherd).toString());
    myShepherd.rollbackDBTransaction();
    return;
}

%><html><head>
<title>Configuration Mockup</title>
<script src="tools/jquery/js/jquery.min.js"></script>
<style>
body {
    font-family: sans, arial;
}
.warn {
    color: #641;
}

.top-menu {
    margin: 6px;
    border: solid 2px #888;
    background-color: #AAA;
    width: 25%;
    height: 100%;
    padding: 8px;
}
#debug {
    position: absolute;
    right: 10px;
    bottom: 10px;
    background-color: #EEE;
    width: 50%;
    font-size: 0.8em;
    height: 5em;
    overflow-y: scroll;
}

.c-label {
    font-weight: bold;
}
.c-description {
    font-size: 0.8em;
    padding: 2px 7px;
    color: #444;
}

.c-link {
    cursor: pointer;
    margin: 4px 6px;
    padding: 0 5px;
    background-color: #CCC;
}
.c-link:hover {
    background-color: #DDC;
}
.c-menulabel {
    font-weight: bold;
    font-size: 0.9em;
    color: #444;
}
.c-menudescription {
    margin: 0 0 0 7px;
    font-size: 0.7em;
    color: #555;
}
</style>
<script>
var lang = null;
var cache = {};
function init() {
    $.ajax({
        url: '../wildbook_data_dir/lang.json',
        type: 'GET',
        dataType: 'json',
        success: function(d) {
            debug('log lang.json of length ' + Object.keys(d).length);
            lang = d;
            build('');
        }
    });
}


function linkClicked(id) {
    console.log('click! %o', id);
    $('#menu').html('');
    build(id);
}

function build(id, el) {
    console.info('building id=%s in el=%o', id, el);
    if (!el) el = $('#menu');
    debug('building id=[' + id + '] in el id=' + el.attr('id'));
    if (cache[id]) {
        display(cache[id], el);
        return;
    }
    $.ajax({
        url: '?id=' + id,
        type: 'GET',
        dataType: 'json',
        complete: function(x, d) {
//console.log('x %o', x);
//console.log('d %o', d);
            if ((x.status != 200) || (!x.responseJSON)) {
                console.log('x=>%o', x);
                console.log('d=>%o', d);
                debug('error fetching; see console. status=' + x.status);
            } else {
                cache[id] = x.responseJSON;
                display(x.responseJSON, el);
            }
        }
    });
}

//we either display in the #menu div, or we are a candidate either for a link or panel
function display(j, el) {
    el.html('');
    var topLevel = (el.attr('id') == 'menu');

    //if (topLevel || j.panel) {
    if (topLevel) {
        if (j.panel) el.addClass('c-panel');
        //label is kinda special cuz we want _something_
        var l = trlang(j, 'label') || '[' + j.name + ']';
        el.append('<div id="c_' + j.name + '_label" class="c-label" title="' + trlang(j, 'alt') + '">' + l + '</div>');
        el.append(plainDiv(j, 'description'));
        el.append(plainDiv(j, 'help'));
        el.append(config(j));  //the guts to set stuff!!
        if (!j.childrenKeys) return;  //no kids, we are done
        //now these become divs for either panels or links depending...
        for (var i = 0 ; i < j.childrenKeys.length ; i++) {
            var kel = $('<div id="c_' + j.name + '_' + j.childrenKeys[i] + '" />');
            el.append(kel);
            build(j.childrenKeys[i], kel);
        }

    } else {
        el.addClass('c-link');
        el.attr('title', trlang(j, 'alt'));
        var l = trlang(j, 'menulabel') || trlang(j, 'label') || '[' + j.name + ']';
        el.append('<div id="c_' + j.name + '_menulabel" class="c-menulabel">' + l + '</div>');
        el.attr('onclick', "return linkClicked('" + j.configurationId + "');");
        el.append(plainDiv(j, 'menudescription'));
    }
}


function plainDiv(j, key) {
    var l = trlang(j, key);
    if (!l) return null;
    return '<div class="c-' + key + '" id="c_' + j.name + '_' + key + '">' + l + '</div>';
}

function trlang(j, key) {
    return lang[j.translationId + '_' + key.toUpperCase()] || '';
}

function debug(msg) {
    $('#debug').append('<div>' + msg + '</div>');
    $("#debug").scrollTop($("#debug")[0].scrollHeight);
}


function config(j) {
    if (!j.settable) return null;
}

</script>
</head>
<body onLoad="init()">
<div class="top-menu" id="menu"></div>
<div id="debug"></div>
</body></html>
<%
myShepherd.commitDBTransaction();
%>
