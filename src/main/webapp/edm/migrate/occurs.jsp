<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Collection,
java.util.List,
java.nio.file.Path,
java.util.ArrayList,
javax.jdo.*,
java.util.Arrays,
java.util.Map,
java.util.HashMap,
org.json.JSONObject,
java.lang.reflect.*,
org.ecocean.Util.MeasurementDesc,
org.ecocean.MigrationUtil,
org.ecocean.api.ApiCustomFields,
org.ecocean.customfield.*,

org.ecocean.media.*
              "
%><%!

public static String showOcc(Occurrence occ) {
    String s = occ.getId() + " [";
    s += occ.getLocationId() + ", ";
    s += occ.getStartTime() + ", ";
    s += occ.getEndTime();
    return s + "]";
}

%><html>
<head>
    <title>Codex Occurrence/Sighting Migration</title>
    <link rel="stylesheet" href="m.css" />
</head>
<body>
<p>
This will build out Occurrences (Sightings) for Encounters which do not have any.
</p>
<hr />


<%


String sql = "SELECT \"ID\" FROM \"ENCOUNTER\" a LEFT JOIN \"OCCURRENCE_ENCOUNTERS\" b ON a.\"ID\" = b.\"ID_EID\" WHERE b.\"ID_EID\" IS NULL;";

String context = "context0";
Shepherd myShepherd = new Shepherd(context);
boolean commit = Util.requestParameterSet(request.getParameter("commit"));
myShepherd.beginDBTransaction();

Query query = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
Collection c = (Collection)query.execute();
List<String> all = new ArrayList<String>();
for (Object o : c) {
    all.add((String)o);
}
query.closeAll();

if (!commit) {
    out.println("<p><b>" + all.size() + " Encounters</b> without an Occurrence/Sighting</p>");
    myShepherd.rollbackDBTransaction();
%>
<hr /><p><b>commit=false</b>, not modifying anything</p>
<p><a href="?commit=true">Create Occurrence/Sightings</a></p>
<%
    return;
}

int ct = 1;
for (String encId : all) {
    Encounter enc = myShepherd.getEncounter(encId);
    ct++;
    if (ct % 200 == 0) {
        System.out.println("occurs.jsp COMMIT");
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
    }
    if (enc == null) continue;  //snh?
    Occurrence existOcc = myShepherd.getOccurrenceForEncounter(enc.getId());
    if (existOcc != null) {
        out.println("<p class=\"muted\">" + enc + " already has " + existOcc + "</p>");
        continue;
    }
    String occId = Util.generateUUID();
    Occurrence occ = new Occurrence(occId, enc);
    enc.setOccurrenceID(occId);

    List<Encounter> sibs = enc.findSiblingsFromMediaAssets(myShepherd);
    if (sibs.size() > 0) for (Encounter sib : sibs) {
        occ.addEncounterAndUpdateIt(sib);
    }
    occ.setLocationIdFromEncounters();
    ////occ.setTimesFromEncounters();  // do not need to do now -- datetime.jsp will base off of encounters
    myShepherd.getPM().makePersistent(occ);

    out.println("<p>" + enc.getId() + ": <b>" + showOcc(occ) + "</b>" + ((sibs.size() == 0) ? "" : " (+ " + sibs.size() + " sib encs)") + "</p>");
    System.out.println("occurs.jsp [" + ct + "/" + all.size()+ "] created: " + occ + " via " + enc);
}

myShepherd.commitDBTransaction();

%>

</body></html>
