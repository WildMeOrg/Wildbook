<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

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

int numFixes=0;
int oldFixes=0;

int errors=0;




try {

	String rootDir = getServletContext().getRealPath("/");
	String baseDir = ServletUtilities.dataDir(context, rootDir).replaceAll("dev_data_dir", "caribwhale_data_dir");

  Iterator<MarkedIndividual> allEncs=myShepherd.getAllMarkedIndividuals();

  boolean committing=true;

  int printPeriod = 50;
  int count=0;
  int limit=20000;

  %>


<p>Starting! committing = <%=committing%> and limit = <%=limit%></p>
<hr></hr>
<ul>
<%


  while(allEncs.hasNext() && count<limit){

    MarkedIndividual mark=allEncs.next();

    count++;
  	numFixes++;

    String oldThumb = mark.getThumbnailUrl();
    String newThumb = mark.refreshThumbnailUrl(request);

    if (Util.stringExists(newThumb) && !newThumb.equals(oldThumb)) {
      oldFixes=numFixes;
      numFixes++;
    }

    %><p>Mark <%=mark%> changed thumb <ul>
      <li><%=oldThumb%></li>
      <li><%=newThumb%></li>
    </ul></p><%

    if ((numFixes > oldFixes) && committing) myShepherd.updateDBTransaction();

  }
}
catch(Exception e){
	myShepherd.rollbackDBTransaction();
}
finally{
	myShepherd.closeDBTransaction();

}

%>

</ul>
<p>Done successfully: <%=numFixes %> fixes</p>
</body>
</html>
