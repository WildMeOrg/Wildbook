<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
java.util.Iterator,
org.ecocean.*
"%>


<%
System.out.println("opensearchSync.jsp begun...");

boolean resetIndex = Util.requestParameterSet("resetIndex");

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

Shepherd myShepherd = new Shepherd(request);
OpenSearch os = new OpenSearch();

if (resetIndex && os.existsIndex("encounter")) {
    os.deleteIndex("encounter");
    out.println("<p>deleted encounter index</p>");
}

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
            //System.out.println(enc.getId() + ": " + enc.getVersion());
            enc.opensearchIndex();
            if (ct % 100 == 0) System.out.println("opensearchSync.jsp: count " + ct);
            ct++;
            if (ct > forceNum) break;
    }

} else {
    int[] res = Encounter.opensearchSyncIndex(myShepherd);
    out.println("<p>re-indexed: <b>" + res[0] + "</b></p>");
    out.println("<p>removed: <b>" + res[1] + "</b></p>");
}

myShepherd.rollbackAndClose();

os.deleteAllPits();
System.out.println("opensearchSync.jsp finished");

%>
