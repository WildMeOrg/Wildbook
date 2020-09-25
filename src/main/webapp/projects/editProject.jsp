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
         org.apache.commons.lang3.StringEscapeUtils,
         java.util.*,org.ecocean.security.Collaboration" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>

<%
  String context="context0";
  context=ServletUtilities.getContext(request);
  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("editProject.jsp");
  String projId = request.getParameter("id").replaceAll("\\+", "").trim();

  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

  String langCode=ServletUtilities.getLanguageCode(request);

  String urlLoc = "//" + CommonConfiguration.getURLLocation(request);
  //collabProps=ShepherdProperties.getProperties("collaboration.properties", langCode, context);
  User currentUser = AccessControl.getUser(request, myShepherd);

  myShepherd.closeDBTransaction();
%>

<jsp:include page="../header.jsp" flush="true"/>
<link rel="stylesheet" href="<%=urlLoc %>/cust/mantamatcher/css/manta.css"/>

<!--hook all HTML to these elements-->
<div class="container maincontent">
  <div id="projectDetails">
  </div>
</div>

<jsp:include page="../footer.jsp" flush="true"/>

<script>

function showEditProject() {
    let ownerId = '<%=currentUser.getId()%>';
    let projectId = '<%=projId%>';
    let getEncounterMetadata = false;
    let json = {};
    json['projectUUID'] = projectId;
    json['getEncounterMetadata'] = getEncounterMetadata;
    doProjectGetAjax(json);
}

function doProjectGetAjax(json){
  $.ajax({
      url: wildbookGlobals.baseUrl + '../ProjectGet',
      type: 'POST',
      data: JSON.stringify(json),
      dataType: 'json',
      contentType: 'application/json',
      success: function(data) {
          populateHtml(data.projects[0]);
          populateOrRefreshUserListHTML(data.projects[0].users);
      },
      error: function(x,y,z) {
        //TODO some sort of indication on user end that something has gone wrong
          console.warn('%o %o %o', x, y, z);
      }
  });
}

function populateOrRefreshUserListHTML(jsonArr) {
  for (i=0;i<jsonArr.length;i++) {
    console.log('appending users.... '+JSON.stringify(jsonArr));
    let thisUser = jsonArr[i];
    appendNewUser(thisUser.id, thisUser.username)
  }
}

function appendNewUser(userId, username) {
  let userList = $('#userListDiv');
  let projectEditUserEl = '<span id="'+userId+'" class="projectEditUserEl"><span class="glyphicon glyphicon-remove remove-ob-x" onclick="removeUserFromProject(this)" ></span> '+username+'</span>';
  userList.append(projectEditUserEl);
}

function populateHtml(project){
  let projectHTML = '';

  projectHTML += '<div class="container researchProjectIdDiv" id="'+project.researchProjectId+'">';
  projectHTML += '<h1>Edit Project</h1>';
  projectHTML += '<h3>Project: '+project.researchProjectName+'</h3>';
  projectHTML += '<hr>';

  projectHTML += '<div class="row">';

  projectHTML += '<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">';
  projectHTML += '  <label>Research Project Name</label><br>';  
  projectHTML += '  <input class="form-control" type="text" value="'+project.researchProjectName+'" id="researchProjectName" name="researchProjectName" size="20" />';
  projectHTML += '</div>'
  projectHTML += '<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">';
  projectHTML += '  <label>Research Project ID</label><br>';
  projectHTML += '  <input class="form-control" type="text" value="'+project.researchProjectId+'" id="researchProjectId" name="researchProjectId" size="20" />'; 
  projectHTML += '</div>'

  projectHTML += '</div>';

  projectHTML += '<div class="row">';

  projectHTML += '<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6 col-xl-6">';
  projectHTML += '  <label>Add Users</label>';
  projectHTML += '  <input class="form-control" name="projectUserIds" type="text" id="projectUserIds" placeholder="Type To Search">';
  projectHTML += '</div>';
  projectHTML += '<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6 col-xl-6"">';
  projectHTML += '  <label>User List</label>';
  projectHTML += '  <div id="userListDiv">';
  projectHTML += '  </div>';
  projectHTML += '</div>';
    
  projectHTML += '</div>';
    
  projectHTML += '<div class="row">';

  projectHTML += '  <div class="col-xs-2 col-sm-2 col-md-1 col-lg-1">';
  projectHTML += '    <p><input class="btn btn-md" type="button" onclick="deleteProject(this)" value="Delete"/></p>';
  projectHTML += '  </div>';
  projectHTML += '  <div class="col-xs-2 col-sm-2 col-md-1 col-lg-1">';
  projectHTML += '    <p><input class="btn btn-md" type="button" onclick="updateProject(this)" value="Update"/></p>';
  projectHTML += '  </div>';
  projectHTML += '  <div class="col-xs-2 col-sm-2 col-md-1 col-lg-1">';
  projectHTML += '    <p><input class="btn btn-md" type="button" onclick="returnToProject(this)" value="Return To Project"/></p>';
  projectHTML += '  </div>';
  projectHTML += '  <div class="col-xs-6 col-sm-6 col-md-6 col-lg-6">';
  projectHTML += '    <label class="updataResponse" id="actionResultMessage"></label>';
  projectHTML += '  </div>';

  projectHTML += '</div>';

  projectHTML += '</div>'; // container
  $("#projectDetails").append(projectHTML);
}


var userIdsToRemove = [];
var userIdsToAdd = [];
function updateProject() {
  console.log('ids to remove: '+JSON.stringify(userIdsToRemove)+' ids to add: '+JSON.stringify(userIdsToAdd));

  let requestJSONArr = [];
  let requestJSON = {};
  requestJSON['researchProjectName'] = $('#researchProjectName').val();
  requestJSON['researchProjectId'] = $('#researchProjectId').val();
  if (userIdsToRemove.length>0) {
    requestJSON['usersToRemove'] = userIdsToRemove;
  }
  if (userIdsToAdd.length>0) {
    requestJSON['userIdsToAdd'] = userIdsToAdd;
  }
  requestJSON['id'] = '<%=projId%>';
  requestJSONArr.push(requestJSON);
  doProjectUpdateAjax({"projects": requestJSONArr });
}

function deleteProject(el) {
  let projectId = $(el).closest(".researchProjectIdDiv").attr("id");
  let json = {};
  json['researchProjectId'] = projectId;
  doDeleteAjax(json);
}

function returnToProject() {
  window.location.replace('/projects/project.jsp?id='+'<%=projId%>');
}

function doDeleteAjax(json){
  $.ajax({
      url: wildbookGlobals.baseUrl + '../ProjectDelete',
      type: 'POST',
      data: JSON.stringify(json),
      dataType: 'json',
      contentType: 'application/json',
      success: function(data) {
        //TODO indicate to user that something good happened
        // probably settimeout with short message display
          window.location.replace('/projects/projectList.jsp');
      },
      error: function(x,y,z) {
          console.warn('%o %o %o', x, y, z);
      }
  });
}

function doProjectUpdateAjax(json) {
  $.ajax({
      url: wildbookGlobals.baseUrl + '../ProjectUpdate',
      type: 'POST',
      data: JSON.stringify(json),
      dataType: 'json',
      contentType: 'application/json',
      success: function(data) {
        $("#actionResultMessage").text("Success updating project data.");
      },
      error: function(x,y,z) {
        $("#actionResultMessage").text("Failure updating project data.");
        console.warn('%o %o %o', x, y, z);
      }
  });
}

function removeUserFromProject(el) {
  let confirmed = confirm('Are you sure you want to remove this user from the project?');
  if (confirmed) {
    let idToRemove = $(el).closest(".projectEditUserEl").attr('id');
    userIdsToRemove.push(idToRemove);
    $(el).closest(".projectEditUserEl").remove();
  }
}

//let userNamesOnAccessList = [];
let currentUsername = '<%=currentUser.getUsername()%>';
$(document).ready(function() {


  showEditProject();

  console.log("do i have the element for projectUserIds??? "+$('#projectUserIds'));

  $('#projectUserIds').autocomplete({
    source: function(request, response){
      $.ajax({
        url: wildbookGlobals.baseUrl + '/UserGetSimpleJSON?searchUser=' + request.term,
        type: 'GET',
        dataType: "json",
        success: function(data){
          console.log("autocomplete hit...");
          let res = null;
          res = $.map(data, function(item){
            if(item.username==currentUsername || typeof item.username == 'undefined' || item.username == undefined||item.username===""){
              return;
            }
            let fullName = "";
            if(item.fullName!=null && item.fullName!="undefined"){
              fullName=item.fullName;
            }
            let label = ("name: " + fullName + " user: " + item.username);
            return {label: label, value: item.username+ ":" + item.id, username: item.username, id: item.id};
          });
          response(res);
        },
        error: function(x,y,z) {
          console.warn('%o %o %o', x, y, z);
          return false;
        }

      });
    }
  });
  $( "#projectUserIds" ).on( "autocompleteselect", function(event,result) {
    let selectedUserStr = result.item.value;
    console.log("Result in autocomplete: "+JSON.stringify(result));
    appendNewUser(result.item.id, result.item.username);
    $(this).val("");
    return false;
  });
  
  
});
</script>
