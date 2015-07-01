
<%@ page contentType="text/html; charset=utf-8" 
		import="java.util.GregorianCalendar,
                 org.ecocean.servlet.ServletUtilities,
                 org.ecocean.*,
                 java.util.Properties" %>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<link href="tools/bootstrap/css/bootstrap.min.css" rel="stylesheet"/>

<jsp:include page="header2.jsp" flush="true"/>

<%
boolean isIE = request.getHeader("user-agent").contains("MSIE ");
String context="context0";
context=ServletUtilities.getContext(request);

  GregorianCalendar cal = new GregorianCalendar();
  int nowYear = cal.get(1);
//setup our Properties object to hold all properties
  Properties props = new Properties();
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  

    //set up the file input stream
    //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/submit.properties"));
    props = ShepherdProperties.getProperties("submit.properties", langCode, context);
    
    Properties socialProps = ShepherdProperties.getProperties("socialAuth.properties", "", context);
    
    long maxSizeMB = CommonConfiguration.getMaxMediaSizeInMegabytes(context);
    long maxSizeBytes = maxSizeMB * 1048576;
%>

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
    
    
 .ui-timepicker-div .ui-widget-header { margin-bottom: 8px; }
.ui-timepicker-div dl { text-align: left; }
.ui-timepicker-div dl dt { float: left; clear:left; padding: 0 0 0 5px; }
.ui-timepicker-div dl dd { margin: 0 10px 10px 40%; }
.ui-timepicker-div td { font-size: 90%; }
.ui-tpicker-grid-label { background: none; border: none; margin: 0; padding: 0; }
.ui-timepicker-div .ui_tpicker_unit_hide{ display: none; }

.ui-timepicker-rtl{ direction: rtl; }
.ui-timepicker-rtl dl { text-align: right; padding: 0 5px 0 0; }
.ui-timepicker-rtl dl dt{ float: right; clear: right; }
.ui-timepicker-rtl dl dd { margin: 0 40% 10px 10px; }

/* Shortened version style */
.ui-timepicker-div.ui-timepicker-oneLine { padding-right: 2px; }
.ui-timepicker-div.ui-timepicker-oneLine .ui_tpicker_time, 
.ui-timepicker-div.ui-timepicker-oneLine dt { display: none; }
.ui-timepicker-div.ui-timepicker-oneLine .ui_tpicker_time_label { display: block; padding-top: 2px; }
.ui-timepicker-div.ui-timepicker-oneLine dl { text-align: right; }
.ui-timepicker-div.ui-timepicker-oneLine dl dd, 
.ui-timepicker-div.ui-timepicker-oneLine dl dd > div { display:inline-block; margin:0; }
.ui-timepicker-div.ui-timepicker-oneLine dl dd.ui_tpicker_minute:before,
.ui-timepicker-div.ui-timepicker-oneLine dl dd.ui_tpicker_second:before { content:':'; display:inline-block; }
.ui-timepicker-div.ui-timepicker-oneLine dl dd.ui_tpicker_millisec:before,
.ui-timepicker-div.ui-timepicker-oneLine dl dd.ui_tpicker_microsec:before { content:'.'; display:inline-block; }
.ui-timepicker-div.ui-timepicker-oneLine .ui_tpicker_unit_hide,
.ui-timepicker-div.ui-timepicker-oneLine .ui_tpicker_unit_hide:before{ display: none; }
    /*customizations*/
    .ui_tpicker_hour_label {margin-bottom:5px !important;}
    .ui_tpicker_minute_label {margin-bottom:5px !important;}
</style>

<script type="text/javascript" src="http://geoxml3.googlecode.com/svn/branches/polys/geoxml3.js"></script>
<script src="http://maps.google.com/maps/api/js?sensor=false&language=<%=langCode%>"></script>

<script src="javascript/timepicker/jquery-ui-timepicker-addon.js"></script>
<script src="javascript/pages/submit.js"></script>

<script type="text/javascript" src="javascript/animatedcollapse.js"></script>
  <script type="text/javascript">
    animatedcollapse.addDiv('advancedInformation', 'fade=1');

    animatedcollapse.ontoggle = function($, divobj, state) { //fires each time a DIV is expanded/contracted
      //$: Access to jQuery
      //divobj: DOM reference to DIV being expanded/ collapsed. Use "divobj.id" to get its ID
      //state: "block" or "none", depending on state
    }
    animatedcollapse.init();
  </script>

 <%
 if(!langCode.equals("en")){
 %>

<script src="javascript/timepicker/datepicker-<%=langCode %>.js"></script>
<script src="javascript/timepicker/jquery-ui-timepicker-<%=langCode %>.js"></script>

 <%
 }
 %>

<script type="text/javascript">

function validate() {
    var requiredfields = "";

    if ($("#submitterName").val().length == 0) {
      /*
       * the value.length returns the length of the information entered
       * in the Submitter's Name field.
       */
      requiredfields += "\n   *  <%=props.getProperty("submit_name") %>";
    }

      /*
      if ((document.encounter_submission.submitterEmail.value.length == 0) ||
        (document.encounter_submission.submitterEmail.value.indexOf('@') == -1) ||
        (document.encounter_submission.submitterEmail.value.indexOf('.') == -1)) {

           requiredfields += "\n   *  valid Email address";
      }
      if ((document.encounter_submission.location.value.length == 0)) {
          requiredfields += "\n   *  valid sighting location";
      }
      */

    if (requiredfields != "") {
      requiredfields = "<%=props.getProperty("pleaseFillIn") %>\n" + requiredfields;
      wildbook.showAlert(requiredfields, null, "Validate Issue");
      return false;
    }

		$('#submit-button').prop('disabled', 'disabled').css('opacity', '0.3').after('<div class="throbbing" style="display: inline-block; width: 24px; vertical-align: top; margin-left: 10px; height: 24px;"></div>');
		var s = $('.social-photo-input');
		if (s.length) {
			var iframeUrl = 'SocialGrabFiles?';
			s.each(function(i, el) {
				iframeUrl += '&fileUrl=' + escape($(el).val());
			});

console.log('iframeUrl %o', iframeUrl);
			document.getElementById('social_files_iframe').src = iframeUrl;
			return false;  //on('load') for the iframe will do actual form submission
		}

		return true;
}
</script>

<script>


$(function() {
  function resetMap() {
      var ne_lat_element = document.getElementById('lat');
      var ne_long_element = document.getElementById('longitude');


      ne_lat_element.value = "";
      ne_long_element.value = "";

    }

    $(window).unload(resetMap);
    
    //
    // Call it now on page load.
    //
    resetMap();
    
    

    $( "#datepicker" ).datetimepicker({
      changeMonth: true,
      changeYear: true,
      dateFormat: 'yy-mm-dd',
      maxDate: '+1d',
      controlType: 'select',
      alwaysSetTime: false
    });
    $( "#datepicker" ).datetimepicker( $.timepicker.regional[ "<%=langCode %>" ] );

    $( "#releasedatepicker" ).datepicker({
        changeMonth: true,
        changeYear: true,
        dateFormat: 'yy-mm-dd'
    });
    $( "#releasedatepicker" ).datepicker( $.datepicker.regional[ "<%=langCode %>" ] );
    $( "#releasedatepicker" ).datepicker( "option", "maxDate", "+1d" );
});

var center = new google.maps.LatLng(10.8, 160.8);

var map;

var marker;

function placeMarker(location) {
    if(marker!=null){marker.setMap(null);}  
    marker = new google.maps.Marker({
          position: location,
          map: map
      });

      //map.setCenter(location);
      
        var ne_lat_element = document.getElementById('lat');
        var ne_long_element = document.getElementById('longitude');


        ne_lat_element.value = location.lat();
        ne_long_element.value = location.lng();
    }

  function initialize() {
    var mapZoom = 3;
    if($("#map_canvas").hasClass("full_screen_map")){mapZoom=3;}


    if(marker!=null){
        center = new google.maps.LatLng(10.8, 160.8);
    }
    
    map = new google.maps.Map(document.getElementById('map_canvas'), {
          zoom: mapZoom,
          center: center,
          mapTypeId: google.maps.MapTypeId.HYBRID
        });
    
    if(marker!=null){
        marker.setMap(map);    
    }

      //adding the fullscreen control to exit fullscreen
      var fsControlDiv = document.createElement('DIV');
      addFullscreenButton(fsControlDiv, map);
      fsControlDiv.index = 1;
      map.controls[google.maps.ControlPosition.TOP_RIGHT].push(fsControlDiv);

      google.maps.event.addListener(map, 'click', function(event) {
            placeMarker(event.latLng);
          });
}

function fullScreen() {
    $("#map_canvas").addClass('full_screen_map');
    $('html, body').animate({scrollTop:0}, 'slow');
    initialize();
    
    //hide header
    $("#header_menu").hide();
    
    //if(overlaysSet){overlaysSet=false;setOverlays();}
}


function exitFullScreen() {
    $("#header_menu").show();
    $("#map_canvas").removeClass('full_screen_map');

    initialize();
    //if(overlaysSet){overlaysSet=false;setOverlays();}
}


//making the exit fullscreen button
function addFullscreenButton(controlDiv, map) {
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
        controlText.innerHTML = '<%=props.getProperty("exitFullscreen") %>';
    } else {
        controlText.innerHTML = '<%=props.getProperty("fullscreen") %>';
    }

    // Setup the click event listeners: toggle the full screen
    google.maps.event.addDomListener(controlUI, 'click', function() {
        if($("#map_canvas").hasClass("full_screen_map")) {
            exitFullScreen();
        } else {
            fullScreen();
        }
    });
}

google.maps.event.addDomListener(window, 'load', initialize);

</script>



<div class="container maincontent">
<p>&nbsp;</p>
  <h1 class="intro"><%=props.getProperty("submit_report")%>
  </h1>

<iframe id="social_files_iframe" style="display: none;" ></iframe>
<form id="encounterForm" action="EncounterForm" method="post" enctype="multipart/form-data"
      name="encounter_submission" target="_self" dir="ltr" lang="en"
      onsubmit="return false;">
<div class="dz-message"></div>

<p><%=props.getProperty("submit_overview")%>
</p>





<script>


$('#social_files_iframe').on('load', function(ev) {
	if (!ev || !ev.target) return;
//console.warn('ok!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!');
	var doc = ev.target.contentDocument || ev.target.contentWindow.contentDocument;
//console.warn('doc is %o', doc);
	if (!doc) return;
//var x = $(doc).find('body').text();
//console.warn('body %o', x);
	var j = JSON.parse($(doc).find('body').text());
	console.info('iframe returned %o', j);

	$('#encounterForm').append('<input type="hidden" name="social_files_id" value="' + j.id + '" />');
	//now do actual submit
	document.forms['encounterForm'].submit();
});


function socialPhotoGrab() {
}


function updateList(inp) {
    var f = '';
    if (inp.files && inp.files.length) {
        var all = [];
        for (var i = 0 ; i < inp.files.length ; i++) {
            if (inp.files[i].size > <%=maxSizeBytes%>) {
                all.push('<span class="error">' + inp.files[i].name + ' (' + Math.round(inp.files[i].size / (1024*1024)) + 'MB is too big, <%=maxSizeMB%>MB max)</span>');
            } else {
                all.push(inp.files[i].name + ' (' + Math.round(inp.files[i].size / 1024) + 'k)');
            }
        }
        f = '<b>' + inp.files.length + ' file' + ((inp.files.length == 1) ? '' : 's') + ':</b> ' + all.join(', ');
    } else {
        f = inp.value;
    }
    document.getElementById('input-file-list').innerHTML = f;
}

function showUploadBox() {
    $("#submitsocialmedia").addClass("hidden");
    $("#submitupload").removeClass("hidden");
}

</script>

<h3><%=props.getProperty("submit_image")%></h3>
<p><%=props.getProperty("submit_pleaseadd")%>
<div class="container-fluid">
    <div class="row">
        <ul id="social_image_buttons" class="list-inline" style="text-align: center;">
            <li class="active">
                <button class="zocial icon" title="Upload from your computer" onclick="showUploadBox()"
                        style="background:url(images/computer.png);">
                </button>
            </li>
        </ul>
    </div>
    <div class="row" >
        <div id="submitupload" class="input-file-drop">
            <% if (isIE) { %>
            <div><%=props.getProperty("dragInstructionsIE")%></div>
            <input class="ie" name="theFiles" type="file" accept=".jpg, .jpeg, .png, .bmp, .gif, .mov, .wmv, .avi, .mp4, .mpg" multiple size="30" onChange="updateList(this);" />
            <% } else { %>
            <input class="nonIE" name="theFiles" type="file" accept=".jpg, .jpeg, .png, .bmp, .gif, .mov, .wmv, .avi, .mp4, .mpg" multiple size="30" onChange="updateList(this);" />
            <div><%=props.getProperty("dragInstructions")%></div>
            <% } %>
            <div id="input-file-list"></div>
        </div>
        <div id="submitsocialmedia" class="container-fluid hidden" style="height:300px;">
            <div id="socialalbums" class="col-md-4" style="height:100%;overflow-y:auto;">
            </div>
            <div id="socialphotos" class="col-md-8" style="height:100%;overflow-y:auto;">
            </div>
        </div>
    </div>
</div>
<br />
<h3><strong><%=props.getProperty("dateAndLocation")%></strong></h3>

<p><%=props.getProperty("submit_note_red")%>

<table id="encounter_report" style="border:0">
<tr class="form_row">
  <td class="form_label" style="border-bottom: #ffffff;"><strong><font color="#CC0000"><%=props.getProperty("submit_date")%></font></strong>
  </td>
  <td style="border-bottom: #ffffff;">
  
     <input type="text" style="position: relative; z-index: 101;" id="datepicker" name="datepicker" size="20" />

    </td>
    <td style="border-bottom: #ffffff;">
    <%=props.getProperty("examples") %>
    <ul>
    <li>2014-01-05 12:30</li>
    <li>2014-03-23</li>
    <li>2013-12</li>
    <li>2010</li>
    </ul>
    
    </td>
</tr>

<%
if(CommonConfiguration.showReleaseDate(context)){
%>

    <tr class="form_row">
    <td class="form_label" style="border-bottom: #ffffff;"><strong><%=props.getProperty("submit_releasedate") %>:</strong></td>
    <td colspan="2" style="border-bottom: #ffffff;">  
        <input type="text" style="position: relative; z-index: 101;" id="releasedatepicker" name="releaseDate" size="20" />
    </td>
    </tr>
<%
}
%>




<tr>
  <td class="form_label" rowspan="7" style="border-bottom: #ffffff;"><strong><font
    color="#CC0000"><%=props.getProperty("submit_location")%></font></strong></td>
    </tr>
    
    <tr>
  <td ><em><%=props.getProperty("where") %></em><br /><input name="location" type="text" id="location" size="40"/></td>
</tr>
<%
//add locationID to fields selectable


if(CommonConfiguration.getSequentialPropertyValues("locationID", context).size()>0){
%>
<tr>
            <td class="form_label1"><em><%=props.getProperty("locationID")%></em><br />
              <select name="locationID" id="locationID">
                  <option value="" selected="selected"></option>
                  <%
                         boolean hasMoreLocationsIDs=true;
                         int locNum=0;
                         
                         while(hasMoreLocationsIDs){
                               String currentLocationID = "locationID"+locNum;
                               if(CommonConfiguration.getProperty(currentLocationID,context)!=null){
                                   %>
                                    
                                     <option value="<%=CommonConfiguration.getProperty(currentLocationID,context)%>"><%=CommonConfiguration.getProperty(currentLocationID,context)%></option>
                                   <%
                                 locNum++;
                            }
                            else{
                               hasMoreLocationsIDs=false;
                            }
                            
                       }
                       
     %>
      </select>
    
</td>
    </tr>
<%
}

if(CommonConfiguration.showProperty("showCountry",context)){

%>
        <tr >
            <td class="form_label1"><em><%=props.getProperty("country")%></em></td>
        <td>
              <select name="country" id="country">
                  <option value="" selected="selected"></option>
                  <%
                         boolean hasMoreCountries=true;
                         int taxNum=0;
                         
                         while(hasMoreCountries){
                               String currentCountry = "country"+taxNum;
                               if(CommonConfiguration.getProperty(currentCountry,context)!=null){
                                   %>
                                    
                                     <option value="<%=CommonConfiguration.getProperty(currentCountry,context)%>"><%=CommonConfiguration.getProperty(currentCountry,context)%></option>
                                   <%
                                 taxNum++;
                            }
                            else{
                               hasMoreCountries=false;
                            }
                            
                       }
                       
     %>
      </select>
    
</td>
    </tr>
    
    
    

<%
}  //end if showCountry

%>

<tr><td colspan="2">
    <p id="map">
    <!--  
      <p>Use the arrow and +/- keys to navigate to a portion of the globe,, then click
        a point to set the sighting location. You can also use the text boxes below the map to specify exact
        latitude and longitude.</p>
    -->
    <p id="map_canvas" style="width: 578px; height: 383px; "></p>
    <p id="map_overlay_buttons"></p>
</td>
</tr>

<tr>
        <td class="form_label1" colspan="2"><em><%=props.getProperty("submit_gpslatitude")%></em>
        <input name="lat" type="text" id="lat" size="10" />
        &deg;
        &nbsp;<em><%=props.getProperty("submit_gpslongitude")%></em>
            <input name="longitude" type="text" id="longitude" size="10" /> &deg;

        <br/><br /> <%=props.getProperty("gpsConverter") %><br /><br />
        </td>
    </tr>
    
    
          <%



if(CommonConfiguration.showProperty("maximumDepthInMeters",context)){
%>
<tr class="">
  <td colspan="2"><em><%=props.getProperty("submit_depth")%></em> 
<input name="depth" type="text" id="depth" size="10" />
  &nbsp;<%=props.getProperty("submit_meters")%> <br />
    </td>
</tr>
<%
}

if(CommonConfiguration.showProperty("maximumElevationInMeters",context)){
%>
<tr>
  <td class="form_label1" colspan="2"><em><%=props.getProperty("submit_elevation")%></em> 
<input name="elevation" type="text" id="elevation" size="10" />
  &nbsp;<%=props.getProperty("submit_meters")%>
 
    </td>
</tr>
<%
}
%>

</table>
<br />
<h3><strong><%=props.getProperty("aboutYou")%></strong></h3>

<table style="border:0;">
<tr>
    <td class="you" colspan="2"><strong><%=props.getProperty("submit_contactinfo")%>*</strong></td>
    <td class="photo" colspan="2"><strong><%=props.getProperty("submit_contactphoto")%>
    </strong><br/><%=props.getProperty("submit_ifyou")%>
    </td>
  </tr>
    <%
    //let's pre-populate important info for logged in users
    String submitterName="";
    String submitterEmail="";
    String affiliation="";
    String project="";
    if(request.getRemoteUser()!=null){
        submitterName=request.getRemoteUser();
        Shepherd myShepherd=new Shepherd(context);
        if(myShepherd.getUser(submitterName)!=null){
            User user=myShepherd.getUser(submitterName);
            if(user.getFullName()!=null){submitterName=user.getFullName();}
            if(user.getEmailAddress()!=null){submitterEmail=user.getEmailAddress();}
            if(user.getAffiliation()!=null){affiliation=user.getAffiliation();}
            if(user.getUserProject()!=null){project=user.getUserProject();}
        }
    }
    %>
  <tr>
    <td><font color="#CC0000"><%=props.getProperty("submit_name")%></font></td>
    <td><input name="submitterName" type="text" id="submitterName" size="24" value="<%=submitterName%>"/></td>
    <td><%=props.getProperty("submit_name")%></td>
    <td><input name="photographerName" type="text" id="photographerName" size="24"/></td>
  </tr>
  <tr>
    <td><font color="#CC0000"><%=props.getProperty("submit_email")%></font></td>

    <td><input name="submitterEmail" type="text" id="submitterEmail" size="24" value="<%=submitterEmail %>"/></td>
    <td><%=props.getProperty("submit_email")%>:</td>
    <td><input name="photographerEmail" type="text" id="photographerEmail" size="24"/></td>
  </tr>
</table>
<table style="border:0;">
  <tr>
    <td ><br /><strong><%=props.getProperty("submitterOrganization")%></strong><br />
    <input name="submitterOrganization" type="text" id="submitterOrganization" size="75" value="<%=affiliation%>"/>
    </td>
  </tr>
  
    <tr>
      <td><br /><strong><%=props.getProperty("submitterProject")%></strong><br />
      <input name="submitterProject" type="text" id="submitterProject" size="75" value="<%=project%>"/>
      </td>
  </tr>
  <tr>
  <td><br><strong><%=props.getProperty("submit_comments")%></strong><br><textarea name="comments" cols="75" id="comments"
                            rows="5"></textarea></td>
</tr>
  </table>
  
  <h4 class="intro" style="background-color: #cccccc; padding:3px; border: 1px solid #000066; "><a
      href="javascript:animatedcollapse.toggle('advancedInformation')" style="text-decoration:none"><img
      src="images/Black_Arrow_down.png" width="14" height="14" border="0" align="absmiddle"/>
      <font color="#000000"><%=props.getProperty("advancedInformation") %></font></a></h4>

<div id="advancedInformation" style="display:none;">
<table style="border:0;">
<tr class="form_row">
  <td class="form_label"><strong><%=props.getProperty("submit_sex")%>:</strong></td>
  <td colspan="2" class="form_label"><label> <input type="radio" name="sex"
                                 value="male"/> <%=props.getProperty("submit_male")%>
  </label> <label>
    <input type="radio" name="sex" value="female"/> <%=props.getProperty("submit_female")%>
  </label>

    <label> <input name="sex" type="radio" value="unknown"
                   checked="checked"/> <%=props.getProperty("submit_unknown")%>
    </label></td>
</tr>
<%

if(CommonConfiguration.showProperty("showTaxonomy",context)){

%>
<tr class="form_row">
  <td class="form_label"><strong><%=props.getProperty("species")%></strong></td>
  <td colspan="2">
  <select name="genusSpecies" id="genusSpecies">
      <option value="" selected="selected"><%=props.getProperty("submit_unsure")%></option>
  <%
                     boolean hasMoreTax=true;
                     int taxNum=0;
                     if(CommonConfiguration.showProperty("showTaxonomy",context)){
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
                   }
 %>
  </select></td>
</tr>
<%
}

%>

<tr class="form_row">
  <td class="form_label"><strong><%=props.getProperty("status") %></strong></td>
  <td colspan="2"><select name="livingStatus" id="livingStatus">
    <option value="alive" selected="selected">Alive</option>
    <option value="dead">Dead</option>
  </select></td>
</tr>

<tr class="form_row">
  <td class="form_label"><strong><%=props.getProperty("submit_behavior")%></strong></td>
  <td colspan="2">
    <input name="behavior" type="text" id="scars" size="75"/></td>
</tr>
<%

if(CommonConfiguration.showProperty("showLifestage",context)){

%>
<tr class="form_row">
  <td class="form_label"><strong><%=props.getProperty("lifeStage")%></strong></td>
  <td colspan="2">
  <select name="lifeStage" id="lifeStage">
      <option value="" selected="selected"></option>
  <%
                     boolean hasMoreStages=true;
                     int stageNum=0;
                     
                     while(hasMoreStages){
                           String currentLifeStage = "lifeStage"+stageNum;
                           if(CommonConfiguration.getProperty(currentLifeStage,context)!=null){
                               %>
                                
                                 <option value="<%=CommonConfiguration.getProperty(currentLifeStage,context)%>"><%=CommonConfiguration.getProperty(currentLifeStage,context)%></option>
                               <%
                             stageNum++;
                        }
                        else{
                          hasMoreStages=false;
                        }
                        
                   }
                   
 %>
  </select></td>
</tr>
<%
}
%>
<%
    pageContext.setAttribute("showMeasurements", CommonConfiguration.showMeasurements(context));
%>
<c:if test="${showMeasurements}">
<%
    pageContext.setAttribute("items", Util.findMeasurementDescs(langCode,context));
    pageContext.setAttribute("samplingProtocols", Util.findSamplingProtocols(langCode,context));
%>
<tr class="form_row">
  <td class="form_label"><strong><%=props.getProperty("measurements")%>:</strong></td>
  <td colspan="2">
  <table class="measurements">
  <tr>
  <th><%=props.getProperty("type") %></th><th><%=props.getProperty("size") %></th><th><%=props.getProperty("units") %></th><c:if test="${!empty samplingProtocols}"><th><%=props.getProperty("samplingProtocol") %></th></c:if>
  </tr>
  <c:forEach items="${items}" var="item">
    <tr>
    <td>${item.label}</td>
    <td><input name="measurement(${item.type})" id="${item.type}"/><input type="hidden" name="measurement(${item.type}units)" value="${item.units}"/></td>
    <td><c:out value="${item.unitsLabel}"/></td>
    <c:if test="${!empty samplingProtocols}">
      <td>
        <select name="measurement(${item.type}samplingProtocol)">
        <c:forEach items="${samplingProtocols}" var="optionDesc">
          <option value="${optionDesc.name}"><c:out value="${optionDesc.display}"/></option>
        </c:forEach>
        </select>
      </td>
    </c:if>
    </tr>
  </c:forEach>
  </table>
  </td>
</tr>
</c:if>
<%
  pageContext.setAttribute("showMetalTags", CommonConfiguration.showMetalTags(context));
  pageContext.setAttribute("showAcousticTag", CommonConfiguration.showAcousticTag(context));
  pageContext.setAttribute("showSatelliteTag", CommonConfiguration.showSatelliteTag(context));
  pageContext.setAttribute("metalTags", Util.findMetalTagDescs(langCode,context));
%>

<c:if test="${showMetalTags and !empty metalTags}">
<tr class="form_row">
  <td class="form_label"><strong><%=props.getProperty("physicalTags") %></strong></td>
  <td colspan="2">
    <table class="metalTags">
    <tr>
      <th><%=props.getProperty("location") %></th><th><%=props.getProperty("tagNumber") %></th>
    </tr>
    <c:forEach items="${metalTags}" var="metalTagDesc">
      <tr>
        <td><c:out value="${metalTagDesc.locationLabel}:"/></td>
        <td><input name="metalTag(${metalTagDesc.location})"/></td>
      </tr>
    </c:forEach>
    </table>
  </td>
</tr>
</c:if>

<c:if test="${showAcousticTag}">
<tr class="form_row">
    <td class="form_label"><strong><%=props.getProperty("acousticTag") %></strong></td>
    <td colspan="2">
      <table class="acousticTag">
      <tr>
      <td><%=props.getProperty("serialNumber") %></td>
      <td><input name="acousticTagSerial"/></td>
      </tr>
      <tr>
        <td><%=props.getProperty("id") %></td>
        <td><input name="acousticTagId"/></td>
      </tr>
      </table>
    </td>
</tr>
</c:if>

<c:if test="${showSatelliteTag}">
<%
  pageContext.setAttribute("satelliteTagNames", Util.findSatelliteTagNames(context));
%>
<tr class="form_row">
    <td class="form_label"><strong><%=props.getProperty("satelliteTag") %></strong></td>
    <td colspan="2">
      <table class="satelliteTag">
      <tr>
        <td><%=props.getProperty("name") %></td>
        <td>
            <select name="satelliteTagName">
              <c:forEach items="${satelliteTagNames}" var="satelliteTagName">
                <option value="${satelliteTagName}">${satelliteTagName}</option>
              </c:forEach>
            </select>
        </td>
      </tr>
      <tr>
        <td><%=props.getProperty("serialNumber") %></td>
        <td><input name="satelliteTagSerial"/></td>
      </tr>
      <tr>
        <td><%=props.getProperty("argosNumber") %></td>
        <td><input name="satelliteTagArgosPttNumber"/></td>
      </tr>
      </table>
    </td>
</tr>
</c:if>

<tr class="form_row">
  <td class="form_label"><strong><%=props.getProperty("submit_scars")%>:</strong></td>
  <td colspan="2">
    <input name="scars" type="text" id="scars" size="75"/></td>
</tr>

<tr>
  <td></td>
  <td></td>
  <td></td>
</tr>
</table>
<table id="encounter_contact">
  

  <tr>
    <td colspan="4"><br /><strong><%=props.getProperty("otherEmails")%></strong><br />
    <input name="informothers" type="text" id="informothers" size="75"/>
    </td>
  </tr>
  
</table>
<p><em><%=props.getProperty("multipleEmailNote")%></em>.</p>
<hr/>
</div>


<p>&nbsp;</p>
<%if (request.getRemoteUser() != null) {%> <input name="submitterID"
                                                  type="hidden"
                                                  value="<%=request.getRemoteUser()%>"/> <%} else {%>
<input
  name="submitterID" type="hidden" value="N/A"/> <%}%>
<p align="center">
<button id="submit-button" onclick="if (validate()) {document.forms['encounterForm'].submit();}"><%=props.getProperty("submit_send")%></button>
</p>

<p>&nbsp;</p>
</form>
</div>


<jsp:include page="footer2.jsp" flush="true"/>
