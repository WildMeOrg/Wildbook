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

String oldNameA = request.getParameter("oldNameA");
String oldNameB = request.getParameter("oldNameB");
String newId = request.getParameter("newId");

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

<h1>Check it out! It's a merge tool!</h1>
<p class="instructions">Confirm the merged values for each of the fields below.</p>
<p class="instructions"><span class="text-danger bg-danger">Fields in red</span> have conflicting values and require attention.</p>
<%
try {
	MarkedIndividual mark = myShepherd.getMarkedIndividualQuiet(newId);

	%>

	<h1>Successfully Merged Individuals</h1>

	<p>Individual <%=oldNameA%> and <%=oldNameB%> were combined to make <a href='<%=mark.getWebUrl(request)%>'><%=mark.getDisplayName()%></a>
		
</p>

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

