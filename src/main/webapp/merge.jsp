<%@ page contentType="text/html; charset=iso-8859-1" language="java"
         import="org.ecocean.servlet.ServletUtilities,javax.servlet.http.HttpUtils,
org.json.JSONObject, org.json.JSONArray,
org.ecocean.media.*,
org.ecocean.identity.IdentityServiceLog,
java.util.ArrayList,org.ecocean.Annotation, org.ecocean.Encounter,
org.dom4j.Document, org.dom4j.Element,org.dom4j.io.SAXReader, org.ecocean.*, org.ecocean.grid.MatchComparator, org.ecocean.grid.MatchObject, java.io.File, java.util.Arrays, java.util.Iterator, java.util.List, java.util.Vector, java.nio.file.Files, java.nio.file.Paths, java.nio.file.Path" %>

<%

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("merge.jsp");

String indIdA = request.getParameter("individualA");
String indIdB = request.getParameter("individualB");

String newId = indIdA;

MarkedIndividual markA = myShepherd.getMarkedIndividualQuiet(indIdA);
MarkedIndividual markB = myShepherd.getMarkedIndividualQuiet(indIdB);
MarkedIndividual[] inds = {markA, markB};

String fullNameA = indIdA;
if (markA!=null) fullNameA += " ("+markA.getDisplayName()+")";
String fullNameB = indIdB;
if (markB!=null) fullNameB += " ("+markB.getDisplayName()+")";



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
	});

	function replaceDefaultKeyStrings() {
		$('span.nameKey').each(function(i, el) {
			var fixedHtml = $(this).html().replace("*","Default").replace("_legacyIndividualID_","Legacy IndividualID").replace("_nickName_","nickname").replace("_alternateID_","Alternate ID");
			$(this).html(fixedHtml);
		});
	}

	function highlightMergeConflicts() {
		$(".row.check_for_diff").each(function(i, el) {
			var val1 = $(this).children("td.diff_check").first().html().trim();
			var val2 = $(this).children("td.diff_check").last().html().trim();
			var val3 = $(this).find("input").val();
			console.log("index="+i+" val1="+val1+", val2="+val2+" and val3="+val3);
			if (val3!==val1 && val3!==val2) {
				$(this).addClass('needs_review');
				$(this).addClass('text-danger');
				$(this).addClass('bg-danger');
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
	<table class="compareZone">
		<tr class="row header">
			<th class="col-md-2"></th>
			<% for (MarkedIndividual ind: inds) {%>
			<th class="col-md-2"><h2>
				<a href='<%=ind.getWebUrl(request)%>'><%=ind.getDisplayName()%></a>
			</h2></th>
			<%}%>
			<th><h2>
				Merged Individual
			</h2></th>
		</tr>

		<tr class="row names">
			<th>Names</th>
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



		<tr class="row encounters">
			<th># Encounters</th>
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
			<th>Species</th>
			<% for (MarkedIndividual ind: inds) {%>
			<td class="col-md-2 diff_check">
				<%=ind.getTaxonomyString()%>
			</td>
			<%}%>

			<td class="merge-field">

				<% 
				String mergeTaxy = Util.betterValue(markA.getTaxonomyString(), markB.getTaxonomyString());
				%>
				 <input name="taxonomy" type="text" class="" id="taxonomyInput" value="<%=mergeTaxy%>"/>
			</td>
		</tr>

		<tr class="row sex check_for_diff">
			<th>Sex</th>
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
				<%=markA.getMergedComments(markB, request)%>
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

