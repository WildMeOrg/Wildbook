
<%@ page contentType="text/html; charset=utf-8"
		import="java.util.GregorianCalendar,
                 org.ecocean.servlet.ServletUtilities,
                 org.ecocean.*,
                 java.util.Properties,
                 java.util.List,
                 java.util.ArrayList,
                 java.util.Locale" %>


<!-- Add reCAPTCHA -->


<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<link href="tools/bootstrap/css/bootstrap.min.css" rel="stylesheet"/>
	
<link type='text/css' rel='stylesheet' href='javascript/timepicker/jquery-ui-timepicker-addon.css' />


<jsp:include page="header.jsp" flush="true"/>

<!-- add recaptcha -->
<script src="https://www.google.com/recaptcha/api.js?render=explicit&onload=onloadCallback"></script>

<%
boolean isIE = request.getHeader("user-agent").contains("MSIE ");
String context="context0";
context=ServletUtilities.getContext(request);

String mapKey = CommonConfiguration.getGoogleMapsKey(context);

  GregorianCalendar cal = new GregorianCalendar();
  int nowYear = cal.get(1);
//setup our Properties object to hold all properties
  Properties props = new Properties();
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);


    //set up the file input stream
    //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/submit.properties"));
    props = ShepherdProperties.getProperties("submit.properties", langCode, context);

    Properties recaptchaProps=ShepherdProperties.getProperties("recaptcha.properties", "", context);

    Properties socialProps = ShepherdProperties.getProperties("socialAuth.properties", "", context);

    long maxSizeMB = CommonConfiguration.getMaxMediaSizeInMegabytes(context);
    long maxSizeBytes = maxSizeMB * 1048576;
    
    
    //let's pre-populate default values
    String default_date="";
    String default_releaseDate="";
    String default_location = "";
    String default_locationID = "";
    String default_latitude = "";
    String default_longitude = "";
    String default_measurement_temperature="";
    String default_measurement_depth="";
    String default_measurement_underwater="";
    String default_charter_operator="";
    //let's pre-populate important info for logged in users
    String submitterName="";
    String submitterEmail="";
    String affiliation="";
    String project="";
    if(request.getRemoteUser()!=null){
        submitterName=request.getRemoteUser();
        Shepherd myShepherd=new Shepherd(context);
        myShepherd.setAction("submit.jsp1");
        myShepherd.beginDBTransaction();
        if(myShepherd.getUser(submitterName)!=null){
            User user=myShepherd.getUser(submitterName);
            if(user.getFullName()!=null){submitterName=user.getFullName();}
            if(user.getEmailAddress()!=null){submitterEmail=user.getEmailAddress();}
            if(user.getAffiliation()!=null){affiliation=user.getAffiliation();}
            if(user.getUserProject()!=null){project=user.getUserProject();}
        }
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
    }
    String photographerName="";
    String photographerEmail="";
    
    //if there's a mimic encounter, set those parameters now
    if(request.getParameter("mimicEncounter")!=null){
    	String mimicEnc=request.getParameter("mimicEncounter");
    	Shepherd mimicShepherd=new Shepherd(context);
    	mimicShepherd.setAction("submit.jsp_mimicShepherd");
    	if(mimicShepherd.isEncounter(mimicEnc)){
    		Encounter enc=mimicShepherd.getEncounter(mimicEnc);
    		
    		if(enc.getSubmitterName()!=null)submitterName=enc.getSubmitterName();
    		if(enc.getSubmitterEmail()!=null)submitterEmail=enc.getSubmitterEmail();
    		if(enc.getPhotographerName()!=null)photographerName=enc.getPhotographerName();
    		if(enc.getPhotographerEmail()!=null)photographerEmail=enc.getPhotographerEmail();
        if(enc.getSubmitterOrganization()!=null)affiliation=enc.getSubmitterOrganization();
        if(enc.getSubmitterProject()!=null)project=enc.getSubmitterProject();
        if(enc.getDate()!=null)default_date=enc.getDate();
        if(enc.getReleaseDate()!=null)default_releaseDate=enc.getReleaseDate().toString();
        if(enc.getVerbatimLocality()!=null)default_location=enc.getVerbatimLocality();
        if(enc.getLocationID()!=null)default_locationID=enc.getLocationID();
        if(enc.getDecimalLatitude()!=null)default_latitude=enc.getDecimalLatitude();
        if(enc.getDecimalLongitude()!=null)default_longitude=enc.getDecimalLongitude();
        if(enc.getCharterOperator()!=null)default_charter_operator=enc.getCharterOperator();

        if(enc.getMeasurement("temperature")!=null) {
          //Got to convert the temp back to F, so it can be left alone for repeated submit. 
          double temp = enc.getMeasurement("temperature").getValue()*1.8+32;
          default_measurement_temperature=String.valueOf(temp);
        }

        if(enc.getDepthAsDouble()!=null) {
          //Got to conver the depth back to feet 
          double depth = enc.getDepthAsDouble()*3.2808; 
          default_measurement_depth=String.valueOf(depth);
        }

        if(enc.getMeasurement("measurement(underwater")!=null) { 
          default_measurement_underwater=enc.getMeasurement("underwater").getValue().toString();
        }  

    	}
    	mimicShepherd.rollbackDBTransaction();
    	mimicShepherd.closeDBTransaction();
    }
    
    
    
    
    
 
    
    
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

<script src="//maps.google.com/maps/api/js?key=<%=mapKey%>&language=<%=langCode%>"></script>

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

	//this is negated cuz we want to halt validation (submit action) if we are sending via background iframe --
	// it will do the submit via on('load')
	return !sendSocialPhotosBackground();
}


//returns true if we are sending stuff via iframe background magic
function sendSocialPhotosBackground() {
	$('#submit-button').prop('disabled', 'disabled').css('opacity', '0.3').after('<div class="throbbing" style="display: inline-block; width: 24px; vertical-align: top; margin-left: 10px; height: 24px;"></div>');
	var s = $('.social-photo-input');
	if (s.length < 1) return false;
	var iframeUrl = 'SocialGrabFiles?';
	s.each(function(i, el) {
		iframeUrl += '&fileUrl=' + escape($(el).val());
	});

console.log('iframeUrl %o', iframeUrl);
	document.getElementById('social_files_iframe').src = iframeUrl;
	return true;
}
</script>

<script>
function isEmpty(str) {
    return (!str || 0 === str.length);
}
</script>

<script>


$(function() {
  function resetMap() {
      var ne_lat_element = document.getElementById('latitude');
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
      dateFormat: 'yyMdd',
      maxDate: '+1d',
      controlType: 'select',
      alwaysSetTime: false,
      showSecond:false,
      showMillisec:false,
      showMicrosec:false,
      showTimezone:false
    });
    $( "#datepicker" ).datetimepicker( $.timepicker.regional[ "<%=langCode %>" ] );

    $( "#releasedatepicker" ).datepicker({
        changeMonth: true,
        changeYear: true,
        dateFormat: 'yy-M-dd'
    });
    $( "#releasedatepicker" ).datepicker( $.datepicker.regional[ "<%=langCode %>" ] );
    $( "#releasedatepicker" ).datepicker( "option", "maxDate", "+1d" );
});

var center = new google.maps.LatLng(34.38, -76.64);

var map;

var marker;

function placeMarker(location) {
    if(marker!=null){marker.setMap(null);}
    marker = new google.maps.Marker({
          position: location,
          map: map
      });

      //map.setCenter(location);

        var ne_lat_element = document.getElementById('latitude');
        var ne_long_element = document.getElementById('longitude');


        ne_lat_element.value = location.lat();
        ne_long_element.value = location.lng();
    }

  function initialize() {
    var mapZoom = 6;
    if($("#map_canvas").hasClass("full_screen_map")){mapZoom=3;}


    if(marker!=null){
        center = new google.maps.LatLng(34.38, -76.64);
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
      //addFullscreenButton(fsControlDiv, map);
      fsControlDiv.index = 1;
      map.controls[google.maps.ControlPosition.TOP_RIGHT].push(fsControlDiv);

      google.maps.event.addListener(map, 'click', function(event) {
            placeMarker(event.latLng);
          });
}




google.maps.event.addDomListener(window, 'load', initialize);

</script>

<div class="container-fluid page-content" role="main">

<div class="container maincontent">

  <div class="col-xs-12 col-sm-4 col-md-4 col-lg-4">
      <h1 class="intro"><%=props.getProperty("submit_report") %></h1>

      <p><%=props.getProperty("submit_overview") %></p>

      <p class="bg-danger text-danger">
        <%=props.getProperty("submit_note_red") %>
      </p>
  </div>


  <div class="col-xs-12 col-sm-7 col-md-7 col-lg-7">
<iframe id="social_files_iframe" style="display: none;" ></iframe>
<form id="encounterForm"
	  action="spambot.jsp"
	  method="post"
	  enctype="multipart/form-data"
      name="encounter_submission"
      target="_self" dir="ltr"
      lang="en"
      onsubmit="return false;"
      class="form-horizontal" 
      accept-charset="UTF-8"
>

<div class="dz-message"></div>





<script>


$('#social_files_iframe').on('load', function(ev) {
	if (!ev || !ev.target) return;
//console.warn('ok!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!');
	var doc = ev.target.contentDocument || ev.target.contentWindow.contentDocument;
	console.warn('doc is %o', doc);
	if (doc === null) return;
//var x = $(doc).find('body').text();
//console.warn('body %o', x);
	var j = JSON.parse($(doc).find('body').text());
	console.info('iframe returned %o', j);

	
	console.log("social_files_id : "+j.id);
	$('#encounterForm').append('<input type="hidden" name="social_files_id" value="' + j.id + '" />');
	//now do actual submit
	submitForm();
});


//this is a simple wrapper to this, as it is called from 2 places (so far)
function submitForm() {
	document.forms['encounterForm'].submit();
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


<fieldset>
<h3 class="text-danger"><%=props.getProperty("submit_image")%></h3>
<p><%=props.getProperty("submit_pleaseadd")%></p>
	<div class="center-block">
        <ul id="social_image_buttons" class="list-inline text-center">
          <li class="active">
              <button class="zocial icon" title="Upload from your computer" onclick="showUploadBox()" style="background:url(images/computer.png);background-repeat: no-repeat;">
              </button>
          </li>

        </ul>
    </div>

    <div>
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

</fieldset>

<hr/>

 <fieldset>
    <div class="row">

      <div class="form-group form-inline">

        <div class="col-xs-1 col-lg-1">

        </div>
        <div class="col-xs-5 col-lg-5">
          <label class="text-danger control-label" style="text-align:left;">Are You the photographer?</label>
        </div>
        <div class="col-xs-5 col-md-5">
          <input type="radio" name="isPhotographer" onChange="tookPhoto()" value="1" id="photographerYes" checked></input>
          <label>Yes</label>
          &nbsp
          <input type="radio" name="isPhotographer" onChange="tookPhoto()" value="0" id="photographerNo"></input>
          <label>No</label>
        </div>
        <div class="col-xs-1 col-lg-1">
        </div>
      </div>

      <div class="col-xs-12 col-lg-6">
        <h3 class="text-danger"><%=props.getProperty("aboutYou") %></h3>
        <p class="help-block"><%=props.getProperty("submit_contactinfo") %></p>
        <br>
        <div class="form-group form-inline">
          <div class="col-xs-6 col-md-4">
            <label class="text-danger control-label"><%=props.getProperty("submit_name") %></label>
          </div>
          <div class="col-xs-6 col-lg-8">
            <input class="form-control" name="submitterName" type="text" id="submitterName" size="24" value="<%=submitterName %>">
          </div>
        </div>

        <div class="form-group form-inline">

          <div class="col-xs-6 col-md-4">
            <label class="text-danger control-label"><%=props.getProperty("submit_email") %></label>
          </div>
          <div class="col-xs-6 col-lg-8">
            <input class="form-control" name="submitterEmail" type="text" id="submitterEmail" size="24" value="<%=submitterEmail %>">
          </div>
        </div>
      </div>

      <div id="photographerData" style="display:none;" class="col-xs-12 col-lg-6">
        <h3><%=props.getProperty("aboutPhotographer") %></h3>
        <p class="help-block"><%=props.getProperty("submit_contactphoto") %></p>
        <div class="form-group form-inline">
          <div class="col-xs-6 col-md-4">
            <label class="control-label"><%=props.getProperty("submit_name") %></label>
          </div>
          <div class="col-xs-6 col-lg-8">
            <input class="form-control" name="photographerName" type="text" id="photographerName" size="24" value="<%=photographerName %>">
          </div>
        </div>

        <div class="form-group form-inline">
          <div class="col-xs-6 col-md-4">
            <label class="control-label"><%=props.getProperty("submit_email") %></label>
          </div>
          <div class="col-xs-6 col-lg-8">
            <input class="form-control" name="photographerEmail" type="text" id="photographerEmail" size="24"  value="<%=photographerEmail %>">
          </div>
        </div>
      </div>

      <div class="form-group">
        <div class="col-xs-6 col-lg-8">
          <label class="control-label"><%=props.getProperty("submitterOrganization") %></label>
          <p class="help-block"><%=props.getProperty("submitterOrganizationSummary") %></p>
          <input class="form-control" name="submitterOrganization" type="text" id="submitterOrganization" size="75" value="<%=affiliation %>">
        </div>
      </div>

      <div class="col-xs-12 col-lg-12">
        <h3><%=props.getProperty("charterOperator") %></h3>
        <p class="help-block"><%=props.getProperty("submit_charteroperator") %></p>
        <div class="form-group form-inline">
          <div class="col-xs-6 col-md-4">
            <label class="control-label"><%=props.getProperty("operatorName") %></label>
          </div>
          <div class="col-xs-6 col-lg-8">
            <select class="form-control"  name="charterOperatorName" id="charterOperatorName">
              <option value="<%=default_charter_operator%>"><%=default_charter_operator%></option>
              <%
              if (CommonConfiguration.getIndexedPropertyValues("charterOperator", context).size()>0) {
                ArrayList<String> operators = (ArrayList) CommonConfiguration.getIndexedPropertyValues("charterOperator", context);
                for (String operator : operators) {
              %>
                  <option value="<%=operator%>"><%=operator%></option>
              <%
                }
              }
              %>
            </select> 
          </div>
        </div>
      </div>

    </div>
  </fieldset>

<hr>/

<fieldset>


<div class="form-group required">

<h3><%=props.getProperty("dateAndLocation")%></h3>

    <div class="form-group required">

      <div class="form-inline col-xs-12 col-sm-12 col-md-6 col-lg-6">
        <label class="control-label text-danger"><%=props.getProperty("submit_date") %></label>
        <input class="form-control" type="text" style="position: relative; z-index: 101;" id="datepicker" name="datepicker" size="20" value="<%=default_date %>"  />
</div>

      <div class="col-xs-12 col-sm-12 col-md-6 col-lg-6">
        <p class="help-block">
          <%=props.getProperty("examples") %>
          <ul>
            <li>2014Jan05 12:30</li>
            <li>2014MAR23</li>
            <li>2013AUG</li>
            <li>2010sep</li>
          </ul>
        </p>
      </div>

    </div>

<%
if(CommonConfiguration.showReleaseDate(context)){
%>

    <div class="form-inline col-xs-12 col-sm-12 col-md-6 col-lg-6">
        <label class="control-label text-danger"><%=props.getProperty("submit_releasedate") %></label>
        <input class="hasDatepicker form-control" type="text" style="position: relative; z-index: 101;" id="releasedatepicker" name="releaseDate" value="<%=default_releaseDate %>" size="20">
      </div>
      </div>

<%
}
%>

</fieldset>

<hr />

<fieldset>
    <h3 class="text-danger"><%=props.getProperty("submit_location")%></h3>
<p class="help-block"><%=props.getProperty("where") %></p>
    <div class="form-group required">
      <div class="col-xs-6 col-sm-6 col-md-4 col-lg-4">
        <label class="control-label">Location description:</label>
      </div>
      <div class="col-xs-6 col-sm-6 col-md-6 col-lg-8">
        <input name="location" type="text" id="location" size="40" class="form-control" value="<%=default_location %>">
      </div>
    </div>
    <div class="col-xs-12 col-sm-12 col-md-6 col-lg-6">
      <p class="text-danger"><%=props.getProperty("requiredLocation") %></p>
      <p class="help-block">
        <%=props.getProperty("locationOptions") %>
        <ul>
          <li>Select location from a list of popular diving and fishing sites</li>
          <li>Provide coordinates for the location using latitude and longitude</li>
          <li>Manually identify location on a map</li>
        </ul>
      </p>
    </div>


<%
//add locationID to fields selectable


if(CommonConfiguration.getIndexedPropertyValues("locationID", context).size()>0){
%>
    <br>
    <div class="form-group required">
      <div class="col-xs-12 col-sm-12 col-md-4 col-lg-12">
        <label class="control-label"><%=props.getProperty("studySites") %></label>
         <p class="help-block"><%=props.getProperty("studySitesSummary") %></p>
      </div>

      <div class="col-xs-6 col-sm-6 col-md-6 col-lg-8">
        <select name="locationID" id="locationID" class="form-control">
            <option value=""></option>
                  <%
                         boolean hasMoreLocationsIDs=true;
                         int locNum=0;

                         while(hasMoreLocationsIDs){
                               String currentLocationID = "locationID"+locNum;
                               String selected="";
                               if(CommonConfiguration.getProperty(currentLocationID,context)!=null){
                            	   
                            	   if(default_locationID.equals(CommonConfiguration.getProperty(currentLocationID,context))) {selected="selected=\"selected\"";}
                                   
                                   %>

                                     <option value="<%=CommonConfiguration.getProperty(currentLocationID,context)%>" <%=selected %>><%=CommonConfiguration.getProperty(currentLocationID,context)%></option>
                                   <%
                                 locNum++;
                            }
                            else{
                               hasMoreLocationsIDs=false;
                            }

                       }

     %>
      </select>
      </div>
    </div>
    
    <script type="text/javascript">
		// This script updates the lat/lang fields when the dropdown is changed
		// I love JS objects!
		function setLatLong(locationKey) {
			var latLongRaw = {BI:["32 37 30 S", 	"152 20 20 E"],
				BS:		["32 28 00 S", 	"152 33 00 E"],
				CH:		["30 14 50 S"	, "153 21 60 E"],
				F0:   ["", ""],
				FR:		["30 56 25 S", 	"153 05 45 E"],
				GS:   ["", ""],
				JR:		["28 36 50 S", 	"153 37 35 E"],
				LR:		["32 12 32 S", 	"152 34 05 E"],
				LS:		["32 28 35 S", 	"152 32 50 E"],
				MI:		["36 14 30 S", 	"150 13 35 E"],
				MP:		["33 57 45 S", 	"151 15 50 E"],
				PF:		["32 14 25 S", 	"152 36 05 E"],
				SR:		["32 28 00 S", 	"152 33 00 E"],
				SS:		["30 12 30 S", 	"153 17 00 E"],
				TB:		["32 09 10 S", 	"152 32 20 E"],
				WR:		["25 54 40 S", 	"153 12 20 E"],
				TG:		["35 45 20 S", 	"150 15 15 E"],
				BT:		["32 10 75 S",	"152 31 31 E"],
				FC:		["33 24 128 S", "151 32 18 E"],
				FL:		["26 59 00 S", 	"153 29 05 E"],
				GI:		["30 54 45 S", 	"153 05 10 E"],
				SK:		["32 24 30 S", 	"152 32 20 E"],
				SF:   ["", ""],
				NB:		["33 54 05 S", 	"151 16 20 E"],
				CG:		["31 40 55 S", 	"152 54 35 E"],
				DP:		["36 10 00 S",	"150 08 00 E"],
				FT:		["27 08 00 S", 	"153 33 30 E"],
				ST:		["32 26 41 S", 	"152 32 20 E"],
				CC:		["27 07 00 S", 	"153 28 30 E"],
				HE:   ["", ""],
				HU:   ["", ""],
				NM:   ["", ""],
				LF:		["33 44 10 S", 	"151 19 30 E"],
				NS:		["29 55 05 S", 	"153 23 00 E"],
				BH:   ["", ""],
				DD:		["35 02 50 S",	"150 50 43 E"],
				MR:		["31 46 05 S",  "152 48 25 E"],
				FB:		["33 47 96 S",	"151 17 91 E"]
			}
			// a very brittle single-use function, only for parsing the S coords above
			function dmsSToDec(dmsSString) {
				var vals = dmsSS.split(" ");
				var total = parseInt(dmsSString.substring(0,2)) + parseInt(dmsSString.substring(4,6))/60.0 + parseInt(dmsSString.substring(8,10))/3600.0;
				return total
			}
			function dmsEToDec(dmsEString) {
				var total = parseInt(dmsEString.substring(0,3)) + parseInt(dmsEString.substring(5,7))/60.0 + parseInt(dmsEString.subString(9,11))/3600.0;
			}
			function dmsToDec(dmsString) {
				var subs = dmsString.split(" ");
				var sign = 1.0;
				if (subs[3]=="S" || subs[3]=="W") sign = -1.0;
				var total = parseInt(subs[0]) + parseInt(subs[1])/60.0 + parseInt(subs[2])/3600.0;
				return total*sign;
			}
			// if locationKey is in the latLongRaw data
			if (latLongRaw.hasOwnProperty(locationKey)) {
			 	if (latLongRaw[locationKey][0]!="") {
					// set default value on 'latitude' text field in html
					$("#latitude").val(dmsToDec(latLongRaw[locationKey][0]));
					$("#longitude").val(dmsToDec(latLongRaw[locationKey][1]));
			}

		}
	}
		$("#locationID").change( function() {
			var locationKey = this.value.substring(0,2);
			setLatLong(locationKey);
		});

	</script>
    
	<div class="col-xs-12 col-lg-12">
		<h4>Enter geographic coordinates</h4>
    <p class="help-block"><%=props.getProperty("gpsSummary") %> Link:(<a href="http://www.csgnetwork.com/gpscoordconv.html">http://www.csgnetwork.com/gpscoordconv.html</a>)</p>
  </div>
  <div class="col-xs-12 col-lg-6">
		<div class="form-group form-inline">
      <div class="row">
        <div class="col-xs-12 col-md-4">
          <label class="text-danger control-label">Latitude</label>
        </div>
        <div class="col-xs-12 col-lg-8">
          <input class="form-control" name="lat" type="text" id="latitude" size="24" value="<%=default_latitude %>">
        </div>
      </div>
		</div>
  </div>
  <div class="col-xs-12 col-lg-6">
		<div class="form-group form-inline">
      <div class="row">
        <div class="col-xs-12 col-md-4">
          <label class="text-danger control-label">Longitude<br></label>
        </div>
        <div class="col-xs-12 col-lg-8">
          <input class="form-control" name="longitude" type="text" id="longitude" size="24" value="<%=default_longitude %>">
        </div>
      </div>
		</div>
  </div>
<%
}

if(CommonConfiguration.showProperty("showCountry",context)){

%>
    <div class="form-group required">
      <div class="col-xs-6 col-sm-6 col-md-4 col-lg-4">
        <label class="control-label"><%=props.getProperty("country") %></label>
      </div>

      <div class="col-xs-6 col-sm-6 col-md-6 col-lg-8">
        <select name="locationID" id="locationID" class="form-control">
            <option value="" selected="selected"></option>
            <%
            String[] locales = Locale.getISOCountries();
			for (String countryCode : locales) {
				Locale obj = new Locale("", countryCode);
				String currentCountry = obj.getDisplayCountry();
                %>
			<option value="<%=currentCountry %>"><%=currentCountry%></option>
            <%
            }
			%>
   		</select>
      </div>
    </div>

<%
}  //end if showCountry

%>
<br>
<div class="col-xs-12 col-lg-12">
  <h4><%=props.getProperty("mapTitle") %></h4>
  <p class="help-block"><%=props.getProperty("mapSummary") %></p>
</div>


<div style="text-align:center;">
    <p id="map">
    <!--
      <p>Use the arrow and +/- keys to navigate to a portion of the globe,, then click
        a point to set the sighting location. You can also use the text boxes below the map to specify exact
        latitude and longitude.</p>
    -->
    <p id="map_canvas" style="width: 578px; height: 383px; "></p>
    <p id="map_overlay_buttons"></p>
</div>

<hr/>
<!-- ENVIRONMENTAL INFORMATION! -->

<div class="row">

  <div class="col-xs-12 col-lg-12">
    <h3><%=props.getProperty("environmentalHeader") %></h3>
    <p class="help-block"><%=props.getProperty("environmentalSummary") %></p>
  </div>

	<!-- depth && above/below water -->
  <div class="form-group form-inline">
    <div class="col-xs-12 col-md-6">
      <label class="text-danger control-label" style="text-align:left;"><%=props.getProperty("aboveBelow") %></label>
    </div>
    <div class="col-xs-12 col-md-6">
      <input type="radio" name="measurement(underwater)" onChange="aboveWater()" value="1" id="underwaterTrue"></input>
      <label>Underwater</label>
      &nbsp
      <input type="radio" name="measurement(underwater)" onChange="aboveWater()" value="0" id="underwaterFalse" checked></input>
      <input type="hidden" name="measurement(underwaterunits)" value="binary">
      <label>Abovewater</label>
    </div>
  </div>
  
  <div id="depthDiv" style="display:none;" class="form-group form-inline">
    <div class="col-xs-12 col-lg-6">
      <label class="control-label" style="text-align:left;"><%=props.getProperty("submit_depth") %></label>
      <p class="help-block"><%=props.getProperty("depthSummary")%></p>
      <p class="help-block"><%=props.getProperty("depthUnits")%></p>
      <input class="form-control" onChange="convertDepth()" name="feetDepth" type="number" id="feetDepth" value="<%=default_measurement_depth %>">
      <input name="depth" type="hidden" id="depth">
    </div>
  </div>

	<div class="form-group form-inline">
	  <div class="col-xs-12 col-md-6">
      <label class="control-label" style="text-align:left;"><%=props.getProperty("waterTemperature") %></label>
      <p class="help-block"><%=props.getProperty("waterUnits") %></p>
      <input class="form-control" name="measurement(temperature)" type="text" id="temperature" size="24" value="<%=default_measurement_temperature  %>">				
      <input type="hidden" name="measurement(temperatureunits)" value="celsius">
      <br>
		</div>
	</div>

</div>


</fieldset>

<script>

function aboveWater() {
  if (document.getElementById("underwaterTrue").checked) {
    $('#depthDiv').show();
  } else {
    $('#depthDiv').hide();
    $('#depth').val('');
  }
}

function scarOrHook() {
  if (document.getElementById("hasScarTrue").checked) {
    $('#scarringChecks').show();
  } else {
    $('#scarringChecks').hide();
  }
}

$(document).ready(function() {
    if ($('#feetDepth').val().length>0) {
      $('#underwaterFalse').prop("checked", false);
      $('#underwaterTrue').prop("checked", true);
      aboveWater()
    }
});

function convertDepth() {
  console.log("converting depth...");
  var feet;
  if ($('#feetDepth').val()!="") {
    feet = parseInt($('#feetDepth').val());
    meters = (feet*0.3048);
    $('#depth').val(meters);
  }
}

function tookPhoto() {
  if (document.getElementById("photographerNo").checked) {
    $('#photographerData').show();
  } else {
    $('#photographerData').hide();
  }
}

</script>
  
<hr/>

<!-- About  the Shark -->
<fieldset>			
	<div class="row">

    <div class="col-xs-12 col-lg-12">
      <h3><%=props.getProperty("aboutTheSharkHeader") %></h3>
    </div>

    <div class="form-group">
      <div class="col-xs-6 col-md-4">
        <label class="control-label"><%=props.getProperty("status") %></label>
      </div>
      <div class="col-xs-6 col-lg-8">
        <label class="radio-inline">
          <input type="radio" name="livingStatus" value="alive" selected="selected"><%=props.getProperty("alive") %></input>
        </label>
        <label class="radio-inline">
          <input type="radio" name="livingStatus" value="dead"><%=props.getProperty("dead") %></input>
        </label>
      </div>
    </div>

    <div class="form-group">
      <div class="col-xs-6 col-md-4">
        <label class="control-label"><%=props.getProperty("submit_sex") %>:</label>
      </div>
      <div class="col-xs-6 col-lg-8">
        <label class="radio-inline">
          <input name="sex" type="radio" value="unknown" checked="checked"> <%=props.getProperty("submit_unsure") %>
        </label>
        <label class="radio-inline">
          <input type="radio" name="sex" value="male"> <%=props.getProperty("submit_male") %>
        </label>
        <label class="radio-inline">
          <input type="radio" name="sex" value="female"> <%=props.getProperty("submit_female") %>
        </label>
      </div>
    </div>

<%
if(CommonConfiguration.showProperty("showLifestage",context)){
%>
        <div class="form-group">
          <div class="col-xs-6 col-md-4">
            <label class="control-label"><%=props.getProperty("lifeStage") %></label>
          </div>
          <div class="col-xs-6 col-lg-8">
            <%
                      boolean hasMoreStages=true;
                      int stageNum=0;

                      while(hasMoreStages){
                            String currentLifeStage = "lifeStage"+stageNum;
                            if(CommonConfiguration.getProperty(currentLifeStage,context)!=null){
                                %>
                                <label class="radio-inline">
                                  <input type="radio" name="lifeStage" value="<%=CommonConfiguration.getProperty(currentLifeStage,context)%>"><%=CommonConfiguration.getProperty(currentLifeStage,context)%></input>
                                </label>
                                <%
                              stageNum++;
                        }
                        else{
                          hasMoreStages=false;
                        }
                    }
            %>
          </div>
        </div>
<%
}
%>
        <br/>

        <div class="form-group">
          <div class="col-xs-6 col-md-4">
            <label class="control-label"><%=props.getProperty("submit_behavior") %></label>
            <p class="help-block"><%=props.getProperty("behaviorExplanation") %></p>
          </div>
          <div class="col-xs-6 col-lg-8">
            <input class="form-control" name="behavior" type="text" id="behavior" size="48">
          </div>
        </div>

        <div class="form-group">
          <div class="col-xs-12 col-lg-12">
            <label class="text-danger"><%=props.getProperty("hookmarkHead") %></p>
          </div>
          <div class="col-xs-12 col-lg-12">
            <label class="radio-inline">
              <input type="radio" name="hasScar" onChange="scarOrHook()" value="1" id="hasScarTrue">Yes</input>
            </label>
            <label class="radio-inline">
              <input type="radio" name="hasScar" onChange="scarOrHook()" value="0" checked="checked" id="hasScarFalse">No</input>
            </label>
          </div>



          <div id="scarringChecks" class="col-xs-6 col-lg-8" style="display:none;">
            <label class="radio-inline">
              <input type="checkbox" name="hookmark" value="FishingHook">Fishing Hook</input>
            </label>
            
            <label class="radio-inline">
              <input type="checkbox" name="hookmark" value="FishingGear">Fishing Gear</input>
            </label>

            <label class="radio-inline">
              <input type="checkbox" name="hookmark" value="Scarred">Scar(s)</input>
            </label>

            <label class="radio-inline">
              <input type="checkbox" name="hookmark" value="Wounded">Wound(s)</input>
            </label>
            <br>
            <p class="help-block"><%=props.getProperty("hookmarkHelp") %></p>
          </div>
        </div>

	</div>
	</fieldset>


  <hr/>



  <fieldset>

    <div class="form-group">
      <div class="col-xs-6 col-md-4">
        <label class="control-label"><%=props.getProperty("submitterProject") %></label>
      </div>

      <div class="col-xs-6 col-lg-8">
        <input class="form-control" name="submitterProject" type="text" id="submitterProject" size="75" value="<%=project %>">
      </div>
    </div>

    <div class="form-group">
      <div class="col-xs-6 col-md-4">
        <label class="control-label"><%=props.getProperty("submit_comments") %></label>
      </div>

      <div class="col-xs-6 col-lg-8">
        <textarea class="form-control" name="comments" id="comments" rows="5"></textarea>
      </div>
    </div>
  </fieldset>

    <div id="advancedInformation" fade="1">

      <h3><%=props.getProperty("aboutAnimal") %></h3>

 
        <fieldset>
<%

if(CommonConfiguration.showProperty("showTaxonomy",context)){

%>

      <div class="form-group">
          <div class="col-xs-6 col-md-4">
            <label class="control-label"><%=props.getProperty("species") %></label>
          </div>

          <div class="col-xs-6 col-lg-8">
            <select class="form-control" name="genusSpecies" id="genusSpecies">
             	<option value="" selected="selected"><%=props.getProperty("submit_unsure") %></option>
  <%

  					List<String> species=CommonConfiguration.getIndexedPropertyValues("genusSpecies", context);
  					int numGenusSpeciesProps=species.size();
  					String selected="";
  					if(numGenusSpeciesProps==1){selected="selected=\"selected\"";}

                     if(CommonConfiguration.showProperty("showTaxonomy",context)){

                    	for(int q=0;q<numGenusSpeciesProps;q++){
                           String currentGenuSpecies = "genusSpecies"+q;
                           if(CommonConfiguration.getProperty(currentGenuSpecies,context)!=null){
                               %>
                                 <option value="<%=CommonConfiguration.getProperty(currentGenuSpecies,context)%>" <%=selected %>><%=CommonConfiguration.getProperty(currentGenuSpecies,context).replaceAll("_"," ")%></option>
                               <%

                        }


                   }
                   }
 %>
  </select>
    </div>
        </div>

        <%
}

%>

</fieldset>
<%
    pageContext.setAttribute("showMeasurements", CommonConfiguration.showMeasurements(context));
%>
<c:if test="${showMeasurements}">
  <hr>
  <fieldset>


  <div class="form-group">
            <h3>Measurements</h3>


  <div class="col-xs-12 col-lg-8">
    <table class="measurements">
    <tr>
    <th>Type</th><th>Size</th><th>Units</th><th>Sampling Protocol</th>
    </tr>
      <!--the below line makes it so that temp is not listed here (temp is listed above)-->
        <tr>
        <td>Pre-caudal Length</td>
        <td><input name="measurement(precaudallength)" id="precaudallength"/><input type="hidden" name="measurement(precaudallengthunits)" value="centimeters"/></td>
        <td>feet</td>
        
          <td>
            <select name="measurement(precaudallengthsamplingProtocol)">
            
              <option value="samplingProtocol0">Laser measured</option>
            
              <option value="samplingProtocol1">Tape measured length qualifiers</option>
            
            </select>
          </td>
        
        </tr>
          
        <tr>
        <td>Length</td>
        <td><input name="measurement(length)" id="length"/><input type="hidden" name="measurement(lengthunits)" value="centimeters"/></td>
        <td>feet</td>
        
          <td>
            <select name="measurement(lengthsamplingProtocol)">
            
              <option value="samplingProtocol0">Laser measured</option>
            
              <option value="samplingProtocol1">Tape measured length qualifiers</option>
            
            </select>
          </td>
        
        </tr>
            
      
      </table>
      </div>
    </div>
  </fieldset>
</c:if>


      <hr/>

       <fieldset>
        <h3><%=props.getProperty("tags") %></h3>
      <%
  pageContext.setAttribute("showMetalTags", CommonConfiguration.showMetalTags(context));
  pageContext.setAttribute("showAcousticTag", CommonConfiguration.showAcousticTag(context));
  pageContext.setAttribute("showSatelliteTag", CommonConfiguration.showSatelliteTag(context));
  pageContext.setAttribute("metalTags", Util.findMetalTagDescs(langCode,context));
%>

<c:if test="${showMetalTags and !empty metalTags}">

 <div class="form-group">
          <div class="col-xs-6 col-md-4">
            <label><%=props.getProperty("physicalTags") %></label>
          </div>

<div class="col-xs-12 col-lg-8">
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
  </div>
  </div>
</c:if>

<c:if test="${showAcousticTag}">
 <div class="form-group">
          <div class="col-xs-6 col-md-4">
            <label><%=props.getProperty("acousticTag") %></label>
          </div>
<div class="col-xs-12 col-lg-8">
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
    </div>
    </div>
</c:if>

<c:if test="${showSatelliteTag}">
 <div class="form-group">
          <div class="col-xs-6 col-md-4">
            <label><%=props.getProperty("satelliteTag") %></label>
          </div>
<%
  pageContext.setAttribute("satelliteTagNames", Util.findSatelliteTagNames(context));
%>
<div class="col-xs-12 col-lg-8">
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
    </div>
    </div>
</c:if>

      </fieldset>

<hr/>

      <div class="form-group">
        <label class="control-label"><%=props.getProperty("otherEmails") %></label>
        <input class="form-control" name="informothers" type="text" id="informothers" size="75">
        <p class="help-block"><%=props.getProperty("multipleEmailNote") %></p>
      </div>
      </div>


         <%
         if(request.getRemoteUser()==null){
         %>
         <div id="myCaptcha" style="width: 50%;margin: 0 auto; "></div>
           <script>
		         //we need to first check here if we need to do the background social image send... in which case,
		        // we cancel do not do the form submit *here* but rather let the on('load') on the iframe do the task

		       var captchaWidgetId;
		        function onloadCallback() {
		        	captchaWidgetId = grecaptcha.render(

			        	'myCaptcha', {
				  			'sitekey' : '<%=recaptchaProps.getProperty("siteKey") %>',  // required
				  			'theme' : 'light'
						});
		        }





           </script>

        <%
         }
        %>
<script>

function sendButtonClicked() {
	console.log('sendButtonClicked()');
	if (sendSocialPhotosBackground()) return false;
	console.log('fell through -- must be no social!');

    <%
    if(request.getUserPrincipal()!=null){
    %>
    	$("#encounterForm").attr("action", "EncounterForm");
    	submitForm();
    <%
    }
    else{
    %>
    	if(($('#myCaptcha > *').length < 1)){
    	    $("#encounterForm").attr("action", "EncounterForm");
			submitForm();
   		}
   		else{	console.log('Here!'); 	
   			    	var recaptachaResponse = grecaptcha.getResponse( captchaWidgetId );
   					
   					console.log( 'g-recaptcha-response: ' + recaptachaResponse );
   					if(!isEmpty(recaptachaResponse)) {		
   						$("#encounterForm").attr("action", "EncounterForm");
   						
   						if (sendSocialPhotosBackground()) return false;
   						
   						submitForm();
   					}
		}
	//alert(recaptachaResponse);
	<%
    }
	%>
	return true;
}

function locationEntered() {
  console.log("Location Entered?");
  var gpsData = false;
  if ($("#latitude").val().length>1&&$('#longitude').val().length>1) {
    gpsData = true;
    console.log("GPS yes!");
  }
  var locID = false; 
  if ($("#locationID").val().length>0) {
    locID = true;
    console.log("Loc ID yes!");
  }
  var loc = false;
  if ($("#location").val().length>0) {
    loc = true;
    console.log("Loc yes!");
  }
  if (gpsData||locID||loc) {
    return true;
    console.log("Return true.");
  } else {
    alert("Please enter at last one location field.");
    $('#agreementCheckbox').prop('checked', false);
    return false;
  }
}

function requiredEntries() {
  var checked = $('#agreementCheckbox').is(':checked');

  if (checked&&locationEntered()) {
    $('#sendButton').prop("disabled", false);
  } else {
    $('#sendButton').prop("disabled", true);
  }
}
</script>
<br>
	<p class="text-center"><input id="agreementCheckbox" type="checkbox" required onchange="requiredEntries()" name="terms"> I accept the <u><a target="_blank" href="userAgreement.jsp">Terms and Conditions of the User Agreement<span class="text-danger" style="text-decoration:none;"><strong> (required)</strong></span></a></u></p>

      <p class="text-center">
        <button class="large" id="sendButton" type="submit" onclick="return sendButtonClicked();" disabled>
          <%=props.getProperty("submit_send") %>
          <span class="button-icon" aria-hidden="true" />
        </button>
      </p>

<p>&nbsp;</p>
<%if (request.getRemoteUser() != null) {%>
	<input name="submitterID" type="hidden" value="<%=request.getRemoteUser()%>"/>
<%}
else {%>
	<input name="submitterID" type="hidden" value="N/A"/>
<%
}
%>


<p>&nbsp;</p>
</form>

</div>
</div>
</div>

<jsp:include page="footer.jsp" flush="true"/>
