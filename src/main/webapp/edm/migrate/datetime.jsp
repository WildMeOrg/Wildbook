<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Collection,
java.util.List,
java.util.ArrayList,
javax.jdo.*,
java.util.Arrays,
org.json.JSONObject,
java.lang.reflect.*,

org.ecocean.api.ApiCustomFields,
org.ecocean.customfield.*,

org.ecocean.media.*
              "
%><html>
<head>
    <title>Codex Date/Time Migration Helper</title>
</head>
<body>
<p>
This will help migrate to Codex ComplexDateTime values.
</p>




<%

boolean commit = Util.requestParameterSet(request.getParameter("commit"));
String tz = request.getParameter("timeZone");
if (tz == null) tz = "Z";

Shepherd myShepherd = new Shepherd("context0");
myShepherd.beginDBTransaction();

%>
<p>
Note: in order to best support <b>time zones</b>, you should have added <i>timeZone</i> values to <b>LocationID.json</b> data,
otherwise all times will be converted using a universal time zone.
</p>
<p>
Default time zone is <b><%=tz%></b> (can be overridden with <i>?timeZone=xxx</i> in url).
</p>
<hr />
<%

String jdoql = "SELECT FROM org.ecocean.Encounter";
if (!commit) jdoql += " RANGE 0,25";

Query query = myShepherd.getPM().newQuery(jdoql);
Collection c = (Collection) (query.execute());
List<Encounter> all = new ArrayList<Encounter>(c);
query.closeAll();

out.println("<ul>");
for (Encounter enc : all) {
    ComplexDateTime cdt = enc.deriveComplexDateTime(tz);
    if (cdt == null) continue;
    out.println("<li>" + enc.getCatalogNumber() + " " + enc.getDate() + " => <b>" + cdt + "</b></li>");
}
out.println("</ul>");

if (!commit) {
    myShepherd.rollbackDBTransaction();
%>
<hr /><p><b>commit=false</b>, not modifying anything</p>
<p><a href="?commit=true">Migrate DateTime values for Encounters and Occurrences</a></p>
<%
    return;
}

//myShepherd.commitDBTransaction();

%>


</body></html>
