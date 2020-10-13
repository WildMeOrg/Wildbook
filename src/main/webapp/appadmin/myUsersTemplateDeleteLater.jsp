<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
org.ecocean.servlet.ServletUtilities,
java.io.*,java.util.*,
java.io.FileInputStream,
java.io.File,
java.io.FileNotFoundException,
org.ecocean.*,
org.ecocean.servlet.*,
javax.jdo.*,
java.lang.StringBuffer,
java.util.Vector,
java.util.Iterator,
java.lang.NumberFormatException"%>

<%
String context="context0";
context=ServletUtilities.getContext(request);
Shepherd myShepherd=new Shepherd(context);
int numFixes=0;
String urlLoc = "//" + CommonConfiguration.getURLLocation(request);
%>

<html>
  <link rel="stylesheet" href="<%=urlLoc %>/cust/mantamatcher/css/manta.css"/>
  <head>
    <title>Users we think belong to you</title>
  </head>
  <body>
    <div class="container" align="center">
      <h2 class="row">Here are the users in our database that we believe are associated with your account:</h2>
      <table class="row tissueSample">

	<thead>
      <tr>
        <th class="tissueSample">User ID</th>
        <th class="tissueSample">Username</th>
        <th class="tissueSample">Full Name</th>
        <th class="tissueSample">Email Address</th>
      </tr>
  </thead>
  <tbody>
    <%
    try{
        User currentUser = AccessControl.getUser(request, myShepherd);
        System.out.println("current user is: " + currentUser.toString());
        if(currentUser != null){
          List<User> similarUsers = UserConsolidate.getSimilarUsers(currentUser, myShepherd.getPM());
          for(int j=0; j<similarUsers.size(); j++){
            if(similarUsers.size()>0){
              %>
              <tr>
                <td class="tissueSample"><%=similarUsers.get(j).getUUID()%></td>
                <td class="tissueSample"><%=similarUsers.get(j).getUsername()%></td>
                <td class="tissueSample"><%=similarUsers.get(j).getFullName()%></td>
                <td class="tissueSample"><%=similarUsers.get(j).getEmailAddress()%></td>
              </tr>
              <%
            }
          }
        }
    }
    catch(Exception e){
    	// myShepherd.rollbackDBTransaction();
    }
    finally{
    	// myShepherd.closeDBTransaction();
    }
    %>
    </tbody>
  </table>
  </div>
  </body>
</html>
