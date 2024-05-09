<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,org.ecocean.media.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);



%>

<html>
<head>
<title>Update Wildbook User Email Hash</title>

</head>


<body>

<ul>
<%

myShepherd.beginDBTransaction();
try{
	
    List<User> users=null;
    String filter="SELECT FROM org.ecocean.User";  
    Query query=myShepherd.getPM().newQuery(filter);
    Collection c = (Collection) (query.execute());
    users=new ArrayList<User>(c);
    query.closeAll();
    int numUsers=users.size();
    for(int i=0;i<numUsers;i++){
    	User user=users.get(i);
    	if(user.getEmailAddress()!=null){
	    	user.setEmailAddress(user.getEmailAddress());
	    	myShepherd.commitDBTransaction();
	    	myShepherd.beginDBTransaction();
	    	%>
	    	<li>Set email hash for: <%=user.getEmailAddress() %></li>
	    	<%
    	}

    }


}
catch(Exception e){
	myShepherd.rollbackDBTransaction();
	%>
	<p>Reported error: <%=e.getMessage() %> <%=e.getStackTrace().toString() %></p>
	<%
	e.printStackTrace();
}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();

}

%>

</ul>

</body>
</html>
