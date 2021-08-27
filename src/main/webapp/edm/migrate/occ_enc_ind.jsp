<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Collection,
java.util.List,
java.util.Iterator,
java.nio.file.Path,
java.util.ArrayList,
java.io.File,
javax.jdo.*,
java.util.Arrays,
java.util.Map,
java.util.HashMap,
java.util.Set,
java.util.UUID,
java.util.HashSet,
org.json.JSONObject,
org.json.JSONArray,
java.sql.Connection,
java.sql.PreparedStatement,
java.sql.DriverManager,
java.lang.reflect.*,
org.ecocean.MigrationUtil,
org.ecocean.Util.MeasurementDesc,
org.ecocean.api.ApiCustomFields,
org.ecocean.customfield.*,
org.ecocean.servlet.ServletUtilities,

org.ecocean.media.*
              "
%><%!

private String fileWriteAndPreview(String name, String content) throws java.io.IOException {
    String fname = "occencind_" + name;
    File loc = MigrationUtil.writeFile(fname, content);
    String rtn = content;
    if (rtn.length() > 3000) rtn = rtn.substring(0, 3000) + "\n\n   [... preview truncated ...]";
    return "<div>This file located at: <i class=\"code\">" + loc.toString() + "</i><br /><textarea class=\"preview\">" + rtn + "</textarea></div>";
}

private String coerceOwnerId(Encounter enc, Shepherd myShepherd) {
    String sub = enc.getSubmitterID();
    User user = myShepherd.getUser(sub);
    if (user != null) return user.getUUID();
    //System.out.println("assets.jsp: could not find User for " + occ);
    return "00000000-0000-0000-0000-000000000002";
}

//ideally there would only be one (total) for any Asset, but this gets "the first one" it finds
private Occurrence getSomeOccurrence(Shepherd myShepherd, MediaAsset ma) {
    Encounter enc = null;
    for (Annotation ann : ma.getAnnotations()) {
        enc = ann.findEncounter(myShepherd);
        if (enc != null) break;
    }
    if (enc == null) {
        System.out.println("migration/assets.jsp could not find any Encounter for " + ma);
        return null;
    }
    Occurrence occ = myShepherd.getOccurrence(enc);
    if (occ == null) {
        System.out.println("migration/assets.jsp could not find any Occurrence for " + enc + " (via " + ma + ")");
        return null;
    }
    return occ;
}


//*every* annot gets all asset keywords -- long live next-gen
private String encSql(Encounter enc, Shepherd myShepherd) {
    String sqlIns = "INSERT INTO encounter (created, updated, viewed, guid, version, owner_guid, public) VALUES (now(), now(), now(), ?, ?, ?, ?);";
    sqlIns = MigrationUtil.sqlSub(sqlIns, enc.getId());
    sqlIns = MigrationUtil.sqlSub(sqlIns, enc.getVersion());
    String oid = coerceOwnerId(enc, myShepherd);
    sqlIns = MigrationUtil.sqlSub(sqlIns, oid);
    sqlIns = MigrationUtil.sqlSub(sqlIns, MigrationUtil.getPublicUserId(myShepherd).equals(oid));

    if (enc.hasAnnotatinos()) {
        Set<String> annIds = new HashSet<String>();
        for (Annotation ann : enc.getAnnotations()) {
            annIds.add(ann.getId());
        }
        sqlIns += "UPDATE annotation SET encounter_guid='" + enc.getId() + "' WHERE guid IN ('" + String.join("', '", annIds) + "');\n";
    }

    return sqlIns;
}


%><html>
<head>
    <title>Codex Encounter/Sighting/Individual Houston Migration</title>
    <link rel="stylesheet" href="m.css" />
</head>
<body>
<p>
<h1>This will help migrate <b>Encounters</b>, <b>Sightings</b>, and <b>Individuals</b> to Houston within Codex.</h1>
</p>
<hr />


<%
MigrationUtil.setDir(request.getParameter("migrationDir"));
String checkDir = MigrationUtil.checkDir();
%>
<p>
migrationDir: <b><%=checkDir%></b>
</p>
<%


String jdoql = "SELECT FROM org.ecocean.Encounter ORDER BY id";
jdoql += " RANGE 0,50";  // debug only

Query query = myShepherd.getPM().newQuery(jdoql);
Collection c = (Collection) (query.execute());
List<Encounter> allEnc = new ArrayList<Encounter>(c);
query.closeAll();

if (!commit) {
    myShepherd.rollbackDBTransaction();
%>
<p><b><%=allEnc.size()%> Encounters</b></p>
<hr /><p><b>commit=false</b>, not modifying anything</p>
<p><a href="?commit=true">Migrate DateTime values for Encounters and Occurrences</a></p>
<%
    return;
}


String content = "BEGIN;\n";
String allSql = "";
ct = 0;
for (Encounter enc : allEnc) {
    ct++;
    if (ct % 100 == 0) System.out.println("migration/occ_enc_ind.jsp [" + ct + "/" + allEnc.size() + "] encounters processed");
    content += encSql(enc, myShepherd);
}
content += "END;\n";

out.println(fileWriteAndPreview("houston_encounters.sql", content));

myShepherd.rollbackDBTransaction();

System.out.println("migration/oc_enc_ind.jsp DONE");
%>

<h1>DONE</h1>

</body></html>
