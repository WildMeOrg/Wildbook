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
    <link rel="stylesheet" href="m.css" />
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
Note: in order to best support <b>time zones</b>, you should have added <i class="code">timeZone</i> values to <b>LocationID.json</b> data,
otherwise all times will be converted using a universal time zone.
</p>
<p>
Default time zone is <b><%=tz%></b> (can be overridden with <i class="code">?timeZone=xxx</i> in url).
</p>
<hr />
<%

String jdoql = "SELECT FROM org.ecocean.Encounter WHERE time == null ORDER BY id";
if (!commit) jdoql += " RANGE 0,25";


Query query = myShepherd.getPM().newQuery(jdoql);
Collection c = (Collection) (query.execute());
List<Encounter> all = new ArrayList<Encounter>(c);
query.closeAll();

out.println("<ul>");
int ct = 0;
for (Encounter enc : all) {
    ct++;
    ComplexDateTime cdt = enc.deriveComplexDateTime(tz);
    if (cdt == null) continue;
    out.println("<li>" + enc.getCatalogNumber() + " " + enc.getDate() + " => <b>" + cdt + "</b></li>");
    if (commit) {
        enc.setTime(cdt);
        System.out.println("datetime.jsp: [" + ct + "/" + all.size() + "] migrated " + cdt + " on " + enc);
    }
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

myShepherd.commitDBTransaction();  //persists all encounter data


myShepherd.beginDBTransaction();

jdoql = "SELECT FROM org.ecocean.Occurrence WHERE encounters.size() > 0 ORDER BY id";
//jdoql += " RANGE 0,15";  //debug only


query = myShepherd.getPM().newQuery(jdoql);
c = (Collection) (query.execute());
List<Occurrence> occs = new ArrayList<Occurrence>(c);
query.closeAll();

out.println("<ul>");
ct = 0;
for (Occurrence occ : occs) {
    ct++;
    boolean changed = occ.setTimesFromEncounters();  //no override so wont touch existing values, which would be weird to exist, but...
    if (!changed) continue;  //nothing to report really
    ComplexDateTime st = occ.getStartTime();
    ComplexDateTime et = occ.getEndTime();
    int encNum = Util.collectionSize(occ.getEncounters());
    out.println("<li>" + occ.getId() + " (" + encNum + " encs) => <b>" + st + "</b> | <b>" + et + "</b> (dur " + (et.gmtLong() - st.gmtLong()) + ")</li>");
    System.out.println("datetime.jsp: [" + ct + "/" + occs.size() + "] migrated " + st + "/" + et + " on " + occ);
}


myShepherd.commitDBTransaction();  //persists all occurrence data

myShepherd.closeDBTransaction();
%>


<p>done.</p>
</body></html>
