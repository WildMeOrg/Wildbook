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
    doAjaxForProject(requestJsonForProjectNamesDropdown);
    // callForIncrementalIdsAndPopulate();
	});

  function addListeners(projectNameResults){
    // let projNameOptions = projectNameResults.map(entry =>{return entry.researchProjectName});
    let prjIdOptions = projectNameResults.map(entry =>{return entry.researchProjectId});
    for(let i = 0; i<prjIdOptions.length; i++){
      let projId = prjIdOptions[i];
      callForIncrementalIdsAndPopulate(projId);
    }
    // $('#proj-id-dropdown').change(function(){
    //   let projName = $( "#proj-id-dropdown" ).val();
    //   let indexMatch = projNameOptions.indexOf(projName);
    //   console.log("indexMatch is: " + indexMatch);
    //   let projId = prjIdOptions[indexMatch];
    //
    //   callForIncrementalIdsAndPopulate(projId);
    // });
  }

  function callForIncrementalIdsAndPopulate(projId){
    let requestJSON = {};
    requestJSON['researchProjectId'] = projId;
    // incrementalIds = [];
    requestJSON['individualIds'] = [];
    <% for (MarkedIndividual ind: inds) {%>
      requestJSON['individualIds'].push({indId: "<%= ind.getIndividualID()%>"});
    <%}%>
    // console.log("requestJSON before going into ajax call in callForIncrementalIdsAndPopulate:");
    // console.log(requestJSON);
    doAjaxForProject(requestJSON);
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
            projectNameResults = data.projects;//data.;//TODO
            if(incrementalIdResults){
              console.log("1: incrementalIdResults!");
              console.log(incrementalIdResults);
              populateProjectIdRow(incrementalIdResults);
              // addListeners();
            }else{
              if(projectNameResults){
                // console.log("2: projectNameResults!");
                // console.log(projectNameResults);
                let projNameOptions = projectNameResults.map(entry =>{return entry.researchProjectName});
                let prjIdOptions = projectNameResults.map(entry =>{return entry.researchProjectId});
                // if(!$( "#proj-id-dropdown" ).val()){ // if the html hasn't been populated at all yet, do that
                  if(projNameOptions.length>0){
                    // console.log("got here. projNameOptions[0] is: " + projNameOptions[0]);
                    // callForIncrementalIdsAndPopulate(prjIdOptions[0]);
                    populateProjectNameDropdown(projNameOptions);
                  }else{
                    callForIncrementalIdsAndPopulate("temp"); //TODO revise
                    // populateProjectNameDropdown(['<%= props.getProperty("NoProjects")%>']);
                  }
                // }
                // populateProjectNameDropdown(projNameOptions);
                addListeners(projectNameResults);
                // console.log("adding "+ projectNameResults.researchProjectId);
                // $('#current-proj-id-display').text(projectNameResults.researchProjectId);
              }else{
                // console.log("Ack should not happen");
              }
            }
            // $('#progress-div').hide();
            // $('#table-div').show();
        },
        error: function(x,y,z) {
            console.warn('%o %o %o', x, y, z);
        }
    });
  }

  function populateProjectIdRow(incrementalIds){
    // console.log("data in populateProjectIdRow is: ");
    // console.log(populateProjectIdRow);
    // console.log("populateProjectIdRow called");
    // console.log("inds.length is "+ <%= inds.length %>);
    let projectIdHtml = '';
    projectIdHtml += '<th><%= props.getProperty("ProjectId") %>';
    if(incrementalIds.length>0){
      projectIdHtml += '<span id="current-proj-id-display"><em> ' + incrementalIds[0].projectName + '</em></span>'
    }
    projectIdHtml += '</th>';
    <% for (int i=0; i<inds.length; i++) {%>
    projectIdHtml += '<td class="col-md-2 diff_check">';
    if(incrementalIds && incrementalIds[<%=i%>]){
      projectIdHtml += incrementalIds[<%=i%>].projectIncrementalId;
    }else{
      projectIdHtml += '<%= props.getProperty("NoIncrementalId") %>';
    }
    projectIdHtml += '</td>';
    <%}%>
    projectIdHtml += '<td class="merge-field">';
    if(incrementalIds && incrementalIds.length>0){
      projectIdHtml += '<select name="proj-confirm-dropdown" id="proj-confirm-dropdown" class="form-control">';
      // projectIdHtml += '<option value="" selected="selected"></option>';
      for(let i=0; i<incrementalIds.length; i++){
        projectIdHtml += '<option value="'+ incrementalIds[i].projectIncrementalId +'" selected="selected">'+ incrementalIds[i].projectIncrementalId +'</option>';
      }
    }
    projectIdHtml += '</td>';
    $("#project-id-table-row").empty();
    $("#project-id-table-row").append(projectIdHtml);
  }

  function populateProjectNameDropdown(options){
    // console.log("populateProjectNameDropdown called");
    // console.log("options are");
    // console.log(options);
    let projectNameHtml = '';
    projectNameHtml += '<select name="proj-id-dropdown" id="proj-id-dropdown" class="form-control" >';
    // let selected = false;
    projectNameHtml += '<option value=""></option>';
    for(let i=0; i<options.length; i++){
      // console.log("in for loop and i is: " + i);
      // console.log(options[i]);
      if(i == 0){
        // selected = true;
        // console.log("true with " + options[i]);
        projectNameHtml += '<option value="'+ options[i] +'" selected>'+ options[i] +'</option>';
      }else{
        // selected = false;
        // console.log("false with " + options[i]);
        projectNameHtml += '<option value="'+ options[i] + '">'+ options[i] +'</option>';
      }
    }
    // console.log("projectNameHtml is:");
    // console.log(projectNameHtml);
    $("#proj-id-dropdown-container").empty();
    $("#proj-id-dropdown-container").append(projectNameHtml);
  }

  function updateProject(){
    console.log("updateProject clicked");
    let projId = $('#current-proj-id-display').text();
    console.log("projId is: " + projId);
    let desiredIncrementalId = $('#proj-confirm-dropdown').find(":selected").text();
    console.log("desiredIncrementalId is: " + desiredIncrementalId);
    if(<%= inds.length%> == 2){
      console.log("got into if branch in updateProject");
      let individualAId = '<%= indIdA%>';
      console.log("individualAId is: " + individualAId);
      let individualBId = '<%= indIdB%>';
      console.log("individualBId is: " + individualBId);
      //TODO call ProjectUpdate ajax;
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
<h4><%= props.getProperty("WhichProject") %></h4>
  <div id="proj-id-dropdown-container">
  </div>
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

    <tr class="row projectId check_for_diff" id="project-id-table-row">
      <!--populated by JS after page load-->
		</tr>



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
				%>
				 <input name="taxonomy" type="text" class="" id="taxonomyInput" value="<%=mergeTaxy%>"/>
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
				%>
				 <input name="sex" type="text" class="" id="sexInput" value="<%=mergeSex%>"/>
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

  <input type="submit" name="Submit" value="Merge Individuals" id="mergeBtn" class="btn btn-md editFormBtn" onclick="updateProject()"/>

	</form>


	<script type="text/javascript">
  $(document).ready(function() {
    $("#mergeBtn").click(function(event) {

    	console.log("mergeBtn was clicked");
      event.preventDefault();
    	console.log("mergeBtn continues");

    	var id1="<%=indIdA%>";
    	var id2="<%=indIdB%>";
    	var fullNameA = '<%=fullNameA%>';
    	var fullNameB = '<%=fullNameB%>';

    	var sex = $("#sexInput").val();
    	var taxonomy = $("#taxonomyInput").val();
    	console.log("Clicked with id1="+id1+", id2="+id2+", sex="+sex+", tax="+taxonomy);

    	$("#mergeForm").attr("action", "MergeIndividual");

      $.post("/MergeIndividual", {
      	"id1": id1,
      	"id2": id2,
      	"sex": sex,
      	"taxonomy": taxonomy
      },
      function() {
		updateNotificationsWidget();
      	var confirmUrl = '/mergeComplete.jsp?oldNameA='+fullNameA+'&oldNameB='+fullNameB+'&newId='+id1;
      	alert("Successfully merged individual! Now redirecting to "+confirmUrl);
				window.location = confirmUrl;

      })
      .fail(function(response) {
      	alert("FAILURE!!");
      });

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
