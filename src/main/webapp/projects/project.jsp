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
         java.util.*,org.ecocean.security.Collaboration" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>

<%
  String context="context0";
  context=ServletUtilities.getContext(request);
  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("project.jsp");
  Project project = null;
  User currentUser = null;
  myShepherd.beginDBTransaction();
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
  Properties projectProps = new Properties();
  String urlLoc = "//" + CommonConfiguration.getURLLocation(request);
  projectProps=ShepherdProperties.getProperties("project.properties", langCode, context);

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
  <%
  try{
    currentUser = AccessControl.getUser(request, myShepherd);
    project = myShepherd.getProject(projId);
    if(project != null){
      List<Encounter> encounters = project.getEncounters();
    // }

  %>
    <title><%= projectProps.getProperty("Project")%> <%=projId%></title>
    <div class="container maincontent">
      <div class="row">
        <div class="col-xs-10 col-sm-10 col-md-10 col-lg-10 col-xl-10">
          <h3><%= projectProps.getProperty("ProjectColon")%> <%=project.getResearchProjectName()%></h3>
        </div>
        <div class="col-xs-2 col-sm-2 col-md-2 col-lg-2 col-xl-10">
          <span id="editButtonSpan"></span>
        </div>
      </div>
          <%

            if(currentUser != null){
              if(encounters == null || encounters.size()<1){
                %>
                  <h4><%= projectProps.getProperty("NoEncountersInProj")%></h4>
                <%
              }else{
                %>
                <div align="center">
                  <div id="progress-div">
                    <h4><%= projectProps.getProperty("EncountersLoading")%></h4>
                    <div class="progress">
                      <div class="progress-bar progress-bar-striped active" role="progressbar" aria-valuenow="50" aria-valuemin="0" aria-valuemax="100" style="width: 50%">
                        <span class="sr-only"><%= projectProps.getProperty("PercentComplete")%></span>
                      </div>
                    </div>
                  </div>
                  <div id="table-div" style="display: none;">
                    <table class="row project-style">
                      <thead>
                        <tr>
                          <th class="project-style"><%= projectProps.getProperty("EncounterTableHeader")%></th>
                          <th class="project-style"><%= projectProps.getProperty("IndividualTableHeader")%></th>
                          <th class="project-style"><%= projectProps.getProperty("DateTimeTableHeader")%></th>
                          <th class="project-style"><%= projectProps.getProperty("LocationTableHeader")%></th>
                          <th class="project-style"><%= projectProps.getProperty("DataOwnerTableHeader")%></th>
                          <th class="project-style"><%= projectProps.getProperty("ProjectIdTableHeader")%></th>
                          <th class="project-style"><%= projectProps.getProperty("ActionTableHeader")%></th>
                        </tr>
                      </thead>
                      <tbody id="encounterList">
                        <!--populated by JS after page load-->
                      </tbody>
          <%
              } // end if encounters + else
            } // end if currentUser
          } // end if project
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
let projIdPrefix = '';
let countOfIncrementalIdRowPopulated = 0;

function markNewIncremental(individualId, projectId, encounterId){
  disableNewButton(encounterId);
  $('#adding-div_' + encounterId).show();
  if(individualId && projectId && encounterId){
    addIncrementalProjectIdAjax(individualId, projectId, encounterId);
  }
}

function createIndividualAndMarkNewIncremental(encounterId, projectId){
  disableNewButton(encounterId);
  $('#adding-div_' + encounterId).show();
  if(projectId && encounterId){
    createMarkedIndividualAjax(projectId, encounterId);
  }
}

function createMarkedIndividualAjax(projectId, encounterId){
  let formJson = {};
  formJson["projectId"] = projectId;
  formJson["encounterId"] = encounterId;
  $.ajax({
    url: wildbookGlobals.baseUrl + '../IndividualCreateForProject',
    type: 'POST',
    data: JSON.stringify(formJson),
    dataType: 'json',
    contentType : 'application/json',
    success: function(data){
      if(data){
        if(data.success){
          let newIndividualId = data.newIndividualId;
          addIncrementalProjectIdAjax(newIndividualId, projectId, encounterId);
        }else{
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
  formJson["projectIdPrefix"] = projectId;
  formJson["individualId"] = individualId;
  $.ajax({
    url: wildbookGlobals.baseUrl + '../IndividualAddIncrementalProjectId',
    type: 'POST',
    data: JSON.stringify(formJson),
    dataType: 'json',
    contentType : 'application/json',
    success: function(data){
      if(data){
        if(data.success){
          $('#adding-div_' + encounterId).hide();
          $('#alert-div_'+encounterId).show();
          $('#mark-new-button_'+encounterId).hide();
          $('#disabled-mark-new-button_'+encounterId).hide();
        }else{
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
  $('#mark-new-button_' + encounterId).hide();
  $('#disabled-mark-new-button_' + encounterId).show();
}

function enableNewButton(encounterId){
  $('#disabled-mark-new-button_' + encounterId).hide();
  $('#mark-new-button_' + encounterId).show();
}

function dismissAlert(encounterId){
  if(encounterId){
    $('#'+'alert-div_'+ encounterId).hide();
    $('#'+'alert-div-warn_'+encounterId).hide();
  }
}

function getEncounterJSON() {
  let projectUUID = '<%=projId%>';
  let requestJSON = {};
  requestJSON['projectUUID'] = projectUUID;
  requestJSON['getEncounterMetadata'] = "true";
  requestJSON['getEditPermission'] = "true";
  let responseJSON = {};
  $.ajax({
      url: wildbookGlobals.baseUrl + '../ProjectGet',
      type: 'POST',
      data: JSON.stringify(requestJSON),
      dataType: 'json',
      contentType: 'application/json',
      success: function(data) {
          $("#encounterList").empty();
          let projectsArr = data.projects;
          if(projectsArr){
            for (let i=0;i<projectsArr.length;i++) {
              let thisProject = projectsArr[i];
              projIdPrefix = thisProject.projectIdPrefix;
              for (let j=0;j<thisProject.encounters.length;j++) {
                let projectHTML = projectHTMLForTable(projectsArr[i].encounters[j], thisProject.encounters, j);
                $("#encounterList").append(projectHTML);
              }

            }
            let userCanEdit = data.userCanEdit;
            if ("true"==userCanEdit) {
              showEditControls();
            }
          }
      },
      error: function(x,y,z) {
          console.warn('%o %o %o', x, y, z);
      }
  });
}

function showEditControls() {
  let editPageLink = '';
  let location = '/projects/editProject.jsp?id=<%=projId%>';
  editPageLink += '<input id="editPageLink" class="btn" onclick="goToEditPage()" value ="<%= projectProps.getProperty("EditProject")%>" type="button" />';
  $('#editButtonSpan').append(editPageLink);
  // show encounter removal buttons also

}

function goToEditPage() {
  window.location.replace('/projects/editProject.jsp?id='+'<%=projId%>');
}

function projectHTMLForTable(json, encounters, currentEncounterIndex) {
  let encounterId = json.encounterId;
  let individualDisplayName = json.individualDisplayName;
  let individualUUID = json.individualUUID;
  let hasNameKeyMatchingProject = json.hasNameKeyMatchingProject;
  let encounterDate = json.encounterDate;
  let locationId = json.locationId;
  let submitterId = json.submitterId;
  let allProjectIds = json.allProjectIds;

  let projectHTML = '';
  projectHTML += '<tr id="enc-'+encounterId+'">';
  projectHTML +=  '<td class="project-style"><a target="_new" href="../encounters/encounter.jsp?number='+encounterId+'">'+encounterId+'</a></td>';
  projectHTML +=  '<td class="project-style"><a target="_new" href="../individuals.jsp?id='+individualUUID+'">'+individualDisplayName+'</a></td>';
  projectHTML +=  '<td class="project-style">'+encounterDate+' </td>';
  projectHTML +=  '<td class="project-style">'+locationId+' </td>';
  projectHTML +=  '<td class="project-style">'+submitterId+' </td>';
  projectHTML +=  '<td class="project-style" id="incremental-id-' + encounterId + '" + >';

  if(!hasNameKeyMatchingProject){
    projectHTML += "(None)";
  } else{
    if(individualUUID){
      let incrementalIdJsonRequest = {};
      incrementalIdJsonRequest['projectIdPrefix'] = projIdPrefix;
      incrementalIdJsonRequest['individualIds'] = [];
      incrementalIdJsonRequest['individualIds'].push({indId: individualUUID});
      doAjaxForIncrementalId(incrementalIdJsonRequest, encounters, currentEncounterIndex);
    }
  }
  projectHTML +=  '</td>';
  projectHTML +=  '<td class="project-style">';
  projectHTML +=  '<input id="encId-'+encounterId+'" class="startMatchButton" onclick="startMatchForEncounter(this)" value ="Start Match" type="button" />';
  projectHTML +=  '</br>';

  // grr.. not worth an AJAX call for just this. one more key and i'm doin it though -CK
  let projectIdPrefix = '<%= project.getProjectIdPrefix()%>';

  if(!hasNameKeyMatchingProject){
    if (individualDisplayName!=null&&individualDisplayName!="") {
      projectHTML += '<button id="mark-new-button_'+encounterId+'" type="button" onclick="markNewIncremental(\''+individualUUID+'\', \''+projectIdPrefix+'\', \''+encounterId+'\')"><%= projectProps.getProperty("MarkNew")%></button>';
      projectHTML += '<button class="disabled-btn" id="disabled-mark-new-button_'+encounterId+'" style="display: none;">Mark New</button>';
    } else {
      projectHTML += '<button id="mark-new-button_'+encounterId+'" type="button" onclick="createIndividualAndMarkNewIncremental(\''+encounterId+'\', \''+projectIdPrefix+'\')"><%= projectProps.getProperty("MarkNew")%></button>';
      projectHTML += '<button class="disabled-btn" id="disabled-mark-new-button_'+encounterId+'" style="display: none;"><%= projectProps.getProperty("MarkNew")%></button>';
    }
  }
  projectHTML += '<div id="adding-div_'+encounterId+'" class="alert alert-info" role="alert" style="display: none;"><%= projectProps.getProperty("AssingingIndividualToProjWait")%></div>';
  projectHTML += '<div id="alert-div_'+encounterId+'" class="alert alert-success" role="alert" style="display: none;">';
  projectHTML += '<button type="button" class="close" onclick="dismissAlert(\''+encounterId+'\')" aria-label="Close"><span aria-hidden="true">&times;</span></button>';
  projectHTML += '<strong><%= projectProps.getProperty("Success")%></strong><%= projectProps.getProperty("IdAdded")%>';
  projectHTML += '</div>';
  projectHTML += '<div id="alert-div-warn_'+encounterId+'" class="alert alert-danger" role="alert" style="display: none;">';
  projectHTML += '<button type="button" class="close" onclick="dismissAlert(\''+encounterId+'\')" aria-label="Close"><span aria-hidden="true">&times;</span></button>';
  projectHTML += '<%= projectProps.getProperty("IdNotAdded")%>';
  projectHTML += '</div>';
  projectHTML +=  '</td>';
  projectHTML += '</tr>';
  countOfIncrementalIdRowPopulated ++;
  if(countOfIncrementalIdRowPopulated == encounters.length){
    //everything is populated!
    $('#progress-div').hide();
    $('#table-div').show();
  }
  return projectHTML;
}

function doAjaxForIncrementalId(requestJSON, encounters, currentEncounterIndex){
  $.ajax({
      url: wildbookGlobals.baseUrl + '../ProjectGet',
      type: 'POST',
      data: JSON.stringify(requestJSON),
      dataType: 'json',
      contentType: 'application/json',
      success: function(data) {
          incrementalIdResults = data.incrementalIdArr;
          if(incrementalIdResults && incrementalIdResults.length>0){
            if(countOfIncrementalIdRowPopulated > 22){
            }
            populateEncounterRowWithIncrementalId(incrementalIdResults, encounters, currentEncounterIndex);
            if(countOfIncrementalIdRowPopulated == encounters.length){
              //everything is populated!
              $('#progress-div').hide();
              $('#table-div').show();
            }
          }
      },
      error: function(x,y,z) {
          console.warn('%o %o %o', x, y, z);
      }
  });
}

function populateEncounterRowWithIncrementalId(incrementalIdResults, encounters, currentEncounterIndex){
  $('#incremental-id-' + encounters[currentEncounterIndex].encounterId).empty();
  $('#incremental-id-' + encounters[currentEncounterIndex].encounterId).append(incrementalIdResults[0].projectIncrementalId);
}

function startMatchForEncounter(el) {
  let elId = $(el).attr('id');
  console.log("--> el id for starting match: "+elId);
  let encId = elId.replace('encId-','');
  let projectIdPrefix = '<%= project.getProjectIdPrefix()%>';
  if (encId&&projectIdPrefix) {
    let requestJSON = {};
    requestJSON['projectIdPrefix'] = projectIdPrefix;
    requestJSON['queryEncounterId'] = encId;
    let responseJSON = {};
    $.ajax({
        url: wildbookGlobals.baseUrl + '../ProjectIA',
        type: 'POST',
        data: JSON.stringify(requestJSON),
        dataType: 'json',
        contentType: 'application/json',
        success: function(d) {
          $(el).val('Sent');
          $(el).css('background-color', 'red');
          console.log("response from ProjectIA : "+JSON.stringify(d));
          // do something to indicate success
        },
        error: function(x,y,z) {
            console.warn('%o %o %o', x, y, z);
        }
    });
  }
}

$(document).ready( function() {
  getEncounterJSON();
});
</script>
