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
      <h2 class="row">Projects</h2>
      <button type="button" name="button">Add Project</button>
      <table class="row tissueSample">

	<thead>
      <tr>
        <th class="tissueSample">Project Name</th>
        <th class="tissueSample">Percent Annotations Identified</th>
        <th class="tissueSample">Number of Individuals</th>
      </tr>
  </thead>
  <tbody>
    <%
    try{
        User currentUser = AccessControl.getUser(request, myShepherd);
        if(currentUser != null){
          List<Project> userProjects = Project.getProjectsForUser(currentUser);
          for(int j=0; j<userProjects.size(); j++){
            if(userProjects.size()>0){
              %>
              <tr onclick="navToProj(userProjects.get(j))">
                <td class="tissueSample"><%=userProjects.get(j).getResearchProjectName()%></td>
                <td class="tissueSample">%<%=userProjects.get(j).getPercentIdentified()%></td>
                <td class="tissueSample"><%=userProjects.get(j).getNumberOfIndividuals()%></td>
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

  <script>
    function navToProj(Project project){
      console.log("got into navToProj");
      console.log("current proj is:");
      console.log(project);
    }
  </script>
  </body>
</html>
