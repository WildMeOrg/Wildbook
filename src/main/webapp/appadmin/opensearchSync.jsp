<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
java.util.Iterator,
org.ecocean.media.*,
org.ecocean.*
"%>


<%
String indexName = request.getParameter("indexName");
if (!OpenSearch.isValidIndexName(indexName)) {
    out.println("must have ?indexName=foo");
    return;
}

Class cls = null;
Base obj = null;
if (indexName.equals("encounter")) {
    cls = Encounter.class;
    obj = new Encounter();
} else if (indexName.equals("annotation")) {
    cls = Annotation.class;
    obj = new Annotation();
} else if (indexName.equals("media_asset")) {
    cls = MediaAsset.class;
    obj = new MediaAsset();
} else if (indexName.equals("individual")) {
    cls = MarkedIndividual.class;
    obj = new MarkedIndividual();
}

System.out.println("opensearchSync.jsp begun...");
long timer = System.currentTimeMillis();
int numProcessed = -1;
Util.mark("opensearchSync begin");

boolean resetIndex = Util.requestParameterSet(request.getParameter("resetIndex"));

String fstr = request.getParameter("endNum");
int endNum = -1;
if ("".equals(fstr)) {
    endNum = 500;
} else if (fstr != null) {
    try {
        endNum = Integer.parseInt(fstr);
    } catch (Exception ex) {}
}

if (endNum == 0) endNum = 999999;

String sstr = request.getParameter("startNum");
int startNum = -1;
if (sstr != null) {
    try {
        startNum = Integer.parseInt(sstr);
    } catch (Exception ex) {}
}

Shepherd myShepherd = new Shepherd(request);
OpenSearch os = new OpenSearch();

if (resetIndex && os.existsIndex(indexName)) {
    os.deleteIndex(indexName);
    OpenSearch.unsetActiveIndexingForeground();
    OpenSearch.unsetActiveIndexingBackground();
    out.println("<p>deleted " + indexName + " index</p>");
}

if (OpenSearch.indexingActive()) {
    out.println("<P>bailing due to active indexing: fore=<b>" + OpenSearch.indexingActiveForeground() + "</b>, back=<b>" + OpenSearch.indexingActiveBackground() + "</b></p>");
    System.out.println("opensearchSync.jsp bailed due to other active indexing");
    return;
}

OpenSearch.setActiveIndexingForeground();

if (!os.existsIndex(indexName)) {
        obj.opensearchCreateIndex();
        out.println("<b>created " + indexName + " index</b>");
}


if (endNum > 0) {
    if (startNum > 0) {
        out.println("<p>indexing " + startNum + "-" + endNum + " " + indexName + "s</p>");
    } else {
        out.println("<p>indexing through " + endNum + " " + indexName + "s</p>");
    }
    int ct = 0;
    Iterator itr = null;
    if (indexName.equals("encounter")) {
        itr = myShepherd.getAllEncounters("catalogNumber");
    } else if (indexName.equals("annotation")) {
        itr = myShepherd.getAllAnnotations("id");
    } else if (indexName.equals("media_asset")) {
        String range = ((startNum > 0) ? startNum : 1) + "," + (endNum + 1);
        itr = myShepherd.getMediaAssetsFilter("parentId == null ORDER BY id " + range);
    } else if (indexName.equals("individual")) {
        itr = myShepherd.getAllMarkedIndividuals();
    }
    System.out.println("opensearchSync.jsp: query all complete");
    while (itr.hasNext()) {
            Base iObj = (Base)itr.next();
            if (!Util.stringExists(iObj.getId())) continue;
            ct++;
            if (startNum > 0) {
                if (ct < startNum) continue;
                if (ct == startNum) System.out.println("opensearchSync.jsp: starting at " + startNum);
            }
            //System.out.println(iObj.getId() + ": " + iObj.getVersion());
            try {
                iObj.opensearchIndex();
                numProcessed++;
            } catch (Exception ex) {
                System.out.println("opensearchSync.jsp: exception failure on " + iObj);
                ex.printStackTrace();
            }
            if (ct % 100 == 0) System.out.println("opensearchSync.jsp: count " + ct);
            if (ct > endNum) break;
    }

} else {
    OpenSearch.unsetActiveIndexingForeground(); // is set by opensearchSyncIndex
    int[] res = Base.opensearchSyncIndex(myShepherd, cls, 0);
    out.println("<p>re-indexed: <b>" + res[0] + "</b></p>");
    out.println("<p>removed: <b>" + res[1] + "</b></p>");
}

myShepherd.rollbackAndClose();

OpenSearch.unsetActiveIndexingForeground();
os.deleteAllPits();

double totalMin = System.currentTimeMillis() - timer;
totalMin = totalMin / 60000D;
if (numProcessed > 0) {
    System.out.println("opensearchSync.jsp finished: " + numProcessed + " in " + String.format("%.2f", totalMin) + " min (" + String.format("%.2f", numProcessed / totalMin) + " per min)");
} else {
    System.out.println("opensearchSync.jsp finished: " + totalMin + " min");
}
Util.mark("opensearchSync ended", timer);

%>
