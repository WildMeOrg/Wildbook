<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Map,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,
org.json.JSONObject,

org.ecocean.media.*
              "
%>




<%

Shepherd myShepherd = new Shepherd("context0");

myShepherd.beginDBTransaction();

Encounter enc = myShepherd.getEncounter(Util.generateUUID());

myShepherd.commitDBTransaction();

%>

<h1>initialized</h1>


