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
<%@ page import="org.ecocean.shepherd.core.Shepherd" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>

<%
  String context="context0";
  context=ServletUtilities.getContext(request);
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility
  String langCode=ServletUtilities.getLanguageCode(request);
  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("createProject.jsp1");
  myShepherd.beginDBTransaction();
  boolean proceed = true;
  boolean haveRendered = false;
  Properties collabProps = new Properties();
  String urlLoc = "//" + CommonConfiguration.getURLLocation(request);
  collabProps=ShepherdProperties.getProperties("collaboration.properties", langCode, context);
  User currentUser = AccessControl.getUser(request, myShepherd);
  Properties props = new Properties();
  props = ShepherdProperties.getProperties("createProject.properties", langCode, context);
%>
<style>
.sleeker-button{
  width: auto;
  height: 3em;
  margin-left: 0.2em;
  padding: 0 5px;
  vertical-align: middle;
  display: inline-block;
  background-color: @whaleSharkblue;
}
.icon_white_stripe{
  border-left: 1px solid white;
  padding-left: 0.3em;
  margin-left: 0.3em;
}
</style>
<jsp:include page="../header.jsp" flush="true"/>
  <link rel="stylesheet" href="<%=urlLoc %>/cust/mantamatcher/css/manta.css"/>
    <div class="container maincontent">
      <title><%=props.getProperty("createAProject") %></title>
          <%
          try{
            if(currentUser != null){
              System.out.println(props.getProperty("researchProjectName"));
              %>
              <h1><%=props.getProperty("newProject") %></h1>
              <form id="create-project-form"
              method="post"
              enctype="multipart/form-data"
              name="create-project-form"
              action="../ProjectCreate"
              accept-charset="UTF-8">
                <div class="form-group required row">
                  <div class="form-inline col-xs-12 col-sm-12 col-md-12 col-lg-12">
                    <label class="control-label text-danger"><strong><%=props.getProperty("researchProjectName") %></strong></label>
                    <input class="form-control" type="text" id="researchProjectName" name="researchProjectName"/>
                  </div>
                </div>
                <div class="form-group required row">
                  <div class="form-inline col-xs-12 col-sm-12 col-md-12 col-lg-12">
                    <label class="control-label text-danger"><strong><%=props.getProperty("projectIdPrefix") %></strong></label>
                    <input class="form-control" type="text" style="position: relative; z-index: 101;" id="projectIdPrefix" name="projectIdPrefix" size="20" />
                    <br/>
                    <label class="control-label"><small><%=props.getProperty("projectIdInstructions") %></small></label>
                  </div>
                </div>
                <div class="form-group row">
                      <div class="col-xs-6 col-sm-6 col-md-6 col-lg-6 col-xl-6">
                        <label><strong><%=props.getProperty("projectUserIds") %></strong></label>
                        <input class="form-control" name="projectUserIds" type="text" id="projectUserIds" placeholder="<%=props.getProperty("typeToSearch") %>">
                      </div>
                      <div id="projectUserIdsListContainer">
                        <div id="access-list-title-container">

                        </div>
                        <div id="projectUserIdsList">
                        </div>
                      </div>
                    </div>
                    <div class="row" id="organizationAccessRow" hidden>
                      <select id="organizationAccess" multiple="multiple" name="organizationAccess">
                        <option value="" selected></option>>
                      </select>
                    </div>
                    <button type="button" id="cancelButton" onclick="cancelButtonClicked();" class="sleeker-button">
                      <span><%=props.getProperty("cancel") %>  </span><span class="glyphicon glyphicon-remove icon_white_stripe" aria-hidden="true"></span>
                    </button>
                    <button type="button" id="createProjectButton" onclick="createButtonClicked();" class="sleeker-button">
                      <span><%=props.getProperty("submit_send") %>  </span><span class="button-icon" aria-hidden="true"></span>
                    </button>
              </form>
              <h4><%= props.getProperty("toAddEncounters")%><%= props.getProperty("projectManagement")%><%= props.getProperty("inSearchResults")%></h4>
              <%
            }else{

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
    </div>
<jsp:include page="../footer.jsp" flush="true"/>

    <script>

    //json ob with translations
    var txt = getText('createProject.properties');

    let myName = '<%=request.getUserPrincipal().getName()%>';
    let userNamesOnAccessList = [];
    $('#projectUserIds').autocomplete({
      source: function(request, response){
        $.ajax({
          url: wildbookGlobals.baseUrl + '/UserGetSimpleJSON?searchUser=' + request.term,
          type: 'GET',
          dataType: "json",
          success: function(data){
            let res = null;
            res = $.map(data, function(item){
              if(item.username==myName || typeof item.username == 'undefined' || item.username == undefined||item.username===""){
                return;
              }
              let fullName = "";
              if(item.fullName!=null && item.fullName!="undefined"){
                fullName=item.fullName;
              }
              let label = ("name: " + fullName + " user: " + item.username);
              return {label: label, value: item.username+ ":" + item.id}; //
            });
            response(res);
          }
        });
      }
    });
    $( "#projectUserIds" ).on( "autocompleteselect", function( event, result ) {
      let selectedUserStr = result.item.value;
      addUserToProject(selectedUserStr);
      $(this).val("");
      return false;
    });

    function updateprojectUserIdsDisplay(){
      userNamesOnAccessList = [...new Set(userNamesOnAccessList)];
      $('#projectUserIdsList').empty();
      $('#access-list-title-container').empty();
      if(userNamesOnAccessList.length >0){
        $('#access-list-title-container').append("<strong>"+txt.usersForAccess+"</strong>");
      }
      for(i=0; i<userNamesOnAccessList.length; i++){
        let elem = "<div class=\"chip\">" + userNamesOnAccessList[i].split(":")[0] + "  <span class=\"glyphicon glyphicon-remove-sign\" aria-hidden=\"true\" onclick=\"removeUserFromProj('" + userNamesOnAccessList[i] + "'); return false\"></span></div>";
        $('#projectUserIdsList').append(elem);
      }
    }

    function addUserToProject(selectedUserStr){
      userNamesOnAccessList.push(selectedUserStr);
      updateprojectUserIdsDisplay();
    }

    function removeUserFromProj(name){
      userNamesOnAccessList = userNamesOnAccessList.filter(element => element !== name);
      updateprojectUserIdsDisplay();
    }

    function createButtonClicked() {
    	console.log('createButtonClicked()');
    	if(!$('#projectIdPrefix').val()){
    		console.log("no projectIdPrefix entered");
    		$('#projectIdPrefix').closest('.form-group').addClass('required-missing');
    		window.setTimeout(function() { alert(txt.needPrefix); }, 100);
    		return false;
      }
      if(!$('#researchProjectName').val()){
    		console.log("no researchProjectName entered");
    		$('#researchProjectName').closest('.form-group').addClass('required-missing');
    		window.setTimeout(function() { alert(txt.needProjName); }, 100);
    		return false;
      }
      if($('#projectIdPrefix').val().includes(";")){
    		console.log("projectIdPrefix contains ; entered");
    		$('#projectIdPrefix').closest('.form-group').addClass('required-missing');
    		window.setTimeout(function() { alert(txt.noSemicolonsId); }, 100);
    		return false;
    	}
      if($('#researchProjectName').val().includes(";")){
    		console.log("researchProjectName contains ; entered");
    		$('#researchProjectName').closest('.form-group').addClass('required-missing');
    		window.setTimeout(function() { alert(txt.noSemicolonsName); }, 100);
    		return false;
    	}
      if($('#projectIdPrefix').val().includes("_")){
    		console.log("projectIdPrefix contains ; entered");
    		$('#projectIdPrefix').closest('.form-group').addClass('required-missing');
    		window.setTimeout(function() { alert(txt.noSemicolonsId); }, 100);
    		return false;
    	}
      if($('#researchProjectName').val().includes("_")){
    		console.log("researchProjectName contains ; entered");
    		$('#researchProjectName').closest('.form-group').addClass('required-missing');
    		window.setTimeout(function() { alert(txt.noUnderscoresName); }, 100);
    		return false;
    	}
      // if($('#projectIdPrefix').val().includes(" ")){
    	// 	console.log("projectIdPrefix contains ; entered");
    	// 	$('#projectIdPrefix').closest('.form-group').addClass('required-missing');
    	// 	window.setTimeout(function() { alert(txt.noSpacesId); }, 100);
    	// 	return false;
    	// }
      // if($('#researchProjectName').val().includes(" ")){
    	// 	console.log("researchProjectName contains ; entered");
    	// 	$('#researchProjectName').closest('.form-group').addClass('required-missing');
    	// 	window.setTimeout(function() { alert(txt.noSpacesName); }, 100);
    	// 	return false;
    	// }
      if($('#projectIdPrefix').val().includes("\'")){
    		console.log("projectIdPrefix contains ; entered");
    		$('#projectIdPrefix').closest('.form-group').addClass('required-missing');
    		window.setTimeout(function() { alert(txt.noApostrophesId); }, 100);
    		return false;
    	}
      if($('#researchProjectName').val().includes("\'")){
    		console.log("researchProjectName contains ; entered");
    		$('#researchProjectName').closest('.form-group').addClass('required-missing');
    		window.setTimeout(function() { alert(txt.noApostrophesName); }, 100);
    		return false;
    	}
      submitForm();
    	return true;
    }

    function cancelButtonClicked(){
      console.log("cancelButtonClicked entered");
      window.location.replace('/projects/projectList.jsp');
    }

    function getUuidsFromAccessList(){
      let uuidsOnAccessList = userNamesOnAccessList.map(function(element){
        return element.split(":")[1];
      });
      return uuidsOnAccessList;
    }

    function submitForm() {
      console.log("submitForm entered");

      let uuidsOnAccessList = getUuidsFromAccessList();
      let formDataArray = $("#create-project-form").serializeArray();
      let formJson = {};
      formJson["organizationAccess"] = [];

      let selectedOrganizations = $("#organizationAccess").val();
      if (selectedOrganizations&&selectedOrganizations.length) {
        formJson["organizationAccess"] = selectedOrganizations;
      }

      for(i=0; i<formDataArray.length; i++){
        if (Object.values(formDataArray[i])[0] === "projectUserIds"){
          formDataArray[i].value=uuidsOnAccessList;
        }
        let currentName = formDataArray[i].name;
        if (Object.values(formDataArray[i])[0] === "organizationAccess"){
          formJson[currentName].push(formDataArray[i].value);
        }else{
          formJson[currentName]=formDataArray[i].value;
        }
      }
      console.log("form JSON");
      console.log(JSON.stringify(formJson));
      $.ajax({
        url: wildbookGlobals.baseUrl + '../ProjectCreate',
        type: 'POST',
        data: JSON.stringify(formJson),
        dataType: 'json',
        contentType : 'application/json',
        success: function(data){
          console.log(data);
          if(data.newProjectUUID){
            window.location.replace('/projects/project.jsp?id='+data.newProjectUUID);
          }else{
            console.log("project id dne in response");
          }
          // window.location.replace('standard-upload?filename='+filename+"&isUserUpload=true");
        },
        error: function(x,y,z) {
          console.warn('%o %o %o', x, y, z);
        }
      });
    }

    var organizationUsersCache = {};
    function getOrganizationsForDropdown() {
      let requestJSON = {};
      requestJSON['action'] = 'getAllForUser';
      $.ajax({
          url: wildbookGlobals.baseUrl + '../OrganizationGet',
          type: 'POST',
          data: JSON.stringify(requestJSON),
          dataType: 'json',
          contentType: 'application/json',
          success: function(d) {
              console.log("Success! response: "+d.organizations);
              organizationUsersCache = d.organizations;
              let organizationsArr = d.organizations;
              populateOrganizationsMultiSelect(organizationsArr)
          },
          error: function(x,y,z) {
              console.warn('%o %o %o', x, y, z);
          }
      });
    }

    function populateOrganizationsMultiSelect(organizationsArr) {
      if (organizationsArr.length>0) {
        $('#organizationAccessRow').removeAttr('hidden');
      }
      let dropdown = $('#organizationAccess');
      for (i=0;i<organizationsArr.length;i++) {
        let org = organizationsArr[i];
        let selectEl;
        if (org.name&&org.id) {
          selectEl = $('<option class="organizationSelectOption" value="'+org.id+'">'+org.name+'</option>');
        }
        dropdown.append(selectEl);
      }
    }

    $(document).ready(function() {
      getOrganizationsForDropdown()
    });
    </script>
