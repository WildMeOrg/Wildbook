<%@ page contentType="text/json; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Map,
java.util.Iterator,
java.util.ArrayList,
java.util.Collections,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,
org.json.JSONArray,
org.json.JSONObject,
org.ecocean.identity.*,
org.ecocean.Project,
org.ecocean.media.*"
%>
<%
JSONArray jarr = new JSONArray();
Shepherd myShepherd = new Shepherd("context0");
myShepherd.setAction("fakeQuery.jsp");
myShepherd.beginDBTransaction();
int count = 0;
Iterator<MarkedIndividual> all = myShepherd.getAllMarkedIndividuals();
while (all.hasNext()) {
    count++;
    if (count > 10) break;
    MarkedIndividual ind = all.next();
    JSONObject jind = new JSONObject();
    jind.put("id", ind.getIndividualID());
    jind.put("taxonomy", ind.getTaxonomyString());
    jind.put("names", ind.getNamesList());
    jarr.put(jind);
}
out.println(jarr.toString(4));
myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();
%>