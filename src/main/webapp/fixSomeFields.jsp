<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.media.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.datanucleus.api.rest.orgjson.JSONObject, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);



%>

<html>
<head>
<title>Fix Some Fields</title>

</head>


<body>
<h1>Counting Media Assets.</h1>

<ul>
<%


myShepherd.beginDBTransaction();

int numFixes=0;
int numAnnots=0;
boolean committing=false;
%><h3>committing = <%=committing%></h3><%


try {

	String rootDir = getServletContext().getRealPath("/");
	String baseDir = ServletUtilities.dataDir(context, rootDir).replaceAll("dev_data_dir", "caribwhale_data_dir");

	Iterator allMAs=myShepherd.getAllMediaAssets();

	while(allMAs.hasNext()){

		MediaAsset ma = (MediaAsset) allMAs.next();
    numFixes++;

    JSONObject j = ma.sanitizeJson(request, new JSONObject());
    %><p><%=j.toString()%></p><ul><%



    if (committing) {
      //ma.updateStandardChildren(myShepherd);
      %><li>updated standard children./li><%

      ma.updateMinimalMetadata();
      %><li>updated minimal metadata./li><%

      myShepherd.commitDBTransaction();
      myShepherd.beginDBTransaction();
    }
    %></ul><%


	}

  Iterator allAnns=myShepherd.getAllAnnotationsNoQuery();
/*
  while(allAnns.hasNext()){

    Annotation ann = (Annotation) allAnns.next();
    numAnnots++;
    if (committing) {
      myShepherd.commitDBTransaction();
      myShepherd.beginDBTransaction();
    }

  }
*/
}
catch(Exception e){
	myShepherd.rollbackDBTransaction();
}
finally{
	myShepherd.closeDBTransaction();

}

%>

</ul>
<p>Done successfully: <%=numFixes %></p>
<p>Num Media Assets: <%=numFixes %></p>
<p>Num Annotations: <%=numAnnots %></p>

</body>
</html>
