<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Collection,
java.util.List,
java.util.ArrayList,
java.util.Set,
java.util.HashSet,
javax.jdo.*,
java.util.Arrays,
org.json.JSONObject,
org.ecocean.MigrationUtil,
org.json.JSONArray,
org.json.JSONException,
java.lang.reflect.*,
java.io.File,

org.ecocean.api.ApiCustomFields,
org.ecocean.customfield.*,
org.ecocean.configuration.*,
org.ecocean.servlet.ServletUtilities,

org.ecocean.media.*
              "
%><%!

public static List<String> getIds(JSONObject json) {
    List<String> got = new ArrayList<String>();
    String id = json.optString("id", null);
    if (id != null) got.add(id);
    JSONArray sub = json.optJSONArray("locationID");
    if (sub != null) {
        for (int i = 0 ; i < sub.length() ; i++) {
            JSONObject next = sub.optJSONObject(i);
            if (next == null) continue;
            got.addAll(getIds(next));
        }
    }
    return got;
}

public void addSuggestions(JSONObject json) {
    List<String> sugg = new ArrayList(Arrays.asList("prefix", "prefixDigitPadding", "geospatialInfo", "timeZone", "description"));
    if (json.optString("id", null) != null) {
        for (String s : sugg) {
            if (!json.has(s)) json.put(s, JSONObject.NULL);
        }
    }
    JSONArray sub = json.optJSONArray("locationID");
    if (sub != null) {
        for (int i = 0 ; i < sub.length() ; i++) {
            if (sub.optJSONObject(i) != null) {
                addSuggestions(sub.getJSONObject(i));
            }
        }
    }
}

%><html>
<head>
    <title>Codex LocationID Migration Helper</title>
    <link rel="stylesheet" href="m.css" />
<%
String context = "context0";
Shepherd myShepherd = new Shepherd(context);
myShepherd.beginDBTransaction();

String configKey = "site.custom.regions";
Configuration currentConf = ConfigurationUtil.getConfiguration(myShepherd, configKey);
%>
</head>
<body>
<p>
This will help migrate to Codex the values of LocationID.json
</p>
<p>
This data will migrate old-world data to the new houston Configuration value
for <b>"regions"</b> (<i class="code"><%=configKey%></i>).
</p>

<%

String json_original = request.getParameter("json_original");
String json_suggested = request.getParameter("json_suggested");
String jsonSave = null;
String suggestedOverride = null;

if (json_original != null) {
    jsonSave = json_original;
} else if (json_suggested != null) {
    jsonSave = json_suggested;
}


if (jsonSave != null) {
    MigrationUtil.writeFile("locationId_saved.json", jsonSave);
    String error = null;
    JSONObject json = null;
    try {
        json = new JSONObject(jsonSave);
    } catch (JSONException je) {
        error = "Invalid JSON format: " + je.toString();
    }

    if (error == null) {
        Configuration conf = ConfigurationUtil.setConfigurationValue(myShepherd, configKey, json);
        conf.resetRootCache();
%>
<p>
    <h2>Saved!</h2>
    Value should now appear at <a target="_new" href="../../api/v0/configuration/<%=configKey%>">configuration <b><%=configKey%></b></a>.
    <xmp class="muted"><%=conf%></xmp>
</p>
<%
        myShepherd.commitDBTransaction();
        out.println("</body></html>");
        return;
    } else {
        suggestedOverride = jsonSave;
%>
<h2 class="error"><%=error%></h2>
<p>Problematic value now shown in <b>suggested</b> block below.</p>
<%
    }
}


if ((currentConf != null) && currentConf.hasValue()) {
%>
<p style="padding: 1em; background-color: #FFC; display: inline-block;">
    <b class="error">Warning:</b>
    It appears you already have a <b>value set</b> for Configuration
    <a target="_new" href="../../api/v0/configuration/<%=configKey%>"><%=configKey%></a>.
    Anything saved here will overwrite this value.
</p>
<%
}

MigrationUtil.setDir(request.getParameter("migrationDir"));
String checkDir = MigrationUtil.checkDir();
%>
<p>
migrationDir: <b><%=checkDir%></b>
</p>
<%

boolean commit = Util.requestParameterSet(request.getParameter("commit"));
//String tz = request.getParameter("timeZone");
//if (tz == null) tz = "Z";

String rootDir = getServletContext().getRealPath("/");
String dataDir = ServletUtilities.dataDir(context, rootDir);
File locFile = new File(dataDir, "WEB-INF/classes/bundles/locationID.json");


String origRawJson = Util.readFromFile(locFile.toString());
JSONObject origJson = new JSONObject(origRawJson);


%>
<h1>original</h1>

<form method="POST" action="locationID.jsp">
<p><input value="Use orignal (can edit!)" type="submit" /></p>
<textarea name="json_original" style="height: 200em !important;">
<%=origRawJson%>
</textarea>
</form>

<%

List<String> ids = getIds(origJson);
//String sql = "SELECT DISTINCT(\"LOCATIONID\") FROM \"ENCOUNTER\" WHERE \"LOCATIONID\" NOT IN ('" + String.join("', '", ids) + "')";
String sql = "SELECT DISTINCT(\"LOCATIONID\") FROM \"ENCOUNTER\"";
Query query = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
Collection c = (Collection)query.execute();
Set<String> missing = new HashSet<String>();
for (Object o : c) {
    String s = (String)o;
    if (ids.contains(s)) continue;
    if (Util.stringExists(s)) missing.add(s);
}
query.closeAll();

JSONObject suggested = new JSONObject(origRawJson);

if (missing.size() > 0) {
%><p><b>Missing locationIDs found in Encounters:</b> <%=String.join(", ", missing)%>.  Added to suggested JSON below under id=<i class="code">_unknown_</i>.</p><%
    JSONObject unknown = new JSONObject();
    unknown.put("id", "_unknown_");
    unknown.put("name", "Unknown / Unsorted");
    JSONArray arr = new JSONArray();
    for (String m : missing) {
        JSONObject mj = new JSONObject();
        mj.put("id", m);
        mj.put("name", m);
        arr.put(mj);
    }
    unknown.put("locationID", arr);
    suggested.getJSONArray("locationID").put(unknown);
    addSuggestions(suggested);
}


String suggestedString = Util.niceJSON(suggested);
if (suggestedOverride != null) suggestedString = suggestedOverride;
%>

<hr />
<h1>suggested</h1>

<form method="POST" action="locationID.jsp">
<p><input value="Use suggested (can edit!)" type="submit" /></p>
<textarea name="json_suggested" style="height: 200em !important;">
<%=suggestedString%>
</textarea>
</form>
<%
myShepherd.rollbackDBTransaction();

MigrationUtil.writeFile("locationId_suggested.json", suggestedString);
MigrationUtil.writeFile("locationId_original.json", origRawJson);
%>


</body></html>
