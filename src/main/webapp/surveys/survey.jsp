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

boolean isOwner = false;
if (request.getUserPrincipal()!=null) {
  isOwner = true;
}

try {
	sv = myShepherd.getSurvey(surveyID);
} catch (NullPointerException npe) {
	npe.printStackTrace();
	errors += "<p>This survey ID does not belong to an existing survey.</p><br/>";
}

String date = "";
String organization = "";
String project = "";
String type = "";
String effort = "";
String comments = "";
String numOccurrences = "";
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
<link rel="stylesheet" href="../css/ecocean.css" type="text/css" media="all"/>

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
				<p>[Display total effort prominently.]</p>
			<%
				if (trks!= null) {
					%>
						<p>Num survey tracks: <%=trks.size()%></p>
					<% 
				}
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
					<td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("numOccs") %></strong></td>
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
				int numOccs = 0;
				ArrayList<Occurrence> occs = null;
				if (trk.getPathID()!=null) {
					String pthID = trk.getPathID();			
					pth = myShepherd.getPath(pthID);
				} else {
					System.out.println("SurveyTrack "+trkID+" did not have an associated Path.");						
				}
				if (trk.getAllOccurrences()!=null) {
					occs = trk.getAllOccurrences();
					numOccs = trk.getAllOccurrences().size();
				}
			%>
				<tr>
					<td class="lineitem">
						<%=trkID%>
						<input name="Show Occurrence ID's" type="button" class="showOccIDs occID-<%=trkID%>" value="<%=props.getProperty("showOccurrences")%>" class="btn btn-sm" />
						<input name="Hide Occurrence ID's" type="button" class="hideOccIDs occID-<%=trkID%>" value="<%=props.getProperty("hideOccurrences")%>" class="btn btn-sm" />
						<div class="occIDDiv">
								<%
								System.out.println("Hit Occs in table...");
								if (occs!=null) {
								%>
									<label><small>Occurrence ID's:</small></label>
									<%
									for (Occurrence occ : occs) {
										String thisOccID = occ.getPrimaryKeyID();
									%>
										<p>
											<small><%=thisOccID%></small>
										</p>
									<%
	
									}
								} else {
								%>	
										<p>
											<small>No occurrences.</small>	
										</p>
								<% 	
								}
								%>
						</div>
					</td>
					<td class="lineitem"><%=trkVessel%></td>	
					<td class="lineitem"><%=trkLocationID%></td>	
					<td class="lineitem"><%=trkType%></td>
					<td class="lineitem"><%=numOccs%></td>	
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
					<td class="lineitem"><%=trkStart%></td>
					<td class="lineitem"><%=trkEnd%></td>	
				</tr>
			<%	
			}
			%>
		</table>
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
	
	
	<div id="surveyObservations">
	
				  <!-- Observations Column -->
<script type="text/javascript">
	$(document).ready(function() {
	  $(".editFormObservation").hide();
	  var buttons = $("#editDynamic, #closeEditDynamic").on("click", function(){
	    buttons.toggle();
	  });
	  $("#editDynamic").click(function() {
	    $("#editInstructions, .editFormObservation").show();
	  });
	  $("#closeEditDynamic").click(function() {
	    $("#editInstructions, .editFormObservation").hide();
	  });
	});
</script>
					<%
					if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
					%>
						<h2>
							<img src="../images/lightning_dynamic_props.gif" />
							<%=props.getProperty("dynamicProperties")%>
							<button class="btn btn-md" type="button" name="button"
								id="editDynamic">Edit</button>
							<button class="btn btn-md" type="button" name="button"
								id="closeEditDynamic" style="display: none;">Close Edit</button>
						</h2>
						<p id="editInstructions" style="display:none;"><small>Set any value to zero to remove.</small></p>
					<%
					} else {
					%>
					<h2>
						<img src="../images/lightning_dynamic_props.gif" />
						<%=props.getProperty("dynamicProperties")%></h2>
					<%
					}
							// Let's make a list of editable Observations... Dynamically!
							
					if (sv!=null&&sv.getBaseObservationArrayList()!=null) {
						ArrayList<Observation> obs = sv.getBaseObservationArrayList();
						System.out.println("Observations ... "+obs);
						int numObservations = sv.getBaseObservationArrayList().size();
						for (Observation ob : obs) {
							
							String nm = ob.getName();
							String vl = ob.getValue();
					%>
							
							<p><em><%=nm%></em>:<%=vl%></p>
									<!-- Start dynamic (Observation) form. -->
									<!-- REMEMBER! These observations use a lot of legacy front end html etc from the deprecated dynamic properties! -->
							<div style="display:none;" id="dialogDP<%=nm%>" class="editFormObservation" title="<%=props.getProperty("set")%> <%=nm%>">
								<p class="editFormObservation">
									<strong><%=props.getProperty("set")%> <%=nm%></strong>
								</p>
								<form name="editFormObservation" action="../BaseClassSetObservation" method="post" class="editFormDynamic">
									<input name="name" type="hidden" value="<%=nm%>" /> 
									<input name="number" type="hidden" value="<%=surveyID%>" />
									<!-- This servlet can handle encounters or occurrences, so you have to pass it the Type!  -->
									<input name="type" type="hidden" value="Occurrence" />
									<div class="form-group row">
										<div class="col-sm-3">
											<label><%=props.getProperty("propertyValue")%></label>
										</div>
										<div class="col-sm-5">
											<input name="value" type="text" class="form-control" id="dynInput" value="<%=vl%>"/>
										</div>
										<div class="col-sm-4">
											<input name="Set" type="submit" id="dynEdit" value="<%=props.getProperty("initCapsSet")%>" class="btn btn-sm editFormBtn" />
										</div>
									</div>
								</form>
							</div>
							
				<%} // Enc
						if (numObservations == 0) {%>
							<p><%=props.getProperty("none")%></p>
				<%}
				} else {
				%>
				<p><%=props.getProperty("none")%></p>
				<%}%>
			<div style="display: none;" id="dialogDPAdd"
				title="<%=props.getProperty("addDynamicProperty")%>"
				class="editFormObservation">
				<p class="editFormObservation">
					<strong><%=props.getProperty("addDynamicProperty")%></strong>
				</p>
				<form name="addDynProp" action="../SurveySetObservation"
					method="post" class="editFormObservation">
					<input name="number" type="hidden" value="<%=surveyID%>" />
					<input name="type" type="hidden" value="Occurrence" />
					<div class="form-group row">
						<div class="col-sm-3">
							<label><%=props.getProperty("propertyName")%></label>
						</div>
						<div class="col-sm-5">
							<input name="name" type="text" class="form-control" id="addDynPropInput" />
						</div>
					</div>
					<div class="form-group row">
						<div class="col-sm-3">		
							<label><%=props.getProperty("propertyValue")%></label>
						</div>
						<div class="col-sm-5">
							<input name="value" type="text" class="form-control" id="addDynPropInput2" />
						</div>
						<div class="col-sm-4">
							<input name="Set" type="submit" id="addDynPropBtn" value="<%=props.getProperty("initCapsSet")%>" class="btn btn-sm editFormBtn" />
						</div>
					</div>
				</form>
			</div>		
		</div>			

		
	</div>
	
	
</div>

<script>
$(document).ready(function() {
	$('#errorSpan').html('<%=errors%>');
	$('.occIDDiv').hide();
	$('.hideOccIDs').hide();
	$('.showOccIDs').click(function(){
		console.log('Show Occ IDs!');
		$('.occIDDiv').slideDown();
		$('.showOccIDs').hide();
		$('.hideOccIDs').show();
	});
	$('.hideOccIDs').click(function(){
		console.log('Hide Occ Ids!');
		$('.occIDDiv').slideUp();
		$('.showOccIDs').show();
		$('.hideOccIDs').hide();
	});
});


</script>
<%
myShepherd.closeDBTransaction();
%>


<jsp:include page="../footer.jsp" flush="true" />



