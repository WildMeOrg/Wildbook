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

// pg_dump -Ft sharks > sharks.out

//pg_restore -d sharks2 /home/webadmin/sharks.out


%>

<html>
<head>
<title>Fix Some Fields</title>

</head>


<body>
<p>Removing all workspaces.</p>
<ul>
<%

myShepherd.beginDBTransaction();

//build queries

int numFixes=0;

try {

	String rootDir = getServletContext().getRealPath("/");
	String baseDir = ServletUtilities.dataDir(context, rootDir).replaceAll("dev_data_dir", "caribwhale_data_dir");

  Iterator allSpaces=myShepherd.getAllWorkspaces();

  boolean committing=false;


  while(allSpaces.hasNext()){

    Workspace wSpace=(Workspace)allSpaces.next()

    %><p>Workspace <%=wSpace.getID()%> with owner <%=wSpace.getOwner()%> is deleted<%

  	numFixes++;

    if (committing) {
  		myShepherd.commitDBTransaction();
  		myShepherd.beginDBTransaction();
    }
  }
}
catch (Exception ex) {

	System.out.println("!!!An error occurred on page fixSomeFields.jsp. The error was:");
	ex.printStackTrace();
	myShepherd.rollbackDBTransaction();


}
finally{

	myShepherd.closeDBTransaction();
	myShepherd=null;
}
%>

</ul>
<p>Done successfully: <%=numFixes %> workspaces deleted.</p>
</body>
</html>
