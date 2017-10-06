<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.movement.*,
java.io.*,java.util.*, java.io.FileInputStream, java.util.Date, java.text.SimpleDateFormat, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%
String context="context0";
context=ServletUtilities.getContext(request);
String langCode=ServletUtilities.getLanguageCode(request);
Shepherd myShepherd=new Shepherd(context);

Properties props = new Properties();

myShepherd.beginDBTransaction();
props = ShepherdProperties.getProperties("survey.properties", langCode,context);

String occID = "none";
if (request.getParameter("occID")!=null) {
occID = request.getParameter("occID").trim();	
}
String surveyID = request.getParameter("surveyID").trim();

Survey sv = myShepherd.getSurvey(surveyID);
ArrayList<SurveyTrack> trks = new ArrayList<SurveyTrack>();
String errors = "";
String date = "";
String organization = "";
String project = "";
if (sv!=null) {
	if (sv.getProjectName()!=null) {
		project = sv.getProjectName();		
	}
	organization = sv.getOrganization();
	date = sv.getDate();
	
	if (sv.getAllSurveyTracks()!=null&&sv.getAllSurveyTracks().size()>0) {
		trks = sv.getAllSurveyTracks();
	} else {
		errors += "<p>Survey tracks were null or did not exist.</p><br/>";
	}
	
	
} else {
	errors += "<p>There was no valid Survey for this ID.</p><br/>";
}
%>

<jsp:include page="../header.jsp" flush="true" />
<script type="text/javascript" src="../javascript/markerclusterer/markerclusterer.js"></script>
<script type="text/javascript" src="https://cdn.rawgit.com/googlemaps/js-marker-clusterer/gh-pages/src/markerclusterer.js"></script> 
<script src="../javascript/oms.min.js"></script>

<div class="container maincontent">
	<div class="row">
		<div class="col-md-12">
			<h3><%=props.getProperty("survey") %></h3>
			<p>The survey contains collections of occurrences and points. It allows you to look at total effort and distance.</p>
			<hr/>
			<div id="errorSpan"></div>
		
		</div>
		<div class="col-md-12">
			<h4>Survey Attributes</h4>
			<%
			if (sv!=null) {
			%>
				<p>Project: <%=project%></p>
				<p>Organization: <%=organization%></p>
				<p>Date: <%=sv.getDate() %></p>
				
				
				<p>[Add track/path/points]</p>
				<p>[Add occurrences]</p>
				<p>[Make points on map clickable]</p>
			<%	
			} 
			%>	
		
		</div>
		
		
		<div class="col-md-12">
			<p><strong><%=props.getProperty("allTracks") %></strong></p>
			
		</div>
		<hr/>
		<div class="col-md-12">
			<p><strong><%=props.getProperty("surveyMap") %></strong></p>
			<jsp:include page="surveyMapEmbed.jsp" flush="true">
         		 <jsp:param name="occID" value="<%=occID%>"/>
        	</jsp:include>
		</div>
		
		<label class="response"></label>
	</div>
</div>

<script>
$(document).ready(function() {
	$('#errorSpan').html('<%=errors%>');
});
</script>

<jsp:include page="../footer.jsp" flush="true" />



