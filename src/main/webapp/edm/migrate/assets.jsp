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
java.util.Set,
java.util.HashSet,
org.json.JSONObject,
java.sql.Connection,
java.sql.PreparedStatement,
java.sql.DriverManager,
java.lang.reflect.*,
org.ecocean.Util.MeasurementDesc,
org.ecocean.api.ApiCustomFields,
org.ecocean.customfield.*,
org.ecocean.servlet.ServletUtilities,

org.ecocean.media.*
              "
%><%!

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


private String getRelPath(MediaAsset ma) {
    String prefix = ((LocalAssetStore)ma.getStore()).root().toString();
    return ma.localPath().toString().substring(prefix.length() + 1);
}

public static Connection getConnection(Shepherd myShepherd) throws java.sql.SQLException {
    //PersistenceManagerFactory pmf = myShepherd.getPMF("context0");
    //Connection con=DriverManager.getConnection(pmf.getConnectionURL(), pmf.getConnectionUserName()
    Connection conn = null;
    //java.util.Properties connectionProps = new java.util.Properties();
    //connectionProps.put("user", CommonConfiguration.getPropertyLEGACY("datanucleus.ConnectionUserName","context0"));
    //connectionProps.put("user", "wildbook");
    //connectionProps.put("password", CommonConfiguration.getPropertyLEGACY("datanucleus.ConnectionPassword","context0"));
    //connectionProps.put("password", "wildbook");
    //conn = DriverManager.getConnection(CommonConfiguration.getPropertyLEGACY("datanucleus.ConnectionURL","context0"), connectionProps);
    conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/mig_after");
    return conn;
}

private static String sqlSub(String inSql, String rep) {
    rep = rep.replaceAll("'", "''");
    return inSql.replaceFirst("\\?", "'" + rep + "'");
}

private static String sqlSub(String inSql, Integer rep) {
    return inSql.replaceFirst("\\?", rep.toString());
}


%><html>
<head>
    <title>Codex Asset Migration</title>
    <link rel="stylesheet" href="m.css" />
</head>
<body>
<p>
This will help migrate <b>Assets</b> to Codex.
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


String sql = "SELECT * FROM \"MEDIAASSET\" ";
sql += "\"MEDIAASSET\" join \"MEDIAASSET_FEATURES\" on (\"ID\" = \"ID_OID\") ";
sql += "join \"ANNOTATION_FEATURES\" using (\"ID_EID\") ";
sql += "join \"ENCOUNTER_ANNOTATIONS\" on (\"ANNOTATION_FEATURES\".\"ID_OID\" = \"ENCOUNTER_ANNOTATIONS\".\"ID_EID\") ";
sql += "join \"ENCOUNTER\" on (\"ENCOUNTER_ANNOTATIONS\".\"ID_OID\" = \"ENCOUNTER\".\"ID\") ";
sql += "order by \"MEDIAASSET\".\"UUID\" ";
sql += "limit 30 ";

//out.println(sql);
//if (sql != null) return;
String context = "context0";
Shepherd myShepherd = new Shepherd(context);
boolean commit = Util.requestParameterSet(request.getParameter("commit"));
myShepherd.beginDBTransaction();

Query query = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
query.setClass(MediaAsset.class);
Collection c = (Collection)query.execute();
List<MediaAsset> all = new ArrayList<MediaAsset>(c);
query.closeAll();

Map<Occurrence, Set<MediaAsset>> agMap = new HashMap<Occurrence, Set<MediaAsset>>();

%><textarea><%
for (MediaAsset ma : all) {
    if (!ma.getStore().getType().equals(AssetStoreType.LOCAL)) continue;
    String path = getRelPath(ma);
    //out.println(ma.getId() + " - " + path);
    out.println(path);
    Occurrence occ = getSomeOccurrence(myShepherd, ma);
    if (occ == null) {
        out.println("#### no occ for " + ma + " (should not happen if you had run occur.jsp)");
        continue;
    }

    Set<MediaAsset> occMas = agMap.get(occ);
    if (occMas == null) {
        occMas = new HashSet<MediaAsset>();
        agMap.put(occ, occMas);
    }
    occMas.add(ma);
}
%></textarea>

<p>
This shell script will need the <b>directories</b> at the top modified to have the right locations, but will then copy the files
which were rsync'ed (above) into the proper final location for the houston assets.
</p>

<textarea><%
///Connection con = getConnection(myShepherd);

out.println("### change these to appropriate directories\nTMP_ASSET_DIR=/data/migration/assets\nTARGET_DIR=/data/var/asset_group\n");
String allSql = "";
for (Occurrence occ : agMap.keySet()) {
    String subdir = occ.getId();
    out.println("\nmkdir -p $TARGET_DIR/" + subdir + "/_asset_group");
    out.println("mkdir $TARGET_DIR/" + subdir + "/_assets");

    String agSql = "INSERT INTO asset_group (created, updated, viewed, guid, major_type, description, owner_guid, config) VALUES (now(), now(), now(), ?, 'filesystem', 'Legacy migration', ?, '\"{}\"');";
    String userId = "88c3f1da-179b-40be-b186-5bd33ef7dc16";
    agSql = sqlSub(agSql, occ.getId());
    agSql = sqlSub(agSql, userId);
    allSql += agSql + "\n";

    String sqlIns = "INSERT INTO asset (created, updated, viewed, guid, extension, path, mime_type, magic_signature, size_bytes, filesystem_xxhash64, filesystem_guid, semantic_guid, title, meta, asset_group_guid) VALUES (now(), now(), now(), ?, ?, ?, ?, 'TBD', ?, '00000000', '00000000-0000-0000-0000-000000000000', ?, ?, ?, ?);";
    for (MediaAsset ma : agMap.get(occ)) {
        String path = getRelPath(ma);
        //out.println(ma);
        out.println("cp -a $TMP_ASSET_DIR'/" + path + "' $TARGET_DIR/" + subdir + "/_asset_group/");
        String fname = ma.getFilename();
        int dot = fname.lastIndexOf(".");
        String ext = (dot < 0) ? "" : "." + fname.substring(dot + 1);
        out.println("ln -s '../_asset_group/" + fname + "' $TARGET_DIR/" + subdir + "/_assets/" + ma.getUUID() + ext);
        String s = sqlIns;
        s = sqlSub(s, ma.getUUID());
        s = sqlSub(s, ext.substring(1));
        s = sqlSub(s, fname);
        s = sqlSub(s, "MIME");
        s = sqlSub(s, -1);
        s = sqlSub(s, Util.generateUUID());  //semantic guid -- needs to be unique????
        s = sqlSub(s, "Legacy MediaAsset id=" + ma.getId());
        s = sqlSub(s, "\"{}\"");  //meta (should include dimensions)
        s = sqlSub(s, occ.getId());
        allSql += s + "\n\n";
    }
}
/*
created                    | 2021-08-10 18:14:16.553598
updated                    | 2021-08-10 18:14:16.553643
viewed                     | 2021-08-10 18:14:16.553657
guid                       | f1bacade-f66b-4e39-9528-8d526bee002e
major_type                 | filesystem
commit                     | 
commit_mime_whitelist_guid | 
commit_houston_api_version | 
description                | Sighting.post fd7f02e3-eb37-4fec-8eb3-e129cd9e5d1e
owner_guid                 | 88c3f1da-179b-40be-b186-5bd33ef7dc16
submitter_guid             | 
config                     | "{}"



created             | 2021-08-16 22:46:38.025156
updated             | 2021-08-16 22:46:38.085569
viewed              | 2021-08-16 22:46:38.025208
guid                | d234bbbd-f536-4c0e-ac10-8f1bcff82171
extension           | jpg
path                | test.jpg
mime_type           | image/jpeg
magic_signature     | JPEG image data, JFIF standard 1.01, resolution (DPI), density 72x72, segment length 16, comment: "2021 All rights reserved. | wildbook.org | Flukebook.org | parent 78d42b24-02cf-4ef3-a878-a358", baseline, precision 8, 4032x3024, components 3
size_bytes          | 3618806
filesystem_xxhash64 | 1882783bb57fa8b9
filesystem_guid     | 3eb744e7-3ad7-e129-ed62-bee20fb27e4e
semantic_guid       | 929bd3cf-1ca4-aeaf-f496-0c0bce462a36
content_guid        | 
title               | 
description         | 
meta                | "{\"derived\": {\"width\": 4032, \"height\": 3024}}"
asset_group_guid    | e42fe051-9253-4390-abd9-2e7d35483872
*/
%></textarea>

<p>
Now this sql will create the <b>AssetGroups</b> and <b>Assets</b> needed.
</p>

<textarea>
<%=allSql%>
</textarea><%
myShepherd.rollbackDBTransaction();

%>


</body></html>
