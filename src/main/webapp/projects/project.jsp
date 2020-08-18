<%@ page contentType="text/html; charset=utf-8" language="java"
         org.ecocean.servlet.ServletUtilities,
         com.drew.imaging.jpeg.JpegMetadataReader,
         com.drew.metadata.Directory,
         org.ecocean.*,
         java.util.regex.Pattern,
         org.ecocean.servlet.ServletUtilities,
         org.json.JSONObject,
         org.json.JSONArray,
         javax.jdo.Extent, javax.jdo.Query,
         java.io.File, java.text.DecimalFormat,
         org.apache.commons.lang.StringEscapeUtils,
         java.util.*,org.ecocean.security.Collaboration" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>

<%
  String context="context0";
  context=ServletUtilities.getContext(request);
  String projId = request.getParameter("id").replaceAll("\\+", "").trim();
  String rootWebappPath = getServletContext().getRealPath("/");
  File webappsDir = new File(rootWebappPath).getParentFile();
  File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
  File projectsDir=new File(shepherdDataDir.getAbsolutePath()+"/projects");
  File projectDir = new File(projectsDir, projId);
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility
  String langCode=ServletUtilities.getLanguageCode(request);
  pageContext.setAttribute("num", num);
  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("project.jsp1");
  boolean proceed = true;
  boolean haveRendered = false;
  Properties collabProps = new Properties();
  collabProps=ShepherdProperties.getProperties("collaboration.properties", langCode, context);
%>
<html>
  <link rel="stylesheet" href="<%=urlLoc %>/cust/mantamatcher/css/manta.css"/>
  <head>
    <title>Project <%=currentUser.getDisplayName()%></title>
  </head>
  <body>
    <jsp:include page="../header.jsp" flush="true"/>
    <%
      System.out.println("projectId is: " + projId);
      Project project = myShepherd.getProject(projId);
      List<Encounter> encounters = project.getEncounters();
      System.out.println("project acquired! It is:");
      System.out.println(project.toString());
    %>
    <table class="row tissueSample">
      <thead>
          <tr>
            <th class="tissueSample">Encounter</th>
            <th class="tissueSample">Individual</th>
            <th class="tissueSample">Location</th>
            <th class="tissueSample">Project ID</th>
            <th class="tissueSample">Actions</th>
          </tr>
      </thead>
      <tbody>
          <%
          try{
              if(currentUser != null){
                if(encounters.size()<1){
                  %>
                  <tr>
                    <td> You don't have any encounters in this project yet</td>
                  </tr>
                  <%
                }else{
                  for(int i=0; i<encounters.size(); i++){
                    if(userProjects.size()>0){
                      %>
                        <tr>
                          <td class="tissueSample"><%=encounters.get(i).getCatalogNumber()%></td>
                          <td class="tissueSample">%<%=encounters.get(i).getIndividual().getDisplayName()%></td>
                          <td class="tissueSample"><%=encounters.get(i).getLocationID()%></td>
                          <td class="tissueSample"><%=encounters.get(i).getProjectId()%></td>
                          <td class="tissueSample">
                            <button>Project Match</button>
                            <button>Mark New</button>
                          </td>
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
          }
          %>
      </tbody>
  </table>
    <jsp:include page="../footer.jsp" flush="true"/>
  </body>
</html>
