<%@ page contentType="text/html; charset=utf-8" language="java"
         import="javax.jdo.Query,org.ecocean.*,org.ecocean.servlet.ServletUtilities,java.io.File, java.util.*, org.ecocean.genetics.*, org.ecocean.security.Collaboration, 
         com.google.gson.Gson,
         org.ecocean.datacollection.Instant,
         org.ecocean.*,
         org.ecocean.tag.*,
         org.datanucleus.api.rest.orgjson.JSONObject
         " %>

<%

boolean isLoggedIn=false;
if(request.getUserPrincipal()!=null)isLoggedIn=true;
String blocker = "";
String context="context0";
context=ServletUtilities.getContext(request);

  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

  //setup data dir
  String rootWebappPath = getServletContext().getRealPath("/");
  File webappsDir = new File(rootWebappPath).getParentFile();
  File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
  File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");

  String langCode=ServletUtilities.getLanguageCode(request);
  
  Properties props = new Properties();
  props = ShepherdProperties.getProperties("occurrence.properties", langCode,context);
  
  Properties encProps = new Properties();
  encProps = ShepherdProperties.getProperties("encounter.properties", langCode,context);

  Properties collabProps = new Properties();
  collabProps=ShepherdProperties.getProperties("collaboration.properties", langCode, context);

  String number = request.getParameter("number").trim();
  
  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("occurrence.jsp");

  boolean isOwner = false;
  if (request.getUserPrincipal()!=null) {
    isOwner = true;
  }

%>

<jsp:include page="header.jsp" flush="true"/>
  
<script src="javascript/sss.js"></script>
<link rel="stylesheet" href="css/sss.css" type="text/css" media="all">
<link rel="stylesheet" href="css/ecocean.css" type="text/css" media="all">
  
<script>
  jQuery(function($) {
    $('.slider').sss({
      slideShow : false, // Set to false to prevent SSS from automatically animating.
      startOn : 0, // Slide to display first. Uses array notation (0 = first slide).
      transition : 400, // Length (in milliseconds) of the fade transition.
      speed : 3500, // Slideshow speed in milliseconds.
      showNav : true // Set to false to hide navigation arrows.
    });
      $(".slider").show();
  });
</script>
  
<div class="container maincontent"> 
  <%
  Occurrence occ = null;
  boolean hasAuthority = false;
  myShepherd.beginDBTransaction();
  try{
	  if (myShepherd.isOccurrence(number)) {
	      occ = myShepherd.getOccurrence(number);
	      hasAuthority = ServletUtilities.isUserAuthorizedForOccurrence(occ, request);
		  List<Collaboration> collabs = Collaboration.collaborationsForCurrentUser(request);
		  boolean visible = occ.canUserAccess(request);
	
		  if (!visible) {
	  		ArrayList<String> uids = occ.getAllAssignedUsers();
			ArrayList<String> possible = new ArrayList<String>();
			for (String u : uids) {
				Collaboration c = null;
				if (collabs != null) c = Collaboration.findCollaborationWithUser(u, collabs);
				if ((c == null) || (c.getState() == null)) {
					User user = myShepherd.getUser(u);
					String fullName = u;
					if (user.getFullName()!=null) fullName = user.getFullName();
						possible.add(u + ":" + fullName.replace(",", " ").replace(":", " ").replace("\"", " "));
					}
				}
				String cmsg = "<p>" + collabProps.getProperty("deniedMessage") + "</p>";
				cmsg = cmsg.replace("'", "\\'");
	
				if (possible.size() > 0) {
	   			String arr = new Gson().toJson(possible);
					blocker = "<script>$(document).ready(function() { $.blockUI({ message: '" + cmsg + "' + _collaborateMultiHtml(" + arr + ", "+isLoggedIn+") }) });</script>";
				} else {
					cmsg += "<p><input type=\"button\" onClick=\"window.history.back()\" value=\"BACK\" /></p>";
					blocker = "<script>$(document).ready(function() { $.blockUI({ message: '" + cmsg + "' }) });</script>";
				}
			}
		out.println(blocker);
	 
	%>
		<table>
			<tr>
				<td valign="middle">
	 				<h2><strong><img style="align: center;" src="images/occurrence.png" />&nbsp;<%=props.getProperty("occurrence") %></strong>: <%=occ.getOccurrenceID()%></h2>
					<p class="caption"><em><%=props.getProperty("description") %></em></p>
	  			</td>		
	  		</tr>
	  	</table>
	  	<p>
		<%
		if (occ.getSurvey(myShepherd)!=null) {
			String surveyID = occ.getSurvey(myShepherd).getID();
		%>	
			<strong><%=props.getProperty("correspondingSurvey") %>:&nbsp</strong> 
				<a href="//<%=CommonConfiguration.getURLLocation(request)%>/surveys/survey.jsp?occID=<%=occ.getOccurrenceID()%>&surveyID=<%=surveyID%>"><%=surveyID%></a>			
		<%	
		} else {
		%>	
			<strong><%=props.getProperty("noSurvey") %></strong>
		<%
		}
		if (isOwner) {
		%>
			<a class="" type="button" name="button" id="editSurvey" style="cursor: pointer;"><strong>Edit</strong></a>
			<a class="" type="button" name="button" id="closeEditSurvey" style="display: none;cursor: pointer;"><strong>Close Edit</strong></a> 			
		<%
		}
		if (occ.getCorrespondingSurveyTrackID()!=null) {
			String surveyTrackID = occ.getCorrespondingSurveyTrackID();
		%>
			<br/>	
			<strong><%=props.getProperty("correspondingSurveyTrack") %>:&nbsp<%=surveyTrackID%></strong> 									
		<%	
		} 
		%>		
		</p>
	
		<!-- Triggers edit survey and track ID form. -->
		
	<script type="text/javascript">
		$(document).ready(function() {
		  var buttons = $("#editSurvey, #closeEditSurvey").on("click", function(){
		    buttons.toggle();
		  });
		  $("#editSurvey").click(function() {
		    //$(".editFormSurvey, .editTextSurvey, .allEditSurvey").toggle();
		    $("#addSurveyForm").slideDown();
		  });
		  $("#closeEditSurvey").click(function() {
		    //$(".editFormSurvey, .editTextSurvey, .resultMessageDiv, .allEditSurvey").toggle();
		    $("#addSurveyForm").slideUp();
		  });
		});
	</script>								
			<% 
				if (isOwner) {
			%>
				<script type="text/javascript">
	                  $(document).ready(function() {
	                    $("#addOccurrence").click(function(event) {
	                      event.preventDefault();
	
	                      var occID = $("#addOccNumber").val();
	                      var surveyID = $("#surveyID").val();
	                      var surveyTrackID = $("#surveyTrackID").val();
	
	                      $.post("../OccurrenceSetSurveyAndTrack", {"occID": occID, "surveyTrackID": surveyTrackID, "surveyID": surveyID},
	                      function() {
	                        $("#addOccErrorDiv").hide();
	                        $("#addDiv").addClass("has-success");
	                        $("#createOccCheck").show();
	                        $("#addSurveyCheck").show().text("Success! Refresh the page to see your changes.");
	                      })
	                      .fail(function(response) {
	                        console.log("<small>Failed to add to survey.</small>");
	                        $("#addDiv").addClass("has-error");
	                        $("#addOccError, #addOccErrorDiv").show();
	                        $("#addSurveyError").show().text("Failed to add survey and track! Make sure it exists.");
	                        $("#addOccurrence").show();
	                      });
	                    });
	
	                    $("#add2OccurrenceInput").click(function() {
	                      $("#addOccError, #addOccCheck, #addOccErrorDiv").hide()
	                      $("#addDiv").removeClass("has-success");
	                      $("#addDiv").removeClass("has-error");
	                      $("#addOccurrence").show();
	                      $("#addEncErrorDiv").hide();
	                    });
	                  });
	                </script>
				<div id="addSurveyForm" style="display:none;">
					<div class="col-xs-6 col-lg-6">
						<div class="highlight resultMessageDiv" id="addSurveyErrorDiv"></div>
						<form name="addSurveyToOccurrence" class="editFormSurvey">
							<input name="number" type="hidden" value="<%=number%>" id="addOccNumber" />
							<div class="form-group row">
								<div class="col-sm-8" id="addDiv">
									<label><%=props.getProperty("addSurvey")%>: </label>
									<input name="surveyID" id="surveyID" type="text" class="form-control" placeholder="<%=props.getProperty("surveyID")%>" /> 	
									<br/>
									<label><%=props.getProperty("addSurveyTrack")%>: </label><br/>
									<label><small>Must be defined to link back from Survey.</small></label>
									<input name="surveyTrackID" id="surveyTrackID" type="text" class="form-control" placeholder="<%=props.getProperty("surveyTrackID")%>" />
									<label style="display:none;" id="addSurveyCheck"></label>
									<label style="color:red;" id="addSurveyError"></label>
								</div>
								<div class="col-sm-8">
									<input name="Add" type="submit" id="addOccurrence" value="<%=props.getProperty("set")%>" class="btn btn-sm editSurveyFormBtn" />
								</div>
							</div>
						</form>					
					</div>
					<div class="col-xs-6 col-lg-6">
						<br>
					</div>
				</div>
			<%
				}
			%>
		
		
		
		
		
		
		
		
		
		
	<div class="row">	
		<div class="col-xs-12">
		<br/>
		<p><%=props.getProperty("species") %>: 
<%
    if (Util.collectionIsEmptyOrNull(occ.getTaxonomies())) {
        out.println("-");
    } else {
        String wait = "";
        out.println("<ul>");
        for (Taxonomy tx : occ.getTaxonomies()) {
            if (tx.getNonSpecific()) {
                wait += "<li style=\"color: #888;\">" + tx.getScientificName() + "</li>";
            } else {
                out.println("<li><i>" + tx.getScientificName() + "</i></li>");
            }
        }
        out.println(wait);
        out.println("</ul>");
    }
%>
</p>

<%
if (!Util.collectionIsEmptyOrNull(occ.getBehaviors())) {
    out.println("<p>" + props.getProperty("behaviors") + ":<ul>");
    for (Instant behav : occ.getBehaviors()) {
        out.println("<li>" + behav.getValue().toString().substring(0,19) + " <b>" + behav.getName() + "</b></li>");
    }
    out.println("</ul></p>");
}
%>
		<p><%=props.getProperty("groupBehavior") %>: 
			<%if(occ.getGroupBehavior()!=null){%>
				<%=occ.getGroupBehavior() %>
			<%}%>
			&nbsp; 
			<%if (hasAuthority && CommonConfiguration.isCatalogEditable(context)) {%>
				<a id="groupB" style="color:blue;cursor: pointer;"><img width="20px" height="20px" style="border-style: none;align: center;" src="images/Crystal_Clear_action_edit.png" /></a>	
			<%}%>
		</p>
		<div id="dialogGroupB" title="<%=props.getProperty("setGroupBehavior") %>" style="display:none">
			<table border="1">
			  <tr>
			    <td align="left" valign="top">
			      <form name="set_groupBhevaior" method="post" action="OccurrenceSetGroupBehavior">
			            <input name="number" type="hidden" value="<%=request.getParameter("number")%>"/> 
			            <%=props.getProperty("groupBehavior") %>:
			        
				        <%
				        List<String> groupBehaviors = CommonConfiguration.getIndexedPropertyValues("groupBehavior",request);
				        System.out.println("We have groupBehaviors "+groupBehaviors);
				        if (!Util.isEmpty(groupBehaviors)) {%>
				        	<select name="behaviorComment" id="behaviorComment">
				        		<option value=""></option>
					   				<%
					   					for (String groupBehavior: groupBehaviors) {
					   					  String selected = (occ.getGroupBehavior()!=null && occ.getGroupBehavior().equals(groupBehavior)) ? "selected=\"selected\"" : "";
              					%><option <%=selected %> value="<%=groupBehavior%>"><%=groupBehavior%></option>
					   					<%}%>
				  				</select>
					   		<%} else {%>
				        	<textarea name="behaviorComment" id="behaviorComment" maxlength="500"></textarea> 
				        <%}%>
			        	<input name="groupBehaviorName" type="submit" id="Name" value="<%=props.getProperty("set") %>">
			        </form>
			    </td>
			  </tr>
			</table>
		</div>
	  
	<script>
		var dlgGroupB = $("#dialogGroupB").dialog({
		  autoOpen: false,
		  draggable: false,
		  resizable: false,
		  width: 600
		});
		
		$("a#groupB").click(function() {
		  dlgGroupB.dialog("open");
		});
	</script>  
	
		<p><%=props.getProperty("numAdults") %>: <%=occ.getNumAdults() %></p>

		<p><%=props.getProperty("numMarkedIndividuals") %>: <%=occ.getMarkedIndividualNamesForThisOccurrence().size() %></p>
		
		<p>
			<%=props.getProperty("estimatedNumMarkedIndividuals") %>: 
			<%if(occ.getIndividualCount()!=null){%>
				<%=occ.getIndividualCount() %>
			<%}%>
			&nbsp; 
			<%if (hasAuthority && CommonConfiguration.isCatalogEditable(context)) { %>
				<a id="indies" style="color:blue;cursor: pointer;">
					<img width="20px" height="20px" style="border-style: none; align: center;" src="images/Crystal_Clear_action_edit.png"/>
				</a>	
			<%}%>
		</p>
		
	  	<div id="dialogIndies" title="<%=props.getProperty("setIndividualCount") %>" style="display:none">           
			<table border="1" >
			  <tr>
			    <td align="left" valign="top">
			      <form name="set_individualCount" method="post" action="OccurrenceSetIndividualCount">
			        <input name="number" type="hidden" value="<%=request.getParameter("number")%>" /> 
			            <%=props.getProperty("newIndividualCount") %>:
			
			        <input name="count" type="text" id="count" size="5" maxlength="7"></input> 
			        <input name="individualCountButton" type="submit" id="individualCountName" value="<%=props.getProperty("set") %>">
			      </form>
			    </td>
			  </tr>
			</table>
		</div>
		
	<script>
		var dlgIndies = $("#dialogIndies").dialog({
		  autoOpen: false,
		  draggable: false,
		  resizable: false,
		  width: 600
		});
		
		$("a#indies").click(function() {
		  dlgIndies.dialog("open");
		});
	</script>
		<p><%=props.getProperty("locationID") %>: 
			<%if(occ.getLocationID()!=null){%>
				<%=occ.getLocationID() %>
			<%}%>
		</p>

<p>
    <%=props.getProperty("latitude")%> /
    <%=props.getProperty("longitude")%> /
    <%=props.getProperty("bearing")%> /
    <%=props.getProperty("distance")%> :
    <%=occ.getDecimalLatitude()%>,
    <%=occ.getDecimalLongitude()%> /
    <%=occ.getBearing()%> m /
    <%=occ.getDistance()%> m
</p>

<%
if (!Util.collectionIsEmptyOrNull(occ.getSubmitters())) {
    out.println("<p>" + props.getProperty("submittedBy") + ": ");
    List<String> subs = new ArrayList<String>();
    for (User sub : occ.getSubmitters()) {
        subs.add(sub.getDisplayName());
    }
    out.println(String.join(", ", subs) + "</p>");
}

if (!Util.collectionIsEmptyOrNull(occ.getInformOthers())) {
    out.println("<p>" + props.getProperty("contribBy") + ": ");
    List<String> subs = new ArrayList<String>();
    for (User sub : occ.getInformOthers()) {
        subs.add(sub.getDisplayName());
    }
    out.println(String.join(", ", subs) + "</p>");
}
%>

                </p>
		<table id="encounter_report" style="width:100%;">
			<tr>
			
			<td align="left" valign="top">
			
			<p><strong><%=occ.getNumberEncounters()%>
			</strong>
			  <%=props.getProperty("numencounters")%>
			</p> 
		</table>
		</div>
	</div>
		<!-- The Encounter display Area -->
		<table id="results" style="width: 100%">
		  <tr class="lineitem">
		      <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("date") %></strong></td>
			   <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("individualID") %></strong></td>
			   <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("location") %></strong></td>
			   <td class="lineitem" bgcolor="#99CCFF"><strong><%=props.getProperty("dataTypes") %></strong></td>
			   <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("encnum") %></strong></td>
			   <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("alternateID") %></strong></td>
			   <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("sex") %></strong></td>
			   <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("behavior") %></strong></td>
			 <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("haplotype") %></strong></td>
		  </tr>
		  <%
		    Encounter[] dateSortedEncs = occ.getDateSortedEncounters(false);
		
		    int total = dateSortedEncs.length;
		    for (int i = 0; i < total; i++) {
		      Encounter enc = dateSortedEncs[i];
		      
		  %>
		  	<tr>
		      <td class="lineitem"><%=enc.getDate()%></td>
		    
		    <td class="lineitem">
		    	<%if (enc.hasMarkedIndividual()) {%>
		    	<a href="individuals.jsp?id=<%=enc.getIndividualID()%>"><%=enc.getIndividual().getDisplayName(request)%></a>
		    	<%}else{%>
		    		&nbsp;
		    	<%}%>
		    </td>
		    
		    <%
		    String location="&nbsp;";
		    if(enc.getLocation()!=null){
		    	location=enc.getLocation();
		    }
		    %>
		    
		    <td class="lineitem"><%=location%></td>
		    
		    <td width="100" height="32px" class="lineitem">
		    	<a href="//<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=enc.getEncounterNumber()%>">
		    		
		    		<% //if the encounter has photos, show photo folder icon	    		
		    		if ((enc.getMedia().size()>0)){%>
		    			<img src="images/Crystal_Clear_filesystem_folder_image.png" height="32px" width="*" />    		
		    		<%} 
		    		//if the encounter has a tissue sample, show an icon
		    		if((enc.getTissueSamples()!=null) && (enc.getTissueSamples().size()>0)){
		    		%>
		    			<img src="images/microscope.gif" height="32px" width="*" />
		    		<%}
		    		//if the encounter has a measurement, show the measurement icon
		    		if(enc.hasMeasurements()){%>	
		    			<img src="images/ruler.png" height="32px" width="*" />
		        	<%}%>
		    		
		    	</a>
		    </td>
		    
		    <td class="lineitem">
		    	<a href="//<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=enc.getEncounterNumber()%><%if(request.getParameter("noscript")!=null){%>&noscript=null<%}%>"><%=enc.getEncounterNumber()%></a>
		    </td>
		
		    <%if (enc.getAlternateID() != null) {%>
			    <td class="lineitem"><%=enc.getAlternateID()%></td>
		    <%} else {%>
			    <td class="lineitem"><%=props.getProperty("none")%></td>
		    <%}%>
		
			<%
			String sexValue="&nbsp;";
			if(enc.getSex()!=null){sexValue=enc.getSex();}
			%>
			
		    <td class="lineitem"><%=sexValue %></td>
		    
		    <td class="lineitem">
			    <%if(enc.getBehavior()!=null){%>
			    	<%=enc.getBehavior() %>
			    <%} else {%>
			    &nbsp;
			    <%}%>
			</td>
			    
			<td class="lineitem">
			    <%if(enc.getHaplotype()!=null){%>
			    <%=enc.getHaplotype() %>
			    <%} else {%>
			    &nbsp;
			    <%}%>
		    </td>
		  </tr>
		  <%} //End of loop iterating over encounters. %>
		</table>
		
		<!-- Start thumbnail images -->
		<br/>
			<p><strong><%=props.getProperty("imageGallery") %></strong></p>
		<hr/>
		
		<div class="slider col-sm-12 center-slider">
		  <%
	      ArrayList<JSONObject> photoObjectArray = occ.getExemplarImages(request);
	      String imgurlLoc = "//" + CommonConfiguration.getURLLocation(request);
	      int numPhotos=photoObjectArray.size();
		  if (numPhotos>0) {
		      for (int extraImgNo=0; extraImgNo<numPhotos; extraImgNo++) {
		        JSONObject newMaJson = new JSONObject();
		        newMaJson = photoObjectArray.get(extraImgNo);
		        String newimgUrl = newMaJson.optString("url", imgurlLoc+"/cust/mantamatcher/img/hero_manta.jpg");
		
		        %>
		        <div class="crop-outer">
		          <div class="crop">
		              <img src="cust/mantamatcher/img/individual_placeholder_image.jpg" class="sliderimg lazyload" data-src="<%=newimgUrl%>" alt="<%=occ.getOccurrenceID()%>" />
		          </div>
		        </div>
		        <%
		      }
	      } else {
			%>
			  <p class="text-center"><%=props.getProperty("noImages") %></p>
			<%
		  }
	      %>
		</div>
		 
		<hr/>
		<br/>
		
		<!-- Begin dual column for tags and observations -->
		<div class="row">
				<div class="col-xs-6">
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
								<img src="images/lightning_dynamic_props.gif" />
								<%=props.getProperty("dynamicProperties")%>
								<button class="btn btn-md" type="button" name="button"
									id="editDynamic">Edit</button>
								<button class="btn btn-md" type="button" name="button"
									id="closeEditDynamic" style="display: none;">Close Edit</button>
							</h2>
							<p id="editInstructions" style="display:none;"><small>Set an empty value field to remove an observation.</small></p>
						<%
						} else {
						%>
						<h2>
							<img src="images/lightning_dynamic_props.gif" />
							<%=props.getProperty("dynamicProperties")%>
						</h2>
						<br/>
						<%
						}
								// Let's make a list of editable Observations... Dynamically!
								
						if (occ.getObservationArrayList() != null) {
							ArrayList<Observation> obs = occ.getObservationArrayList();
							System.out.println("Observations ... "+obs);
							int numObservations = occ.getObservationArrayList().size();
							for (Observation ob : obs) {
								
								String nm = ob.getName();
								String vl = ob.getValue();
						%>
								
								<p><em><%=nm%></em>:&nbsp<%=vl%></p>
								<!-- Start dynamic (Observation) form. -->
								<div style="display:none;" id="dialogDP<%=nm%>" class="editFormObservation" title="<%=props.getProperty("set")%> <%=nm%>">
									<p class="editFormObservation">
										<strong><%=props.getProperty("set")%> <%=nm%></strong>
									</p>
									<form name="editFormObservation" action="../OccurrenceSetObservation" method="post" class="editFormDynamic">
										<input name="name" type="hidden" value="<%=nm%>" /> 
										<input name="number" type="hidden" value="<%=number%>" />
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
					<form name="addDynProp" action="../OccurrenceSetObservation"
						method="post" class="editFormObservation">
						<input name="number" type="hidden" value="<%=number%>" />
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

			<br/><br/>
		</div>
		
			<div>
				<div style="margin-left: 10px; padding: 3px; border: solid #AAA 2px;" class="comments">Comments: <%=occ.getComments()%></div>

			</div>

			<%
	  		}
	  		else{
			%>
			<p><%=props.getProperty("noRecord",langCode) %></p>
			<%
	  		}
  		}
	  catch(Exception e){
		  e.printStackTrace();
		  %>
		  <p>I hit an exception while trying to render this page.</p>
		  <%
	  }
  finally{
	  myShepherd.rollbackDBTransaction();
	  myShepherd.closeDBTransaction();
  }
	  
		%>

</div> <!-- End Maincontent Div --> 

<jsp:include page="footer.jsp" flush="true"/>

  
  
  
  
  
  
  
  
  
  
  
  
