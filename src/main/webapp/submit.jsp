<%@ page contentType="text/html; charset=utf-8"
		import="java.util.GregorianCalendar,
                 org.ecocean.servlet.ServletUtilities,
                 org.ecocean.*,
                 java.util.Properties,
                 java.util.List,java.util.ArrayList,
                 java.util.Locale" %>


<!-- Add reCAPTCHA -->


<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<link href="tools/bootstrap/css/bootstrap.min.css" rel="stylesheet"/>

<link type='text/css' rel='stylesheet' href='javascript/timepicker/jquery-ui-timepicker-addon.css' />
<!-- Select2 CSS -->
<link href="https://cdn.jsdelivr.net/npm/select2@4.1.0-beta.1/dist/css/select2.min.css" rel="stylesheet" />

<!-- jQuery -->
<script src="https://ajax.googleapis.com/ajax/libs/jquery/3.5.1/jquery.min.js"></script>

<!-- Select2 JS -->
<script src="https://cdnjs.cloudflare.com/ajax/libs/select2/4.0.8/js/select2.min.js" defer></script>


<jsp:include page="header.jsp" flush="true"/>

<!-- add recaptcha -->
<script src="https://www.google.com/recaptcha/api.js?render=explicit&onload=onloadCallback"></script>

<!-- https://github.com/exif-js/exif-js -->
<script src="javascript/exif.js"></script>

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


    long maxSizeMB = CommonConfiguration.getMaxMediaSizeInMegabytes(context);
    long maxSizeBytes = maxSizeMB * 1048576;
		//let's pre-populate important info for logged in users
    String submitterName="";
    String submitterEmail="";
    String affiliation= (request.getParameter("organization")!=null) ? request.getParameter("organization") : "";
    String project="";
    Shepherd myShepherd=new Shepherd(context);
		User user = null;
    myShepherd.setAction("submit.jsp1");
    myShepherd.beginDBTransaction();
    String qualifier=ShepherdProperties.getOverwriteStringForUser(request,myShepherd);
    if(qualifier==null) {qualifier="default";}
    else{qualifier=qualifier.replaceAll(".properties","");}
    if(request.getRemoteUser()!=null){
        submitterName=request.getRemoteUser();

        if(myShepherd.getUser(submitterName)!=null){
            user=myShepherd.getUser(submitterName);
            if(user.getFullName()!=null){submitterName=user.getFullName();}
            if(user.getEmailAddress()!=null){submitterEmail=user.getEmailAddress();}
            if(user.getAffiliation()!=null){affiliation=user.getAffiliation();}
            if(user.getUserProject()!=null){project=user.getUserProject();}
        }

    }
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();


    boolean useCustomProperties = User.hasCustomProperties(request); // don't want to call this a bunch


%>

<script>
$(document).ready( function() {  

   $('#locationID').select2({width: '100%', height:'50px'});
   $('#country').select2({width: '100%', height:'50px'});

	populateProjectNameDropdown([],[],"", false, getDefaultSelectedProject(), getDefaultSelectedProjectId(), getLoggedOutDefaultDesired());
	<%
	if(user != null){
		%>
		let userId = '<%= user.getUsername()%>';
		let requestForProjectNames = {};
		//requestForProjectNames['ownerId'] = userId;
		requestForProjectNames['participantId'] = userId;
		doAjaxForProject(requestForProjectNames,userId);
		<%
	}
	else{
		%>

		<%
	}
	%>
});

function populateProjectNameDropdown(options, values, selectedOption, isVisible, defaultSelectItem, defaultSelectItemId, loggedOutDefaultDesired){
	let useCustomStyle = '<%= ServletUtilities.useCustomStyle(request,CommonConfiguration.getDefaultProjectOrganizationParameter(context)) %>' == "true"?true: false;
	if(useCustomStyle){
		//do nothing unusual
	}else{
		defaultSelectItem = null;
		defaultSelectItemId = null;
		loggedOutDefaultDesired = false;
	}
	// if(options.length<1){
	// 	isVisible=false;
	// }
		let projectNameHtml = '';
		projectNameHtml += '<div class="col-xs-6 col-md-4">';
		if(loggedOutDefaultDesired){
			projectNameHtml += '<input type="hidden" name="defaultProject" id="defaultProject" value="' + getDefaultSelectedProjectId() + '" />';
			// console.log("hidden default project selected with name: " + getDefaultSelectedProjectId());
		}
		if(isVisible){
			projectNameHtml += '<label class="control-label "><%=props.getProperty("projectMultiSelectLabel") %></label>';
			projectNameHtml += '<select name="proj-id-dropdown" id="proj-id-dropdown" class="form-control" multiple="multiple">';
		}else{
			projectNameHtml += '<select style="display: none;" name="proj-id-dropdown" id="proj-id-dropdown" class="form-control" multiple="multiple">';
		}
		projectNameHtml += '<option value=""></option>';
		if(defaultSelectItem){
			projectNameHtml += '<option value="' + defaultSelectItemId + '" selected>'+ defaultSelectItem +'</option>';
			options = options.remove(defaultSelectItem);
		}
		for(let i=0; i<options.length; i++){
			if(options[i] === selectedOption){
				projectNameHtml += '<option value="'+ values[i] +'" selected>'+ options[i] +'</option>';
			}else{
				projectNameHtml += '<option value="'+ values[i] + '">'+ options[i] +'</option>';
			}
		}
		projectNameHtml += '</div>';
		$("#proj-id-dropdown-container").empty();
		$("#proj-id-dropdown-container").append(projectNameHtml);
}

Array.prototype.remove = function() {
    var what, a = arguments, L = a.length, ax;
    while (L && this.length) {
        what = a[--L];
        while ((ax = this.indexOf(what)) !== -1) {
            this.splice(ax, 1);
        }
    }
    return this;
};

function getDefaultSelectedProject(){
	let defaultProject = '<%= CommonConfiguration.getDefaultSelectedProject(context) %>';
	return defaultProject;
}

function getDefaultProjectOrganizationParameter(){
	let defaultProjectOrganizationParameter = '<%= CommonConfiguration.getDefaultProjectOrganizationParameter(context) %>';
	return defaultProjectOrganizationParameter;
}

function getDefaultSelectedProjectId(){
	let defaultProjectId = '<%= CommonConfiguration.getDefaultSelectedProjectId(context) %>';
	return defaultProjectId;
}

function getLoggedOutDefaultDesired(){
	let loggedOutDefaultDesired = '<%= CommonConfiguration.getLoggedOutDefaultDesired(context) %>';
	return loggedOutDefaultDesired;
}

function doAjaxForProject(requestJSON,userId){
	$.ajax({
			url: wildbookGlobals.baseUrl + '../ProjectGet',
			type: 'POST',
			data: JSON.stringify(requestJSON),
			dataType: 'json',
			contentType: 'application/json',
			success: function(data) {
				let projectNameResults = data.projects;
				let projNameOptions = null;
				if(projectNameResults){
					projNameOptions = projectNameResults.map(entry =>{return entry.researchProjectName});
					projNameIds = projectNameResults.map(entry =>{return entry.projectIdPrefix});
					populateProjectNameDropdown(projNameOptions,projNameIds,"", true, getDefaultSelectedProject(), getDefaultSelectedProjectId(), getLoggedOutDefaultDesired());
				}
			},
			error: function(x,y,z) {
					console.warn('%o %o %o', x, y, z);
			}
	});
}

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
    }

	.required-missing {
		outline: solid 4px rgba(255,0,0,0.5);
		background-color: #FF0;
	}

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
    <%
	if((CommonConfiguration.getProperty("allowSocialMediaLogin", context)!=null)&&(CommonConfiguration.getProperty("allowSocialMediaLogin", context).equals("true"))){
	%>
	<script src="javascript/pages/submit.js"></script>
	<%
	}
	%>
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

console.log('iframeUrl %o (setting action to EncounterForm)', iframeUrl);
    	$("#encounterForm").attr("action", "EncounterForm");
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
        dateFormat: 'yy-mm-dd'
    });
    $( "#releasedatepicker" ).datepicker( $.datepicker.regional[ "<%=langCode %>" ] );
    $( "#releasedatepicker" ).datepicker( "option", "maxDate", "+1d" );
});

var center = null;
centerMap();

var map;

var marker;

function centerMap(){
  let centerLat = '<%=CommonConfiguration.getCenterLat(context)%>';
  let centerLong = '<%=CommonConfiguration.getCenterLong(context)%>';
  if (centerLat && centerLong) {
    center = new google.maps.LatLng(centerLat, centerLong);
  } else {
    center = new google.maps.LatLng(10.8, 160.8);
  }
}

function updateMap() {
    var latVal = $('#lat').val();
    var lonVal = $('#longitude').val();
    var pt = placeMarkerLatLon(latVal, lonVal);
    if (pt) {
        map.setCenter(pt);
        map.setZoom(5);
    }
}

function placeMarkerLatLon(lat, lon) {  //convenience
    var latFloat = parseFloat(lat);
    var lonFloat = parseFloat(lon);
    if (isNaN(latFloat) || isNaN(lonFloat)) return;
    var pt = new google.maps.LatLng(latFloat, lonFloat);
    if (!pt) return;
    placeMarker(pt);
    return pt;
}

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
        centerMap();
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
                EXIF.getData(inp.files[i], function() { gotExif(this); });
            }
        }
        f = '<b>' + inp.files.length + ' file' + ((inp.files.length == 1) ? '' : 's') + ':</b> ' + all.join(', ');
    } else {
        f = inp.value;
    }
    document.getElementById('input-file-list').innerHTML = f;
}


var dtList = [];
var llList = [];
var commentJson = {};
//TODO Bearing, Altitude
function gotExif(file) {
    exifFindDateTimes(file.exifdata);
console.log('dtList => %o', dtList);
    var dtDiv = $('#dt-div');
    if (!dtDiv.length) {
        dtDiv = $('<div class="exif-derived" id="dt-div" />');
        $('#datepicker').parent().append(dtDiv);
    }
    if (dtList.length > 0) {
        dtList.sort();
        var h = '<%=props.getProperty("fromImageMetadata")%>: <select onChange="return exifDTSet(this);"><option value="">Select date/time</option>';
        for (var i = 0 ; i < dtList.length ; i ++) {
            h += '<option>' + dtList[i] + '</option>';
        }
        h += '</select>';
        dtDiv.html(h);
    }

    exifFindLatLon(file.exifdata);
console.log('llList => %o', llList);
    var llDiv = $('#ll-div');
    if (!llDiv.length) {
        llDiv = $('<div class="exif-derived" id="ll-div" />');
        $('#longitude').parent().parent().append(llDiv);
    }
    if (llList.length > 0) {
        llList.sort();
        var h = '<%=props.getProperty("fromImageMetadata")%>: <select onChange="return exifLLSet(this);"><option value="">Select lat/lon</option>';
        for (var i = 0 ; i < llList.length ; i ++) {
            h += '<option>' + llList[i] + '</option>';
        }
        h += '</select>';
        llDiv.html(h);
    }

    var fj = {};
    if (file.exifdata) for (var key in file.exifdata) {
        if (!key.toLowerCase().match('^(artist|copyright|imagedescription)$')) continue;
        fj[key] = file.exifdata[key];
    }
    if (Object.keys(fj).length) commentJson[file.name] = fj;
    var h = '';
    for (var fname in commentJson) {
        h += '<p class="exif-comment" id="exif-' + fname + '">';
        for (var k in commentJson[fname]) {
            h += k + ': <b>' + commentJson[fname][k] + '</b><br />';
        }
        h += '</p>';
    }
    //$('#comments').val(h);
}


function exifFindDateTimes(exif) {
    for (var key in exif) {
        if (key.toLowerCase().indexOf('date') < 0) continue;
        var clean = cleanupDateTime(exif[key]);
        if (clean && (dtList.indexOf(clean) < 0)) dtList.push(clean);
    }
}

function exifFindLatLon(exif) {
    //unknown if these keys are "standard".  :(  doubt it.
    var lat = cleanupLatLon(exif.GPSLatitudeRef, exif.GPSLatitude);
    var lon = cleanupLatLon(exif.GPSLongitudeRef, exif.GPSLongitude);
    if (!lat || !lon) return;
    var clean = lat + ', ' + lon;
    if (clean && (llList.indexOf(clean) < 0)) llList.push(clean);
}

function cleanupDateTime(dt) {
    var f = dt.split(/\D+/);
    if (f.length == 3) return f.join('-');
    if ((f.length == 5) || (f.length == 6)) return f.slice(0,3).join('-') + ' ' + f.slice(3,6).join(':');
    return null;
}

function cleanupLatLon(llDir, ll) {
    var sign = ((llDir == 'W' || llDir == 'S') ? -1 : 1);
    if (!ll || (ll.length != 3)) return null;
    return Math.round(sign * dms2dd(ll[0], ll[1], ll[2]) * 1000000) / 1000000;
}

function dms2dd(d, m, s) {
    return d + (m / 60) + (s / 3600);
}

function exifDTSet(el) {
    $('#datepicker').val(el.value);
}

function exifLLSet(el) {
    var ll = [ '', '' ];
    if (el.value.indexOf(', ') >= 0) ll = el.value.split(', ');
    $('#lat').val(ll[0]);
    $('#longitude').val(ll[1]);
    updateMap();
}

function showUploadBox() {
    $("#submitsocialmedia").addClass("hidden");
    $("#submitupload").removeClass("hidden");
}

</script>


<fieldset>
<h3><%=props.getProperty("submit_image")%></h3>
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
            <input class="nonIE" name="theFiles" id="theFiles" type="file" accept=".jpg, .jpeg, .png, .bmp, .gif, .mov, .wmv, .avi, .mp4, .mpg" multiple size="30" onChange="updateList(this);" />
            <div><span><%=props.getProperty("dragInstructions")%></span></div>
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

<hr />

<fieldset>
<h3><%=props.getProperty("dateAndLocation")%></h3>

<div class="form-group required">

    <div class="form-group required">

      <div class="form-inline col-xs-12 col-sm-12 col-md-6 col-lg-6">
        <label class="control-label text-danger"><%=props.getProperty("submit_date") %></label>
        <input class="form-control" type="text" style="position: relative; z-index: 101;" id="datepicker" name="datepicker" size="20" />
			</div>

      <div class="col-xs-12 col-sm-12 col-md-6 col-lg-6">
        <p class="help-block">
          <%=props.getProperty("examples") %>
          <ul>
            <li>2014-01-05 12:30</li>
            <li>2014-03-23</li>
            <li>2013-12</li>
            <li>2010</li>
          </ul>
        </p>
      </div>

    </div>

<%
if(CommonConfiguration.showReleaseDate(context)){
%>

    <div class="form-inline col-xs-12 col-sm-12 col-md-6 col-lg-6">
        <label class="control-label text-danger"><%=props.getProperty("submit_releasedate") %></label>
        <input class="hasDatepicker form-control" type="text" style="position: relative; z-index: 101;" id="releasedatepicker" name="releaseDate" size="20" onChange="$('.required-missing').removeClass('required-missing')">
      </div>

<%
}
%>

</fieldset>

<hr />

<fieldset>
    <h3><%=props.getProperty("submit_location")%></h3>

    <p class="help-block"><%=props.getProperty("locationIDMatchNote") %></p>

    <div class="form-group required">
      <div class="col-xs-6 col-sm-6 col-md-4 col-lg-4">
        <label class="control-label text-danger"><%=props.getProperty("where") %></label>
      </div>
      <div class="col-xs-6 col-sm-6 col-md-6 col-lg-8">
        <input name="location" type="text" id="location" size="40" class="form-control">
      </div>
    </div>


    <%

//add locationID to fields selectable

%>
    <div class="form-group required">
      <div class="col-xs-6 col-sm-6 col-md-4 col-lg-4">
        <label class="control-label"><%=props.getProperty("locationID") %></label>
      </div>

      <div class="col-xs-6 col-sm-6 col-md-6 col-lg-8">
          <%=LocationID.getHTMLSelector(false,(String)null,qualifier,"locationID","locationID","form-control") %>

      </div>
    </div>
<%


if(CommonConfiguration.showProperty("showCountry",context)){

%>
          <div class="form-group required">
      <div class="col-xs-6 col-sm-6 col-md-4 col-lg-4">
        <label class="control-label"><%=props.getProperty("country") %></label>
      </div>

      <div class="col-xs-6 col-sm-6 col-md-6 col-lg-8">
        <select name="country" id="country" class="form-control">
          <option value="" selected="selected"></option>
          <%
            List<String> countries = (useCustomProperties)
            ? CommonConfiguration.getIndexedPropertyValues("country", request)
            : CommonConfiguration.getIndexedPropertyValues("country", context); //passing context doesn't check for custom props
            for (String country: countries) {
              %><option value="<%=country%>"><%=country%></option><%
            }%>
        </select>
      </div>
    </div>

<%
}  //end if showCountry

%>
<em><%=props.getProperty("gps_title") %></em>
<div>
    <p id="map">
    <!--
      <p>Use the arrow and +/- keys to navigate to a portion of the globe,, then click
        a point to set the sighting location. You can also use the text boxes below the map to specify exact
        latitude and longitude.</p>
    -->
    <p id="map_canvas" style="width: 578px; height: 383px; "></p>
    <p id="map_overlay_buttons"></p>
</div>

    <div>
      <div class=" form-group form-inline">
        <div class="col-xs-12 col-sm-6">
          <label class="control-label pull-left"><%=props.getProperty("submit_gpslatitude") %>&nbsp;</label>
          <input class="form-control" name="lat" type="text" id="lat"> &deg;
        </div>

        <div class="col-xs-12 col-sm-6">
          <label class="control-label  pull-left"><%=props.getProperty("submit_gpslongitude") %>&nbsp;</label>
          <input class="form-control" name="longitude" type="text" id="longitude"> &deg;
        </div>
      </div>

      <p class="help-block">
        <%=props.getProperty("gpsConverter") %></p>
    </div>


<%
if(CommonConfiguration.showProperty("maximumDepthInMeters",context)){
%>
 <div class="form-inline">
      <label class="control-label"><%=props.getProperty("submit_depth")%></label>
      <input class="form-control" name="depth" type="text" id="depth">
      &nbsp;<%=props.getProperty("submit_meters")%> <br>
    </div>
<%
}

if(CommonConfiguration.showProperty("maximumElevationInMeters",context)){
%>
 <div class="form-inline">
      <label class="control-label"><%=props.getProperty("submit_elevation")%></label>
      <input class="form-control" name="elevation" type="text" id="elevation">
      &nbsp;<%=props.getProperty("submit_meters")%> <br>
    </div>
<%
}
%>

</fieldset>
<hr />

  <fieldset>
    <div class="row">
      <div class="col-xs-12 col-lg-6">
        <h3><%=props.getProperty("aboutYou") %></h3>
        <p class="help-block"><%=props.getProperty("submit_contactinfo") %></p>
        <div class="form-group form-inline" id="test2">
          <div class="col-xs-6 col-md-4">
            <label class="control-label"><%=props.getProperty("submit_name") %></label>
          </div>
          <div class="col-xs-6 col-lg-8">
            <input class="form-control" name="submitterName" type="text" id="submitterName" size="24" value="<%=submitterName %>">
          </div>
        </div>

        <div class="form-group form-inline required" id="test1">
	          <div class="col-xs-6 col-md-4">
	            <label class="control-label"><%=props.getProperty("submit_email") %></label>
	          </div>
	          <div class="col-xs-6 col-lg-8">
	            <input class="form-control" name="submitterEmail" type="text" id="submitterEmail" size="24" value="<%=submitterEmail %>" onChange="$('.required-missing').removeClass('required-missing');">
	          </div>
        </div>
      </div>

      <div class="col-xs-12 col-lg-6">
        <h3><%=props.getProperty("aboutPhotographer") %></h3>

        <div class="form-group form-inline" id="test3">
          <div class="col-xs-6 col-md-4">
            <label class="control-label"><%=props.getProperty("submit_name") %></label>
          </div>
          <div class="col-xs-6 col-lg-8">
            <input class="form-control" name="photographerName" type="text" id="photographerName" size="24">
          </div>
        </div>

        <div class="form-group form-inline" id="test4">
          <div class="col-xs-6 col-md-4">
            <label class="control-label"><%=props.getProperty("submit_email") %></label>
          </div>
          <div class="col-xs-6 col-lg-8">
            <input class="form-control" name="photographerEmail" type="text" id="photographerEmail" size="24">
          </div>
        </div>
      </div>

    </div>
  </fielset>

  <hr/>

  <fieldset>

		<div class="form-group form-inline" id="proj-id-dropdown-container">
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



<%

if(CommonConfiguration.showProperty("showTaxonomy",context)){

%>

      <div class="form-group">
          <div class="col-xs-6 col-md-4">
            <label class="control-label text-danger"><%=props.getProperty("species") %></label>
          </div>

          <div class="col-xs-6 col-lg-8">
            <select class="form-control" name="genusSpecies" id="genusSpecies" onChange="$('.required-missing').removeClass('required-missing'); return true;">
             	<option value="" selected="selected"><%=props.getProperty("submit_unsure") %></option>
  <%

  					List<String> species=CommonConfiguration.getIndexedPropertyValues("genusSpecies", context);
  					int numGenusSpeciesProps=species.size();
  					String selected="";
  					if(numGenusSpeciesProps==1){selected="selected=\"selected\"";}

                     if(CommonConfiguration.showProperty("showTaxonomy",context)){



                    	for(int q=0;q<numGenusSpeciesProps;q++){
                           String currentGenuSpecies = "genusSpecies"+q;

                           String showCommonNames = CommonConfiguration.getProperty("showCommonSpeciesNames",context);

                           if(CommonConfiguration.getProperty(currentGenuSpecies,context)!=null){

                            String commonNameLabel = "";
                             if (showCommonNames!=null&&"true".equals(showCommonNames)) {
                                String commonNameVal = CommonConfiguration.getProperty("commonName"+q,context);
                                if (commonNameVal!=null&&!"null".equals(commonNameVal)&&!"".equals(commonNameVal)) {
                                  commonNameLabel = " ("+commonNameVal+")";
                                }
                              }

                               %>
                                 <option value="<%=CommonConfiguration.getProperty(currentGenuSpecies,context)%>" <%=selected %>><%=CommonConfiguration.getProperty(currentGenuSpecies,context).replaceAll("_"," ")+commonNameLabel%></option>
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



  <h4 class="accordion">
    <a href="javascript:animatedcollapse.toggle('advancedInformation')" style="text-decoration:none">
      <img src="images/Black_Arrow_down.png" width="14" height="14" border="0" align="absmiddle">
      <%=props.getProperty("advancedInformation") %>
    </a>
  </h4>

    <div id="advancedInformation" fade="1" style="display: none;">

      <h3><%=props.getProperty("aboutAnimal") %></h3>

      <fieldset>

        <div class="form-group">
          <div class="col-xs-6 col-md-4">
            <label class="control-label"><%=props.getProperty("submit_sex") %></label>
          </div>

          <div class="col-xs-6 col-lg-8">
            <label class="radio-inline">
              <input type="radio" name="sex" value="male"> <%=props.getProperty("submit_male") %>
            </label>
            <label class="radio-inline">
              <input type="radio" name="sex" value="female"> <%=props.getProperty("submit_female") %>
            </label>
            <label class="radio-inline">
              <input name="sex" type="radio" value="unknown" checked="checked"> <%=props.getProperty("submit_unsure") %>
            </label>
          </div>
        </div>
        </fieldset>
        <hr>
        <fieldset>

  <div class="form-group">
          <div class="col-xs-6 col-md-4">
            <label class="control-label"><%=props.getProperty("status") %></label>
          </div>

          <div class="col-xs-6 col-lg-8">
            <select class="form-control" name="livingStatus" id="livingStatus">
              <option value="alive" selected="selected"><%=props.getProperty("alive") %></option>
              <option value="dead"><%=props.getProperty("dead") %></option>
            </select>
          </div>
        </div>

        <!--
        <div class="form-group">
          <div class="col-xs-6 col-md-4">
            <label class="control-label"><%=props.getProperty("manual_id") %></label>
          </div>

          <div class="col-xs-6 col-lg-8">
            <input class="form-control" name="manualID" type="text" id="manualID" size="75">
          </div>
        </div>
        -->

<!--
				<div class="form-group">
					<div class="col-xs-6 col-md-4">
						<label class="control-label"><%=props.getProperty("alternate_id") %></label>
					</div>

					<div class="col-xs-6 col-lg-8">
						<input class="form-control" name="alternateID" type="text" id="alternateID" size="75">
					</div>
				</div>
-->

        <div class="form-group">
          <div class="col-xs-6 col-md-4">
            <label class="control-label"><%=props.getProperty("occurrence_id") %></label>
          </div>

          <div class="col-xs-6 col-lg-8">
            <input class="form-control" name="occurrenceID" type="text" id="occurrenceID" size="75">
          </div>
        </div>

        <div class="form-group">
          <div class="col-xs-6 col-md-4">
            <label class="control-label"><%=props.getProperty("submit_behavior") %></label>
          </div>

          <div class="col-xs-6 col-lg-8">
            <input class="form-control" name="behavior" type="text" id="behavior" size="75">
          </div>
        </div>



           <div class="form-group">
          <div class="col-xs-6 col-md-4">
            <label class="control-label"><%=props.getProperty("submit_scars") %></label>
          </div>

          <div class="col-xs-6 col-lg-8">
            <input class="form-control" name="scars" type="text" id="scars" size="75">
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
<%
    pageContext.setAttribute("items", Util.findMeasurementDescs(langCode,context));
    pageContext.setAttribute("samplingProtocols", Util.findSamplingProtocols(langCode,context));
%>

 <div class="form-group">
           <h3><%=props.getProperty("measurements") %></h3>


<div class="col-xs-12 col-lg-8">
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
	// $('.required-missing').removeClass('required-missing')
	// if an mediaAsset is ever required
	// if(!$('#theFiles').val()){
	// 	console.log("No file submitted!");
	// 	$('#theFiles').closest('.form-group').addClass('required-missing');
	// 	window.setTimeout(function() { alert('You must provide a photo or video.'); }, 100);
	// 	return false;
	// }
	if(!$('#location').val() && !$('#locationID').val() && (!$('#lat').val() || !$('#longitude').val())){
		$('#location').closest('.form-group').addClass('required-missing');
		window.setTimeout(function() { alert('You must provide some kind of location information.'); }, 100);
		return false;
	}

	if ($('#submitterEmail').val()) {
			var email = $('#submitterEmail').val();
	    var re = /^(([^<>()\[\]\\.,;:\s@"]+(\.[^<>()\[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;
	    if(!re.test(email.toLowerCase())){
				$('#submitterEmail').closest('.form-group').addClass('required-missing');
				window.setTimeout(function() { alert('Please provide a valid email address.'); }, 100);
				return false;
			}
	}

	// if (!$('#submitterEmail').val()) { //TODO comment back in if you want email address required in addition to validated
	// 	// console.log("email address not present");
	// 	$('#submitterEmail').parents('.form-group').addClass('required-missing');
	// 	window.setTimeout(function() { alert('You must provide an email address first.'); }, 100);
	// 	return false;
	// }else{
	// 	var email = $('#submitterEmail').val();
  //   var re = /^(([^<>()\[\]\\.,;:\s@"]+(\.[^<>()\[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;
  //   if(!re.test(email.toLowerCase())){
	// 		console.log("not a valid email address");
	// 		$('#submitterEmail').closest('.form-group').addClass('required-missing');
	// 		window.setTimeout(function() { alert('You must provide a valid email address first.'); }, 100);
	// 		return false;
	// 	}
  // }

	if (!$('#datepicker').val()) {
		$('#datepicker').closest('.form-group').addClass('required-missing');
		window.setTimeout(function() { alert('You must set a date first.'); }, 100);
		return false;
	}

	if (!$('#genusSpecies').val()) {
		$('#genusSpecies').closest('.form-group').addClass('required-missing');
		window.setTimeout(function() { alert('You must set a species first.'); }, 100);
		return false;
	}

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
</script>


      <p class="text-center">
        <button id="submitEncounterButton" class="large" type="submit" onclick="sendButtonClicked();">
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
