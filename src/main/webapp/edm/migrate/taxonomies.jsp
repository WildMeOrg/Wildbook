<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Collection,
java.util.List,
java.nio.file.Path,
java.util.ArrayList,
javax.jdo.*,
java.util.Iterator,
java.util.Arrays,
java.util.Map,
java.util.HashMap,
org.json.JSONObject,
org.json.JSONArray,
java.lang.reflect.*,
org.ecocean.Util.MeasurementDesc,
org.ecocean.MigrationUtil,
org.ecocean.api.ApiCustomFields,
org.ecocean.customfield.*,
org.ecocean.configuration.*,

org.ecocean.media.*
              "
%><%!


%><html>
<head>
    <title>Codex Taxonomy Migration</title>
    <link rel="stylesheet" href="m.css" />
</head>
<body>
<p>
This will help migrate Taxonomy data.
</p>
<hr />


<%


String sql = "SELECT DISTINCT(CONCAT(\"GENUS\", ' ', \"SPECIFICEPITHET\")) FROM \"ENCOUNTER\" WHERE \"GENUS\" IS NOT NULL AND \"SPECIFICEPITHET\" IS NOT NULL";

String configJson = request.getParameter("configJson");
String context = "context0";
Shepherd myShepherd = new Shepherd(context);
boolean commit = Util.requestParameterSet(request.getParameter("commit"));
myShepherd.beginDBTransaction();

List<String> all = new ArrayList<String>();
Query query = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
List results = (List)query.execute();
Iterator it = results.iterator();
while (it.hasNext()) {
    String t = (String)it.next();
    all.add(t);
}
query.closeAll();


Map<String,Taxonomy> txMap = new HashMap<String,Taxonomy>();
JSONArray newTarr = new JSONArray();
for (String txStr : all) {
    Taxonomy tx = myShepherd.getOrCreateTaxonomy(txStr);
    txMap.put(txStr, tx);
    out.println("<p>" + txStr + " => <b>" + tx + "</b></p>");
    newTarr.put(tx.asApiJSONObject());
}

String jdoql = "SELECT FROM org.ecocean.Encounter WHERE taxonomy == null ORDER BY id";
query = myShepherd.getPM().newQuery(jdoql);
Collection c = (Collection)query.execute();
List<Encounter> allEnc = new ArrayList<Encounter>(c);
query.closeAll();

out.println("<hr />");
if (!commit) {
    out.println("<p><b>" + all.size() + " Taxonomies</b> in use by Encounters</p><p><b>" + allEnc.size() + " Encounters</b> needing .taxonomy</p><hr />");
    myShepherd.commitDBTransaction();

    String configKey = "site.species";
    Configuration currentConf = null;
    JSONArray tarr = null;
    if (configJson == null) {
        currentConf = ConfigurationUtil.getConfiguration(myShepherd, configKey);
        if ((currentConf != null) && currentConf.hasValue()) {
            out.println("<p class=\"important\"><b>" + configKey + "</b> has already been set, this will alter it</p>");
            tarr = currentConf.getValueAsJSONArray();
        } else {
            tarr = newTarr;
        }

    } else {  //try to save
        JSONArray jarr = Util.stringToJSONArray(configJson);
        if (jarr == null) {
            out.println("<h3 class=\"error\">could not parse form value</h3>");
            tarr = new JSONArray();
            tarr.put("Error parsing previous form data");
        } else {
            currentConf = ConfigurationUtil.setConfigurationValue(myShepherd, configKey, jarr);
            currentConf.resetRootCache();
            out.println("<h3 class=\"important\">Updated and saved " + configKey + " value</h3>");
            tarr = jarr;
        }
    }
%>
<h2>Proposed <%=configKey%> site settings:</h2>
<form method="POST">
<textarea name="configJson" style="height: 30em;">
<%=tarr.toString(4)%>
</textarea>

<p>
<input type="submit" value="Use this for <%=configKey%>" />
<br />See current <a target="_new" href="../../api/v0/configuration/<%=configKey%>">configuration <b><%=configKey%></b></a>.
</p>
</form>

<hr /><p><b>commit=false</b>, not modifying anything</p>
<p><a href="?commit=true">Proccess <b><%=allEnc.size()%> Encounters</b> needing Taxonomies</a></p>
<%
    return;
}

int ct = 0;
for (Encounter enc : allEnc) {
    ct++;
    Taxonomy tx = txMap.get(enc.getTaxonomyString());
    if (tx == null) {
        out.println("<p>" + enc.getId() + ": <b>invalid taxonomyString [" + enc.getTaxonomyString() + "]</b>; skipping</p>");
        System.out.println("taxonomies.jsp: got invalid taxonomyString ["+ enc.getTaxonomyString() + "] on " + enc);
        continue;
    }
    enc.setTaxonomy(tx);
    if (ct % 100 == 0) System.out.println("taxonomies.jsp [" + ct + "/" + allEnc.size()+ "] updated enc taxonomies");
}

myShepherd.commitDBTransaction();


%>

<h1>DONE</h1>
</body></html>
