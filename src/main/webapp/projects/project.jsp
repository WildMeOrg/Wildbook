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

  String urlLoc = "//" + CommonConfiguration.getURLLocation(request);
  Properties projectProps = new Properties();

  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

  String langCode=ServletUtilities.getLanguageCode(request);

  projectProps=ShepherdProperties.getProperties("project.properties", langCode, context);
%>

<jsp:include page="../header.jsp" flush="true"/>

  <%
  try{

	  String projId = request.getParameter("id").replaceAll("\\+", "").trim();
	  String rootWebappPath = getServletContext().getRealPath("/");
	  File webappsDir = new File(rootWebappPath).getParentFile();
	  File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
	  File projectsDir=new File(shepherdDataDir.getAbsolutePath()+"/projects");
	  File projectDir = new File(projectsDir, projId);


	  pageContext.setAttribute("projId", projId);
	  boolean proceed = true;
	  boolean haveRendered = false;



	    currentUser = AccessControl.getUser(request, myShepherd);
	    project = myShepherd.getProject(projId);

	    if(project != null){
	      List<Encounter> encounters = project.getEncounters();


	  	%>
	    	<title><%= projectProps.getProperty("Project")%> <%=projId%></title>
	    	<div class="container maincontent">
	      		<div class="row">
	        		<div class="col-xs-10 col-sm-10 col-md-10 col-lg-10 col-xl-10">
	          			<h3><%= projectProps.getProperty("ProjectColon")%> <%=project.getResearchProjectName()%></h3>
	          			<p><%= projectProps.getProperty("EncounterDirectionsPt1")%><a target="_new" href="/react/encounter-search"> <%= projectProps.getProperty("EncounterDirectionsPt2")%></a><%= projectProps.getProperty("EncounterDirectionsPt3")%></p>
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
	              }
	              else{
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
	                              <p>Total encounters: <%=encounters.size() %></p>
	                    <table class="row project-style js-sort-table" >
	                      <thead>
	                        <tr>
	                          <th class="project-style" style="max-width: 180px; word-break: break-all; over-flow:hidden"><%= projectProps.getProperty("OccurrenceTableHeader")%></th>
	                          <th class="project-style" style="max-width: 180px; word-break: break-all; over-flow:hidden"><%= projectProps.getProperty("EncounterTableHeader")%></th>
	                          <th class="project-style" style="max-width: 180px; word-break: break-all; over-flow:hidden" data-js-sort-colnum="0"><%= projectProps.getProperty("IndividualTableHeader")%></th>
	                          <th class="project-style js-sort-date" data-js-sort-colnum="1"><%= projectProps.getProperty("DateTimeTableHeader")%></th>
	                          <th class="project-style"><%= projectProps.getProperty("LocationTableHeader")%></th>
	                          <th class="project-style"><%= projectProps.getProperty("DataOwnerTableHeader")%></th>
	                          <th class="project-style"><%= projectProps.getProperty("ProjectIdTableHeader")%></th>
	                          <th class="project-style js-sort-none"><%= projectProps.getProperty("ActionTableHeader")%></th>
	                        </tr>
	                      </thead>
	                      <tbody id="encounterList">
	                        <!--populated by JS after page load-->
	                      </tbody>
	          <%
	              } // end if encounters + else
	            } // end if currentUser

	            %>

	            </table>

            </div>
          </div>
          
           

<script type="text/javascript">

var annotIdTaskIdAdapter = {};
var txt = getText("project.properties");

let projIdPrefix = '';
let countOfIncrementalIdRowPopulated = 0;

function markNewIncremental(individualId, projectIdPrefix, encounterId){
  disableNewButton(encounterId);
  $('#adding-div_' + encounterId).show();
  if(individualId && projectIdPrefix && encounterId){
    addIncrementalProjectIdAjax(individualId, projectIdPrefix, encounterId);
  }
}

function createIndividualAndMarkNewIncremental(encounterId, projectIdPrefix){
  disableNewButton(encounterId);
  $('#adding-div_' + encounterId).show();
  if(projectIdPrefix && encounterId){
    createMarkedIndividualAjax(projectIdPrefix, encounterId);
  }
}

function createMarkedIndividualAjax(projectIdPrefix, encounterId){
  let formJson = {};
  formJson["projectIdPrefix"] = projectIdPrefix;
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
          addIncrementalProjectIdAjax(newIndividualId, projectIdPrefix, encounterId);
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

function addIncrementalProjectIdAjax(individualId, projectIdPrefix, encounterId){
  let formJson = {};
  formJson["projectIdPrefix"] = projectIdPrefix;
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

function disableStartMatchButton(encounterId){
  $('#encId-' + encounterId).hide();
  $('#disabled-encId-' + encounterId).show();
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

function dismissAlertForMatchStart(encounterId){
  if(encounterId){
    $('#'+'match-start-alert-div_'+ encounterId).hide();
    $('#'+'match-start-alert-div_-warn_'+encounterId).hide();
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
                // enable Match Results as needed ?
                // getIAInfoForEncounterData(null, thisProject.encounters[j].encounterId);
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
  let occurrenceUUID = json.occurrenceUUID;
  let hasNameKeyMatchingProject = json.hasNameKeyMatchingProject;
  let encounterDate = json.encounterDate;
  let locationId = json.locationId;
  let submitterId = json.submitterId;
  let allProjectIds = json.allProjectIds;

  let projectHTML = '';
  projectHTML += '<tr id="enc-'+encounterId+'" class="encounterRow">';
  projectHTML +=  '<td id="occurrenceID-'+encounterId+'" class="project-style" style="max-width: 180px; word-break: break-all; over-flow:hidden"><a target="_new" href="../occurrence.jsp?number='+occurrenceUUID+'">'+occurrenceUUID+'</a></td>';
  projectHTML +=  '<td id="catalogNumber-'+encounterId+'" class="project-style" style="max-width: 180px; word-break: break-all; over-flow:hidden"><a target="_new" href="../encounters/encounter.jsp?number='+encounterId+'">'+encounterId+'</a></td>';
  projectHTML +=  '<td id="indivID-'+encounterId+'" class="project-style" style="max-width: 180px; word-break: break-all; over-flow:hidden"><a target="_new" href="../individuals.jsp?id='+individualUUID+'">'+individualDisplayName+'</a></td>';
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
  let projectIdPrefix = '<%= project.getProjectIdPrefix()%>';
  projectHTML +=  '</td>';
  projectHTML +=  '<td class="project-style">';

  //upper row action
  projectHTML +=  '<div class="row">';
  projectHTML +=  '   <div class="col-sm-12 col-md-12 col-lg-6">';
              // add JS check for ia availability onload
  projectHTML +=  '     <button id="encId-'+encounterId+'" class="startMatchButton proj-action-btn" onclick="startMatchForEncounter(this, \'' + encounterId + '\')">'+txt.startMatch+'</button>';
  projectHTML +=  '     <button id="disabled-encId-'+encounterId+'" class="disabled-btn proj-action-btn" style="display: none;">'+txt.startMatch+'</button>';
  projectHTML +=  '     </br>';
  projectHTML +=  '   </div>';
              // add JS check for exisitng results onload (add .disabled-btn if appropriate)

  projectHTML +=  '   <div class="col-sm-12 col-md-12 col-lg-6">';
  projectHTML +=  '     <button id="disabled-match-results-encId-'+encounterId+'" style="display: none;" class="disabled-btn visitResultsButton proj-action-btn">'+txt.matchResults+'</button>';
  projectHTML +=  '     <button id="match-results-encId-'+encounterId+'" class="visitResultsButton proj-action-btn" onclick="openIaResultsOptions(this)">'+txt.matchResults+'</button>';
  projectHTML +=  '     </br>';
  projectHTML +=  '   </div>';
  projectHTML +=  '</div>';
  //end upper action row

  //lower row action
  projectHTML += '<div class="row">';
  projectHTML += '   <div class="col-sm-12 col-md-12 col-lg-6">';

  let disabledMarkNew = '  <button class="disabled-btn proj-action-btn" id="disabled-mark-new-button_'+encounterId+'" style="display: none;"><%= projectProps.getProperty("MarkNew")%></button>';
  if(!hasNameKeyMatchingProject){
    if (individualDisplayName!=null&&individualDisplayName!="") {
      projectHTML += '  <button class="proj-action-btn" id="mark-new-button_'+encounterId+'" type="button" onclick="markNewIncremental(\''+individualUUID+'\', \''+projectIdPrefix+'\', \''+encounterId+'\')"><%= projectProps.getProperty("MarkNew")%></button>';
      projectHTML += disabledMarkNew;
    } else {
      projectHTML += '  <button class="proj-action-btn" id="mark-new-button_'+encounterId+'" type="button" onclick="createIndividualAndMarkNewIncremental(\''+encounterId+'\', \''+projectIdPrefix+'\')"><%= projectProps.getProperty("MarkNew")%></button>';
      projectHTML += disabledMarkNew;
    }
  } else {
    //same disabled but visible
    projectHTML += '<button class="disabled-btn proj-action-btn" id="disabled-mark-new-button_'+encounterId+'"><%= projectProps.getProperty("MarkNew")%></button>';
  }

  projectHTML += '   </div>';

  projectHTML += '   <div class="col-sm-12 col-md-12 col-lg-6">';
  projectHTML += '    <button class="btn-warn proj-action-btn" onclick="removeEncounterFromProject(this)" title="remove encounter from project">'+txt.remove+'</button>';
  projectHTML += '    <span class="deleteMessage text-danger"></span>';
  projectHTML += '   </div>';
  projectHTML += '</div>';
  //end lower action row

  projectHTML += '<div id="adding-div_'+encounterId+'" class="alert alert-info" role="alert" style="display: none;"><%= projectProps.getProperty("AssingingIndividualToProjWait")%></div>';
  projectHTML += '<div id="alert-div_'+encounterId+'" class="alert alert-success" role="alert" style="display: none;">';
  projectHTML += '  <button type="button" class="close" onclick="dismissAlert(\''+encounterId+'\')" aria-label="Close"><span aria-hidden="true">&times;</span></button>';
  projectHTML += '  <strong><%= projectProps.getProperty("Success")%></strong><%= projectProps.getProperty("IdAdded")%>';
  projectHTML += '</div>';
  projectHTML += '<div id="alert-div-warn_'+encounterId+'" class="alert alert-danger" role="alert" style="display: none;">';
  projectHTML += '  <button type="button" class="close" onclick="dismissAlert(\''+encounterId+'\')" aria-label="Close"><span aria-hidden="true">&times;</span></button>';
  projectHTML += '  <%= projectProps.getProperty("IdNotAdded")%>';
  projectHTML += '</div>';
  projectHTML += '<div id="match-start-adding-div_'+encounterId+'" class="alert alert-info" role="alert" style="display: none;">' + txt.matchingIntiatedWait + '</div>';
  projectHTML += '<div id="match-start-alert-div_'+encounterId+'" class="alert alert-success" role="alert" style="display: none;">';
  projectHTML += '  <button type="button" class="close" onclick="dismissAlertForMatchStart(\''+encounterId+'\')" aria-label="Close"><span aria-hidden="true">&times;</span></button>';
  projectHTML += '  <strong>' + txt.Success + '</strong> ' + txt.matchStartedSuccessfully;
  projectHTML += '</div>';
  projectHTML += '<div id="match-start-alert-div-warn_'+encounterId+'" class="alert alert-danger" role="alert" style="display: none;">';
  projectHTML += '  <button type="button" class="close" onclick="dismissAlertForMatchStart(\''+encounterId+'\')" aria-label="Close"><span aria-hidden="true">&times;</span></button>';
  projectHTML += txt.matchStartFailed;
  projectHTML += '</div>';

  projectHTML += '</td>';
  projectHTML += '</tr>';

  projectHTML += '<tr id="iaResultsMenu-'+encounterId+'" class="iaResultsMenuRow" style="display: none;">';
  projectHTML += '  <td colspan="999" class="project-style">';
  projectHTML += '    <div class="iaResultsMenuContent" ></div>';
  projectHTML += '  </td>';
  projectHTML += '</tr>';


  countOfIncrementalIdRowPopulated ++;
  if(countOfIncrementalIdRowPopulated == encounters.length){
    //everything is populated! -MF
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
              //everything is populated! -MF
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

function startMatchForEncounter(el, currentEncounterId) {
  $('#match-start-adding-div_' + currentEncounterId).show();
  disableStartMatchButton(currentEncounterId);
  disableMatchResultsButton(currentEncounterId);
  let menuRow = $('#iaResultsMenu-'+currentEncounterId);
  if (!isHidden(menuRow)) {
    menuRow.hide(); // note that this HAS to close and be re-opened in order to run getIAInfoForEncounterData again which in turn runs generateIALinkingMenu which looks for taskId updates from this startMatch logic herein
    $("#match-results-encId-"+currentEncounterId).html(txt.matchResults);
  }
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
          console.log("response from ProjectIA : ");
          console.log(d);
          if(d){
            if(d.success){
              $('#match-start-adding-div_' + currentEncounterId).hide();
              $('#match-start-alert-div_'+currentEncounterId).show();
              populateAnnotIdTaskIdAdapter(d, currentEncounterId);
              enableMatchResultsButton(currentEncounterId);
            }else{
              $('#match-start-adding-div_' + currentEncounterId).hide();
              $('#match-start-alert-div-warn_'+currentEncounterId).show();
            }
          }
        },
        error: function(x,y,z) {
            console.warn('%o %o %o', x, y, z);
        }
    });
  }
}

function enableMatchResultsButton(encId){
  $("#disabled-match-results-encId-" + encId).hide();
  $("#match-results-encId-" + encId).show();
}

function disableMatchResultsButton(encId){
  $("#match-results-encId-" + encId).hide();
  $("#disabled-match-results-encId-" + encId).show();
}

function populateAnnotIdTaskIdAdapter(data, encounterId){
  if(data.success){
    const initiatedJobs = data.initiatedJobs;
    if(initiatedJobs.length > 0){
      for(let i = 0; i<initiatedJobs.length; i++){
        const currentJob = initiatedJobs[i];
        const currentQueryAnnId = currentJob.queryAnnId;
        const currentChildTaskId = currentJob.childTaskId;
        annotIdTaskIdAdapter[currentQueryAnnId] = currentChildTaskId;
      }
    }
  }

}

function removeEncounterFromProject(el) {
  let confirmed = confirm(txt.youSure);
  if (confirmed) {
    removeEncounterFromProjectAjax(el);
  }
}

function removeEncounterFromProjectAjax(el) {
  let requestJSON = {};
  let projectsArr = [];
  let projectData = {};
  projectData['id'] = '<%=projId%>';
  projectData['encountersToRemove'] = [];
  let encRow = $(el).closest('.encounterRow');
  let encId = encRow.attr('id').replace('enc-', '');
  projectData.encountersToRemove.push(encId);
  projectsArr.push(projectData);
  requestJSON['projects'] = projectsArr;

  $.ajax({
      url: wildbookGlobals.baseUrl + '../ProjectUpdate',
      type: 'POST',
      data: JSON.stringify(requestJSON),
      dataType: 'json',
      contentType: 'application/json',
      success: function(d) {
        console.log("data from success remove encounter action: "+JSON.stringify(d));
        if (d.success==true&&d.modified==true) {
          encRow.remove();
        } else {
          encRow.find(".deleteMessage").text(txt.unauthorized);
        }
      },
      error: function(x,y,z) {
          console.warn('%o %o %o', x, y, z);
          encRow.find(".deleteMessage").text(txt.error);
      }
  });
}

function goToIAResults(taskId) {
	  let projectIdPrefix = '<%= project.getProjectIdPrefix()%>';
	  window.open('/iaResults.jsp?taskId='+taskId+'&projectIdPrefix='+encodeURIComponent(projIdPrefix), "_blank");
}

function generateIALinkingMenu(json, encId) {
  let menuRow = $("#iaResultsMenu-"+encId);
  let menuContentDiv = menuRow.find('.iaResultsMenuContent');
  let content = '';
  if (json) {
    for (i=0;i<json.length;i++) {
      let annData = json[i];
      // lets flag some bad detection states

      let needsIaClass = true;
      let iaClassEl = '<p>'+txt.iaClass+': <span style="color:white;background-color:darkred;"> '+txt.none+' </span></p>';
      if (annData.iaClass!=''||annData.iaClass!='undefined'||annData!=undefined) {
        needsIaClass = false;
        iaClassEl = '<p>'+txt.iaClass+': '+annData.iaClass+'</p>';
      }

      let needsDetection = false;
      let detectionStatusEl = '<p>'+txt.detectionStatus+': '+annData.assetDetectionStatus+'</p>';
      if (annData.assetDetectionStatus=='initiated'||annData.assetDetectionStatus=='error') {
        needsDetection = true;
        detectionStatusEl = '<p>'+txt.detectionStatus+': '+annData.assetDetectionStatus+' <span style="color:white;background-color:darkred;">'+txt.needDetection+'</span></p>'
      }
      let resultsLink = '<div id="visitBtnDiv-' + annData.id + '"><p>'+txt.latestResults+': <button class="disabled-btn btn-sm visitIaBtn">'+txt.view+'</button></p></div>'; // let's be very paranoid and start with a disabled view button
      if(annData.lastTaskId){ // check for a last taskId
        resultsLink = '<div id="visitBtnDiv-' + annData.id + '"><p>'+txt.latestResults+': <button class="btn-sm visitIaBtn" onclick="goToIAResults(\''+annData.lastTaskId+'\')">'+txt.view+'</button></p></div>';
      }

      if(annotIdTaskIdAdapter[annData.id]){ // but if a taskId generated by the "Start Match" button is generated, use that more recent taskId instead
        resultsLink = '<div id="visitBtnDiv-' + annData.id + '"><p>'+txt.latestResults+': <button class="btn-sm visitIaBtn" onclick="goToIAResults(\''+annotIdTaskIdAdapter[annData.id]+'\')">'+txt.view+'</button></p></div>';
      }

      if (needsIaClass||needsDetection) {
        resultsLink = '<p>'+txt.latestResults+':<button class="disabled-btn btn-sm visitIaBtn">'+txt.noneAvailable+'</button></p>';
      }

      content += '<div id="annIA-'+annData.id+'" class="row projIaOption">';
      content += '  <div class="col-sm-6 col-md-6 col-lg-6">';
      content += '    <p>ID: '+annData.id+'</p>';
      content += iaClassEl;
      content += detectionStatusEl;
      content += "    <p>"+txt.identificationStatus+": "+annData.identificationStatus+"</p>";
      content += resultsLink;
      content += '  </div>';
      content += '  <div class="col-sm-6 col-md-6 col-lg-6">';
      content += '    <p class="projIaAnnot"><img src="'+annData.assetWebURL+'" width="275px" height="*"/></p>';
      content += '  </div>';
      content += '</div>';
    }
  } else {
    content += '<div>';
      content += '  <p>'+txt.noResults+'</p>';
    content += '</div>';
  }
  menuContentDiv.empty();
  menuContentDiv.append(content);
  menuRow.removeClass('hidden');
}

function openIaResultsOptions(el) {
  let encRow = $(el).closest('.encounterRow');
  let encId = encRow.attr('id').replace('enc-', '');
  let menuRow = $('#iaResultsMenu-'+encId);


  if (isHidden(menuRow)) {
    $(el).html(txt.close);
    getIAInfoForEncounterData(el, null);
    menuRow.detach();
    encRow.after(menuRow);
    menuRow.show();
  } else {
    $(el).html(txt.matchResults);
    menuRow.hide();
  }
}

function isHidden(el) {
  return $(el).css('display') == 'none';
}

function getIAInfoForEncounterData(optionalEl, optionalEncId) {
  let requestJSON = {};
  requestJSON['action'] = 'getIAInfoForEncounter';
  requestJSON['onlyIdentifiable'] = 'true';
  let encId = optionalEncId;
  if(optionalEl && !optionalEncId){
    let encRow = $(optionalEl).closest('.encounterRow');
    encId = encRow.attr('id').replace('enc-', '');
  }

  requestJSON['encounterId'] = encId;
  $.ajax({
    url: wildbookGlobals.baseUrl + '../GetCurrentIAInfo',
    type: 'POST',
    data: JSON.stringify(requestJSON),
    dataType: 'json',
    contentType: 'application/json',
    success: function(d) {
      console.log("what is the response from GetCurrentIAInfo? : ");
      console.log(d);
      generateIALinkingMenu(d.IAInfo, encId);
    },
    error: function(x,y,z) {
        console.log('error getting some ia options!');
        console.warn('%o %o %o', x, y, z);
    }
  });
}

$(document).ready( function() {
  getEncounterJSON();
  //hide results row if we're sorting on column headers
  $("th").click(function() {
	  $(".visitResultsButton").html(txt.matchResults);
	  $(".iaResultsMenuRow").hide();
  });
});
</script>
 <script src="../javascript/sort-table.js"></script>
	            <%

	          } // end if project
	    	else{

	    		//if no project by this ID
	    		%>

	    		<title>No Project Found</title>
	    	    <div class="container maincontent">

	    		<%


	    	}


          }
  		catch (Exception e) {
            e.printStackTrace();
          } // end try block
          finally{
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
          }
          %>

    </div>
<jsp:include page="../footer.jsp" flush="true"/>
