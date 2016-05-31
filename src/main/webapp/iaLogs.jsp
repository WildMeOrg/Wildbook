<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Map,
java.util.ArrayList,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,
org.json.JSONArray,
org.json.JSONObject,
org.ecocean.identity.*,
org.ecocean.media.*
              "
%>




<%

Shepherd myShepherd=null;
myShepherd = new Shepherd("context0");


String id = request.getParameter("id");
if (id == null) {
	out.println("{\"success\": false, \"error\": \"no object id passed\"}");
	return;
}

ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadMostRecentByObjectID("IBEISIA", id, myShepherd);

if (logs == null) {
	out.println("[]");
	return;
}

JSONArray all = new JSONArray();
for (IdentityServiceLog l : logs) {
	all.put(l.toJSONObject());
}

out.println(all.toString());

%>



