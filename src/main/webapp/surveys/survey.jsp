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
String surveyID = request.getParameter("surveyID").trim();
Survey sv = null;
String errors = "";

try {
	sv = myShepherd.getSurvey(surveyID);
} catch (NullPointerException npe) {
	npe.printStackTrace();
	errors += "<p>This survey does not belong to an actual survey or is mangled.</p><br/>";
}

String date = "";
String organization = "";
String project = "";
String type = "";
String effort = "";
String comments = "";
ArrayList<SurveyTrack> trks = new ArrayList<SurveyTrack>();
if (sv!=null) {
	if (sv.getProjectName()!=null) {
		project = sv.getProjectName();		
	}
	if (sv.getOrganization()!=null) {
		organization = sv.getOrganization();	
	}
	if (sv.getProjectName()!=null) {
		date = sv.getDate();
	}
	if (sv.getEffort()!=null) {
		effort = String.valueOf(sv.getEffort());
	}
	if (sv.getComments()!=null) {
		comments = sv.getComments();
	}
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
<link rel="stylesheet" href="css/ecocean.css" type="text/css" media="all"/>

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
				<p>Date: <%=date%></p>
				<p>[Add track/path/points]</p>
				<p>[Add occurrences]</p>
				<p>[Make points on map clickable]</p>
			<%	
			} 
			%>	
		
		</div>
		
		
		<div class="col-md-12">
			<p><strong><%=props.getProperty("allTracks") %></strong></p>
			<table id="trackTable" style="width:100%;">
				
				<tr class="lineItem">
					<td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("id") %></strong></td>
					<td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("vessel") %></strong></td>
					<td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("locationID") %></strong></td>
					<td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("type") %></strong></td>
					<td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("numPoints") %></strong></td>
					<td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("start") %></strong></td>
					<td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("end") %></strong></td>				
				</tr>
			
			<%
			for (SurveyTrack trk : trks) {
				String trkID  = trk.getID();
				String trkLocationID = trk.getLocationID();
				String trkVessel = trk.getVesselID();
				String trkType = trk.getType();
				String trkStart = "Unavailable";
				String trkEnd = "Unavailable";
				Path pth = null;
					if (trk.getPathID()!=null) {
						String pthID = trk.getPathID();			
						pth = myShepherd.getPath(pthID);
					} else {
						System.out.println("SurveyTrack "+trkID+" did not have an associated Path.");						
					}
			%>
				<tr>
					<td class="lineitem"><%=trkID%></td>
					<td class="lineitem"><%=trkVessel%></td>	
					<td class="lineitem"><%=trkLocationID%></td>	
					<td class="lineitem"><%=trkType%></td>
					<td class="lineitem">
						<%
						int numPoints = 0;
						if (pth!=null&&pth.getAllPointLocations()!=null) {
							numPoints = pth.getAllPointLocations().size();
							if (pth.getStartTime()!=null) {
								trkStart = pth.getStartTime();								
							}
							if (pth.getEndTime()!=null) {
								trkEnd = pth.getEndTime();								
							}
						}
						%>
						<%=numPoints%>
					</td>
					<td><%=trkStart%></td>
					<td><%=trkEnd%></td>	
				</tr>
				
			<%	
			}
			%>
		</table>
		<br/>
		<hr/>
		<br/>
		</div>
		<div class="col-md-12">
			<p><strong><%=props.getProperty("surveyMap") %></strong></p>
			<jsp:include page="surveyMapEmbed.jsp" flush="true">
         		 <jsp:param name="surveyID" value="<%=surveyID%>"/>
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
<%
myShepherd.closeDBTransaction();
%>


<jsp:include page="../footer.jsp" flush="true" />



