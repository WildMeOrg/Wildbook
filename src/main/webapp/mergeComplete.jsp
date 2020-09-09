<%@ page contentType="text/html; charset=iso-8859-1" language="java"
         import="org.ecocean.servlet.ServletUtilities,javax.servlet.http.HttpUtils,
org.json.JSONObject, org.json.JSONArray,
org.ecocean.media.*,
java.util.Properties,
org.ecocean.scheduled.*,
org.ecocean.identity.IdentityServiceLog,
java.util.ArrayList,org.ecocean.Annotation, org.ecocean.Encounter,
org.dom4j.Document, org.dom4j.Element,org.dom4j.io.SAXReader, org.ecocean.*, org.ecocean.grid.MatchComparator, org.ecocean.grid.MatchObject, java.io.File, java.util.Arrays, java.util.Iterator, java.util.List, java.util.Vector, java.nio.file.Files, java.nio.file.Paths, java.nio.file.Path" %>

<%

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("mergeComplete.jsp");

String oldNameA = request.getParameter("oldNameA");
String oldNameB = request.getParameter("oldNameB");
String newId = request.getParameter("newId");

context=ServletUtilities.getContext(request);
String langCode=ServletUtilities.getLanguageCode(request);
Properties props = new Properties();
props = ShepherdProperties.getProperties("merge.properties", langCode, context);

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
</style>

<script>
	$(document).ready(function() {
		replaceDefaultKeyStrings();
	});

	function replaceDefaultKeyStrings() {
	}

</script>

<div class="container maincontent">

<div class="row">
	<div class="col-col-sm-12 col-md-12 col-lg-12 col-xl-12">
		<h1><%=props.getProperty("mergeCompleteHeader") %></h1>
		
		<br>
		<p class="instructions"><span class="text-danger bg-danger">Fields displayed in red</span> may have conflicting values and need to be set manually on the Marked Individual's page.</p>
		<br>
		<%
		try {
			MarkedIndividual mark = myShepherd.getMarkedIndividualQuiet(newId);

			ArrayList<ScheduledIndividualMerge> merges = myShepherd.getAllIncompleteScheduledIndividualMerges();
			boolean scheduled = false;
			if (merges!=null) {
				for (ScheduledIndividualMerge merge : merges) {
					if (mark.getId().equals(merge.getPrimaryIndividual().getId())) {
						scheduled = true;
					}
				}
			}
			if (!scheduled) {
			%>
		
			<h3>Successfully Merged Individuals</h3>
		
			<p><%=props.getProperty("individual") %> <%=oldNameA%> <%=props.getProperty("and") %> <%=oldNameB%> <%=props.getProperty("wereCombined") %> <a href='<%=mark.getWebUrl(request)%>'><%=mark.getDisplayName()%></a>
			<br>	
			<p><%=props.getProperty("containsAll") %></p>
			<br>
			<%
			} else {

			%>


				<h3>Merge Scheduled</h3>
		
				<p><%=props.getProperty("individual") %> <%=oldNameA%> <%=props.getProperty("and") %> <%=oldNameB%> <%=props.getProperty("willBeCombined") %> <a href='<%=mark.getWebUrl(request)%>'><%=mark.getDisplayName()%></a>
				<br>	
				<p><%=props.getProperty("scheduledFor") %></p>
				<br>

			<%
			}
			%>
				
		</p>
	

	</div>

</div>


<%
} catch (Exception e) {
	System.out.printf("Exception on mergeComplete.jsp! oldNameA=%s, oldNameB=%s, newId=%s", oldNameA, oldNameB, newId);
	myShepherd.rollbackDBTransaction();
} finally {
	myShepherd.closeDBTransaction();
}
%>


</div>



<jsp:include page="footer.jsp" flush="true"/>

<!--<script src="javascript/underscore-min.js"></script>
<script src="javascript/backbone-min.js"></script>-->

