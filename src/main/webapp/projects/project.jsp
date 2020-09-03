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
                      // System.out.println("locationID is not null");
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
                      <%
                        Encounter currentEncounter = encounters.get(i);
                        System.out.println("currentEncoutner is: " + currentEncounter.toString());
                        MarkedIndividual currentIndividual = myShepherd.getMarkedIndividual(currentEncounter);
                        ArrayList<String> nameKeys = (ArrayList<String>) currentIndividual.getNameKeys();
                        System.out.println("nameKeys is: " + nameKeys.toString());
                        if (nameKeys.contains(project.getResearchProjectId())) {
                          System.out.println("nameKeys contains research project ID for " + currentIndividual.getIndividualID());
                        }
                        System.out.println("currentIndividual is: " + currentIndividual.toString());
                        if(currentIndividual != null){
                          // List<String> foundNameIds = currentIndividual.findNameIds(project.getResearchProjectId());
                          // System.out.println("foundNameIds is: " + foundNameIds.toString());
                          if(!currentIndividual.hasNameKey(project.getResearchProjectId())){
                            System.out.println("catalog number actually is "+ encounters.get(i).getCatalogNumber());
                            %>
                            <button id="mark-new-button_<%= encounters.get(i).getCatalogNumber()%>" type="button" onclick="markNewIncremental('<%= currentIndividual.getIndividualID()%>', '<%= project.getResearchProjectId()%>', '<%= encounters.get(i).getCatalogNumber()%>')">Mark New</button>
                            <%
                          }
                        }else{
                          %>
                          <button type="button" onclick="createIndividualAndMarkNewIncremental('<%= encounters.get(i).getCatalogNumber()%>', '<%= project.getResearchProjectId()%>')">Mark New</button>
                          <%
                        }
                      %>
                      <div id="alert-div_<%= encounters.get(i).getCatalogNumber()%>" class="alert alert-success" role="alert" style="display: none;">
                        <button type="button" class="close" onclick="dismissAlert('<%= encounters.get(i).getCatalogNumber()%>')" aria-label="Close"><span aria-hidden="true">&times;</span></button>
                        <strong>Success!</strong> An ID has been added to your project for this individual!
                      </div>
                      <div id="alert-div-warn_<%= encounters.get(i).getCatalogNumber()%>" class="alert alert-danger" role="alert" style="display: none;">
                        <button type="button" class="close" onclick="dismissAlert('<%= encounters.get(i).getCatalogNumber()%>')" aria-label="Close"><span aria-hidden="true">&times;</span></button>
                        An ID could not be added to your project for this individual!
                      </div>
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

function markNewIncremental(individualId, projectId, encounterId){
  console.log("markNewIncremental entered");
  if(individualId && projectId && encounterId){
    console.log("projectId is " + projectId);
    console.log("individualId is " + individualId);
    doAjaxCall(individualId, projectId, encounterId);
  }
}

function createIndividualAndMarkNewIncremental(encounterId, projectId){
  console.log("createIndividualAndMarkNewIncremental entered!");
  if(projectId && encounterId){
    console.log("projectId is " + projectId);
    console.log("encounterId is " + encounterId);
    //TODO create individual and get individualID... probably from ajax call result
    //TODO doAjaxCall(individualId, projectId, encounterId);
  }
}

function doAjaxCall(individualId, projectId, encounterId){
  let formJson = {};
  formJson["researchProjectId"] = projectId;
  formJson["individualId"] = individualId;
  console.log("form JSON");
  console.log(JSON.stringify(formJson));
  $.ajax({
    url: wildbookGlobals.baseUrl + '../IndividualAddIncrementalProjectId',
    type: 'POST',
    data: JSON.stringify(formJson),
    dataType: 'json',
    contentType : 'application/json',
    success: function(data){
      if(data){
        console.log(data);
        if(data.success){
          console.log("success!");
          $('#alert-div_'+encounterId).show();
          $('#mark-new-button_'+encounterId).hide();
        }else{
          console.log("failure!");
          $('#alert-div-warn_'+encounterId).show();
        }
      }
    },
    error: function(x,y,z) {
      console.warn('%o %o %o', x, y, z);
    }
  });
}

function dismissAlert(encounterId){
  console.log("dismissAlert clicked");
  if(encounterId){
    console.log("encounterId is "+ encounterId);
    $('#'+'alert-div_'+ encounterId).hide();
    $('#'+'alert-div-warn_'+encounterId).hide();
  }
}

</script>
