<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Collection,
java.util.List,
java.util.Iterator,
java.nio.file.Path,
java.util.ArrayList,
java.io.File,
java.io.BufferedReader,
java.io.FileReader,
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

private String filename(String name) {
    return "assets_" + name;
}
private String filePreview(String name) throws java.io.IOException {
    File path = new File(MigrationUtil.getDir(), name);
    BufferedReader br = new BufferedReader(new FileReader(path));
    String content = "";
    String line;
    while ( ((line = br.readLine()) != null) && (content.length() < 3500) ) {
        content += line + "\n";
    }
    String rtn = content;
    if (rtn.length() > 3000) rtn = rtn.substring(0, 3000) + "\n\n   [... preview truncated ...]";
    return "<div>This file located at: <i class=\"code\">" + path.toString() + "</i><br /><textarea class=\"preview\">" + rtn + "</textarea></div>";
}
private String coerceOwnerId(Occurrence occ, Shepherd myShepherd) {
    String gotId = null;
    String pubId = MigrationUtil.getPublicUserId(myShepherd);
    for (Encounter enc : occ.getEncounters()) {
        String sub = enc.getSubmitterID();
        if (sub == null) continue;
        User user = myShepherd.getUser(sub);
        if (user == null) continue;
        if (user.getUUID().equals(pubId)) {
            gotId = pubId;
            break;  //this wins over other users
        }
        gotId = user.getUUID();
        //we keep trying in case there is a public encounter in here
    }
    if (gotId != null) return gotId;
    System.out.println("assets.jsp: could not find User for " + occ + "; making public");
    return pubId;
}

//ideally there would only be one (total) for any Asset, but this gets "the first one" it finds
private String getSomeOccurrence(Shepherd myShepherd, MediaAsset ma) {
    String sql = "select \"OCCURRENCE_ENCOUNTERS\".\"ID_OID\" FROM \"OCCURRENCE_ENCOUNTERS\" join \"ENCOUNTER\" on (\"ENCOUNTER\".\"ID\" = \"OCCURRENCE_ENCOUNTERS\".\"ID_EID\") join \"ENCOUNTER_ANNOTATIONS\" on (\"ENCOUNTER_ANNOTATIONS\".\"ID_OID\" = \"ENCOUNTER\".\"ID\") join \"ANNOTATION_FEATURES\" on (\"ENCOUNTER_ANNOTATIONS\".\"ID_EID\" = \"ANNOTATION_FEATURES\".\"ID_OID\") join \"MEDIAASSET_FEATURES\" on (\"MEDIAASSET_FEATURES\".\"ID_EID\" = \"ANNOTATION_FEATURES\".\"ID_EID\") where \"MEDIAASSET_FEATURES\".\"ID_OID\" = " + ma.getId();

    Query query = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
    Collection c = (Collection)query.execute();
    if (c.isEmpty()) {
        query.closeAll();
        System.out.println("migration/assets.jsp could not find any Encounter for " + ma);
        return null;
    }
    String occId = (String)c.iterator().next();
    return occId;
}

private String meta(MediaAsset ma) {
    JSONObject m = new JSONObject();
    JSONObject d = new JSONObject();
    if (ma.getMetadata() != null) ma.getMetadata().getDataAsString();  //nudge to load w/h properly. :sigh: thanks dn
    d.put("width", ma.getWidth());
    d.put("height", ma.getHeight());
    m.put("derived", d);
    return MigrationUtil.jsonQuote(m);
}

/*
created        | 2021-08-10 18:27:46.077475
updated        | 2021-08-10 18:27:46.077588
viewed         | 2021-08-10 18:27:46.077628
guid           | 2b272728-f132-42cd-9347-559a74188347
version        | 
asset_guid     | e01f9cb2-0442-4f42-9068-244351e4b207
encounter_guid | 
jobs           | "null"
ia_class       | TEST
bounds         | "{\"rect\": [0, 2, 2170, 7120]}"


annotation_keywords
 created         | timestamp without time zone |           | not null | 
 updated         | timestamp without time zone |           | not null | 
 viewed          | timestamp without time zone |           | not null | 
 annotation_guid | uuid                        |           | not null | 
 keyword_guid    | uuid                        |           | not null | 

*/
private String annotSql(MediaAsset ma, Map<String,String> kmap) {
    if (!ma.hasAnnotations()) {
        return "-- no annots on " + ma + "\n";
    }
    String s = "";
    for (Annotation ann : ma.getAnnotations()) {
        String one = annotSingleSql(ann, ma) + "\n";
        s += one;
        if (one.contains("already processed")) continue;
        s += annotKeywords(ann, ma, kmap);
        s += "\n";
    }
    return s;
}

public Set<String> doneAnns = new HashSet<String>();

private String annotSingleSql(Annotation ann, MediaAsset ma) {
    if (doneAnns.contains(ann.getId())) return "-- already processed " + ann + "\n";
    doneAnns.add(ann.getId());
    String sqlIns = "INSERT INTO annotation (created, updated, viewed, guid, asset_guid, ia_class, bounds) VALUES (now(), now(), now(), ?, ?, ?, ?);";
    sqlIns = MigrationUtil.sqlSub(sqlIns, ann.getId());
    sqlIns = MigrationUtil.sqlSub(sqlIns, ma.getUUID());
    String iac = ann.getIAClass();
    if (iac == null) iac = "";
    sqlIns = MigrationUtil.sqlSub(sqlIns, iac);
    JSONObject bounds = new JSONObject();
    if (ma.getMetadata() != null) ma.getMetadata().getDataAsString();  //nudge to load w/h properly. :sigh: thanks dn
    int[] bb = ann.getBbox();
    if (bb == null) {
        bounds.put("migrateBbox", false);
    } else {
        bounds.put("rect", new JSONArray(bb));
    }
    sqlIns = MigrationUtil.sqlSub(sqlIns, MigrationUtil.jsonQuote(bounds));
    return sqlIns;
}

//*every* annot gets all asset keywords -- long live next-gen
private String annotKeywords(Annotation ann, MediaAsset ma, Map<String,String> kmap) {
    String s = "";
    if (!ma.hasKeywords()) return s;
    Set<String> hasKw = new HashSet<String>();
    for (Keyword kw : ma.getKeywords()) {
        String kid = kmap.get(kw.getReadableName());
        if (kid == null) {
            s += "-- WARNING: no kmap id for " + kw.getReadableName() + " on " + ann + " (might be LabeledKeyword)\n";
            continue;
        }
        if (hasKw.contains(kid)) continue;
        hasKw.add(kid);
        String sqlIns = "INSERT INTO annotation_keywords (created, updated, viewed, annotation_guid, keyword_guid) VALUES (now(), now(), now(), ?, ?);";
        sqlIns = MigrationUtil.sqlSub(sqlIns, ann.getId());
        sqlIns = MigrationUtil.sqlSub(sqlIns, kid);
        s += sqlIns + "\n";
    }
    return s;
}

private String getRelPath(MediaAsset ma) {
    String prefix = ((LocalAssetStore)ma.getStore()).root().toString();
    return ma.localPath().toString().substring(prefix.length() + 1);
}


%><html>
<head>
    <title>Codex Asset Migration</title>
    <link rel="stylesheet" href="m.css" />
</head>
<body>
<p>
<h1>This will help migrate <b>Assets</b> to Codex.</h1>
</p>
<hr />

<p>
The following contents below can be used with <i class="code">rsync</i> to copy the files the codex server.  This should place the files
into a <i>temporary</i> location, which will be used in subsequent steps (below) to actually ingest the files as houston <b>Assets</b>.
</p>

<p>
In this example, the temporary directory (on the houston container) will be <i class="code">/data/migration/assets</i> and the contents
of below will be copied into a file <i class="code">/tmp/filelist</i>.  Due to the nature of
<i class="code">rsync</i>, this example only serves as a template and should be modified for specific needs.
</p>
<div class="code-block">
rsync -av --files-from=/tmp/filelist oldworld.wildbook.server:/var/lib/tomcat8/webapps/wildbook_data_dir/ /data/migration/assets
</div>

<%
MigrationUtil.setDir(request.getParameter("migrationDir"));
String checkDir = MigrationUtil.checkDir();
doneAnns = new HashSet<String>();
%>
<p>
migrationDir: <b><%=checkDir%></b>
</p>
<%


String sql = "SELECT * FROM \"MEDIAASSET\" ";
sql += "\"MEDIAASSET\" join \"MEDIAASSET_FEATURES\" on (\"ID\" = \"ID_OID\") ";
sql += "join \"ANNOTATION_FEATURES\" using (\"ID_EID\") ";
sql += "join \"ENCOUNTER_ANNOTATIONS\" on (\"ANNOTATION_FEATURES\".\"ID_OID\" = \"ENCOUNTER_ANNOTATIONS\".\"ID_EID\") ";
sql += "join \"ENCOUNTER\" on (\"ENCOUNTER_ANNOTATIONS\".\"ID_OID\" = \"ENCOUNTER\".\"ID\") ";
sql += "order by \"MEDIAASSET\".\"UUID\" ";
//sql += "limit 1000 ";

//out.println(sql);
//if (sql != null) return;
String context = "context0";
Shepherd myShepherd = new Shepherd(context);
boolean commit = Util.requestParameterSet(request.getParameter("commit"));
myShepherd.beginDBTransaction();

Query query = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
query.setClass(MediaAsset.class);
Collection c = (Collection)query.execute();
List<MediaAsset> allMA = new ArrayList<MediaAsset>(c);
query.closeAll();
System.out.println("migration/assets.jsp finished initial query");

Map<String, Set<Integer>> agMap = new HashMap<String, Set<Integer>>();

String fname = filename("rsync_for_assets.filelist");
MigrationUtil.writeFile(fname, "");
String content = "";
Set<Integer> done = new HashSet<Integer>();
Set<Integer> usable = new HashSet<Integer>();
int ct = 0;
for (MediaAsset ma : allMA) {
    ct++;
    if (ct % 100 == 0) System.out.println("migration/assets.jsp [" + ct + "/" + allMA.size() + "] assets processed");
    if (done.contains(ma.getId())) continue;
    done.add(ma.getId());
    if (!ma.getStore().getType().equals(AssetStoreType.LOCAL)) continue;
    String path = getRelPath(ma);
    //out.println(ma.getId() + " - " + path);
    content += path + "\n";
    String occId = getSomeOccurrence(myShepherd, ma);
    if (occId == null) {
        content += "#### no occ for " + ma + " (should not happen if you had run occur.jsp)\n";
        continue;
    }
    MigrationUtil.appendFile(fname, content);
    content = "";

    Set<Integer> occMas = agMap.get(occId);
    if (occMas == null) {
        occMas = new HashSet<Integer>();
        agMap.put(occId, occMas);
    }
    occMas.add(ma.getId());
    usable.add(ma.getId());
}

out.println(filePreview(fname));
System.out.println("migration/assets.jsp finished assets");
%>


<h2>Make dirs and copy files</h2>
<p>
This shell script will need the <b>directories</b> at the top modified to have the right locations, but will then copy the files
which were rsync'ed (above) into the proper final location for the houston assets.
</p>

<%

fname = filename("dirs_and_copy.sh");
MigrationUtil.writeFile(fname, "");
String allSql_fname = filename("houston_assetgroups_assets.sql");
MigrationUtil.writeFile(allSql_fname, "");
content = "### change these to appropriate directories\nTMP_ASSET_DIR=/data/migration/assets\nTARGET_DIR=/data/var/asset_group\n\n";
String allSql = "BEGIN;\n\n";
ct = 0;
for (String occId : agMap.keySet()) {
    content = "";
    Occurrence occ = myShepherd.getOccurrence(occId);
    ct++;
    if (ct % 10 == 0) System.out.println("migration/assets.jsp [" + ct + "/" + agMap.keySet().size() + "] asset_groups processed");
    String subdir = occId;
    content += "\nmkdir -p $TARGET_DIR/" + subdir + "/_asset_group\n";
    content += "mkdir $TARGET_DIR/" + subdir + "/_assets\n";

    String agSql = "INSERT INTO asset_group (created, updated, viewed, guid, major_type, description, owner_guid, config) VALUES (now(), now(), now(), ?, 'filesystem', 'Legacy migration', ?, '\"{}\"');";
    String userId = coerceOwnerId(occ, myShepherd);
    agSql = MigrationUtil.sqlSub(agSql, occId);
    agSql = MigrationUtil.sqlSub(agSql, userId);
    allSql += agSql + "\n";
    occ = null;

    // ma.contentHash _may_ contain filesystem_xxhash64 (but needs getter)
    String sqlIns = "INSERT INTO asset (created, updated, viewed, guid, extension, path, mime_type, magic_signature, size_bytes, filesystem_xxhash64, filesystem_guid, semantic_guid, title, meta, asset_group_guid) VALUES (now(), now(), now(), ?, ?, ?, ?, 'TBD', ?, '00000000', '00000000-0000-0000-0000-000000000000', ?, ?, ?, ?);";
    for (Integer maId : agMap.get(occId)) {
        System.out.println("migration/assets.jsp: " + maId + " from " + occId);
        MediaAsset ma = MediaAssetFactory.load(maId, myShepherd);
        if (ma == null) continue;
        String path = getRelPath(ma);
        //out.println(ma);
        content += "cp -a $TMP_ASSET_DIR'/" + path + "' $TARGET_DIR/" + subdir + "/_asset_group/\n";
        String filename = ma.getFilename();
        int dot = filename.lastIndexOf(".");
        String ext = (dot < 0) ? "" : "." + filename.substring(dot + 1);
        if (ext.length() < 2) ext = ".unknown";  //fallback?
        content += "ln -s '../_asset_group/" + filename + "' $TARGET_DIR/" + subdir + "/_assets/" + ma.getUUID() + ext + "\n";
        String s = sqlIns;
        s = MigrationUtil.sqlSub(s, ma.getUUID());
        s = MigrationUtil.sqlSub(s, ext.substring(1));
        s = MigrationUtil.sqlSub(s, filename);
        s = MigrationUtil.sqlSub(s, "MIME");
        s = MigrationUtil.sqlSub(s, -1);
        s = MigrationUtil.sqlSub(s, Util.generateUUID());  //semantic guid -- needs to be unique????
        s = MigrationUtil.sqlSub(s, "Legacy MediaAsset id=" + ma.getId());
        s = MigrationUtil.sqlSub(s, meta(ma));  //meta (should include dimensions)
        s = MigrationUtil.sqlSub(s, occId);
        allSql += s + "\n\n";
        ma = null;
    }
    MigrationUtil.appendFile(fname, content);
    content = "";

    MigrationUtil.appendFile(allSql_fname, allSql);
    allSql = "";

    myShepherd.rollbackDBTransaction();
    myShepherd.beginDBTransaction();
}

out.println(filePreview(fname));
System.out.println("migration/assets.jsp dirs assets");

%>

<h2>Houston sql for AssetGroups and Assets</h2>
<p>
Now this sql will create the <b>AssetGroups</b> and <b>Assets</b> needed.
</p>

<%
MigrationUtil.appendFile(allSql_fname, "\n\nEND;\n");
out.println(filePreview(allSql_fname));
System.out.println("migration/assets.jsp dirs asset_groups");
%>

<h2>Keywords in houston</h2>
<p>
SQL to create the keywords in houston:
</p>

<%
fname = filename("houston_keywords.sql");
MigrationUtil.writeFile(fname, "");
content = "BEGIN;\n";
Map<String,String> kmap = new HashMap<String,String>();
List<LabeledKeyword> lkws = myShepherd.getAllLabeledKeywords();
List<Keyword> kws = myShepherd.getAllKeywordsList();
//TODO should we order by indexname to keep consistent?
for (Keyword kw : kws) {
    if (kmap.containsKey(kw.getReadableName())) continue;
    if (lkws.contains(kw)) {
        System.out.println("skipping LABELED Keyword " + kw);
        continue;
    }
    kmap.put(kw.getReadableName(), MigrationUtil.toUUID(kw.getIndexname()));
}

String sqlIns = "INSERT INTO keyword (created, updated, viewed, guid, value, source) VALUES (now(), now(), now(), ?, ?, 'user');";
for (String k : kmap.keySet()) {
    String s = sqlIns;
    s = MigrationUtil.sqlSub(s, kmap.get(k));
    s = MigrationUtil.sqlSub(s, k);
    content += s + "\n";
    MigrationUtil.appendFile(fname, content);
    content = "";
}

MigrationUtil.appendFile(fname, "\n\nEND;\n");
out.println(filePreview(fname));
System.out.println("migration/assets.jsp finished keywords");

%>


<h2>Annotations/keywords in houston</h2>
<p>SQL for Annotations and keywords in houston</p>

<%
fname = filename("houston_annotations.sql");
MigrationUtil.writeFile(fname, "");
content = "BEGIN;\n";
ct = 0;
done = new HashSet<Integer>();
for (MediaAsset ma : allMA) {
    ct++;
    if (!usable.contains(ma.getId())) {
        System.out.println("migration/assets.jsp: cannot do annotation on unusable " + ma);
        continue;
    }
    if (done.contains(ma.getId())) continue;
    done.add(ma.getId());
    content += annotSql(ma, kmap) + "\n";
    MigrationUtil.appendFile(fname, content);
    content = "";
    if (ct % 100 == 0) System.out.println("migration/assets.jsp [" + ct + "/" + allMA.size() + "] annotations processed");
}

MigrationUtil.appendFile(fname, "\n\nEND;\n");
out.println(filePreview(fname));
System.out.println("migration/assets.jsp finished annotations");

myShepherd.rollbackDBTransaction();
%>


</body></html>
