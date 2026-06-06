<%@ page contentType="text/plain; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,java.util.Properties, java.io.FileInputStream,
org.ecocean.shepherd.core.Shepherd,
org.ecocean.ia.*,
org.ecocean.ia.plugin.*,
org.ecocean.identity.IBEISIA,

java.util.ArrayList,
org.json.JSONObject,

java.io.File, java.io.FileNotFoundException, org.ecocean.*" %>
<%

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.beginDBTransaction();

// integer arg below is how big a batch to run; adjust if desired
// see Embedding.catchUpEmbeddings() for details on what this does
JSONObject res = Embedding.catchUpEmbeddings(myShepherd, null, 100);

JSONObject show = new JSONObject();
show.put("_runCount", res.get("_runCount"));
show.put("_runOk", res.get("_runOk"));
show.put("_runIds", res.get("_runIds"));

out.println(show.toString(4));


myShepherd.commitDBTransaction();
%>


