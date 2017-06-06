<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.*, org.ecocean.servlet.ServletUtilities, java.awt.Dimension, java.io.File, java.util.*, java.util.concurrent.ThreadPoolExecutor, javax.servlet.http.HttpSession" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>

<jsp:include page="header.jsp" flush="true"/>


<%
  String context="context0";
  context=ServletUtilities.getContext(request);
  String number = request.getParameter("number").trim();
  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("confirmSubmit.jsp");
	//HttpSession session = request.getSession(false);

  String filesOKMessage = "";
  if (session.getAttribute("filesOKMessage") != null) { filesOKMessage = session.getAttribute("filesOKMessage").toString(); }
  String filesBadMessage = "";
  if (session.getAttribute("filesBadMessage") != null) { filesBadMessage = session.getAttribute("filesBadMessage").toString(); }

//setup our Properties object to hold all properties
  Properties props = new Properties();
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);

  String mapKey = CommonConfiguration.getGoogleMapsKey(context);
  
  //set up the file input stream
  //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/submit.properties"));
  props = ShepherdProperties.getProperties("submit.properties", langCode,context);



  //email_props.load(getClass().getResourceAsStream("/bundles/confirmSubmitEmails.properties"));


  //link path to submit page with appropriate language
  String submitPath = "submit.jsp";
  
  //let's set up references to our file system components
  String rootWebappPath = getServletContext().getRealPath("/");
  File webappsDir = new File(rootWebappPath).getParentFile();
  File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
  if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
  File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
  if(!encountersDir.exists()){encountersDir.mkdirs();}
  File thisEncounterDir = null;// = new File();  //gets set after we have encounter



%>
<script src="//maps.google.com/maps/api/js?key=<%=mapKey%>&language=<%=langCode%>"></script>

<div class="container maincontent">
<%

  StringBuffer new_message = new StringBuffer();
  new_message.append("<html><body>");
  new_message.append("The "+CommonConfiguration.getProperty("htmlTitle",context)+" library has received a new encounter submission. You can " +
    "view it at:<br>" + CommonConfiguration.getURLLocation(request) +
    "/encounters/encounter" +
    ".jsp?number="+ number);
  new_message.append("<br><br>Quick stats:<br>");
  String photographer = "None";
  boolean emailPhoto = false;
  //get all needed DB reads out of the way in case Dynamic Image fails
  String addText = "";
  boolean hasImages = true;
  String submitter = "";
  String informOthers = "";
  String informMe = "";
  String rootDir = getServletContext().getRealPath("/");
  String baseDir = ServletUtilities.dataDir(context, rootDir);

  Encounter enc = null;
  if (!number.equals("fail")) {
    myShepherd.beginDBTransaction();
    try {
      enc = myShepherd.getEncounter(number);
      
      
			thisEncounterDir = new File(enc.dir(baseDir));
			String thisEncDirString=Encounter.dir(shepherdDataDir,enc.getCatalogNumber());
			thisEncounterDir=new File(thisEncDirString);
			if(!thisEncounterDir.exists()){thisEncounterDir.mkdirs();System.out.println("I am making the encDir: "+thisEncDirString);}
			
			
			
      if ((enc.getAdditionalImageNames() != null) && (enc.getAdditionalImageNames().size() > 0)) {
        addText = (String)enc.getAdditionalImageNames().get(0);
      }
      if ((enc.getLocationCode() != null) && (!enc.getLocationCode().equals("None"))) {
        
    	  
    	  //the old way was to load a list of email addresses from a properties files using the locationID as the property key
    	  //informMe = email_props.getProperty(enc.getLocationCode());
        
        //the new way loads email addresses based on User object roles matching location ID
        informMe=myShepherd.getAllUserEmailAddressesForLocationID(enc.getLocationID(),context);
        
        
      } else {
        hasImages = false;
      }
      new_message.append("Location: " + enc.getLocation() + "<br>");
      new_message.append("Date: " + enc.getDate() + "<br>");
      if(enc.getSex()!=null){
      	new_message.append("Sex: " + enc.getSex() + "<br>");
      }
      new_message.append("Submitter: " + enc.getSubmitterName() + "<br>");
      new_message.append("Email: " + enc.getSubmitterEmail() + "<br>");
      new_message.append("Photographer: " + enc.getPhotographerName() + "<br>");
      new_message.append("Email: " + enc.getPhotographerEmail() + "<br>");
      new_message.append("Comments: " + enc.getComments() + "<br>");
      new_message.append("</body></html>");
      submitter = enc.getSubmitterEmail();
      if ((enc.getPhotographerEmail() != null) && (!enc.getPhotographerEmail().equals("None")) && (!enc.getPhotographerEmail().equals(""))) {
        photographer = enc.getPhotographerEmail();
        emailPhoto = true;
      }

      if ((enc.getInformOthers() != null) && (!enc.getInformOthers().equals(""))) {
        informOthers = enc.getInformOthers();
      }

    } catch (Exception e) {
      System.out.println("Error encountered in confirmSubmit.jsp:");
      e.printStackTrace();
    }
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
    
  }

  String thumbLocation = thisEncounterDir.getAbsolutePath() + "/thumb.jpg";
  if (myShepherd.isAcceptableVideoFile(addText)) {
    addText = rootWebappPath+"/images/video_thumb.jpg";
  } 
  else if(myShepherd.isAcceptableImageFile(addText)){
    addText = thisEncounterDir.getAbsolutePath() + "/" + addText;
  }
  else if(addText.equals("")){
	  addText = rootWebappPath+"/images/no_images.jpg";
  }

  //File file2process = new File(getServletContext().getRealPath(("/" + addText)));

  File file2process = new File(addText);
  File thumbFile = new File(thumbLocation.substring(5));
  


  if(file2process.exists() && myShepherd.isAcceptableImageFile(file2process.getName())){
  	int intWidth = 100;
  	int intHeight = 75;
  	int thumbnailHeight = 75;
  	int thumbnailWidth = 100;

  
  	String height = "";
  	String width = "";

  	Dimension imageDimensions = org.apache.sanselan.Sanselan.getImageSize(file2process);

    width = Double.toString(imageDimensions.getWidth());
    height = Double.toString(imageDimensions.getHeight());

    intHeight = ((new Double(height)).intValue());
    intWidth = ((new Double(width)).intValue());

	  if (intWidth > thumbnailWidth) {
	    double scalingFactor = intWidth / thumbnailWidth;
	    intWidth = (int) (intWidth / scalingFactor);
	    intHeight = (int) (intHeight / scalingFactor);
	    if (intHeight < thumbnailHeight) {
	      thumbnailHeight = intHeight;
	    }
	  } else {
	    thumbnailWidth = intWidth;
	    thumbnailHeight = intHeight;
	  }
   
  }  
  
  // I'm Not sure what the heck is going on with the code above this block. 
  // It looks like it does some things i need, but every time I touch it something explodes.
  // This block is just grabbing thumbs to display. It just kinda plays quietly by itself.
  // It's hacky. I know.
  Encounter enc2 = null;
  try {
	  myShepherd.beginDBTransaction();
	  enc2 = myShepherd.getEncounter(number);
	  myShepherd.commitDBTransaction();
  } catch (Exception e) {
	  e.printStackTrace();
  }
 String rootDir2 = getServletContext().getRealPath("/");
 System.out.println("RootDir : "+rootDir2);
 String baseDir2 = ServletUtilities.dataDir(context, rootDir2);
 System.out.println("BaseDir2 : "+baseDir2);
 String assetURLs = "<br>";
 File folder = new File(thisEncounterDir.getAbsolutePath());
 System.out.println("Folder : "+folder.toString());
 String swap = folder.toString();
 swap = swap.replaceAll("encounters/", "");
 folder = new File(swap);
 try {
 	File[] imageList = folder.listFiles();
 	ArrayList<String> thumbs = new ArrayList<String>();
 	int l = imageList.length;
 	if (l > 0) {
	  	for (int i = 0; i < l; i++ ) {
	  		System.out.println("LENGTH : "+ imageList.length);
	   		if (imageList[i].isFile()) {
	    		System.out.println("IS FILE : "+ imageList[i].isFile());
	    		File f = imageList[i];
	    		System.out.println("Contents : "+ f);
	    		int lngth = f.getName().length();
	    		String suffix = f.getName().substring(lngth - 7).trim();
	    		System.out.println("Suffix : "+suffix);
	    		if (suffix.equals("mid.jpg")) {
	    			swap = f.toString();
	    			swap = swap.replaceAll("/opt/tomcat/webapps/", "");
	  	    		assetURLs = assetURLs + "<div class=\"col-xs-2\"><img class=\"new-thumb\" src=\""+swap+"\"/></div>";
	  	    		System.out.println("ASSET URLS : "+assetURLs);
	    		}
	   		}
	  }
  }	
} catch (Exception e) {
	e.printStackTrace();
}
  // End thumb block. 
%>


<div border="0" fillPaint="#ffffff"
        output="<%=thumbLocation%>" expAfter="0" threading="limited" align="left" valign="left"> 
</div>

<h1 class="intro"><%=props.getProperty("success") %></h1>

<p><strong><%=props.getProperty("thankYou") %></strong></p>

<p><strong><%=props.getProperty("confirmFilesOK") %>:</strong> <%=filesOKMessage %></p>
<p><strong><%=props.getProperty("confirmFilesBad") %>:</strong> <%=filesBadMessage %></p>

<p><%=props.getProperty("futureReference") %> <strong><%=number%></strong>.</p>

<%=props.getProperty("questions") %> <a href="mailto:<%=CommonConfiguration.getAutoEmailAddress(context) %>"><%=CommonConfiguration.getAutoEmailAddress(context) %></a></p>

<p>
	
	<a href="//<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=number%>"><%=props.getProperty("viewEncounter") %> <%=number%></a>
</p>
<p>
	<%=props.getProperty("emailExplanation") %>
</p>
<%if (!assetURLs.equals("<br>")) { %>
<div class="row"id="thumbList"> 
	<%=assetURLs%>
</div>
<br>
	<label><%=props.getProperty("yourMedia") %></label>
<%}%>





<%
// Let's display a google map for the encounter if it was submitted with coordinates.

Double mapLon = -1.0000;
Double mapLat = -1.0000;
if (enc2.getLatitudeAsDouble() != null && enc2.getLongitudeAsDouble() !=null) {
	System.out.println("LAT FROM SUBMIT : "+Double.toString(enc2.getLatitudeAsDouble()));
	System.out.println("LON FROM SUBMIT : "+Double.toString(enc2.getLatitudeAsDouble()));	
	mapLat = enc2.getLatitudeAsDouble();
	mapLon = enc2.getLongitudeAsDouble();
} else {
	System.out.println("NO LAT OR LON FOUND!");
}
%>

<br>
<%
if(!mapLon.equals(null) && !mapLat.equals(null) && !mapLon.equals(-1.0000) && !mapLat.equals(-1.0000)) {
%>
	<p><%=props.getProperty("confirmSubmitMapNote") %></p>
<%
}
%>

<div id="map_canvas" style="width: 510px; height: 350px; overflow: hidden;"></div>

<script>

$(function() {
  function resetMap() {
      var ne_lat_element;
      var ne_long_element;
	  
	  
	  ne_lat_element = <%=mapLat%>;
	  ne_long_element = <%=mapLon%>;
  
      ne_lat_element.value = -1.00000;
   	  ne_long_element.value = -1.00000;
	  


    }

    $(window).unload(resetMap);

    //
    // Call it now on page load.
    //
    resetMap();
});
var center = new google.maps.LatLng(<%=mapLat%>,<%=mapLon%>);
var map;
var marker;
var newCenter;
var mapzoom;

function placeMarker(location) {
    if(marker!=null){marker.setMap(null);}
    marker = new google.maps.Marker({
          position: center,
          map: map
      });

      //map.setCenter(location);

        var ne_lat_element = <%=mapLat%>;
        var ne_long_element = <%=mapLon%>;


        ne_lat_element.value = center.lat();
        ne_long_element.value = center.lng();
    }

  function initialize() {
    mapZoom = 6;
    if($("#map_canvas").hasClass("full_screen_map")){mapZoom=3;}


    if(marker!=null){
        center = new google.maps.LatLng(<%=mapLat%>,<%=mapLon%>);
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
      
      
 	 google.maps.event.addListener(map, 'dragend', function() {
 		var idleListener = google.maps.event.addListener(map, 'idle', function() {
 			google.maps.event.removeListener(idleListener);
 			console.log("GetCenter : "+map.getCenter());
 			mapZoom = map.getZoom();
 			newCenter = map.getCenter();
 			center = newCenter;
 			map.setCenter(map.getCenter());
 		});
 		 
	 }); 	 
 	 
 	 google.maps.event.addDomListener(window, "resize", function() {	 
	    	console.log("Resize Center : "+center);
	    	google.maps.event.trigger(map, "resize");
	  	    console.log("Resize : "+newCenter);
	  	    map.setCenter(center);
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

</script>

<%
// If there are no valid coordinates then we won't render the message or gmao canvas at all by disabling the listener.
if(!mapLon.equals(null) && !mapLat.equals(null) && !mapLon.equals(-1.0000) && !mapLat.equals(-1.0000)) {
%>
 	<script>google.maps.event.addDomListener(window, 'load', initialize);</script>	
<%
}
%>

<%
if(CommonConfiguration.sendEmailNotifications(context)){

  // Retrieve background service for processing emails
  ThreadPoolExecutor es = MailThreadExecutorService.getExecutorService();

  // Email new submission address(es) defined in commonConfiguration.properties
  Map<String, String> tagMap = NotificationMailer.createBasicTagMap(request, enc);
  List<String> mailTo = NotificationMailer.splitEmails(CommonConfiguration.getNewSubmissionEmail(context));
  String mailSubj = "New encounter submission: " + number;
  for (String emailTo : mailTo) {
    NotificationMailer mailer = new NotificationMailer(context, langCode, emailTo, "newSubmission-summary", tagMap);
    mailer.setUrlScheme(request.getScheme());
    es.execute(mailer);
  }

  // Email those assigned this location code
  if (informMe != null) {
    List<String> cOther = NotificationMailer.splitEmails(informMe);
    for (String emailTo : cOther) {
    	NotificationMailer mailer = new NotificationMailer(context, null, emailTo, "newSubmission-summary", tagMap);
    	mailer.setUrlScheme(request.getScheme());
      	es.execute(mailer);
    }
  }

  // Add encounter dont-track tag for remaining notifications (still needs email-hash assigned).
  tagMap.put(NotificationMailer.EMAIL_NOTRACK, "number=" + enc.getCatalogNumber());

  // Email submitter and photographer
  if (submitter != null) {
    List<String> cOther = NotificationMailer.splitEmails(submitter);
    for (String emailTo : cOther) {
      String msg = CommonConfiguration.appendEmailRemoveHashString(request, "", emailTo, context);
      tagMap.put(NotificationMailer.EMAIL_HASH_TAG, Encounter.getHashOfEmailString(emailTo));
      NotificationMailer mailer=new NotificationMailer(context, null, emailTo, "newSubmission", tagMap);
      mailer.setUrlScheme(request.getScheme());
      es.execute(mailer);
    }
  }
  if (emailPhoto && photographer != null) {
    List<String> cOther = NotificationMailer.splitEmails(photographer);
    for (String emailTo : cOther) {
      String msg = CommonConfiguration.appendEmailRemoveHashString(request, "", emailTo, context);
      tagMap.put(NotificationMailer.EMAIL_HASH_TAG, Encounter.getHashOfEmailString(emailTo));
      NotificationMailer mailer=new NotificationMailer(context, null, emailTo, "newSubmission", tagMap);
      mailer.setUrlScheme(request.getScheme());
      es.execute(mailer);
    }
  }

  // Email interested others
  if (informOthers != null) {
    List<String> cOther = NotificationMailer.splitEmails(informOthers);
    for (String emailTo : cOther) {
      String msg = CommonConfiguration.appendEmailRemoveHashString(request, "", emailTo, context);
      tagMap.put(NotificationMailer.EMAIL_HASH_TAG, Encounter.getHashOfEmailString(emailTo));
      NotificationMailer mailer=new NotificationMailer(context, null, emailTo, "newSubmission", tagMap);
      mailer.setUrlScheme(request.getScheme());
      es.execute(mailer);
    }
  }
  es.shutdown();
}

myShepherd=null;

%>
</div>

<jsp:include page="footer.jsp" flush="true"/>

