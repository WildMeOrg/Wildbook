<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.joda.time.format.DateTimeFormat,
         org.joda.time.format.DateTimeFormatter,
         org.joda.time.LocalDateTime,
         java.util.Locale,
         org.ecocean.servlet.ServletUtilities,
         com.drew.imaging.jpeg.JpegMetadataReader,
         com.drew.metadata.Directory,
         com.drew.metadata.Metadata,
         com.drew.metadata.Tag,
         org.ecocean.*,
         org.ecocean.servlet.ServletUtilities,
         org.ecocean.Util,org.ecocean.Measurement,
         org.ecocean.Util.*, org.ecocean.genetics.*,
         org.ecocean.tag.*, java.awt.Dimension,
         javax.jdo.Extent, javax.jdo.Query,
         java.io.File, java.text.DecimalFormat,
         java.util.*,org.ecocean.security.Collaboration" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>

<%!

  //shepherd must have an open trasnaction when passed in
  public String getNextIndividualNumber(Encounter enc, Shepherd myShepherd, String context) {
    String returnString = "";
    try {
      String lcode = enc.getLocationCode();
      if ((lcode != null) && (!lcode.equals(""))) {

        //let's see if we can find a string in the mapping properties file
        Properties props = new Properties();
        //set up the file input stream
        //props.load(getClass().getResourceAsStream("/bundles/newIndividualNumbers.properties"));
        props=ShepherdProperties.getProperties("newIndividualNumbers.properties", "",context);

        //let's see if the property is defined
        if (props.getProperty(lcode) != null) {
          returnString = props.getProperty(lcode);


          int startNum = 1;
          boolean keepIterating = true;

          //let's iterate through the potential individuals
          while (keepIterating) {
            String startNumString = Integer.toString(startNum);
            if (startNumString.length() < 3) {
              while (startNumString.length() < 3) {
                startNumString = "0" + startNumString;
              }
            }
            String compositeString = returnString + startNumString;
            if (!myShepherd.isMarkedIndividual(compositeString)) {
              keepIterating = false;
              returnString = compositeString;
            } else {
              startNum++;
            }

          }
          return returnString;

        }


      }
      return returnString;
    }
    catch (Exception e) {
      e.printStackTrace();
      return returnString;
    }
  }

%>

<%


String context="context0";
context=ServletUtilities.getContext(request);
//get encounter number
String num = request.getParameter("number").replaceAll("\\+", "").trim();

//let's set up references to our file system components
String rootWebappPath = getServletContext().getRealPath("/");
File webappsDir = new File(rootWebappPath).getParentFile();
File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
File encounterDir = new File(encountersDir, num);


  GregorianCalendar cal = new GregorianCalendar();
  int nowYear = cal.get(1);


//handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

//gps decimal formatter
  DecimalFormat gpsFormat = new DecimalFormat("###.####");

//handle translation
  //String langCode = "en";
String langCode=ServletUtilities.getLanguageCode(request);




//let's load encounters.properties
  //Properties encprops = new Properties();
  //encprops.load(getClass().getResourceAsStream("/bundles/" + langCode + "/encounter.properties"));

  Properties encprops = ShepherdProperties.getProperties("encounter.properties", langCode, context);

	Properties collabProps = new Properties();
 	collabProps=ShepherdProperties.getProperties("collaboration.properties", langCode, context);



  pageContext.setAttribute("num", num);


  Shepherd myShepherd = new Shepherd(context);
  Extent allKeywords = myShepherd.getPM().getExtent(Keyword.class, true);
  Query kwQuery = myShepherd.getPM().newQuery(allKeywords);
//System.out.println("???? query=" + kwQuery);
  boolean proceed = true;
  boolean haveRendered = false;

  pageContext.setAttribute("set", encprops.getProperty("set"));
%>



<jsp:include page="../header.jsp" flush="true"/>

  <style type="text/css">
    <!--

	#spot-image-wrapper-left,
	#spot-image-wrapper-right
	{
		position: relative;
		height: 510px;
	}
	#spot-image-left, #spot-image-canvas-left,
	#spot-image-right, #spot-image-canvas-right
	{
		position: absolute;
		left: 0;
		top: 0;
		max-width: 600px;
		max-height: 500px;
	}

	.spot-td {
		display: table;
	}

    .style2 {
      color: #000000;
      font-size: small;
    }

    .style3 {
      font-weight: bold
    }

    .style4 {
      color: #000000
    }

    table.adopter {
      border-width: 1px 1px 1px 1px;
      border-spacing: 0px;
      border-style: solid solid solid solid;
      border-color: black black black black;
      border-collapse: separate;
      background-color: white;
    }

    table.adopter td {
      border-width: 1px 1px 1px 1px;
      padding: 3px 3px 3px 3px;
      border-style: none none none none;
      border-color: gray gray gray gray;
      background-color: white;
      -moz-border-radius: 0px 0px 0px 0px;
      font-size: 12px;
      color: #330099;
    }

    table.adopter td.name {
      font-size: 12px;
      text-align: center;
    }

    table.adopter td.image {
      padding: 0px 0px 0px 0px;
    }

    div.scroll {
      height: 200px;
      overflow: auto;
      border: 1px solid #666;
      background-color: #ccc;
      padding: 8px;
    }

    -->




th.measurement{
	 font-size: 0.9em;
	 font-weight: normal;
	 font-style:italic;
}

td.measurement{
	 font-size: 0.9em;
	 font-weight: normal;
}

</style>


  <!--
    1 ) Reference to the files containing the JavaScript and CSS.
    These files must be located on your server.
  -->

  <script type="text/javascript" src="../highslide/highslide/highslide-with-gallery.js"></script>
  <link rel="stylesheet" type="text/css" href="../highslide/highslide/highslide.css"/>

  <!--
    2) Optionally override the settings defined at the top
    of the highslide.js file. The parameter hs.graphicsDir is important!
  -->


<script type="text/javascript">

  var map;
  var marker;

          function placeMarker(location) {

          //alert("entering placeMarker!");

          	if(marker!=null){marker.setMap(null);}
          	marker = new google.maps.Marker({
          	      position: location,
          	      map: map,
          	      visible: true
          	  });

          	  //map.setCenter(location);

          	    var ne_lat_element = document.getElementById('lat');
          	    var ne_long_element = document.getElementById('longitude');


          	    ne_lat_element.value = location.lat();
          	    ne_long_element.value = location.lng();
	}
	</script>



  <script>
            function initialize() {
            //alert("Initializing map!");
              var mapZoom = 1;
          	if($("#map_canvas").hasClass("full_screen_map")){mapZoom=3;}


              var center = new google.maps.LatLng(10.8, 160.8);

              map = new google.maps.Map(document.getElementById('map_canvas'), {
                zoom: mapZoom,
                center: center,
                mapTypeId: google.maps.MapTypeId.HYBRID
        });

        	if(marker!=null){
			marker.setMap(map);
			map.setCenter(marker.position);

 			//alert("Setting center!");
		}

        google.maps.event.addListener(map, 'click', function(event) {
					//alert("Clicked map!");
				    placeMarker(event.latLng);
			  });


	//adding the fullscreen control to exit fullscreen
    	  var fsControlDiv = document.createElement('DIV');
    	  var fsControl = new FSControl(fsControlDiv, map);
    	  fsControlDiv.index = 1;
    	  map.controls[google.maps.ControlPosition.TOP_RIGHT].push(fsControlDiv);



        }




var encounterNumber = '<%=num%>';

  </script>

<style type="text/css">
.full_screen_map {
position: absolute !important;
top: 0px !important;
left: 0px !important;
z-index: 1 !imporant;
width: 100% !important;
height: 100% !important;
margin-top: 0px !important;
margin-bottom: 8px !important;

  .ui-dialog-titlebar-close { display: none; }
  code { font-size: 2em; }

</style>


<!--added below for improved map selection -->



<!--  FACEBOOK LIKE BUTTON -->
<div id="fb-root"></div>
<script>(function(d, s, id) {
  var js, fjs = d.getElementsByTagName(s)[0];
  if (d.getElementById(id)) return;
  js = d.createElement(s); js.id = id;
  js.src = "//connect.facebook.net/en_US/all.js#xfbml=1";
  fjs.parentNode.insertBefore(js, fjs);
}(document, 'script', 'facebook-jssdk'));</script>

<!-- GOOGLE PLUS-ONE BUTTON -->
<script type="text/javascript">
  (function() {
    var po = document.createElement('script'); po.type = 'text/javascript'; po.async = true;
    po.src = 'https://apis.google.com/js/plusone.js';
    var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(po, s);
  })();
</script>
</head>

<style type="text/css">

.full_screen_map {
position: absolute !important;
top: 0px !important;
left: 0px !important;
z-index: 1 !imporant;
width: 100% !important;
height: 100% !important;
margin-top: 0px !important;
margin-bottom: 8px !important;


/* css for timepicker */
.ui-timepicker-div .ui-widget-header { margin-bottom: 8px; }
.ui-timepicker-div dl { text-align: left; padding: 0 5px 0 0;}
.ui-timepicker-div dl dt { float: left; clear:left; padding: 0 0 0 5px; }
.ui-timepicker-div dl dd { margin: 0 10px 10px 45%; }
.ui-timepicker-div td { font-size: 90%; }
.ui-tpicker-grid-label { background: none; border: none; margin: 0; padding: 0; }

.ui-timepicker-rtl{ direction: rtl; }
.ui-timepicker-rtl dl { text-align: right; padding: 0 5px 0 0; }
.ui-timepicker-rtl dl dt{ float: right; clear: right; }
.ui-timepicker-rtl dl dd { margin: 0 45% 10px 10px; }

/*customizations*/
.ui_tpicker_hour_label {margin-bottom:5px !important;}
.ui_tpicker_minute_label {margin-bottom:5px !important;}


</style>



<script src="http://maps.google.com/maps/api/js?sensor=false&language=<%=langCode%>"></script>
<script type="text/javascript" src="http://geoxml3.googlecode.com/svn/branches/polys/geoxml3.js"></script>


  <script src="../javascript/timepicker/jquery-ui-timepicker-addon.js"></script>

<script src="../javascript/imageTools.js"></script>




<div class="container maincontent">


			<%
  			myShepherd.beginDBTransaction();

  			if (myShepherd.isEncounter(num)) {
    			try {

      			Encounter enc = myShepherd.getEncounter(num);
						boolean visible = enc.canUserAccess(request);

						if (!visible) {
							String blocker = "";
							List collabs = Collaboration.collaborationsForCurrentUser(request);
							Collaboration c = Collaboration.findCollaborationWithUser(enc.getAssignedUsername(), collabs);
							String cmsg = "<p>" + collabProps.getProperty("deniedMessage") + "</p>";
							String uid = null;
							String name = null;
							if (request.getUserPrincipal() == null) {
								cmsg = "<p>Access limited.</p>";
							} if ((c == null) || (c.getState() == null)) {
								uid = enc.getAssignedUsername();
								name = enc.getSubmitterName();
								if ((name == null) || name.equals("N/A")) name = enc.getAssignedUsername();
							} else if (c.getState().equals(Collaboration.STATE_INITIALIZED)) {
								cmsg += "<p>" + collabProps.getProperty("deniedMessagePending") + "</p>";
							} else if (c.getState().equals(Collaboration.STATE_REJECTED)) {
								cmsg += "<p>" + collabProps.getProperty("deniedMessageRejected") + "</p>";
							}

							cmsg = cmsg.replace("'", "\\'");
							if (!User.isUsernameAnonymous(uid) && (request.getUserPrincipal() != null)) {
								blocker = "<script>$(document).ready(function() { $.blockUI({ message: '" + cmsg + "' + _collaborateHtml('" + uid + "', '" + name.replace("'", "\\'") + "') }) });</script>";
							} else {
								blocker = "<script>$(document).ready(function() { $.blockUI({ message: '<p>" + cmsg + "' + collabBackOrCloseButton() + '</p>' }) });</script>";
							}
							out.println(blocker);
						}


      			pageContext.setAttribute("enc", enc);
      			String livingStatus = "";
      			if ((enc.getLivingStatus()!=null)&&(enc.getLivingStatus().equals("dead"))) {
        			livingStatus = " (deceased)";
      			}

if (request.getParameter("refreshImages") != null) {
	System.out.println("refreshing images!!! ==========");
	//enc.refreshAssetFormats(context, ServletUtilities.dataDir(context, rootWebappPath));
	enc.refreshAssetFormats(myShepherd);
	System.out.println("============ out ==============");
}

				//let's see if this user has ownership and can make edits
      			boolean isOwner = ServletUtilities.isUserAuthorizedForEncounter(enc, request);
      			pageContext.setAttribute("editable", isOwner && CommonConfiguration.isCatalogEditable(context));
      			boolean loggedIn = false;
      			try{
      				if(request.getUserPrincipal()!=null){loggedIn=true;}
      			}
      			catch(NullPointerException nullLogged){}

      			String headerBGColor="FFFFFC";
      			//if(CommonConfiguration.getProperty(()){}
    			%>

<script type="text/javascript">



$(function() {
    $( "#datepicker" ).datetimepicker({
      changeMonth: true,
      changeYear: true,
      dateFormat: 'yy-mm-dd',

      <%
      //set a default date if we cann
      if(enc.getDateInMilliseconds()!=null){

    	  //LocalDateTime jodaTime = new LocalDateTime(enc.getDateInMilliseconds());


          //DateTimeFormatter parser1 = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm");
          LocalDateTime jodaTime=new LocalDateTime(enc.getDateInMilliseconds());

      %>
      defaultDate: '<%=jodaTime.toString("yyyy-MM-dd HH:mm") %>',
      hour: <%=jodaTime.getHourOfDay() %>,
      minute: <%=jodaTime.getMinuteOfHour() %>,
      <%
      }
      %>


      altField: '#datepickerField',
      altFieldTimeOnly: false,
      maxDate: '+1d',
      controlType: 'select',
      alwaysSetTime: false
    });
    $( "#datepicker" ).datetimepicker( $.timepicker.regional[ "<%=langCode %>" ] );


  });
  </script>

   <script type="text/javascript">
  $(function() {
    $( "#releasedatepicker" ).datepicker({
      changeMonth: true,
      changeYear: true,
      dateFormat: 'yy-mm-dd',
      maxDate: '+1d',
      altField: '#releasedatepickerField',


      <%
      //set a default date if we cann
      if((enc.getReleaseDateLong()!=null)&&(enc.getReleaseDateLong()>0)){

    	  LocalDateTime jodaTime = new LocalDateTime(enc.getReleaseDateLong().longValue());
          DateTimeFormatter parser1 = DateTimeFormat.forPattern("yyyy-MM-dd");

      %>
      defaultDate: '<%=parser1.print(jodaTime) %>',
      <%
      }
      %>


    });
    $( "#releasedatepicker" ).datepicker( $.datepicker.regional[ "<%=langCode %>" ] );

  });
  </script>


    			<table width="100%">
    				<tr>
    					<td bgcolor="#<%=headerBGColor %>">
    						<%
    						//int stateInt=-1;
    						String classColor="approved_encounters";
							boolean moreStates=true;
							int cNum=0;
							while(moreStates){
	  								String currentLifeState = "encounterState"+cNum;
	  								if(CommonConfiguration.getProperty(currentLifeState,context)!=null){

										if(CommonConfiguration.getProperty(currentLifeState,context).equals(enc.getState())){
											//stateInt=taxNum;
											moreStates=false;
											if(CommonConfiguration.getProperty(("encounterStateCSSClass"+cNum),context)!=null){
												classColor=CommonConfiguration.getProperty(("encounterStateCSSClass"+cNum),context);
											}
										}
										cNum++;
  									}
  									else{
     									moreStates=false;
  									}

								} //end while


    						%>


    						<h1 class="<%=classColor%>">
    						 	<%=encprops.getProperty("title") %><%=livingStatus %>
    						 </h1>


    					</td>
    				</tr>
    			</table>



    			<p class="caption"><em><%=encprops.getProperty("description") %></em></p>
 					<table style="border-spacing: 10px;border-collapse: inherit;">
 						<tr valign="middle">
  							<td>
    							<!-- Google PLUS-ONE button -->
								<g:plusone size="small" annotation="none"></g:plusone>
							</td>
							<td>
								<!--  Twitter TWEET THIS button -->
								<a href="https://twitter.com/share" class="twitter-share-button" data-count="none">Tweet</a>
								<script>!function(d,s,id){var js,fjs=d.getElementsByTagName(s)[0];if(!d.getElementById(id)){js=d.createElement(s);js.id=id;js.src="//platform.twitter.com/widgets.js";fjs.parentNode.insertBefore(js,fjs);}}(document,"script","twitter-wjs");</script>
							</td>
							<td>
								<!-- Facebook SHARE button -->
								<div class="fb-share-button" data-href="http://<%=CommonConfiguration.getURLLocation(request) %>/encounters/encounter.jsp?number=<%=request.getParameter("number") %>" data-type="button_count"></div></td>
						</tr>
					</table>
					<table>
						<tr>
							<td width="560px" style="vertical-align:top">



<!-- START IDENTITY ATTRIBUTE -->

  <h2><img align="absmiddle" src="../images/wild-me-logo-only-100-100.png" width="40px" height="40px" /> <%=encprops.getProperty("identity") %></h2>

<% if (isOwner && CommonConfiguration.isCatalogEditable(context)) { %>
<!--
<div class="encounter-vm-button">
	<a href="encounterVM.jsp?number=<%=num%>">[Visual Matcher]</a>
</div>
-->
<% } %>


    							<%
    							if (!enc.hasMarkedIndividual()) {
  								%>
    							<p class="para">
    								 <%=encprops.getProperty("identified_as") %> <%=enc.getIndividualID()%>
      								<%
        							if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
     								%>
      									<a id="identity" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a>
      								<%
        							}
      								%>
    							</p>
    							<%
    							}
    							else {
    							%>
    							<p class="para">

      								<%=encprops.getProperty("identified_as") %> <a href="../individuals.jsp?langCode=<%=langCode%>&number=<%=enc.getIndividualID()%><%if(request.getParameter("noscript")!=null){%>&noscript=true<%}%>"><%=enc.getIndividualID()%></a>
      								<%
        							if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
      								%>
      									<a id="identity" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a>
      								<%
        							}
      								%>
      								<br />
      								<br />
      								<img align="absmiddle" src="../images/Crystal_Clear_app_matchedBy.gif"> <%=encprops.getProperty("matched_by") %>: <%=enc.getMatchedBy()%>
      								<%
        							if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
      								%>
     								 <a id="matchedBy" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a>
        							<div id="dialogMatchedBy" title="<%=encprops.getProperty("matchedBy")%>" style="display:none">
  										<table>
    										<tr>
      											<td align="left" valign="top">
        											<form name="setMBT" action="../EncounterSetMatchedBy" method="post">
          												<select name="matchedBy" id="matchedBy">
            												<option value="Unmatched first encounter"><%=encprops.getProperty("unmatchedFirstEncounter")%></option>
            												<option value="Visual inspection"><%=encprops.getProperty("visualInspection")%></option>
            												<option value="Pattern match" selected><%=encprops.getProperty("patternMatch")%></option>
          												</select>
          												<input name="number" type="hidden" value="<%=num%>" />
          												<input name="setMB" type="submit" id="setMB" value="<%=encprops.getProperty("set")%>" />
        											</form>
      											</td>
    										</tr>
  										</table>
  									</div>
									<script>
  										var dlgMatchedBy = $("#dialogMatchedBy").dialog({
    										autoOpen: false,
    										draggable: false,
    										resizable: false,
    										width: 600
  										});

  										$("a#matchedBy").click(function() {
    										dlgMatchedBy.dialog("open");
  										});
  									</script>
        							<%
       								 }
      								%>
    							</p>
    							<%
      							} //end else

      							if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
      							%>
     							<div id="dialogIdentity" title="<%=encprops.getProperty("manageIdentity")%>" style="display:none">
  									<p><em><%=encprops.getProperty("identityMessage") %></em></p>

  									<%
  									if(!enc.hasMarkedIndividual()) {
  									%>

  									<table border="1" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF" >
    									<tr>
      										<td align="left" valign="top" class="para">
      											<font color="#990000">
        											<img align="absmiddle" src="../images/tag_small.gif"/><br />
        											<strong><%=encprops.getProperty("add2MarkedIndividual")%>:</strong>
        										</font>
        									</td>
    									</tr>
    									<tr>
      										<td align="left" valign="top">
        										<form name="add2shark" action="../IndividualAddEncounter" method="post">
        											<%=encprops.getProperty("individual")%>:
              											<input name="individual" type="text" size="10" maxlength="50" /><br /> <%=encprops.getProperty("matchedBy")%>:<br />
          												<select name="matchType" id="matchType">
            												<option value="Unmatched first encounter"><%=encprops.getProperty("unmatchedFirstEncounter")%></option>
            												<option value="Visual inspection"><%=encprops.getProperty("visualInspection")%></option>
            												<option value="Pattern match" selected><%=encprops.getProperty("patternMatch")%></option>
          												</select>
          												<br />
          												<input name="noemail" type="checkbox" value="noemail" />
          												<%=encprops.getProperty("suppressEmail")%><br />
          												<input name="number" type="hidden" value="<%=num%>" />
          												<input name="action" type="hidden" value="add" />
          												<input name="Add" type="submit" id="Add" value="<%=encprops.getProperty("add")%>" />
        										</form>
     										 </td>
    									</tr>
  									</table>
									<br />
									<strong>--<%=encprops.getProperty("or") %>--</strong>
									<br /><br />
									<%
  									}
  		 	  	  					//Remove from MarkedIndividual if not unassigned
		  	  						if(enc.hasMarkedIndividual() && CommonConfiguration.isCatalogEditable(context)) {
		  							%>
									<table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">
 										<tr>
    										<td align="left" valign="top" class="para">
      											<table>
        											<tr>
          												<td>
          													<font color="#990000">
          														<img align="absmiddle" src="../images/cancel.gif"/>
          													</font>
          												</td>
          												<td>
          													<strong>
          														<%=encprops.getProperty("removeFromMarkedIndividual")%>
          													</strong>
          												</td>
        											</tr>
      											</table>
    										</td>
  										</tr>
  										<tr>
    										<td align="left" valign="top">
      											<form action="../IndividualRemoveEncounter" method="post" name="removeShark">
      												<input name="number" type="hidden" value="<%=num%>" />
                									<input name="action" type="hidden" value="remove" />
                									<input type="submit" name="Submit" value="<%=encprops.getProperty("remove")%>" />
      											</form>
    										</td>
  										</tr>
									</table>
									<br />
									<%
   									}
									if(!enc.hasMarkedIndividual()) {
									%>

									<table border="1" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">
  										<tr>
    										<td align="left" valign="top" class="para">
    											<font color="#990000">
      												<img align="absmiddle" src="../images/tag_small.gif"/>
      												<strong><%=encprops.getProperty("createMarkedIndividual")%>:</strong>
      											</font>
    										</td>
  										</tr>
  										<tr>
    										<td align="left" valign="top">
      											<form name="createShark" method="post" action="../IndividualCreate">
        											<input name="number" type="hidden" value="<%=num%>" />
        											<input name="action" type="hidden" value="create" />
        											<input name="individual" type="text" id="individual" size="10" maxlength="50" value="<%=getNextIndividualNumber(enc, myShepherd,context)%>" /><br />
													<input name="noemail" type="checkbox" value="noemail" />
        											<%=encprops.getProperty("suppressEmail")%><br />

      												<input name="Create" type="submit" id="Create" value="<%=encprops.getProperty("create")%>" />
      											</form>
    										</td>
  										</tr>
									</table>
								<%
								}
								%>
							</div>

  							<script>
  								var dlgIdentity = $("#dialogIdentity").dialog({
    								autoOpen: false,
    								draggable: false,
    								resizable: false,
    								width: 600
  								});

  								$("a#identity").click(function() {
    								dlgIdentity.dialog("open");
  								});
  							</script>
  						<%
  						}
						%>

<!-- END INDIVIDUALID ATTRIBUTE -->

						<!-- START ALTERNATEID ATTRIBUTE -->
    <p class="para">
    <%
    String alternateID="";
    if(enc.getAlternateID()!=null){
    	alternateID=enc.getAlternateID();
    }
    %>
    	<img align="absmiddle" src="../images/alternateid.gif"> <%=encprops.getProperty("alternate_id")%>: <%=alternateID%>
      <%
      if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
      %>
      <a id="alternateID" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a>
      <%
        }
      %>
    </p>
    <%
    if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
    %>
    <!-- start set alternate ID popup -->
<div id="dialogAlternateID" title="<%=encprops.getProperty("setAlternateID")%>" style="display:none">
<table border="1" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">
    <tr>
      <td align="left" valign="top">
        <form name="setAltID" action="../EncounterSetAlternateID" method="post">
              <input name="alternateid" type="text" size="10" maxlength="50" />
                                   <input name="encounter" type="hidden" value="<%=num%>" />
          <input name="Set" type="submit" id="<%=encprops.getProperty("set")%>" value="<%=encprops.getProperty("set")%>" />
          </form>
      </td>
    </tr>
  </table>
</div>
                         		<!-- popup dialog script -->
<script>
var dlgAlternateID = $("#dialogAlternateID").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#alternateID").click(function() {
  dlgAlternateID.dialog("open");
});
</script>
<%
}
%>
<!-- END ALTERNATEID ATTRIBUTE -->


						<!-- START EVENTID ATTRIBUTE -->
 						<%
    					if (enc.getEventID() != null) {
  						%>
  							<p class="para">
  								<%=encprops.getProperty("eventID") %>: <%=enc.getEventID() %>
  							</p>
  						<%
    					}
  						%>
						<!-- END EVENTID ATTRIBUTE -->


						<!-- START OCCURRENCE ATTRIBUTE -->
						<p class="para">
							<img width="24px" height="24px" align="absmiddle" src="../images/occurrence.png" />&nbsp;<%=encprops.getProperty("occurrenceID") %>:
							<%
							if(myShepherd.getOccurrenceForEncounter(enc.getCatalogNumber())!=null){
							%>
								<a href="../occurrence.jsp?number=<%=myShepherd.getOccurrenceForEncounter(enc.getCatalogNumber()).getOccurrenceID() %>"><%=myShepherd.getOccurrenceForEncounter(enc.getCatalogNumber()).getOccurrenceID() %></a>
							<%
							}
							else{
							%>
								<%=encprops.getProperty("none_assigned") %>
							<%
							}

        					if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
      						%>
      							<a id="occurrence" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a>
      						<%
        					}
      						%>
  						</p>

  						<%
						if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
						%>

<div id="dialogOccurrence" title="<%=encprops.getProperty("assignOccurrence")%>" style="display:none">

<p><em><%=encprops.getProperty("occurrenceMessage")%></em></p>

<!-- start Occurrence management section-->
	<%
    //Remove from occurrence if assigned
	if((myShepherd.getOccurrenceForEncounter(enc.getCatalogNumber())!=null) && isOwner) {


	%>
	<table border="0" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">
  	<tr>
    	<td align="left" valign="top" class="para">
      <table>
        <tr>
          <td><font color="#990000"><img align="absmiddle" src="../images/cancel.gif"/></font></td>
          <td><strong><%=encprops.getProperty("removeFromOccurrence")%>
          </strong></td>
        </tr>
      </table>
    </td>
  </tr>
  <tr>
    <td align="left" valign="top">
      <form action="../OccurrenceRemoveEncounter" method="post" name="removeOccurrence">
      	<input name="number" type="hidden" value="<%=num%>" />
      	<input name="action" type="hidden" value="remove" />
      	<input type="submit" name="Submit" value="<%=encprops.getProperty("remove")%>" />
      </form>
    </td>
  </tr>
</table>
<br /> <%
      	}
      	  //create new Occurrence with name

      if(isOwner && (myShepherd.getOccurrenceForEncounter(enc.getCatalogNumber())==null)){

      %>
<table border="0" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">
  <tr>
    <td align="left" valign="top" class="para">
    	<font color="#990000">
      		<strong><%=encprops.getProperty("createOccurrence")%></strong></font></td>
  </tr>
  <tr>
    <td align="left" valign="top">
      <form name="createOccurrence" method="post" action="../OccurrenceCreate">
        <input name="number" type="hidden" value="<%=num%>" />
        <input name="action" type="hidden" value="create" />
        <%=encprops.getProperty("newOccurrenceID")%><br />
        <input name="occurrence" type="text" id="occurrence" size="10" maxlength="50" value="" />
        <br />
        <input name="Create" type="submit" id="Create" value="<%=encprops.getProperty("create")%>" />
      </form>
    </td>
  </tr>
</table>
<br/>
<strong>--<%=encprops.getProperty("or") %>--</strong>
<br />
<br />
  <table border="0" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">
    <tr>
      <td align="left" valign="top" class="para"><font color="#990000">

        <strong><%=encprops.getProperty("add2Occurrence")%></strong></font></td>
    </tr>
    <tr>
      <td align="left" valign="top">
        <form name="add2occurrence" action="../OccurrenceAddEncounter" method="post">
        <%=encprops.getProperty("occurrenceID")%>: <input name="occurrence" type="text" size="10" maxlength="50" /><br />

            <input name="number" type="hidden" value="<%=num%>" />
            <input name="action" type="hidden" value="add" />
          <input name="Add" type="submit" id="Add" value="<%=encprops.getProperty("add")%>" />
          </form>
      </td>
    </tr>
  </table>
 <%
 }

%>

<!-- end Occurrence management section -->

</div>
                         		<!-- popup dialog script -->
<script>
var dlgOccurrence = $("#dialogOccurrence").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#occurrence").click(function() {
  dlgOccurrence.dialog("open");
});
</script>
<!-- end set occurrenceID -->
<%
}
%>
<!-- END OCCURRENCE ATTRIBUTE -->

<br />

<!-- start DATE section -->
<table>
<tr>
<td width="560px" style="vertical-align:top; background-color: #E8E8E8">

<h2><img align="absmiddle" src="../images/calendar.png" width="40px" height="40px" /><%=encprops.getProperty("date") %>
</h2>
<p>
<%if(enc.getDateInMilliseconds()!=null){ %>
  <a
    href="http://<%=CommonConfiguration.getURLLocation(request)%>/xcalendar/calendar.jsp?scDate=<%=enc.getMonth()%>/1/<%=enc.getYear()%>">
    <%=enc.getDate()%>
  </a>
    <%
}
else{
%>
<%=encprops.getProperty("unknown") %>
<%
}


				if(isOwner&&CommonConfiguration.isCatalogEditable(context)) {
 					%><font size="-1"><a id="date" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a></font> <%
        		}
        		%>


<br />
<em><%=encprops.getProperty("verbatimEventDate")%></em>:
    <%
				if(enc.getVerbatimEventDate()!=null){
				%>
    <%=enc.getVerbatimEventDate()%>
    <%
				}
				else {
				%>
    <%=encprops.getProperty("none") %>
    <%
				}
				if(isOwner&&CommonConfiguration.isCatalogEditable(context)) {
 					%> <font size="-1"><a id="VBDate" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a></font> <%
        		}
        		%>


<!-- end verbatim event date -->



<%
  pageContext.setAttribute("showReleaseDate", CommonConfiguration.showReleaseDate(context));
%>
<c:if test="${showReleaseDate}">
  <br /><em><%=encprops.getProperty("releaseDate") %></em>:
    <fmt:formatDate value="${enc.releaseDate}" pattern="yyyy-MM-dd"/>
    <c:if test="${editable}">
        <font size="-1"><a id="releaseDate" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a></font>
    </c:if>
  </p>
</c:if>

<!-- start releaseDate popup -->
<div id="dialogReleaseDate" title="<%=encprops.getProperty("setReleaseDate")%>" style="display:none">

  <table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF" >

    <tr>
        <td>
            <form name="setReleaseDate" method="post" action="../EncounterSetReleaseDate">
                <input type="hidden" name="encounter" value="${num}"/>
            <table>

                <tr><td>

                    <div id="releasedatepicker"></div>

          <p>
           <%=encprops.getProperty("setReleaseDate")%> <input type="text" style="position: relative; z-index: 101;" id="releasedatepickerField" name="releasedatepicker" size="20" /><br /> <font size="-1"><%=encprops.getProperty("leaveBlank")%></font>
          </p>

          <br />
           <input name="AddDate" type="submit" id="AddDate" value="<%=encprops.getProperty("setReleaseDate")%>" />


                </td></tr>
            </table>
            </form>
        </td>
    </tr>
  </table>
</div>
                         		<!-- popup dialog script -->
<script>
var dlgReleaseDate = $("#dialogReleaseDate").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#releaseDate").click(function() {
  dlgReleaseDate.dialog("open");
});
</script>
<!-- end releaseDate -->
<!-- start verbatim event date popup -->
<div id="dialogVBDate" title="<%=encprops.getProperty("setVerbatimEventDate")%>" style="display:none">

	  <table border="1" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">
		    <tr>
		      <td align="left" valign="top" class="para"><strong><font
		        color="#990000"><%=encprops.getProperty("setVerbatimEventDate")%>:</font></strong>
		        <br />
			<font size="-1"><em><%=encprops.getProperty("useZeroIfUnknown")%>
          		</em></font>
		        </td>
		    </tr>
		    <tr>
		      <td align="left" valign="top">
		        <form name="setVerbatimEventDate" action="../EncounterSetVerbatimEventDate"
		              method="post"><input name="verbatimEventDate" type="text" size="10" maxlength="50">
		              <input name="encounter" type="hidden" value=<%=num%>>
		          <input name="Set" type="submit" id="<%=encprops.getProperty("set")%>" value="<%=encprops.getProperty("set")%>"></form>
		      </td>
		    </tr>
		  </table>
</div>
                         		<!-- popup dialog script -->
<script>
var dlgVBDate = $("#dialogVBDate").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#VBDate").click(function() {
  dlgVBDate.dialog("open");
});
</script>
<!-- start date popup -->
<div id="dialogDate" title="<%=encprops.getProperty("resetEncounterDate")%>" style="display:none">

  <table border="1" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">

    <tr>
      <td align="left" valign="top">
        <form name="setencdate" action="../EncounterResetDate" method="post">

          <div id="datepicker"></div>

          <p>
           <%=encprops.getProperty("setDate")%> <input type="text" style="position: relative; z-index: 101;" id="datepickerField" name="datepicker" size="20" /> yyyy-MM-dd HH:mm<br /> <font size="-1"><%=encprops.getProperty("leaveBlank")%></font>
          </p>

          <br />
        <input name="number" type="hidden" value="<%=num%>" id="number" />
        <input name="action" type="hidden" value="changeEncounterDate" />
        <input name="AddDate" type="submit" id="AddDate" value="<%=encprops.getProperty("setDate")%>" />
        </form>
      </td>
    </tr>
  </table>
</div>
                         		<!-- popup dialog script -->
<script>
var dlgDate = $("#dialogDate").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#date").click(function() {
  dlgDate.dialog("open");
});
</script>
<!-- end date dialog -->

</td>
</tr>
</table>




      <%

  String isLoggedInValue="true";
  String isOwnerValue="true";

  if(!loggedIn){isLoggedInValue="false";}
  if(!isOwner){isOwnerValue="false";}

%>



<br />
<h2>
	<img src="../images/2globe_128.gif" width="40px" height="40px" align="absmiddle"/> <%=encprops.getProperty("location") %>
</h2>
<%
if(enc.getLocation()!=null){
%>
<em><%=encprops.getProperty("locationDescription")%> <%=enc.getLocation()%></em>
<%
}
%>

  <%
    if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
  %><a id="location" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a>
  <%
    }
  %>
<br /><em><%=encprops.getProperty("locationID") %></em>: <%=enc.getLocationCode()%>
  <%
    if (isOwner && CommonConfiguration.isCatalogEditable(context)) {%>
  <font size="-1"><a id="locationID" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a></font>
  <a href="<%=CommonConfiguration.getWikiLocation(context)%>locationID" target="_blank"><img
    src="../images/information_icon_svg.gif" alt="Help" border="0" align="absmiddle"></a> <%
    }
  %>

<br />

 <em><%=encprops.getProperty("country") %></em>:
  <%
  if(enc.getCountry()!=null){
  %>
  <%=enc.getCountry()%>
  <%
  }
    if (isOwner && CommonConfiguration.isCatalogEditable(context)) {%>
  <font size="-1"><a id="country" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a></font>
  <a href="<%=CommonConfiguration.getWikiLocation(context)%>country" target="_blank"><img
    src="../images/information_icon_svg.gif" alt="Help" border="0" align="absmiddle"></a> <%
    }
  %>


  <!-- Display maximumDepthInMeters so long as show_maximumDepthInMeters is not false in commonCOnfiguration.properties-->
    <%
		if(CommonConfiguration.showProperty("maximumDepthInMeters",context)){
		%>
<br />
<em><%=encprops.getProperty("depth") %>

  <%
    if (enc.getDepthAsDouble() !=null) {
  %>
  <%=enc.getDepth()%> <%=encprops.getProperty("meters")%> <%
  } else {
  %> <%=encprops.getProperty("unknown") %>
  <%
    }

if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
  %>
&nbsp;<a id="depth" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a>
<%
}
%>
</em>
<%
  }
%>
<!-- End Display maximumDepthInMeters -->

<%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>
<!-- start depth popup -->
<div id="dialogDepth" title="<%=encprops.getProperty("setDepth")%>" style="display:none">

	<table border="1" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">

    <tr>
      <td align="left" valign="top">
        <form name="setencdepth" action="../EncounterSetMaximumDepth" method="post">
          <input name="depth" type="text" id="depth" size="10" /> <%=encprops.getProperty("meters")%>
          <input name="lengthUnits" type="hidden" id="lengthUnits" value="Meters" />
          <input name="number" type="hidden" value="<%=num%>" id="number" />
          <input name="action" type="hidden" value="setEncounterDepth" />
          <input name="AddDepth" type="submit" id="AddDepth" value="<%=encprops.getProperty("setDepth")%>" />
        </form>
      </td>
    </tr>
  </table>
</div>
                         		<!-- popup dialog script -->
<script>
var dlgDepth = $("#dialogDepth").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#depth").click(function() {
  dlgDepth.dialog("open");
});
</script>
<!-- end depth popup -->
<%
}
%>


<!-- Display maximumElevationInMeters so long as show_maximumElevationInMeters is not false in commonCOnfiguration.properties-->
<%
  if (CommonConfiguration.showProperty("maximumElevationInMeters",context)) {
%>
<br />
<em><%=encprops.getProperty("elevation") %></em>
&nbsp;
<%
    if (enc.getMaximumElevationInMeters()!=null) {
  %>
    <%=enc.getMaximumElevationInMeters()%> <%=encprops.getProperty("meters")%> <%
  } else {
  %>
  <%=encprops.getProperty("unknown") %>
  <%
    }

 if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
  %>
<a id="elev" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a>
  <%
    }
  %>


<%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>
<!-- start elevation popup -->
<div id="dialogElev" title="<%=encprops.getProperty("setElevation")%>" style="display:none">

<table border="1" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">
    <tr>
      <td align="left" valign="top">
        <form name="setencelev" action="../EncounterSetMaximumElevation" method="post">
          <input name="elevation" type="text" id="elevation" size="10" /> Meters <input
          name="lengthUnits" type="hidden" id="lengthUnits" value="Meters" />
          <input name="number" type="hidden" value="<%=num%>" id="number" />
          <input name="action" type="hidden" value="setEncounterElevation" /><br />
          <input name="AddElev" type="submit" id="AddElev" value="<%=encprops.getProperty("setElevation")%>" />
        </form>
      </td>
    </tr>
  </table>
</div>

<!-- popup dialog script -->
<script>
var dlgElev = $("#dialogElev").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#elev").click(function() {
  dlgElev.dialog("open");
});
</script>
<!-- end elevation popup -->
<%
}
%>

<%
  }
%>
<!-- End Display maximumElevationInMeters -->


  <br /><br />
 	<!-- START MAP and GPS SETTER -->

    <script type="text/javascript">
        var markers = [];
        var latLng = new google.maps.LatLng(<%=enc.getDecimalLatitude()%>, <%=enc.getDecimalLongitude()%>);
        //bounds.extend(latLng);
         	<%
         	//currently unused programatically
           	String markerText="";

           	String haploColor="CC0000";
           	if((encprops.getProperty("defaultMarkerColor")!=null)&&(!encprops.getProperty("defaultMarkerColor").trim().equals(""))){
        	   	haploColor=encprops.getProperty("defaultMarkerColor");
           	}


           	%>

       marker = new google.maps.Marker({
    	   icon: 'https://chart.googleapis.com/chart?chst=d_map_pin_letter&chld=<%=markerText%>|<%=haploColor%>',
    	   position:latLng,
    	   map:map
    	});

	   		<%
	   		if((enc.getDecimalLatitude()==null)&&(enc.getDecimalLongitude()==null)){
	   		%>
	   			marker.setVisible(false);

	   		<%
	   		}
 			%>

       markers.push(marker);
       //map.fitBounds(bounds);

      function fullScreen(){
    		$("#map_canvas").addClass('full_screen_map');
    		$('html, body').animate({scrollTop:0}, 'slow');
    		//hide header
    		$("#header_menu").hide();
    		initialize();
    		if(overlaysSet){overlaysSet=false;setOverlays();}
    		//alert("Trying to execute fullscreen!");
    	}

    	function exitFullScreen() {
    		$("#header_menu").show();
    		$("#map_canvas").removeClass('full_screen_map');

    		initialize();
    		if(overlaysSet){overlaysSet=false;setOverlays();}
    		//alert("Trying to execute exitFullScreen!");
    	}


    	//making the exit fullscreen button
    	function FSControl(controlDiv, map) {

    	  // Set CSS styles for the DIV containing the control
    	  // Setting padding to 5 px will offset the control
    	  // from the edge of the map
    	  controlDiv.style.padding = '5px';

    	  // Set CSS for the control border
    	  var controlUI = document.createElement('DIV');
    	  controlUI.style.backgroundColor = '#f8f8f8';
    	  controlUI.style.borderStyle = 'solid';
    	  controlUI.style.borderWidth = '1px';
    	  controlUI.style.borderColor = '#a9bbdf';;
    	  controlUI.style.boxShadow = '0 1px 3px rgba(0,0,0,0.5)';
    	  controlUI.style.cursor = 'pointer';
    	  controlUI.style.textAlign = 'center';
    	  controlUI.title = 'Toggle the fullscreen mode';
    	  controlDiv.appendChild(controlUI);

    	  // Set CSS for the control interior
    	  var controlText = document.createElement('DIV');
    	  controlText.style.fontSize = '12px';
    	  controlText.style.fontWeight = 'bold';
    	  controlText.style.color = '#000000';
    	  controlText.style.paddingLeft = '4px';
    	  controlText.style.paddingRight = '4px';
    	  controlText.style.paddingTop = '3px';
    	  controlText.style.paddingBottom = '2px';
    	  controlUI.appendChild(controlText);
    	  //toggle the text of the button
    	   if($("#map_canvas").hasClass("full_screen_map")){
    	      controlText.innerHTML = '<%=encprops.getProperty("exitFullscreen")%>';
    	    } else {
    	      controlText.innerHTML = '<%=encprops.getProperty("fullscreen")%>';
    	    }

    	  // Setup the click event listeners: toggle the full screen

    	  google.maps.event.addDomListener(controlUI, 'click', function() {

    	   if($("#map_canvas").hasClass("full_screen_map")){
    	    exitFullScreen();
    	    } else {
    	    fullScreen();
    	    }
    	  });

    	}



      google.maps.event.addDomListener(window, 'load', initialize);
    </script>

 	<%
 	if((request.getUserPrincipal()!=null) && ((enc.getLatitudeAsDouble()!=null)&&(enc.getLongitudeAsDouble()!=null))){
 	%>
 		<p><%=encprops.getProperty("map_note") %></p>
 		<div id="map_canvas" style="width: 510px; height: 350px; "></div>
 	<%
 	}
 	else {
 	%>
 	<p><%=encprops.getProperty("nomap") %></p>
 	<%
 	}
 	%>
 	<!-- adding ne submit GPS-->



 	<%
 	if(isOwner){
 		String longy="";
       	String laty="";
       	if(enc.getLatitudeAsDouble()!=null){laty=enc.getLatitudeAsDouble().toString();}
       	if(enc.getLongitudeAsDouble()!=null){longy=enc.getLongitudeAsDouble().toString();}

     	%>
     	<a name="gps"></a>
     		<table>
     			<tr>
					<td>
					<form name="resetGPSform" method="post" action="../EncounterSetGPS">
				    	<input name="action" type="hidden" value="resetGPS" />

						<strong><%=encprops.getProperty("latitude")%>:</strong>

						<input name="lat" type="text" id="lat" size="10" value="<%=laty%>" /> &deg;
						<strong><%=encprops.getProperty("longitude")%>:</strong>
						<input name="longitude" type="text" id="longitude" size="10" value="<%=longy%>" />&nbsp;&deg;
						<br />
						<input name="setGPSbutton" type="submit" id="setGPSbutton" value="<%=encprops.getProperty("setGPS")%>" />

						<br/>
						<br/>
						<%=encprops.getProperty("gpsConverter")%> <a href="http://www.csgnetwork.com/gpscoordconv.html" target="_blank">Click here to find a converter.</a>
						<input name="number" type="hidden" value=<%=num%> />

					</form>
				</td>
			</tr>
     	</table>
     	<%
 		}  //end isOwner
     	%>
<br /> <br />
 <!--end adding submit GPS-->
 <!-- END MAP and GPS SETTER -->


<%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>
<!-- start locationID popup-->
<div id="dialogLocationID" title="<%=encprops.getProperty("setLocationID")%>" style="display:none">

  <table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">
    <tr>
      <td align="left" valign="top">
        <form name="addLocCode" action="../EncounterSetLocationID" method="post">

              <%
              if(CommonConfiguration.getProperty("locationID0",context)==null){
              %>
              <input name="code" type="text" size="10" maxlength="50" />
              <%
              }
              else{
            	  //iterate and find the locationID options
            	  %>
            	  <select name="code" id="code">
						            	<option value=""></option>

						       <%
						       boolean hasMoreLocs=true;
						       int taxNum=0;
						       while(hasMoreLocs){
						       	  String currentLoc = "locationID"+taxNum;
						       	  if(CommonConfiguration.getProperty(currentLoc,context)!=null){
						       	  	%>

						       	  	  <option value="<%=CommonConfiguration.getProperty(currentLoc,context)%>"><%=CommonConfiguration.getProperty(currentLoc,context)%></option>
						       	  	<%
						       		taxNum++;
						          }
						          else{
						             hasMoreLocs=false;
						          }

						       }
						       %>


						      </select>


            <%
              }
              %>

                                   <input name="number" type="hidden" value="<%=num%>" />
                                   <input name="action" type="hidden" value="addLocCode" />
          							<input name="Set Location ID" type="submit" id="Add" value="<%=encprops.getProperty("setLocationID")%>" />
          </form>
      </td>
    </tr>
  </table>
</div>
                         		<!-- popup dialog script -->
<script>
var dlgLocationID = $("#dialogLocationID").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#locationID").click(function() {
  dlgLocationID.dialog("open");
});
</script>
<!-- end locationID -->


<!-- start location popup -->
<div id="dialogLocation" title="<%=encprops.getProperty("setLocation")%>" style="display:none">

  <table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">
    <tr>
      <td align="left" valign="top">
        <form name="setLocation" action="../EncounterSetLocation" method="post">
        <%
        String thisLocation="";
        if(enc.getLocation()!=null){
        	thisLocation=enc.getLocation().trim();
        }
        %>
        <textarea name="location" size="15"><%=thisLocation%></textarea>
          <input name="number" type="hidden" value="<%=num%>" />
          <input name="action" type="hidden" value="setLocation" />
          <input name="Add" type="submit" id="Add" value="<%=encprops.getProperty("setLocation")%>" />
        </form>
      </td>
    </tr>
  </table>
</div>
                         		<!-- popup dialog script -->
<script>
var dlgLocation = $("#dialogLocation").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#location").click(function() {
  dlgLocation.dialog("open");
});
</script>
<!-- end location -->


<!-- start country popup -->
<div id="dialogCountry" title="<%=encprops.getProperty("resetCountry")%>" style="display:none">

		<table border="1" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF" >
			<tr>
				<td align="left" valign="top" class="para"><strong><font color="#990000">
					<%=encprops.getProperty("resetCountry")%>:</font></strong><br /> <font size="-1"><%=encprops.getProperty("leaveBlank")%></font>
						    </td>
						  </tr>
						  <tr>
						    <td align="left" valign="top">
						      <form name="countryForm" action="../EncounterSetCountry" method="post">
						            <select name="country" id="country">
						            	<option value=""></option>

						       <%



				                  String[] locales = Locale.getISOCountries();
				  				  for (String countryCode : locales) {
				  					Locale obj = new Locale("", countryCode);
				  				    %>
				                      <option value="<%=obj.getDisplayCountry() %>"><%=obj.getDisplayCountry() %></option>

								 <%
				              	 }
								 %>



						      </select>
						      <input name="encounter" type="hidden" value="<%=num%>" id="number" />
						        <input name="<%=encprops.getProperty("set")%>" type="submit" id="<%=encprops.getProperty("set")%>" value="<%=encprops.getProperty("set")%>" />
						      </form>
						    </td>
						  </tr>
						</table>
</div>
                         		<!-- popup dialog script -->
<script>
var dlgCountry = $("#dialogCountry").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#country").click(function() {
  dlgCountry.dialog("open");
});
</script>
<!-- end country popup-->

<%
    }
%>

<br />

<table>
<tr>
<td width="560px" style="vertical-align:top; background-color: #E8E8E8">

<h2><img align="absmiddle" src="../images/Crystal_Clear_kuser2.png" width="40px" height="42px" /> <%=encprops.getProperty("contactInformation") %></h2>

<table>
	<tr>
		<td valign="top">
			<p class="para"><em><%=encprops.getProperty("submitter") %></em>
				<%
 				if(isOwner&&CommonConfiguration.isCatalogEditable(context)) {
 				%>
 					<a id="submitter" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a>
    			<%
 				}
 				%>
 				<%
 				if(enc.getSubmitterName()!=null){
 				%>
 				<br/><%=enc.getSubmitterName()%>
 				<%
 				}
     			if (isOwner) {

					if((enc.getSubmitterEmail()!=null)&&(!enc.getSubmitterEmail().equals(""))&&(enc.getSubmitterEmail().indexOf(",")!=-1)) {
						//break up the string
						StringTokenizer stzr=new StringTokenizer(enc.getSubmitterEmail(),",");
						while(stzr.hasMoreTokens()) {
							String nextie=stzr.nextToken();
							%>
							<br/><a href="mailto:<%=nextie%>?subject=<%=encprops.getProperty("contactEmailMessageHeading") %><%=enc.getCatalogNumber()%>:<%=CommonConfiguration.getProperty("htmlTitle",context)%>"><%=nextie%></a>
							<%
						}

					}
					else if((enc.getSubmitterEmail()!=null)&&(!enc.getSubmitterEmail().equals(""))) {
					%> <br/>
						<a href="mailto:<%=enc.getSubmitterEmail()%>?subject=<%=encprops.getProperty("contactEmailMessageHeading") %><%=enc.getCatalogNumber()%>:<%=CommonConfiguration.getProperty("htmlTitle",context)%>"><%=enc.getSubmitterEmail()%></a>
					<%
					}

					if((enc.getSubmitterPhone()!=null)&&(!enc.getSubmitterPhone().equals(""))){
					%>
						<br/> <%=enc.getSubmitterPhone()%>
					<%
					}
					if((enc.getSubmitterAddress()!=null)&&(!enc.getSubmitterAddress().equals(""))){
					%>
					<br /><%=enc.getSubmitterAddress()%>
					<%
					}

					if((enc.getSubmitterOrganization()!=null)&&(!enc.getSubmitterOrganization().equals(""))){%>
						<br/><%=enc.getSubmitterOrganization()%>
					<%
					}
					if((enc.getSubmitterProject()!=null)&&(!enc.getSubmitterProject().equals(""))){%>
						<br/><%=enc.getSubmitterProject()%>
					<%
					}

				}

				if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
				%>
				</p>
				<!-- start submitter popup -->
				<div id="dialogSubmitter" title="<%=encprops.getProperty("editContactInfo")%> (<%=encprops.getProperty("submitter")%>)" style="display:none">
					<form name="setPersonalDetails" action="../EncounterSetSubmitterPhotographerContactInfo" method="post">
  						<table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">
    						<%

    						String sName="";
    						if(enc.getSubmitterName()!=null){sName=enc.getSubmitterName();}
    						String sEmail="";
    						if(enc.getSubmitterEmail()!=null){sEmail=enc.getSubmitterEmail();}
    						String sPhone="";
    						if(enc.getSubmitterPhone()!=null){sPhone=enc.getSubmitterPhone();}
    						String sAddress="";
    						if(enc.getSubmitterAddress()!=null){sAddress=enc.getSubmitterAddress();}
    						String sOrg="";
    						if(enc.getSubmitterOrganization()!=null){sOrg=enc.getSubmitterOrganization();}
    						String sProject="";
    						if(enc.getSubmitterProject()!=null){sProject=enc.getSubmitterProject();}

    						%>

    						<tr>
      							<td>
       								<input type="hidden" name="contact" value="submitter" />
									<%=encprops.getProperty("name")%><br />
          						</td>
          						<td>
          							<input name="name" type="text" size="20" value="<%=sName %>" maxlength="100"></input>
          						</td>
          					</tr>
          					<tr>
          						<td>
          							<%=encprops.getProperty("email")%><br />
          						</td>
          						<td>
          							<input name="email" type="text" value="<%=sEmail %>" size="20"></input>
          						</td>
          					</tr>
          					<tr>
          						<td>
          							<%=encprops.getProperty("phone")%>
          						</td>
          						<td>
          							<input name="phone" type="text" size="20" value="<%=sPhone %>" maxlength="100"></input>
          						</td>
          					</tr>
          					<tr>
          						<td>
          							<%=encprops.getProperty("address")%>
          						</td>
          						<td>
          							<input name="address" type="text" size="20" value="<%=sAddress %>" maxlength="100"></input>
          						</td>
          					</tr>
          					<tr>
          						<td>
           							<%=encprops.getProperty("submitterOrganization")%>
          						</td>
          						<td>
          							<input name="submitterOrganization" type="text" size="20" value="<%=sOrg %>" maxlength="100"></input>
          						</td>
          					</tr>
          					<tr>
          						<td>
          							<%=encprops.getProperty("submitterProject")%>
	   							</td>
	   							<td>
	   								<input name="submitterProject" type="text" size="20" value="<%=sProject %>" maxlength="100"></input>
	            				</td>
	            			</tr>
          					<tr>
          						<td>
            						<input name="number" type="hidden" value="<%=num%>" />
            						<input name="action" type="hidden" value="editcontact" />
            						<input name="EditContact" type="submit" id="EditContact" value="Update" />
     							</td>
    						</tr>
  					</table>
	 			</form>
			</div>

			<script>
				var dlgSubmitter = $("#dialogSubmitter").dialog({
  					autoOpen: false,
  					draggable: false,
  					resizable: false,
  					width: 600
				});

				$("a#submitter").click(function() {
  					dlgSubmitter.dialog("open");
				});
			</script>
			<!-- end submitter popup -->
		<%
		}
		%>
		</td>
		<td valign="top">
			<p class="para">
				<em><%=encprops.getProperty("photographer") %></em>
				<%
 				if(isOwner&&CommonConfiguration.isCatalogEditable(context)) {
		 		%>
		 			<a id="photographer" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a>
    			<%
 				}
 				if(enc.getPhotographerName()!=null){
 				%>
 					<br/> <%=enc.getPhotographerName()%>
 				<%
 				}

				if (isOwner) {

					if((enc.getPhotographerEmail()!=null)&&(!enc.getPhotographerEmail().equals(""))){
					%>
						<br/><a href="mailto:<%=enc.getPhotographerEmail()%>?subject=<%=encprops.getProperty("contactEmailMessageHeading") %><%=enc.getCatalogNumber()%>:<%=CommonConfiguration.getProperty("htmlTitle",context)%>"><%=enc.getPhotographerEmail()%></a>
					<%
					}
					if((enc.getPhotographerPhone()!=null)&&(!enc.getPhotographerPhone().equals(""))){
					%>
						<br/><%=enc.getPhotographerPhone()%>
					<%
					}
					if((enc.getPhotographerAddress()!=null)&&(!enc.getPhotographerAddress().equals(""))){
					%>
						<br/><%=enc.getPhotographerAddress()%>
					<%
					}

					if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
					%>
						<!-- start submitter popup -->
						<div id="dialogPhotographer" title="<%=encprops.getProperty("editContactInfo")%> (<%=encprops.getProperty("photographer")%>)" style="display:none">
							<form action="../EncounterSetSubmitterPhotographerContactInfo" method="post">
  								<table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">
    									<%

    						String pName="";
    						if(enc.getPhotographerName()!=null){pName=enc.getPhotographerName();}
    						String pEmail="";
    						if(enc.getPhotographerEmail()!=null){pEmail=enc.getPhotographerEmail();}
    						String pPhone="";
    						if(enc.getPhotographerPhone()!=null){pPhone=enc.getPhotographerPhone();}
    						String pAddress="";
    						if(enc.getPhotographerAddress()!=null){pAddress=enc.getPhotographerAddress();}

    						%>

    								<tr>
      									<td>
       										<input type="hidden" name="contact" value="photographer" />

          									<%=encprops.getProperty("name")%><br />
          								</td>
          								<td>
          									<input name="name" type="text" size="20" value="<%=pName %>" maxlength="100"></input>
          								</td>
          							</tr>
          							<tr>
          								<td>
          									<%=encprops.getProperty("email")%><br />
          								</td>
          								<td>
          									<input name="email" type="text" value="<%=pEmail %>" size="20"></input>
          								</td>
          							</tr>
          							<tr>
          								<td>
          									<%=encprops.getProperty("phone")%>
          								</td>
          								<td>
          									<input name="phone" type="text" size="20" value="<%=pPhone %>" maxlength="100"></input>
          								</td>
          							</tr>
          							<tr>
          								<td>
          									<%=encprops.getProperty("address")%>
          								</td>
          								<td>
          									<input name="address" type="text" size="20" value="<%=pAddress %>" maxlength="100"></input>
          								</td>
          							</tr>
          							<tr>
          								<td>
            								<input name="number" type="hidden" value="<%=num%>" />
            								<input name="action" type="hidden" value="editcontact" />
            								<input name="EditContact" type="submit" id="EditContact" value="Update" />
      									</td>
    								</tr>
  								</table>
	 						</form>
						</div>

						<script>
							var dlgPhotographer = $("#dialogPhotographer").dialog({
  								autoOpen: false,
  								draggable: false,
 								resizable: false,
 								width: 600
							});

							$("a#photographer").click(function() {
  								dlgPhotographer.dialog("open");
							});
						</script>
						<!-- end photographer popup -->
					<%
					}
				}
				%>
				</p>
				</td>
			</tr>
			<%
			if(isOwner){
			%>
			<tr>
				<td colspan="2">
					<p class="para">
						<em>
							<%=encprops.getProperty("inform_others") %>
						</em>
						<%
 						if(isOwner&&CommonConfiguration.isCatalogEditable(context)) {
 						%>
 							<a id="inform" class="launchPopup">
 								<img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" />
 							</a>
    					<%
 						}
 						%>
 						<br/>
 						<%
    					if(enc.getInformOthers()!=null){

        					if(enc.getInformOthers().indexOf(",")!=-1) {
        						//break up the string
        						StringTokenizer stzr=new StringTokenizer(enc.getInformOthers(),",");

        						while(stzr.hasMoreTokens()) {
        						%>
        							<%=stzr.nextToken()%><br/>
        						<%
								}

							}
							else{
							%>
								<%=enc.getInformOthers()%><br/> <%
							}
						}
						else {
						%>
  							<%=encprops.getProperty("none") %>
  						<%
						}
 						%>
 					</p>
 					<%
					if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
					%>

						<div id="dialogInform" title="<%=encprops.getProperty("setOthersToInform")%>" style="display:none">
							<p>
								<em><%=encprops.getProperty("separateEmails") %></em>
							</p>
  							<table cellpadding="1" bordercolor="#FFFFFF">
    							<tr>
      								<td align="left" valign="top">
        								<form name="setOthers" action="../EncounterSetInformOthers" method="post">
          									<input name="encounter" type="hidden" value="<%=num%>" />
          									<input name="informothers" type="text" size="50" <%if(enc.getInformOthers()!=null){%> value="<%=enc.getInformOthers().trim()%>" <%}%> maxlength="1000" />
          									<br />
          									<input name="Set" type="submit" id="Set" value="<%=encprops.getProperty("set")%>" />
        								</form>
      								</td>
    							</tr>
  							</table>
						</div>
                        <!-- popup dialog script -->
						<script>
							var dlgInform = $("#dialogInform").dialog({
  								autoOpen: false,
  								draggable: false,
  								resizable: false,
  								width: 600
							});

							$("a#inform").click(function() {
  								dlgInform.dialog("open");
							});
						</script>
						<!-- end inform others popup -->
					<%
					}
					}
					%>

 				</td>
 			</tr>
 		</table>


  </td>
  </tr>
  </table>

  <br />
  <h2><img align="absmiddle" src="../images/Note-Book-icon.png" width="40px" height="40px" /> <%=encprops.getProperty("observationAttributes") %></h2>
<!-- START TAXONOMY ATTRIBUTE -->
<%
    if(CommonConfiguration.showProperty("showTaxonomy",context)){

    String genusSpeciesFound=encprops.getProperty("notAvailable");
    if((enc.getGenus()!=null)&&(enc.getSpecificEpithet()!=null)){genusSpeciesFound=enc.getGenus()+" "+enc.getSpecificEpithet();}
    %>

        <p class="para"><img align="absmiddle" src="../images/taxontree.gif">
          <%=encprops.getProperty("taxonomy")%>: <em><%=genusSpeciesFound%></em>&nbsp;<%
            if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
          %><a id="taxon" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a><%
            }
          %>
       </p>

  <%
    if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
    %>
    <!-- start set taxonomy ID popup -->
<div id="dialogTaxon" title="<%=encprops.getProperty("resetTaxonomy")%>" style="display:none">
			<table border="0" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">

			  <tr>
			    <td align="left" valign="top">
			      <form name="taxonomyForm" action="../EncounterSetGenusSpecies" method="post">
			           <img align="absmiddle" src="../images/taxontree.gif" /> <select name="genusSpecies" id="genusSpecies">
			            	<option value="unknown"><%=encprops.getProperty("notAvailable")%></option>

			       <%
			       boolean hasMoreTax=true;
			       int taxNum=0;
			       while(hasMoreTax){
			       	  String currentGenuSpecies = "genusSpecies"+taxNum;
			       	  if(CommonConfiguration.getProperty(currentGenuSpecies,context)!=null){
			       	  	%>

			       	  	  <option value="<%=CommonConfiguration.getProperty(currentGenuSpecies,context)%>"><%=CommonConfiguration.getProperty(currentGenuSpecies,context).replaceAll("_"," ")%></option>
			       	  	<%
			       		taxNum++;
			          }
			          else{
			             hasMoreTax=false;
			          }

			       }
			       %>


			      </select> <input name="encounter" type="hidden" value="<%=num%>" id="number">
			        <input name="<%=encprops.getProperty("set")%>" type="submit" id="<%=encprops.getProperty("set")%>" value="<%=encprops.getProperty("set")%>">
			      </form>
			    </td>
			  </tr>
			</table>
</div>
                         		<!-- popup dialog script -->
<script>
var dlgTaxon = $("#dialogTaxon").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#taxon").click(function() {
  dlgTaxon.dialog("open");
});
</script>
<%
}


}
%>
<!-- END TAXONOMY ATTRIBUTE -->


<!-- START ALIVE-DEAD ATTRIBUTE -->
<p class="para">
      <%=encprops.getProperty("status")%>:
      <%
      if(enc.getLivingStatus()!=null){
      %>
      <%=enc.getLivingStatus()%>
       <%
    }
        if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
      %><a id="livingStatus" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a><%
        }
      %>
    </p>
    <%
    if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
    %>
        <!-- start set living status popup -->
<div id="dialogLivingStatus" title="<%=encprops.getProperty("resetStatus")%>" style="display:none">
<table border="1" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">

  <tr>
    <td align="left" valign="top">
      <form name="livingStatusForm" action="../EncounterSetLivingStatus" method="post">
            <select name="livingStatus" id="livingStatus">
        <option value="alive" selected><%=encprops.getProperty("alive")%></option>
        <option value="dead"><%=encprops.getProperty("dead")%></option>
        <option value="unknown">unknown</option>
      </select> <input name="encounter" type="hidden" value="<%=num%>" id="number" />
        <input name="Add" type="submit" id="Add" value="<%=encprops.getProperty("resetStatus")%>" />
      </form>
    </td>
  </tr>
</table>
</div>
<!-- popup dialog script -->
<script>
var dlgLivingStatus = $("#dialogLivingStatus").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#livingStatus").click(function() {
  dlgLivingStatus.dialog("open");
});
</script>
<%
    }
%>
<!-- END ALIVE-DEAD ATTRIBUTE -->

<!--  START SEX SECTION -->
<%
String sex="";
if(enc.getSex()!=null){sex=enc.getSex();}
%>
<p class="para"><%=encprops.getProperty("sex") %>&nbsp;<%=sex %>
<%
if(isOwner&&CommonConfiguration.isCatalogEditable(context)) {
 %>
 <a id="sex" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a>
<%
}
%>
</p>

<p class="para">Class:
<%=enc.getZebraClass()%>
</p>

<%
if(isOwner&&CommonConfiguration.isCatalogEditable(context)) {
%>
<!-- start elevation popup -->
<div id="dialogSex" title="<%=encprops.getProperty("resetSex")%>" style="display:none">

<table border="1" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">

  <tr>
    <td align="left" valign="top">
      <form name="setxencshark" action="../EncounterSetSex" method="post">
        <select name="selectSex" size="1" id="selectSex">
          <option value="unknown" selected><%=encprops.getProperty("unknown")%>
          </option>
          <option value="male"><%=encprops.getProperty("male")%>
          </option>
          <option value="female"><%=encprops.getProperty("female")%>
          </option>
        </select>
        <input name="number" type="hidden" value="<%=num%>" id="number" />
        <input name="action" type="hidden" value="setEncounterSex" />
        <input name="Add" type="submit" id="Add" value="<%=encprops.getProperty("resetSex")%>" />
      </form>
    </td>
  </tr>
</table>
</div>

<!-- popup dialog script -->
<script>
var dlgSex = $("#dialogSex").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#sex").click(function() {
  dlgSex.dialog("open");
});
</script>
<!-- end sex popup -->
<%
 	}
 %>
 <!--  END SEX SECTION -->


<!--  START SCARRING SECTION -->
<p class="para"><%=encprops.getProperty("scarring") %>&nbsp;

<%
String recordedScarring="";
if(enc.getDistinguishingScar()!=null){recordedScarring=enc.getDistinguishingScar();}
%>
<%=recordedScarring%>
<%
if(isOwner&&CommonConfiguration.isCatalogEditable(context)) {
 %>
<a id="scar" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a>
<%
}
%>
</p>
<%
if(isOwner&&CommonConfiguration.isCatalogEditable(context)) {
 %>
<div id="dialogScar" title="<%=encprops.getProperty("editScarring")%>" style="display:none">

  <table border="0" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">

    <tr>
      <td align="left" valign="top">
        <form name="setencsize" action="../EncounterSetScarring" method="post">
          <textarea name="scars" size="15"><%=enc.getDistinguishingScar()%>
          </textarea>
          <input name="number" type="hidden" value="<%=num%>" id="number" />
          <input name="action" type="hidden" value="setScarring" />
          <br />
          <input name="Add" type="submit" id="scar" value="<%=encprops.getProperty("resetScarring")%>" />
        </form>
      </td>
    </tr>
  </table>
</div>

<!-- popup dialog script -->
<script>
var dlgScar = $("#dialogScar").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#scar").click(function() {
  dlgScar.dialog("open");
});
</script>


    <%
 	}
 	%>
<!--  END SCARRING SECTION -->


<!--  START BEHAVIOR SECTION -->
<p class="para"><%=encprops.getProperty("behavior") %>&nbsp;

  <%
    if (enc.getBehavior() != null) {
  %>
  <%=enc.getBehavior()%>
  <%
  } else {
  %>
  <%=encprops.getProperty("none")%>
  <%
    }

if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
	  %>
<a id="behavior" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a>
	  <%
	    }
%>
</p>


  <%
    if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
    %>
    <!-- start set behavior popup -->
<div id="dialogBehavior" title="<%=encprops.getProperty("editBehaviorComments")%>" style="display:none">
			  <table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">
    <tr>
      <td align="left" valign="top" class="para">
      	<p><em><font size="-1"><%=encprops.getProperty("leaveBlank")%></font></em></p>
      </td>
    </tr>
    <tr>
      <td align="left" valign="top">
        <form name="setBehaviorComments" action="../EncounterSetBehavior" method="post">
        <textarea name="behaviorComment" cols="50"><%

         if((enc.getBehavior()!=null)&&(!enc.getBehavior().trim().equals(""))){
         %>
<%=enc.getBehavior().trim()%>
        <%
        }
        %>
        </textarea>
          <input name="number" type="hidden" value="<%=num%>" />
          <input name="action" type="hidden" value="editBehavior" /> <br />
          <input name="EditBeh" type="submit" id="EditBeh" value="<%=encprops.getProperty("submitEdit")%>" />
        </form>
      </td>
    </tr>
  </table>
</div>

<!-- popup dialog script -->
<script>
var dlgBehavior = $("#dialogBehavior").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#behavior").click(function() {
  dlgBehavior.dialog("open");
});
</script>
<%
}
%>
<!--  END BEHAVIOR SECTION -->


<!--  START PATTERNING CODE SECTION -->
<%
  if (CommonConfiguration.showProperty("showPatterningCode",context)) {
%>
<p class="para"><%=encprops.getProperty("patterningCode") %>&nbsp;

  <%
    if (enc.getPatterningCode() != null) {
  %>
  <%=enc.getPatterningCode()%>
  <%
  } else {
  %>
  <%=encprops.getProperty("none")%>
  <%
    }

if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
	  %>
<a id="patterningCode" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a>
	  <%
	    }
%>
</p>


  <%
    if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
    %>
    <!-- start set patterning code popup -->
<div id="dialogPatterningCode" title="<%=encprops.getProperty("editPatterningCode")%>" style="display:none">
			  <table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">
    <tr>
      <td align="left" valign="top" class="para">
      	<p><em><font size="-1"><%=encprops.getProperty("leaveBlank")%></font></em></p>
      </td>
    </tr>
    <tr>
      <td align="left" valign="top">
        <form name="setPatterningCode" action="../EncounterSetPatterningCode" method="post">
         <%
              if(CommonConfiguration.getProperty("patterningCode0",context)==null){
              %>
              <input name="patterningCode" type="text" size="10" maxlength="50" />
              <%
              }
              else{
            	  //iterate and find the locationID options
            	  %>
            	  <select name="patterningCode" id="colorCode">
						            	<option value=""></option>

						       <%
						       boolean hasMoreLocs=true;
						       int taxNum=0;
						       while(hasMoreLocs){
						       	  String currentLoc = "patterningCode"+taxNum;
						       	  if(CommonConfiguration.getProperty(currentLoc,context)!=null){
						       	  	%>

						       	  	  <option value="<%=CommonConfiguration.getProperty(currentLoc,context)%>"><%=CommonConfiguration.getProperty(currentLoc,context)%></option>
						       	  	<%
						       		taxNum++;
						          }
						          else{
						             hasMoreLocs=false;
						          }

						       }
						       %>


						      </select>


            <%
              }
              %>
          <input name="number" type="hidden" value="<%=num%>" />
          <input name="EditPC" type="submit" id="EditPC" value="<%=encprops.getProperty("submitEdit")%>" />
        </form>
      </td>
    </tr>
  </table>
</div>

<!-- popup dialog script -->
<script>
var dlgPatterningCode = $("#dialogPatterningCode").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#patterningCode").click(function() {
	dlgPatterningCode.dialog("open");
});
</script>
<%
}
  }
%>
<!--  END PATTERNING CODE SECTION -->


<!--  START LIFESTAGE SECTION -->
<%
  if (CommonConfiguration.showProperty("showLifestage",context)) {
%>
<p class="para"><%=encprops.getProperty("lifeStage")%>&nbsp;

  <%
    if (enc.getLifeStage() != null) {
  %>
  <%=enc.getLifeStage()%>
  <%
  }
 %>
 <%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
  %>
  <a id="LifeStage" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a>
  <%
    }
  %>
</p>

 <%
    if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
    %>
    <!-- start set life stage popup -->
<div id="dialogLifeStage" title="<%=encprops.getProperty("resetLifeStage")%>" style="display:none">
	<table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">

						  <tr>
						    <td align="left" valign="top">
						      <form name="lifeStageForm" action="../EncounterSetLifeStage" method="post">
						            <select name="lifeStage" id="lifeStage">
						            	<option value=""></option>

						       <%
						       boolean hasMoreStages=true;
						       int taxNum=0;
						       while(hasMoreStages){
						       	  String currentLifeStage = "lifeStage"+taxNum;
						       	  if(CommonConfiguration.getProperty(currentLifeStage,context)!=null){
						       	  	%>

						       	  	  <option value="<%=CommonConfiguration.getProperty(currentLifeStage,context)%>"><%=CommonConfiguration.getProperty(currentLifeStage,context)%></option>
						       	  	<%
						       		taxNum++;
						          }
						          else{
						             hasMoreStages=false;
						          }

						       }
						       %>


						      </select>
						      <input name="encounter" type="hidden" value="<%=num%>" id="number"/>
						        <input name="<%=encprops.getProperty("set")%>" type="submit" id="<%=encprops.getProperty("set")%>" value="<%=encprops.getProperty("set")%>" />
						      </form>
						    </td>
						  </tr>
						</table>
</div>
                         		<!-- popup dialog script -->
<script>
var dlgLifeStage = $("#dialogLifeStage").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#LifeStage").click(function() {
  dlgLifeStage.dialog("open");
});
</script>
<%
  }
}
  %>
<!--  END LIFESTAGE SECTION -->


<!-- START ADDITIONAL COMMENTS -->
<p class="para"><%=encprops.getProperty("comments") %>
  <%
    if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
  %>&nbsp;<a id="comments" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a>
  <%
    }
  %>
<br/>
<%
String recordedComments="";
if(enc.getComments()!=null){recordedComments=enc.getComments();}
%>
<em><%=recordedComments%></em>

</p>
<br/>
<%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>

<div id="dialogComments" title="<%=encprops.getProperty("editSubmittedComments")%>" style="display:none">

<table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">

      <td align="left" valign="top" cols="50">
        <form name="setComments" action="../EncounterSetOccurrenceRemarks" method="post"><textarea name="fixComment" size="15"><%=enc.getComments()%>
</textarea>
          <input name="number" type="hidden" value="<%=num%>" />
          <input name="action" type="hidden" value="editComments" />
           <br /><input name="EditComm" type="submit" id="EditComm" value="<%=encprops.getProperty("submitEdit")%>" /></form>
      </td>
    </tr>
  </table>

</div>
                         		<!-- popup dialog script -->
<script>
var dlgComments = $("#dialogComments").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#comments").click(function() {
  dlgComments.dialog("open");
});
</script>
<!-- end addtl comments popup -->
<%
}
%>
<!-- END ADDITIONAL COMMENTS -->

<br />
<table>
<tr>
<td width="560px" style="vertical-align:top; background-color: #E8E8E8">

<h2><img align="absmiddle" width="40px" height="40px" style="border-style: none;" src="../images/workflow_icon.gif" /> <%=encprops.getProperty("metadata") %></h2>


								<p class="para">
									Number: <%=num%>
								</p>
								<!-- START WORKFLOW ATTRIBUTE -->


 								<%

									String state="";
									if (enc.getState()!=null){state=enc.getState();}
									%>
									<p class="para">
										 <%=encprops.getProperty("workflowState") %> <%=state %>

										<%
										if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
										%>
										<a id="state" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a>
										<%
										}
										%>

									</p>
									<%
										if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
									%>
   									<div id="dialogState" title="<%=encprops.getProperty("setWorkflowState")%>" style="display:none">
  										<table class="popupForm">
						  					<tr>
						    					<td align="left" valign="top">
						      						<form name="countryForm" action="../EncounterSetState" method="post">
						            					<select name="state" id="state">
															<%
						       								boolean hasMoreStates=true;
						       								int taxNum=0;
						       								while(hasMoreStates){
						       	  								String currentLifeState = "encounterState"+taxNum;
						       	  								if(CommonConfiguration.getProperty(currentLifeState,context)!=null){
						       	  									%>
						       	  	  								<option value="<%=CommonConfiguration.getProperty(currentLifeState,context)%>"><%=CommonConfiguration.getProperty(currentLifeState,context)%></option>
						       	  									<%
						       										taxNum++;
						          								}
						          								else{
						             								hasMoreStates=false;
						          								}

						       								} //end while
						       								%>
						      						</select>
						      						<input name="number" type="hidden" value="<%=num%>" id="number" />
						        					<input name="<%=encprops.getProperty("set")%>" type="submit" id="<%=encprops.getProperty("set")%>" value="<%=encprops.getProperty("set")%>" />
						      					</form>
						    				</td>
						  				</tr>
									</table>
  								</div>
								<script>
  									var dlgState = $("#dialogState").dialog({
    									autoOpen: false,
    									draggable: false,
    									resizable: false,
    									width: 600
  									});

  									$("a#state").click(function() {
    									dlgState.dialog("open");
  									});
  								</script>
       							<%
        						}
      							%>
								<!-- END WORKFLOW ATTRIBUTE -->


								<!-- START USER ATTRIBUTE -->
								<%
 								if((CommonConfiguration.showUsersToPublic(context))||(request.getUserPrincipal()!=null)){
 								%>

    							<table>
    								<tr>
    									<td>
     										<img align="absmiddle" src="../images/Crystal_Clear_app_Login_Manager.gif" /> <%=encprops.getProperty("assigned_user")%>&nbsp;
     									</td>
        								<%
        								if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
      									%>
      									<td>
      										<a id="user" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a>
      									</td>
      									<%
        								}
      									%>
     								</tr>
     								<tr>
     									<td>
                         				<%
                         				if(enc.getAssignedUsername()!=null){

                        	 				String username=enc.getAssignedUsername();
                        	 				Shepherd aUserShepherd=new Shepherd("context0");
                         					if(aUserShepherd.getUser(username)!=null){
                         					%>
                                			<table>
                                			<%

                         					User thisUser=aUserShepherd.getUser(username);
                                			String profilePhotoURL="../images/empty_profile.jpg";

                         					if(thisUser.getUserImage()!=null){
                         						profilePhotoURL="/"+CommonConfiguration.getDataDirectoryName("context0")+"/users/"+thisUser.getUsername()+"/"+thisUser.getUserImage().getFilename();
                         					}
                         					%>
                     						<tr>
                     							<td>
                     								<center>
                     									<div style="height: 50px">
															<a id="username" class="launchPopup"><img style="height: 100%" border="1" align="top" src="<%=profilePhotoURL%>"  /></a>
														</div>
													</center>
												</td>
											</tr>
                     						<%
                         					String displayName="";
                         					if(thisUser.getFullName()!=null){
                         						displayName=thisUser.getFullName();
                         						%>
                         					<tr>
                         						<td style="border:none">
                         							<center>
                         								<a class="launchPopup" id="username" style="font-weight:normal;border:none"><%=displayName %></a>
                         							</center>
                         						</td>
                         					</tr>
                         					<tr>
                         						<td>
                         							<center>
                         								<p class="caption">(click to learn more)</p>
                         							</center>
                         						</td>
                         					</tr>
                         					<%
                         					}
                         					else{
                                			%>
                                			<tr>
                                				<td>&nbsp;</td>
                                			</tr>
                                			<%
                                			}
                         					%>
                         				</table>

                         				<!-- Now prep the popup dialog -->
                         				<div id="dialog" title="<%=displayName %>" style="display:none">
                         					<table cellpadding="3px">
                         						<tr>
                         							<td>
                         								<div style="height: 150px"><img border="1" align="top" src="<%=profilePhotoURL%>" style="height: 100%" />
                         			</td>
                         			<td><p>
                         			<%
                         			if(thisUser.getAffiliation()!=null){
                         			%>
                         			<strong>Affiliation:</strong> <%=thisUser.getAffiliation() %><br />
                         			<%
                         			}

                         			if(thisUser.getUserProject()!=null){
                         			%>
                         			<strong>Research Project:</strong> <%=thisUser.getUserProject() %><br />
                         			<%
                         			}

                         			if(thisUser.getUserURL()!=null){
                             			%>
                             			<strong>Web site:</strong> <a style="font-weight:normal;color: blue" class="ecocean" href="<%=thisUser.getUserURL()%>"><%=thisUser.getUserURL() %></a><br />
                             			<%
                             		}

                         			if(thisUser.getUserStatement()!=null){
                             			%>
                             			<br /><em>"<%=thisUser.getUserStatement() %>"</em>
                             			<%
                             		}
                         			%>
                         			</p>
                         			</td></tr></table>
                         		</div>
                         		<!-- popup dialog script -->
<script>
var dlg = $("#dialog").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#username").click(function() {
  dlg.dialog("open");
});
</script>
<%
                         	}


                      	else{
                      	%>
                      	&nbsp;
                      	<%
                      	}
                        aUserShepherd.rollbackDBTransaction();
                        aUserShepherd.closeDBTransaction();
                      	}
                         				//insert here
                         	if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>


<!-- start set username popup -->
<div id="dialogUser" title="<%=encprops.getProperty("assignUser")%>" style="display:none">

	    <table border="1" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">
    <tr>
      <td align="left" valign="top" class="para"><font
        color="#990000"><img align="absmiddle"
                             src="../images/Crystal_Clear_app_Login_Manager.gif"/>
        <strong><%=encprops.getProperty("assignUser")%>:</strong></font></td>
    </tr>
    <tr>
      <td align="left" valign="top">
        <form name="asetSubmID" action="../EncounterSetSubmitterID" method="post">

          <select name="submitter" id="submitter">
        	<option value=""></option>
        	<%

        	Shepherd userShepherd=new Shepherd("context0");
        	userShepherd.beginDBTransaction();
        	ArrayList<String> usernames=userShepherd.getAllUsernames();



        	int numUsers=usernames.size();
        	for(int i=0;i<numUsers;i++){
        		String thisUsername=usernames.get(i);
        		User thisUser2=userShepherd.getUser(thisUsername);
        		String thisUserFullname=thisUsername;
        		if(thisUser2.getFullName()!=null){thisUserFullname=thisUser2.getFullName();}
        	%>
        	<option value="<%=thisUsername%>"><%=thisUserFullname%></option>
        	<%
			}
        	userShepherd.rollbackDBTransaction();
        	userShepherd.closeDBTransaction();
        	%>
      	</select>

          <input name="number" type="hidden" value="<%=num%>" />
          <input name="Assign" type="submit" id="Assign" value="<%=encprops.getProperty("assign")%>" />
        </form>
      </td>
    </tr>
  </table>
</div>
                         		<!-- popup dialog script -->
<script>
var dlgUser = $("#dialogUser").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#user").click(function() {
  dlgUser.dialog("open");
});
</script>



                         		<%
                         	}

                         }
                         else {
                         %>
                         &nbsp;
                         <%
                         }
                        %>
                        </td>


    </tr></table>


<!-- END USER ATTRIBUTE -->

<!-- START TAPIRLINK DISPLAY AND SETTER -->
<%
if (isOwner) {
%>
<table width="100%" border="0" cellpadding="1">
    <tr>
      <td height="30" class="para">
        <form name="setTapirLink" method="post" action="../EncounterSetTapirLinkExposure">
              <input name="action" type="hidden" id="action" value="tapirLinkExpose" />
              <input name="number" type="hidden" value="<%=num%>" />
              <%
              String tapirCheckIcon="cancel.gif";
              if(enc.getOKExposeViaTapirLink()){tapirCheckIcon="check_green.png";}
              %>
              TapirLink:&nbsp;<input align="absmiddle" name="approve" type="image" src="../images/<%=tapirCheckIcon %>" id="approve" value="<%=encprops.getProperty("change")%>" />&nbsp;<a href="<%=CommonConfiguration.getWikiLocation(context)%>tapirlink" target="_blank"><img src="../images/information_icon_svg.gif" alt="Help" border="0" align="absmiddle"/></a>
        </form>
      </td>
    </tr>
  </table>
<!-- END TAPIRLINK DISPLAY AND SETTER -->
<%
}
%>

<!-- START DELETE ENCOUNTER FORM -->
<%
if (isOwner) {
%><br />
<table width="100%" border="0" cellpadding="1">
    <tr>
      <td height="30" class="para">
        <form onsubmit="return confirm('<%=encprops.getProperty("sureDelete") %>');" name="deleteEncounter" method="post" action="../EncounterDelete">
              <input name="number" type="hidden" value="<%=num%>" />
              <%
              String deleteIcon="cancel.gif";
              %>
              <img src="../images/Warning_icon_small.png" align="absmiddle" />&nbsp;<%=encprops.getProperty("deleteEncounter") %> <input align="absmiddle" name="approve" type="image" src="../images/<%=deleteIcon %>" id="deleteButton" />
        </form>
      </td>
    </tr>
  </table>
<!-- END DELETE ENCOUNTER FORM -->
<%
}
%>

<!-- START AUTOCOMMENTS -->
<p class="para"><%=encprops.getProperty("auto_comments")%> <a id="autocomments" class="launchPopup"><img height="40px" width="40px" align="middle" src="../images/Crystal_Clear_app_kaddressbook.gif" /></a></p>

<!-- start autocomments popup -->
<div id="dialogAutoComments" title="<%=encprops.getProperty("auto_comments")%>" style="display:none">
<table>
  <tr>
    <td valign="top">

      <%
      String rComments="";
      if(enc.getRComments()!=null){rComments=enc.getRComments();}
      %>

      <div style="text-align:left;border:1px solid black;width:575px;height:400px;overflow-y:scroll;overflow-x:scroll;">

      		<p class="para"><%=rComments.replaceAll("\n", "<br />")%></p>
      </div>

      <%
      if(isOwner && CommonConfiguration.isCatalogEditable(context)){
      %>
      <form action="../EncounterAddComment" method="post" name="addComments">
        <p class="para">
          <input name="user" type="hidden" value="<%=request.getRemoteUser()%>" id="user" />
          <input name="number" type="hidden" value="<%=enc.getEncounterNumber()%>" id="number" />
          <input name="action" type="hidden" value="enc_comments" id="action" />
		</p>
        <p>
          <textarea name="autocomments" cols="50" id="autocomments"></textarea> <br/>
          <input name="Submit" type="submit" value="<%=encprops.getProperty("add_comment")%>" />
        </p>
      </form>
      <%
      }
      %>



    </td>
  </tr>
</table>
</div>

<script>
var dlgAutoComments = $("#dialogAutoComments").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#autocomments").click(function() {
  dlgAutoComments.dialog("open");
});
</script>
<!-- END AUTOCOMMENTS -->

<%
  pageContext.setAttribute("showMeasurements", CommonConfiguration.showMeasurements(context));
  pageContext.setAttribute("showMetalTags", CommonConfiguration.showMeasurements(context));
  pageContext.setAttribute("showAcousticTag", CommonConfiguration.showAcousticTag(context));
  pageContext.setAttribute("showSatelliteTag", CommonConfiguration.showSatelliteTag(context));
%>
</td>
</tr>
</table>

<c:if test="${showMeasurements}">
<br />
<%
  pageContext.setAttribute("measurementTitle", encprops.getProperty("measurements"));
  pageContext.setAttribute("measurements", Util.findMeasurementDescs(langCode,context));
%>
<h2><img align="absmiddle" width="40px" height="40px" style="border-style: none;" src="../images/ruler.png" /> <c:out value="${measurementTitle}"></c:out></h2>
<c:if test="${editable and !empty measurements}">
  <a id="measure" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a></font>
</c:if>
<table>
<tr>
<th class="measurement"><%=encprops.getProperty("type") %></th><th class="measurement"><%=encprops.getProperty("size") %></th><th class="measurement"><%=encprops.getProperty("units") %></th><c:if test="${!empty samplingProtocols}"><th class="measurement"><%=encprops.getProperty("samplingProtocol") %></th></c:if>
</tr>
<c:forEach var="item" items="${measurements}">
 <%
    MeasurementDesc measurementDesc = (MeasurementDesc) pageContext.getAttribute("item");
    //Measurement event =  enc.findMeasurementOfType(measurementDesc.getType());
    Measurement event=myShepherd.getMeasurementOfTypeForEncounter(measurementDesc.getType(), num);
    if (event != null) {
        pageContext.setAttribute("measurementValue", event.getValue());
        pageContext.setAttribute("samplingProtocol", Util.getLocalizedSamplingProtocol(event.getSamplingProtocol(), langCode,context));
    }
    else {
        pageContext.setAttribute("measurementValue", null);
        pageContext.setAttribute("samplingProtocol", null);
   }
 %>
<tr>
    <td class="measurement"><c:out value="${item.label}"/></td><td class="measurement"><c:out value="${measurementValue}"/></td><td class="measurement"><c:out value="${item.unitsLabel}"/></td><td class="measurement"><c:out value="${samplingProtocol}"/></td>
</tr>
</c:forEach>
</table>
</p>

<%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>

<div id="dialogMeasure" title="<%=encprops.getProperty("setMeasurements")%>" style="display:none">
 <%
   pageContext.setAttribute("items", Util.findMeasurementDescs(langCode,context));
 %>

       <table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">
        <form name="setMeasurements" method="post" action="../EncounterSetMeasurements">
        <input type="hidden" name="encounter" value="${num}"/>
        <c:set var="index" value="0"/>
        <%
          List<Measurement> list = (List<Measurement>) enc.getMeasurements();

        %>
        <c:forEach items="${items}" var="item">
        <%
          MeasurementDesc measurementDesc = (MeasurementDesc) pageContext.getAttribute("item");
          Measurement measurement = enc.findMeasurementOfType(measurementDesc.getType());
          if (measurement == null) {
              measurement = new Measurement(enc.getEventID(), measurementDesc.getType(), null, measurementDesc.getUnits(), null);
          }
          pageContext.setAttribute("measurementEvent", measurement);
          pageContext.setAttribute("optionDescs", Util.findSamplingProtocols(langCode,context));
        %>
            <tr>
              <td class="form_label"><c:out value="${item.label}"/><input type="hidden" name="measurement${index}(id)" value="${measurementEvent.dataCollectionEventID}"/></td>
              <td><input name="measurement${index}(value)" value="${measurementEvent.value}"/>
                  <input type="hidden" name="measurement${index}(type)" value="${item.type}"/><input type="hidden" name="measurement${index}(units)" value="${item.unitsLabel}"/><c:out value="(${item.unitsLabel})"/>
                  <select name="measurement${index}(samplingProtocol)">
                  <c:forEach items="${optionDescs}" var="optionDesc">
                    <c:choose>
                    <c:when test="${measurementEvent.samplingProtocol eq optionDesc.name}">
                      <option value="${optionDesc.name}" selected="selected"><c:out value="${optionDesc.display}"/></option>
                    </c:when>
                    <c:otherwise>
                      <option value="${optionDesc.name}"><c:out value="${optionDesc.display}"/></option>
                    </c:otherwise>
                    </c:choose>
                  </c:forEach>
                  </select>
              </td>
            </tr>
            <c:set var="index" value="${index + 1}"/>
        </c:forEach>
        <tr>
        <td><input name="${set}" type="submit" value="${set}"/></td>
        </tr>
        </form>
        </table>

</div>
                         		<!-- popup dialog script -->
<script>
var dlgMeasure = $("#dialogMeasure").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#measure").click(function() {
  dlgMeasure.dialog("open");
});
</script>
<!-- end measurements popup -->
<%
}
%>


</c:if>

<table>
<tr>
<td width="560px" style="vertical-align:top; background-color: #E8E8E8">



<c:if test="${showMetalTags}">

<h2><img align="absmiddle" src="../images/Crystal_Clear_app_starthere.png" width="40px" height="40px" /> <%=encprops.getProperty("tracking") %></h2>
<%
  pageContext.setAttribute("metalTagTitle", encprops.getProperty("metalTags"));
  pageContext.setAttribute("metalTags", Util.findMetalTagDescs(langCode,context));
%>
<p class="para"><em><c:out value="${metalTagTitle}"></c:out></em>
<%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>
&nbsp;<a id="metal" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a>
<%
}
%>
<table>
<c:forEach var="item" items="${metalTags}">
 <%
    MetalTagDesc metalTagDesc = (MetalTagDesc) pageContext.getAttribute("item");
    MetalTag metalTag =  enc.findMetalTagForLocation(metalTagDesc.getLocation());
    pageContext.setAttribute("number", metalTag == null ? null : metalTag.getTagNumber());
    pageContext.setAttribute("locationLabel", metalTagDesc.getLocationLabel());
 %>
<tr>
    <td><c:out value="${locationLabel}:"/></td><td><c:out value="${number}"/></td>
</tr>
</c:forEach>
</table>
</p>


<%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>
<!-- start metal tag popup -->
<div id="dialogMetal" title="<%=encprops.getProperty("resetMetalTags")%>" style="display:none">

        <% pageContext.setAttribute("metalTagDescs", Util.findMetalTagDescs(langCode,context)); %>

 <form name="setMetalTags" method="post" action="../EncounterSetTags">
 <input type="hidden" name="tagType" value="metalTags"/>
 <input type="hidden" name="encounter" value="${num}"/>
 <table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">

 <c:forEach items="${metalTagDescs}" var="metalTagDesc">
    <%
      MetalTagDesc metalTagDesc = (MetalTagDesc) pageContext.getAttribute("metalTagDesc");
      MetalTag metalTag = Util.findMetalTag(metalTagDesc, enc);
      if (metalTag == null) {
          metalTag = new MetalTag();
      }
      pageContext.setAttribute("metalTag", metalTag);
    %>
    <tr><td class="formLabel"><c:out value="${metalTagDesc.locationLabel}"/></td></tr>
    <tr><td><input name="metalTag(${metalTagDesc.location})" value="${metalTag.tagNumber}"/></td></tr>
 </c:forEach>
 <tr><td><input name="${set}" type="submit" value="${set}"/></td></tr>
 </table>
 </form>


</div>
                         		<!-- popup dialog script -->
<script>
var dlgMetal = $("#dialogMetal").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#metal").click(function() {
  dlgMetal.dialog("open");
});
</script>
<!-- end metal tags popup -->
<%
}
%>
</c:if>

<c:if test="${showAcousticTag}">
<%
  pageContext.setAttribute("acousticTagTitle", encprops.getProperty("acousticTag"));
  pageContext.setAttribute("acousticTag", enc.getAcousticTag());
%>
<p class="para"><em><c:out value="${acousticTagTitle}"></c:out></em>
<c:if test="${editable}">
&nbsp;
<%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>
<a id="acoustic" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a>
<%
}
%>
</c:if>
<table>
<tr>
    <td><%=encprops.getProperty("serialNumber") %></td><td><c:out value="${empty acousticTag ? '' : acousticTag.serialNumber}"/></td>
</tr>
<tr>
    <td>ID:</td><td><c:out value="${empty acousticTag ? '' : acousticTag.idNumber}"/></td>
</tr>
</table>
</p>


<%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>
<!-- start acoustic tag popup -->
<div id="dialogAcoustic" title="<%=encprops.getProperty("resetAcousticTag")%>" style="display:none">

<c:set var="acousticTag" value="${enc.acousticTag}"/>
 <c:if test="${empty acousticTag}">
 <%
   pageContext.setAttribute("acousticTag", new AcousticTag());
 %>
 </c:if>
 <table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">

    <tr>
      <td>
        <form name="setAcousticTag" method="post" action="../EncounterSetTags">
        <input type="hidden" name="encounter" value="${num}"/>
        <input type="hidden" name="tagType" value="acousticTag"/>
        <input type="hidden" name="id" value="${acousticTag.id}"/>
        <table>
          <tr><td class="formLabel"><%=encprops.getProperty("serialNumber") %></td></tr>
          <tr><td><input name="acousticTagSerial" value="${acousticTag.serialNumber}"/></td></tr>
          <tr><td class="formLabel">ID:</td></tr>
          <tr><td><input name="acousticTagId" value="${acousticTag.idNumber}"/></td></tr>
          <tr><td><input name="${set}" type="submit" value="${set}"/></td></tr>
        </table>
        </form>
      </td>
    </tr>
 </table>


</div>
                         		<!-- popup dialog script -->
<script>
var dlgAcoustic = $("#dialogAcoustic").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#acoustic").click(function() {
  dlgAcoustic.dialog("open");
});
</script>
<!-- end acoustic tag popup -->
<%
}
%>

</c:if>


<c:if test="${showSatelliteTag}">
<%
  pageContext.setAttribute("satelliteTagTitle", encprops.getProperty("satelliteTag"));
  pageContext.setAttribute("satelliteTag", enc.getSatelliteTag());
%>
<p class="para"><em><c:out value="${satelliteTagTitle}"></c:out></em>
<%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>
&nbsp;<a id="sat" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a>
<%
}
%>
<table>
<tr>
    <td><%=encprops.getProperty("name") %></td><td><c:out value="${satelliteTag.name}"/></td>
</tr>
<tr>
    <td><%=encprops.getProperty("serialNumber") %></td><td><c:out value="${empty satelliteTag ? '' : satelliteTag.serialNumber}"/></td>
</tr>
<tr>
    <td>Argos PTT:</td><td><c:out value="${empty satelliteTag ? '' : satelliteTag.argosPttNumber}"/></td>
</tr>
</table>
</p>

<%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>
<!-- start sat tag metadata popup -->
<div id="dialogSat" title="<%=encprops.getProperty("resetSatelliteTag")%>" style="display:none">

 <c:set var="satelliteTag" value="${enc.satelliteTag}"/>
 <c:if test="${empty satelliteTag}">
 <%
   pageContext.setAttribute("satelliteTag", new SatelliteTag());
 %>
 </c:if>
 <%
    pageContext.setAttribute("satelliteTagNames", Util.findSatelliteTagNames(context));
 %>
 <form name="setSatelliteTag" method="post" action="../EncounterSetTags">
 <input type="hidden" name="tagType" value="satelliteTag"/>
 <input type="hidden" name="encounter" value="${num}"/>
 <input type="hidden" name="id" value="${satelliteTag.id}"/>
 <table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">

    <tr><td class="formLabel"><%=encprops.getProperty("name") %></td></tr>
    <tr><td>
      <select name="satelliteTagName">
      <c:forEach items="${satelliteTagNames}" var="satelliteTagName">
        <c:choose>
            <c:when test="${satelliteTagName eq satelliteTag.name}">
                <option value="${satelliteTagName}" selected="selected">${satelliteTagName}</option>
            </c:when>
            <c:otherwise>
                <option value="${satelliteTagName}">${satelliteTagName}</option>
            </c:otherwise>
        </c:choose>
      </c:forEach>
      </select>
    </td></tr>
    <tr><td class="formLabel"><%=encprops.getProperty("serialNumber") %></td></tr>
    <tr><td><input name="satelliteTagSerial" value="${satelliteTag.serialNumber}"/></td></tr>
    <tr><td class="formLabel">Argos PTT:</td></tr>
    <tr><td><input name="satelliteTagArgosPttNumber" value="${satelliteTag.argosPttNumber}"/></td></tr>
    <tr><td><input name="${set}" type="submit" value="${set}"/></td></tr>
 </table>
 </form>


</div>
                         		<!-- popup dialog script -->
<script>
var dlgSat = $("#dialogSat").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#sat").click(function() {
  dlgSat.dialog("open");
});
</script>
<!-- end sat tag popup -->
<%
}
%></c:if>
</td>
</tr>
</table>

<h2><img align="absmiddle" src="../images/lightning_dynamic_props.gif" /> <%=encprops.getProperty("dynamicProperties") %></h2>
<%
if(isOwner){
%>
	<a id="dynamicPropertyAdd" class="launchPopup">
		<img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit_add.png" />
	</a>
<%
}


  if (enc.getDynamicProperties() != null) {
    //let's create a TreeMap of the properties
    StringTokenizer st = new StringTokenizer(enc.getDynamicProperties(), ";");
    int numDynProps=0;
    while (st.hasMoreTokens()) {
      String token = st.nextToken();
      int equalPlace = token.indexOf("=");
      String nm = token.substring(0, (equalPlace)).replaceAll(" ", "_");
      String vl = token.substring(equalPlace + 1);
      numDynProps++;
%>
<p class="para"> <em><%=nm%></em>: <%=vl%>
  <%
    if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
  %>
  <a id="dynamicProperty<%=nm%>" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a>

  <%
    }
  %>

  <%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>
<!-- start depth popup -->
<div id="dialogDP<%=nm %>" title="<%=encprops.getProperty("set")%> <%=nm %>" style="display:none">

 <table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">

    <tr>
      <td align="left" valign="top" class="para">
        <form name="addDynProp" action="../EncounterSetDynamicProperty" method="post">
			<p><em><%=encprops.getProperty("setDPMessage") %></em></p>
			<input name="name" type="hidden" size="10" value="<%=nm %>" />
          <%=encprops.getProperty("propertyValue")%>:<br/><input name="value" type="text" size="10" maxlength="500" value="<%=vl %>"/>
          <input name="number" type="hidden" value="<%=num%>" />
          <input name="Set" type="submit" id="<%=encprops.getProperty("set")%>" value="<%=encprops.getProperty("initCapsSet")%>" />
        </form>
      </td>
    </tr>
  </table>

</div>

<script>
var dlgDP<%=nm %> = $("#dialogDP<%=nm %>").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#dynamicProperty<%=nm%>").click(function() {
	dlgDP<%=nm %>.dialog("open");
});
</script>

<%
}
%>

</p>


<%
  }
    if(numDynProps==0){
    	  %>
    	  <p><%=encprops.getProperty("none")%></p>
    	  <%
   	}

  }
//display a message if none are defined
else{
	  %>
	  <p><%=encprops.getProperty("none")%></p>
	  <%
	    }

if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>
<!-- start depth popup -->
<div id="dialogDPAdd" title="<%=encprops.getProperty("addDynamicProperty")%>" style="display:none">

 <table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">

    <tr>
      <td align="left" valign="top" class="para">
        <form name="addDynProp" action="../EncounterSetDynamicProperty" method="post">
			<%=encprops.getProperty("propertyName")%>:<br/><input name="name" type="text" size="10" maxlength="500" /><br />

          <%=encprops.getProperty("propertyValue")%>:<br/><input name="value" type="text" size="10" maxlength="500" /><br />
          <input name="number" type="hidden" value="<%=num%>" />
          <input name="Set" type="submit" id="<%=encprops.getProperty("set")%>" value="<%=encprops.getProperty("initCapsSet")%>" />
        </form>
      </td>
    </tr>
  </table>

</div>

<script>
var dlgDPAdd = $("#dialogDPAdd").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#dynamicPropertyAdd").click(function() {
	dlgDPAdd.dialog("open");
});
</script>

<%
}
%>


  </td>


  <!-- here lies the photo gallery  -->
  <td style="vertical-align: top;padding-left: 10px;">

    <jsp:include page="encounterMediaGallery.jsp" flush="true">
    	<jsp:param name="encounterNumber" value="<%=num%>" />
    	<jsp:param name="isOwner" value="<%=isOwner %>" />
    	<jsp:param name="loggedIn" value="<%=loggedIn %>" />
  	</jsp:include>

  </td>
</tr>
</table>








<td width="250px" align="left" valign="top">
<%
//String isLoggedInValue="true";
//String isOwnerValue="true";

if(!loggedIn){isLoggedInValue="false";}
if(!isOwner){isOwnerValue="false";}
%>






<%
  if (CommonConfiguration.allowAdoptions(context)) {
%>
<div class="module">
  <jsp:include page="encounterAdoptionEmbed.jsp" flush="true">
    <jsp:param name="encounterNumber" value="<%=enc.getCatalogNumber()%>"/>
  </jsp:include>
</div>
<%
  }
%>
</td>
</tr>
</table>
<%
if(loggedIn){
%>
<hr />
<a name="tissueSamples" />
<p class="para"><img align="absmiddle" src="../images/microscope.gif" />
    <strong><%=encprops.getProperty("tissueSamples") %></strong>
</p>
    <p class="para">
    	<a id="sample" class="launchPopup"><img align="absmiddle" width="24px" style="border-style: none;" src="../images/Crystal_Clear_action_edit_add.png" /></a>&nbsp;<a id="sample" class="launchPopup"><%=encprops.getProperty("addTissueSample") %></a>
    </p>

<%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>
<div id="dialogSample" title="<%=encprops.getProperty("setTissueSample")%>" style="display:none">

<form id="setTissueSample" action="../EncounterSetTissueSample" method="post">
<table cellspacing="2" bordercolor="#FFFFFF" >
    <tr>

      	<td>

          <%=encprops.getProperty("sampleID")%> (<%=encprops.getProperty("required")%>)</td><td>
          <%
          TissueSample thisSample=new TissueSample();
          String sampleIDString="";
          if((request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("tissueSample"))&&(request.getParameter("sampleID")!=null) && (request.getParameter("function")!=null) && (request.getParameter("function").equals("1")) &&(myShepherd.isTissueSample(request.getParameter("sampleID"), request.getParameter("number")))){
        	  sampleIDString=request.getParameter("sampleID");
        	  thisSample=myShepherd.getTissueSample(sampleIDString, enc.getCatalogNumber());

          }
          %>
          <input name="sampleID" type="text" size="20" maxlength="100" value="<%=sampleIDString %>" />
        </td>
     </tr>

     <tr>
     	<td>
          <%
          String alternateSampleID="";
          if(thisSample.getAlternateSampleID()!=null){alternateSampleID=thisSample.getAlternateSampleID();}
          %>
          <%=encprops.getProperty("alternateSampleID")%></td><td><input name="alternateSampleID" type="text" size="20" maxlength="100" value="<%=alternateSampleID %>" />
       </td>
   	</tr>

    <tr>
    	<td>
          <%
          String tissueType="";
          if(thisSample.getTissueType()!=null){tissueType=thisSample.getTissueType();}
          %>
          <%=encprops.getProperty("tissueType")%>
       </td>
       <td>
              <%
              if(CommonConfiguration.getProperty("tissueType0",context)==null){
              %>
              <input name="tissueType" type="text" size="20" maxlength="50" />
              <%
              }
              else{
            	  //iterate and find the locationID options
            	  %>
            	  <select name="tissueType" id="tissueType">
						            	<option value=""></option>

						       <%
						       boolean hasMoreLocs=true;
						       int taxNum=0;
						       while(hasMoreLocs){
						       	  String currentLoc = "tissueType"+taxNum;
						       	  if(CommonConfiguration.getProperty(currentLoc,context)!=null){

						       		  String selected="";
						       		  if(tissueType.equals(CommonConfiguration.getProperty(currentLoc,context))){selected="selected=\"selected\"";}
						       	  	%>

						       	  	  <option value="<%=CommonConfiguration.getProperty(currentLoc,context)%>" <%=selected %>><%=CommonConfiguration.getProperty(currentLoc,context)%></option>
						       	  	<%
						       		taxNum++;
						          }
						          else{
						             hasMoreLocs=false;
						          }

						       }
						       %>


						      </select>


            <%
              }
              %>
           </td></tr>

          <tr><td>
          <%
          String preservationMethod="";
          if(thisSample.getPreservationMethod()!=null){preservationMethod=thisSample.getPreservationMethod();}
          %>
          <%=encprops.getProperty("preservationMethod")%></td><td><input name="preservationMethod" type="text" size="20" maxlength="100" value="<%=preservationMethod %>"/>
          </td></tr>

          <tr><td>
          <%
          String storageLabID="";
          if(thisSample.getStorageLabID()!=null){storageLabID=thisSample.getStorageLabID();}
          %>
          <%=encprops.getProperty("storageLabID")%></td><td><input name="storageLabID" type="text" size="20" maxlength="100" value="<%=storageLabID %>"/>
          </td></tr>

          <tr><td>
          <%
          String samplingProtocol="";
          if(thisSample.getSamplingProtocol()!=null){samplingProtocol=thisSample.getSamplingProtocol();}
          %>
          <%=encprops.getProperty("samplingProtocol")%></td><td><input name="samplingProtocol" type="text" size="20" maxlength="100" value="<%=samplingProtocol %>" />
          </td></tr>

          <tr><td>
          <%
          String samplingEffort="";
          if(thisSample.getSamplingEffort()!=null){samplingEffort=thisSample.getSamplingEffort();}
          %>
          <%=encprops.getProperty("samplingEffort")%></td><td><input name="samplingEffort" type="text" size="20" maxlength="100" value="<%=samplingEffort%>"/>
     		</td></tr>

			<tr><td>
          <%
          String fieldNumber="";
          if(thisSample.getFieldNumber()!=null){fieldNumber=thisSample.getFieldNumber();}
          %>
		  <%=encprops.getProperty("fieldNumber")%></td><td><input name="fieldNumber" type="text" size="20" maxlength="100" value="<%=fieldNumber %>" />
          </td></tr>


          <tr><td>
          <%
          String fieldNotes="";
          if(thisSample.getFieldNotes()!=null){fieldNotes=thisSample.getFieldNotes();}
          %>
           <%=encprops.getProperty("fieldNotes")%></td><td><input name="fieldNNotes" type="text" size="20" maxlength="100" value="<%=fieldNotes %>" />
          </td></tr>

          <tr><td>
          <%
          String eventRemarks="";
          if(thisSample.getEventRemarks()!=null){eventRemarks=thisSample.getEventRemarks();}
          %>
          <%=encprops.getProperty("eventRemarks")%></td><td><input name="eventRemarks" type="text" size="20" value="<%=eventRemarks %>" />
          </td></tr>

          <tr><td>
          <%
          String institutionID="";
          if(thisSample.getInstitutionID()!=null){institutionID=thisSample.getInstitutionID();}
          %>
          <%=encprops.getProperty("institutionID")%></td><td><input name="institutionID" type="text" size="20" maxlength="100" value="<%=institutionID %>" />
          </td></tr>


          <tr><td>
          <%
          String collectionID="";
          if(thisSample.getCollectionID()!=null){collectionID=thisSample.getCollectionID();}
          %>
          <%=encprops.getProperty("collectionID")%></td><td><input name="collectionID" type="text" size="20" maxlength="100" value="<%=collectionID %>" />
          </td></tr>

          <tr><td>
          <%
          String collectionCode="";
          if(thisSample.getCollectionCode()!=null){collectionCode=thisSample.getCollectionCode();}
          %>
          <%=encprops.getProperty("collectionCode")%></td><td><input name="collectionCode" type="text" size="20" maxlength="100" value="<%=collectionCode %>" />
          </td></tr>

          <tr><td>
          <%
          String datasetID="";
          if(thisSample.getDatasetID()!=null){datasetID=thisSample.getDatasetID();}
          %>
			<%=encprops.getProperty("datasetID")%></td><td><input name="datasetID" type="text" size="20" maxlength="100" value="<%=datasetID %>" />
          </td></tr>


          <tr><td>
          <%
          String datasetName="";
          if(thisSample.getDatasetName()!=null){datasetName=thisSample.getDatasetName();}
          %>
          <%=encprops.getProperty("datasetName")%></td><td><input name="datasetName" type="text" size="20" maxlength="100" value="<%=datasetName %>" />
			</td></tr>


            <tr><td colspan="2">
            	<input name="encounter" type="hidden" value="<%=num%>" />
            	<input name="action" type="hidden" value="setTissueSample" />
            	<input name="EditTissueSample" type="submit" id="EditTissueSample" value="<%=encprops.getProperty("set")%>" />
   			</td></tr>
      </td>
    </tr>
  </table>
</form>
</div>
                         		<!-- popup dialog script -->
<script>
var dlgSample = $("#dialogSample").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#sample").click(function() {
  dlgSample.dialog("open");
  $("#setTissueSample").find("input[type=text], textarea").val("");


});
</script>
<!-- end add bio sample popup -->
<%
}


//setup the javascript to handle displaying an edit tissue sample dialog box
if((request.getParameter("sampleID")!=null) && (request.getParameter("edit")!=null) && request.getParameter("edit").equals("tissueSample") && (myShepherd.isTissueSample(request.getParameter("sampleID"), request.getParameter("number")))){
%>
<script>
dlgSample.dialog("open");
</script>

<%
}
%>


<p>
<%
//List<TissueSample> tissueSamples=enc.getTissueSamples();
List<TissueSample> tissueSamples=myShepherd.getAllTissueSamplesForEncounter(enc.getCatalogNumber());

if((tissueSamples!=null)&&(tissueSamples.size()>0)){

	int numTissueSamples=tissueSamples.size();

%>
<table width="100%" class="tissueSample">
<tr><th><strong><%=encprops.getProperty("sampleID") %></strong></th><th><strong><%=encprops.getProperty("values") %></strong></th><th><strong><%=encprops.getProperty("analyses") %></strong></th><th><strong><%=encprops.getProperty("editTissueSample") %></strong></th><th><strong><%=encprops.getProperty("removeTissueSample") %></strong></th></tr>
<%
for(int j=0;j<numTissueSamples;j++){
	TissueSample thisSample=tissueSamples.get(j);
	%>
	<tr><td><span class="caption"><%=thisSample.getSampleID()%></span></td><td><span class="caption"><%=thisSample.getHTMLString() %></span></td>

	<td><table>
		<%
		int numAnalyses=thisSample.getNumAnalyses();
		List<GeneticAnalysis> gAnalyses = thisSample.getGeneticAnalyses();
		for(int g=0;g<numAnalyses;g++){
			GeneticAnalysis ga = gAnalyses.get(g);
			if(ga.getAnalysisType().equals("MitochondrialDNA")){
				MitochondrialDNAAnalysis mito=(MitochondrialDNAAnalysis)ga;
				%>
				<tr><td style="border-style: none;"><strong><span class="caption"><%=encprops.getProperty("haplotype") %></strong></span></strong>: <span class="caption"><%=mito.getHaplotype() %>
				<%
				if(!mito.getSuperHTMLString().equals("")){
				%>
				<em>
				<br /><%=encprops.getProperty("analysisID")%>: <%=mito.getAnalysisID()%>
				<br /><%=mito.getSuperHTMLString()%>
				</em>
				<%
				}
				%>
				</span></td>
				<td style="border-style: none;">
					<a id="haplo<%=mito.getAnalysisID() %>" class="launchPopup"><img width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a>

							<%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>
<!-- start haplotype popup -->
<div id="dialogHaplotype<%=mito.getAnalysisID() %>" title="<%=encprops.getProperty("setHaplotype")%>" style="display:none">
<form id="setHaplotype<%=mito.getAnalysisID() %>" action="../TissueSampleSetHaplotype" method="post">
<table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">

  <tr>
    <td>


        <%=encprops.getProperty("analysisID")%> (<%=encprops.getProperty("required")%>)</td><td>
        <%
        MitochondrialDNAAnalysis mtDNA=new MitochondrialDNAAnalysis();
        mtDNA=mito;
        %>
        <input name="analysisID" type="text" size="20" maxlength="100" value="<%=mtDNA.getAnalysisID() %>" /></td>
   </tr>
   <tr>
        <%
        String haplotypeString="";
        try{
        	if(mtDNA.getHaplotype()!=null){haplotypeString=mtDNA.getHaplotype();}
        }
        catch(NullPointerException npe34){}
        %>
        <td><%=encprops.getProperty("haplotype")%> (<%=encprops.getProperty("required")%>)</td><td>
        <input name="haplotype" type="text" size="20" maxlength="100" value="<%=haplotypeString %>" />
 		</td></tr>

 		 <tr>
 		 <%
        String processingLabTaskID="";
        if(mtDNA.getProcessingLabTaskID()!=null){processingLabTaskID=mtDNA.getProcessingLabTaskID();}
        %>
        <td><%=encprops.getProperty("processingLabTaskID")%></td><td>
        <input name="processingLabTaskID" type="text" size="20" maxlength="100" value="<%=processingLabTaskID %>" />
 		</td></tr>

 		<tr><td>
  		 <%
        String processingLabName="";
        if(mtDNA.getProcessingLabName()!=null){processingLabName=mtDNA.getProcessingLabName();}
        %>
        <%=encprops.getProperty("processingLabName")%></td><td>
        <input name="processingLabName type="text" size="20" maxlength="100" value="<%=processingLabName %>" />
 		</td></tr>

 		<tr><td>
   		 <%
        String processingLabContactName="";
        if(mtDNA.getProcessingLabContactName()!=null){processingLabContactName=mtDNA.getProcessingLabContactName();}
        %>
        <%=encprops.getProperty("processingLabContactName")%></td><td>
        <input name="processingLabContactName type="text" size="20" maxlength="100" value="<%=processingLabContactName %>" />
 		</td></tr>

 		<tr><td>
   		 <%
        String processingLabContactDetails="";
        if(mtDNA.getProcessingLabContactDetails()!=null){processingLabContactDetails=mtDNA.getProcessingLabContactDetails();}
        %>
        <%=encprops.getProperty("processingLabContactDetails")%></td><td>
        <input name="processingLabContactDetails type="text" size="20" maxlength="100" value="<%=processingLabContactDetails %>" />
 		</td></tr>
 		<tr><td colspan="2">
 		  <input name="sampleID" type="hidden" value="<%=thisSample.getSampleID() %>" />
          <input name="number" type="hidden" value="<%=num%>" />
          <input name="action" type="hidden" value="setHaplotype" />
          <input name="EditTissueSample" type="submit" id="EditTissueSample" value="<%=encprops.getProperty("set")%>" />

    </td>
  </tr>
</table>
	</form>

</div>

<script>
var dlgHaplotype<%=mito.getAnalysisID() %> = $("#dialogHaplotype<%=mito.getAnalysisID() %>").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#haplo<%=mito.getAnalysisID() %>").click(function() {
  dlgHaplotype<%=mito.getAnalysisID() %>.dialog("open");

});
</script>
<!-- end haplotype popup -->
<%
}
%>

				</td><td style="border-style: none;"><a onclick="return confirm('<%=encprops.getProperty("deleteHaplotype") %>');" href="../TissueSampleRemoveHaplotype?encounter=<%=enc.getCatalogNumber()%>&sampleID=<%=thisSample.getSampleID()%>&analysisID=<%=mito.getAnalysisID() %>"><img width="20px" height="20px" style="border-style: none;" src="../images/cancel.gif" /></a></td></tr></li>
			<%
			}
			else if(ga.getAnalysisType().equals("SexAnalysis")){
				SexAnalysis mito=(SexAnalysis)ga;
				%>
				<tr><td style="border-style: none;"><strong><span class="caption"><%=encprops.getProperty("geneticSex") %></strong></span></strong>: <span class="caption"><%=mito.getSex() %>
				<%
				if(!mito.getSuperHTMLString().equals("")){
				%>
				<em>
				<br /><%=encprops.getProperty("analysisID")%>: <%=mito.getAnalysisID()%>
				<br /><%=mito.getSuperHTMLString()%>
				</em>
				<%
				}
				%>
				</span></td><td style="border-style: none;"><a id="setSex<%=thisSample.getSampleID() %>" class="launchPopup"><img width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a>

				<%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>
<!-- start genetic sex popup -->
<div id="dialogSexSet<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>" title="<%=encprops.getProperty("setSexAnalysis")%>" style="display:none">

<form name="setSexAnalysis" action="../TissueSampleSetSexAnalysis" method="post">

<table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">
<tr>
  <td>

      <%=encprops.getProperty("analysisID")%> (<%=encprops.getProperty("required")%>)<br />
      <%
      SexAnalysis mtDNA=mito;
      String analysisIDString=mtDNA.getAnalysisID();
      %>
      </td><td><input name="analysisID" type="text" size="20" maxlength="100" value="<%=analysisIDString %>" /><br />
      </td></tr>
      <tr><td>
      <%
      String haplotypeString="";
      try{
      	if(mtDNA.getSex()!=null){haplotypeString=mtDNA.getSex();}
      }
      catch(NullPointerException npe34){}
      %>
      <%=encprops.getProperty("geneticSex")%> (<%=encprops.getProperty("required")%>)<br />
      </td><td><input name="sex" type="text" size="20" maxlength="100" value="<%=haplotypeString %>" />
		</td></tr>

		<tr><td>
		 <%
      String processingLabTaskID="";
      if(mtDNA.getProcessingLabTaskID()!=null){processingLabTaskID=mtDNA.getProcessingLabTaskID();}
      %>
      <%=encprops.getProperty("processingLabTaskID")%><br />
      </td><td><input name="processingLabTaskID" type="text" size="20" maxlength="100" value="<%=processingLabTaskID %>" />
	</td></tr>

		<tr><td>
		 <%
      String processingLabName="";
      if(mtDNA.getProcessingLabName()!=null){processingLabName=mtDNA.getProcessingLabName();}
      %>
      <%=encprops.getProperty("processingLabName")%><br />
      </td><td><input name="processingLabName type="text" size="20" maxlength="100" value="<%=processingLabName %>" />
</td></tr>

		<tr><td>
 		 <%
      String processingLabContactName="";
      if(mtDNA.getProcessingLabContactName()!=null){processingLabContactName=mtDNA.getProcessingLabContactName();}
      %>
      <%=encprops.getProperty("processingLabContactName")%><br />
      </td><td><input name="processingLabContactName type="text" size="20" maxlength="100" value="<%=processingLabContactName %>" />
</td></tr>

		<tr><td>
 		 <%
      String processingLabContactDetails="";
      if(mtDNA.getProcessingLabContactDetails()!=null){processingLabContactDetails=mtDNA.getProcessingLabContactDetails();}
      %>
      <%=encprops.getProperty("processingLabContactDetails")%><br />
      </td><td><input name="processingLabContactDetails type="text" size="20" maxlength="100" value="<%=processingLabContactDetails %>" />
</td></tr>

		<tr><td>
		  <input name="sampleID" type="hidden" value="<%=thisSample.getSampleID()%>" />
        <input name="number" type="hidden" value="<%=num%>" />
        <input name="action" type="hidden" value="setSexAnalysis" />
        <input name="EditTissueSampleSexAnalysis" type="submit" id="EditTissueSampleSexAnalysis" value="<%=encprops.getProperty("set")%>" />

  </td>
</tr>
</table>
  </form>

</div>

<script>
var dlgSexSet<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %> = $("#dialogSexSet<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#setSex<%=thisSample.getSampleID() %>").click(function() {
  dlgSexSet<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>.dialog("open");

});
</script>
<!-- end genetic sex popup -->
<%
}
%>

				</td>
				<td style="border-style: none;"><a onclick="return confirm('<%=encprops.getProperty("deleteGenetic") %>');" href="../TissueSampleRemoveSexAnalysis?encounter=<%=enc.getCatalogNumber()%>&sampleID=<%=thisSample.getSampleID()%>&analysisID=<%=mito.getAnalysisID() %>"><img width="20px" height="20px" style="border-style: none;" src="../images/cancel.gif" /></a></td></tr>
			<%
			}
			else if(ga.getAnalysisType().equals("MicrosatelliteMarkers")){
				MicrosatelliteMarkersAnalysis mito=(MicrosatelliteMarkersAnalysis)ga;

			%>
			<tr>
				<td style="border-style: none;">
					<p><span class="caption"><strong><%=encprops.getProperty("msMarkers") %></strong></span>
					<%
					if((enc.getIndividualID()!=null)&&(!enc.getIndividualID().toLowerCase().equals("unassigned"))&&(request.getUserPrincipal()!=null)){
					%>
					<a href="../individualSearch.jsp?individualDistanceSearch=<%=enc.getIndividualID()%>"><img height="20px" width="20px" align="absmiddle" alt="Individual-to-Individual Genetic Distance Search" src="../images/Crystal_Clear_app_xmag.png"></img></a>
					<%
					}
					%>
					</p>
					<span class="caption"><%=mito.getAllelesHTMLString() %>
						<%
									if(!mito.getSuperHTMLString().equals("")){
									%>
									<em>
									<br /><%=encprops.getProperty("analysisID")%>: <%=mito.getAnalysisID()%>
									<br /><%=mito.getSuperHTMLString()%>
									</em>
									<%
									}
				%>

					</span>



				</td>
				<td style="border-style: none;"><a class="launchPopup" id="msmarkersSet<%=thisSample.getSampleID()%>"><img width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a></td><td style="border-style: none;"><a onclick="return confirm('<%=encprops.getProperty("deleteMSMarkers") %>');" href="../TissueSampleRemoveMicrosatelliteMarkers?encounter=<%=enc.getCatalogNumber()%>&sampleID=<%=thisSample.getSampleID()%>&analysisID=<%=mito.getAnalysisID() %>"><img width="20px" height="20px" style="border-style: none;" src="../images/cancel.gif" /></a>

															<%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>
<!-- start ms marker popup -->
<div id="dialogMSMarkersSet<%=thisSample.getSampleID().replaceAll("[-+.^:,]","")%>" title="<%=encprops.getProperty("setMsMarkers")%>" style="display:none">

<form id="setMsMarkers" action="../TissueSampleSetMicrosatelliteMarkers" method="post">

<table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">
  <tr>
    <td align="left" valign="top">

        <%=encprops.getProperty("analysisID")%> (<%=encprops.getProperty("required")%>)</td><td>
        <%
        MicrosatelliteMarkersAnalysis msDNA=new MicrosatelliteMarkersAnalysis();
        msDNA=mito;
        String analysisIDString=msDNA.getAnalysisID();
        %>
        <input name="analysisID" type="text" size="20" maxlength="100" value="<%=analysisIDString %>" /></td></tr>

		<tr><td>
 		 <%
        String processingLabTaskID="";
        if(msDNA.getProcessingLabTaskID()!=null){processingLabTaskID=msDNA.getProcessingLabTaskID();}
        %>
        <%=encprops.getProperty("processingLabTaskID")%><br />
        </td><td><input name="processingLabTaskID" type="text" size="20" maxlength="100" value="<%=processingLabTaskID %>" />
 		</td></tr>

 		<tr><td>
  		 <%
        String processingLabName="";
        if(msDNA.getProcessingLabName()!=null){processingLabName=msDNA.getProcessingLabName();}
        %>
        <%=encprops.getProperty("processingLabName")%><br />
        </td><td><input name="processingLabName" type="text" size="20" maxlength="100" value="<%=processingLabName %>" />
 		</td></tr>

 		<tr><td>
   		 <%
        String processingLabContactName="";
        if(msDNA.getProcessingLabContactName()!=null){processingLabContactName=msDNA.getProcessingLabContactName();}
        %>
        <%=encprops.getProperty("processingLabContactName")%><br />
        </td><td><input name="processingLabContactName" type="text" size="20" maxlength="100" value="<%=processingLabContactName %>" />
 		</td></tr>

 		<tr><td>
   		 <%
        String processingLabContactDetails="";
        if(msDNA.getProcessingLabContactDetails()!=null){processingLabContactDetails=msDNA.getProcessingLabContactDetails();}
        %>
        <%=encprops.getProperty("processingLabContactDetails")%><br />
        </td><td><input name="processingLabContactDetails" type="text" size="20" maxlength="100" value="<%=processingLabContactDetails %>" />
 		</td></tr>
 		<tr><td>
 		<%
 		//begin setting up the loci and alleles
 	      int numPloids=2; //most covered species will be diploids
 	      try{
 	        numPloids=(new Integer(CommonConfiguration.getProperty("numPloids",context))).intValue();
 	      }
 	      catch(Exception e){System.out.println("numPloids configuration value did not resolve to an integer.");e.printStackTrace();}

 	      int numLoci=10;
 	      try{
 	 	  	numLoci=(new Integer(CommonConfiguration.getProperty("numLoci",context))).intValue();
 	 	  }
 	 	  catch(Exception e){System.out.println("numLoci configuration value did not resolve to an integer.");e.printStackTrace();}

 		  for(int locus=0;locus<numLoci;locus++){
 			 String locusNameValue="";
 			 if((msDNA.getLoci()!=null)&&(locus<msDNA.getLoci().size())){locusNameValue=msDNA.getLoci().get(locus).getName();}
 		  %>
			<br /><%=encprops.getProperty("locus") %>: <input name="locusName<%=locus %>" type="text" size="10" value="<%=locusNameValue %>" /><br />
 				<%
 				for(int ploid=0;ploid<numPloids;ploid++){
 					Integer ploidValue=0;
 					if((msDNA.getLoci()!=null)&&(locus<msDNA.getLoci().size())&&(msDNA.getLoci().get(locus).getAllele(ploid)!=null)){ploidValue=msDNA.getLoci().get(locus).getAllele(ploid);}

 				%>
 				<%=encprops.getProperty("allele") %>: <input name="allele<%=locus %><%=ploid %>" type="text" size="10" value="<%=ploidValue %>" /><br />


 				<%
 				}
 				%>

		  <%
 		  }  //end for loci looping
		  %>

		  <tr><td colspan="2">
 		  <input name="sampleID" type="hidden" value="<%=thisSample.getSampleID()%>" />
          <input name="number" type="hidden" value="<%=num%>" />

          <input name="EditTissueSample" type="submit" id="EditTissueSample" value="<%=encprops.getProperty("set")%>" />
    </td></tr>
    </td>
  </tr>
</table>
	  </form>
</div>

<script>
var dlgMSMarkersSet<%=thisSample.getSampleID().replaceAll("[-+.^:,]","")%> = $("#dialogMSMarkersSet<%=thisSample.getSampleID().replaceAll("[-+.^:,]","")%>").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#msmarkersSet<%=thisSample.getSampleID()%>").click(function() {
  dlgMSMarkersSet<%=thisSample.getSampleID().replaceAll("[-+.^:,]","")%>.dialog("open");
});
</script>
<!-- end ms markers popup -->
<%
}

%>

				</td></tr>



			<%
			}
			else if(ga.getAnalysisType().equals("BiologicalMeasurement")){
				BiologicalMeasurement mito=(BiologicalMeasurement)ga;
				%>
				<tr><td style="border-style: none;"><strong><span class="caption"><%=mito.getMeasurementType()%> <%=encprops.getProperty("measurement") %></span></strong><br /> <span class="caption"><%=mito.getValue().toString() %> <%=mito.getUnits() %> (<%=mito.getSamplingProtocol() %>)
				<%
				if(!mito.getSuperHTMLString().equals("")){
				%>
				<em>
				<br /><%=encprops.getProperty("analysisID")%>: <%=mito.getAnalysisID()%>
				<br /><%=mito.getSuperHTMLString()%>
				</em>
				<%
				}
				%>
				</span></td><td style="border-style: none;"><a class="launchPopup" id="setBioMeasure<%=thisSample.getSampleID() %>"><img width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a>

						<%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>
<!-- start biomeasure popup -->
<div id="dialogSetBiomeasure4<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>" title="<%=encprops.getProperty("setBiologicalMeasurement")%>" style="display:none">
  <form action="../TissueSampleSetMeasurement" method="post">

<table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">


<tr>
<td>

    <%=encprops.getProperty("analysisID")%> (<%=encprops.getProperty("required")%>)<br />
    <%
    BiologicalMeasurement mtDNA=mito;
    String analysisIDString=mtDNA.getAnalysisID();

    %>
    </td><td><input name="analysisID" type="text" size="20" maxlength="100" value="<%=analysisIDString %>" /><br />
    </td></tr>

    <tr><td>
    <%
    String type="";
    if(mtDNA.getMeasurementType()!=null){type=mtDNA.getMeasurementType();}
    %>
    <%=encprops.getProperty("type")%> (<%=encprops.getProperty("required")%>)
    </td><td>


     		<%
     		ArrayList<String> values=CommonConfiguration.getSequentialPropertyValues("biologicalMeasurementType",context);
 			int numProps=values.size();
 			ArrayList<String> measurementUnits=CommonConfiguration.getSequentialPropertyValues("biologicalMeasurementUnits",context);
 			int numUnitsProps=measurementUnits.size();

     		if(numProps>0){

     			%>
     			<p><select size="<%=(numProps+1) %>" name="measurementType" id="measurementType">
     			<%

     			for(int y=0;y<numProps;y++){
     				String units="";
     				if(numUnitsProps>y){units="&nbsp;("+measurementUnits.get(y)+")";}
     				String selected="";
     				if((mtDNA.getMeasurementType()!=null)&&(mtDNA.getMeasurementType().equals(values.get(y)))){
     					selected="selected=\"selected\"";
     				}
     			%>
     				<option value="<%=values.get(y) %>" <%=selected %>><%=values.get(y) %><%=units %></option>
     			<%
     			}
     			%>
     			</select>
				</p>
			<%
     		}
     		else{
			%>
    			<input name="measurementType" type="text" size="20" maxlength="100" value="<%=type %>" />
    		<%
     		}
    %>
    </td></tr>

    <tr><td>
    <%
    String thisValue="";
    if(mtDNA.getValue()!=null){thisValue=mtDNA.getValue().toString();}
    %>
    <%=encprops.getProperty("value")%> (<%=encprops.getProperty("required")%>)<br />
    </td><td><input name="value" type="text" size="20" maxlength="100" value="<%=thisValue %>"></input>
    </td></tr>

    <tr><td>
	<%
    String thisSamplingProtocol="";
    if(mtDNA.getSamplingProtocol()!=null){thisSamplingProtocol=mtDNA.getSamplingProtocol();}
    %>
    <%=encprops.getProperty("samplingProtocol")%>
    </td><td>

     		<%
     		ArrayList<String> protovalues=CommonConfiguration.getSequentialPropertyValues("biologicalMeasurementSamplingProtocols",context);
 			int protonumProps=protovalues.size();

     		if(protonumProps>0){

     			%>
     			<p><select size="<%=(protonumProps+1) %>" name="samplingProtocol" id="samplingProtocol">
     			<%

     			for(int y=0;y<protonumProps;y++){
     				String selected="";
     				if((mtDNA.getSamplingProtocol()!=null)&&(mtDNA.getSamplingProtocol().equals(protovalues.get(y)))){
     					selected="selected=\"selected\"";
     				}
     			%>
     				<option value="<%=protovalues.get(y) %>" <%=selected %>><%=protovalues.get(y) %></option>
     			<%
     			}
     			%>
     			</select>
				</p>
			<%
     		}
     		else{
			%>
    			<input name="samplingProtocol" type="text" size="20" maxlength="100" value="<%=type %>" />
    		<%
     		}
			%>
			</td></tr>

    <tr><td>
    <%
    String processingLabTaskID="";
    if(mtDNA.getProcessingLabTaskID()!=null){processingLabTaskID=mtDNA.getProcessingLabTaskID();}
    %>
    <%=encprops.getProperty("processingLabTaskID")%><br />
    </td><td><input name="processingLabTaskID" type="text" size="20" maxlength="100" value="<%=processingLabTaskID %>" />
</td></tr>

    <tr><td>
		 <%
    String processingLabName="";
    if(mtDNA.getProcessingLabName()!=null){processingLabName=mtDNA.getProcessingLabName();}
    %>
    <%=encprops.getProperty("processingLabName")%><br />
    </td><td><input name="processingLabName" type="text" size="20" maxlength="100" value="<%=processingLabName %>" />

</td></tr>

    <tr><td>
		 <%
    String processingLabContactName="";
    if(mtDNA.getProcessingLabContactName()!=null){processingLabContactName=mtDNA.getProcessingLabContactName();}
    %>
    <%=encprops.getProperty("processingLabContactName")%><br />
    </td><td><input name="processingLabContactName" type="text" size="20" maxlength="100" value="<%=processingLabContactName %>" />
</td></tr>

    <tr><td>
		 <%
    String processingLabContactDetails="";
    if(mtDNA.getProcessingLabContactDetails()!=null){processingLabContactDetails=mtDNA.getProcessingLabContactDetails();}
    %>
    <%=encprops.getProperty("processingLabContactDetails")%><br />
    </td><td><input name="processingLabContactDetails" type="text" size="20" maxlength="100" value="<%=processingLabContactDetails %>" />
</td></tr>

    <tr><td>
		  <input name="sampleID" type="hidden" value="<%=thisSample.getSampleID()%>" />
      <input name="encounter" type="hidden" value="<%=num%>" />
      <input name="action" type="hidden" value="setBiologicalMeasurement" />
      <input name="EditTissueSampleBiomeasurementAnalysis" type="submit" id="EditTissueSampleBioMeasurementAnalysis" value="<%=encprops.getProperty("set")%>" />

</td>
</tr>
</table>
	 </form>
</div>

<script>
var dlgSetBiomeasure<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %> = $("#dialogSetBiomeasure4<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#setBioMeasure<%=thisSample.getSampleID() %>").click(function() {
  dlgSetBiomeasure<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>.dialog("open");

});
</script>
<!-- end biomeasure popup -->
<%
}
%>

				</td>
				<td style="border-style: none;"><a onclick="return confirm('<%=encprops.getProperty("deleteBio") %>');" href="../TissueSampleRemoveBiologicalMeasurement?encounter=<%=enc.getCatalogNumber()%>&sampleID=<%=thisSample.getSampleID()%>&analysisID=<%=mito.getAnalysisID() %>"><img width="20px" height="20px" style="border-style: none;" src="../images/cancel.gif" /></a></td>
			</tr>
			<%
			}
		}
		%>
		</table>
		<p><span class="caption"><a id="addHaplotype<%=thisSample.getSampleID() %>" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit_add.png" /></a> <a id="addHaplotype<%=thisSample.getSampleID() %>" class="launchPopup"><%=encprops.getProperty("addHaplotype") %></a></span></p>
		<%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>
<!-- start haplotype popup -->
<div id="dialogHaplotype4<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>" title="<%=encprops.getProperty("setHaplotype")%>" style="display:none">
<form id="setHaplotype" action="../TissueSampleSetHaplotype" method="post">
<table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">

  <tr>
    <td>


        <%=encprops.getProperty("analysisID")%> (<%=encprops.getProperty("required")%>)</td><td>
        <%
        MitochondrialDNAAnalysis mtDNA=new MitochondrialDNAAnalysis();
        String analysisIDString="";
        //if((request.getParameter("function")!=null)&&(request.getParameter("function").equals("2"))&&(request.getParameter("edit")!=null) && (request.getParameter("edit").equals("haplotype")) && (request.getParameter("analysisID")!=null)&&(myShepherd.isGeneticAnalysis(request.getParameter("sampleID"),request.getParameter("number"),request.getParameter("analysisID"),"MitochondrialDNA"))){
      	//    analysisIDString=request.getParameter("analysisID");
      	//	mtDNA=myShepherd.getMitochondrialDNAAnalysis(request.getParameter("sampleID"), enc.getCatalogNumber(),analysisIDString);
        //}
        %>
        <input name="analysisID" type="text" size="20" maxlength="100" value="<%=analysisIDString %>" /></td>
   </tr>
   <tr>
        <%
        String haplotypeString="";
        try{
        	if(mtDNA.getHaplotype()!=null){haplotypeString=mtDNA.getHaplotype();}
        }
        catch(NullPointerException npe34){}
        %>
        <td><%=encprops.getProperty("haplotype")%> (<%=encprops.getProperty("required")%>)</td><td>
        <input name="haplotype" type="text" size="20" maxlength="100" value="<%=haplotypeString %>" />
 		</td></tr>

 		 <tr>
 		 <%
        String processingLabTaskID="";
        if(mtDNA.getProcessingLabTaskID()!=null){processingLabTaskID=mtDNA.getProcessingLabTaskID();}
        %>
        <td><%=encprops.getProperty("processingLabTaskID")%></td><td>
        <input name="processingLabTaskID" type="text" size="20" maxlength="100" value="<%=processingLabTaskID %>" />
 		</td></tr>

 		<tr><td>
  		 <%
        String processingLabName="";
        if(mtDNA.getProcessingLabName()!=null){processingLabName=mtDNA.getProcessingLabName();}
        %>
        <%=encprops.getProperty("processingLabName")%></td><td>
        <input name="processingLabName type="text" size="20" maxlength="100" value="<%=processingLabName %>" />
 		</td></tr>

 		<tr><td>
   		 <%
        String processingLabContactName="";
        if(mtDNA.getProcessingLabContactName()!=null){processingLabContactName=mtDNA.getProcessingLabContactName();}
        %>
        <%=encprops.getProperty("processingLabContactName")%></td><td>
        <input name="processingLabContactName type="text" size="20" maxlength="100" value="<%=processingLabContactName %>" />
 		</td></tr>

 		<tr><td>
   		<%
        String processingLabContactDetails="";
        if(mtDNA.getProcessingLabContactDetails()!=null){processingLabContactDetails=mtDNA.getProcessingLabContactDetails();}
        %>
        <%=encprops.getProperty("processingLabContactDetails")%></td><td>
        <input name="processingLabContactDetails type="text" size="20" maxlength="100" value="<%=processingLabContactDetails %>" />
 		</td></tr>
 		<tr><td colspan="2">
 		  <input name="sampleID" type="hidden" value="<%=thisSample.getSampleID()%>" />
          <input name="number" type="hidden" value="<%=num%>" />
          <input name="action" type="hidden" value="setHaplotype" />
          <input name="EditTissueSample" type="submit" id="EditTissueSample" value="<%=encprops.getProperty("set")%>" />

    </td>
  </tr>
</table>
	</form>

</div>

<script>
var dlgHaplotypeAdd<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %> = $("#dialogHaplotype4<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#addHaplotype<%=thisSample.getSampleID() %>").click(function() {
  dlgHaplotypeAdd<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>.dialog("open");
  //$("#setHaplotype").find("input[type=text], textarea").val("");

});
</script>
<!-- end haplotype popup -->
<%
}
%>


		<p><span class="caption"><a id="msmarkersAdd<%=thisSample.getSampleID()%>" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit_add.png" /></a> <a id="msmarkersAdd<%=thisSample.getSampleID()%>" class="launchPopup"><%=encprops.getProperty("addMsMarkers") %></a></span></p>
<%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>
<!-- start sat tag metadata popup -->
<div id="dialogMSMarkersAdd<%=thisSample.getSampleID().replaceAll("[-+.^:,]","")%>" title="<%=encprops.getProperty("setMsMarkers")%>" style="display:none">

<form id="setMsMarkers" action="../TissueSampleSetMicrosatelliteMarkers" method="post">

<table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">
  <tr>
    <td align="left" valign="top">

        <%=encprops.getProperty("analysisID")%> (<%=encprops.getProperty("required")%>)</td><td>
        <%
        MicrosatelliteMarkersAnalysis msDNA=new MicrosatelliteMarkersAnalysis();
        String analysisIDString="";
        %>
        <input name="analysisID" type="text" size="20" maxlength="100" value="<%=analysisIDString %>" /></td></tr>

		<tr><td>
 		 <%
        String processingLabTaskID="";
        if(msDNA.getProcessingLabTaskID()!=null){processingLabTaskID=msDNA.getProcessingLabTaskID();}
        %>
        <%=encprops.getProperty("processingLabTaskID")%><br />
        </td><td><input name="processingLabTaskID" type="text" size="20" maxlength="100" value="<%=processingLabTaskID %>" />
 		</td></tr>

 		<tr><td>
  		 <%
        String processingLabName="";
        if(msDNA.getProcessingLabName()!=null){processingLabName=msDNA.getProcessingLabName();}
        %>
        <%=encprops.getProperty("processingLabName")%><br />
        </td><td><input name="processingLabName" type="text" size="20" maxlength="100" value="<%=processingLabName %>" />
 		</td></tr>

 		<tr><td>
   		 <%
        String processingLabContactName="";
        if(msDNA.getProcessingLabContactName()!=null){processingLabContactName=msDNA.getProcessingLabContactName();}
        %>
        <%=encprops.getProperty("processingLabContactName")%><br />
        </td><td><input name="processingLabContactName" type="text" size="20" maxlength="100" value="<%=processingLabContactName %>" />
 		</td></tr>

 		<tr><td>
   		 <%
        String processingLabContactDetails="";
        if(msDNA.getProcessingLabContactDetails()!=null){processingLabContactDetails=msDNA.getProcessingLabContactDetails();}
        %>
        <%=encprops.getProperty("processingLabContactDetails")%><br />
        </td><td><input name="processingLabContactDetails" type="text" size="20" maxlength="100" value="<%=processingLabContactDetails %>" />
 		</td></tr>
 		<tr><td>
 		<%
 		//begin setting up the loci and alleles
 	      int numPloids=2; //most covered species will be diploids
 	      try{
 	        numPloids=(new Integer(CommonConfiguration.getProperty("numPloids",context))).intValue();
 	      }
 	      catch(Exception e){System.out.println("numPloids configuration value did not resolve to an integer.");e.printStackTrace();}

 	      int numLoci=10;
 	      try{
 	 	  	numLoci=(new Integer(CommonConfiguration.getProperty("numLoci",context))).intValue();
 	 	  }
 	 	  catch(Exception e){System.out.println("numLoci configuration value did not resolve to an integer.");e.printStackTrace();}

 		  for(int locus=0;locus<numLoci;locus++){
 			 String locusNameValue="";
 			 if((msDNA.getLoci()!=null)&&(locus<msDNA.getLoci().size())){locusNameValue=msDNA.getLoci().get(locus).getName();}
 		  %>
			<br /><%=encprops.getProperty("locus") %>: <input name="locusName<%=locus %>" type="text" size="10" value="<%=locusNameValue %>" /><br />
 				<%
 				for(int ploid=0;ploid<numPloids;ploid++){
 					Integer ploidValue=0;
 					if((msDNA.getLoci()!=null)&&(locus<msDNA.getLoci().size())&&(msDNA.getLoci().get(locus).getAllele(ploid)!=null)){ploidValue=msDNA.getLoci().get(locus).getAllele(ploid);}

 				%>
 				<%=encprops.getProperty("allele") %>: <input name="allele<%=locus %><%=ploid %>" type="text" size="10" value="<%=ploidValue %>" /><br />


 				<%
 				}
 				%>

		  <%
 		  }  //end for loci loop
		  %>

		  <tr><td colspan="2">
 		  <input name="sampleID" type="hidden" value="<%=thisSample.getSampleID()%>" />
          <input name="number" type="hidden" value="<%=num%>" />

          <input name="EditTissueSample" type="submit" id="EditTissueSample" value="<%=encprops.getProperty("set")%>" />
    </td></tr>
    </td>
  </tr>
</table>
	  </form>
</div>

<script>
var dlgMSMarkersAdd<%=thisSample.getSampleID().replaceAll("[-+.^:,]","")%> = $("#dialogMSMarkersAdd<%=thisSample.getSampleID().replaceAll("[-+.^:,]","")%>").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#msmarkersAdd<%=thisSample.getSampleID()%>").click(function() {
  dlgMSMarkersAdd<%=thisSample.getSampleID().replaceAll("[-+.^:,]","")%>.dialog("open");
  //$("#setMsMarkers").find("input[type=text], textarea").val("");
});
</script>
<!-- end ms markers popup -->
<%
}
%>



<p><span class="caption"><a id="addSex<%=thisSample.getSampleID() %>" class="launchPopup"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit_add.png" /></a> <a id="addSex<%=thisSample.getSampleID() %>" class="launchPopup"><%=encprops.getProperty("addGeneticSex") %></a></span></p>

<%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>
<!-- start genetic sex popup -->
<div id="dialogSex4<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>" title="<%=encprops.getProperty("setSexAnalysis")%>" style="display:none">

<form name="setSexAnalysis" action="../TissueSampleSetSexAnalysis" method="post">

<table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">
<tr>
  <td>

      <%=encprops.getProperty("analysisID")%> (<%=encprops.getProperty("required")%>)<br />
      <%
      SexAnalysis mtDNA=new SexAnalysis();
      String analysisIDString="";
      %>
      </td><td><input name="analysisID" type="text" size="20" maxlength="100" value="<%=analysisIDString %>" /><br />
      </td></tr>
      <tr><td>
      <%
      String haplotypeString="";
      try{
      	if(mtDNA.getSex()!=null){haplotypeString=mtDNA.getSex();}
      }
      catch(NullPointerException npe34){}
      %>
      <%=encprops.getProperty("geneticSex")%> (<%=encprops.getProperty("required")%>)<br />
      </td><td><input name="sex" type="text" size="20" maxlength="100" value="<%=haplotypeString %>" />
		</td></tr>

		<tr><td>
		 <%
      String processingLabTaskID="";
      if(mtDNA.getProcessingLabTaskID()!=null){processingLabTaskID=mtDNA.getProcessingLabTaskID();}
      %>
      <%=encprops.getProperty("processingLabTaskID")%><br />
      </td><td><input name="processingLabTaskID" type="text" size="20" maxlength="100" value="<%=processingLabTaskID %>" />
	</td></tr>

		<tr><td>
		 <%
      String processingLabName="";
      if(mtDNA.getProcessingLabName()!=null){processingLabName=mtDNA.getProcessingLabName();}
      %>
      <%=encprops.getProperty("processingLabName")%><br />
      </td><td><input name="processingLabName type="text" size="20" maxlength="100" value="<%=processingLabName %>" />
</td></tr>

		<tr><td>
 		 <%
      String processingLabContactName="";
      if(mtDNA.getProcessingLabContactName()!=null){processingLabContactName=mtDNA.getProcessingLabContactName();}
      %>
      <%=encprops.getProperty("processingLabContactName")%><br />
      </td><td><input name="processingLabContactName type="text" size="20" maxlength="100" value="<%=processingLabContactName %>" />
</td></tr>

		<tr><td>
 		 <%
      String processingLabContactDetails="";
      if(mtDNA.getProcessingLabContactDetails()!=null){processingLabContactDetails=mtDNA.getProcessingLabContactDetails();}
      %>
      <%=encprops.getProperty("processingLabContactDetails")%><br />
      </td><td><input name="processingLabContactDetails type="text" size="20" maxlength="100" value="<%=processingLabContactDetails %>" />
</td></tr>

		<tr><td>
		  <input name="sampleID" type="hidden" value="<%=thisSample.getSampleID()%>" />
        <input name="number" type="hidden" value="<%=num%>" />
        <input name="action" type="hidden" value="setSexAnalysis" />
        <input name="EditTissueSampleSexAnalysis" type="submit" id="EditTissueSampleSexAnalysis" value="<%=encprops.getProperty("set")%>" />

  </td>
</tr>
</table>
  </form>

</div>

<script>
var dlgSexAdd<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %> = $("#dialogSex4<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#addSex<%=thisSample.getSampleID() %>").click(function() {
  dlgSexAdd<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>.dialog("open");

});
</script>
<!-- end genetic sex popup -->
<%
}
%>


		<p><span class="caption"><a class="launchPopup" id="addBioMeasure<%=thisSample.getSampleID() %>"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit_add.png" /></a> <a class="launchPopup" id="addBioMeasure<%=thisSample.getSampleID() %>"><%=encprops.getProperty("addBiologicalMeasurement") %></a></span></p>

		<%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>
<!-- start genetic sex popup -->
<div id="dialogBiomeasure4<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>" title="<%=encprops.getProperty("setBiologicalMeasurement")%>" style="display:none">
  <form name="setBiologicalMeasurement" action="../TissueSampleSetMeasurement" method="post">

<table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">


<tr>
<td>

    <%=encprops.getProperty("analysisID")%> (<%=encprops.getProperty("required")%>)<br />
    <%
    BiologicalMeasurement mtDNA=new BiologicalMeasurement();
    String analysisIDString="";

    %>
    </td><td><input name="analysisID" type="text" size="20" maxlength="100" value="<%=analysisIDString %>" /><br />
    </td></tr>

    <tr><td>
    <%
    String type="";
    if(mtDNA.getMeasurementType()!=null){type=mtDNA.getMeasurementType();}
    %>
    <%=encprops.getProperty("type")%> (<%=encprops.getProperty("required")%>)
    </td><td>


     		<%
     		ArrayList<String> values=CommonConfiguration.getSequentialPropertyValues("biologicalMeasurementType",context);
 			int numProps=values.size();
 			ArrayList<String> measurementUnits=CommonConfiguration.getSequentialPropertyValues("biologicalMeasurementUnits",context);
 			int numUnitsProps=measurementUnits.size();

     		if(numProps>0){

     			%>
     			<p><select size="<%=(numProps+1) %>" name="measurementType" id="measurementType">
     			<%

     			for(int y=0;y<numProps;y++){
     				String units="";
     				if(numUnitsProps>y){units="&nbsp;("+measurementUnits.get(y)+")";}
     				String selected="";
     				if((mtDNA.getMeasurementType()!=null)&&(mtDNA.getMeasurementType().equals(values.get(y)))){
     					selected="selected=\"selected\"";
     				}
     			%>
     				<option value="<%=values.get(y) %>" <%=selected %>><%=values.get(y) %><%=units %></option>
     			<%
     			}
     			%>
     			</select>
				</p>
			<%
     		}
     		else{
			%>
    			<input name="measurementType" type="text" size="20" maxlength="100" value="<%=type %>" />
    		<%
     		}
    %>
    </td></tr>

    <tr><td>
    <%
    String thisValue="";
    if(mtDNA.getValue()!=null){thisValue=mtDNA.getValue().toString();}
    %>
    <%=encprops.getProperty("value")%> (<%=encprops.getProperty("required")%>)<br />
    </td><td><input name="value" type="text" size="20" maxlength="100" value="<%=thisValue %>"></input>
    </td></tr>

    <tr><td>
	<%
    String thisSamplingProtocol="";
    if(mtDNA.getSamplingProtocol()!=null){thisSamplingProtocol=mtDNA.getSamplingProtocol();}
    %>
    <%=encprops.getProperty("samplingProtocol")%>
    </td><td>

     		<%
     		ArrayList<String> protovalues=CommonConfiguration.getSequentialPropertyValues("biologicalMeasurementSamplingProtocols",context);
 			int protonumProps=protovalues.size();

     		if(protonumProps>0){

     			%>
     			<p><select size="<%=(protonumProps+1) %>" name="samplingProtocol" id="samplingProtocol">
     			<%

     			for(int y=0;y<protonumProps;y++){
     				String selected="";
     				if((mtDNA.getSamplingProtocol()!=null)&&(mtDNA.getSamplingProtocol().equals(protovalues.get(y)))){
     					selected="selected=\"selected\"";
     				}
     			%>
     				<option value="<%=protovalues.get(y) %>" <%=selected %>><%=protovalues.get(y) %></option>
     			<%
     			}
     			%>
     			</select>
				</p>
			<%
     		}
     		else{
			%>
    			<input name="samplingProtocol" type="text" size="20" maxlength="100" value="<%=type %>" />
    		<%
     		}
			%>
			</td></tr>

    <tr><td>
    <%
    String processingLabTaskID="";
    if(mtDNA.getProcessingLabTaskID()!=null){processingLabTaskID=mtDNA.getProcessingLabTaskID();}
    %>
    <%=encprops.getProperty("processingLabTaskID")%><br />
    </td><td><input name="processingLabTaskID" type="text" size="20" maxlength="100" value="<%=processingLabTaskID %>" />
</td></tr>

    <tr><td>
		 <%
    String processingLabName="";
    if(mtDNA.getProcessingLabName()!=null){processingLabName=mtDNA.getProcessingLabName();}
    %>
    <%=encprops.getProperty("processingLabName")%><br />
    </td><td><input name="processingLabName" type="text" size="20" maxlength="100" value="<%=processingLabName %>" />

</td></tr>

    <tr><td>
		 <%
    String processingLabContactName="";
    if(mtDNA.getProcessingLabContactName()!=null){processingLabContactName=mtDNA.getProcessingLabContactName();}
    %>
    <%=encprops.getProperty("processingLabContactName")%><br />
    </td><td><input name="processingLabContactName" type="text" size="20" maxlength="100" value="<%=processingLabContactName %>" />
</td></tr>

    <tr><td>
		 <%
    String processingLabContactDetails="";
    if(mtDNA.getProcessingLabContactDetails()!=null){processingLabContactDetails=mtDNA.getProcessingLabContactDetails();}
    %>
    <%=encprops.getProperty("processingLabContactDetails")%><br />
    </td><td><input name="processingLabContactDetails" type="text" size="20" maxlength="100" value="<%=processingLabContactDetails %>" />
</td></tr>

    <tr><td>
		  <input name="sampleID" type="hidden" value="<%=thisSample.getSampleID()%>" />
      <input name="encounter" type="hidden" value="<%=num%>" />
      <input name="action" type="hidden" value="setBiologicalMeasurement" />
      <input name="EditTissueSampleBiomeasurementAnalysis" type="submit" id="EditTissueSampleBioMeasurementAnalysis" value="<%=encprops.getProperty("set")%>" />

</td>
</tr>
</table>
	 </form>
</div>

<script>
var dlgAddBiomeasure<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %> = $("#dialogBiomeasure4<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#addBioMeasure<%=thisSample.getSampleID() %>").click(function() {
  dlgAddBiomeasure<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>.dialog("open");

});
</script>
<!-- end biomeasure popup -->
<%
}
%>

	</td>


	<td><a id="sample" href="encounter.jsp?number=<%=enc.getCatalogNumber() %>&sampleID=<%=thisSample.getSampleID()%>&edit=tissueSample&function=1"><img width="24px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a></td><td><a onclick="return confirm('<%=encprops.getProperty("deleteTissue") %>');" href="../EncounterRemoveTissueSample?encounter=<%=enc.getCatalogNumber()%>&sampleID=<%=thisSample.getSampleID()%>"><img style="border-style: none;" src="../images/cancel.gif" /></a></td></tr>
	<%
}
%>
</table>
</p>


<%
}
else {
%>
	<p class="para"><%=encprops.getProperty("noTissueSamples") %></p>
<%
}

}

//now iterate through the jspImport# declarations in encounter.properties and import those files locally
int currentImportNum=0;
while(encprops.getProperty(("jspImport"+currentImportNum))!=null){
	  String importName=encprops.getProperty(("jspImport"+currentImportNum));
	//let's set up references to our file system components

%>
	<hr />
		<jsp:include page="<%=importName %>" flush="true">
			<jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
			<jsp:param name="encounterNumber" value="<%=num%>" />
    		<jsp:param name="isOwner" value="<%=isOwner %>" />
		</jsp:include>

    <%

 currentImportNum++;
} //end while for jspImports


%>

</p>
</td>
</tr>

</table>


<%

kwQuery.closeAll();
myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();
kwQuery=null;
myShepherd=null;

}
catch(Exception e){
	e.printStackTrace();
	%>
	<p>Hit an error.<br /> <%=e.toString()%></p>


<%
}

	}  //end if this is an encounter
    else {
  		myShepherd.rollbackDBTransaction();
  		myShepherd.closeDBTransaction();
		%>
		<p class="para">There is no encounter #<%=num%> in the database. Please double-check the encounter number and try again.</p>

<form action="encounter.jsp" method="post" name="encounter"><strong>Go
  to encounter: </strong> <input name="number" type="text" value="<%=num%>" size="20"> <input name="Go" type="submit" value="Submit" /></form>


<p><font color="#990000"><a href="../individualSearchResults.jsp">View all individuals</a></font></p>


<%
}
%>


</div>

<!--db: These are the necessary tools for photoswipe.-->
<%
String urlLoc = "http://" + CommonConfiguration.getURLLocation(request);
String pswipedir = urlLoc+"/photoswipe";
%>
<link rel='stylesheet prefetch' href='<%=pswipedir %>/photoswipe.css'>
<link rel='stylesheet prefetch' href='<%=pswipedir %>/default-skin/default-skin.css'>
<jsp:include page="../photoswipe/photoswipeTemplate.jsp" flush="true"/>
<script src='<%=pswipedir%>/photoswipe.js'></script>
<script src='<%=pswipedir%>/photoswipe-ui-default.js'></script>


<jsp:include page="../footer.jsp" flush="true"/>
