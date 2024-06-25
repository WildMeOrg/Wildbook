<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Map,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,
org.json.JSONObject,

org.ecocean.Util,
org.ecocean.servlet.ServletUtilities,
org.ecocean.Encounter,
org.ecocean.OpenSearch,
org.ecocean.media.*
              "
%>




<%

Shepherd myShepherd=null;
myShepherd = new Shepherd("context0");
myShepherd.beginDBTransaction();

//String rootDir = getServletContext().getRealPath("/");
//String baseDir = ServletUtilities.dataDir("context0", rootDir);


Encounter enc = new Encounter();
enc.setId(Util.generateUUID());
enc.opensearchCreateIndex();
enc.opensearchIndex();
//out.println(enc.opensearchIndexName());
/*
OpenSearch os = new OpenSearch();
out.println(os.client);

*/
%>
