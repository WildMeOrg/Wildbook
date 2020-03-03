<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.ia.*,java.util.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%!

public static List<Task> getTasksFor(Annotation ann, Shepherd myShepherd) {
    String qstr = "SELECT FROM org.ecocean.ia.Task WHERE objectAnnotations.contains(obj) && obj.id == \"" + ann.getId() + "\" VARIABLES org.ecocean.Annotation obj";
    Query query = myShepherd.getPM().newQuery(qstr);
    query.setIgnoreCache(true);
    query.setOrdering("created");
    Collection c=(Collection)=query.execute();
    ArrayList<Task> listy=new ArrayList<Task>(c);
    query.closeAll();
    return listy;
}

%>

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

Annotation annot=myShepherd.getAnnotation("c41b8651-01be-403b-83f5-56e7fc212609");

List<Task> tasks=getTasksFor(annot, myShepherd);
for (Task t:tasks){
	%>
	<p>Task ID: <%=t.getId() %></p>
	<%
}

try {
	


	
}
catch(Exception e){
	e.printStackTrace();
}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();

}

%>



</body>
</html>
