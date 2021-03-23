<%@ page contentType="text/html; charset=iso-8859-1" language="java"
         import="org.ecocean.servlet.ServletUtilities,javax.servlet.http.HttpUtils,
org.json.JSONObject, org.json.JSONArray,
org.ecocean.media.*,
org.ecocean.identity.IdentityServiceLog,
java.util.ArrayList,org.ecocean.Annotation, org.ecocean.Encounter,
org.dom4j.Document, org.dom4j.Element,org.dom4j.io.SAXReader, java.util.*, org.ecocean.*, org.ecocean.grid.MatchComparator, org.ecocean.grid.MatchObject, java.io.File, java.util.Arrays, java.util.Iterator, java.util.List, java.util.Vector, java.nio.file.Files, java.nio.file.Paths, java.nio.file.Path,
java.net.URLEncoder,
java.nio.charset.StandardCharsets,
java.io.UnsupportedEncodingException

" %>

<%
String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
Properties props = new Properties();
String langCode=ServletUtilities.getLanguageCode(request);
props = ShepherdProperties.getProperties("merge.properties", langCode,context);
myShepherd.setAction("merge.jsp");
User currentUser = AccessControl.getUser(request, myShepherd);
String indIdA = request.getParameter("individualA");
String indIdB = request.getParameter("individualB");
String newId = indIdA;
MarkedIndividual markA = myShepherd.getMarkedIndividualQuiet(indIdA);
MarkedIndividual markB = myShepherd.getMarkedIndividualQuiet(indIdB);
MarkedIndividual[] inds = {markA, markB};
String fullNameA = indIdA;
if (markA!=null) fullNameA += " ("+URLEncoder.encode(markA.getDisplayName(request, myShepherd), StandardCharsets.UTF_8.toString())+")";
String fullNameB = indIdB;
if (markB!=null) fullNameB += " ("+URLEncoder.encode(markB.getDisplayName(request, myShepherd), StandardCharsets.UTF_8.toString())+")";
%>

<jsp:include page="header.jsp" flush="true" />

<!-- overwrites ia.IBEIS.js for testing -->

<style>
table td,th {
	padding: 10px;
}
#mergeBtn {
	float: right;
}
table.compareZone tr th {
	background: inherit;
}
</style>

<script>
  let conflictingProjs = [];
  let conflictingProjOwners = [];
  let countOfIncrementalIdRowPopulated = 0;
  let projNamesBelongingToIndividuals = [];
  let projIdPrefixesBelongingToIndividuals = [];
	$(document).ready(function() {
		// highlightMergeConflicts(); //TODO add this back in -MF
		replaceDefaultKeyStrings();
    let requestJsonForIndividualsProjects = {};
    requestJsonForIndividualsProjects['individualIdsForProj'] = [];
    <% for (MarkedIndividual ind: inds) {%>
      requestJsonForIndividualsProjects['individualIdsForProj'].push({indId: "<%= ind.getIndividualID()%>"});
    <%}%>
    doAjaxForProjectIndividuals(requestJsonForIndividualsProjects);
	});
  function callForIncrementalIdsAndPopulate(projIdPrefix, numProjects){
    let incrementalIdJsonRequest = {};
    incrementalIdJsonRequest['projectIdPrefix'] = projIdPrefix;
    incrementalIdJsonRequest['individualIds'] = [];
    <% for (MarkedIndividual ind: inds) {%>
      incrementalIdJsonRequest['individualIds'].push({indId: "<%= ind.getIndividualID()%>"});
    <%}%>
    doAjaxForProject(incrementalIdJsonRequest, numProjects);
  }
  function doAjaxForProject(requestJSON, numProjects){
    $.ajax({
        url: wildbookGlobals.baseUrl + '../ProjectGet',
        type: 'POST',
        data: JSON.stringify(requestJSON),
        dataType: 'json',
        contentType: 'application/json',
        success: function(data) {
            incrementalIdResults = data.incrementalIdArr;
            projectNameResults = data.projects;
            if(incrementalIdResults && incrementalIdResults.length>0){
              populateProjectIdRow(incrementalIdResults, incrementalIdResults[0].projectName, incrementalIdResults[0].projectUuid, incrementalIdResults[0].projectIdPrefix, incrementalIdResults[0].projectOwner);
              countOfIncrementalIdRowPopulated ++;
              if(countOfIncrementalIdRowPopulated == numProjects){
                //everything is populated! Now check whether user's projects include conflicting projs
                let requestJsonForCurrentUsersProjects = {};
                requestJsonForCurrentUsersProjects['participantId'] = '<%= currentUser.getUsername()%>';
                doAjaxForProject(requestJsonForCurrentUsersProjects);
              }
            }else{
              if(projectNameResults){
                let projNameOptions = projectNameResults.map(entry =>{return entry.researchProjectName});
                let prjIdOptions = projectNameResults.map(entry =>{return entry.projectIdPrefix});
                  if(projNameOptions.length>0){
                    compareUserProjectsToConflictedOnesOnIndividuals(projNameOptions,conflictingProjs);
                  }
              }
            }
        },
        error: function(x,y,z) {
            console.warn('%o %o %o', x, y, z);
        }
    });
  }
  function compareUserProjectsToConflictedOnesOnIndividuals(userProjNames, conflictingProjsOnIndividuals){
    let shouldShow = true;
    let conflictingProjectToWhichUserHasNoAccess = [];
    let conflictingProjectOwnerForUserToContact = [];
    for (let i=0; i<conflictingProjsOnIndividuals.length; i++){
      currentConflictingProjName = conflictingProjsOnIndividuals[i];
      if(!userProjNames.includes(currentConflictingProjName)){
        conflictingProjectToWhichUserHasNoAccess.push(currentConflictingProjName);
        conflictingProjectOwnerForUserToContact.push(conflictingProjOwners[i]);
        shouldShow = false;
      }
    }
    if(shouldShow){
      $('#everything-else').show();
      $('#progress-div').hide();
      $('#not-permitted').hide();
    }else{
      $('#everything-else').hide();
      $('#progress-div').hide();
      populateContactList(conflictingProjectToWhichUserHasNoAccess, conflictingProjectOwnerForUserToContact);
      $('#not-permitted').show();
    }
  }
  function populateContactList(conflictingProjectToWhichUserHasNoAccess, conflictingProjectOwnerForUserToContact){
    let contactListHtml = '';
    contactListHtml += '';
    contactListHtml += '<ul>';
    for(let i=0; i<conflictingProjectToWhichUserHasNoAccess.length; i++){
      contactListHtml += '<li>';
      contactListHtml += '<em>';
      contactListHtml += conflictingProjectToWhichUserHasNoAccess[i];
      contactListHtml += '</em>';
      contactListHtml += ' <%= props.getProperty("OwnedBy")%> ';
      contactListHtml += '<em>';
      contactListHtml += conflictingProjectOwnerForUserToContact[i];
      contactListHtml += '</em>';
      contactListHtml += '</li>';
    }
    contactListHtml += '</ul>';
    $("#proj-contact-list").empty();
    $("#proj-contact-list").append(contactListHtml);
  }
  function doAjaxForProjectIndividuals(requestJSON){
    $.ajax({
        url: wildbookGlobals.baseUrl + '../ProjectGet',
        type: 'POST',
        data: JSON.stringify(requestJSON),
        dataType: 'json',
        contentType: 'application/json',
        success: function(data) {
            let projectByIndividArr = data.projectByIndividArr;
            if(projectByIndividArr && projectByIndividArr.length>0){
              for(let i=0; i< projectByIndividArr.length; i++){
                let currentIndividaulsProjects = projectByIndividArr[i];
                if(currentIndividaulsProjects && currentIndividaulsProjects.length>0){
                  currentIndividaulsProjectNames = currentIndividaulsProjects.map(entry =>{return entry.researchProjectName});
                  currentIndividaulsProjectIdPrefixes = currentIndividaulsProjects.map(entry =>{return entry.projectIdPrefix});
                  projNamesBelongingToIndividuals = projNamesBelongingToIndividuals.concat(currentIndividaulsProjectNames);
                  //uniquify
                  projNamesBelongingToIndividuals = [...new Set(projNamesBelongingToIndividuals)];
                  projIdPrefixesBelongingToIndividuals = projIdPrefixesBelongingToIndividuals.concat(currentIndividaulsProjectIdPrefixes);
                  projIdPrefixesBelongingToIndividuals = [...new Set(projIdPrefixesBelongingToIndividuals)];
                }
              }
              //done with fetching project names and id prefixes belonging to individuals
              populateProjectRows(projNamesBelongingToIndividuals, projIdPrefixesBelongingToIndividuals);
            }
            else{
            // no projects on the individuals, so no project rows to populate
            }
          },
          error: function(x,y,z) {
              console.warn('%o %o %o', x, y, z);
          }
      });
  }
  function populateProjectIdRow(incrementalIds, projName, projUuid, projId, projOwner){
    let projectIdHtml = '';
    <% for (int i=0; i<inds.length; i++) {%>
    projectIdHtml += '<td class="col-md-2 diff_check">';
    if(incrementalIds && incrementalIds[<%=i%>] && incrementalIds[<%=i%>].projectIncrementalId !== ""){
      projectIdHtml += incrementalIds[<%=i%>].projectIncrementalId;
    }else{
      projectIdHtml += '<%= props.getProperty("NoIncrementalId") %>';
    }
    projectIdHtml += '</td>';
    <%}%>
    projectIdHtml += '<td class="merge-field">';
    if(incrementalIds && incrementalIds.length>1 && incrementalIds[0].projectIncrementalId !== "" && incrementalIds[1].projectIncrementalId !== ""){
      // two incremental IDs for projName
      if(!conflictingProjs.includes(projName)){
        conflictingProjs.push(projName);
        conflictingProjOwners.push(projOwner);
      }
      projectIdHtml += '<select name="' + projId + '" data-id="proj-confirm-dropdown-' + projName + '" class="form-control">';
      for(let i=0; i<incrementalIds.length; i++){
        if(i==0){
          projectIdHtml += '<option name="incremental-id-option" value="'+ incrementalIds[i].projectIncrementalId +'" selected>'+ incrementalIds[i].projectIncrementalId +'</option>';
        }else{
          projectIdHtml += '<option name="incremental-id-option" value="'+ incrementalIds[i].projectIncrementalId +'">'+ incrementalIds[i].projectIncrementalId +'</option>';
        }
      }
      projectIdHtml += '</td>';
      $('[data-id="current-proj-id-display-' + projName + '"]').closest("tr").append(projectIdHtml);
    } else{
      if(incrementalIds && incrementalIds.length>0 && (incrementalIds[0].projectIncrementalId !== "" || incrementalIds[1].projectIncrementalId !== "")){ //one incremental ID is missing
        //populate with the one incremental ID and don't give them a choice about it, but give it the IDs and names required to still fetch this value upon form submission
        projectIdHtml += '<span name="' + projId + '" data-id="proj-confirm-dropdown-' + projName + '">';
        let betterVal = betterValWithTieBreaker(incrementalIds[0].projectIncrementalId, incrementalIds[1].projectIncrementalId);
        projectIdHtml += betterVal;
        projectIdHtml += '</span>'
        projectIdHtml += '</td>';
        projectIdHtml += '<td>';
        $('[data-id="current-proj-id-display-' + projName + '"]').closest("tr").append(projectIdHtml);
      }else{
        //populate with no incremental IDs, but give it the IDs and names required to still fetch this value upon form submission
        projectIdHtml += '<span name="' + projId + '" data-id="proj-confirm-dropdown-' + projName + '">';
        projectIdHtml += '<%= props.getProperty("NoIncrementalId") %>';
        projectIdHtml += '</span>'
        projectIdHtml += '</td>';
        projectIdHtml += '<td>';
        $('[data-id="current-proj-id-display-' + projName + '"]').closest("tr").append(projectIdHtml);
      }
    }
  }
  function betterValWithTieBreaker(candidate1, candidate2){
    if (candidate1!=null && candidate2!=null && candidate1.trim() === candidate2.trim()) {
      // return shorter string (less whitespace)
      if (candidate1.length()<candidate2.length()){
        return candidate1;
      }
      else{
        return candidate2;
      }
    }
    if (!candidate2){
      return candidate1;
    }
    if (!candidate1){
      return candidate2;
    }
    return candidate1;
  }
  function getDeprecatedIncrementalIdFromOptions (stringOfSemiColonDelimitedCumulativeDesiredIncrementalIds, arrayOfOptionElements){
    let returnVal = "_";
    for(let i=0; i<arrayOfOptionElements.length; i++){
      let currentOptionElem = arrayOfOptionElements[i];
      let counter = 0;
      let currentOptionVal = $(currentOptionElem).text();
      desiredIncrementalIdArr = stringOfSemiColonDelimitedCumulativeDesiredIncrementalIds.split(";");
      if(!desiredIncrementalIdArr.includes(currentOptionVal)){
        returnVal = currentOptionVal;
      }
    }
    return returnVal;
  }
  function concatenateToConsolidationString(index, totalElementLength, candidateArrAsString, currentArrEntry){
    let returnVal = "";
    if(index == totalElementLength-1){
      //the last entry
      returnVal = candidateArrAsString + currentArrEntry;
    }else{
      returnVal = candidateArrAsString + currentArrEntry + ';';
    }
    return returnVal;
  }
  function populateProjectRows(projectNames, projectIds){
    let projectIdHtml = '';
    if(projectNames.length>0){
      for(let i =0; i<projectNames.length; i++){
        projectIdHtml += '<tr class="row projectId check_for_diff" data-id="project-id-table-row-' + projectNames[i] + '">';
        projectIdHtml += '<th><%= props.getProperty("ProjectId") %>';
        projectIdHtml += '<span data-id="current-proj-id-display-' + projectNames[i] + '"><em> ' + projectNames[i] + '</em></span>';
        projectIdHtml += '</th>';
        projectIdHtml += '</tr>';
      }
      $("tr.row.names").last().after(projectIdHtml);
      for(let j =0; j<projectNames.length; j++){ //projectNames and projectIds must be linked; otherwise, this will break
        callForIncrementalIdsAndPopulate(projectIds[j],projectNames.length);
      }
    } else {
      $('#everything-else').show();
      $('#progress-div').hide();
      $('#not-permitted').hide();
    }
  }
	function replaceDefaultKeyStrings() {
		$('span.nameKey').each(function(i, el) {
			var fixedHtml = $(this).html().replace("*","Default").replace("_legacyIndividualID_","Legacy IndividualID").replace("_nickName_","nickname").replace("_alternateID_","Alternate ID");
			$(this).html(fixedHtml);
		});
	}
	function highlightMergeConflicts() {
		$(".row.check_for_diff").each(function(i, el) {
      if($(this).children("td.diff_check").first().html()){
        let val1 = $(this).children("td.diff_check").first().html().trim();
        let val2 = $(this).children("td.diff_check").last().html().trim();
        let val3 = $(this).find("input").val(); //TODO update
        console.log("index="+i+" val1="+val1+", val2="+val2+" and val3="+val3);
        if (val3!==val1 && val3!==val2) {
          $(this).addClass('needs_review');
          $(this).addClass('text-danger');
          $(this).addClass('bg-danger');
        }
      }
		});
	}
</script>

<div class="container maincontent">
  <div id="progress-div">
    <h4><%= props.getProperty("Loading")%></h4>
    <div class="progress">
      <div class="progress-bar progress-bar-striped active" role="progressbar" aria-valuenow="50" aria-valuemin="0" aria-valuemax="100" style="width: 50%">
        <span class="sr-only"><%= props.getProperty("PercentComplete")%></span>
      </div>
    </div>
  </div>
  <div id="not-permitted" style="display: none;">
    <h4><%= props.getProperty("NotPermitted")%></h4>
    <div id="proj-contact-list"></div>
  </div>
  <div id="everything-else" style="display: none;">
    <%
    // build query for EncounterMediaGallery here
    //String queryString = "SELECT FROM org.ecocean.Encounter WHERE individual.individualID == '"+indIdA+"' || individual.individualID == '"+indIdB+"'";
    //System.out.println("Merge.jsp has queryString "+queryString);
    // consider including an enc media gallery below?
    %>
    <%
    try {
    	%>
      <h1>Marked Individual Merge Tool</h1>
      <p class="instructions">Confirm the merged values for each of the fields below.</p>
      <p class="instructions"><span class="text-danger bg-danger">Fields in red</span> have conflicting values and require attention.</p>
      <form id="mergeForm"
        action="MergeIndividual"
        method="post"
    	  enctype="multipart/form-data"
        name="merge_individual_submission"
        target="_self" dir="ltr"
        lang="en"
        onsubmit="console.log('the form has been submitted!');"
        class="form-horizontal"
        accept-charset="UTF-8"
      >
    	<table class="compareZone">
    		<tr class="row header">
    			<th class="col-md-2"></th>
    			<% for (MarkedIndividual ind: inds) {%>
    			<th class="col-md-2"><h2>
    				<a href='<%=ind.getWebUrl(request)%>'><%=ind.getDisplayName(request, myShepherd)%></a>
    			</h2></th>
    			<%}%>
    			<th><h2>
    				<%= props.getProperty("MergedIndividual") %>
    			</h2></th>
    		</tr>
    		<tr class="row names">
    			<th><%= props.getProperty("Names") %></th>
    			<% for (MarkedIndividual ind: inds) {%>
    			<td class="col-md-2">
    				<% for (String key: ind.getNameKeys()) {
    					String nameStr = String.join(", ", ind.getNamesList(key));
    					%><span class="nameKey"><%=key%></span>: <span class="nameValues"><%=nameStr%></span><br/><%
    				}
    				%>
    			</td>
    			<%}%>
    			<td class="col-md-2 mergedNames">
    				<%
    				MultiValue allNames = MultiValue.merge(markA.getNames(), markB.getNames());
    				for (String key: allNames.getKeys()) {
    					String nameStr = String.join(", ", allNames.getValuesAsList(key));
    					%><span class="nameKey"><%=key%></span>: <span class="nameValues"><%=nameStr%></span><br/><%
    				}
    				%>
    			</td>
    		</tr>
          <!--populated by JS after page load-->
    		<tr class="row encounters">
    			<th><%= props.getProperty("NumEncounters") %></th>
    			<% int totalEncs = 0;
    			for (MarkedIndividual ind: inds) {
    				int encs = ind.numEncounters();
    				totalEncs+= encs;
    				%>
    				<td class="col-md-2">
    					<%=encs%>
    				</td>
    			<%}%>
    			<td class="col-md-2">
    				<%=totalEncs%>
    			</td>
    		</tr>
    		<tr class="row species check_for_diff">
    			<th><%= props.getProperty("Species") %></th>
    			<% for (MarkedIndividual ind: inds) {%>
    			<td class="col-md-2 diff_check">
    				<%=ind.getGenusSpeciesDeep()%>
    			</td>
    			<%}%>
    			<td class="merge-field">
    				<%
    				String mergeTaxy = Util.betterValue(markA.getGenusSpeciesDeep(), markB.getGenusSpeciesDeep());
            if(markA.getGenusSpeciesDeep()!= null && markB.getGenusSpeciesDeep()!= null && !markA.getGenusSpeciesDeep().equals("") && !markB.getGenusSpeciesDeep().equals("") && !markA.getGenusSpeciesDeep().equals(markB.getGenusSpeciesDeep())){
              %>
                <select name="taxonomy-dropdown" id="taxonomy-dropdown" class="">
                <option value="<%= markA.getGenusSpeciesDeep()%>" selected><%= markA.getGenusSpeciesDeep()%></option>
                <option value="<%= markB.getGenusSpeciesDeep()%>"><%= markB.getGenusSpeciesDeep()%></option>
              <%
            }else{
              System.out.println("getting here");
              %>
              <%= mergeTaxy%>
              <%
            }
    				%>
    			</td>
    		</tr>
        <tr class="row sex check_for_diff">
    			<th><%= props.getProperty("Sex") %></th>
    			<% for (MarkedIndividual ind: inds) {%>
    			<td class="col-md-2 diff_check">
    				<%=ind.getSex()%>
    			</td>
    			<%}%>
    			<td class="merge-field">
    				<%
    				String mergeSex = Util.betterValue(markA.getSex(), markB.getSex());
            if(markA.getSex()!= null && markB.getSex()!= null && !markA.getSex().equals("") && !markB.getSex().equals("") && !markA.getSex().equals(markB.getSex())){
              %>
                <select name="sex-dropdown" id="sex-dropdown" class="">
                <option value="<%= markA.getSex()%>" selected><%= markA.getSex()%></option>
                <option value="<%= markB.getSex()%>"><%= markB.getSex()%></option>
              <%
            }else{
              %>
              <%= mergeSex%>
              <%
            }
    				%>
    			</td>
    		</tr>
    		</tr>
    	</table>
      <input type="submit" name="Submit" value="Merge Individuals" id="mergeBtn" class="btn btn-md editFormBtn"/>
    	</form>
    	<script type="text/javascript">
      $(document).ready(function() {
        $("#mergeBtn").click(function(event) {
          event.preventDefault();
        	let id1="<%=indIdA%>";
        	let id2="<%=indIdB%>";
        	let fullNameA = '<%=fullNameA%>';
        	let fullNameB = '<%=fullNameB%>';
          let sex = $("#sex-dropdown").val();
          if(!sex){
            //It's because they match
            sex = '<%= Util.betterValue(markA.getSex(), markB.getSex()) %>';
          }
          let taxonomy = $("#taxonomy-dropdown").val();
          if(!taxonomy){
            //It's because they match
            taxonomy = '<%= Util.betterValue(markA.getGenusSpeciesDeep(), markB.getGenusSpeciesDeep()) %>';
          }
          let projIdElems = $('[data-id^=proj-confirm-dropdown-]');
          let projIdConsolidated = '';
          let desiredIncrementalIdConsolidated = '';
          let deprecatedIncrementIdConsolidated = '';
          let currentDeprecatedIncrementalID  = "";
          let currentOptionElems = [];
          debugger;
          for(let i=0; i<projIdElems.length; i++){
            let currentElem = projIdElems[i];
            let currentProjUuid = $(currentElem).attr('name');
            let currentDesiredIncrementalId = $(currentElem).find(":selected").text();
            if(currentDesiredIncrementalId){
              currentOptionElems = $(currentElem).children('option[name="incremental-id-option"]'); //optionElems.concat($(currentElem).children('option[name="incremental-id-option"]'));
            }else{
              //if you can't get it from a selected element, it's not from a a <select>, but you still need to capture the value
              if($(currentElem).text() === '<%= props.getProperty("NoIncrementalId")%>'){
                currentDesiredIncrementalId = "_";
                currentDeprecatedIncrementalID = "_"; // a placeholder
              }else{
                currentDesiredIncrementalId = $(currentElem).text();
                currentDeprecatedIncrementalID = "_"; // a placeholder
              }
            }
            projIdConsolidated = concatenateToConsolidationString(i, projIdElems.length, projIdConsolidated, currentProjUuid);
            desiredIncrementalIdConsolidated = concatenateToConsolidationString(i, projIdElems.length, desiredIncrementalIdConsolidated, currentDesiredIncrementalId);
            currentDeprecatedIncrementalID = getDeprecatedIncrementalIdFromOptions(desiredIncrementalIdConsolidated, currentOptionElems);
            deprecatedIncrementIdConsolidated = concatenateToConsolidationString(i, projIdElems.length, deprecatedIncrementIdConsolidated, currentDeprecatedIncrementalID);
            currentOptionElems = [];
          }
        	$("#mergeForm").attr("action", "MergeIndividual"); // Is this necessary given <form's already-existing attributes? -MF
          $.post("/MergeIndividual", {
          	"id1": id1,
          	"id2": id2,
          	"sex": sex,
          	"taxonomy": taxonomy,
            "projIds": projIdConsolidated,
            "desiredIncrementalIds": desiredIncrementalIdConsolidated,
            "deprecatedIncrementIds": deprecatedIncrementIdConsolidated
          },
          function() {
    		    updateNotificationsWidget();
          	var confirmUrl = '/mergeComplete.jsp?oldNameA='+fullNameA+'&oldNameB='+fullNameB+'&newId='+id1;
    				window.location = confirmUrl;
          })
          .fail(function(response) {
          	alert("FAILURE!!");
          });
    	  });
    	});
    	</script>
    	<%
    } catch (Exception e) {
    	System.out.println("Exception on merge.jsp! indIdA="+indIdA+" indIdB="+indIdB);
    	myShepherd.rollbackDBTransaction();
    } finally {
    	myShepherd.closeDBTransaction();
    }
    %>
  </div>
</div>
<jsp:include page="footer.jsp" flush="true"/>

<!--<script src="javascript/underscore-min.js"></script>
<script src="javascript/backbone-min.js"></script>-->