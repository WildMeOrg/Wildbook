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
         org.ecocean.media.MediaAsset,
         org.ecocean.servlet.ServletUtilities,
         org.ecocean.Util,org.ecocean.Measurement,
         org.ecocean.Util.*, org.ecocean.genetics.*,
         org.ecocean.tag.*, java.awt.Dimension,
	 org.json.JSONObject,
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


	public boolean checkAccessKey(HttpServletRequest request, Encounter enc) {
		if ((request == null) || (enc == null)) return false;
		JSONObject jobj = new JSONObject();
		String accessKey = request.getParameter("accessKey");
		if (accessKey == null) return false;
		for (MediaAsset ma : enc.getMedia()) {
			JSONObject p = ma.getParameters();
			if (p == null) return false;
			if (!accessKey.equals(p.optString("accessKey", null))) return false;
		}
		return true;
	}
%>
<link type='text/css' rel='stylesheet' href='../javascript/timepicker/jquery-ui-timepicker-addon.css' />

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
  myShepherd.setAction("encounter.jsp1");
  //Extent allKeywords = myShepherd.getPM().getExtent(Keyword.class, true);
  //Query kwQuery = myShepherd.getPM().newQuery(allKeywords);
//System.out.println("???? query=" + kwQuery);
  boolean proceed = true;
  boolean haveRendered = false;

  pageContext.setAttribute("set", encprops.getProperty("set"));
  
  String mapKey = CommonConfiguration.getGoogleMapsKey(context);
%>



<jsp:include page="../header.jsp" flush="true"/>

<script src="//maps.google.com/maps/api/js?key=<%=mapKey%>&language=<%=langCode%>"></script>


  <style type="text/css">

.annot-link {
    display: none;
    position: absolute;
    text-decoration: none !important;
    right: 5px;
    top: 5px;
    font-size: 1.3em;
}
.image-enhancer-feature:hover .annot-link {
    display: block;
}

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



  <link rel="stylesheet" type="text/css" href="../css/encounterStyles.css">

  <!--
    2) Optionally override the settings defined at the top
    of the highslide.js file. The parameter hs.graphicsDir is important!
  -->


<script type="text/javascript">

  var map;
  var marker;
  var center = new google.maps.LatLng(0, 0);


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
function setIndivAutocomplete(el) {
    if (!el || !el.length) return;
    var args = {
        resMap: function(data) {
            var res = $.map(data, function(item) {
                if (item.type != 'individual') return null;
                var label = item.label;
                if (item.species) label += '   ( ' + item.species + ' )';
                return { label: label, type: item.type, value: item.value };
            });
            return res;
        }
    };
    wildbook.makeAutocomplete(el[0], args);
}
</script>


  <script>
            function initialize() {
	            //alert("Initializing map!");
	              //var mapZoom = 1;
	              var mapZoom = 1;

	              //var center = new google.maps.LatLng(10.8, 160.8);
	              var center = new google.maps.LatLng(0, 0);


	              map = new google.maps.Map(document.getElementById('map_canvas'), {
	                zoom: mapZoom,
	                center: center,
	                mapTypeId: google.maps.MapTypeId.TERRAIN,
	                zoomControl: true,
	                scaleControl: false,
	                scrollwheel: false,
	                disableDoubleClickZoom: true,
	        	});

	        	if(marker!=null){
					marker.setMap(map);
					//map.setCenter(marker.position);

	 			//alert("Setting center!");
				}

	        	google.maps.event.addListener(map, 'click', function(event) {
						//alert("Clicked map!");
					    placeMarker(event.latLng);
				  });


		//adding the fullscreen control to exit fullscreen
	    	//  var fsControlDiv = document.createElement('DIV');
	    	//  var fsControl = new FSControl(fsControlDiv, map);
	    	//  fsControlDiv.index = 1;
	    	//  map.controls[google.maps.ControlPosition.TOP_RIGHT].push(fsControlDiv);



        }




var encounterNumber = '<%=num%>';

  </script>

<style type="text/css">
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





  <script src="../javascript/timepicker/jquery-ui-timepicker-addon.js"></script>

<script src="../javascript/qualityChecks.js"></script>

<script src="../javascript/imageTools.js"></script>




<div class="container maincontent">

<div class="row" id="mainHeader">
  <div class="col-sm-12">

			<%
  			myShepherd.beginDBTransaction();

  			if (myShepherd.isEncounter(num)) {
    			try {

      			Encounter enc = myShepherd.getEncounter(num);
            String encNum = enc.getCatalogNumber();
						boolean visible = enc.canUserAccess(request);
						if (!visible) visible = checkAccessKey(request, enc);
						if (!visible) {
							String blocker = "";
							List<Collaboration> collabs = Collaboration.collaborationsForCurrentUser(request);
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
        maxDate: '+1d',
        controlType: 'select',
        alwaysSetTime: false,
        showSecond:false,
        showMillisec:false,
        showMicrosec:false,
        showTimezone:false,

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

				String individuo="<a id=\"topid\">"+encprops.getProperty("unassigned")+"</a>";
				if(enc.getIndividualID()!=null){
					individuo=encprops.getProperty("of")+"&nbsp;<a id=\"topid\" href=\"../individuals.jsp?number="+enc.getIndividualID()+"\">"+enc.getIndividualID()+"</a>";
				}
    			%>
               	<h1 class="<%=classColor%>" id="headerText">
                	<%=encprops.getProperty("title") %> <%=individuo %></a> <%=livingStatus %>
                </h1>


<% String dup = enc.getDynamicPropertyValue("duplicateOf");  if (dup != null) { %>
<div style="display: inline-block; padding: 1px 5px; background-color: #AAA; color: #833; border-radius: 4px;">This encounter is marked as a <b>duplicate of <a href="encounter.jsp?number=<%=dup%>"><%=dup%></a></b>.</div><% } %>

    			<p class="caption"><em><%=encprops.getProperty("description") %></em></p>
 					<table style="border-spacing: 10px;margin-left:-10px;border-collapse: inherit;">
 						<tr valign="middle">
  							<td>
    							<!-- Google PLUS-ONE button -->
								<g:plusone size="medium" annotation="none"></g:plusone>
							</td>
							<td>
								<!--  Twitter TWEET THIS button -->
								<a href="https://twitter.com/share" class="twitter-share-button" data-count="none">Tweet</a>
								<script>!function(d,s,id){var js,fjs=d.getElementsByTagName(s)[0];if(!d.getElementById(id)){js=d.createElement(s);js.id=id;js.src="//platform.twitter.com/widgets.js";fjs.parentNode.insertBefore(js,fjs);}}(document,"script","twitter-wjs");</script>
							</td>
							<td>
								<!-- Facebook SHARE button -->
								<div class="fb-share-button" data-href="//<%=CommonConfiguration.getURLLocation(request) %>/encounters/encounter.jsp?number=<%=request.getParameter("number") %>" data-type="button_count"></div></td>
						</tr>
					</table>
          </div>
        </div>
<!-- end main header row -->


	<!-- main display area -->

				<div class="container">
					<div class="row">


            <div class="col-xs-12 col-sm-6" style="vertical-align: top;padding-left: 10px;">

<%-- START LOCATION --%>
<% if (isOwner && CommonConfiguration.isCatalogEditable(context)) { %>
<h2>
	<img src="../images/2globe_128.gif" width="40px" height="40px" align="absmiddle"/>
  <%=encprops.getProperty("location") %>
  <button class="btn btn-md" type="button" name="button" id="editLocation">Edit</button>
  <button class="btn btn-md" type="button" name="button" id="closeEditLocation" style="display:none;">Close Edit</button>
</h2>



<script type="text/javascript">
$(document).ready(function() {
  var buttons = $("#editLocation, #closeEditLocation").on("click", function(){
    buttons.toggle();
  });
  $("#editLocation").click(function() {
    $(".editFormLocation, .editTextLocation, #AddDepth, #setLocationBtn, #addLocation, #countryFormBtn, #AddElev, #setGPSbutton").show();

    $("#setLocationCheck, #setLocationError, #countryCheck, #countryError, #locationIDcheck, #locationIDerror, #depthCheck, #depthError, #elevationCheck, #elevationError, #latCheck, #longCheck").hide();

    $("#depthDiv, #elevationDiv").removeClass("has-error");

    $("#depthDiv, #elevationDiv").removeClass("has-success");

  });

  $("#closeEditLocation").click(function() {
    $(".editFormLocation, .editTextLocation, .resultMessageDiv").hide();
  });
});
</script>


<% }
else {
 %>
 <h2>
 	<img src="../images/2globe_128.gif" width="40px" height="40px" align="absmiddle"/> <%=encprops.getProperty("location") %>
 </h2>

 <%}%>



<%
if(enc.getLocation()!=null){
%>

<em><%=encprops.getProperty("locationDescription")%> <span id="displayLocation"><%=enc.getLocation()%></span></em>
<%
}
%>

<br>

<a href="<%=CommonConfiguration.getWikiLocation(context)%>locationID" target="_blank"><img
    src="../images/information_icon_svg.gif" alt="Help" border="0" align="absmiddle"></a>
<em><%=encprops.getProperty("locationID") %></em><span> <span id="displayLocationID"><%=enc.getLocationCode()%></span></span>

<br>


  <a href="<%=CommonConfiguration.getWikiLocation(context)%>country" target="_blank"><img
    src="../images/information_icon_svg.gif" alt="Help" border="0" align="absmiddle"></a>
  <em><%=encprops.getProperty("country") %></em>
  <%
  if(enc.getCountry()!=null){
  %>
  <span>: <span id="displayCountry"><%=enc.getCountry()%></span></span>
  <%
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
  <span id="displayDepth"><%=enc.getDepth()%></span> <%=encprops.getProperty("meters")%> <%
  } else {
  %> <%=encprops.getProperty("unknown") %>
  <%
    }

%>
</em>
<%
  }
%>
<!-- End Display maximumDepthInMeters -->

<!-- start location  -->
<script type="text/javascript">
  $(document).ready(function() {
    $("#addLocation").click(function(event) {
      event.preventDefault();

      $("#addLocation").hide();

      var number = $("#setLocationNumber").val();
      var encounter = $("#setLocationEncounter").val();
      var location = $("#locationInput").val();

      $.post("../EncounterSetLocation", {"number": number, "encounter": encounter, "location": location},
      function() {
        $("#setLocationErrorDiv").hide();
        $("#setLocationCheck").show();
        $("#displayLocation").html(location);
      })
      .fail(function(response) {
        $("#setLocationError, #setLocationErrorDiv").show();
        $("#setLocationErrorDiv").html(response.responseText);
      });
    });

    $("#locationInput").click(function() {
      $("#setLocationError, #setLocationCheck, #setLocationErrorDiv").hide()
      $("#addLocation").show();
    });
  });
</script>
<div>
  <div class="highlight resultMessageDiv" id="setLocationErrorDiv"></div>

  <p class="editTextLocation"><strong><%=encprops.getProperty("setLocation")%></strong></p>
  <form name="setLocation" class="editFormLocation">
    <input name="number" type="hidden" value="<%=num%>" id="setLocationNumber"/>
    <input name="action" type="hidden" value="setLocation" />
    <input name="encounter" type="hidden" value="<%=num%>" id="setLocationEncounter">

  <%
  String thisLocation="";
  if(enc.getLocation()!=null){
    thisLocation=enc.getLocation().trim();
  }
  %>
  <div class="form-group row">
    <div class="col-sm-5">
      <textarea name="location" class="form-control" id="locationInput"><%=thisLocation%></textarea>
    </div>
    <div class="col-sm-3">
      <input name="Add" type="submit" id="addLocation" value="<%=encprops.getProperty("setLocation")%>" class="btn btn-sm"/>
      <span class="form-control-feedback" id="setLocationCheck">&check;</span>
      <span class="form-control-feedback" id="setLocationError">X</span>
    </div>
  </div>
  </form>
</div>
<!-- end location -->


<!-- start country -->
<script type="text/javascript">
  $(document).ready(function() {
    $("#countryFormBtn").click(function(event) {
      event.preventDefault();

      $("#countryFormBtn").hide();

      var encounter = $("#countryEncounter").val();
      var country = $("#selectCountry").val();

      $.post("../EncounterSetCountry", {"encounter": encounter, "country": country},
      function() {
        $("#countryErrorDiv").hide();
        $("#countryCheck").show();
        $("#displayCountry").html(country);
      })
      .fail(function(response) {
        $("#countryError, #countryErrorDiv").show();
        $("#countryErrorDiv").html(response.responseText);
      });
    });

    $("#selectCountry").click(function() {
      $("#countryError, #countryCheck, #countryErrorDiv").hide()
      $("#countryFormBtn").show();
    });
  });
</script>

<div>
  <div class="highlight resultMessageDiv" id="countryErrorDiv"></div>

  <p class="editTextLocation"><strong><%=encprops.getProperty("resetCountry")%></strong></p>
  <span class="editTextLocation"><font size="-1"><%=encprops.getProperty("leaveBlank")%></font></span>

  <form name="countryForm" class="editFormLocation">
    <input name="encounter" type="hidden" value="<%=num%>" id="countryEncounter" />
    <div class="form-group row">
      <div class="col-sm-5">
        <select name="country" id="selectCountry" size="1" class="form-control">
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
      </div>
      <div class="col-sm-3">
        <input name="<%=encprops.getProperty("set")%>" type="submit" id="countryFormBtn" value="<%=encprops.getProperty("set")%>" class="btn btn-sm editFormBtn"/>
        <span class="form-control-feedback" id="countryCheck">&check;</span>
        <span class="form-control-feedback" id="countryError">X</span>
      </div>
    </div>
  </form>
</div>
<!-- end country popup-->

<!-- start locationID -->
<script type="text/javascript">
  $(document).ready(function() {
    $("#setLocationBtn").click(function(event) {
      event.preventDefault();

      $("#setLocationBtn").hide();

      var number = $("#locationIDnumber").val();
      var code = $("#selectCode").val();

      $.post("../EncounterSetLocationID", {"number": number, "code": code},
      function() {
        $("#locationIDerrorDiv").hide();
        $("#locationIDcheck").show();
        $("#displayLocationID").html(code);
      })
      .fail(function(response) {
        $("#locationIDerror, #locationIDerrorDiv").show();
        $("#locationIDerrorDiv").html(response.responseText);
      });
    });

    $("#selectCode").click(function() {
      $("#locationIDerror, #locationIDcheck, #locationIDerrorDiv").hide()
      $("#setLocationBtn").show();
    });
  });
</script>

<div>
  <div class="highlight resultMessageDiv" id="locationIDerrorDiv"></div>

  <p class="editTextLocation"><strong><%=encprops.getProperty("setLocationID")%></strong></p>
  <form name="addLocCode" class="editFormLocation">
    <input name="number" type="hidden" value="<%=num%>" id="locationIDnumber"/>
    <input name="action" type="hidden" value="addLocCode" />

        <%
        if(CommonConfiguration.getProperty("locationID0",context)==null){
        %>
        <div class="form-group row">
          <div class="col-sm-5">
            <input name="code" type="text" class="form-control" id="selectCode"/>
          </div>
          <div class="col-sm-3">
            <input name="Set Location ID" type="submit" id="setLocationBtn" value="<%=encprops.getProperty("setLocationID")%>" class="btn btn-sm"/>
          </div>
        </div>
        <%
        }
        else{
          //iterate and find the locationID options
          %>
          <div class="form-group row">
            <div class="col-sm-5">
              <select name="code" id="selectCode" class="form-control" size=="1">
                <option value=""></option>

                <%
                boolean hasMoreLocs=true;
                int codeTaxNum=0;
                while(hasMoreLocs){
                  String currentLoc = "locationID"+codeTaxNum;
                  if(CommonConfiguration.getProperty(currentLoc,context)!=null){
                	  String selected="";
                	  if((enc.getLocationID()!=null)&&(CommonConfiguration.getProperty(currentLoc,context).equals(enc.getLocationID()))){
                		  selected="selected=\"selected\"";
                	  }
                    %>
                    <option <%=selected %> value="<%=CommonConfiguration.getProperty(currentLoc,context)%>"><%=CommonConfiguration.getProperty(currentLoc,context)%></option>
                    <%
                    codeTaxNum++;
                  }
                  else{
                    hasMoreLocs=false;
                  }

                }
                %>

              </select>
            </div>
            <div class="col-sm-3">
              <input name="Set Location ID" type="submit" id="setLocationBtn" value="<%=encprops.getProperty("setLocationID")%>" class="btn btn-sm"/>
              <span class="form-control-feedback" id="locationIDcheck">&check;</span>
              <span class="form-control-feedback" id="locationIDerror">X</span>
            </div>
          </div>
      <%
        }
        %>

    </form>
</div>
<!-- end locationID -->


<!-- start depth -->
<script type="text/javascript">
  $(document).ready(function() {
    $("#AddDepth").click(function(event) {
      event.preventDefault();

      $("#AddDepth").hide();

      var number = $("#depthNumber").val();
      var depth = $("#depthInput").val();

      $.post("../EncounterSetMaximumDepth", {"number": number, "depth": depth},
      function() {
        $("#depthErrorDiv").hide();
        $("#depthDiv").addClass("has-success");
        $("#depthCheck").show();
        $("#displayDepth").html(depth);
      })
      .fail(function(response) {
        $("#depthDiv").addClass("has-error");
        $("#depthError, #depthErrorDiv").show();
        $("#depthErrorDiv").html(response.responseText);
      });
    });

    $("#depthInput").click(function() {
      $("#depthError, #depthCheck, #depthErrorDiv").hide()
      $("#depthDiv").removeClass("has-success");
      $("#depthDiv").removeClass("has-error");
      $("#AddDepth").show();
    });
  });
</script>

<div>
  <div class="highlight resultMessageDiv" id="depthErrorDiv"></div>

  <p class="editTextLocation"><strong><%=encprops.getProperty("setDepth")%></strong></p>
  <form name="setencdepth" class="editFormLocation">
    <input name="lengthUnits" type="hidden" id="lengthUnits" value="Meters" />
    <input name="number" type="hidden" value="<%=num%>" id="depthNumber" />
    <input name="action" type="hidden" value="setEncounterDepth" />
    <div class="form-group row">
      <div class="col-sm-5" id="depthDiv">
        <input name="depth" type="text" id="depthInput" class="form-control"/><span><%=encprops.getProperty("meters")%></span>
        <span class="form-control-feedback" id="depthCheck">&check;</span>
        <span class="form-control-feedback" id="depthError">X</span>
      </div>
      <div class="col-sm-3">
        <input name="AddDepth" type="submit" id="AddDepth" value="<%=encprops.getProperty("setDepth")%>" class="btn btn-sm editFormBtn"/>
      </div>
    </div>
  </form>
</div>


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
  <span id="displayElevation"><%=enc.getMaximumElevationInMeters()%></span><%=encprops.getProperty("meters")%> <%
  } else {
  %>
  <span id="displayElevation"><%=encprops.getProperty("unknown") %></span>
  <%
    }

  %>

  <%
  %>


<%
%>
<!-- start elevation -->
<script type="text/javascript">
  $(document).ready(function() {
    $("#AddElev").click(function(event) {
      event.preventDefault();

      $("#AddElev").hide();

      var number = $("#elevationNumber").val();
      var elevation = $("#elevation").val();

      $.post("../EncounterSetMaximumElevation", {"number": number, "elevation": elevation},
      function() {
        $("#elevationErrorDiv").hide();
        $("#elevationDiv").addClass("has-success");
        $("#elevationCheck").show();
        $("#displayElevation").html(elevation);
      })
      .fail(function(response) {
        $("#elevationDiv").addClass("has-error");
        $("#elevationError, #elevationErrorDiv").show();
        $("#elevationErrorDiv").html(response.responseText);
      });
    });

    $("#elevationInput").click(function() {
      $("#elevationError, #elevationCheck, #elevationErrorDiv").hide()
      $("#elevationDiv").removeClass("has-success");
      $("#elevationDiv").removeClass("has-error");
      $("#AddElev").show();
    });
  });
</script>
<div>
  <div class="highlight resultMessageDiv" id="elevationErrorDiv"></div>

  <p class="editTextLocation"><strong><%=encprops.getProperty("setElevation")%></strong></p>
  <form name="setencelev" class="editFormLocation">
    <input name="number" type="hidden" value="<%=num%>" id="elevationNumber" />
    <input name="action" type="hidden" value="setEncounterElevation" />
    <input name="lengthUnits" type="hidden" id="lengthUnits" value="Meters" />
    <div class="form-group row">
      <div class="col-sm-5" id="elevationDiv">
        <input name="elevation" type="text" id="elevation" class="form-control"/><span><%=encprops.getProperty("meters")%></span>
        <span class="form-control-feedback" id="elevationCheck">&check;</span>
        <span class="form-control-feedback" id="elevationError">X</span>
      </div>
    </div>
    <input name="AddElev" type="submit" id="AddElev" value="<%=encprops.getProperty("setElevation")%>" class="btn btn-sm editFormBtn"/>
  </form>
</div>
<!-- end elevation  -->
<%
%>

<%
  }
%>
<!-- End Display maximumElevationInMeters -->

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

    	//}



      google.maps.event.addDomListener(window, 'load', initialize);
      
      function emptyMarkers() {

    	    // Loop through markers and set map to null for each
    	    for (var i=0; i<markers.length; i++) {

    	        markers[i].setMap(null);
    	    }

    	    // Reset the markers array
    	    markers = [];

    	}
      
      
    </script>

 	<%
 	if((request.getUserPrincipal()!=null)){
 	%>
 		<p><%=encprops.getProperty("map_note") %></p>
 		<div id="map_canvas" style="width: 510px; height: 350px; overflow: hidden;"></div>
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

      <script type="text/javascript">
        $(document).ready(function() {
        	
          //form submission	
          $("#setGPSbutton").click(function(event) {
            event.preventDefault();

            var number = $("#gpsNumber").val();
            var lat = $("#lat").val();
            var longitude = $("#longitude").val();

            $.post("../EncounterSetGPS", {"number": number, "lat": lat, "longitude": longitude},
            function() {
              $("#latCheck, #longCheck").show();
            })
            .fail(function(response) {
              $("#gpsErrorDiv").show();
              $("#gpsErrorDiv").html(response.responseText);
              $("#latCheck, #longCheck").hide();
            });
          });

          $("#lat, #longitude, #map_canvas").click(function() {
            $("#gpsErrorDiv").hide()
            $("#latCheck, #longCheck").hide();
          });
          
          
          
          //validate GPS values
          $('#lat,#longitude').keyup(function() {
              if( ( $('#lat').val() == "") && ( $('#longitude').val() == "") ) {
                  $("#setGPSbutton").removeAttr("disabled");
                  //alert("here 1!");
                  emptyMarkers();
              }
              else if( $('#lat').val() == "" || $('#longitude').val() == "" ) {
                  $("#setGPSbutton").attr("disabled","disabled");
                  //alert("here 2!");
              }  
              else{
              	//alert("Trying to validate!");
              	var valid=validate_coords($('#lat').val(),$('#longitude').val());
              	if(!valid){
              		$("#setGPSbutton").attr("disabled","disabled");
              		emptyMarkers();
              	}
              	else{
                    $("#setGPSbutton").removeAttr("disabled");
                    emptyMarkers();
                    var newLatLng = new google.maps.LatLng($('#lat').val(), $('#longitude').val());   
                    var newMarker = new google.maps.Marker({
                 	   icon: 'https://chart.googleapis.com/chart?chst=d_map_pin_letter&chld=<%=markerText%>|<%=haploColor%>',
                 	   position:newLatLng,
                 	   map:map
                 	});
                    markers.push(newMarker);
              	}
              }
          });
          
          
        });
        

        
        
      </script>


     	<a name="gps"></a>
        <div>
          <br>
          <div class="highlight resultMessageDiv" id="gpsErrorDiv"></div>
          <form name="resetGPSform" class="editFormLocation">
            <input name="number" type="hidden" value="<%=num%>" id="gpsNumber"/>
            <input name="action" type="hidden" value="resetGPS" id="gpsAction"/>
            <div class="form-group row">
              <div class="col-sm-2">
                <label><%=encprops.getProperty("latitude")%>:</label>
              </div>
              <div class="col-sm-3">
                <input name="lat" type="text" id="lat" class="form-control" value="<%=laty%>" />
                <span class="form-control-feedback" id="latCheck">&check;</span>
              </div>
              <div class="col-sm-2">
                <label><%=encprops.getProperty("longitude")%>:</label>
              </div>
              <div class="col-sm-3">
                <input name="longitude" type="text" id="longitude" class="form-control" value="<%=longy%>" />
                <span class="form-control-feedback" id="longCheck">&check;</span>
              </div>
            </div>
            <div class="form-group row">
              <div class="col-sm-3">
                <input name="setGPSbutton" type="submit" id="setGPSbutton" value="<%=encprops.getProperty("setGPS")%>" class="btn btn-sm"/>

              </div>
            </div>
          </form>
          
          
          

          <br/>
          <span class="editTextLocation"><%=encprops.getProperty("gpsConverter")%></span><a class="editTextLocation" href="http://www.csgnetwork.com/gpscoordconv.html" target="_blank">Click here to find a converter.</a>
        </div>

     	<%
 		}  //end isOwner
     	%>
<br /> <br />
 <!--end adding submit GPS-->
 <!-- END MAP and GPS SETTER -->

<div style="background-color: #E8E8E8;padding-left: 10px;padding-right: 10px;padding-top: 10px;padding-bottom: 10px;">
        <!-- START IDENTITY ATTRIBUTE -->
        <% if (isOwner && CommonConfiguration.isCatalogEditable(context)) { %>
	        <h2><img align="absmiddle" src="../images/wild-me-logo-only-100-100.png" width="40px" height="40px" /> <%=encprops.getProperty("identity") %>
	        <button class="btn btn-md" type="button" name="button" id="editIdentity">Edit</button>
	        <button class="btn btn-md" type="button" name="button" id="closeEditIdentity" style="display:none;">Close Edit</button>
	      </h2>
	
	
	
	        <script type="text/javascript">
	        $(document).ready(function() {
	          var buttons = $("#editIdentity, #closeEditIdentity").on("click", function(){
	            buttons.toggle();
	          });
	          $("#editIdentity").click(function() {
	            $("#matchCheck, #matchError, #individualCheck, #individualError, #matchedByCheck, #matchedByError, #indCreateCheck, #indCreateError, #altIdCheck, #altIdError, #createOccurCheck, #createOccurError, #addOccurCheck, #addOccurError").hide();
	
	            $(".editForm, .editText, #setMB, #Add, #individualRemoveEncounterBtn, #Create, #setAltIDbtn, #createOccur, #addOccurrence, #removeOccurrenceBtn").show();
	
				<%
				if(enc.getIndividualID()!=null){
				%>
					$(".add2shark").hide();               
					$(".removeFromShark").show();                   	                                      
				<%
				}
				else{
				%>
					$(".add2shark").show();               
					$(".removeFromShark").hide(); 
				<%
				}
				%>
	            
	            
	            $("#individualDiv, #createSharkDiv, #altIdErrorDiv, #occurDiv, #addDiv").removeClass("has-error");
	
	            $("#individualDiv, #createSharkDiv, #altIdErrorDiv, #occurDiv, #addDiv").removeClass("has-success");
	          });
	
	          $("#closeEditIdentity").click(function() {
	            $(".editForm, .editText, .resultMessageDiv").hide();
	          });
	        });
	        </script>
	
	
	        <% 
        }
        else {
	         %>
	         <h2><img align="absmiddle" src="../images/wild-me-logo-only-100-100.png" width="40px" height="40px" /> <%=encprops.getProperty("identity") %></h2>
	         <%
	     }
         %>


    					
    						
    							<div>
    							<p class="para">
    								 <%=encprops.getProperty("identified_as") %> 
                     <a href="../individuals.jsp?langCode=<%=langCode%>&number=<%=enc.getIndividualID()%>">
                     <span id="displayIndividualID"><%=ServletUtilities.handleNullString(enc.getIndividualID())%></span></a></p>
    							
                  <p>
                    <img align="absmiddle" src="../images/Crystal_Clear_app_matchedBy.gif">
                      <span><%=encprops.getProperty("matched_by") %>: <span id="displayMatchedBy"><%=enc.getMatchedBy()%></span></span>
                  </p>

    							

          <%-- START MATCHED BY --%>
    			

                    <script type="text/javascript">
                    $(document).ready(function() {
                      $("#matchedBy option[value='Pattern match']").attr('selected','selected');

                      $("#setMB").click(function(event) {
                        event.preventDefault();
                        $("#setMB").hide();

                        var number = $("#setMBnumber").val();
                        var matchedBy = $("#matchedBy").val();

                        $.post("../EncounterSetMatchedBy", {"number": number, "matchedBy": matchedBy},
                        function() {
                          $("#matchErrorDiv").hide();
                          $("#matchCheck").show();
                          $("#displayMatchedBy").html(matchedBy);

                        })
                        .fail(function(response) {
                          $("#matchError, #matchErrorDiv").show();
                          $("#matchErrorDiv").html(response.responseText);
                        });
                      });

                      $("#matchedBy").click(function() {
                        $("#matchError, #matchCheck, #matchErrorDiv").hide();
                        $("#setMB").show();
                      });
                    });
                    </script>

                    <div class="highlight resultMessageDiv" id="matchErrorDiv"></div>
                    <form name="setMBT" class="editForm">
                      <input name="number" type="hidden" value="<%=num%>" id="setMBnumber"/>
                      <div class="form-group row" id="selectMatcher">
                        <div class="col-sm-3">
                          <label><%=encprops.getProperty("matchedBy")%></label>
                        </div>
                        <div class="col-sm-5 col-xs-10">
                          <select name="matchedBy" id="matchedBy" size="1" class="form-control">
                            <option value="Unmatched first encounter"><%=encprops.getProperty("unmatchedFirstEncounter")%></option>
                            <option value="Visual inspection"><%=encprops.getProperty("visualInspection")%></option>
                            <option value="Pattern match" selected><%=encprops.getProperty("patternMatch")%></option>
                          </select>
                        </div>
                        <div class="col-sm-3">
                          <input name="setMB" type="submit" id="setMB" value='<%=encprops.getProperty("set")%>' class="btn btn-sm editFormBtn"/>
                          <span id="matchCheck">&check;</span>
                          <span id="matchError">X</span>
                        </div>
                      </div>
                    </form>
    							</div>
    						
      <%-- END MATCHED BY --%>

      <%-- START MANAGE IDENTITY --%>

     		<div id="dialogIdentity" title="<%=encprops.getProperty("manageIdentity")%>" class="editForm">

 
 					
 

                    <script type="text/javascript">
                    $(document).ready(function() {
                    	
                    	
                      $("#Add").click(function(event) {
                        event.preventDefault();

                        $("#Add").hide();

                        var number = $("#individualAddEncounterNumber").val();
                        var individual = $("#individualAddEncounterInput").val();
                        var matchType = $("#matchType").val();
                        var noemail = $( "input:checkbox:checked" ).val();
                        var action = $("#individualAddEncounterAction").val();

                        $.post("../IndividualAddEncounter", {"number": number, "individual": individual, "matchType": matchType, "noemail": noemail, "action": action},
                        function() {
                          $("#individualErrorDiv").hide();
                          $("#individualDiv").addClass("has-success");
                          $("#individualCheck, #matchedByCheck").show();
                          $("#displayIndividualID").html(individual);
                          $('#displayIndividualID').closest('a').prop('href', '../individuals.jsp?number=' + individual);
                          
                          $('#topid').prop('href', '../individuals.jsp?number=' + individual);
                          $("#topid").html(individual);
          				  $(".add2shark").hide();               
        				  $(".removeFromShark").show();      
                          //add topid update here
                          
                          $("#displayMatchedBy").html(matchType);
                          $("#addSuccessDiv").html("<strong>Success:</strong> Encounter " + number + " was successfully added to " + individual + ".");
                          

                        })
                        .fail(function(response) {
                          $("#individualDiv").addClass("has-error");
                          $("#individualError, #matchedByError, #individualErrorDiv").show();
                          $("#individualErrorDiv").html(response.responseText);
                        });
                      });

                      $("#individualAddEncounterInput, #matchType").click(function() {
                        $("#individualError, #individualCheck, #matchedByCheck, #matchedByError, #individualErrorDiv").hide()
                        $("#individualDiv").removeClass("has-success");
                        $("#individualDiv").removeClass("has-error");
                        $("#Add").show();
                        $("#setRemoveResultDiv").hide();
                      });
                    });
                    </script>

                    <div class="editText">
                      <h3><%=encprops.getProperty("manageIdentity")%></h3>
                         <p><em><small><%=encprops.getProperty("identityMessage") %></small></em></p>
                   
                    </div>



                    <p class="add2shark"><strong><%=encprops.getProperty("add2MarkedIndividual")%></strong></p>

                    <form name="add2shark" class="add2shark">
                      <input name="number" type="hidden" value="<%=num%>" id="individualAddEncounterNumber"/>
                      <input name="action" type="hidden" value="add" id="individualAddEncounterAction"/>
                      <div class="form-group row" id="individualDiv">
                        <div class="col-sm-3">
                          <label><%=encprops.getProperty("individual")%></label>
                        </div>
                        <div class="col-sm-5 col-xs-10">
                          <input name="individual" type="text" class="form-control" id="individualAddEncounterInput"/>
                          
                          <span class="form-control-feedback" id="individualCheck">&check;</span>
                          <span class="form-control-feedback" id="individualError">X</span><br>
                          <%
                          String nextID=getNextIndividualNumber(enc, myShepherd, context);
                          %>
                           <script type="text/javascript">
                          	function populateID() {
                          		$('individualAddEncounterInput').val('<%=nextID %>');
                          	}
	                      </script>
                          <p style="font-size: smaller;"><em>Next suggested new ID: <a onclick="$('#individualAddEncounterInput').val('<%=nextID %>');$('#matchType').val('Unmatched first encounter').change();"><%=nextID  %></a></em></p>

                        </div>
                      </div>
                      <div class="form-group row" id="matchedByDiv">
                        <div class="col-sm-3">
                          <label><%=encprops.getProperty("matchedBy")%>: </label>
                        </div>
                        <div class="col-sm-5 col-xs-10">
                          <select id="matchType" name="matchType" id="matchType" class="form-control" size="1">
                            <option value="Unmatched first encounter"><%=encprops.getProperty("unmatchedFirstEncounter")%></option>
                            <option value="Visual inspection"><%=encprops.getProperty("visualInspection")%></option>
                            <option value="Pattern match" selected><%=encprops.getProperty("patternMatch")%></option>
                          </select>
                          <span class="form-control-feedback" id="matchedByCheck">&check;</span>
                          <span class="form-control-feedback" id="matchedByError">X</span>
                        </div>
                      </div>
                      <div class="form-group row">
                        <div class="col-sm-5 col-xs-10">
                          <label><input name="noemail" type="checkbox" value="noemail" /> <%=encprops.getProperty("suppressEmail")%></label>
                        </div>
                      </div>
                        <input name="Add" type="submit" id="Add" value="<%=encprops.getProperty("add")%>" class="btn btn-sm editFormBtn add2shark"/>
                    </form>
					
					<script type="text/javascript">
	                    $(document).ready(function() {
	                    	
	                    	//set autocomplete on #individualAddEncounterInput above
	                    	setIndivAutocomplete($('#individualAddEncounterInput'));
	                    	
	                    	
	                    	
	                    });
                    </script>


       
             <script type="text/javascript">
                    $(document).ready(function() {
                    

                      $("#individualRemoveEncounterBtn").click(function(event) {
                        event.preventDefault();

                        $("#individualRemoveEncounterBtn").hide();

                        var number = $("#individualRemoveEncounterNumber").val();
                        var individual = $("#displayIndividualID").text();

                        $.post("../IndividualRemoveEncounter", {"number": number},
                        function(response) {
                          $("#setRemoveResultDiv").show();
                          $("#removeSuccessDiv").html("<strong>Success:</strong> Encounter " + number + " was successfully removed from " + individual + ".");
                          $("#removeErrorDiv").empty();
                          $("#removeShark").hide();
                          $("#removeLabel").hide();
                          //$("#manageIdentityMessage").hide();
                          $("#displayIndividualID").html("");
                          $("#individualAddEncounterInput").value="";
                          $("#topid").html("<%=encprops.getProperty("unassigned") %>");
          					$(".add2shark").show();               
        					$(".removeFromShark").hide();  
                            $("#individualErrorDiv").hide();
                            $("#individualDiv").removeClass("has-success");
                            $("#individualCheck, #matchedByCheck, #individualResultsDiv").hide();
                            
                            $("#displayIndividualID").html("");
                            //$('#displayIndividualID').closest('a').prop('href', '../individuals.jsp?number=' + individual);
                            //$('#topid').prop('href', '../individuals.jsp?number=' + individual);
                            $("#topid").html("<%=encprops.getProperty("unassigned") %>");
                            $("#topid").removeAttr("href");
                            
                        })
                        .fail(function(response) {
                          $("#setRemoveResultDiv").show();
                          $("#removeErrorDiv").html("<strong>Error:</strong> You can't remove this encounter from a marked individual because it is not assigned to one.");
                          $("#removeSuccessDiv").empty();
                          $("#individualRemoveEncounterBtn").show();
                          $("#removeLabel").show();
                          //$("#manageIdentityMessage").show();
                        });
                      });
                    });
                    </script>
                    
                    <div class="highlight resultMessageDiv removeFromShark" id="individualResultsDiv">
                      <span class="highlight" id="addErrorDiv"></span>
                      <span class="successHighlight" id="addSuccessDiv"></span>
                    </div>
                    
					<div id="setRemoveResultDiv" class="resultMessageDiv add2shark">
                      <span class="highlight" id="removeErrorDiv"></span>
                      <span class="successHighlight" id="removeSuccessDiv"></span>
                    </div>
         
                    <form class="removeFromShark" id="removeShark" name="removeShark">
                      <div class="form-group row">
                        <div class="col-sm-12 col-xs-10">
                          <label id="removeLabel" class="highlight"><strong><%=encprops.getProperty("removeFromMarkedIndividual")%></strong></label>
                          <input name="number" type="hidden" value="<%=num%>" id="individualRemoveEncounterNumber"/>
                          <input name="action" type="hidden" value="remove" />
                          <input type="submit" name="Submit" value="<%=encprops.getProperty("remove")%>" id="individualRemoveEncounterBtn" class="btn btn-sm editFormBtn"/>
                        </div>
                      </div>
                    </form>
                    <br>
		</div>


<!-- END INDIVIDUALID ATTRIBUTE -->

<!-- START ALTERNATEID ATTRIBUTE -->
            <%
            String alternateID="";
            if(enc.getAlternateID()!=null){
              alternateID=enc.getAlternateID();
            }
            %>
            <p>
              <img align="absmiddle" src="../images/alternateid.gif">
              <%=encprops.getProperty("alternate_id")%>: <span id="displayAltID"><%=alternateID%></span>
            </p>


          <script type="text/javascript">
            $(document).ready(function() {
              $("#setAltIDbtn").click(function(event) {
                event.preventDefault();

                $("#setAltIDbtn").hide();

                var encounter = $("#altIDencounter").val();
                var alternateid = $("#alternateid").val();

                $.post("../EncounterSetAlternateID", {"encounter": encounter, "alternateid": alternateid},
                function() {
                  $("#altIdErrorDiv").hide();
                  $("#altIdDiv").addClass("has-success");
                  $("#altIdCheck").show();
                  $("#displayAltID").html(alternateid);
                })
                .fail(function(response) {
                  $("#altIdDiv").addClass("has-error");
                  $("#altIdError, #altIdErrorDiv").show();
                  $("#altIdErrorDiv").html(response.responseText);
                });
              });

              $("#alternateid").click(function() {
                $("#altIdError, #altIdCheck, #altIdErrorDiv").hide()
                $("#altIdDiv").removeClass("has-success");
                $("#altIdDiv").removeClass("has-error");
                $("#setAltIDbtn").show();
              });
            });
          </script>

          <div class="highlight resultMessageDiv" id="altIdErrorDiv"></div>
            <form name="setAltID" class="editForm">
              <input name="encounter" type="hidden" value="<%=num%>" id="altIDencounter"/>
              <div class="form-group row">
                <div class="col-sm-3">
                  <label><%=encprops.getProperty("setAlternateID")%>:</label>
                </div>
                <div class="col-sm-5 col-xs-10" id="altIdDiv">
                  <input name="alternateid" id="alternateid" type="text" class="form-control" placeholder="<%=encprops.getProperty("alternate_id")%>"/>
                  <span class="form-control-feedback" id="altIdCheck">&check;</span>
                  <span class="form-control-feedback" id="altIdError">X</span>
                </div>
                <div class="col-sm-4">
                  <input name="Set" type="submit" id="setAltIDbtn" value="<%=encprops.getProperty("set")%>" class="btn btn-sm editFormBtn"/>
                </div>
              </div>
            </form>

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
								<a href="../occurrence.jsp?number=<%=myShepherd.getOccurrenceForEncounter(enc.getCatalogNumber()).getOccurrenceID() %>"><span id="displayOccurrenceID"><%=myShepherd.getOccurrenceForEncounter(enc.getCatalogNumber()).getOccurrenceID() %></span></a>
							<%
							}
							else{
							%>
								<span id="displayOccurrenceID"><%=encprops.getProperty("none_assigned") %></span>
							<%
							}
      				%>
  					</p>

              <%
                //Remove from occurrence if assigned
                if((myShepherd.getOccurrenceForEncounter(enc.getCatalogNumber())!=null) && isOwner) {
              %>
              <script type="text/javascript">
                $(document).ready(function() {

                  $("#removeOccurrenceBtn").click(function(event) {
                    event.preventDefault();

                    $("#removeOccurrenceBtn").hide();

                    var number = $("#occurrenceRemoveEncounterNumber").val();
                    var occurrence = $("#displayOccurrenceID").val();

                    $.post("../OccurrenceRemoveEncounter", {"number": number},
                    function(response) {
                      $("#occurrenceRemoveResultDiv").show();
                      $("#occurRemoveSuccessDiv").html("<strong>Success:</strong> Encounter " + number + " was successfully removed from occurrence " + occurrence + ".");
                      $("#occurRemoveErrorDiv").empty();
                      $("#removeOccurrenceBtn").hide();
                      $("#removeOccurLabel").hide();
                      $("#occurrenceEditMessage").hide();
                      $("#displayOccurrenceID").html("");
                      $("#")
                    })
                    .fail(function(response) {
                      $("#occurrenceRemoveResultDiv").show();
                      $("#occurRemoveErrorDiv").html(response.responseText);
                      $("#occurRemoveSuccessDiv").empty();
                      $("#removeOccurrenceBtn").show();
                      $("#removeOccurLabel").show();
                      $("#occurrenceEditMessage").show();
                    });
                  });
                });
              </script>

              <div class="editText" id="occurrenceEditMessage">
                <h3><%=encprops.getProperty("assignOccurrence")%></h3>
                <p class="editText"><em><small><%=encprops.getProperty("occurrenceMessage")%></small></em></p>
              </div>
              <div id="occurrenceRemoveResultDiv" class="resultMessageDiv">
                <span class="highlight" id="occurRemoveErrorDiv"></span>
                <span class="successHighlight" id="occurRemoveSuccessDiv"></span>
              </div>
              <form class="editForm" name="removeOccurrence">
                <input name="number" type="hidden" value="<%=num%>" id="occurrenceRemoveEncounterNumber"/>
                <input name="action" type="hidden" value="remove" id="occurrenceRemoveEncounterAction"/>
                <div class="form-group row">
                  <div class="col-sm-12">
                    <label id="removeOccurLabel" class="highlight"><strong><%=encprops.getProperty("removeFromOccurrence")%></strong></label>
                    <input type="submit" name="Submit" value="<%=encprops.getProperty("remove")%>" id="removeOccurrenceBtn" class="btn btn-sm editFormBtn"/>
                  </div>
                </div>
              </form>

                <br />
                <%
                }
                //create new Occurrence with name

                if(isOwner && (myShepherd.getOccurrenceForEncounter(enc.getCatalogNumber())==null)){
                %>
                <script type="text/javascript">
                  $(document).ready(function() {
                    $("#createOccur").click(function(event) {
                      event.preventDefault();

                      $("#createOccur").hide();

                      var occurrence = $("#createOccurrenceInput").val();
                      var number = $("#createOccurNumber").val();

                      $.post("../OccurrenceCreate", {"occurrence": occurrence, "number": number},
                      function() {
                        $("#createOccurErrorDiv").hide();
                        $("#occurDiv").addClass("has-success");
                        $("#createOccurCheck").show();
                        $("#displayOccurrenceID").html(occurrence);
                      })
                      .fail(function(response) {
                        $("#occurDiv").addClass("has-error");
                        $("#createOccurError, #createOccurErrorDiv").show();
                        $("#createOccurErrorDiv").html(response.responseText);
                      });
                    });

                    $("#createOccurrenceInput").click(function() {
                      $("#createOccurError, #createOccurCheck, #createOccurErrorDiv").hide()
                      $("#occurDiv").removeClass("has-success");
                      $("#occurDiv").removeClass("has-error");
                      $("#createOccur").show();
                    });
                  });
                </script>
                <div class="editText">
                  <h3><%=encprops.getProperty("assignOccurrence")%></h3>
                  <p class="editText"><em><small><%=encprops.getProperty("occurrenceMessage")%></small></em></p>
                </div>

                <div class="highlight resultMessageDiv" id="createOccurErrorDiv"></div>

                  <form name="createOccurrence" class="editForm">
                    <input name="number" type="hidden" value="<%=num%>" id="createOccurNumber"/>
                    <div class="form-group row">
                      <div class="col-sm-3">
                        <label><%=encprops.getProperty("createOccurrence")%>:</label>
                      </div>
                      <div class="col-sm-5 col-xs-10" id="occurDiv">
                        <input name="occurrence" type="text" id="createOccurrenceInput" class="form-control" placeholder="<%=encprops.getProperty("newOccurrenceID")%>" />
                        <span class="form-control-feedback" id="createOccurCheck">&check;</span>
                        <span class="form-control-feedback" id="createOccurError">X</span>
                      </div>
                      <div class="col-sm-4">
                        <input name="Create" type="submit" id="createOccur" value="<%=encprops.getProperty("create")%>" class="btn btn-sm editFormBtn"/>
                      </div>
                    </div>
                  </form>

                  <p class="editText"><strong>--<%=encprops.getProperty("or") %>--</strong></p>

                  <script type="text/javascript">
                    $(document).ready(function() {
                      $("#addOccurrence").click(function(event) {
                        event.preventDefault();

                        $("#addOccurrence").hide();

                        var occurrence = $("#add2OccurrenceInput").val();
                        var number = $("#addOccurNumber").val();
                        var action = $("#addOccurAction").val();

                        $.post("../OccurrenceAddEncounter", {"occurrence": occurrence, "number": number, "action": action},
                        function() {
                          $("#addOccurErrorDiv").hide();
                          $("#addDiv").addClass("has-success");
                          $("#createOccurCheck").show();
                          $("#displayOccurrenceID").html(occurrence);
                        })
                        .fail(function(response) {
                          $("#addDiv").addClass("has-error");
                          $("#addOccurError, #addOccurErrorDiv").show();
                          $("#addOccurErrorDiv").html(response.responseText);
                          $("#addOccurrence").show();
                        });
                      });

                      $("#add2OccurrenceInput").click(function() {
                        $("#addOccurError, #addOccurCheck, #addOccurErrorDiv").hide()
                        $("#addDiv").removeClass("has-success");
                        $("#addDiv").removeClass("has-error");
                        $("#addOccurrence").show();
                        $("#addOccurErrorDiv").hide();
                      });
                    });
                  </script>

                  <div class="highlight resultMessageDiv" id="addOccurErrorDiv"></div>

                  <form name="add2occurrence" class="editForm">
                    <input name="number" type="hidden" value="<%=num%>" id="addOccurNumber"/>
                    <input name="action" type="hidden" value="add" id="addOccurAction"/>
                    <div class="form-group row">
                      <div class="col-sm-3">
                        <label><%=encprops.getProperty("add2Occurrence")%>: </label>
                      </div>
                      <div class="col-sm-5 col-xs-10" id="addDiv">
                        <input name="occurrence" id="add2OccurrenceInput" type="text" class="form-control" placeholder="<%=encprops.getProperty("occurrenceID")%>"/>
                        <span class="form-control-feedback" id="addOccurCheck">&check;</span>
                        <span class="form-control-feedback" id="addOccurError">X</span>
                      </div>
                      <div class="col-sm-4">
                        <input name="Add" type="submit" id="addOccurrence" value="<%=encprops.getProperty("add")%>" class="btn btn-sm editFormBtn"/>
                      </div>
                    </div>
                  </form>

                    <%
                      }
                      %>
    <!-- END OCCURRENCE ATTRIBUTE -->

</div>


<%-- START CONTACT INFORMATION --%>
        <div>


          <% if (isOwner && CommonConfiguration.isCatalogEditable(context)) { %>
          <h2>
            <img align="absmiddle" src="../images/Crystal_Clear_kuser2.png" width="40px" height="42px" /> <%=encprops.getProperty("contactInformation") %>
            <button class="btn btn-md" type="button" name="button" id="editContactBtn">Edit</button>
            <button class="btn btn-md" type="button" name="button" id="closeEditContact" style="display:none;">Close Edit</button>
          </h2>


          <script type="text/javascript">
          $(document).ready(function() {
            var buttons = $("#editContactBtn, #closeEditContact").on("click", function(){
              buttons.toggle();
            });
            
            $(".editUsers").hide();
            //$(".addUser").hide();
            
            $("#editContactBtn").click(function() {
              $(".editUsers,.editFormContact, .editTextContact, #editContact, #editPhotographer, #setOthers").show();

              $("#submitNameError, #submitEmailError, #submitPhoneError, #submitAddressError, #submitOrgError, #submitProjectError, #submitNameCheck, #submitEmailCheck, #submitPhoneCheck, #submitAddressCheck, #submitOrgCheck, #submitProjectCheck, #photoNameCheck, #photoEmailCheck, #photoPhoneCheck, #photoAddressCheck, #informError, #informCheck").hide();

              $("#submitNameDiv, #submitEmailDiv, #submitPhoneDiv, #submitAddressDiv, #submitOrgDiv, #submitProjectDiv, #photoNameDiv, #photoEmailDiv, #photoPhoneDiv, #photoAddressDiv, #informOthersDiv").removeClass("has-error");

              $("#submitNameDiv, #submitEmailDiv, #submitPhoneDiv, #submitAddressDiv, #submitOrgDiv, #submitProjectDiv, #photoNameDiv, #photoEmailDiv, #photoPhoneDiv, #photoAddressDiv, #informOthersDiv").removeClass("has-success");

            });

            $("#closeEditContact").click(function() {
              $(".editFormContact, .editTextContact, .resultMessageDiv,.editUsers").hide();
            });
          });
          </script>


          <% }
          else {
           %>
           <h2><img align="absmiddle" src="../images/Crystal_Clear_kuser2.png" width="40px" height="42px" /> <%=encprops.getProperty("contactInformation") %></h2>

           <%}%>




	      <p class="para"><h4><%=encprops.getProperty("submitter") %></h4>
	      <%
	       if(enc.getSubmitters()!=null){   
	    	   %>
	    	   <table id="submitters" width="100%">
	    	   <tbody>
	    	   <%
	    	   List<User> submitters=enc.getSubmitters();
	    	   int numSubmitters=submitters.size();
			   for(int f=0;f<numSubmitters;f++){
				   User user=submitters.get(f);
				   %>
				   <tr id="<%=user.getUUID() %>">
					   <td>
					   <%
				    	   
					   		String name=encprops.getProperty("noname");
				    	   %>
				    	   <p style="background-color: #B0C4DE;border-radius:5px;padding: 5px;" id="<%=user.getUUID() %>">
				    	   <%
				          if(user.getFullName()!=null){name=user.getFullName();}
				            %>
				            <span id="displaySubmitName"><%=name %></span>
				            <%
				          
				          if (isOwner) {
				
						            if((user.getEmailAddress()!=null)&&(!user.getEmailAddress().equals(""))) {
						              //break up the string
						              StringTokenizer stzr=new StringTokenizer(user.getEmailAddress(),",");
		
						                %>
						                <br/><a href="mailto:<%=user.getEmailAddress()%>?subject=<%=encprops.getProperty("contactEmailMessageHeading") %><%=enc.getCatalogNumber()%>:<%=CommonConfiguration.getProperty("htmlTitle",context)%>"><%=user.getEmailAddress()%></a>
						                <%
						            }
					                if((user.getAffiliation()!=null)&&(!user.getAffiliation().equals(""))){
					                %>
					                	<br/><span id="displaySubmitOrg"><%=user.getAffiliation() %></span>
					                <%
					                }
				
				         } //end if isOwner
					         %>
					         </p>
					         </td>
					         <td style="display: table;vertical-align:middle;">
					         <%
					         if(isOwner){
					         %>
					         	&nbsp;<div name="deleteUsers" class="editFormUsers">
					         			<input type="hidden" name="uuid" value="<%=user.getUUID() %>" />
					         			<input type="hidden" name="type" value="submitter" />
					         			<input type="hidden" name="encounter" value="<%=enc.getCatalogNumber() %>" />
					         			&nbsp;<button id="remove<%=user.getUUID() %>button" class="btn btn-sm editUsers" style="margin-top:0;display: inline-block;" type="submit"><%=encprops.getProperty("remove") %></button>
					         		  </div>
					         <%
			   					}
					         %>
					         </td>
				         </tr>
				         <%
			   	} //submitters for loop     
			   	
			   	%>
			   	</tbody>
			   	</table>
			   	<%
			   	
	 		} //end if submitters!=null
			%>
			
			</p> <!--  End submitters paragraph -->
			<%
			if(isOwner){
			%>
			<div name="addUser" class="editFormUsers editUsers">
				<input type="hidden" name="encounter" value="<%=enc.getCatalogNumber() %>" />
				<input type="hidden" name="type" value="submitter" />
				<%=encprops.getProperty("addSubmitter") %> <input class="btn btn-sm addUser" name="email" id="addSubmitter" type="text"></input>&nbsp;<button class="btn btn-sm addUser" style="margin-top:0;display: inline-block;" type="submit"><%=encprops.getProperty("add") %></button>
			</div>
			<%
			}
			%>
		
	      <p class="para"><h4><%=encprops.getProperty("photographer") %></h4>
	      <%
	       if(enc.getPhotographers()!=null){   
	    	   %>
	    	   
	    	   <table id="photographers" width="100%">
	    	   <tbody>
	    	   <%
	    	   List<User> photographers=enc.getPhotographers();
	    	   int numSubmitters=photographers.size();
			   for(int f=0;f<numSubmitters;f++){
				   User user=photographers.get(f);
				   %>
				   <tr id="<%=user.getUUID() %>">
					   <td>
					   <%
				    	   
				    	   String name=encprops.getProperty("noname");
				    	   %>
				    	   <p style="background-color: #B0C4DE;border-radius:5px;padding: 5px;" id="<%=user.getUUID() %>">
				    	   <%
				          if(user.getFullName()!=null){name=user.getFullName();}
				            %>
				            <span id="displaySubmitName"><%=name%></span>
				            <%
				          
				          if (isOwner) {
				
						            if((user.getEmailAddress()!=null)&&(!user.getEmailAddress().equals(""))) {
						              //break up the string
						              StringTokenizer stzr=new StringTokenizer(user.getEmailAddress(),",");
		
						                %>
						                <br/><a href="mailto:<%=user.getEmailAddress()%>?subject=<%=encprops.getProperty("contactEmailMessageHeading") %><%=enc.getCatalogNumber()%>:<%=CommonConfiguration.getProperty("htmlTitle",context)%>"><%=user.getEmailAddress()%></a>
						                <%
						            }
					                if((user.getAffiliation()!=null)&&(!user.getAffiliation().equals(""))){
					                %>
					                	<br/><span id="displaySubmitOrg"><%=user.getAffiliation() %></span>
					                <%
					                }
				
				         } //end if isOwner
					         %>
					         </p>
					         </td>
					         <td style="display: table;vertical-align:middle;">
					         <%
					         if(isOwner){
					         %>
					         	&nbsp;<div name="deleteUsers" class="editFormUsers">
					         			<input type="hidden" name="uuid" value="<%=user.getUUID() %>" />
					         			<input type="hidden" name="type" value="photographer" />
					         			
					         			&nbsp;<button id="remove<%=user.getUUID() %>button" class="btn btn-sm editUsers" style="margin-top:0;display: inline-block;" type="submit"><%=encprops.getProperty("remove") %></button>
					         		  </div>
					         <%
			   					}
					         %>
					         </td>
				         </tr>
				         <%
			   	} //photographers for loop   
			   	%>
			   	</tbody>
			   	</table>
			   	<%
	 		} //end if photographers!=null
			%>
			</p> <!--  End photographers paragraph -->
			<%
			if(isOwner){
			%>
			<div name="addUser" class="editFormUsers editUsers">
				<input type="hidden" name="encounter" value="<%=enc.getCatalogNumber() %>" />
				<input type="hidden" name="type" value="photographer" />
				<%=encprops.getProperty("addPhotographer") %> <input class="btn btn-sm addUser" name="email" id="addPhotographer" type="text"></input>&nbsp;<button class="btn btn-sm addUser" style="margin-top:0;display: inline-block;" type="submit"><%=encprops.getProperty("add") %></button>
			</div>
		    <%
			}
		    %>          
		                   
		                  
		                   
		                   
		                   
		                   
		                   
							<%
		                    if(isOwner){
		           
		                    %>
		
		                     	<p class="para"><h4><%=encprops.getProperty("inform_others") %></h4> <%
	       						if(enc.getPhotographers()!=null){   
	    	   %>
	    	   
	    	   <table id="informOthers" width="100%">
	    	   <tbody>
	    	   <%
	    	   List<User> informOthers=enc.getInformOthers();
	    	   int numOthers=informOthers.size();
			   for(int f=0;f<numOthers;f++){
				   User user=informOthers.get(f);
				   %>
				   <tr id="<%=user.getUUID() %>">
					   <td>
					   <%
				    	   
				    	   String name=encprops.getProperty("noname");
				    	   %>
				    	   <p style="background-color: #B0C4DE;border-radius:5px;padding: 5px;" id="<%=user.getUUID() %>">
				    	   <%
				          if(user.getFullName()!=null){
				        	  name=user.getFullName();
				            	%>
				            	<span id="displaySubmitName"><%=name%></span>
				            	<%
			   				}
				          
				          if (isOwner) {
				
						            if((user.getEmailAddress()!=null)&&(!user.getEmailAddress().equals(""))) {
						              //break up the string
						              StringTokenizer stzr=new StringTokenizer(user.getEmailAddress(),",");
		
						                %>
						                <br/><a href="mailto:<%=user.getEmailAddress()%>?subject=<%=encprops.getProperty("contactEmailMessageHeading") %><%=enc.getCatalogNumber()%>:<%=CommonConfiguration.getProperty("htmlTitle",context)%>"><%=user.getEmailAddress()%></a>
						                <%
						            }
					                if((user.getAffiliation()!=null)&&(!user.getAffiliation().equals(""))){
					                %>
					                	<br/><span id="displaySubmitOrg"><%=user.getAffiliation() %></span>
					                <%
					                }
				
				         } //end if isOwner
					         %>
					         </p>
					         </td>
					         <td style="display: table;vertical-align:middle;">
					         <%
					         if(isOwner){
					         %>
					         	&nbsp;<div name="deleteUsers" class="editFormUsers">
					         			<input type="hidden" name="uuid" value="<%=user.getUUID() %>" />
					         			<input type="hidden" name="type" value="informOther" />
					         			
					         			&nbsp;<button id="remove<%=user.getUUID() %>button" class="btn btn-sm editUsers" style="margin-top:0;display: inline-block;" type="submit"><%=encprops.getProperty("remove") %></button>
					         		  </div>
					         <%
			   					}
					         %>
					         </td>
				         </tr>
				         <%
			   	} //informOthers for loop   
			   	%>
			   	</tbody>
			   	</table>
			   	<%
	 		} //end if informOther !=null
			%>
			</p> <!--  End informOthers paragraph -->
			<%
			if(isOwner){
			%>
			<div name="addUser" class="editFormUsers editUsers">
				<input type="hidden" name="encounter" value="<%=enc.getCatalogNumber() %>" />
				<input type="hidden" name="type" value="informOther" />
				<%=encprops.getProperty("addOthers") %> <input class="btn btn-sm addUser" name="email" id="addOther" type="text"></input>&nbsp;<button class="btn btn-sm addUser" style="margin-top:0;display: inline-block;" type="submit"><%=encprops.getProperty("add") %></button>
			</div>
			<%
			}
			%>
		            
		               		<%
		                  	} //end if isOwner
    	   		

                  	%>
                  	<br>
                  	 <!--  remake for Users removal -->         
		         <script type="text/javascript">
                    $(document).ready(function() {
                    	
                    	
                      //$("button.editUsers").click(function(event) {
                      $("#submitters,#photographers,#informOthers").on('click', 'button.editUsers',function(event) {
                    	//alert("Made it here");  
                        event.preventDefault();
						if(confirm('<%=encprops.getProperty("sureDeleteUser") %>')){

	                        var SendButton = $(event.target);
	                        var elemID=event.target.id;
	    					var TheRow = SendButton.parents('tr');
	    					var TheDiv = SendButton.parents('div');
	    					var type= $(TheDiv).find("> input[name='type']").val();
	    					//alert("type:"+type);
	    					var uuid = $(TheDiv).find("> input[name='uuid']").val();
	    					//alert("uuid:"+uuid);
	
	                        
	
	                        $.post("../EncounterRemoveUser", 
	                        	{
		                        	"encounter": '<%=enc.getCatalogNumber() %>', 
		                        	"type": type, 
		                        	"uuid": uuid, 
	                        	},
		                        function() {
		                          //$("#individualErrorDiv").hide();
		                          TheRow.hide();
		                         
		
		                     }) //end post
		                     .fail(function(response) {
		                          alert("I could not remove this user. Please check the logs for errors.");
		                        }); //end fail
	                        
		                      } //end if
						
                      	
	                    	
                    	}); //end click function
                    });  //end document ready
                    </script>   
                    
                 <!--  remake for User addition -->         
		         <script type="text/javascript">
                    $(document).ready(function() {
                    	
                    	
                      $("button.addUser").click(function(event) {
                    	//alert("Made it here");  
                        event.preventDefault();
						
	                        var SendButton = $(event.target);
	                        var elemID=event.target.id;
	    					//var TheTable = SendButton.parents('table');
	    					var TheDiv = SendButton.parents('div');
	    					var type= $(TheDiv).find("> input[name='type']").val();
	    					//alert("type:"+type);
	    					var email = $(TheDiv).find("> input[name='email']").val();
	    					///alert("email:"+email);
	
	                        
	
	                        $.post("../EncounterAddUser", 
	                        	{
		                        	"encounter": '<%=enc.getCatalogNumber() %>', 
		                        	"type": type, 
		                        	"email": email, 
	                        	},
		                        function(data) {
		                          
	                        		//add User row above
	                        		var remove="<%=encprops.getProperty("remove") %>";
	                        		var encounter="<%=enc.getCatalogNumber() %>";
	                        		
	                        		$("table#"+type+"s").find('> tbody:last-child')
	                        			.append('<tr id=\"'+data.uuid+'\">'
	                        			                            +'<td><p style=\"background-color: #B0C4DE;border-radius:5px;padding: 5px;\">'+email+'</p></td>'
	                        			                            +'<td style=\"display: table;vertical-align:middle;\">'
	                        			                            	+'&nbsp;<div name=\"deleteUsers\" class=\"editFormUsers\">'
	                        						         			+'<input type=\"hidden\" name=\"uuid\" value=\"'+data.uuid+'\" />'
	                        						         			+'<input type=\"hidden\" name=\"type\" value=\"'+type+'\" />'
	                        						         			+'<input type=\"hidden\" name=\"encounter\" value=\"'+encounter+'\" />'
	                        						         			+'&nbsp;<button id=\"remove'+data.uuid+'button\" class=\"btn btn-sm editUsers\" style=\"margin-top:0;display: inline-block;\" type=\"submit\">'+remove+'</button>'
	                        						         		  +'</div>'
	                        			                            +'</td>'
	                        			        +'</tr>'
	                        		);
	                        		
		                         
		
		                     }, 'json' 
	                         ) //end post
		                     .fail(function(response) {
		                          alert("I could not remove this user. Please check the logs for errors.");
		                        }); //end fail
	                        
		                      
						
                      	
	                    	
                    	}); //end click function
                    });  //end document ready
                    </script>      
                  
                   
        </div>
<%-- END CONTACT INFORMATION --%>


<%-- START METADATA --%>
<table>
<tr>
<td width="560px" style="vertical-align:top; background-color: #E8E8E8;padding-left: 10px;padding-right: 10px;padding-top: 10px;padding-bottom: 10px;">

<% if (isOwner && CommonConfiguration.isCatalogEditable(context)) { %>
<h2>
  <img align="absmiddle" width="40px" height="40px" style="border-style: none;" src="../images/workflow_icon.gif" /> <%=encprops.getProperty("metadata") %>
  <button class="btn btn-md" type="button" name="button" id="editMeta">Edit</button>
  <button class="btn btn-md" type="button" name="button" id="closeEditMeta" style="display:none;">Close Edit</button>
</h2>


<script type="text/javascript">
$(document).ready(function() {
  var buttons = $("#editMeta, #closeEditMeta").on("click", function(){
    buttons.toggle();
  });
  $("#editMeta").click(function() {
    $(".editFormMeta, .editTextMeta, #editWork, #Assign").show();

    $("#setMetaCheck, #setMetaError, #workCheck, #workError, #assignCheck, #assignError").hide();

  });

  $("#closeEditMeta").click(function() {
    $(".editFormMeta, .editTextMeta, .resultMessageDiv").hide();
  });
});
</script>


<% }
else {
 %>
 <h2><img align="absmiddle" width="40px" height="40px" style="border-style: none;" src="../images/workflow_icon.gif" /> <%=encprops.getProperty("metadata") %></h2>

 <%}%>





								<p class="para">
									Number: <%=num%>
								</p>
			<!-- START WORKFLOW ATTRIBUTE -->


 								<%

									String state="";
									if (enc.getState()!=null){state=enc.getState();}
									%>
									<p class="para">
										 <%=encprops.getProperty("workflowState") %> <span id="displayWork"><%=state %></span>

										<%
										%>

										<%
										%>

									</p>
									<%
									%>

                  <script type="text/javascript">
                    $(document).ready(function() {
                      $("#selectState option[value='<%=state %>']").attr('selected','selected');

                      $("#editWork").click(function(event) {
                        event.preventDefault();

                        $("#editWork").hide();

                        var number = $("#workNumber").val();
                        var state = $("#selectState").val();

                        $.post("../EncounterSetState", {"number": number, "state": state},
                        function() {
                          $("#workErrorDiv").hide();
                          $("#workCheck").show();
                          $("#displayWork").html(state);
                        })
                        .fail(function(response) {
                          $("#workError, #workErrorDiv").show();
                          $("#workErrorDiv").html(response.responseText);
                        });
                      });

                      $("#selectState").click(function() {
                        $("#workerror, #workCheck, #workErrorDiv").hide()
                        $("#editWork").show();
                      });
                    });
                  </script>


                  <div>
                    <div class="highlight resultMessageDiv" id="workErrorDiv"></div>

                    <p class="editTextMeta"><strong><%=encprops.getProperty("setWorkflowState")%></strong></p>

                    <form name="workflowStateForm" class="editFormMeta">
                      <input name="number" type="hidden" value="<%=num%>" id="workNumber" />
                      <div class="form-group row">
                        <div class="col-sm-5">
                          <select name="state" id="selectState" class="form-control" size="1">
															<%
						       								boolean hasMoreStates=true;
						       								int stateTaxNum=0;
						       								while(hasMoreStates){
						       	  								String currentLifeState = "encounterState"+stateTaxNum;
						       	  								if(CommonConfiguration.getProperty(currentLifeState,context)!=null){
						       	  									%>
						       	  	  								<option value="<%=CommonConfiguration.getProperty(currentLifeState,context)%>"><%=CommonConfiguration.getProperty(currentLifeState,context)%></option>
						       	  									<%
						       										stateTaxNum++;
						          								}
						          								else{
						             								hasMoreStates=false;
						          								}

						       								} //end while
						       								%>
						      				</select>
                        </div>
                        <div class="col-sm-3">
                          <input name="<%=encprops.getProperty("set")%>" type="submit" id="editWork" value="<%=encprops.getProperty("set")%>" class="btn btn-sm editFormBtn"/>
                          <span class="form-control-feedback" id="workCheck">&check;</span>
                          <span class="form-control-feedback" id="workError">X</span>
                        </div>
                      </div>
                    </form>
                  </div>

       							<%
        						// }
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
      									%>

      									<%
      									%>
     								</tr>
     								<tr>
     									<td>
                         				<%
                         				if(enc.getAssignedUsername()!=null){

                        	 				String username=enc.getAssignedUsername();
                        	 				Shepherd aUserShepherd=new Shepherd("context0");
                        	 				aUserShepherd.setAction("encounter.jsp2");
                         					if(aUserShepherd.getUser(username)!=null){
                         					%>
                                			<%

                         					User thisUser=aUserShepherd.getUser(username);
                                			String profilePhotoURL="../images/empty_profile.jpg";

                         					if(thisUser.getUserImage()!=null){
                         						profilePhotoURL="/"+CommonConfiguration.getDataDirectoryName("context0")+"/users/"+thisUser.getUsername()+"/"+thisUser.getUserImage().getFilename();
                         					}
                         					%>
                     						<%
                         					String displayName="";
                         					if(thisUser.getFullName()!=null){
                         						displayName=thisUser.getFullName();
                         						%>
                         					<%
                         					}
                                			%>

     								<div>
                      <div class="row">
                        <div class="col-sm-6" style="padding-top: 15px; padding-bottom: 15px;">
                          <img src="../cust/mantamatcher/img/individual_placeholder_image.jpg" class="lazyload" align="top" data-src="<%=profilePhotoURL%>" style="height: 150px;border: 1px solid;" />
                        </div>
                        <div class="col-sm-6" style="padding-top: 15px; padding-bottom: 15px;">
                          <%-- <p> --%>

                        <%
                        if(thisUser.getAffiliation()!=null){
                        %>
                        <p><strong><%=displayName %></strong></p>
                        <p><strong><%=encprops.getProperty("affiliation") %></strong> <%=thisUser.getAffiliation() %></p>
                        <%
                        }

                        if(thisUser.getUserProject()!=null){
                        %>
                        <p><strong><%=encprops.getProperty("researchProject") %></strong> <%=thisUser.getUserProject() %></p>
                        <%
                        }

                        if(thisUser.getUserURL()!=null){
                            %>
                            <p><strong><%=encprops.getProperty("webSite") %></strong> <a style="font-weight:normal;color: blue" class="ecocean" href="<%=thisUser.getUserURL()%>"><%=thisUser.getUserURL() %></a></p>
                            <%
                          }

                        if(thisUser.getUserStatement()!=null){
                            %>
                            <p/><em>"<%=thisUser.getUserStatement() %>"</em></p>
                            <%
                          }
                        %>
                        </div>
                      </div>

                  </div>

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
%>


<!-- start set username -->
<div>
  <div class="highlight resultMessageDiv" id="assignErrorDiv"></div>

  <p class="editTextMeta"><strong><%=encprops.getProperty("assignUser")%></strong></p>

  <form name="asetSubmID" action="../EncounterSetSubmitterID" method="post" class="editFormMeta">
    <input name="number" type="hidden" value="<%=num%>" id="assignNumber"/>
    <div class="form-group row">
      <div class="col-sm-5">
        <select name="submitter" id="submitterSelect" class="form-control" size="1">
            <option value=""></option>
            <%

            Shepherd userShepherd=new Shepherd("context0");
            userShepherd.setAction("encounter.jsp3");
            userShepherd.beginDBTransaction();

            ArrayList<String> usernames=userShepherd.getAllUsernames();

            int numUsers=usernames.size();
            for(int i=0;i<numUsers;i++){
                String thisUsername=usernames.get(i);
                //User thisUser2=userShepherd.getUser(thisUsername);
                String thisUserFullname=thisUsername;
                //if((thisUser2!=null)&&(thisUser2.getFullName()!=null)){thisUserFullname=thisUser2.getFullName();}
              %>
              <option value="<%=thisUsername%>"><%=thisUserFullname%></option>
              <%
            }
            userShepherd.rollbackDBTransaction();
            userShepherd.closeDBTransaction();
            %>
          </select>
      </div>
      <div class="col-sm-3">
        <input name="Assign" type="submit" id="Assign" value="<%=encprops.getProperty("assign")%>" class="btn btn-sm editFormBtn"/>
      </div>
    </div>
  </form>
</div>


                   		<%

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
<script type="text/javascript">
  $(document).ready(function() {
    $("#tapirApprove").click(function(event) {
      event.preventDefault();


      var number = $("#tapirNumber").val();
      var action = $("#tapirAction").val();

      $.post("../EncounterSetTapirLinkExposure", {"number": number, "action": action},
      function(response) {
        $("#tapirResultDiv").show();
        $("#tapirError").hide();
        $("#tapirSuccess").html(response);
      })
      .fail(function(response) {
        $("#tapirResultDiv").show();
        $("#tapirSuccess").hide();
        $("#tapirError").html(response.responseText);
      });
    });
  });
</script>
    <div>
      <div id="tapirResultDiv" class="resultMessageDiv">
        <span id="tapirSuccess" class="successHighlight"></span>
        <span id="tapirError" class="highlight"></span>
      </div>
      <form name="setTapirLink" class="editFormMeta">
        <input name="action" type="hidden" id="tapirAction" value="tapirLinkExpose" />
        <input name="number" type="hidden" value="<%=num%>" id="tapirNumber"/>
        <%
        String tapirCheckIcon="cancel.gif";
        if(enc.getOKExposeViaTapirLink()){tapirCheckIcon="check_green.png";}
        %>
        <label>TapirLink:</label>&nbsp;
        <input  style="width: 40px;height: 40px;" align="absmiddle" name="approve" type="image" src="../images/<%=tapirCheckIcon %>" id="tapirApprove" value="<%=encprops.getProperty("change")%>"/>
        &nbsp;
        <a href="<%=CommonConfiguration.getWikiLocation(context)%>tapirlink" target="_blank"><img src="../images/information_icon_svg.gif" alt="Help" border="0" align="absmiddle"/></a>
      </form>
    </div>

<!-- END TAPIRLINK DISPLAY AND SETTER -->
<%
}
%>

<!-- START AUTOCOMMENTS -->
<%
if(request.getUserPrincipal()!=null){
%>
<!-- start autocomments -->
<script type="text/javascript">
  $(document).ready(function() {
    $("#manualAdd").click(function(event) {
      event.preventDefault();


      var number = $("#autoNumber").val();
      var user = $("#autoUser").val();
      var autocomments = $("#autoComments").val();

      $.post("../EncounterAddComment", {"number": number, "user": user, "autocomments": autocomments},
      function() {
        $("#autoCommentErrorDiv").hide();
        $("#autoCommentsDiv").prepend("<p>" + autocomments + "</p>");
        $("#autoComments").val("");
      })
      .fail(function(response) {
        $("#autoCommentErrorDiv").show();
        $("#autoCommentErrorDiv").html(response.responseText);
      });
    });
  });
</script>
<div>
  <p ><strong><%=encprops.getProperty("auto_comments")%></strong></p>
  <div class="highlight resultMessageDiv" id="autoCommentErrorDiv"></div>
    <%
    String rComments="";
    if(enc.getRComments()!=null){rComments=enc.getRComments();}
    %>

    <div id="autoCommentsDiv" style="text-align:left;border: 1px solid lightgray;width:auto;height: 200px;overflow-y:scroll;overflow-x:scroll;background-color: white;padding-left: 10px;padding-right: 10px;">
      <p class="para"><%=rComments.replaceAll("\n", "<br />")%></p>

    </div>

    <form name="addComments" class="editFormMeta">
        <input name="user" type="hidden" value="<%=request.getRemoteUser()%>" id="autoUser" />
        <input name="number" type="hidden" value="<%=enc.getEncounterNumber()%>" id="autoNumber" />
        <input name="action" type="hidden" value="enc_comments" id="autoAction" />

        <textarea name="autocomments" cols="50" id="autoComments" class="form-control"></textarea>
        <input name="Submit" type="submit" value="<%=encprops.getProperty("add_comment")%>" class="btn btn-sm" id="manualAdd"/>

    </form>
</div>

<%
}
%>
<!-- END AUTOCOMMENTS -->

<!-- START DELETE ENCOUNTER FORM -->
<%
if (isOwner) {
%>
<table width="100%" border="0" cellpadding="1">
    <tr>
      <td height="30" class="para">
        <form onsubmit="return confirm('<%=encprops.getProperty("sureDelete") %>');" name="deleteEncounter" class="editFormMeta" method="post" action="../EncounterDelete">
              <input name="number" type="hidden" value="<%=num%>" />
              <%
              String deleteIcon="cancel.gif";
              %>
              <input align="absmiddle" name="approve" type="submit" class="btn btn-sm btn-block deleteEncounterBtn" id="deleteButton" value="<%=encprops.getProperty("deleteEncounter") %>" />
        </form>
      </td>
    </tr>
  </table>
<!-- END DELETE ENCOUNTER FORM -->
<%
}
%>

</td>
</tr>
</table>


<%
  pageContext.setAttribute("showMeasurements", CommonConfiguration.showMeasurements(context));
  pageContext.setAttribute("showMetalTags", CommonConfiguration.showMeasurements(context));
  pageContext.setAttribute("showAcousticTag", CommonConfiguration.showAcousticTag(context));
  pageContext.setAttribute("showSatelliteTag", CommonConfiguration.showSatelliteTag(context));
%>

<c:if test="${showMeasurements}">
<br />
<%
  pageContext.setAttribute("measurementTitle", encprops.getProperty("measurements"));
  pageContext.setAttribute("measurements", Util.findMeasurementDescs(langCode,context));
%>

<% if (isOwner && CommonConfiguration.isCatalogEditable(context)) { %>
<h2>
  <img align="absmiddle" width="40px" height="40px" style="border-style: none;" src="../images/ruler.png" />
  <c:out value="${measurementTitle}"></c:out>
  <button class="btn btn-md" type="button" name="button" id="editMeasure">Edit</button>
  <button class="btn btn-md" type="button" name="button" id="closeEditMeasure" style="display:none;">Close Edit</button>
</h2>


<script type="text/javascript">
$(document).ready(function() {
  var buttons = $("#editMeasure, #closeEditMeasure").on("click", function(){
    buttons.toggle();
  });
  $("#editMeasure").click(function() {
    $(".editFormMeasure, .editTextMeasure, #editWork, #Assign").show();
  });

  $("#closeEditMeasure").click(function() {
    $(".editFormMeasure, .editTextMeasure, .resultMessageDiv").hide();
  });
});
</script>


<% }
else {
 %>
 <h2>
   <img align="absmiddle" width="40px" height="40px" style="border-style: none;" src="../images/ruler.png" />
   <c:out value="${measurementTitle}"></c:out>
 </h2>

 <%}%>



<c:if test="${editable and !empty measurements}">
</c:if>
<table>
<tr>
<th class="measurement">
  <span id="displayMtype"><%=encprops.getProperty("type") %></span>
</th>
<th class="measurement">
  <span id="displayMsize"><%=encprops.getProperty("size") %></span>
</th>
<th class="measurement">
  <span id="displayMunits"><%=encprops.getProperty("units") %></span>
</th>
<c:if test="${!empty samplingProtocols}">
  <th class="measurement">
    <display id="displayMsample"><%=encprops.getProperty("samplingProtocol") %></span>
  </th>
</c:if>
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
    <td class="measurement">
      <c:out value="${item.label}"/>
  </td>
  <td class="measurement">
    <c:out value="${measurementValue}"/>
  </td>
  <td class="measurement">
    <c:out value="${item.unitsLabel}"/>
  </td>
  <td class="measurement">
    <c:out value="${samplingProtocol}"/>
  </td>
</tr>
</c:forEach>
</table>
</p>

<%
%>
<%-- start measuremnts form--%>
<div>
  <p class="editTextMeasure"><strong><%=encprops.getProperty("setMeasurements")%></strong></p>

    <%
    pageContext.setAttribute("items", Util.findMeasurementDescs(langCode,context));
    %>

    <table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF" class="editFormMeasure">
      <form name="setMeasurements" class="editFormMeasure" id="measurementForm" action="../EncounterSetMeasurements" method="post">
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
            <td class="form_label">
            <c:out value="${item.label}"/>
            <input type="hidden" name="measurement${index}(id)" value="${measurementEvent.dataCollectionEventID}"/>
          </td>
            <td>
            <input name="measurement${index}(value)" value="${measurementEvent.value}" id="measurementEvent${index}"/>
            <input type="hidden" name="measurement${index}(type)" value="${item.type}"/>
            <input type="hidden" name="measurement${index}(units)" value="${item.unitsLabel}"/>
            <c:out value="(${item.unitsLabel})"/>
            <select name="measurement${index}(samplingProtocol)" id="selectMeasurement">
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
        <td>
          <input name="${set}" type="submit" value="${set}" class="btn btn-sm editFormBtn" id="addMeasurements"/>
        </td>
      </tr>
    </table>
    </form>
</div>
<!-- end measurements form -->

<%
%>

</c:if>

<table>
<tr>
<td width="560px" style="vertical-align:top;background-color: #E8E8E8;padding-left: 10px;padding-right: 10px;">



<c:if test="${showMetalTags}">



<% if (isOwner && CommonConfiguration.isCatalogEditable(context)) { %>
<h2>
  <img align="absmiddle" src="../images/Crystal_Clear_app_starthere.png" width="40px" height="40px" /> <%=encprops.getProperty("tracking") %>
  <button class="btn btn-md" type="button" name="button" id="editTracking">Edit</button>
  <button class="btn btn-md" type="button" name="button" id="closeEditTracking" style="display:none;">Close Edit</button>
</h2>


<script type="text/javascript">
$(document).ready(function() {
  var buttons = $("#editTracking, #closeEditTracking").on("click", function(){
    buttons.toggle();
  });
  $("#editTracking").click(function() {
    $(".editFormTracking, .editTextTracking").show();
  });

  $("#closeEditTracking").click(function() {
    $(".editFormTracking, .editTextTracking, .resultMessageDiv").hide();
  });
});
</script>


<% }
else {
 %>
 <h2><img align="absmiddle" src="../images/Crystal_Clear_app_starthere.png" width="40px" height="40px" /> <%=encprops.getProperty("tracking") %></h2>

 <%}%>


<%
  pageContext.setAttribute("metalTagTitle", encprops.getProperty("metalTags"));
  pageContext.setAttribute("metalTags", Util.findMetalTagDescs(langCode,context));
%>
<p class="para"><em><c:out value="${metalTagTitle}"></c:out></em>
<%
%>
<%
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
%>
<!-- start metal tag popup -->

<div>
  <p class="editTextTracking"><strong><%=encprops.getProperty("resetMetalTags")%></strong></p>
  <% pageContext.setAttribute("metalTagDescs", Util.findMetalTagDescs(langCode,context)); %>

  <form name="setMetalTags" class="editFormTracking" id="tagsForm" action="../EncounterSetTags" method="post">
  <input type="hidden" name="tagType" value="metalTags" id="metalTagsType"/>
  <input type="hidden" name="encounter" value="${num}" id="metalTagsEncounter"/>
  <table cellpadding="1" cellspacing="0">

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
     <tr><td><input name="metalTag(${metalTagDesc.location})" value="${metalTag.tagNumber}" id="metalTagLocation" class="form-control"/></td></tr>
  </c:forEach>
  <tr><td><input name="${set}" type="submit" value="${set}" class="btn btn-sm editFormBtn" id="setMetalTags"/></td></tr>
  </table>
  </form>
</div>

<%
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
%>
<%
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
%>
<!-- start acoustic tag  -->

<div>
  <p class="editTextTracking"><strong><%=encprops.getProperty("resetAcousticTag")%></strong></p>

  <c:set var="acousticTag" value="${enc.acousticTag}"/>
   <c:if test="${empty acousticTag}">
   <%
     pageContext.setAttribute("acousticTag", new AcousticTag());
   %>
   </c:if>
   <table cellpadding="1" cellspacing="0">

    <tr>
      <td>
        <form name="setAcousticTag" class="editFormTracking" id="acousticTagsForm" action="../EncounterSetTags" method="post">
        <input type="hidden" name="encounter" value="${num}" id="acousticTagEncounter"/>
        <input type="hidden" name="tagType" value="acousticTag" id="acousticTagType"/>
        <input type="hidden" name="id" value="${acousticTag.id}"/>
        <table>
          <tr><td class="formLabel"><%=encprops.getProperty("serialNumber") %></td></tr>
          <tr><td><input name="acousticTagSerial" value="${acousticTag.serialNumber}" class="form-control" id="acousticTagInput"/></td></tr>
          <tr><td class="formLabel">ID:</td></tr>
          <tr><td><input name="acousticTagId" value="${acousticTag.idNumber}" id="acousticTagId" class="form-control"/></td></tr>
          <tr><td><input name="${set}" type="submit" value="${set}" class="btn btn-sm editFormBtn" id="setAcousticTags"/></td></tr>
        </table>
        </form>
      </td>
    </tr>
 </table>
</div>

<%
%>

</c:if>


<c:if test="${showSatelliteTag}">
<%
  pageContext.setAttribute("satelliteTagTitle", encprops.getProperty("satelliteTag"));
  pageContext.setAttribute("satelliteTag", enc.getSatelliteTag());
%>
<p class="para"><em><c:out value="${satelliteTagTitle}"></c:out></em>
<%
%>
&nbsp;
<%

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
%>
<!-- start sat tag metadata  -->
<div>
  <p class="editTextTracking"><strong><%=encprops.getProperty("resetSatelliteTag")%></strong></p>

   <c:set var="satelliteTag" value="${enc.satelliteTag}"/>
   <c:if test="${empty satelliteTag}">
   <%
     pageContext.setAttribute("satelliteTag", new SatelliteTag());
   %>
   </c:if>
   <%
      pageContext.setAttribute("satelliteTagNames", Util.findSatelliteTagNames(context));
   %>
   <form name="setSatelliteTag" class="editFormTracking" action="../EncounterSetTags" method="post">
   <input type="hidden" name="tagType" value="satelliteTag" id="satelliteTagType"/>
   <input type="hidden" name="encounter" value="${num}" id="satelliteTagEncounter"/>
   <input type="hidden" name="id" value="${satelliteTag.id}" id="satelliteTagId"/>
   <table cellpadding="1" cellspacing="0">

      <tr><td class="formLabel"><%=encprops.getProperty("name") %></td></tr>
      <tr><td>
        <select name="satelliteTagName" class="form-control" size="1">
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
      <tr><td><input name="satelliteTagSerial" class="form-control" value="${satelliteTag.serialNumber}" id="satelliteTagSerial"/></td></tr>
      <tr><td class="formLabel">Argos PTT:</td></tr>
      <tr><td><input name="satelliteTagArgosPttNumber" value="${satelliteTag.argosPttNumber}" id="satelliteTagArgosPttNumber" class="form-control"/></td></tr>
      <tr><td><input name="${set}" type="submit" value="${set}" class="btn btn-sm editFormBtn" id="setSatelliteTags"/></td></tr>
   </table>
   </form>
</div>

<%

%></c:if>
</td>
</tr>
</table>

      <div id="dialogOccurrence" title="<%=encprops.getProperty("assignOccurrence")%>" style="display:none"></div>


  </div>
  <%-- END LEFT COLUMN --%>

  <%-- START RIGHT COLUMN --%>
  <div class="col-xs-12 col-sm-6" style="vertical-align:top">


    <!-- start DATE section -->
    <table>
    <tr>
    <td width="560px" style="vertical-align:top;">

      <% if (isOwner && CommonConfiguration.isCatalogEditable(context)) { %>
      <h2>
        <img align="absmiddle" src="../images/calendar.png" width="40px" height="40px" /><%=encprops.getProperty("date") %>
        <button class="btn btn-md" type="button" name="button" id="editDate">Edit</button>
        <button class="btn btn-md" type="button" name="button" id="closeEditDate" style="display:none;">Close Edit</button>
    </h2>



      <script type="text/javascript">
      $(document).ready(function() {
        var buttons = $("#editDate, #closeEditDate").on("click", function(){
          buttons.toggle();
        });
        $("#editDate").click(function() {
          $("#releaseCheck, #releaseError, #verbatimCheck, #verbatimError, #resetDateCheck, #resetDateError").hide();

          $(".editFormDate, .editTextDate, #setVerbatimEventDateBtn, #AddDate, #addResetDate").show();

          $("#releaseDiv, #verbatimDiv, #resetDateDiv").removeClass("has-error");

          $("#releaseDiv, #verbatimDiv, #resetDateDiv").removeClass("has-success");
        });

        $("#closeEditDate").click(function() {
          $(".editFormDate, .editTextDate, .resultMessageDiv").hide();
        });
      });
      </script>


      <% }
      else {
       %>
       <h2><img align="absmiddle" src="../images/calendar.png" width="40px" height="40px" /><%=encprops.getProperty("date") %>
       </h2>
       <%}%>

    <p>
    <%if(enc.getDateInMilliseconds()!=null){ %>
      <a
        href="//<%=CommonConfiguration.getURLLocation(request)%>/xcalendar/calendar2.jsp?scDate=<%=enc.getMonth()%>/1/<%=enc.getYear()%>">
        <span id="displayDate"><%=enc.getDate()%></span>
      </a>
        <%
    }
    else{
    %>
    <span id="displayDate"><%=encprops.getProperty("unknown") %></span>
    <%
    }
            		%>

    <br />
    <em><%=encprops.getProperty("verbatimEventDate")%></em>:
        <%
    				if(enc.getVerbatimEventDate()!=null){
    				%>
        <span id="displayVerbatimDate"><%=enc.getVerbatimEventDate()%></span>
        <%
    				}
    				else {
    				%>
        <span id="displayVerbatimDate"><%=encprops.getProperty("none") %></span>
        <%
    				}

            		%>

    <!-- end verbatim event date -->



    <%
      pageContext.setAttribute("showReleaseDate", CommonConfiguration.showReleaseDate(context));
    %>
    <c:if test="${showReleaseDate}">
      <br /><em><%=encprops.getProperty("releaseDate") %></em>: <span id="displayReleaseDate"></span>
        <fmt:formatDate value="${enc.releaseDate}" pattern="yyyy-MM-dd"/>
      </p>
    </c:if>
    <br>
        <!-- start date -->
    <script type="text/javascript">
      $(document).ready(function() {
        $("#addResetDate").click(function(event) {
          event.preventDefault();

          $("#addResetDate").hide();

          var number = $("#resetDateNumber").val();
          var datepicker = $("#datepickerField").val();

          $.post("../EncounterResetDate", {"number": number, "datepicker": datepicker},
          function() {
            $("#resetDateErrorDiv").hide();
            $("#resetDateDiv").addClass("has-success");
            $("#resetDateCheck").show();
            $("#displayDate").html(datepicker);
          })
          .fail(function(response) {
            $("#resetDateDiv").addClass("has-error");
            $("#resetDateError, #resetDateErrorDiv").show();
            $("#resetDateErrorDiv").html(response.responseText);
          });
        });

        $("#datepickerField").click(function() {
          $("#resetDateError, #resetDateCheck, #resetDateErrorDiv").hide()
          $("#resetDateDiv").removeClass("has-success");
          $("#resetDateDiv").removeClass("has-error");
          $("#addResetDate").show();
        });
      });
    </script>
    <div>
    <div class="highlight resultMessageDiv" id="resetDateErrorDiv"></div>

      <p class="editTextDate"><strong><%=encprops.getProperty("resetEncounterDate")%></strong></p>
      <form name="setencdate" class="editFormDate">
        <input name="number" type="hidden" value="<%=num%>" id="resetDateNumber" />
        <input name="action" type="hidden" value="changeEncounterDate"/>
        <div id="datepicker" class="editFormDate"></div>
        <div class="form-group row editFormDate">
          <div class="col-sm-5">
            <label><%=encprops.getProperty("setDate")%> (yyyy-MM-dd HH:mm)</label>
            <p><font size="-1"><%=encprops.getProperty("leaveBlank")%></font></p>
          </div>
          <div class="col-sm-5" id="resetDateDiv">
            <input type="text" id="datepickerField" name="datepicker" class="form-control" />
            <span class="form-control-feedback" id="resetDateCheck">&check;</span>
            <span class="form-control-feedback" id="resetDateError">X</span>
          </div>
          <div class="col-sm-2">
            <input name="AddDate" type="submit" id="addResetDate" value="<%=encprops.getProperty("setDate")%>" class="btn btn-sm editFormBtn"/>
          </div>
        </div>
      </form>
    </div>


    <!-- start releaseDate -->
    <script type="text/javascript">
      $(document).ready(function() {
        $("#AddDate").click(function(event) {
          event.preventDefault();

          $("#AddDate").hide();

          var encounter = $("#releaseDateEncounter").val();
          var releasedatepicker = $("#releasedatepickerField").val();

          $.post("../EncounterSetReleaseDate", {"encounter": encounter, "releasedatepicker": releasedatepicker},
          function() {
            $("#releaseErrorDiv").hide();
            $("#releaseDiv").addClass("has-success");
            $("#releaseCheck").show();
            $("#displayReleaseDate").html(releasedatepicker);
          })
          .fail(function(response) {
            $("#releaseDiv").addClass("has-error");
            $("#releaseError, #releaseErrorDiv").show();
            $("#releaseErrorDiv").html(response.responseText);
          });
        });

        $("#releasedatepickerField").click(function() {
          $("#releaseError, #releaseCheck, #releaseErrorDiv").hide()
          $("#releaseDiv").removeClass("has-success");
          $("#releaseDiv").removeClass("has-error");
          $("#AddDate").show();
        });
      });
    </script>
	<c:if test="${showReleaseDate}">
    <div>
      <div class="highlight resultMessageDiv" id="releaseErrorDiv"></div>

      <p class="editTextDate"><strong><%=encprops.getProperty("setReleaseDate")%></strong></p>
      <form name="setReleaseDate" class="editFormDate">
        <input type="hidden" name="encounter" value="${num}" id="releaseDateEncounter"/>
        <div id="releasedatepicker" class="editFormDate"></div>
        <div class="form-group row editFormDate">
          <div class="col-sm-4">
            <label><%=encprops.getProperty("setReleaseDate")%></label>
            <p><font size="-1"><%=encprops.getProperty("leaveBlank")%></font></p>
          </div>
          <div class="col-sm-5" id="releaseDiv">
            <input type="text" id="releasedatepickerField" name="releasedatepicker" class="form-control" />
            <span class="form-control-feedback" id="releaseCheck">&check;</span>
            <span class="form-control-feedback" id="releaseError">X</span>
          </div>
          <div class="col-sm-3">
            <input name="AddDate" type="submit" id="AddDate" value="<%=encprops.getProperty("setReleaseDate")%>" class="btn btn-sm editText"/>
          </div>
        </div>
      </form>

    </div>
    </c:if>
    <!-- end releaseDate -->

    <br>
    <!-- start verbatim event date -->
    <script type="text/javascript">
      $(document).ready(function() {
        $("#setVerbatimEventDateBtn").click(function(event) {
          event.preventDefault();

          $("#setVerbatimEventDateBtn").hide();

          var encounter = $("#verbatimDateEncounter").val();
          var verbatimEventDate = $("#verbatimEventDateInput").val();

          $.post("../EncounterSetVerbatimEventDate", {"encounter": encounter, "verbatimEventDate": verbatimEventDate},
          function() {
            $("#verbatimErrorDiv").hide();
            $("#verbatimDiv").addClass("has-success");
            $("#verbatimCheck").show();
            $("#displayVerbatimDate").html(verbatimEventDate);
          })
          .fail(function(response) {
            $("#verbatimDiv").addClass("has-error");
            $("#verbatimError, #verbatimErrorDiv").show();
            $("#verbatimErrorDiv").html(response.responseText);
          });
        });

        $("#verbatimEventDateInput").click(function() {
          $("#verbatimError, #verbatimCheck, #verbatimErrorDiv").hide()
          $("#verbatimDiv").removeClass("has-success");
          $("#verbatimDiv").removeClass("has-error");
          $("#setVerbatimEventDateBtn").show();
        });
      });
    </script>
    <div>
      <div class="highlight resultMessageDiv" id="verbatimErrorDiv"></div>

      <p class="editTextDate"><strong><%=encprops.getProperty("setVerbatimEventDate")%></strong></p>
      <form name="setVerbatimEventDate" class="editFormDate">
        <input name="encounter" type="hidden" value="<%=num%>" id="verbatimDateEncounter">
        <div class="form-group row">
          <div class="col-sm-4">
            <label><%=encprops.getProperty("setVerbatimEventDate")%>:</label>
            <p><font size="-1"><%=encprops.getProperty("leaveBlank")%></font></p>
          </div>
          <div class="col-sm-5 col-xs-10" id="verbatimDiv">
            <input name="verbatimEventDate" type="text" class="form-control" id="verbatimEventDateInput">
              <span class="form-control-feedback" id="verbatimCheck">&check;</span>
              <span class="form-control-feedback" id="verbatimError">X</span>
            </div>
            <div class="col-sm-3">
              <input name="Set" type="submit" id="setVerbatimEventDateBtn" value="<%=encprops.getProperty("set")%>" class="btn btn-sm editFormBtn">
            </div>
          </div>
        </form>
    </div>



    </td>
    </tr>
    </table>
<%
String queryString="SELECT FROM org.ecocean.Encounter WHERE catalogNumber == \""+num+"\"";
%>
    <%-- START IMAGES --%>
        <jsp:include page="encounterMediaGallery.jsp" flush="true">
        	<jsp:param name="encounterNumber" value="<%=num%>" />
        	<jsp:param name="queryString" value="<%=queryString%>" />

        	<jsp:param name="isOwner" value="<%=isOwner %>" />
        	<jsp:param name="loggedIn" value="<%=loggedIn %>" />
      	</jsp:include>

		<%
		if(isOwner){
		%>
	        <div id="add-image-zone" class="bc4">

	          <h2 style="text-align:left"><%=encprops.getProperty("addImage") %></h2>

	          <div class="flow-box bc4" style="text-align:center" >

	            <div id="file-activity" style="display:none"></div>

	            <div id="updone"></div>

	            <div id="upcontrols">
	              <input type="file" id="file-chooser" multiple accept="audio/*,video/*,image/*" onChange="return filesChanged(this)" />
	              <div id="flowbuttons">

	                <button id="reselect-button" class="btn" style="display:none">choose a different image</button>
	                <button id="upload-button" class="btn" style="display:none">begin upload</button>

	              </div>
	            </div>
	          </div>
	        </div>
        <%
        }
        %>
    <%-- END IMAGES --%>


      <%

  String isLoggedInValue="true";
  String isOwnerValue="true";

  if(!loggedIn){isLoggedInValue="false";}
  if(!isOwner){isOwnerValue="false";}

%>



<br />



  <br /><br />



<%-- OBSERVATION ATTRIBUTES --%>
  <% if (isOwner && CommonConfiguration.isCatalogEditable(context)) { %>
  <h2>
    <img align="absmiddle" src="../images/Note-Book-icon.png" width="40px" height="40px" /> <%=encprops.getProperty("observationAttributes") %>
    <button class="btn btn-md" type="button" name="button" id="editObservation">Edit</button>
    <button class="btn btn-md" type="button" name="button" id="closeEditObservation" style="display:none;">Close Edit</button>
  </h2>



  <script type="text/javascript">
  $(document).ready(function() {
    var buttons = $("#editObservation, #closeEditObservation").on("click", function(){
      buttons.toggle();
    });
    $("#editObservation").click(function() {
      $("#taxCheck, #taxError, #statusCheck, #statusError, #sexCheck, #sexError, #scarCheck, #scarError, #behaviorCheck, #behaviorError, #lifeCheck, #lifeError, #commentCheck, #commentError, #patternCheck, #patternError").hide();

      $(".editFormObservation, #taxBtn, #addStatus, #addSex, #addScar, #editPattern, #editBehavior, #addLife, #editComment").show();

    });

    $("#closeEditObservation").click(function() {
      $(".editFormObservation, .editTextObservation, .resultMessageDiv").hide();
    });
  });
  </script>


  <% }
  else {
   %>
   <h2><img align="absmiddle" src="../images/Note-Book-icon.png" width="40px" height="40px" /> <%=encprops.getProperty("observationAttributes") %></h2>
   <%}%>







<!-- START TAXONOMY ATTRIBUTE -->
<%
    if(CommonConfiguration.showProperty("showTaxonomy",context)){

    String genusSpeciesFound=encprops.getProperty("notAvailable");
    if((enc.getGenus()!=null)&&(enc.getSpecificEpithet()!=null)){genusSpeciesFound=enc.getGenus()+" "+enc.getSpecificEpithet();}
    %>

        <p class="para"><img align="absmiddle" src="../images/taxontree.gif">
          <%=encprops.getProperty("taxonomy")%>:<em><span id="displayTax"><%=genusSpeciesFound%></span></em>&nbsp;<%
          %>
          <%
          %>
       </p>

  <%
    %>
  <!-- start set taxonomy ID  -->
    <script type="text/javascript">
    function updateAssetsTaxonomy(newValue) {
        if (!assets || (assets.length < 1)) return;
        for (var i = 0 ; i < assets.length ; i++) {
            assets[i].taxonomyString = newValue;
        }
    }
      $(document).ready(function() {
        $("#taxBtn").click(function(event) {
          event.preventDefault();

          $("#taxBtn").hide();

          var encounter = $("#taxNumber").val();
          var genusSpecies = $("#genusSpecies").val();

          $.post("../EncounterSetGenusSpecies", {"encounter": encounter, "genusSpecies": genusSpecies},
          function() {
            $("#taxErrorDiv").hide();
            $("#taxCheck").show();
            $("#displayTax").html(genusSpecies);
            updateAssetsTaxonomy(genusSpecies);
          })
          .fail(function(response) {
            $("#taxError, #taxErrorDiv").show();
            $("#taxErrorDiv").html(response.responseText);
          });
        });

        $("#genusSpecies").click(function() {
          $("#taxerror, #taxCheck, #taxErrorDiv").hide()
          $("#taxBtn").show();
        });
      });
    </script>

    <div>
      <div class="highlight resultMessageDiv" id="taxErrorDiv"></div>

      <p class="editTextObservation"><strong><%=encprops.getProperty("resetTaxonomy")%></strong></p>

      <form name="taxonomyForm" class="editFormObservation">
        <input name="encounter" type="hidden" value="<%=num%>" id="taxNumber">
        <div class="form-group row">
          <div class="col-sm-5">
            <select name="genusSpecies" id="genusSpecies" class="form-control" size="1">
              <option value="unknown"><%=encprops.getProperty("notAvailable")%></option>

              <%
              boolean hasMoreTax=true;
              int genusTaxNum=0;
              while(hasMoreTax){
                String currentGenuSpecies = "genusSpecies"+genusTaxNum;
                if(CommonConfiguration.getProperty(currentGenuSpecies,context)!=null){
                  %>

                  <option value="<%=CommonConfiguration.getProperty(currentGenuSpecies,context)%>"><%=CommonConfiguration.getProperty(currentGenuSpecies,context).replaceAll("_"," ")%></option>
                  <%
                  genusTaxNum++;
                }
                else{
                  hasMoreTax=false;
                }

              }
              %>
            </select>
          </div>
          <div class="col-sm-3">
            <input name="<%=encprops.getProperty("set")%>" type="submit" id="taxBtn" value="<%=encprops.getProperty("set")%>" class="btn btn-sm editFormBtn"/>
            <span class="form-control-feedback" id="taxCheck">&check;</span>
            <span class="form-control-feedback" id="taxError">X</span>
          </div>
        </div>
      </form>
    </div>
<%

}
%>
<!-- END TAXONOMY ATTRIBUTE -->


<!-- START ALIVE-DEAD ATTRIBUTE -->
<p class="para">
      <%=encprops.getProperty("status")%>:
      <%
      if(enc.getLivingStatus()!=null){
      %>
      <span id="displayStatus"><%=enc.getLivingStatus()%></span>
       <%
    }
      %>
      <%
      %>
    </p>
    <%
    %>
<!-- start set living status -->
  <script type="text/javascript">
    $(document).ready(function() {
      $("#addStatus").click(function(event) {
        event.preventDefault();

        $("#addStatus").hide();

        var encounter = $("#statusNumber").val();
        var livingStatus = $("#livingStatus").val();

        $.post("../EncounterSetLivingStatus", {"encounter": encounter, "livingStatus": livingStatus},
        function() {
          $("#statusErrorDiv").hide();
          $("#statusCheck").show();
          $("#displayStatus").html(livingStatus);
        })
        .fail(function(response) {
          $("#statusError, #statusErrorDiv").show();
          $("#statusErrorDiv").html(response.responseText);
        });
      });

      $("#genusSpecies").click(function() {
        $("#statuserror, #statusCheck, #statusErrorDiv").hide()
        $("#addStatus").show();
      });
    });
  </script>


  <div>
    <div class="highlight resultMessageDiv" id="statusErrorDiv"></div>

    <p class="editTextObservation"><strong><%=encprops.getProperty("resetStatus")%></strong></p>

    <form name="livingStatusForm" class="editFormObservation">
      <input name="encounter" type="hidden" value="<%=num%>" id="statusNumber" />
      <div class="form-group row">
        <div class="col-sm-5">
          <select name="livingStatus" id="livingStatus" class="form-control" size="1">
            <option value="alive" selected><%=encprops.getProperty("alive")%></option>
            <option value="dead"><%=encprops.getProperty("dead")%></option>
          </select>
        </div>
        <div class="col-sm-3">
          <input name="Add" type="submit" id="addStatus" value="<%=encprops.getProperty("resetStatus")%>" class="btn btn-sm"/>
          <span class="form-control-feedback" id="statusCheck">&check;</span>
          <span class="form-control-feedback" id="statusError">X</span>
        </div>
      </div>
    </form>
  </div>

<%
%>
<!-- END ALIVE-DEAD ATTRIBUTE -->


<!--  START SEX SECTION -->
<%
String sex="";
if(enc.getSex()!=null){sex=enc.getSex();}
%>
<p class="para"><%=encprops.getProperty("sex") %>&nbsp;<span id="displaySex"><%=sex %></span>
<%
 %>
<%
%>
</p>
<%
%>
<script type="text/javascript">
  $(document).ready(function() {
    $("#addSex").click(function(event) {
      event.preventDefault();

      $("#addSex").hide();

      var action = $("#sexAction").val();
      var number = $("#sexNumber").val();
      var selectSex = $("#selectSex").val();

      $.post("../EncounterSetSex", {"action": action, "number": number, "selectSex": selectSex},
      function() {
        $("#sexErrorDiv").hide();
        $("#sexCheck").show();
        $("#displaySex").html(selectSex);
      })
      .fail(function(response) {
        $("#sexError, #sexErrorDiv").show();
        $("#sexErrorDiv").html(response.responseText);
      });
    });

    $("#selectSex").click(function() {
      $("#sexerror, #sexCheck, #sexErrorDiv").hide()
      $("#addSex").show();
    });
  });
</script>


<div>
  <div class="highlight resultMessageDiv" id="sexErrorDiv"></div>

  <p class="editTextObservation"><strong><%=encprops.getProperty("resetSex")%></strong></p>

  <form name="setxencshark" class="editFormObservation">
    <input name="number" type="hidden" value="<%=num%>" id="sexNumber" />
    <input name="action" type="hidden" value="setEncounterSex" id="sexAction"/>
    <div class="form-group row">
      <div class="col-sm-5">
        <select name="selectSex" size="1" id="selectSex" class="form-control">
          <option value="unknown" selected><%=encprops.getProperty("unknown")%>
          </option>
          <option value="male"><%=encprops.getProperty("male")%>
          </option>
          <option value="female"><%=encprops.getProperty("female")%>
          </option>
        </select>
      </div>
      <div class="col-sm-3">
        <input name="Add" type="submit" id="addSex" value="<%=encprops.getProperty("resetSex")%>" class="btn btn-sm editFormBtn"/>
        <span class="form-control-feedback" id="sexCheck">&check;</span>
        <span class="form-control-feedback" id="sexError">X</span>
      </div>
    </div>
  </form>
</div>

<%
 %>
 <!--  END SEX SECTION -->


<!--  START SCARRING SECTION -->
<p class="para"><%=encprops.getProperty("scarring") %>&nbsp;

<%
String recordedScarring="";
if(enc.getDistinguishingScar()!=null){recordedScarring=enc.getDistinguishingScar();}
%>
<span id="displayScarring"><%=recordedScarring%></span>
<%
 %>
<%
%>
</p>
<%
 %>

 <script type="text/javascript">
   $(document).ready(function() {
     $("#addScar").click(function(event) {
       event.preventDefault();

       $("#addScar").hide();

       var number = $("#scarNumber").val();
       var scars = $("#scarInput").val();

       $.post("../EncounterSetScarring", {"number": number, "scars": scars},
       function() {
         $("#scarErrorDiv").hide();
         $("#scarCheck").show();
         $("#displayScarring").html(scars);
       })
       .fail(function(response) {
         $("#scarerror, #scarErrorDiv").show();
         $("#scarErrorDiv").html(response.responseText);
       });
     });

     $("#scarInput").click(function() {
       $("#scarerror, #scarCheck, #scarErrorDiv").hide()
       $("#addScar").show();
     });
   });
 </script>
 <div>
   <div class="highlight resultMessageDiv" id="scarErrorDiv"></div>

   <p class="editTextObservation"><strong><%=encprops.getProperty("editScarring")%></strong></p>
   <form name="setencsize" class="editFormObservation">
     <input name="number" type="hidden" value="<%=num%>" id="scarNumber" />
     <input name="action" type="hidden" value="setScarring" id="scarAction"/>

   <div class="form-group row">
     <div class="col-sm-5">
       <textarea name="scars" class="form-control" id="scarInput"><%=enc.getDistinguishingScar()%></textarea>
     </div>
     <div class="col-sm-3">
       <input name="Add" type="submit" id="addScar" value="<%=encprops.getProperty("resetScarring")%>" class="btn btn-sm"/>
       <span class="form-control-feedback" id="scarCheck">&check;</span>
       <span class="form-control-feedback" id="scarError">X</span>
     </div>
   </div>
   </form>
 </div>
    <%
 	%>
<!--  END SCARRING SECTION -->


<!--  START BEHAVIOR SECTION -->
<p class="para"><%=encprops.getProperty("behavior") %>&nbsp;

  <%
    if (enc.getBehavior() != null) {
  %>
  <span id="displayBehavior"><%=enc.getBehavior()%></span>
  <%
  } else {
  %>
  <span id="displayBehavior"><%=encprops.getProperty("none")%></span>
  <%
    }
	  %>
	  <%
%>
</p>


  <%
    %>
    <!-- start set behavior popup -->
    <script type="text/javascript">
      $(document).ready(function() {
        $("#editBehavior").click(function(event) {
          event.preventDefault();

          $("#editBehavior").hide();

          var number = $("#behaviorNumber").val();
          var behaviorComment = $("#behaviorInput").val();

          $.post("../EncounterSetBehavior", {"number": number, "behaviorComment": behaviorComment},
          function() {
            $("#behaviorErrorDiv").hide();
            $("#behaviorCheck").show();
            $("#displayBehavior").html(behaviorComment);
          })
          .fail(function(response) {
            $("#behaviorError, #behaviorErrorDiv").show();
            $("#behaviorErrorDiv").html(response.responseText);
          });
        });

        $("#behaviorInput").click(function() {
          $("#behaviorError, #behaviorCheck, #behaviorErrorDiv").hide()
          $("#editBehavior").show();
        });
      });
    </script>
    <div>
      <div class="highlight resultMessageDiv" id="behaviorErrorDiv"></div>

      <p class="editTextObservation"><strong><%=encprops.getProperty("editBehaviorComments")%></strong></p>
      <span class="editTextObservation"><em><font size="-1"><%=encprops.getProperty("leaveBlank")%></font></em></span>
      <form name="setBehaviorComments" class="editFormObservation">
        <input name="number" type="hidden" value="<%=num%>" id="behaviorNumber"/>
        <input name="action" type="hidden" value="editBehavior" id="behaviorAction"/>

      <div class="form-group row">
        <div class="col-sm-5">
          <textarea name="behaviorComment" class="form-control" id="behaviorInput">
            <%
           if((enc.getBehavior()!=null)&&(!enc.getBehavior().trim().equals(""))){
           %>
              <%=enc.getBehavior().trim()%>
          <%
          }
          %>
          </textarea>
        </div>
        <div class="col-sm-3">
          <input name="EditBeh" type="submit" id="editBehavior" value="<%=encprops.getProperty("submitEdit")%>" class="btn btn-sm"/>
          <span class="form-control-feedback" id="behaviorCheck">&check;</span>
          <span class="form-control-feedback" id="behaviorError">X</span>
        </div>
      </div>
      </form>
    </div>

<%
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
  <span id="displayPattern"><%=enc.getPatterningCode()%></span>
  <%
  } else {
  %>
  <%=encprops.getProperty("none")%>
  <%
    }
	  %>

	  <%
%>
</p>


  <%
    %>
<!-- start set patterning code -->
<script type="text/javascript">
  $(document).ready(function() {
    $("#editPattern").click(function(event) {
      event.preventDefault();

      $("#editPattern").hide();

      var number = $("#sexNumber").val();
      var patterningCode = $("#colorCode").val();

      $.post("../EncounterSetPatterningCode", {"number": number, "patterningCode": patterningCode},
      function() {
        $("#patternErrorDiv").hide();
        $("#patternCheck").show();
        $("#displayPattern").html(patterningCode);
      })
      .fail(function(response) {
        $("#patternError, #patternErrorDiv").show();
        $("#patternErrorDiv").html(response.responseText);
      });
    });

    $("#colorCode").click(function() {
      $("#patternerror, #patternCheck, #patternErrorDiv").hide()
      $("#editPattern").show();
    });
  });
</script>


<div>
  <div class="highlight resultMessageDiv" id="patternErrorDiv"></div>

  <p class="editTextObservation"><strong><%=encprops.getProperty("editPatterningCode")%></strong></p>
  <span class="editTextObservation"><em><font size="-1"><%=encprops.getProperty("leaveBlank")%></font></em></span>

  <form name="setPatterningCode" class="editFormObservation">
    <input name="number" type="hidden" value="<%=num%>" id="patternNumber"/>
    <div class="form-group row">
      <div class="col-sm-5">
        <%
             if(CommonConfiguration.getProperty("patterningCode0",context)==null){
             %>
             <input name="patterningCode" type="text" class="form-control" id="colorCode"/>
             <%
             }
             else{
               //iterate and find the locationID options
               %>
               <select name="patterningCode" id="colorCode" class="form-control" size="1">
                         <option value=""></option>

                  <%
                  boolean hasMoreLocs=true;
                  int patternTaxNum=0;
                  while(hasMoreLocs){
                     String currentLoc = "patterningCode"+patternTaxNum;
                     if(CommonConfiguration.getProperty(currentLoc,context)!=null){
                       %>

                         <option value="<%=CommonConfiguration.getProperty(currentLoc,context)%>"><%=CommonConfiguration.getProperty(currentLoc,context)%></option>
                       <%
                     patternTaxNum++;
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
      </div>
      <div class="col-sm-3">
        <input name="EditPC" type="submit" id="editPattern" value="<%=encprops.getProperty("submitEdit")%>" class="btn btn-sm"/>
        <span class="form-control-feedback" id="patternCheck">&check;</span>
        <span class="form-control-feedback" id="patternError">X</span>
      </div>
    </div>
  </form>
</div>



<%
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
  <span id="displayLife"><%=enc.getLifeStage()%></span>
  <%
  }
 %>
 <%
  %>
  <%
  %>
</p>

 <%
    %>
<!-- start set life stage -->
<script type="text/javascript">
  $(document).ready(function() {
    $("#addLife").click(function(event) {
      event.preventDefault();

      $("#addLife").hide();

      var encounter = $("#lifeEncounter").val();
      var lifeStage = $("#lifeStage").val();

      $.post("../EncounterSetLifeStage", {"encounter": encounter, "lifeStage": lifeStage},
      function() {
        $("#lifeErrorDiv").hide();
        $("#lifeCheck").show();
        $("#displayLife").html(lifeStage);
      })
      .fail(function(response) {
        $("#lifeError, #lifeErrorDiv").show();
        $("#lifeErrorDiv").html(response.responseText);
      });
    });

    $("#lifeStage").click(function() {
      $("#lifeerror, #lifeCheck, #lifeErrorDiv").hide()
      $("#addLife").show();
    });
  });
</script>


<div>
  <div class="highlight resultMessageDiv" id="lifeErrorDiv"></div>

  <p class="editTextObservation"><strong><%=encprops.getProperty("resetLifeStage")%></strong></p>

  <form name="lifeStageForm" class="editFormObservation">
      <input name="encounter" type="hidden" value="<%=num%>" id="lifeEncounter"/>
    <div class="form-group row">
      <div class="col-sm-5">
        <select name="lifeStage" id="lifeStage" class="form-control" size="1">
          <option value=""></option>
             <%
             boolean hasMoreStages=true;
             int lifeTaxNum=0;
             while(hasMoreStages){
                String currentLifeStage = "lifeStage"+lifeTaxNum;
                if(CommonConfiguration.getProperty(currentLifeStage,context)!=null){
                  %>

                    <option value="<%=CommonConfiguration.getProperty(currentLifeStage,context)%>"><%=CommonConfiguration.getProperty(currentLifeStage,context)%></option>
                  <%
                lifeTaxNum++;
                }
                else{
                   hasMoreStages=false;
                }

             }
             %>
        </select>
      </div>
      <div class="col-sm-3">
        <input name="<%=encprops.getProperty("set")%>" type="submit" id="addLife" value="<%=encprops.getProperty("set")%>" class="btn btn-sm editFormBtn"/>
        <span class="form-control-feedback" id="lifeCheck">&check;</span>
        <span class="form-control-feedback" id="lifeError">X</span>
      </div>
    </div>
  </form>
</div>


<%
}
  %>
<!--  END LIFESTAGE SECTION -->

<!-- START ADDITIONAL COMMENTS -->
<p class="para"><%=encprops.getProperty("comments") %>
  <%
  %>
  <%
  %>
<br/>
<%
String recordedComments="";
if(enc.getComments()!=null){recordedComments=enc.getComments();}
%>
<em><span id="displayComment"><%=recordedComments%></span></em>

</p>
<%
%>

<script type="text/javascript">
  $(document).ready(function() {
    $("#editComment").click(function(event) {
      event.preventDefault();

      $("#editComment").hide();

      var number = $("#commentNumber").val();
      var fixComment = $("#commentInput").val();

      $.post("../EncounterSetOccurrenceRemarks", {"number": number, "fixComment": fixComment},
      function() {
        $("#commentErrorDiv").hide();
        $("#commentCheck").show();
        $("#displayComment").html(fixComment);
      })
      .fail(function(response) {
        $("#commentError, #commentErrorDiv").show();
        $("#commentErrorDiv").html(response.responseText);
      });
    });

    $("#commentInput").click(function() {
      $("#commentError, #commentCheck, #commentErrorDiv").hide()
      $("#editComment").show();
    });
  });
</script>
<div>
  <div class="highlight resultMessageDiv" id="commentErrorDiv"></div>

  <p class="editTextObservation"><strong><%=encprops.getProperty("editSubmittedComments")%></strong></p>
  <form name="setComments" class="editFormObservation">
    <input name="number" type="hidden" value="<%=num%>" id="commentNumber"/>
    <input name="action" type="hidden" value="editComments" id="commentAction"/>
  <div class="form-group row">
    <div class="col-sm-5">
      <textarea name="fixComment" class="form-control" id="commentInput"><%=enc.getComments()%></textarea>
    </div>
    <div class="col-sm-3">
      <input name="EditComm" type="submit" id="editComment" value="<%=encprops.getProperty("submitEdit")%>" class="btn btn-sm"/>
      <span class="form-control-feedback" id="commentCheck">&check;</span>
      <span class="form-control-feedback" id="commentError">X</span>
    </div>
  </div>
  </form>
</div>
<%
%>
<!-- END ADDITIONAL COMMENTS -->

<br />



<!-- START DYNAMIC PROPERTIES -->

<% if (isOwner && CommonConfiguration.isCatalogEditable(context)) { %>
<h2>
  <img align="absmiddle" src="../images/lightning_dynamic_props.gif" />
   <%=encprops.getProperty("dynamicProperties") %>
   <button class="btn btn-md" type="button" name="button" id="editDynamic">Edit</button>
   <button class="btn btn-md" type="button" name="button" id="closeEditDynamic" style="display:none;">Close Edit</button>
 </h2>



<script type="text/javascript">
$(document).ready(function() {
  var buttons = $("#editDynamic, #closeEditDynamic").on("click", function(){
    buttons.toggle();
  });
  $("#editDynamic").click(function() {
    $(".editFormDynamic").show();

  });

  $("#closeEditDynamic").click(function() {
    $(".editFormDynamic, .editTextDynamic, .resultMessageDiv").hide();
  });
});
</script>


<% }
else {
 %>
 <h2><img align="absmiddle" src="../images/lightning_dynamic_props.gif" /> <%=encprops.getProperty("dynamicProperties") %></h2>

 <%}%>




<%
if(isOwner){
%>

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
  %>

  <%
  %>

  <%
%>
<!-- start dynamic form -->
<div id="dialogDP<%=nm %>" title="<%=encprops.getProperty("set")%> <%=nm %>" class="editFormDynamic">
  <p class="editTextDynamic"><strong><%=encprops.getProperty("set")%> <%=nm %></strong></p>
  <p class="editTextDynamic"><em><small><%=encprops.getProperty("setDPMessage") %></small></em></p>

        <form name="addDynProp" action="../EncounterSetDynamicProperty" method="post" class="editFormDynamic">
          <input name="name" type="hidden" size="10" value="<%=nm %>" />
          <input name="number" type="hidden" value="<%=num%>" />
          <div class="form-group row">
            <div class="col-sm-3">
              <label><%=encprops.getProperty("propertyValue")%>:</label>
            </div>
            <div class="col-sm-5">
              <input name="value" type="text" class="form-control" id="dynInput" value="<%=vl %>"/>
            </div>
            <div class="col-sm-4">
              <input name="Set" type="submit" id="dynEdit" value="<%=encprops.getProperty("initCapsSet")%>" class="btn btn-sm editFormBtn"/>
            </div>
          </div>
        </form>

</div>

<%
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

%>
<div id="dialogDPAdd" title="<%=encprops.getProperty("addDynamicProperty")%>" class="editFormDynamic">
  <p class="editTextDynamic"><strong><%=encprops.getProperty("addDynamicProperty")%></strong></p>
    <form name="addDynProp" action="../EncounterSetDynamicProperty" method="post" class="editFormDynamic">
      <input name="number" type="hidden" value="<%=num%>" />
      <div class="form-group row">
        <div class="col-sm-3">
          <label><%=encprops.getProperty("propertyName")%>:</label>
        </div>
        <div class="col-sm-5">
          <input name="name" type="text" class="form-control" id="addDynPropInput"/>
        </div>
      </div>
      <div class="form-group row">
        <div class="col-sm-3">
          <label><%=encprops.getProperty("propertyValue")%>:</label>
        </div>
        <div class="col-sm-5">
          <input name="value" type="text" class="form-control" id="addDynPropInput2"/>
        </div>
      </div>
      <input name="Set" type="submit" id="addDynPropBtn" value="<%=encprops.getProperty("initCapsSet")%>" class="btn btn-sm editFormBtn"/>
    </form>
</div>


<%

%>


  </div>



</div>
</div>

<!-- end two columns here -->

<script src="../tools/flow.min.js"></script>
<style>

div#add-image-zone {
  background-color: #e8e8e8;
  margin-bottom: 8px;
  padding: 13px;
}

div#file-activity {
	font-family: sans;
  padding-top: 8px;
	padding-bottom: 8px;
	margin: 0px;
	min-height: 20px;
  border-radius: 5px;
}
div.file-item {
	position: relative;
	background-color: #DDD;
	border-radius: 3px;
	margin: 2px;
}

div.file-item div {
	display: inline-block;
	padding: 3px 7px;
}
.file-size {
	width: 10%;
}

.file-bar {
	position: absolute;
	width: 0;
	height: 100%;
	padding: 0 !important;
	left: 0;
	border-radius: 3px;
	background-color: rgba(100,100,100,0.3);
}

#flowbuttons {
  width: 100%;
  margin-left:1px;
  margin-right:1px;
}
#flowbuttons button {
  width:48%;
}
#flowbuttons button:first-child {
  float: left;
  margin-right: 2%;
}

#flowbuttons button:hover {
  background-color: #fff;
  border-color: #fff;
  color:  #005589;
}

button#upload-button {
  margin-right: 0px;
}

#upcontrols {
  width: 100%;
  padding-bottom: 8px;
}

</style>

<script>

  var keyToFilename = {};
  var filenames = [];
  var pendingUpload = -1;

  $("button#add-image").click(function(){$(".flow-box").show()})


  console.info("uploader is using uploading direct to host (not S3)");
  var flow = new Flow({
    target:'../ResumableUpload',
    forceChunkSize: true,
    testChunks: false,
  });

  flow.assignBrowse(document.getElementById('file-chooser'));

  flow.on('fileAdded', function(file, event){
    $('#file-activity').show();
    console.log('added %o %o', file, event);
  });
  flow.on('fileProgress', function(file, chunk){
    var el = findElement(file.name, file.size);
    var p = ((file._prevUploadedSize / file.size) * 100) + '%';
    updateProgress(el, p, 'uploading');
    console.log('progress %o %o', file._prevUploadedSize, file);
  });
  flow.on('fileSuccess', function(file,message){
    var el = findElement(file.name, file.size);
    updateProgress(el, -1, 'completed', 'rgba(200,250,180,0.3)');
    console.log('success %o %o', file, message);
    console.log('filename: '+file.name);
    filenames.push(file.name);
    pendingUpload--;
    if (pendingUpload == 0) uploadFinished();
  });
  flow.on('fileError', function(file, message){
    console.log('error %o %o', file, message);
    pendingUpload--;
    if (pendingUpload == 0) uploadFinished();
  });

  document.getElementById('upload-button').addEventListener('click', function(ev) {
    var files = flow.files;
    pendingUpload = files.length;
    for (var i = 0 ; i < files.length ; i++) {
        filenameToKey(files[i].name);
    }
    document.getElementById('upcontrols').style.display = 'none';
    console.log('#pendingUpload='+pendingUpload);
    flow.upload();
  }, false);

  document.getElementById('reselect-button').addEventListener('click', function(ev) {
    var files = flow.files;
    for (var i = 0 ; i < files.length ; i++) {
        console.info('flow.js removing file '+files[i].name);
        $("#file-item-"+i).hide();
        flow.removeFile(files[i]);
    }
    document.getElementById('upload-button').style.display = 'none';
    document.getElementById('reselect-button').style.display = 'none';
    document.getElementById('file-activity').style.display = 'none';
    $('#file-chooser').show();
    pendingUpload = flow.files.length;
    console.log('#pendingUpload='+pendingUpload);
  }, false);


  function filesChanged(f) {
  	var h = '';
  	for (var i = 0 ; i < f.files.length ; i++) {
  		h += '<div class="file-item" id="file-item-' + i + '" data-i="' + i + '" data-name="' + f.files[i].name + '" data-size="' + f.files[i].size + '"><div class="file-name">' + f.files[i].name + '</div><div class="file-size">' + niceSize(f.files[i].size) + '</div><div class="file-status"></div><div class="file-bar"></div></div>';
  	}
  	document.getElementById('file-activity').innerHTML = h;
    $('#file-chooser').hide();
    $('#upload-button').show();
    $('#reselect-button').show();
  }
  function niceSize(s) {
  	if (s < 1024) return s + 'b';
  	if (s < 1024*1024) return Math.floor(s/1024) + 'k';
  	return Math.floor(s/(1024*1024) * 10) / 10 + 'M';
  }
  function updateProgress(el, width, status, bg) {
  	if (!el) {console.info("quick return");return;}
  	var els = el.children;
  	if (width < 0) {  //special, means 100%
  		els[3].style.width = '100%';
  	} else if (width) {
  		els[3].style.width = width;
  	}
  	if (status) els[2].innerHTML = status;
  	if (bg) els[3].style.backgroundColor = bg;
  }
  function filenameToKey(fname) {
      var key = fname;
      keyToFilename[key] = fname;
      console.info('key = %s', key);
      return key;
  }

  function findElement(key, size) {
          var name = keyToFilename[key];
          if (!name) {
              console.warn('could not find filename for key %o; bailing!', key);
              return false;
          }
  	var items = document.getElementsByClassName('file-item');
  	for (var i = 0 ; i < items.length ; i++) {
  		if ((name == items[i].getAttribute('data-name')) && ((size < 0) || (size == items[i].getAttribute('data-size')))) return items[i];
  	}
  	return false;
  }
  function uploadFinished() {
  	document.getElementById('updone').innerHTML = '<i>Upload complete. Refresh page to see new image.</i>';
    console.log("upload finished.");
    console.log('upload finished. Files added: '+filenames);

    if (filenames.length > 0) {
      console.log("creating mediaAsset for filename "+filenames[0]);
      $.ajax({
        url: '../MediaAssetCreate',
        type: 'POST',
        dataType: 'json',
        contentType: 'application/javascript',
        data: JSON.stringify({
          "MediaAssetCreate": [
            {"assets": [
               {"filename": filenames[0] }
              ]
            }
          ]
        }),
        success: function(d) {
          console.info('Success! Got back '+JSON.stringify(d));
          var maId = d.withoutSet[0].id;
          console.info('parsed id = '+maId);

          var ajaxData = {"attach":"true","EncounterID":"<%=encNum%>","MediaAssetID":maId};
          var ajaxDataString = JSON.stringify(ajaxData);
          console.info("ajaxDataString="+ajaxDataString);


          $.ajax({
            url: '../MediaAssetAttach',
            type: 'POST',
            dataType: 'json',
            contentType: "application/json",
            data: ajaxDataString,
            success: function(d) {
              console.info("I attached MediaAsset "+maId+" to encounter <%=encNum%>");
            },
            error: function(x,y,z) {
              console.warn("failed to MediaAssetAttach");
              console.warn('%o %o %o', x, y, z);
            }
          });

        },
        error: function(x,y,z) {
          console.warn('%o %o %o', x, y, z);
        },
      });

    }
  }


  </script>







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
<script type="text/javascript">
  $(document).ready(function() {
    $(".addBioSample").click(function() {
      $("#dialogSample").toggle();
    });
  });
</script>


<hr />
<a name="tissueSamples"></a>
<p class="para"><img align="absmiddle" src="../images/microscope.gif" />
    <strong><%=encprops.getProperty("tissueSamples") %></strong>
</p>
    <p class="para">
    	<a class="addBioSample toggleBtn" class="launchPopup"><img align="absmiddle" width="24px" style="border-style: none;" src="../images/Crystal_Clear_action_edit_add.png" /></a>&nbsp;<a class="addBioSample toggleBtn" class="launchPopup"><%=encprops.getProperty("addTissueSample") %></a>
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
						       int tissueTaxNum=0;
						       while(hasMoreLocs){
						       	  String currentLoc = "tissueType"+tissueTaxNum;
						       	  if(CommonConfiguration.getProperty(currentLoc,context)!=null){

						       		  String selected="";
						       		  if(tissueType.equals(CommonConfiguration.getProperty(currentLoc,context))){selected="selected=\"selected\"";}
						       	  	%>

						       	  	  <option value="<%=CommonConfiguration.getProperty(currentLoc,context)%>" <%=selected %>><%=CommonConfiguration.getProperty(currentLoc,context)%></option>
						       	  	<%
						       		tissueTaxNum++;
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
            	<input name="EditTissueSample" type="submit" id="EditTissueSample" value="<%=encprops.getProperty("set")%>" class="btn btn-sm editFormBtn"/>
   			</td></tr>
      </td>
    </tr>
  </table>
</form>
</div>
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
<table width="100%" class="table table-bordered table-striped tissueSampleTable">
<tr><th><%=encprops.getProperty("sampleID") %></th><th><%=encprops.getProperty("values") %></th><th><%=encprops.getProperty("analyses") %></th><th><%=encprops.getProperty("editTissueSample") %></th><th><%=encprops.getProperty("removeTissueSample") %></th></tr>
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
					<a id="haplo<%=mito.getAnalysisID() %>" class="toggleBtn"><img width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a>

							<%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>
<!-- start haplotype popup -->
<script type="text/javascript">
  $(document).ready(function() {
    $("#haplo<%=mito.getAnalysisID() %>").click(function() {
      $("#dialogHaplotype<%=mito.getAnalysisID() %>").toggle();
    });
  });
</script>

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

<%-- <script>
var dlgHaplotype<%=mito.getAnalysisID() %> = $("#dialogHaplotype<%=mito.getAnalysisID() %>").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#haplo<%=mito.getAnalysisID() %>").click(function() {
  dlgHaplotype<%=mito.getAnalysisID() %>.dialog("open");

});
</script> --%>
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
				</span></td>
        <td style="border-style: none;">
          <a id="setSex<%=thisSample.getSampleID() %>" class="launchPopup"><img width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" />
        </a>

				<%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>
<!-- start genetic sex popup -->
<script type="text/javascript">
  $("#setSex<%=thisSample.getSampleID() %>").click(function() {
    $("#dialogSexSet<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>").toggle();
  });

</script>
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
<!-- end genetic sex popup -->
<%
}
%>

				</td>
				<td style="border-style: none;"><a onclick="return confirm('<%=encprops.getProperty("deleteGenetic") %>');" href="../TissueSampleRemoveSexAnalysis?encounter=<%=enc.getCatalogNumber()%>&sampleID=<%=thisSample.getSampleID()%>&analysisID=<%=mito.getAnalysisID() %>"><img style="border-style: none;width: 40px;height: 40px;" src="../images/cancel.gif" /></a></td></tr>
			<%
			}
			else if(ga.getAnalysisType().equals("MicrosatelliteMarkers")){
				MicrosatelliteMarkersAnalysis mito=(MicrosatelliteMarkersAnalysis)ga;

			%>
			<tr>
				<td style="border-style: none;">
					<p><span class="caption"><strong><%=encprops.getProperty("msMarkers") %></strong></span>
					<%
					if((enc.getIndividualID()!=null)&&(request.getUserPrincipal()!=null)){
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
				<td style="border-style: none;">
          <a class="launchPopup" id="msmarkersSet<%=thisSample.getSampleID()%>">
          <img width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" />
        </a>
      </td>
      <td style="border-style: none;">
        <a onclick="return confirm('<%=encprops.getProperty("deleteMSMarkers") %>');" href="../TissueSampleRemoveMicrosatelliteMarkers?encounter=<%=enc.getCatalogNumber()%>&sampleID=<%=thisSample.getSampleID()%>&analysisID=<%=mito.getAnalysisID() %>">
        <img style="border-style: none;width: 40px;height: 40px;" src="../images/cancel.gif" />
      </a>

															<%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>

<!-- start ms marker popup -->
<script type="text/javascript">
  $(document).ready(function() {
    $("#msmarkersSet<%=thisSample.getSampleID()%>").click(function() {
      $("#dialogMSMarkersSet").toggle();
    });
  });
</script>

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

<%-- <script>
var dlgMSMarkersSet<%=thisSample.getSampleID().replaceAll("[-+.^:,]","")%> = $("#dialogMSMarkersSet<%=thisSample.getSampleID().replaceAll("[-+.^:,]","")%>").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});


</script> --%>
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
     		List<String> values=CommonConfiguration.getIndexedPropertyValues("biologicalMeasurementType",context);
 			int numProps=values.size();
 			List<String> measurementUnits=CommonConfiguration.getIndexedPropertyValues("biologicalMeasurementUnits",context);
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
     		List<String> protovalues=CommonConfiguration.getIndexedPropertyValues("biologicalMeasurementSamplingProtocols",context);
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

    <script type="text/javascript">
      $(document).ready(function() {
        $(".addHaplotype<%=thisSample.getSampleID() %>").click(function() {
          $("#dialogHaplotype4<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>").toggle();
        });
      });
    </script>
		<p>
      <span class="caption">
        <a class="addHaplotype<%=thisSample.getSampleID() %> toggleBtn">
        <img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit_add.png"/>
        </a>
      <a class="toggleBtn addHaplotype<%=thisSample.getSampleID() %>"><%=encprops.getProperty("addHaplotype") %></a>
    </span>
  </p>
		<%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>
<!-- start haplotype popup -->
<script type="text/javascript">
  $(document).ready(function() {
    $(".addHaplotype<%=thisSample.getSampleID() %>").click(function() {
      $("#dialogHaplotype4<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>").toggle();
    });
  });
</script>
<div id="dialogHaplotype4<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>" title="<%=encprops.getProperty("setHaplotype")%>" style="display:none">
<form id="setHaplotype" action="../TissueSampleSetHaplotype" method="post">
<table cellpadding="1" cellspacing="0">

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

<!-- end haplotype popup -->
<%
}
%>


		<p>
      <span class="caption">
        <a class="msmarkersAdd<%=thisSample.getSampleID()%> toggleBtn">
        <img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit_add.png"/>
        </a>
        <a class="msmarkersAdd<%=thisSample.getSampleID()%> toggleBtn"><%=encprops.getProperty("addMsMarkers") %></a>
      </span>
    </p>
<%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>
<!-- start sat tag metadata -->
<script type="text/javascript">
  $(document).ready(function() {
    $(".msmarkersAdd<%=thisSample.getSampleID()%>").click(function() {
      $("#dialogMSMarkersAdd<%=thisSample.getSampleID().replaceAll("[-+.^:,]","")%>").toggle();
    });
  });
</script>

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
<!-- end ms markers popup -->
<%
}
%>



<p>
  <span class="caption">
    <a class="addSex<%=thisSample.getSampleID() %> toggleBtn">
    <img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit_add.png"/>
  </a>
  <a class="addSex<%=thisSample.getSampleID() %> toggleBtn"><%=encprops.getProperty("addGeneticSex") %></a>
</span>
</p>

<%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>

<!-- start genetic sex popup -->
<script type="text/javascript">
  $(document).ready(function() {
    $(".addSex<%=thisSample.getSampleID() %>").click(function() {
      $("#dialogSex4<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>").toggle();

    });
  });
</script>

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
<!-- end genetic sex -->
<%
}
%>


		<p>
      <span class="caption">
      <a class="toggleBtn addBioMeasure<%=thisSample.getSampleID() %>">
        <img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit_add.png"/>
      </a>
      <a class="toggleBtn addBioMeasure<%=thisSample.getSampleID() %>"><%=encprops.getProperty("addBiologicalMeasurement") %></a>
    </span>
  </p>

		<%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>
<!-- start genetic sex -->
<script type="text/javascript">
  $(document).ready(function() {
    $(".addBioMeasure<%=thisSample.getSampleID() %>").click(function() {
      $("#dialogBiomeasure4<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>").toggle();

    });
  });
</script>

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
     		List<String> values=CommonConfiguration.getIndexedPropertyValues("biologicalMeasurementType",context);
 			int numProps=values.size();
 			List<String> measurementUnits=CommonConfiguration.getIndexedPropertyValues("biologicalMeasurementUnits",context);
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
     		List<String> protovalues=CommonConfiguration.getIndexedPropertyValues("biologicalMeasurementSamplingProtocols",context);
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
<!-- end biomeasure popup -->
<%
}
%>

	</td>


	<td><a id="sample" href="encounter.jsp?number=<%=enc.getCatalogNumber() %>&sampleID=<%=thisSample.getSampleID()%>&edit=tissueSample&function=1"><img width="24px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a></td><td><a onclick="return confirm('<%=encprops.getProperty("deleteTissue") %>');" href="../EncounterRemoveTissueSample?encounter=<%=enc.getCatalogNumber()%>&sampleID=<%=thisSample.getSampleID()%>"><img style="border-style: none;width: 40px;height: 40px;" src="../images/cancel.gif" /></a></td></tr>
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

//kwQuery.closeAll();


}
catch(Exception e){
	e.printStackTrace();
	%>
	<p>Hit an error.<br /> <%=e.toString()%></p>


<%
}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
	//kwQuery=null;
	myShepherd=null;
}

	}  //end if this is an encounter
    else {
  		myShepherd.rollbackDBTransaction();
  		myShepherd.closeDBTransaction();
		%>
		<p class="para">There is no corresponding encounter number in the database. Please double-check the encounter number and try again.</p>

<form action="encounter.jsp" method="post" name="encounter"><strong>Go
  to encounter: </strong> <input name="number" type="text" value="" size="20"> <input name="Go" type="submit" value="Submit" /></form>




<%
}
%>


</div>

<!--db: These are the necessary tools for photoswipe.-->
<%
String urlLoc = "//" + CommonConfiguration.getURLLocation(request);
String pswipedir = urlLoc+"/photoswipe";
%>
<link rel='stylesheet prefetch' href='<%=pswipedir %>/photoswipe.css'>
<link rel='stylesheet prefetch' href='<%=pswipedir %>/default-skin/default-skin.css'>
<!--  <p>Looking for photoswipe in <%=pswipedir%></p>-->
<jsp:include page="../photoswipe/photoswipeTemplate.jsp" flush="true"/>
<script src='<%=pswipedir%>/photoswipe.js'></script>
<script src='<%=pswipedir%>/photoswipe-ui-default.js'></script>


<jsp:include page="../footer.jsp" flush="true"/>
