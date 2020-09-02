<%@ page contentType="text/html; charset=utf-8" language="java"
         import ="org.ecocean.servlet.ServletUtilities,
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
  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("project.jsp");
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
  pageContext.setAttribute("projId", projId);
  boolean proceed = true;
  boolean haveRendered = false;
  Properties collabProps = new Properties();
  String urlLoc = "//" + CommonConfiguration.getURLLocation(request);
  collabProps=ShepherdProperties.getProperties("collaboration.properties", langCode, context);
  User currentUser = AccessControl.getUser(request, myShepherd);
%>
<jsp:include page="../header.jsp" flush="true"/>
  <link rel="stylesheet" href="<%=urlLoc %>/cust/mantamatcher/css/manta.css"/>
    <title>Project <%=projId%></title>
    <%
      System.out.println("projectId is: " + projId);
      Project project = myShepherd.getProject(projId);
      List<Encounter> encounters = project.getEncounters();
      System.out.println("project acquired! It is:");
      System.out.println(project.toString());
    %>
    <div class="container maincontent">
          <%
          try{
            if(currentUser != null){
              System.out.println("projectname is " + project.getResearchProjectName());
              %>
              <h3>Project: <%=project.getResearchProjectName()%></h3>
              <%
              System.out.println("got here");
              if(encounters == null || encounters.size()<1){
                System.out.println("got here null or empty");
                %>
                  <h4>You don't have any encounters in this project yet</h4>
                <%
              }else{
                System.out.println("not null");
                %>
                <div align="center">
                  <table class="row tissueSampleProjectList">
                    <thead>
                      <tr>
                        <th class="tissueSampleProjectList">Encounter</th>
                        <th class="tissueSampleProjectList">Individual</th>
                        <th class="tissueSampleProjectList">Date/Time</th>
                        <th class="tissueSampleProjectList">Location</th>
                        <th class="tissueSampleProjectList">Data Owner</th>
                        <th class="tissueSampleProjectList">Project IDs</th>
                        <th class="tissueSampleProjectList">Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                <%
                if(encounters!=null && encounters.size()>0){
                  System.out.println("not null again");
                  for(int i=0; i<encounters.size(); i++){
                    %>
                    <tr>
                    <%
                    String location = "";
                    if(encounters.get(i).getLocationID() != null){
                      System.out.println("locationID is not null");
                      location = encounters.get(i).getLocationID();
                    }
                    String individualDisplayName = "";
                    if(encounters.get(i).getIndividual() != null){
                      individualDisplayName = encounters.get(i).getIndividual().getDisplayName();
                    }
                    String dataOwner = "";
                    if(encounters.get(i).getAssignedUsername() !=null){
                      dataOwner = encounters.get(i).getAssignedUsername();
                    }
                    String encounterDate = "";
                    if(encounters.get(i).getDate()!= null){
                      encounterDate = encounters.get(i).getDate();
                    }
                    %>
                    <td class="tissueSampleProjectList"><%=encounters.get(i).getCatalogNumber()%></td>
                    <td class="tissueSampleProjectList"><%=individualDisplayName%></td>
                    <td class="tissueSampleProjectList"><%=encounterDate%></td>
                    <td class="tissueSampleProjectList"><%=location%></td>
                    <td class="tissueSampleProjectList"><%=dataOwner%></td>
                    <td class="tissueSampleProjectList">
                    <%
                      List<String> researchProjectIds = myShepherd.getResearchProjectIdsForEncounter(encounters.get(i));
                      for(int j=0; j<researchProjectIds.size(); j++){
                        %>
                        <%= researchProjectIds.get(j) %>
                        <%
                      }
                      %>
                    </td>
                    <td class="tissueSampleProjectList">
                      <button type="button">Project Match</button>
                      </br>
                      <button type="button" onclick="markNew('<%= encounters.get(i).getCatalogNumber()%>')">Mark New</button>
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
          </div>
    </div>
<jsp:include page="../footer.jsp" flush="true"/>

<script type="text/javascript">

function markNew(encounterCatalogNumber){
  console.log("markNew clicked!");
  // console.log("encounterCatalogNumber is " + encounterCatalogNumber);
  // let projectId = "<%= project.getResearchProjectId()%>";
  // console.log("projectId is: " + projectId);

  // let formJson = {};
  // formJson["organizationAccess"] = [];
  //TODO
  // let formDataArray = $("#add-encounter-to-project-form").serializeArray();
  // let formJson = {};
  // formJson["projects"] = [];
  // for(i=0; i<formDataArray.length; i++){
  //   let currentName = formDataArray[i].name;
  //   if (currentName === "id"){
  //     let currentProjId = formDataArray[i].value;
  //     formJson = constructProjectObjJsonFromIdAndAddToJsonArray(currentProjId, formJson);
  //   }else{
  //     console.log("ack I shouldn't get here!!!!!!!!!!!!!!!!!!");
  //   }
  // }
  // for(i=0; i<formDataArray.length; i++){
  //   if (Object.values(formDataArray[i])[0] === "projectUserIds"){
  //     formDataArray[i].value=uuidsOnAccessList;
  //   }
  //   let currentName = formDataArray[i].name;
  //   if (Object.values(formDataArray[i])[0] === "organizationAccess"){
  //     formJson[currentName].push(formDataArray[i].value);
  //   }else{
  //     formJson[currentName]=formDataArray[i].value;
  //   }
  // }
  // console.log("form JSON");
  // console.log(JSON.stringify(formJson));
  // $.ajax({
  //   url: wildbookGlobals.baseUrl + '../IndividualAddIncrementalProjectId',
  //   type: 'POST',
  //   data: JSON.stringify(formJson),
  //   dataType: 'json',
  //   contentType : 'application/json',
  //   success: function(data){
  //     console.log(data);
  //     if(data.newProjectUUID){
  //       window.location.replace('/projects/project.jsp?id='+data.newProjectUUID);
  //     }else{
  //       console.log("project id dne in response");
  //     }
  //     // window.location.replace('standard-upload?filename='+filename+"&isUserUpload=true");
  //   },
  //   error: function(x,y,z) {
  //     console.warn('%o %o %o', x, y, z);
  //   }
  // });
}
</script>
