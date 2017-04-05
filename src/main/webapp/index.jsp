<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
              org.ecocean.servlet.ServletUtilities,
              java.util.List,
              java.util.Map,
              java.util.Iterator,
              java.util.Properties,
              java.util.StringTokenizer,
              javax.jdo.Query
              "
%>



<jsp:include page="header.jsp" flush="true"/>

<%
String context=ServletUtilities.getContext(request);

//set up our Shepherd

Shepherd myShepherd=null;
myShepherd=new Shepherd(context);


//check for and inject a default user 'tomcat' if none exists

  	//check usernames and passwords
	myShepherd.beginDBTransaction();
  	List<User> users=myShepherd.getAllUsers();
  	if(users.size()==0){
  		String salt=ServletUtilities.getSalt().toHex();
        String hashedPassword=ServletUtilities.hashAndSaltPassword("tomcat123", salt);
        //System.out.println("Creating default hashed password: "+hashedPassword+" with salt "+salt);


  		User newUser=new User("tomcat",hashedPassword,salt);
  		myShepherd.getPM().makePersistent(newUser);
  		System.out.println("Creating tomcat user account...");
  		myShepherd.commitDBTransaction();

  	  	List<Role> roles=myShepherd.getAllRoles();
  	  	if(roles.size()==0){

  	  		myShepherd.beginDBTransaction();
  	  		System.out.println("Creating tomcat roles...");

  	  		Role newRole1=new Role("tomcat","admin");
  	  		newRole1.setContext("context0");
  	  		myShepherd.getPM().makePersistent(newRole1);
	  		Role newRole4=new Role("tomcat","destroyer");
	  		newRole4.setContext("context0");
	  		myShepherd.getPM().makePersistent(newRole4);

			Role newRole7=new Role("tomcat","rest");
	  		newRole7.setContext("context0");
	  		myShepherd.getPM().makePersistent(newRole7);

			myShepherd.commitDBTransaction();


	  		System.out.println("Creating tomcat user account...");
  	  	}
  	}

// Here I'll do some preliminary stuff for


%>

<script src="http://maps.google.com/maps/api/js?sensor=false"></script>


<script src="cust/mantamatcher/js/google_maps_style_vars.js"></script>
<script src="cust/mantamatcher/js/richmarker-compiled.js"></script>



  <script type="text/javascript">

//Define the overlay, derived from google.maps.OverlayView
  function Label(opt_options) {
   // Initialization
   this.setValues(opt_options);

   // Label specific
   var span = this.span_ = document.createElement('span');
   span.style.cssText = 'font-weight: bold;' +
                        'white-space: nowrap; ' +
                        'padding: 2px; z-index: 999 !important;';
   span.style.zIndex=999;

   var div = this.div_ = document.createElement('div');
   div.style.zIndex=999;

   div.appendChild(span);
   div.style.cssText = 'position: absolute; display: none;z-index: 999 !important;';
  };
  Label.prototype = new google.maps.OverlayView;

  // Implement onAdd
  Label.prototype.onAdd = function() {
   var pane = this.getPanes().overlayLayer;
   pane.appendChild(this.div_);

   // Ensures the label is redrawn if the text or position is changed.
   var me = this;
   this.listeners_ = [
     google.maps.event.addListener(this, 'position_changed',
         function() { me.draw(); }),
     google.maps.event.addListener(this, 'text_changed',
         function() { me.draw(); })
   ];
  };

  // Implement onRemove
  Label.prototype.onRemove = function() {
   this.div_.parentNode.removeChild(this.div_);

   // Label is removed from the map, stop updating its position/text.
   for (var i = 0, I = this.listeners_.length; i < I; ++i) {
     google.maps.event.removeListener(this.listeners_[i]);
   }
  };

  // Implement draw
  Label.prototype.draw = function() {
   var projection = this.getProjection();
   var position = projection.fromLatLngToDivPixel(this.get('position'));

   var div = this.div_;
   div.style.left = position.x + 'px';
   div.style.top = position.y + 'px';
   div.style.display = 'block';
   div.style.zIndex=999;

   this.span_.innerHTML = this.get('text').toString();
  };


  		//map
  		var map;
  		var bounds = new google.maps.LatLngBounds();

      function initialize() {


    	// Create an array of styles for our Goolge Map.
  	    //var gmap_styles = [{"stylers":[{"visibility":"off"}]},{"featureType":"water","stylers":[{"visibility":"on"},{"color":"#00c0f7"}]},{"featureType":"landscape","stylers":[{"visibility":"on"},{"color":"#005589"}]},{"featureType":"administrative","elementType":"geometry.stroke","stylers":[{"visibility":"on"},{"color":"#00c0f7"},{"weight":1}]}]


        var center = new google.maps.LatLng(0,0);
        var mapZoom = 8;
    	if($("#map_canvas").hasClass("full_screen_map")){mapZoom=3;}


        map = new google.maps.Map(document.getElementById('map_canvas'), {
          zoom: mapZoom,
          center: center,
          mapTypeId: google.maps.MapTypeId.HYBRID,
          zoomControl: true,
          scaleControl: false,
          scrollwheel: false,
          disableDoubleClickZoom: true,
        });

    	  //adding the fullscreen control to exit fullscreen
    	  var fsControlDiv = document.createElement('DIV');
    	  var fsControl = new FSControl(fsControlDiv, map);
    	  fsControlDiv.index = 1;
    	  map.controls[google.maps.ControlPosition.TOP_RIGHT].push(fsControlDiv);


    	    // Create a new StyledMapType object, passing it the array of styles,
    	    // as well as the name to be displayed on the map type control.
    	    var styledMap = new google.maps.StyledMapType(gmap_styles, {name: "Styled Map"});

    	    //Associate the styled map with the MapTypeId and set it to display.
    	    map.mapTypes.set('map_style', styledMap);
    	    map.setMapTypeId('map_style');

        var markers = [];
 	    var movePathCoordinates = [];

 	    //iterate here to add points per location ID

 		var maxZoomService = new google.maps.MaxZoomService();
 		maxZoomService.getMaxZoomAtLatLng(map.getCenter(), function(response) {
 			    if (response.status == google.maps.MaxZoomStatus.OK) {
 			    	if(response.zoom < map.getZoom()){
 			    		map.setZoom(response.zoom);
 			    	}
 			    }

 		});


 		//let's add map points for our locationIDs
 		<%
 		List<String> locs=CommonConfiguration.getSequentialPropertyValues("locationID", context);
 		int numLocationIDs = locs.size();
 		Properties locProps=ShepherdProperties.getProperties("locationIDGPS.properties", "", context);
 		myShepherd.beginDBTransaction();
 		try{
	 		for(int i=0;i<numLocationIDs;i++){

	 			String locID = locs.get(i);
	 			if((locProps.getProperty(locID)!=null)&&(locProps.getProperty(locID).indexOf(",")!=-1)){

	 				StringTokenizer st = new StringTokenizer(locProps.getProperty(locID), ",");
	 				String lat = st.nextToken();
	 				String longit=st.nextToken();
	 				String thisLatLong=lat+","+longit;

	 		        //now  let's calculate how many
	 		        int numSightings=myShepherd.getNumEncounters(locID);
	 		        if(numSightings>0){

	 		        	Integer numSightingsInteger=new Integer(numSightings);


	 		          %>

	 		         var latLng<%=i%> = new google.maps.LatLng(<%=thisLatLong%>);
			          bounds.extend(latLng<%=i%>);

	 		          var divString<%=i%> = "<div style=\"padding-top: 25px;font-weight:bold;text-align: center;line-height: 35px;vertical-align: middle;width:60px;height:49px; background-image: url('//www.whaleshark.org/cust/mantamatcher/img/fin-silhouette.svg');background-size: cover\"><a href=\"http://www.whaleshark.org/encounters/searchResults.jsp?locationCodeField=<%=locID %>\"><%=numSightingsInteger.toString() %></a></div>";
	 		          //http://www.flukebook.org/cust/mantamatcher/img/manta-silhouette.png

	 		         var marker<%=i%> = new RichMarker({
	 		            position: latLng<%=i%>,
	 		            map: map,
	 		            draggable: false,
	 		           content: divString<%=i%>,
	 		           flat: true
	 		        });



	 			      markers.push(marker<%=i%>);
	 		          map.fitBounds(bounds);

	 				<%
	 			} //end if

	 			}  //end if

	 		}  //end for
 		}
 		catch(Exception e){e.printStackTrace();}
 		finally{
 			//myShepherd.rollbackDBTransaction();
 			//myShepherd.closeDBTransaction();
 		}
 	 	%>


 	 } // end initialize function

      function fullScreen(){
  		$("#map_canvas").addClass('full_screen_map');
  		$('html, body').animate({scrollTop:0}, 'slow');
  		initialize();

  		//hide header
  		$("#header_menu").hide();

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
  	  //controlDiv.appendChild(controlUI);

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
  	  controlText.style.visibility='hidden';
  	  //toggle the text of the button

  	  if($("#map_canvas").hasClass("full_screen_map")){
  	      controlText.innerHTML = 'Exit Fullscreen';
  	    } else {
  	      controlText.innerHTML = 'Fullscreen';
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
    google.maps.event.addDomListener(window, "resize", function() {
    	 var center = map.getCenter();
    	 google.maps.event.trigger(map, "resize");
    	 map.setCenter(center);
    	});




  </script>

<%


//let's quickly get the data we need from Shepherd

int numMarkedIndividuals=0;
int numEncounters=0;
int numDataContributors=0;
int numMarkedIndividualsLeftFlank=0;
int numEncountersLeftFlank=0;

try {

    myShepherd.beginDBTransaction();

    numMarkedIndividuals=myShepherd.getNumMarkedIndividuals();
    numEncounters=myShepherd.getNumEncounters();
    numDataContributors=myShepherd.getNumUsers();

    numEncountersLeftFlank = myShepherd.getNumEncountersLeftFlank();
    numMarkedIndividualsLeftFlank = myShepherd.getNumMarkedIndividualsLeftFlank();

}
catch(Exception e){
    e.printStackTrace();
}
finally{
    if(myShepherd!=null){
        if(myShepherd.getPM()!=null){
            myShepherd.rollbackDBTransaction();
            if(!myShepherd.getPM().isClosed()){myShepherd.closeDBTransaction();}
        }
    }
}
%>

<section class="hero container-fluid main-section relative">
    <div class="container relative">
        <div class="col-xs-12 col-sm-10 col-md-8 col-lg-6">
            <h1 class="hidden">Spotashark</h1>
            <h2>Your photos will help identify and protect the critically endangered grey nurse shark.</h2>
            <!--
            <button id="watch-movie" class="large light">
				Watch the movie
				<span class="button-icon" aria-hidden="true">
			</button>
			-->
            <a href="submit.jsp">
                <button class="large">Report encounter<span class="button-icon" aria-hidden="true"></button>
            </a>
        </div>

	</div>
	 <div class="video-wrapper">
		<div class="embed-container">
			<iframe id="herovideo" src="http://player.vimeo.com/video/123083341?api=1&amp;player_id=herovideo" frameborder="0" webkitAllowFullScreen mozallowfullscreen allowFullScreen></iframe>
		</div>
	</div>
    <p id="image-credit"><i>photo credit Jayne Jenkins</i></p>
</section>

<section class="container text-center main-section">

	<h2 class="section-header">How it works</h2>

	<div id="howtocarousel" class="carousel slide" data-ride="carousel">
		<ol class="list-inline carousel-indicators slide-nav">
	        <li data-target="#howtocarousel" data-slide-to="0" class="active">1. Photograph a shark<span class="caret"></span></li>
	        <li data-target="#howtocarousel" data-slide-to="1" class="">2. Submit a photo<span class="caret"></span></li>
	        <li data-target="#howtocarousel" data-slide-to="2" class="">3. Researcher verification<span class="caret"></span></li>
	        <li data-target="#howtocarousel" data-slide-to="3" class="">4. Matching process<span class="caret"></span></li>
	        <li data-target="#howtocarousel" data-slide-to="4" class="">5. Match result<span class="caret"></span></li>
	    </ol>
		<div class="carousel-inner text-left">
			<div class="item active">
				<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
					<h3>Photograph a Shark</h3>
					<p class="lead">
						Every shark has a unique spot pattern on each flank. A photo of a flank can be matched to another photo of the same flank. Or your shark may be a new addition to our database.
					</p>
					<!--
					<p class="lead">
						<a href="photographing.jsp" title="">See the photography guide</a>
					</p>-->
				</div>
				<div class="col-xs-12 col-sm-4 col-sm-offset-2 col-md-4 col-md-offset-2 col-lg-4 col-lg-offset-2">
					<img class="pull-right" src="images/spotashark/SaS-ResearchPhotographer.jpg" alt=""  />
				</div>
			</div>
			<div class="item">
				<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
					<h3>Submit a photo</h3>
					<p class="lead">
						You can upload files from your computer, or take them directly from your Flickr or Facebook account. Be sure to enter when and where you saw the shark and add any other information where possible, such as flank, sex and human impact. You will receive email updates when your shark is processed by a researcher or matched in the future.
					</p>
				</div>
				<div class="col-xs-12 col-sm-4 col-sm-offset-2 col-md-4 col-md-offset-2 col-lg-4 col-lg-offset-2">
					<img class="pull-right" src="images/spotashark/SaS-SubmitAPhoto.jpg" alt=""  />
				</div>
			</div>
			<div class="item">
				<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
					<h3>Researcher verification</h3>
					<p class="lead">
						When you submit a shark identification photo, a local researcher receives a notification. This researcher will double-check the information you have submitted. After this, your photo is available for match searches.
					</p>
				</div>
				<div class="col-xs-12 col-sm-4 col-sm-offset-2 col-md-4 col-md-offset-2 col-lg-4 col-lg-offset-2">
					<img class="pull-right" src="images/spotashark/SaS-ResearcherVerification.jpg" alt=""  />
				</div>
			</div>
			<div class="item">
				<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
					<h3>Matching process</h3>
					<p class="lead">
						Once a researcher is happy with all the data accompanying the identification photo, they will run the matcher algorithm. The algorithm is like facial recognition software for shark flanks.
					</p>
				</div>
				<div class="col-xs-12 col-sm-4 col-sm-offset-2 col-md-4 col-md-offset-2 col-lg-4 col-lg-offset-2">
					<img class="pull-right" src="images/how_it_works_matching_process.jpg" alt=""  />
				</div>
			</div>
			<div class="item">
				<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
					<h3>Match Result</h3>
					<p class="lead">
					The algorithm provides researchers with a ranked selection of possible shark matches. Researchers will then visually confirm a match to an existing shark in the database, or create a new shark profile. At this stage, you will receive an update email.					</p>
				</div>
				<div class="col-xs-12 col-sm-4 col-sm-offset-2 col-md-4 col-md-offset-2 col-lg-4 col-lg-offset-2">
					<img class="pull-right" src="images/spotashark/datascreen.jpg" alt=""  />
				</div>
			</div>
		</div>
	</div>
</section>

<div class="container-fluid relative data-section">

    <aside class="container main-section">
        <div class="row">

            <!-- Random user profile to select -->
            <%
            myShepherd.beginDBTransaction();
            User featuredUser=myShepherd.getRandomUserWithPhotoAndStatement();
            if(featuredUser!=null){
                String profilePhotoURL="images/empty_profile.jpg";
                if(featuredUser.getUserImage()!=null){
                	profilePhotoURL="/"+CommonConfiguration.getDataDirectoryName(context)+"/users/"+featuredUser.getUsername()+"/"+featuredUser.getUserImage().getFilename();
                }

            %>
                <section class="col-xs-12 col-sm-6 col-md-4 col-lg-4 padding focusbox">
                    <div class="focusbox-inner opec">
                        <h2>Our contributors</h2>
                        <div>
                            <img src="<%=profilePhotoURL %>" width="80px" height="*" alt="" class="pull-left" />
                            <p><%=featuredUser.getFullName() %>
                                <%
                                if(featuredUser.getAffiliation()!=null){
                                %>
                                <i><%=featuredUser.getAffiliation() %></i>
                                <%
                                }
                                %>
                            </p>
                            <p><%=featuredUser.getUserStatement() %></p>
                        </div>
                        <a href="whoAreWe.jsp" title="" class="cta">Show me all the contributors</a>
                    </div>
                </section>
            <%
            }
            myShepherd.rollbackDBTransaction();
            %>


            <section class="col-xs-12 col-sm-6 col-md-4 col-lg-4 padding focusbox">
                <div class="focusbox-inner opec">
                    <h2>Latest animal encounters</h2>
                    <ul class="encounter-list list-unstyled">

                       <%
                       try {
                       List<Encounter> latestIndividuals=myShepherd.getMostRecentIdentifiedEncountersByDate(3);
                       int numResults=latestIndividuals.size();
                       myShepherd.beginDBTransaction();
                       for(int i=0;i<numResults;i++){
                           Encounter thisEnc=latestIndividuals.get(i);
                           %>
                            <li>
                                <img src="cust/mantamatcher/img/fin-silhouette.svg" alt="" width="85px" height="75px" class="pull-left" />
                                <small>
                                    <time>
                                        <%=thisEnc.getDate() %>
                                        <%
                                        if((thisEnc.getLocationID()!=null)&&(!thisEnc.getLocationID().trim().equals(""))){
                                        %>/ <%=thisEnc.getLocationID() %>
                                        <%
                                           }
                                        %>
                                    </time>
                                </small>
                                <p><a href="encounters/encounter.jsp?number=<%=thisEnc.getCatalogNumber() %>" title=""><%=thisEnc.getIndividualID() %></a></p>


                            </li>
                        <%
                        }
                        myShepherd.rollbackDBTransaction();
                       }
                       catch (IndexOutOfBoundsException e) {
                    	   %> <p><i> No data to display at this time </i></p>  <%
                    	   }
					   finally {}
                        %>

                    </ul>
                    <a href="encounters/searchResults.jsp?state=approved" title="" class="cta">See more encounters</a>
                </div>
            </section>
            <section class="col-xs-12 col-sm-6 col-md-4 col-lg-4 padding focusbox">
                <div class="focusbox-inner opec">
                    <h2>Top spotters (past 30 days)</h2>
                    <ul class="encounter-list list-unstyled">
                    <%
                    myShepherd.beginDBTransaction();

                    //System.out.println("Date in millis is:"+(new org.joda.time.DateTime()).getMillis());
                    long startTime=(new org.joda.time.DateTime()).getMillis()+(1000*60*60*24*30);

                    System.out.println("  I think my startTime is: "+startTime);

                    Map<String,Integer> spotters = myShepherd.getTopUsersSubmittingEncountersSinceTimeInDescendingOrder(startTime);
                    int numUsersToDisplay=3;
                    if(spotters.size()<numUsersToDisplay){numUsersToDisplay=spotters.size();}
                    Iterator<String> keys=spotters.keySet().iterator();
                    Iterator<Integer> values=spotters.values().iterator();
                    while((keys.hasNext())&&(numUsersToDisplay>0)){
                          String spotter=keys.next();
                          int numUserEncs=values.next().intValue();
                          if(myShepherd.getUser(spotter)!=null){
                        	  String profilePhotoURL="images/empty_profile.jpg";
                              User thisUser=myShepherd.getUser(spotter);
                              if(thisUser.getUserImage()!=null){
                              	profilePhotoURL="/"+CommonConfiguration.getDataDirectoryName(context)+"/users/"+thisUser.getUsername()+"/"+thisUser.getUserImage().getFilename();
                              }
                              //System.out.println(spotters.values().toString());
                            Integer myInt=spotters.get(spotter);
                            //System.out.println(spotters);

                          %>
                                <li>
                                    <img src="<%=profilePhotoURL %>" width="80px" height="*" alt="" class="pull-left" />
                                    <%
                                    if(thisUser.getAffiliation()!=null){
                                    %>
                                    <small><%=thisUser.getAffiliation() %></small>
                                    <%
                                      }
                                    %>
                                    <p><a href="#" title=""><%=spotter %></a>, <span><%=numUserEncs %> encounters<span></p>
                                </li>

                           <%
                           numUsersToDisplay--;
                    }
                   } //end while
                   myShepherd.rollbackDBTransaction();
                   %>

                    </ul>
                    <a href="whoAreWe.jsp" title="" class="cta">See all spotters</a>
                </div>
            </section>
        </div>
    </aside>
</div>

<div class="container-fluid">
    <section class="container text-center  main-section">
        <div class="row">
            <section class="col-xs-12 col-sm-4 col-md-4 col-lg-4 padding">
                <p class="brand-primary"><i>
                  <span class="half-massive"><%=numMarkedIndividualsLeftFlank %> Left </span></br>
                  <span class="half-massive"><%= numMarkedIndividuals - numMarkedIndividualsLeftFlank %> Right </span> </br>
                  unique individuals
                </i></p>
            </section>
            <section class="col-xs-12 col-sm-4 col-md-4 col-lg-4 padding">
                <p class="brand-primary"><i>
                  <span class="half-massive"><%=numEncountersLeftFlank %> Left </span></br>
                  <span class="half-massive"><%= numEncounters - numEncountersLeftFlank %> Right </span></br>
                  reported encounters
                </i></p>
            </section>
            <section class="col-xs-12 col-sm-4 col-md-4 col-lg-4 padding">
                <p class="brand-primary"><i><span class="massive"><%=numDataContributors %></span> contributors</i></p>
            </section>
        </div>

        <hr/>

        <main class="container">
            <article class="text-center">
                <div class="row">
                <div class="pull-left col-xs-7 col-sm-4 col-md-4 col-lg-4 col-xs-offset-2 col-sm-offset-1 col-md-offset-1 col-lg-offset-1">
                    <img src="images/spotashark/whywedo.jpg" alt="" />
                    <p style="margin-top:-40px;color:white;"><i>photo credit Jim Dodd</i></p>

                    </div>
                    <div class="col-xs-12 col-sm-6 col-md-6 col-lg-6 text-left">
                        <h1>Why we do this</h1>
                        <p class="lead">
                            <i>&ldquo;We aim to monitor and report on the health of the Australian Grey Nurse Shark by providing non-invasive tools to the dive community to record changes at their local aggregations sites. We also welcome similar groups from around the world to join this project.&rdquo;</i> </br>- Sean Barker, Project Leader</p>
                        <a href="#" title="">I want to know more</a>
                    </div>
                </div>
            </article>
        <main>

    </section>
</div>

<div class="container-fluid main-section">
    <h2 class="section-header">Encounters around the world</h2>

      <div id="map_canvas" style="width: 100% !important; height: 510px; margin: 0 auto;"></div>
</div>

<div class="container-fluid">
    <section class="container main-section">
        <h2 class="section-header">How can I help?</h2>
        <p class="lead text-center"></p>

		<!--  DELETED FROM HERE: adopt an animal section-->
        <section class="donate-section">
            <div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
                <h3>Donate</h3>
                <p>If you are not a diver, there are still ways you can help.</p>
            </div>
            <div class="col-xs-12 col-sm-5 col-md-5 col-lg-5 col-sm-offset-1 col-md-offset-1 col-lg-offset-1">
                <a href="">
	                <button class="large contrast">
	                    Donate
	                    <span class="button-icon" aria-hidden="true">
	                </button>
                </a>
            </div>
        </section>
    </section>
</div>


<jsp:include page="footer.jsp" flush="true"/>

<%
myShepherd.closeDBTransaction();
myShepherd=null;
%>
