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
    if (sub == null) return null;
    User user = myShepherd.getUser(sub);
    if (user != null) return user.getUUID();
    return null;
}

//ideally there would only be one (total) for any Asset, but this gets "the first one" it finds
private Occurrence getSomeOccurrence(Shepherd myShepherd, MediaAsset ma) {
    Encounter enc = null;
    for (Annotation ann : ma.getAnnotations()) {
        enc = ann.findEncounter(myShepherd);
        if (enc != null) break;
    }
    if (enc == null) {
        System.out.println("migration/occ_enc_ind.jsp could not find any Encounter for " + ma);
        return null;
    }
    Occurrence occ = myShepherd.getOccurrence(enc);
    if (occ == null) {
        System.out.println("migration/occ_enc_ind.jsp could not find any Occurrence for " + enc + " (via " + ma + ")");
        return null;
    }
    return occ;
}


private String encSql(Encounter enc, Shepherd myShepherd) {
    boolean err = false;
    if (!Util.stringExists(enc.getId())) return "";
    String sqlIns = "INSERT INTO encounter (created, updated, viewed, guid, version, owner_guid, public) VALUES (now(), now(), now(), ?, ?, ?, ?);\n";
    sqlIns = MigrationUtil.sqlSub(sqlIns, enc.getId());
    Long vers = enc.getVersion();
    if (vers == null) vers = 3L;  //better than null, i say?
    sqlIns = MigrationUtil.sqlSub(sqlIns, vers);
    String oid = coerceOwnerId(enc, myShepherd);
    if (oid == null) {
        sqlIns = MigrationUtil.sqlSub(sqlIns, "__NO_OWNER_FOUND__");
        err = true;
    } else {
        sqlIns = MigrationUtil.sqlSub(sqlIns, oid);
    }
    sqlIns = MigrationUtil.sqlSub(sqlIns, MigrationUtil.getPublicUserId(myShepherd).equals(oid));

    if (err) {
        System.out.println("migration/occ_enc_ind.jsp: could not find owner for " + enc);
        sqlIns = "-- could not find owner for " + enc + "\n-- " + sqlIns;
    }

    if (enc.hasAnnotations()) {
        Set<String> annIds = new HashSet<String>();
        for (Annotation ann : enc.getAnnotations()) {
            annIds.add(ann.getId());
        }
        sqlIns += (err ? "-- " : "") + "UPDATE annotation SET encounter_guid='" + enc.getId() + "' WHERE guid IN ('" + String.join("', '", annIds) + "');\n";
    }

    return "\n" + sqlIns;
}

private String occSql(Occurrence occ, Shepherd myShepherd) {
    if (!Util.stringExists(occ.getId())) return "";
    String sqlIns = "INSERT INTO sighting (created, updated, viewed, guid, version, stage, name) VALUES (now(), now(), now(), ?, ?, ?, ?);\n";
    sqlIns = MigrationUtil.sqlSub(sqlIns, occ.getId());
    Long vers = occ.getVersion();
    if (vers == null) vers = 3L;  //better than null, i say?
    sqlIns = MigrationUtil.sqlSub(sqlIns, vers);
    sqlIns = MigrationUtil.sqlSub(sqlIns, "processed");
    sqlIns = MigrationUtil.sqlSub(sqlIns, occ.getAlternateId());

    // are we allowing empty sightings in codex??  TODO
    if (!Util.collectionIsEmptyOrNull(occ.getEncounters())) {
        Set<String> encIds = new HashSet<String>();
        for (Encounter enc : occ.getEncounters()) {
            encIds.add(enc.getId());
        }
        sqlIns += "UPDATE encounter SET sighting_guid='" + occ.getId() + "' WHERE guid IN ('" + String.join("', '", encIds) + "');\n";
    }

    return "\n" + sqlIns;
}

private String indSql(MarkedIndividual indiv, Shepherd myShepherd) {
    if (!Util.stringExists(indiv.getId())) return "";
    String sqlIns = "INSERT INTO individual (created, updated, viewed, guid, version) VALUES (now(), now(), now(), ?, ?);\n";
    sqlIns = MigrationUtil.sqlSub(sqlIns, indiv.getId());
    Long vers = indiv.getVersion();
    if (vers == null) vers = 3L;  //better than null, i say?
    sqlIns = MigrationUtil.sqlSub(sqlIns, vers);

    // are we allowing empty individuals in codex??  TODO
    if (indiv.numEncounters() > 0) {
        Set<String> encIds = new HashSet<String>();
        for (Encounter enc : indiv.getEncounters()) {
            encIds.add(enc.getId());
        }
        sqlIns += "UPDATE encounter SET individual_guid='" + indiv.getId() + "' WHERE guid IN ('" + String.join("', '", encIds) + "');\n";
    }

    return "\n" + sqlIns;
}


%><html>
<head>
    <title>Codex Encounter/Sighting/Individual Houston Migration</title>
    <link rel="stylesheet" href="m.css" />
</head>
<body>
<p>
<h2>This will help migrate <b>Encounters</b>, <b>Sightings</b>, and <b>Individuals</b> to Houston within Codex.</h2>
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

boolean debug = false;
String context = "context0";
Shepherd myShepherd = new Shepherd(context);
boolean commit = Util.requestParameterSet(request.getParameter("commit"));
myShepherd.beginDBTransaction();


String jdoql = "SELECT FROM org.ecocean.Encounter ORDER BY id";
if (debug) jdoql += " RANGE 0,500";
Query query = myShepherd.getPM().newQuery(jdoql);
Collection c = (Collection) (query.execute());
List<Encounter> allEnc = new ArrayList<Encounter>(c);
query.closeAll();

// now occurrences/sightings
jdoql = "SELECT FROM org.ecocean.Occurrence ORDER BY id";
if (debug) jdoql += " RANGE 0,500";
query = myShepherd.getPM().newQuery(jdoql);
c = (Collection) (query.execute());
List<Occurrence> allOcc = new ArrayList<Occurrence>(c);
query.closeAll();

// now indiv
jdoql = "SELECT FROM org.ecocean.MarkedIndividual ORDER BY id";
if (debug) jdoql += " RANGE 0,500";
query = myShepherd.getPM().newQuery(jdoql);
c = (Collection) (query.execute());
List<MarkedIndividual> allInd = new ArrayList<MarkedIndividual>(c);
query.closeAll();

if (!commit) {
    myShepherd.rollbackDBTransaction();
%>
<p>
    <b><%=allEnc.size()%> Encounters</b>,
    <b><%=allOcc.size()%> Sightings</b>,
    <b><%=allInd.size()%> Individuals</b>
</p>
<hr /><p><b>commit=false</b>, not modifying anything</p>
<p><a href="?commit=true">Generate sql</a></p>
<%
    return;
}


String content = "BEGIN;\n";
int ct = 0;
for (Encounter enc : allEnc) {
    ct++;
    if (ct % 100 == 0) System.out.println("migration/occ_enc_ind.jsp [" + ct + "/" + allEnc.size() + "] encounters processed");
    content += encSql(enc, myShepherd);
}
content += "END;\n";
out.println(fileWriteAndPreview("houston_encounters.sql", content));


content = "BEGIN;\n";
ct = 0;
for (Occurrence occ : allOcc) {
    ct++;
    if (ct % 100 == 0) System.out.println("migration/occ_enc_ind.jsp [" + ct + "/" + allOcc.size() + "] occurrences processed");
    content += occSql(occ, myShepherd);
}
content += "END;\n";
out.println(fileWriteAndPreview("houston_occurrences.sql", content));


content = "BEGIN;\n";
ct = 0;
for (MarkedIndividual ind : allInd) {
    ct++;
    if (ct % 100 == 0) System.out.println("migration/occ_enc_ind.jsp [" + ct + "/" + allInd.size() + "] individuals processed");
    content += indSql(ind, myShepherd);
}
content += "END;\n";
out.println(fileWriteAndPreview("houston_individuals.sql", content));

myShepherd.rollbackDBTransaction();

System.out.println("migration/oc_enc_ind.jsp DONE");
%>

<h1>DONE</h1>

</body></html>
