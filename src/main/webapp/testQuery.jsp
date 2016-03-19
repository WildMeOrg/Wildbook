<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Map,
java.util.List,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,
org.json.JSONObject,

org.ecocean.media.*,
javax.jdo.Query
              "
%>




<%

Shepherd myShepherd=null;
myShepherd = new Shepherd("context0");

//WBQuery wbq = ((WBQuery) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(WBQuery.class, 1), true)));

JSONObject exJSON = new JSONObject("{\"class\":\"org.ecocean.Encounter\",\"query\": {\"sex\":\"male\",\"livingStatus\":\"dead\"}}");

JSONObject exJSON2 = new JSONObject("{\"class\": \"org.ecocean.Encounter\",\"query\": {\"location\": \"The Big Lagoon\",\"sex\": {\"$ne\": \"female\"},\"maxDate\": {\"$gte\": \"2013-05-01T00:00:00\"},\"minDate\": {\"$lt\": \"2013-06-01T00:00:00\"}}}");

WBQuery wbq = new WBQuery(exJSON);
WBQuery wbq2 = new WBQuery(exJSON2);

String toJDOQL = wbq.toJDOQL();
String toJDOQL2 = wbq2.toJDOQL();
//List<Object> q1 = wbq.doQuery(myShepherd);

//List<Object> all = wbq.doQuery(myShepherd);
out.println("<p>toJDOQL 1:</br>");
out.println(toJDOQL);
out.println("</p><p>toJDOQL 2:</br>");
out.println(toJDOQL2+"</p>");
%>
