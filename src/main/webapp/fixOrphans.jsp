<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.media.*,org.ecocean.*,
org.ecocean.identity.*,
org.ecocean.ia.IA,
org.ecocean.ia.Task,
org.ecocean.ia.plugin.WildbookIAM,
org.ecocean.RestClient,
org.json.JSONObject,
java.io.*,java.util.*,java.net.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>
<%
String context="context0";
context=ServletUtilities.getContext(request);
Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("sendMediaAssets.jsp");
String commit = request.getParameter("commit");

%>
<html>
<head>
<title>No orphans match</title>
</head>
<body>
  <h1>Fixing orphan anns</h1>
<ul>
<%
try {
    int total = 0;
    List<Annotation> orphans = new ArrayList<>();
    Iterator anns = myShepherd.getAllAnnotationsNoQuery();
    while (anns.hasNext()) {
        total++;
        Annotation ann = anns.next();
        Encounter enc = ann.findEncounter(myShepherd);
        if (enc==null&&ann.getMatchAgainst()==true) {orphans.add(ann);}
    }
    System.out.println("Total Anns = "+total);
    System.out.println("Total orphans = "+orphans.size());

    if ("true".equals(commit)) {
        System.out.println("COMMITING!");
        for (Annotation ann : orphans) {
            ann.setMatchAgainst(false);
            System.out.println("matchAgainst=false on Annotation "+ann.getId());
        }
    }

} catch (Exception e) {
	System.out.println("!!! ----------- > An error occurred setting anns with no enc matchAgainst=false...");
	e.printStackTrace();
	myShepherd.rollbackDBTransaction();
} finally {
	myShepherd.closeDBTransaction();
	myShepherd=null;
}

%>

</ul>
</body>
</html>

