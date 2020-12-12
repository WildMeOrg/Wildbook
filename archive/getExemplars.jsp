<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.media.*,org.ecocean.Annotation,
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

<%


myShepherd.beginDBTransaction();


try {

	String rootDir = getServletContext().getRealPath("/");
	String baseDir = ServletUtilities.dataDir(context, rootDir).replaceAll("dev_data_dir", "caribwhale_data_dir");


  ArrayList<Annotation> exemplars = Annotation.getExemplars("Lynx pardinus", myShepherd);

  %>
  <h1>Checking exemplars</h1>

  <p>There are <%=exemplars.size()%> exemplars</p>
  <ul>
  <%

	for (Annotation exemp: exemplars)  {

    %><li>Annot <%=exemp.getId()%> has exemplar <%=exemp.getMediaAsset()%></li><%

	}
  %></ul><%


}
catch(Exception e){
	myShepherd.rollbackDBTransaction();
}
finally{
	myShepherd.closeDBTransaction();

}

%>

</body>
</html>
