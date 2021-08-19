<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Collection,
java.util.List,
java.util.ArrayList,
javax.jdo.*,
java.util.Arrays,
java.util.Collections,
java.util.Map,
java.util.HashMap,
java.util.Set,
java.util.HashSet,
org.json.JSONObject,
org.ecocean.MigrationUtil,
java.lang.reflect.*,
org.ecocean.Util.MeasurementDesc,
org.ecocean.api.ApiCustomFields,
org.ecocean.datacollection.Instant,
org.ecocean.customfield.*,
org.joda.time.DateTime,

org.ecocean.media.*
              "
%><%!


private String measDesc(MeasurementDesc desc) {
    String d = desc.getType();
    d += " / " + desc.getLabel();
    d += " / " + desc.getUnits();
    d += " / " + desc.getUnitsLabel();
    return d;
}

private String cfdNameFromMeasurementDesc(MeasurementDesc desc) {
    String cfdName = desc.getType();
    String units = desc.getUnits();
    if ((units != null) && !units.equals("nounits") && !units.equals("")) cfdName = cfdName + "_" + units.toLowerCase();
    return cfdName;
}

%><html>
<head>
    <title>Codex CustomField Behavior Migration Helper</title>
    <link rel="stylesheet" href="m.css" />
</head>
<body>
<p>
This will help migrate <b>Behaviors</b> to Codex CustomFields.
</p>
<hr />



<%

String context = "context0";
Shepherd myShepherd = new Shepherd(context);
myShepherd.beginDBTransaction();


boolean useEncDD = (request.getParameter("enc-dropdown") != null);
boolean useOccDD = (request.getParameter("occ-dropdown") != null);
boolean commit = (request.getParameter("commit") != null);

/*
Occurrence zocc = myShepherd.getOccurrence("163d2b79-09b1-4cd7-b4e5-db8e8525c36c");
List<Instant> bs = new ArrayList<Instant>();
Instant zins = new Instant("fuZZZZ", new DateTime(), null);
bs.add(zins);
zins = new Instant("fubarr", new DateTime(), null);
bs.add(zins);
zocc.setBehaviors(bs);
out.println(zocc);
out.println(zocc.getBehaviors());
myShepherd.commitDBTransaction();
myShepherd.closeDBTransaction();
if (zocc != null) return;
*/

Map<String,String> encMap = new HashMap<String,String>();
String sql = "SELECT DISTINCT(TRIM(\"BEHAVIOR\")) FROM \"ENCOUNTER\" WHERE \"BEHAVIOR\" IS NOT NULL;";
Query query = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
Collection c = (Collection)query.execute();
for (Object o : c) {
    String b = MigrationUtils.cleanup((String)o);
    if (b != null) encMap.put(b, Util.generateUUID());
}
query.closeAll();

Map<String,String> occMap = new HashMap<String,String>();
sql = "SELECT DISTINCT(TRIM(\"GROUPBEHAVIOR\")) FROM \"OCCURRENCE\" WHERE \"GROUPBEHAVIOR\" IS NOT NULL;";
query = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
c = (Collection)query.execute();
for (Object o : c) {
    String b = MigrationUtils.cleanup((String)o);
    if (b != null) occMap.put(b, Util.generateUUID());
}
query.closeAll();

String jdoql = "SELECT FROM org.ecocean.Occurrence WHERE behaviors.size() > 0";
query = myShepherd.getPM().newQuery(jdoql);
c = (Collection)query.execute();
List<Occurrence> bOccs = new ArrayList<Occurrence>(c);
query.closeAll();
for (Occurrence occ : bOccs) {
    for (Instant ins : occ.getBehaviors()) {
        String b = MigrationUtils.cleanup(ins.getName());
        if (b != null) occMap.put(b, Util.generateUUID());
    }
}


%>
<form method="POST" action="behaviors.jsp">
<h2>Encounters</h2>
<input type="checkbox" name="enc-dropdown" /> Use list below as dropdown
<textarea name="enc-choices">
<%=String.join("\n", MigrationUtils.setSort(encMap.keySet()))%>
</textarea>

<h2>Sightings/Occurrences</h2>
<input type="checkbox" name="occ-dropdown" /> Use list below as dropdown
<textarea name="occ-choices">
<%=String.join("\n", MigrationUtils.setSort(occMap.keySet()))%>
</textarea>

<%

CustomFieldDefinition cfdEnc = new CustomFieldDefinition("org.ecocean.Encounter", "string", "behaviors", true);
CustomFieldDefinition found = CustomFieldDefinition.find(myShepherd, cfdEnc);
if (found != null) {
    out.println("<p>Encounter has collision with existing cfd: <b>" + found + "</b></p>");
    myShepherd.rollbackDBTransaction();
    return;
}

CustomFieldDefinition cfdOcc = new CustomFieldDefinition("org.ecocean.Occurrence", "string", "behaviors", true);
found = CustomFieldDefinition.find(myShepherd, cfdOcc);
if (found != null) {
    out.println("<p>Occurrence has collision with existing cfd: <b>" + found + "</b></p>");
    myShepherd.rollbackDBTransaction();
    return;
}

%>
<p>Encounter: <%=cfdEnc%></p>
<p>Occurrence: <%=cfdOcc%></p>
<hr />
<%

if (!commit) {
    myShepherd.rollbackDBTransaction();
%>
<hr /><p><b>commit=false</b>, not modifying anything</p>
<p><input type="submit" name="commit" value="Create CustomFields for behavior and migrate data" /></p>
</form>
<%
    return;
}

out.println("COMMIT");

myShepherd.getPM().makePersistent(cfdEnc);
myShepherd.getPM().makePersistent(cfdOcc);

jdoql = "SELECT FROM org.ecocean.Encounter WHERE behavior != null";
//jdoql += " RANGE 0,10";  //debugging only
out.println("<p>jdoql = <b>" + jdoql + "</b></p><hr /><h1>Migrating field values on Encounters:</h1>");
query = myShepherd.getPM().newQuery(jdoql);
c = (Collection) (query.execute());
List<Encounter> encAll = new ArrayList<Encounter>(c);
query.closeAll();
int ct = 1;
for (Encounter enc : encAll) {
    String value = MigrationUtils.cleanup(enc.getBehavior()); //TODO should this be the uuid of this string instead for choices?
    if (value == null) continue;
    CustomFieldValue cfv = CustomFieldValue.makeSpecific(cfdEnc, value);
    enc.addCustomFieldValue(cfv);
    out.println(enc + ": <b>" + value + "</b><br />");
    System.out.println("behaviors.jsp: [" + ct + "/" + encAll.size() + "] migrated behaviors on " + enc);
    ct++;
}

out.println("<hr />");

jdoql = "SELECT FROM org.ecocean.Occurrence WHERE groupBehavior != null";
//jdoql += " RANGE 0,10";  //debugging only
out.println("<p>jdoql = <b>" + jdoql + "</b></p><hr /><h1>Migrating field values on Occurrences:</h1>");
query = myShepherd.getPM().newQuery(jdoql);
c = (Collection) (query.execute());
List<Occurrence> occAll = new ArrayList<Occurrence>(c);
query.closeAll();
ct = 1;
for (Occurrence occ : occAll) {
    String value = MigrationUtils.cleanup(occ.getGroupBehavior()); //TODO should this be the uuid of this string instead for choices?
    if (value == null) continue;
    CustomFieldValue cfv = CustomFieldValue.makeSpecific(cfdOcc, value);
    occ.addCustomFieldValue(cfv);
    out.println(occ + ": <b>" + value + "</b><br />");
    System.out.println("behaviors.jsp: [" + ct + "/" + occAll.size() + "] migrated behaviors on " + occ);
    ct++;
}
out.println("<hr />");
jdoql = "SELECT FROM org.ecocean.Occurrence WHERE behaviors.size() > 0";
query = myShepherd.getPM().newQuery(jdoql);
c = (Collection) (query.execute());
occAll = new ArrayList<Occurrence>(c);
query.closeAll();
ct = 1;
for (Occurrence occ : occAll) {
    for (Instant ins : occ.getBehaviors()) {
        String value = MigrationUtils.cleanup(ins.getName()); //TODO should this be the uuid of this string instead for choices?
        if (value == null) continue;
        CustomFieldValue cfv = CustomFieldValue.makeSpecific(cfdOcc, value);
        occ.addCustomFieldValue(cfv);
        out.println(occ + ": <b>" + value + "</b><br />");
    }
    System.out.println("behaviors.jsp: [" + ct + "/" + occAll.size() + "] migrated (Instant) behaviors on " + occ);
    ct++;
}


myShepherd.commitDBTransaction();

%>


</body></html>
