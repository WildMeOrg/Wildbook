<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.DateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.movement.*,
java.io.*,java.util.*, java.io.FileInputStream, java.util.Date, java.text.SimpleDateFormat, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%!
    private String niceDuration(Long ms) {
        if (ms == null) return "";
        if (ms < 8000) return ms + "ms";
        if (ms < 120 * 1000) return Math.round(ms / 1000) + "s";
        if (ms < 60 * 60 * 1000) return Math.round(ms / (60*1000)) + "m";
        if (ms < 24 * 60 * 60 * 1000) return Math.round(ms / (60*60*1000)) + "h" + Math.round((ms % (60*60*1000)) / 60000) + "m";
        return (Math.round(ms / (24 * 60 * 60 * 1000 * 10)) / 10) + "d";
    }
%>
<%
String context=ServletUtilities.getContext(request);
String langCode=ServletUtilities.getLanguageCode(request);
Shepherd myShepherd=new Shepherd(context);
//myShepherd.setAction("survey.jsp");

//response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
//response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
//response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
//response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility


Properties props = new Properties();
myShepherd.beginDBTransaction();
props = ShepherdProperties.getProperties("survey.properties", langCode,context);
String surveyID = request.getParameter("surveyID").trim();
Survey sv = null;
String errors = "";
String urlLoc = "//" + CommonConfiguration.getURLLocation(request);
String occLocation = urlLoc + "/occurrence.jsp?number=";

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

String type = "";
String effort = "";
String comments = "";
String numOccurrences = "";
String surveyAttributes = "";
String effortData = "";
String surveyStart = "";
String surveyEnd = "";
ArrayList<SurveyTrack> trks = new ArrayList<SurveyTrack>();
if (sv!=null) {
	if (sv.getProjectName()!=null) {
		surveyAttributes += "<p>Project Name: "+sv.getProjectName()+"</p>";
	}
	if (sv.getProjectType()!=null) {
		surveyAttributes += "<p>Project Type: "+sv.getProjectType()+"</p>";
	}
	if (sv.getOrganization()!=null) {
		surveyAttributes += "<p>Organization: "+sv.getOrganization()+"</p>";
	}
	surveyStart = sv.getStartDateTime();
	surveyEnd = sv.getEndDateTime();

	if (surveyStart!=null) {
		surveyAttributes +=  "<p>Date: "+surveyStart+"</p>";
	}
	if (surveyEnd!=null) {
		surveyAttributes += "<p>End: "+surveyEnd+"</p>";
	}

        Long tstart = sv.getStartTimeTracks();
        Long tend = sv.getEndTimeTracks();

        if (tstart != null) surveyAttributes += "<p>Start (of first track): " + new DateTime(tstart).toString().substring(0,16) + "</p>";
        if (tend != null) surveyAttributes += "<p>End (of last track): " + new DateTime(tend).toString().substring(0,16) + "</p>";

        Long sdur = sv.getComputedDuration();
        if (sdur != null) effortData += "<p>Computed duration: " + niceDuration(sdur) + "</p>";
        Long sdurT = sv.getComputedDurationTracks();
        if (sdurT != null) effortData += "<p>Computed duration (tracks, start-to-end): " + niceDuration(sdurT) + "</p>";
        Long sdurSum = sv.getComputedDurationTrackSum();
        if (sdurSum != null) effortData += "<p>Computed duration (sum of track durations): " + niceDuration(sdurSum) + "</p>";

	if (sv.getEffort()!=null) {
		Measurement effortMeasurement = sv.getEffort();
		String value = String.valueOf(effortMeasurement.getValue());
		effortData += "<p>Calculated: "+value+" Hrs</p>";
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
<script type="text/javascript" src="../javascript/markerclusterer/markerclusterer.js"></script>
<script type="text/javascript" src="https://cdn.rawgit.com/googlemaps/js-marker-clusterer/gh-pages/src/markerclusterer.js"></script> 
<script src="../javascript/oms.min.js"></script>

<link rel="stylesheet" href="../css/ecocean.css" type="text/css" media="all"/>
<jsp:include page="../header.jsp" flush="true" />

<div class="container maincontent">
	<div class="row">
		<div class="col-md-12">
			<h3 class="surveyHeader">
				<img src="../images/survey_icon_boat.png" />
				<%=props.getProperty("survey") %>: <%=surveyID %>
			</h3>
			<p>The survey contains collections of occurrences, survey tracks and points. It allows you to look at total effort and distance.</p>
			<hr/>
			<div id="errorSpan"></div>
		</div>	
		<%
		if (sv!=null) {
		%>
		<div class="col-md-6">
			<h4>Survey Attributes</h4>
			<!-- Collected Above -->
			<%=surveyAttributes%>
			
			<%
			if (trks!= null) {
			%>
				<p>Num survey tracks: <%=trks.size()%></p>
			<% 
			}
			%>
		</div>
		<div class="col-md-6">
			<h4>Total Effort</h4>
			<%
			if (trks!= null) {
			%>
				<%=effortData%>
			<% 
			}
			%>			
		</div>
		<%
		} 
		%>	
		<hr/>
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
					<td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("duration") %></strong></td>				
				</tr>	
			<%
                        int occKey = 0;
			for (SurveyTrack trk : trks) {
				String trkID  = trk.getID();
				String trkLocationID = "Unavailable";
				if (trk.getLocationID()!=null) {
					trkLocationID = trk.getLocationID();
				}
				String trkVessel = "Unavailable";
				if (trk.getVesselID()!=null) {
					trkVessel = trk.getVesselID();
				}
				String trkType = "Unavailable";
				if (trk.getType()!=null) {
					trkType = trk.getType();
				}
				String trkStart = "Unavailable";
				String trkEnd = "Unavailable";
				Path pth = trk.getPath();
				int numOccs = 0;
				ArrayList<Occurrence> occs = null;
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
										String thisOccID = occ.getID();
										String link = occLocation + thisOccID;
                                                                                String taxs = "";
                                                                                if (!Util.collectionIsEmptyOrNull(occ.getTaxonomies())) {
                                                                                    for (Taxonomy tx : occ.getTaxonomies()) {
                                                                                        taxs += tx.getScientificName() + "; ";
                                                                                    }
                                                                                }
                                                                                String when = "-";
                                                                                if (occ.getDateTimeCreated() != null) when = occ.getDateTimeCreated().substring(11,16);
									%>
									<p>
										<small>(<%=Character.toString((char)(occKey+65))%>) <a href="<%=link%>"><%=thisOccID.substring(0,8)%></a> &nbsp; <b><%=when%></b> <i><%=taxs%></i></small>
									</p>
									<%
                                                                            occKey++;
									}
								} else {
								%>	
									<p>
										<small>No occurrences.</small>	
									</p>
								<% 	
								}
								%>
								<!-- Add another occ to the selected track here... -->
								<form name="addOcc" action="../AddOccToTrack" method="post" class="addOccToTrack">
									<input name="trackID" value="<%=trkID%>" type="hidden"  id="addOccInput1" />
									<input name="surveyID" value="<%=surveyID%>" type="hidden"  id="addOccInput2" />
									<div class="input-group">
									</div>
										<div class="row"> 
											<div class="input-group">
												<div class="col-sm-8">
													<input name="occID" title="addOccID" type="text" class="form-control" id="addOccInput3" />
												</div>
												<div class="col-sm-4">
													<span class="input-group-btn">
														<input name="Add Occurrence" style="margin-bottom:13px;margin-top:2px;" type="submit" id="addOccToTrackButton" value="<%=props.getProperty("addOcc")%>" class="btn btn-sm addOccButton" />
													</span>
												</div>
											</div>
										</div>
										<span><small>Enter an existing Occurrence ID to add it to the selected survey track.</small></span>
								</form>
						</div>
					</td>	
					<td class="lineitem"><%=trkVessel%></td>	
					<td class="lineitem"><%=trkLocationID%></td>	
					<td class="lineitem"><%=trkType%></td>
					<td class="lineitem"><%=numOccs%></td>	
					<td class="lineitem">
						<% 
                                                Long trkDur = trk.getComputedDuration();
						int numPoints = 0;
						if (pth!=null&&pth.getAllPointLocations()!=null) {
							numPoints = pth.getAllPointLocations().size();
							ArrayList<PointLocation> pts = pth.getAllPointLocations();

							// Lets get a time range from the occs instead. Because it's better.
							try {
								if (pth.getStartTime()!=null) {
									trkStart = pth.getStartTime();					
								}
								if (pth.getEndTime()!=null) {
									trkEnd = pth.getEndTime();								
								}								
							} catch (NullPointerException npe) {
								npe.printStackTrace();
							}
						}
						%>
						<%=numPoints%>
					</td>
					<td class="lineitem"><%=trkStart%></td>
					<td class="lineitem"><%=trkEnd%></td>	
					<td class="lineitem"><%=((trkDur == null) ? "-" : niceDuration(trkDur))%></td>	
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
	$(document).ready(function(){	
	  $(".editFormObservation").hide();
	  var buttons = $("#editDynamic, #closeEditDynamic").on("click", function(){
	    buttons.toggle();
	  });
	  $("#editDynamic").click(function() {
	    $("#editInstructions, .editFormObservation, #addObservationForm").show();
	  });
	  $("#closeEditDynamic").click(function() {
	    $("#editInstructions, .editFormObservation, #addObservationForm").hide();
	  });
	});
</script>
				<%
				if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
				%>
					<h2>
						<img src="../images/lightning_dynamic_props.gif" />
						<%=props.getProperty("dynamicProperties")%></h2>
							<button class="btn btn-md" type="button" name="button"
								id="editDynamic">Edit</button>
							<button class="btn btn-md" type="button" name="button"
								id="closeEditDynamic" style="display: none;">Close Edit</button>
					</h2>
					<p id="editInstructions" style="display:none;"><small>Set any value to zero (0) to remove.</small></p>
					<br/>
				<%
						// Let's make a list of editable Observations... Dynamically!
						
				if (sv!=null&&sv.getObservationArrayList()!=null) {
					ArrayList<Observation> obs = sv.getObservationArrayList();
					//System.out.println("Observations ... "+obs);
					int numObservations = sv.getObservationArrayList().size();
					for (Observation ob : obs) {
						
						String nm = ob.getName();
						String vl = ob.getValue();
				%>		
				
					<p><em><%=nm%></em>:&nbsp<%=vl%></p>
					<div style="display:none;" id="dialogDP<%=nm%>" class="editFormObservation" title="<%=props.getProperty("set")%> <%=nm%>">
						<p class="editFormObservation">
							<strong><%=props.getProperty("set")%> <%=nm%></strong>
						</p>
						<form name="editFormObservation" action="../SurveySetObservation" method="post" class="editFormDynamic">
							<input name="name" type="hidden" value="<%=nm%>" /> 
							<input name="number" type="hidden" value="<%=surveyID%>" />
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

			<%} 
				if (numObservations==0) {%>
				
					<br/>
					<p style="margin-top:1em;"><%=props.getProperty("none")%></p>	
					<br/>
			<%
				}
			} 
			%>
			<div style="display: none;" id="addObservationForm"
				title="<%=props.getProperty("addDynamicProperty")%>"
				class="editFormObservation">
				<p class="editFormObservation">
					<strong><%=props.getProperty("addDynamicProperty")%></strong>
				</p>
				<form name="addDynProp" action="../SurveySetObservation"
					method="post" class="editFormObservation">
					<input name="number" type="hidden" value="<%=surveyID%>" />
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
		<%
		}
		%>

<div>
<h2>Comments</h2>
<p>
<%=comments%>
</p>
</div>

	</div>			
	<br/>
	<hr/>
</div>


<script>
$(document).ready(function() {
	$('#errorSpan').html('<%=errors%>');
/*
	$('.occIDDiv').hide();
	$('.hideOccIDs').hide();
*/
	$('.showOccIDs').hide();
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



