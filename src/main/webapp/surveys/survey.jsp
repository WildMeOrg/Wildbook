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

String number = request.getParameter("number").trim();
String mapKey = CommonConfiguration.getGoogleMapsKey(context);

Survey sv = myShepherd.getSurvey(number);
ArrayList<SurveyTrack> trks = new ArrayList<SurveyTrack>();
String errors = "";
String date = "";
String organization = "";
String project = "";
if (sv!=null) {
	project = sv.getProjectName();
	organization = sv.getOrganization();
	date = sv.getDate();
	
	if (sv.getAllSurveyTracks().size()>0) {
		trks = sv.getAllSurveyTracks();
	}
	
} else {
	errors += "<p>There was no valid Survey for this ID.</p><br/>";
}

%>


<jsp:include page="../header.jsp" flush="true" />

<script src="//maps.google.com/maps/api/js?key=<%=mapKey%>&language=<%=langCode%>"></script>	

<div class="container maincontent">
	<div class="row">
		<div class="col-md-12">
			<h3><%=props.getProperty("survey") %></h3>
			<hr/>
			<div id="errorSpan"></div>
			

		
		</div>
		
		<div class="col-md-12">
			<p><strong><%=props.getProperty("allTracks") %></strong></p>
			

		
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



