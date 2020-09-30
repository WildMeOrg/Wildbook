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
if (markA!=null) fullNameA += " ("+URLEncoder.encode(markA.getDisplayName(), StandardCharsets.UTF_8.toString())+")";
String fullNameB = indIdB;
if (markB!=null) fullNameB += " ("+URLEncoder.encode(markB.getDisplayName(), StandardCharsets.UTF_8.toString())+")";



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
	$(document).ready(function() {
		highlightMergeConflicts();
		replaceDefaultKeyStrings();
    let requestJsonForProjectNamesDropdown = {};
    requestJsonForProjectNamesDropdown['ownerId'] = '<%= currentUser.getId()%>';
    // console.log("requestJsonForProjectNamesDropdown is: ");
    // console.log(requestJsonForProjectNamesDropdown);
    doAjaxForProject(requestJsonForProjectNamesDropdown);
	});

  function callForIncrementalIdsAndPopulate(projId){
    let incrementalIdJsonRequest = {};
    incrementalIdJsonRequest['researchProjectId'] = projId;
    incrementalIdJsonRequest['individualIds'] = [];
    <% for (MarkedIndividual ind: inds) {%>
      incrementalIdJsonRequest['individualIds'].push({indId: "<%= ind.getIndividualID()%>"});
    <%}%>
    console.log("json for incremental ID call is: ");
    console.log(incrementalIdJsonRequest);
    doAjaxForProject(incrementalIdJsonRequest);
  }


  function doAjaxForProject(requestJSON){
    // console.log("json going into ajax request is: ");
    // console.log(JSON.stringify(requestJSON));
    $.ajax({
        url: wildbookGlobals.baseUrl + '../ProjectGet',
        type: 'POST',
        data: JSON.stringify(requestJSON),
        dataType: 'json',
        contentType: 'application/json',
        success: function(data) {
            // console.log("literal response:");
            // console.log(data);
            // console.info('Success in ProjectGet retrieving data! Got back '+JSON.stringify(data));
            incrementalIdResults = data.incrementalIdArr;
            projectNameResults = data.projects;
            if(incrementalIdResults && incrementalIdResults.length>0){
              console.log("1: incrementalIdResults!");
              console.log(incrementalIdResults);
              console.log(data);
              populateProjectIdRow(incrementalIdResults, incrementalIdResults[0].projectName, incrementalIdResults[0].projectUuid);
            }else{
              if(projectNameResults){
                // console.log("2: projectNameResults!");
                // console.log(projectNameResults);
                let projNameOptions = projectNameResults.map(entry =>{return entry.researchProjectName});
                let prjIdOptions = projectNameResults.map(entry =>{return entry.researchProjectId});
                  if(projNameOptions.length>0){
                    // console.log("about to enter populateProjectRows...");
                    populateProjectRows(projNameOptions, prjIdOptions);
                  }else{
                    // callForIncrementalIdsAndPopulate("temp"); //TODO revise or do nothing???
                    // populateProjectRows(['<%= props.getProperty("NoProjects")%>']);
                  }
              }else{
                // console.log("Ack should not happen");
              }
            }
        },
        error: function(x,y,z) {
            console.warn('%o %o %o', x, y, z);
        }
    });
  }

  function populateProjectIdRow(incrementalIds, projName, projUuid){
    console.log("data in populateProjectIdRow is: ");
    console.log(incrementalIds);
    console.log(projName);
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
      projectIdHtml += '<select name="' + projUuid + '" id="proj-confirm-dropdown-' + projName + '" class="form-control">';
      for(let i=0; i<incrementalIds.length; i++){
        if(i==0){
          projectIdHtml += '<option name="incremental-id-option" value="'+ incrementalIds[i].projectIncrementalId +'" selected>'+ incrementalIds[i].projectIncrementalId +'</option>';
        }else{
          projectIdHtml += '<option name="incremental-id-option" value="'+ incrementalIds[i].projectIncrementalId +'">'+ incrementalIds[i].projectIncrementalId +'</option>';
        }
      }
      projectIdHtml += '</td>';
      // console.log("projectIdHtml is: ");
      // console.log(projectIdHtml);
      $("#current-proj-id-display-" + projName).closest("tr").append(projectIdHtml);
      // $("#incrementalId-container-" + incrementalIds[0].projectName).append(projectIdHtml);
    } else{
      if(incrementalIds && (incrementalIds[0].projectIncrementalId === "" || incrementalIds[1].projectIncrementalId === "")){
        console.log("got in here!")
        //populate with the one incremental ID and don't give them a choice about it
        let betterVal = betterValWithTieBreaker(incrementalIds[0].projectIncrementalId, incrementalIds[1].projectIncrementalId);
        projectIdHtml += betterVal;
        projectIdHtml += '</td>';
        projectIdHtml += '<td>';
        $("#current-proj-id-display-" + projName).closest("tr").append(projectIdHtml);
      }else{
        //populate with no incremental IDs
        projectIdHtml += '<%= props.getProperty("NoIncrementalId") %>';
        projectIdHtml += '</td>';
        projectIdHtml += '<td>';
        $("#current-proj-id-display-" + projName).closest("tr").append(projectIdHtml);
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

  // function removeItemAll(arr, value) {
  //   let i = 0;
  //   while (i < arr.length) {
  //     if (arr[i] === value) {
  //       arr.splice(i, 1);
  //     } else {
  //       ++i;
  //     }
  //   }
  //   return arr;
  // }

  function populateProjectRows(projectNames, projectIds){
    // console.log("populateProjectRows called");
    // console.log("projectNames is: ");
    // console.log(projectNames);
    // console.log("projectIds is: ");
    // console.log(projectIds);
    let projectIdHtml = '';
    if(projectNames.length>0){
      for(let i =0; i<projectNames.length; i++){
        projectIdHtml += '<tr class="row projectId check_for_diff" id="project-id-table-row-' + projectNames[i] + '">';
        projectIdHtml += '<th><%= props.getProperty("ProjectId") %>';
        projectIdHtml += '<span id="current-proj-id-display-' + projectNames[i] + '"><em> ' + projectNames[i] + '</em></span>';
        projectIdHtml += '</th>';
        // projectIdHtml += '<div id="incrementalId-container-' + projectNames[i] + '">';
        // projectIdHtml += '</div>';
        // projectIdHtml += '<td class="merge-field">';
        // projectIdHtml += '</td>';
        projectIdHtml += '</tr>';
      }
      // console.log("projectIdHtml in populateProjectRows is: ");
      // console.log(projectIdHtml);
      $("tr.row.names").last().after(projectIdHtml);
      // $("#project-id-table-row-container").append(projectIdHtml);
      for(let j =0; j<projectNames.length; j++){ //projectNames and projectIds must be linked
        console.log("calling callForIncrementalIdsAndPopulate in loop. Current project id is: " + projectIds[j]);
        callForIncrementalIdsAndPopulate(projectIds[j]);
      }
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
        let val3 = $(this).find("input").val();
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

<h1>Marked Individual Merge Tool</h1>
<p class="instructions">Confirm the merged values for each of the fields below.</p>
<p class="instructions"><span class="text-danger bg-danger">Fields in red</span> have conflicting values and require attention.</p>

<%
// build query for EncounterMediaGallery here
//String queryString = "SELECT FROM org.ecocean.Encounter WHERE individual.individualID == '"+indIdA+"' || individual.individualID == '"+indIdB+"'";
//System.out.println("Merge.jsp has queryString "+queryString);

// consider including an enc media gallery below?
%>
<%


try {



	%>

	<form>
	<table class="compareZone">
		<tr class="row header">
			<th class="col-md-2"></th>
			<% for (MarkedIndividual ind: inds) {%>
			<th class="col-md-2"><h2>
				<a href='<%=ind.getWebUrl(request)%>'><%=ind.getDisplayName()%></a>
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
        if(markA.getSex()!= null && markB.getSex()!= null && !markA.getSex().equals("") && !markB.getSex().equals("") && !markA.getSex().equals(markB.getSex())){
          %>
            <select name="taxonomy-dropdown" id="taxonomy-dropdown" class="">
            <option value="<%= markA.getGenusSpeciesDeep()%>" selected><%= markA.getGenusSpeciesDeep()%></option>
            <option value="<%= markB.getGenusSpeciesDeep()%>"><%= markB.getGenusSpeciesDeep()%></option>
          <%
        }else{
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

		<!--
		<tr class="row comments check_for_diff">
			<th>Notes</th>
			<% for (MarkedIndividual ind: inds) {%>
			<td class="col-md-2">
				<%=ind.getComments()%>
			</td>
			<%}%>
			<td class="col-md-2 merge-field">
				<%=markA.getMergedComments(markB, request, myShepherd)%>
			</td>
		-->

		</tr>
	</table>

  <input type="submit" name="Submit" value="Merge Individuals" id="mergeBtn" class="btn btn-md editFormBtn"/>

	</form>


	<script type="text/javascript">
  $(document).ready(function() {
    $("#mergeBtn").click(function(event) {

    	console.log("mergeBtn was clicked");
      event.preventDefault();
    	console.log("mergeBtn continues");

    	let id1="<%=indIdA%>";
    	let id2="<%=indIdB%>";
    	let fullNameA = '<%=fullNameA%>';
    	let fullNameB = '<%=fullNameB%>';

      let desiredIncrementalId = "test1"; //TODO update
      let deprecatedIncrementId = "test2"; //TODO update
    	let sex = $("#sex-dropdown").val();
    	let taxonomy = $("#taxonomy-dropdown").val();
      let projIdElems = $('[id^=proj-confirm-dropdown-]');
      let projIdConsolidated = '';
      let desiredIncrementalIdConsolidated = '';
      let deprecatedIncrementIdConsolidated = '';
      let optionElems = [];
      for(let i=0; i<projIdElems.length; i++){
        let currentElem = projIdElems[i];
        // console.log("currentElem is: ");
        // console.log(currentElem);
        let currentProjUuid = $(currentElem).attr('name');
        // console.log("uuid is: " + currentProjUuid);
        console.log("current element bearing selected is: ");
        console.log($(currentElem).find(":selected"));
        let currentDesiredIncrementalId = $(currentElem).find(":selected").text();
        optionElems = optionElems.concat($(currentElem).children('option[name="incremental-id-option"]'));
        // console.log("optionElems is:");
        // console.log(optionElems);
        if (i == projIdElems.length-1){
          projIdConsolidated += currentProjUuid; //I'm sure there's a more elegant solution, but passing an array of strings as semi-colon-separated single string :poop emoji:
          desiredIncrementalId += currentDesiredIncrementalId;
        }else{
          projIdConsolidated += currentProjUuid + ';';
          desiredIncrementalId += currentDesiredIncrementalId + ';';
        }
      }
      console.log("optionElems.length is: " + optionElems.length);
      desiredIncrementalIdArr = desiredIncrementalId.split(";");
      for(let i=0; i<optionElems.length; i++){
        let currentOptionElem = optionElems[i];
        // console.log("currentOptionElem is:");
        // console.log(currentOptionElem);
        let currentOptionVal = $(currentOptionElem).text();
        console.log("currentOptionVal is: " + currentOptionVal);
        if(!desiredIncrementalIdArr.includes(currentOptionVal)){
          if (i == projIdElems.length-1){
            deprecatedIncrementIdConsolidated += currentOptionVal;
          }else{
            deprecatedIncrementIdConsolidated += currentOptionVal + ';';
          }
        }
      }
      console.log("deprecatedIncrementIdConsolidated is " + deprecatedIncrementIdConsolidated);
      console.log("desiredIncrementalId is "+ desiredIncrementalId);
      console.log("projIdConsolidated is: " + projIdConsolidated);
      debugger;

    	// console.log("Clicked with id1="+id1+", id2="+id2+", sex="+sex+", tax="+taxonomy);

    	// $("#mergeForm").attr("action", "MergeIndividual");

      // $.post("/MergeIndividual", {
      // 	"id1": id1,
      // 	"id2": id2,
      // 	"sex": sex,
      // 	"taxonomy": taxonomy,
      //   "projIds": projIdConsolidated,
      //   "desiredIncrementalIds": desiredIncrementalIdConsolidated,
      //   "deprecatedIncrementIds": deprecatedIncrementIdConsolidated
      // },
      // function() {
		  //   updateNotificationsWidget();
      // 	var confirmUrl = '/mergeComplete.jsp?oldNameA='+fullNameA+'&oldNameB='+fullNameB+'&newId='+id1;
      // 	alert("Successfully merged individual! Now redirecting to "+confirmUrl);
			// 	window.location = confirmUrl;
      // })
      // .fail(function(response) {
      // 	alert("FAILURE!!");
      // });

			//document.forms['mergeForm'].submit();

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



<jsp:include page="footer.jsp" flush="true"/>

<!--<script src="javascript/underscore-min.js"></script>
<script src="javascript/backbone-min.js"></script>-->
