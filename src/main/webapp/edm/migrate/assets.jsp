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
org.ecocean.api.ApiCustomFields,
org.ecocean.customfield.*,

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
<i class="code">rsync</i>, this only serves as a template and should be modified for specific needs.
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

%><textarea style="width: 100%; height: 10em;"><%
for (MediaAsset ma : all) {
    if (!ma.getStore().getType().equals(AssetStoreType.LOCAL)) continue;
    String prefix = ((LocalAssetStore)ma.getStore()).root().toString();
    String path = ma.localPath().toString().substring(prefix.length() + 1);
    //out.println(ma.getId() + " - " + path);
    out.println(path);
}
%></textarea><%

myShepherd.rollbackDBTransaction();

%>


</body></html>
