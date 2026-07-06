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

// batchSize url param sets how many annotations to sweep this run (default 100)
int batchSize = 100;
String batchParam = request.getParameter("batchSize");
if (batchParam != null) {
    try {
        batchSize = Integer.parseInt(batchParam.trim());
    } catch (NumberFormatException nfe) {
        response.setStatus(400);
        out.println(new JSONObject().put("error",
            "invalid batchSize parameter: " + batchParam).toString(4));
        return;
    }
    if (batchSize < 1) {
        response.setStatus(400);
        out.println(new JSONObject().put("error",
            "batchSize must be a positive integer; got: " + batchParam).toString(4));
        return;
    }
}

Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("appadmin.catchUpEmbeddings");
myShepherd.beginDBTransaction();
try {
    // see Embedding.catchUpEmbeddings() for details on what this does
    JSONObject res = Embedding.catchUpEmbeddings(myShepherd, null, batchSize);
    myShepherd.commitDBTransaction();

    JSONObject show = new JSONObject();
    show.put("_batchSize", batchSize);
    show.put("_runCount", res.get("_runCount"));
    show.put("_runOk", res.get("_runOk"));
    show.put("_runIds", res.get("_runIds"));
    out.println(show.toString(4));
} catch (Exception ex) {
    myShepherd.rollbackDBTransaction();
    ex.printStackTrace();
    response.setStatus(500);
    out.println(new JSONObject().put("error", ex.toString()).toString(4));
} finally {
    myShepherd.closeDBTransaction();
}
%>
