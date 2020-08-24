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
        <a href="<%=urlLoc%>/projects/createProject.jsp"><button type="button" name="button" class="flex-right-justify">Add Project</button></a>
      </div>
      <table class="row tissueSample">
      	<thead>
            <tr>
              <th class="tissueSample">Project Name</th>
              <th class="tissueSample">Percent Annotations Identified</th>
              <th class="tissueSample">Number of Encounters</th>
            </tr>
        </thead>
        <tbody>
          <%
          try{
              if(currentUser != null){
                List<Project> userProjects = myShepherd.getProjectsForUserId(currentUser.getId());
                if(userProjects.size()<1){
                  %>
                  <tr>
                    <td> You don't have any projects yet</td>
                  </tr>
                  <%
                }else{
                  for(int j=0; j<userProjects.size(); j++){
                    if(userProjects.size()>0){
                      %>

                        <tr>
                          <td class="tissueSample"><a href="<%=urlLoc%>/projects/project.jsp?id=<%=userProjects.get(j).getId()%>"> <%=userProjects.get(j).getResearchProjectName()%></a></td>
                          <td class="tissueSample">%<%=userProjects.get(j).getPercentIdentified()%></td>
                          <td class="tissueSample"><%=userProjects.get(j).getEncounters().size()%></td>
                        </tr>
                      <%
                    }
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
<jsp:include page="../footer.jsp" flush="true"/>
