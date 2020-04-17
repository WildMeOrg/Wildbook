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
.warn {
    color: #641;
}
</style>
</html><body><%

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
        out.println("<div class=\"set\">set <b>" + pkey + "</b></div>");
    } catch (ConfigurationException ex) {
        out.println("<div class=\"set warn\">could not set <b>" + pkey + "</b>: " + ex.toString() + "</div>");
    }
}

if (id == null) {
    Map<String,JSONObject> meta = ConfigurationUtil.getMeta();
    out.println("<ul>");
    for (String k : meta.keySet()) {
        out.println("<li><a href=\"configTest.jsp?id=" + k + "\">" + k + "</a></li>");
    }
    out.println("</ul>");
    return;
}

out.println("<h1>" + id + "</h1>");
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
    return;
}

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

out.println("<p><b>content</b>:</p><pre>" + conf.getContent().toString(8) + "</pre>");


String rmId = request.getParameter("rmId");
if (rmId != null) out.println("<p>[" + rmId + "] <i>remove test=</i>" + Boolean.toString(ConfigurationUtil.removeConfiguration(myShepherd, rmId)) + "</p>");


out.println("<ul>");
for (String k : conf.getChildKeys()) {
    out.println("<li><a href=\"configTest.jsp?id=" + id + "." + k + "\">" + id + "." + k + "</a></li>");
}
out.println("</ul>");


if (root != null) {
    Configuration top = ConfigurationUtil.getConfiguration(myShepherd, root);
    out.println("<div class=\"value\">" + top.getContent().toString(8) + "</div>");
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

myShepherd.commitDBTransaction();

%>
</body></html>

