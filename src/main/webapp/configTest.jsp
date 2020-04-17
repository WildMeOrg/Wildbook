<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.List,
java.util.ArrayList,
java.util.Arrays,
java.util.Map,
java.util.HashMap,
java.io.File,
org.json.JSONObject,

org.ecocean.configuration.*,

org.ecocean.media.*
              "
%><html><head>
<title>Configuration Test</title>
<script src="tools/jquery/js/jquery.min.js"></script>
<style>
body {
    font-family: sans, arial;
}
pre {
    display: inline-block;
    padding: 10px;
    margin: 6px;
    background-color: #CCC;
}
.value {
    border-radius: 4px;
    padding: 6px;
    margin: 2px;
    display: inline-block;
    color: #FFF;
    background-color: #8AF;
}
.warn {
    color: #641;
}
.set {
    border-radius: 4px;
    font-size: 0.8em;
    padding: 6px;
    margin: 3px;
    display: inline-block;
    color: #888;
    background-color: #8FA;
}
.set a {
    color: #888;
}
.warn {
    color: #641;
}

.form {
    border-radius: 4px;
    padding: 6px;
    margin: 5px;
    display: inline-block;
    background-color: #EEC;
}
.form input {
    width: 20em;
}
</style>
</html><body onLoad="init()"><%

Shepherd myShepherd = new Shepherd("context0");
myShepherd.beginDBTransaction();

ConfigurationUtil.init();   //not required, but clears cache for us (for testing)

String id = request.getParameter("id");

Map<String,String[]> pmap = request.getParameterMap();
if (pmap.size() > 0) for (String pkey : pmap.keySet()) {
    if (!ConfigurationUtil.idHasValidRoot(pkey)) continue;
    if ((pmap.get(pkey) == null) || (pmap.get(pkey).length < 1)) continue;
    Configuration confSet = null;
    try {
        if (pmap.get(pkey).length == 1) {
            confSet = ConfigurationUtil.setConfigurationValue(myShepherd, pkey, pmap.get(pkey)[0]);
        } else {
            //this needs to correctly handle List for (only) multi  TODO
            confSet = ConfigurationUtil.setConfigurationValue(myShepherd, pkey, Arrays.asList(pmap.get(pkey)));
        }
        out.println("<div class=\"set\">set <b><a href=\"configTest.jsp?id=" + pkey + "\">" + pkey + "</a></b></div>");
    } catch (ConfigurationException ex) {
        out.println("<div class=\"set warn\">could not set <b>" + pkey + "</b>: " + ex.toString() + "</div>");
    }
}

String rmId = request.getParameter("rmId");
if (rmId != null) {
    Object rm = ConfigurationUtil.removeConfiguration(myShepherd, rmId);
    out.println("<div class=\"set\">[" + rmId + "] <i>remove=</i>" + ((rm == null) ? "<b>failed</b>" : rm.toString()) + "</div>");
}



if (id == null) {
    Map<String,JSONObject> meta = ConfigurationUtil.getMeta();
    out.println("<ul>");
    for (String k : meta.keySet()) {
        out.println("<li><a href=\"configTest.jsp?id=" + k + "\">" + k + "</a></li>");
    }
    out.println("</ul>");
    myShepherd.commitDBTransaction();
    return;
}

out.println("<h1 id=\"h1id\">" + id + "</h1>");
String root = null;
List<String> path = ConfigurationUtil.idPath(id);
if (path.size() > 1) {
    root = path.get(0);
    path.remove(path.size() - 1);
    String up = String.join(".", path);
    out.println("<p><i>Up to <a href=\"configTest.jsp?id=" + up + "\">" + up + "</a></i></p>");
} else {
    out.println("<p><i>Up to <a href=\"configTest.jsp\">[TOP]</a></i></p>");
}


Configuration conf = ConfigurationUtil.getConfiguration(myShepherd, id);
if (conf == null) {
    out.println("<p>unknown id <b>" + id + "</b></p>");
    myShepherd.commitDBTransaction();
    return;
}

out.println("<ul>");
for (String k : conf.getChildKeys()) {
    out.println("<li><a href=\"configTest.jsp?id=" + id + "." + k + "\">" + id + "." + k + "</a></li>");
}
out.println("</ul><hr />");


if (conf.hasValue()) {
    out.println("<div class=\"value\">");
    if (conf.isMultiple()) {
        out.println("<ul>");
        try {
            List<String> vals = conf.getValueAsStringList();
            for (String val : vals) {
                out.println("<li>" + val + "</li>");
            }
        } catch (Exception ex) {}
        out.println("</ul>");
    } else {
        try {
            String val = conf.getValueAsString();
            out.println(val);
        } catch (Exception ex) {}
    }
    out.println("</div>");
} else {
    out.println("<div class=\"value warn\"><i>no value set</i></div>");
}

//JSONObject meta = ConfigurationUtil.getMeta(id);
out.println("<p>" + conf + "</p>");
if (conf.getMeta() != null) out.println("<p>our <b>meta</b>:</p><pre>" + conf.getMeta().toString(8) + "</pre>");

out.println("<p>for <b>front end</b>:</p><pre>" + conf.toFrontEndJSONObject(myShepherd).toString(8) + "</pre>");

out.println("<p><b>.toJSONObject()</b>:</p><pre>" + conf.toJSONObject().toString(8) + "</pre>");

if (conf.getContent() != null) out.println("<p><b>content</b>:</p><pre>" + conf.getContent().toString(8) + "</pre>");


out.println("<p>&nbsp;</p>");

if (root != null) {
    Configuration top = ConfigurationUtil.getConfiguration(myShepherd, root);
    out.println("<pre class=\"value\">" + top.getContent().toString(8) + "</pre>");
}

/*
//out.println("<p>" + ConfigurationUtil.getConfiguration(myShepherd, "cache.bar") + "</p>");

//out.println(ConfigurationUtil.getConfiguration(myShepherd, "cache.bar").toJSONObject());


out.println(ConfigurationUtil.setConfigurationValue(myShepherd, "test.fu.barr", 2170));


//out.println(ConfigurationUtil.getConfiguration(myShepherd, "cache.bar").toJSONObject());

//out.println(  ConfigurationUtil.getConfigurationValue(myShepherd, "cache.bar")  );



JSONObject jobj = new JSONObject();
List<String> path = ConfigurationUtil.idPath("test.foo");
out.println(ConfigurationUtil.setDeepJSONObject(jobj, path, 2170));

*/



%>

<script>

var meta = <%=conf.toFrontEndJSONObject(myShepherd)%>;
var isValid = <%=conf.isValid()%>;
function init() {
    if (!isValid) return;
    $('#h1id').after('<div><input type="button" value="set value" onClick="return buildUI(this);" /></div>');
}

function buildUI(b) {
    $(b).hide();
    var h = '<form class="form">';

    if (!meta.multiple) {
        var val = '';
        if (meta.currentValue != undefined) {
            val = meta.currentValue;
        } else {
            val = decodeDefaultSingle();
        }
        if (meta.values) {
            h += pulldown(val);
        } else {
            h += '<input class="inp" name="' + meta.configurationId + '" value="' + val + '" />';
        }

    } else {  //multiple
        var vals = meta.currentValue;
        if (vals == undefined) vals = decodeDefaultMulti();
        if (vals == undefined) {
            vals = [];
        } else if (!Array.isArray(vals)) {
            vals = [ vals ];
        }
console.log('vals=%o', vals);
        if (meta.values) {
            h += multi(vals);
        } else {
            h += '<input title="THIS SHOULD ALLOW MULTIPLE!" class="inp" name="' + meta.configurationId + '" value="" />';
        }
    }

    h += '<br /><input type="button" value="update" onClick="return set()" />';
    h += '</form>';
    $(b).after(h);
}

function set() {
    var parts = [];
    $('.inp').each(function(i, el) {
        var jel = $(el);
        var name = jel.prop('name');
        var val = jel.val();
        if (val == undefined) return;
        if (!Array.isArray(val)) val = [ val ];
console.log('%d: %s => %o', i, name, val);
        if (!val.length) return;
        for (var i = 0 ; i < 1 ; i++) {  //FIXME we dont support val.length yet, only 1 !!!
            parts.push(name + '=' + val[i]);
        }
    });
    if (!parts.length) return;
    $('.form').hide();
    window.location.href = 'configTest.jsp?' + parts.join('&');
}

function pulldown(sel) {
    var h = '<select class="inp" name="' + meta.configurationId + '">';
    if (!meta.required) h += '<option value="">SELECT</option>';
    for (var i = 0 ; i < meta.values.length ; i++) {
        var v = meta.values[i];
        var l = v;
        if (meta.valueLabels && meta.valueLabels[v]) l = meta.valueLabels[v] + ' [' + v + ']';
        h += '<option value="' + v + '"' + ((v == sel) ? ' selected' : '') + '>' + l + '</option>';
    }
    h += '</select>';
    return h;
}

function multi(vals) {
    var sz = meta.values.length + 1;
    if (sz > 8) sz = 8;
    var h = '<select multiple size="' + sz + '" class="inp" name="' + meta.configurationId + '">';
    for (var i = 0 ; i < meta.values.length ; i++) {
        var v = meta.values[i];
        var l = v;
        if (meta.valueLabels && meta.valueLabels[v]) l = meta.valueLabels[v] + ' [' + v + ']';
        h += '<option value="' + v + '"' + ((vals.includes(v)) ? ' selected' : '') + '>' + l + '</option>';
    }
    h += '</select>';
    return h;
}

function decodeDefaultSingle() {
    var m = decodeDefaultMacro();
    if (m != undefined) return m;
    return meta.defaultValue;
}
function decodeDefaultMulti() {
    var m = decodeDefaultMacro();
    if (m != undefined) return m;
    return meta.defaultValue;
}

function decodeDefaultMacro() {
    if (typeof meta.defaultValue != 'object') return;
    if (!meta.defaultValue.macro) return;
    if (meta.defaultValue.macro == 'now') return new Date().toISOString();
    return;
}

</script>
</body></html>

<%
myShepherd.commitDBTransaction();
%>
