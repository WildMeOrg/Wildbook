<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.media.*,
org.ecocean.identity.*,
org.json.JSONObject,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>
<%
String context="context0";
context=ServletUtilities.getContext(request);
Shepherd myShepherd=new Shepherd(context);
String commit = request.getParameter("commit");
%>
<html>
<head>
<title>Match This</title>
</head>

<body>
  <h1>Set match against for non unity feature annotations.</h1>
<ul>

<%
try {

    myShepherd.beginDBTransaction();
    Iterator<Annotation> annsIt = myShepherd.getAllAnnotationsNoQuery();

    int changed = 0;
    while (annsIt.hasNext()) {
        System.out.println("Checking another ann...");
        Annotation ann = annsIt.next();

        ArrayList<Feature> farr = ann.getFeatures();

        for (Feature f : farr) {
            if (f.getType()!=null) {
                System.out.println("--- Checking Feature...");
                if (commit!=null&&"true".equals(commit)) {
                    changed++;
                    System.out.println("CHANGING to MATCH AGAINST!! "+ann.getId());
                    ann.setMatchAgainst(true);
                    continue;
                } else {
                    changed++
                    System.out.println("Potentially changing this Annotation to match against: "+ann.getId());
                }
            }
        }
    }
    System.out.println("Anns to change = "+changed);

    myShepherd.commitDBTransaction();


} catch (Exception e) {
	System.out.println("!!! ----------- > Can't set match against on annotations!: "+e.toString());
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
