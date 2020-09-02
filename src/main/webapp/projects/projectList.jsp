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
User currentUser = AccessControl.getUser(request, myShepherd);
%>

  <jsp:include page="../header.jsp" flush="true"/>
  <link rel="stylesheet" href="<%=urlLoc %>/cust/mantamatcher/css/manta.css"/>
  <title>Project List for <%=currentUser.getDisplayName()%></title>
    <div class="container maincontent" align="center">
      <div class="flexbox">
        <h2 class="flex-left-justify">Projects for <%=currentUser.getDisplayName()%></h2>
        <a href="<%=urlLoc%>/projects/createProject.jsp"><button type="button" name="button" class="flex-right-justify">Add Project <span class="glyphicon glyphicon-plus"></span></button></a>
      </div>

          <%
          try{
              if(currentUser != null){
                List<Project> userProjects = myShepherd.getProjectsForUserId(currentUser.getId());
                
                if(userProjects==null || userProjects.size()<1){
                  %>
                  <h4>You don't have any projects yet</h4>
                  <%
                }else{
                  %>
                  <table class="row clickable-row hoverRow tissueSampleProjectList">
                  	<thead>
                        <tr>
                          <th class="tissueSampleProjectList">Project Name</th>
                          <th class="tissueSampleProjectList">Percent Annotations Identified</th>
                          <th class="tissueSampleProjectList">Number of Encounters</th>
                        </tr>
                    </thead>
                    <tbody>
                  <%
                  for(int j=0; j<userProjects.size(); j++){
                    if(userProjects.size()>0){
                      %>
                        <tr onclick="window.location='<%=urlLoc%>/projects/project.jsp?id=<%=userProjects.get(j).getId()%>'" class="tissueSampleProjectList">
                          <td class="clickable-row"><%=userProjects.get(j).getResearchProjectName()%></td>
                          <td class="clickable-row"><%=userProjects.get(j).getPercentWithIncrementalIds(myShepherd)%> %</td>
                          <td class="clickable-row"><%=userProjects.get(j).getEncounters().size()%></td>
                        </tr>
                      <%
                    }
                  }
                }
              }
          }
          catch(Exception e){
          	e.printStackTrace();
          }
          %>
      </tbody>
  </table>
</div>
<jsp:include page="../footer.jsp" flush="true"/>
