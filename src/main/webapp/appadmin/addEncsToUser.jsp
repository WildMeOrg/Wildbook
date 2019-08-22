<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
org.ecocean.datacollection.*,
java.util.*,
org.ecocean.media.*,
org.ecocean.*,
javax.jdo.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%
String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("addEncsToUser.jsp");
String orgName = request.getParameter("orgName");
String commit = request.getParameter("commit");
String limitStr = request.getParameter("limit");
String username = "ORP";
if (request.getParameter("username")!=null) username = request.getParameter("username");  
String filter = "this.submitterOrganization == 'Olive Ridley Project' ";
if (orgName!=null) {
    filter = "this.submitterOrganization == '"+orgName+"' ";
}
Collection c = null;
List<Encounter> encs = null;
try {
    Extent encClass = myShepherd.getPM().getExtent(Encounter.class, true);
    Query acceptedEncounters = myShepherd.getPM().newQuery(encClass, filter);
    c = (Collection) (acceptedEncounters.execute());
    encs = new ArrayList<>(c);          
    //int size=c.size();
    acceptedEncounters.closeAll();

} catch (Exception e) {
    System.out.println("Exception retrieving encounters...");
    e.printStackTrace();
} 

System.out.println("-------------------> Specified organization is "+orgName+" commit="+commit);
System.out.println("-----------------> Got "+encs.size()+" encs as candidates...");


%>

<html>
<head>
<title>Add all Encounters with Organization to User </title>

</head>

<body>
<ul>
<%

//int limit = 0;
int count = 0;
try {
    User u = myShepherd.getUser(username);
    System.out.println("Got user?  name = "+username);
    if ("true".equals(commit)&&u!=null) {
        System.out.println("limit = "+limitStr);
        for (Encounter enc : encs) {
            if (limitStr!=null&&count<Integer.valueOf(limitStr)) {
                enc.addComments("<p><em>Encounter included in batch assignment to user <b>"+username+"</b></em></p>");
                enc.setSubmitterID(username);
                System.out.println("Success! enc.getSubmitterID() == "+enc.getSubmitterID()+" on encounter # "+enc.getCatalogNumber());
            } else {
                break;
            } 
            count++;
            System.out.println("count = "+count);
        }
        myShepherd.commitDBTransaction();
    } else {
        System.out.println("Commit was false or user was null. User = "+u);
    }
} catch (Exception e){
	myShepherd.rollbackDBTransaction();
	e.printStackTrace();
}

myShepherd.closeDBTransaction();
%>

</ul>

</body>
</html>
