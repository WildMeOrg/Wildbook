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
<title>Re-send Stuff</title>
</head>

<body>
  <h1>Re-Sending Media assets and Annotations.</h1>
<ul>

<%
try {

    myShepherd.beginDBTransaction();
    ArrayList<MediaAsset> mas = myShepherd.getAllMediaAssetsAsArray();
    Iterator<Annotation> annsIt = myShepherd.getAllAnnotationsNoQuery();
    ArrayList<Annotation> anns = new ArrayList<>();

    while (annsIt.hasNext()) {
        Annotation ann = annsIt.next();
        anns.add(ann);
    }

    int fails = 0;

    if (commit.equals("true")) {

        %>
            <h3>Commiting is true!</h3>
        <%

        JSONObject maOb = IBEISIA.sendMediaAssetsNew(mas, context);
        out.println(maOb.toString());
        JSONObject annOb = IBEISIA.sendAnnotationsNew(anns, context);
        out.println(annOb.toString());
    } else {
        %>
            <h3>Not Committing!</h3>
        <%    
    }

} catch (Exception e) {
	System.out.println("!!! ----------- > An error occurred resending ann and media assets: "+ex.printStackTrace());
	ex.printStackTrace();
	myShepherd.rollbackDBTransaction();

} finally {

	myShepherd.closeDBTransaction();
	myShepherd=null;
}

%>

</ul>
</body>
</html>
