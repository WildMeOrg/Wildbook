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
java.lang.reflect.*,
org.ecocean.Util.MeasurementDesc,
org.ecocean.api.ApiCustomFields,
org.ecocean.datacollection.Instant,
org.ecocean.customfield.*,
org.joda.time.DateTime,

org.ecocean.media.*
              "
%><%!

private String cleanup(String in) {
    if (in == null) return null;
    in = in.replaceAll("\\s+", " ").trim();
    if (in.equals("")) return null;
    return in;
}

private List<String> setSort(Set<String> in) {
    List<String> sort = new ArrayList<String>(in);
    Collections.sort(sort, String.CASE_INSENSITIVE_ORDER);
    return sort;
}

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
Occurrence occ = myShepherd.getOccurrence("163d2b79-09b1-4cd7-b4e5-db8e8525c36c");
List<Instant> bs = new ArrayList<Instant>();
Instant ins = new Instant("fubarr", new DateTime(), null);
bs.add(ins);
occ.setBehaviors(bs);
out.println(occ);
out.println(occ.getBehaviors());
myShepherd.commitDBTransaction();
myShepherd.closeDBTransaction();
if (occ != null) return;
*/

Map<String,String> encMap = new HashMap<String,String>();
String sql = "SELECT DISTINCT(TRIM(\"BEHAVIOR\")) FROM \"ENCOUNTER\" WHERE \"BEHAVIOR\" IS NOT NULL;";
Query query = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
Collection c = (Collection)query.execute();
for (Object o : c) {
    String b = cleanup((String)o);
    if (b != null) encMap.put(b, Util.generateUUID());
}
query.closeAll();

Map<String,String> occMap = new HashMap<String,String>();
sql = "SELECT DISTINCT(TRIM(\"GROUPBEHAVIOR\")) FROM \"OCCURRENCE\" WHERE \"GROUPBEHAVIOR\" IS NOT NULL;";
query = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
c = (Collection)query.execute();
for (Object o : c) {
    String b = cleanup((String)o);
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
        String b = cleanup(ins.getName());
        if (b != null) occMap.put(b, Util.generateUUID());
    }
}


%>
<form method="POST" action="behaviors.jsp">
<h2>Encounters</h2>
<input type="checkbox" name="enc-dropdown" /> Use list below as dropdown
<textarea name="enc-choices">
<%=String.join("\n", setSort(encMap.keySet()))%>
</textarea>

<h2>Sightings/Occurrences</h2>
<input type="checkbox" name="occ-dropdown" /> Use list below as dropdown
<textarea name="occ-choices">
<%=String.join("\n", setSort(occMap.keySet()))%>
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

/*
List<MeasurementDesc> descs = Util.findMeasurementDescs("en", context);
List<CustomFieldDefinition> cfds = new ArrayList<CustomFieldDefinition>();
for (MeasurementDesc desc : descs) {
    CustomFieldDefinition cfd = new CustomFieldDefinition("org.ecocean.Encounter", "double", cfdNameFromMeasurementDesc(desc));
    CustomFieldDefinition found = CustomFieldDefinition.find(myShepherd, cfd);
    if (found != null) {
        out.println("<p>collision with existing cfd: <b>" + found + "</b></p>");
        myShepherd.rollbackDBTransaction();
        return;
    }
    cfds.add(cfd);
    String show = measDesc(desc);
}
*/


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

/*
String jdoql = "SELECT FROM org.ecocean.Encounter WHERE measurements.size() > 0";
//jdoql += " RANGE 0,10";  //debugging only

out.println("<p>jdoql = <b>" + jdoql + "</b></p><hr /><h1>Migrating field values:</h1>");

Map<String, CustomFieldDefinition> cfdMap = new HashMap<String, CustomFieldDefinition>();
for (CustomFieldDefinition cfd : cfds) {
    myShepherd.getPM().makePersistent(cfd);
    cfdMap.put(cfd.getName(), cfd);
}

Query query = myShepherd.getPM().newQuery(jdoql);
Collection c = (Collection) (query.execute());
List<Encounter> all = new ArrayList<Encounter>(c);
query.closeAll();
int ect = 1;
for (Encounter enc : all) {
    out.println("<hr /><b>" + enc + "</b>");
    int ct = 0;
    for (MeasurementDesc desc : descs) {
        Measurement ms = myShepherd.getMeasurementOfTypeForEncounter(desc.getType(), enc.getCatalogNumber());
        if (ms != null) {
            String nm = cfdNameFromMeasurementDesc(desc);
            CustomFieldDefinition cfd = cfdMap.get(nm);
            if (cfd == null) {
                out.println("<p>could not find CustomFieldDefinition that matched name=" + nm + " for " + ms + "</p>");
                myShepherd.rollbackDBTransaction();
                return;
            }
            out.println("<br />" + cfd + " => " + ms.getValue());
            CustomFieldValue cfv = CustomFieldValue.makeSpecific(cfd, ms.getValue());
            enc.addCustomFieldValue(cfv);
            ct++;
        }
    }
    System.out.println("measurements.jsp: [" + ect + "/" + all.size() + "] migrated " + ct + " Measurements on " + enc);
    ect++;
}
*/


//myShepherd.commitDBTransaction();
myShepherd.rollbackDBTransaction();

%>


</body></html>
