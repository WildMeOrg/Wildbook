<%@ page contentType="application/javascript; charset=utf-8" 
		language="java"
        import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,
org.ecocean.datacollection.Instant,
org.ecocean.media.MediaAsset,
org.ecocean.media.Feature,
org.json.JSONObject,
org.json.JSONArray,
org.joda.time.DateTime,
java.io.File,
java.util.Iterator,
java.util.List,
javax.jdo.*,
java.util.Properties" %>
<%

String[] xxvals = request.getParameterValues("hash");
if ((xxvals == null) || (xxvals.length < 1)) {
    out.println("[]");
    return;
}
for (int i = 0 ; i < xxvals.length ; i++) {
    xxvals[i] = Util.sanitizeUserInput(xxvals[i]);
}

JSONArray rtn = new JSONArray();
String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.beginDBTransaction();

String sql = "SELECT \"MEDIAASSET\".\"ID\", \"CONTENTHASH\", \"PARAMETERS\", \"CATALOGNUMBER_OID\", \"DATEINMILLISECONDS\" FROM \"MEDIAASSET\" JOIN \"MEDIAASSET_FEATURES\" ON (\"ID\" = \"ID_OID\") JOIN \"ANNOTATION_FEATURES\" USING (\"ID_EID\") JOIN \"ENCOUNTER_ANNOTATIONS\" ON (\"ANNOTATION_FEATURES\".\"ID_OID\" = \"ENCOUNTER_ANNOTATIONS\".\"ID_EID\") JOIN \"ENCOUNTER\" ON (\"ENCOUNTER_ANNOTATIONS\".\"CATALOGNUMBER_OID\" = \"ENCOUNTER\".\"CATALOGNUMBER\") WHERE \"CONTENTHASH\" in ('" + String.join("','", xxvals) + "')";


System.out.println(sql);
Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
List results = (List)q.execute();
Iterator it = results.iterator();
while (it.hasNext()) {
    JSONObject jm = new JSONObject();
    Object[] row = (Object[]) it.next();
    jm.put("id", (Integer)row[0]);
    jm.put("contentHash", (String)row[1]);
    JSONObject params = Util.stringToJSONObject((String)row[2]);
    File f = new File(params.optString("path", null));
    jm.put("filename", f.getName());
    jm.put("encounterId", (String)row[3]);
    jm.put("dateMillis", (Long)row[4]);
    rtn.put(jm);
}
out.println(rtn.toString());

%>
