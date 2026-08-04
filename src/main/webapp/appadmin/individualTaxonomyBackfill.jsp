<%@ page contentType="text/plain; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,
org.ecocean.shepherd.core.Shepherd,
org.json.JSONObject,
org.ecocean.*" %>
<%
// Gives individuals with no taxonomy of their own the taxonomy their encounters agree on, so the
// header quicksearch and the individual search results stop showing a blank species for them
// (issue #1113). Dry run unless commit=true. Walks the table across runs via a stored cursor;
// pass startId to override where this run begins.

String context = ServletUtilities.getContext(request);

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
boolean commit = "true".equals(request.getParameter("commit"));
String startId = request.getParameter("startId");

Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("appadmin.individualTaxonomyBackfill");
myShepherd.beginDBTransaction();
try {
    JSONObject res = MarkedIndividual.backfillTaxonomyFromEncounters(myShepherd, startId,
        batchSize, commit);
    if (commit) {
        // commitDBTransaction() swallows commit failures; use the status variant
        // so a failed commit reports an error instead of success json
        if (!myShepherd.commitDBTransactionWithStatus()) {
            throw new RuntimeException("commit failed; see logs");
        }
    } else {
        res.put("_note",
            "dry run: nothing was written and the cursor did not move; add commit=true to apply");
        myShepherd.rollbackDBTransaction();
    }
    out.println(res.toString(4));
} catch (Exception ex) {
    if (myShepherd.isDBTransactionActive()) myShepherd.rollbackDBTransaction();
    ex.printStackTrace();
    response.setStatus(500);
    out.println(new JSONObject().put("error", ex.toString()).toString(4));
} finally {
    myShepherd.closeDBTransaction();
}
%>
