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
  Project project = myShepherd.getProject(projId);
  List<Encounter> encounters = project.getEncounters();
%>
<style type="text/css">
  .disabled-btn { /* moving this to _encounter-pages.less AND moving that beneath buttons custom import in manta.less did not work. */
    background:#62676d30;
    border:0;
    color:#fff;
    line-height:2em;
    padding:7px 13px;
    font-weight:300;
    vertical-align:middle;
    margin-right:10px;
    margin-top:15px
  }
</style>

<jsp:include page="../header.jsp" flush="true"/>
  <link rel="stylesheet" href="<%=urlLoc %>/cust/mantamatcher/css/manta.css"/>
    <title>Project <%=projId%></title>
    <div class="container maincontent">
      <h3>Project: <%=project.getResearchProjectName()%></h3>
          <%
          try{
            if(currentUser != null){
              if(encounters == null || encounters.size()<1){
                %>
                  <h4>You don't have any encounters in this project yet</h4>
                <%
              }else{
                %>
                <div align="center">
                  <div id="progress-div">
                    <h4>Encounters are loading. This may take several minutes...</h4>
                    <div class="progress">
                      <div class="progress-bar progress-bar-striped active" role="progressbar" aria-valuenow="50" aria-valuemin="0" aria-valuemax="100" style="width: 50%">
                        <span class="sr-only">~50% Complete</span>
                      </div>
                    </div>
                  </div>
                  <div id="table-div" style="display: none;">
                    <table class="row project-style">
                      <thead>
                        <tr>
                          <th class="project-style">Encounter</th>
                          <th class="project-style">Individual</th>
                          <th class="project-style">Date/Time</th>
                          <th class="project-style">Location</th>
                          <th class="project-style">Data Owner</th>
                          <th class="project-style">Project IDs</th>
                          <th class="project-style">Actions</th>
                        </tr>
                      </thead>
                      <tbody id="encounterList">
                        <!--populated by JS after page load-->
                      </tbody>
          <%
              } // end if encounters + else
            } // end if currentUser
          } catch (Exception e) {
            e.printStackTrace();
          } // end try block
          finally{
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
          }
          %>
              </table>
            </div>
          </div>
    </div>
<jsp:include page="../footer.jsp" flush="true"/>

<script type="text/javascript">

function markNewIncremental(individualId, projectId, encounterId){
  console.log("markNewIncremental entered");
  disableNewButton(encounterId);
  $('#adding-div_' + encounterId).show();
  if(individualId && projectId && encounterId){
    // console.log("projectId is " + projectId);
    // console.log("individualId is " + individualId);
    addIncrementalProjectIdAjax(individualId, projectId, encounterId);
  }
}

function createIndividualAndMarkNewIncremental(encounterId, projectId){
  console.log("createIndividualAndMarkNewIncremental entered!");
  disableNewButton(encounterId);
  $('#adding-div_' + encounterId).show();
  if(projectId && encounterId){
    // console.log("projectId is " + projectId);
    // console.log("encounterId is " + encounterId);
    createMarkedIndividualAjax(projectId, encounterId);
  }
}

function createMarkedIndividualAjax(projectId, encounterId){
  console.log("createMarkedIndividualAjax entered");
  let formJson = {};
  formJson["projectId"] = projectId;
  formJson["encounterId"] = encounterId;
  console.log("form JSON");
  console.log(JSON.stringify(formJson));
  $.ajax({
    url: wildbookGlobals.baseUrl + '../IndividualCreateForProject',
    type: 'POST',
    data: JSON.stringify(formJson),
    dataType: 'json',
    contentType : 'application/json',
    success: function(data){
      if(data){
        console.log(data);
        if(data.success){
          console.log("success!");
          let newIndividualId = data.newIndividualId;
          addIncrementalProjectIdAjax(newIndividualId, projectId, encounterId);
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

function addIncrementalProjectIdAjax(individualId, projectId, encounterId){
  let formJson = {};
  formJson["researchProjectId"] = projectId;
  formJson["individualId"] = individualId;
  console.log("form JSON in addIncrementalProjectIdAjax");
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
          $('#adding-div_' + encounterId).hide();
          $('#alert-div_'+encounterId).show();
          $('#mark-new-button_'+encounterId).hide();
          $('#disabled-mark-new-button_'+encounterId).hide();
          //TODO getEncounterJSON()?
        }else{
          console.log("failure!");
          $('#adding-div_' + encounterId).hide();
          $('#alert-div-warn_'+encounterId).show();
          enableNewButton(encounterId);
        }
      }
    },
    error: function(x,y,z) {
      console.warn('%o %o %o', x, y, z);
    }
  });
}

function disableNewButton(encounterId){
  console.log("disableNewButton called with encounterId of " + encounterId);
  $('#mark-new-button_' + encounterId).hide();
  $('#disabled-mark-new-button_' + encounterId).show();
}

function enableNewButton(encounterId){
  console.log("enableNewButton called with encounterId of " + encounterId);
  $('#disabled-mark-new-button_' + encounterId).hide();
  $('#mark-new-button_' + encounterId).show();
}

function dismissAlert(encounterId){
  console.log("dismissAlert clicked");
  if(encounterId){
    console.log("encounterId is "+ encounterId);
    $('#'+'alert-div_'+ encounterId).hide();
    $('#'+'alert-div-warn_'+encounterId).hide();
  }
}

function getEncounterJSON() {
  console.log('Current projectUUID : <%=projId%>');
  let projectUUID = '<%=projId%>';
  console.log("SENDING projectUUID: "+projectUUID);
  let requestJSON = {};
  requestJSON['projectUUID'] = projectUUID;
  requestJSON['getEncounterMetadata'] = "true";
  console.log("here!");
  console.log("all requestJSON: "+JSON.stringify(requestJSON));

  let responseJSON = {};

  $.ajax({
      url: wildbookGlobals.baseUrl + '../ProjectGet',
      type: 'POST',
      data: JSON.stringify(requestJSON),
      dataType: 'json',
      contentType: 'application/json',
      success: function(d) {
          console.log("literal response: "+d.projects);
          console.info('Success in ProjectGet retrieving data! Got back '+JSON.stringify(d));
          $("#encounterList").empty();
          let projectsArr = d.projects;
          for (let i=0;i<projectsArr.length;i++) {
              let thisProject = projectsArr[i];
              for (let j=0;j<thisProject.encounters.length;j++) {
                let projectHTML = projectHTMLForTable(projectsArr[i].encounters[j])
                $("#encounterList").append(projectHTML);
                console.log("appending!!");
              }
          }
          $('#progress-div').hide();
          $('#table-div').show();
      },
      error: function(x,y,z) {
          console.warn('%o %o %o', x, y, z);
      }
  });
}

function projectHTMLForTable(json) {

  let encounterId = json.encounterId;
  let individualDisplayName = json.individualDisplayName;
  let individualUUID = json.individualUUID;
  let hasNameKeyMatchingProject = json.hasNameKeyMatchingProject;
  let encounterDate = json.encounterDate;
  let locationId = json.locationId;
  let submitterId = json.submitterId;
  //let allProjectIds = json.allProjectIds;

  console.log("THIS ENCOUNTER JSON: "+JSON.stringify(json));

  let projectHTML = '';
  projectHTML += '<tr id="enc-'+encounterId+'">';
  projectHTML +=  '<td class="project-style">'+encounterId+'</td>';
  projectHTML +=  '<td class="project-style">'+individualDisplayName+' </td>';
  projectHTML +=  '<td class="project-style">'+encounterDate+' </td>';
  projectHTML +=  '<td class="project-style">'+locationId+' </td>';
  projectHTML +=  '<td class="project-style">'+submitterId+' </td>';
  projectHTML +=  '<td class="project-style">'+individualDisplayName+' </td>';
  //projectHTML +=  '<td class="project-style">';
  // if (allProjectIds) {
  //   for (i=0;i<allProjectIds.length;i++) {
  //     projectHTML += (allProjectIds[i]+" ");
  //   }
  // }  else {
  //   projectHTML += "(None)";
  // }
  //projectHTML +=  '</td>';
  projectHTML +=  '<td class="project-style">';
  projectHTML +=  '<button type="button">Project Match</button>';
  projectHTML +=  '</br>';

  // grr.. not worth an AJAX call for just this. one more key and i'm doin it though
  let researchProjectId = '<%= project.getResearchProjectId()%>';

  if(!hasNameKeyMatchingProject){
    if (individualDisplayName!=null&&individualDisplayName!="") {
      projectHTML += '<button id="mark-new-button_'+encounterId+'" type="button" onclick="markNewIncremental(\''+individualUUID+'\', \''+researchProjectId+'\', \''+encounterId+'\')">Mark New</button>';
      projectHTML += '<button class="disabled-btn" id="disabled-mark-new-button_'+encounterId+'" style="display: none;">Mark New</button>';
    } else {
      projectHTML += '<button id="mark-new-button_'+encounterId+'" type="button" onclick="createIndividualAndMarkNewIncremental(\''+encounterId+'\', \''+researchProjectId+'\')">Mark New</button>';
      projectHTML += '<button class="disabled-btn" id="disabled-mark-new-button_'+encounterId+'" style="display: none;">Mark New</button>';
    }
  }
  projectHTML += '<div id="adding-div_'+encounterId+'" class="alert alert-info" role="alert" style="display: none;">Assigning individual to project... Please Wait for Confirmation.</div>';
  projectHTML += '<div id="alert-div_'+encounterId+'" class="alert alert-success" role="alert" style="display: none;">';
  projectHTML += '<button type="button" class="close" onclick="dismissAlert(\''+encounterId+'\')" aria-label="Close"><span aria-hidden="true">&times;</span></button>';
  projectHTML += '<strong>Success!</strong> An ID has been added to your project for this individual!';
  projectHTML += '</div>';
  projectHTML += '<div id="alert-div-warn_'+encounterId+'" class="alert alert-danger" role="alert" style="display: none;">';
  projectHTML += '<button type="button" class="close" onclick="dismissAlert(\''+encounterId+'\')" aria-label="Close"><span aria-hidden="true">&times;</span></button>';
  projectHTML += 'An ID could not be added to your project for this individual!';
  projectHTML += '</div>';
  projectHTML +=  '</td>';
  projectHTML += '</tr>';
  return projectHTML;
}

$(document).ready( function() {
  getEncounterJSON();
});
</script>
