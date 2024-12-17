<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
java.util.Iterator,
org.ecocean.*
"%>


<%
System.out.println("opensearchSync.jsp begun...");
long timer = System.currentTimeMillis();
int numProcessed = -1;
Util.mark("opensearchSync begin");

boolean resetIndex = Util.requestParameterSet(request.getParameter("resetIndex"));

String fstr = request.getParameter("forceNum");
int forceNum = -1;
if ("".equals(fstr)) {
    forceNum = 500;
} else if (fstr != null) {
    try {
        forceNum = Integer.parseInt(fstr);
    } catch (Exception ex) {}
}

if (forceNum == 0) forceNum = 999999;

String sstr = request.getParameter("startNum");
int startNum = -1;
if (sstr != null) {
    try {
        startNum = Integer.parseInt(sstr);
    } catch (Exception ex) {}
}

Shepherd myShepherd = new Shepherd(request);
OpenSearch os = new OpenSearch();

if (resetIndex && os.existsIndex("encounter")) {
    os.deleteIndex("encounter");
    OpenSearch.unsetActiveIndexingForeground();
    OpenSearch.unsetActiveIndexingBackground();
    out.println("<p>deleted encounter index</p>");
}

if (OpenSearch.indexingActive()) {
    out.println("<P>bailing due to active indexing: fore=<b>" + OpenSearch.indexingActiveForeground() + "</b>, back=<b>" + OpenSearch.indexingActiveBackground() + "</b></p>");
    System.out.println("opensearchSync.jsp bailed due to other active indexing");
    return;
}

OpenSearch.setActiveIndexingForeground();

if (!os.existsIndex("encounter")) {
        Encounter enc = new Encounter();
        enc.opensearchCreateIndex();
        out.println("<b>created encounter index</b>");
}


if (forceNum > 0) {
    out.println("<p>indexing " + forceNum + " Encounters</p>");
    int ct = 0;
    Iterator itr = myShepherd.getAllEncounters("catalogNumber");
    while (itr.hasNext()) {
            Encounter enc = (Encounter)itr.next();
            if (!Util.stringExists(enc.getId())) continue;
            ct++;
            if (startNum > 0) {
                if (ct < startNum) continue;
                if (ct == startNum) System.out.println("opensearchSync.jsp: starting at " + startNum);
            }
            //System.out.println(enc.getId() + ": " + enc.getVersion());
            try {
                enc.opensearchIndex();
                numProcessed++;
            } catch (Exception ex) {
                System.out.println("opensearchSync.jsp: exception failure on " + enc);
                ex.printStackTrace();
            }
            if (ct % 100 == 0) System.out.println("opensearchSync.jsp: count " + ct);
            if (ct > forceNum) break;
    }

} else {
    int[] res = Encounter.opensearchSyncIndex(myShepherd);
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
