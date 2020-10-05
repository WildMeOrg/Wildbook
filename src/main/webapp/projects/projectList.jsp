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
myShepherd.setAction("projectList.jsp");
myShepherd.beginDBTransaction();
Properties props = new Properties();
String langCode = ServletUtilities.getLanguageCode(request);
props = ShepherdProperties.getProperties("projectList.properties", langCode, context);

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
                List<Project> allProjects = new ArrayList<Project>();
                List<Project> userProjects = myShepherd.getOwnedProjectsForUserId(currentUser.getId(), "researchProjectName");
                List<Project> projectsUserBelongsTo = myShepherd.getParticipatingProjectsForUserId(currentUser.getUsername());
                if(userProjects != null && userProjects.size()>0){
                  for(int i=0; i<userProjects.size(); i++){
                    if(!allProjects.contains(userProjects.get(i))){ //avoid duplicates
                      allProjects.add(userProjects.get(i));
                    }
                  }
                }
                if(projectsUserBelongsTo != null && projectsUserBelongsTo.size()>0){
                  for(int i=0; i<projectsUserBelongsTo.size(); i++){
                    if(!allProjects.contains(projectsUserBelongsTo.get(i))){ //avoid duplicates
                      allProjects.add(projectsUserBelongsTo.get(i));
                    }
                  }
                }
                userProjects = allProjects;

                if(userProjects==null || userProjects.size()<1){
                  %>
                  <h4><%=props.getProperty("NoProjects") %></h4>
                  <%
                }else{
                  %>
                  <table class="row clickable-row hoverRow project-style">
                  	<thead>
                        <tr>
                          <th class="project-style"><%=props.getProperty("ProjectName") %></th>
                          <th class="project-style"><%=props.getProperty("PercentAnnotations") %></th>
                          <th class="project-style"><%=props.getProperty("NumEncounters") %></th>
                        </tr>
                    </thead>
                    <tbody>
                  <%
                  for(int j=0; j<userProjects.size(); j++){
                    if(userProjects.size()>0){
                      %>
                        <tr onclick="window.location='<%=urlLoc%>/projects/project.jsp?id=<%=userProjects.get(j).getId()%>'" class="project-style">
                          <td class="clickable-row"><%=userProjects.get(j).getResearchProjectName()%></td>
                          <td class="clickable-row"><%=userProjects.get(j).getPercentWithIncrementalIds()%> %</td>
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
          finally{
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
          }
          %>
      </tbody>
  </table>
</div>
<jsp:include page="../footer.jsp" flush="true"/>
