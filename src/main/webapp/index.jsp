<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
              org.ecocean.servlet.ServletUtilities,
              java.util.ArrayList,
              java.util.List,
              java.util.Map,
              java.util.Iterator,
              java.util.Properties,
              java.util.StringTokenizer
              "
%>



<jsp:include page="header.jsp" flush="true"/>

<%
String context=ServletUtilities.getContext(request);

//set up our Shepherd

Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("index.jsp");


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
</style>
<script src="//maps.google.com/maps/api/js?key=<%=mapKey%>&language=<%=langCode%>"></script>
<script src="cust/mantamatcher/js/google_maps_style_vars.js"></script>
<script src="cust/mantamatcher/js/richmarker-compiled.js"></script>
  <script type="text/javascript">
  var map;
  var mapZoom = 8;
  var center;
  var newCenter;	
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
  		//var map;
  	  var bounds = new google.maps.LatLngBounds();
      function initialize() {


    	// Create an array of styles for our Goolge Map.
  	    //var gmap_styles = [{"stylers":[{"visibility":"off"}]},{"featureType":"water","stylers":[{"visibility":"on"},{"color":"#00c0f7"}]},{"featureType":"landscape","stylers":[{"visibility":"on"},{"color":"#005589"}]},{"featureType":"administrative","elementType":"geometry.stroke","stylers":[{"visibility":"on"},{"color":"#00c0f7"},{"weight":1}]}]
    	if($("#map_canvas").hasClass("full_screen_map")){mapZoom=3;}
		
    	if (center == null) {
	    	center = new google.maps.LatLng(0,0);
    	} else {
    		center = map.getCenter();
    	}
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
 		List<String> locs=CommonConfiguration.getIndexedPropertyValues("locationID", context);
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

	 		         var latLng = new google.maps.LatLng(<%=thisLatLong%>);
			          bounds.extend(latLng);

	 		          var divString<%=i%> = "<div style=\"padding-top: 25px;font-weight:bold;text-align: center;line-height: 35px;vertical-align: middle;width:60px;height:49px; background-image: url('//www.whaleshark.org/cust/mantamatcher/img/fin-silhouette.svg');background-size: cover\"><a href=\"http://www.whaleshark.org/encounters/searchResults.jsp?locationCodeField=<%=locID %>\"><%=numSightingsInteger.toString() %></a></div>";
	 		          //http://www.flukebook.org/cust/mantamatcher/img/manta-silhouette.png

	 		         var marker<%=i%> = new RichMarker({
	 		            position: latLng,
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
 	  google.maps.event.addDomListener(controlUI, 'click', function() {
 	 	if($("#map_canvas").hasClass("full_screen_map")){
 	  	  exitFullScreen();
 	  	} else {
 	  	  fullScreen();
 	  	}
 	  });
  	  
  	  // Setup the click event listeners: toggle the full screen
  	}
    google.maps.event.addDomListener(window, 'load', initialize);
  	
  </script>
<%


//let's quickly get the data we need from Shepherd

int numMarkedIndividuals=0;
int numEncounters=0;
int numDataContributors=0;
//Shepherd myShepherd=new Shepherd(context);
myShepherd.beginDBTransaction();
try{


    numMarkedIndividuals=myShepherd.getNumMarkedIndividuals();
    numEncounters=myShepherd.getNumEncounters();
    numDataContributors=myShepherd.getNumUsers();


}
catch(Exception e){
    e.printStackTrace();
}
finally{
	//myShepherd.rollbackDBTransaction();
	//myShepherd.closeDBTransaction();
}
%>

<section class="hero container-fluid main-section relative">
    <div class="container relative">
        <div class="col-xs-12 col-sm-10 col-md-8 col-lg-6">
            <h2>Help us protect and study the world's biggest fish!</h2>
            <!--
            <button id="watch-movie" class="large light">
				Watch the movie
				<span class="button-icon" aria-hidden="true">
			</button>
			-->
            <a href="submit.jsp">
                <button class="large heroBtn">Report your sightings<span class="button-icon" aria-hidden="true"></button>
            </a>
            <br>
            <a href="adoptashark.jsp">
                <button class="large heroBtn">Adopt a shark<span class="button-icon" aria-hidden="true"></button>
            </a>
            <br>
            <br>
            <h4 style="color: white;">Our work is possible with the generous support of:</h4>
            <img border="1px" src="images/QWSP_Logo_small.png" height="125px" width="170px" /> <img border="1px" src="images/Siren_Fleet_small.gif" height="125px" width="128px"  />
        </div>

	</div>


</section>

<section class="container text-center main-section">

	<h2 class="section-header">How it works</h2>
	<p class="lead">The Wildbook for Whale Sharks photo-identification library is a visual database of whale shark (Rhincodon typus) encounters and of individually catalogued whale sharks. The library is maintained and used by marine biologists to collect and analyze whale shark sighting data to learn more about these amazing creatures.</p>
	<p class="lead">The Wildbook uses photographs of the skin patterning behind the gills of each shark, and any scars, to distinguish between individual animals. Cutting-edge software supports rapid identification using pattern recognition and photo management tools.

You too can assist with whale shark research, by submitting photos and sighting data. The information you submit will be used in mark-recapture studies to help with the global conservation of this threatened species.</p>

	<div id="howtocarousel" class="carousel slide" data-ride="carousel">
		<ol class="list-inline carousel-indicators slide-nav">
	        <li data-target="#howtocarousel" data-slide-to="0" class="active">1. Photograph an animal<span class="caret"></span></li>
	        <li data-target="#howtocarousel" data-slide-to="1" class="">2. Submit photo/video<span class="caret"></span></li>
	        <li data-target="#howtocarousel" data-slide-to="2" class="">3. Researcher verification<span class="caret"></span></li>
	        <li data-target="#howtocarousel" data-slide-to="3" class="">4. Matching process<span class="caret"></span></li>
	        <li data-target="#howtocarousel" data-slide-to="4" class="">5. Match result<span class="caret"></span></li>
	    </ol>
		<div class="carousel-inner text-left">
			<div class="item active">
				<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
					<h3>Photograph the spots behind the gills</h3>
					<p class="lead">
						Each whale shark has an individual fingerprint: the pattern of spots behind the gills on the left or right sides. Get an image or video of their &ldquo;print&rdquo; and we can match that pattern to others already in the database, or your whale shark might be completely new to the database.
					</p>
					<p class="lead">
						<a href="photographing.jsp" title="">See the photography guide</a>
					</p>
				</div>
				<div class="col-xs-12 col-sm-4 col-sm-offset-2 col-md-4 col-md-offset-2 col-lg-4 col-lg-offset-2">
					<img class="pull-right" src="cust/mantamatcher/img/bellyshotofmanta.jpg" alt=""  />
				</div>
			</div>
			<div class="item">
				<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
					<h3>Submit photo/video</h3>
					<p class="lead">
						You can upload files from your computer, or take them directly from your Flickr or Facebook account. Be sure to enter when and where you saw the shark, and add other information, such as scarring and sex, if you can. You will receive email updates when your animal is processed by a researcher.
					</p>
				</div>
				<div class="col-xs-12 col-sm-4 col-sm-offset-2 col-md-4 col-md-offset-2 col-lg-4 col-lg-offset-2">
					<img class="pull-right" src="images/how_it_works_submit.jpg" alt=""  />
				</div>
			</div>
			<div class="item">
				<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
					<h3>Researcher verification</h3>
					<p class="lead">
						When you submit an identification photo, a local researcher receives a notification. This researcher will double check that the information you submitted is correct and add any additional information.
					</p>
				</div>
				<div class="col-xs-12 col-sm-4 col-sm-offset-2 col-md-4 col-md-offset-2 col-lg-4 col-lg-offset-2">
					<img class="pull-right" src="images/how_it_works_researcher_verification.jpg" alt=""  />
				</div>
			</div>
			<div class="item">
				<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
					<h3>Matching process</h3>
					<p class="lead">
						Once a researcher is happy with all the data accompanying the identification photo using two spot pattern matching algorithms. The algorithms are like facial recognition software for animal patterns.
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
						The algorithm (or manual comparison) provides researchers with a ranked selection of possible matches. Researchers will then visually confirm a match to an existing whale shark in the database, or create a new individual profile.
					</p>
				</div>
				<div class="col-xs-12 col-sm-4 col-sm-offset-2 col-md-4 col-md-offset-2 col-lg-4 col-lg-offset-2">
					<img class="pull-right" src="images/how_it_works_match_result.jpg" alt=""  />
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
            //Shepherd myShepherd=new Shepherd(context);
            //myShepherd.beginDBTransaction();
			try{
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
			}
			catch(Exception e){e.printStackTrace();}
			finally{
	            //myShepherd.rollbackDBTransaction();
	            //myShepherd.closeDBTransaction();
			}
            %>


            <section class="col-xs-12 col-sm-6 col-md-4 col-lg-4 padding focusbox">
                <div class="focusbox-inner opec">
                    <h2>Latest shark encounters</h2>
                    <ul class="encounter-list list-unstyled">

                       <%
                       //Shepherd myShepherd=new Shepherd(context);


                       //myShepherd.beginDBTransaction();
                       try{
                    	   List<Encounter> latestIndividuals=myShepherd.getMostRecentIdentifiedEncountersByDate(3);
                           int numResults=latestIndividuals.size();

	                       for(int i=0;i<numResults;i++){
	                           Encounter thisEnc=latestIndividuals.get(i);
	                           %>
	                            <li>
	                                <img src="cust/mantamatcher/img/whalesharkbw.png" alt="" class="pull-left" />
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
                       }
                       catch(Exception e){e.printStackTrace();}
                       finally{
	                       //myShepherd.rollbackDBTransaction();
	                       //myShepherd.closeDBTransaction();
                       }
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
                    //Shepherd myShepherd=new Shepherd(context);
                    //myShepherd.beginDBTransaction();

                    try{
	                    //System.out.println("Date in millis is:"+(new org.joda.time.DateTime()).getMillis());
	                    long startTime=(new org.joda.time.DateTime()).getMillis()+(1000*60*60*24*30);

	                    //System.out.println("  I think my startTime is: "+startTime);

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
                    }
                    catch(Exception e){e.printStackTrace();}
                    finally{
                    	//myShepherd.rollbackDBTransaction();
                        //myShepherd.closeDBTransaction();
                    }


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
            <section class="col-xs-12 col-sm-3 col-md-3 col-lg-3 padding">
                <p class="brand-primary"><i><span class="massive"><%=numMarkedIndividuals %></span> identified whale sharks</i></p>
            </section>
            <section class="col-xs-12 col-sm-3 col-md-3 col-lg-3 padding">
                <p class="brand-primary"><i><span class="massive"><%=numEncounters %></span> reported sightings</i></p>
            </section>
            <section class="col-xs-12 col-sm-3 col-md-3 col-lg-3 padding">

                <p class="brand-primary"><i><span class="massive">5200</span> citizen scientists</i></p>
            </section>
            <section class="col-xs-12 col-sm-3 col-md-3 col-lg-3 padding">

                <p class="brand-primary"><i><span class="massive"><%=numDataContributors %></span> researchers and volunteers</i></p>
            </section>
        </div>

        <hr/>

        <main class="container">
            <article class="text-center">
                <div class="row">
                      <h1 style="color: #005589;-webkit-text-fill-color: #005589;">Why we do this</h1>
                        <p class="lead">
                            <i>&ldquo;Wildbook for Whale Sharks has pioneered the way for a new generation of global scale, collaborative wildlife projects that blend citizen science and computer vision to help researchers get bigger and more detailed pictures of some of the world's most mysterious species.&rdquo;</i> - Jason Holmberg, Information Architect</p>
                        <a href="whoAreWe.jsp" title="">Learn more about the Researchers and Volunteers</a><br>
                        <a href="http://www.wildme.org" title="">Learn more about Wild Me and Wildbook</a>
                    
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
        <p class="lead text-center">If you are not on site, there are still other ways to get engaged</p>

        <section class="adopt-section row">
            <div class=" col-xs-12 col-sm-6 col-md-6 col-lg-6">
                <h3 class="uppercase">Adopt a Whale Shark</h3>
                <ul>
                    <li>Support individual research programs in different regions</li>
					<li>Receive email updates when we resight your adopted animal</li>
					<li>Display your photo and a quote on the animal's page in our database</li>
</ul>
                <a href="adoptashark.jsp" title="">Learn more about adopting an individual animal in our study</a>
            </div>
            <%
            //Shepherd myShepherd=new Shepherd(context);
            //myShepherd.beginDBTransaction();
            try{
	            Adoption adopt=myShepherd.getRandomAdoptionWithPhotoAndStatement();
	            if(adopt!=null){
	            %>
	            	<div class="adopter-badge focusbox col-xs-12 col-sm-6 col-md-6 col-lg-6">
		                <div class="focusbox-inner" style="overflow: hidden;">
		                	<%
		                    String profilePhotoURL="/"+CommonConfiguration.getDataDirectoryName(context)+"/adoptions/"+adopt.getID()+"/thumb.jpg";

		                	%>
		                    <img src="<%=profilePhotoURL %>" alt="" class="pull-right round">
		                    <h2><small>Meet an adopter:</small><%=adopt.getAdopterName() %></h2>
		                    <%
		                    if(adopt.getAdopterQuote()!=null){
		                    %>
			                    <blockquote>
			                        <%=adopt.getAdopterQuote() %>
			                    </blockquote>
		                    <%
		                    }
		                    %>
		                </div>
		            </div>

	            <%
				}
            }
            catch(Exception e){e.printStackTrace();}
            finally{
	            myShepherd.rollbackDBTransaction();
	            myShepherd.closeDBTransaction();
            }
            %>


        </section>
        <hr />
        <section class="donate-section">
            <div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
                <h3>Donate</h3>
                <p>Donations, including in-kind, large or small, are always welcome. Your support helps the continued development of our project and can support effective, science-based conservation management, and safeguard these sharks and their habitat.</p>
                <p>You can make a one time donation or <a href="adoptashark.jsp" title="More information about donations">learn about adopting an animal</a></p>
            </div>
            <div class="col-xs-12 col-sm-5 col-md-5 col-lg-5 col-sm-offset-1 col-md-offset-1 col-lg-offset-1">
                <a href="oneTimeDonation.jsp">
	                <button class="large">
	                    One Time Donation
	                    <span class="button-icon" aria-hidden="true">
	                </button>
                </a>
            </div>
        </section>
    </section>
</div>

<div class="container-fluid">
    <section class="container main-section">
        <h2 class="section-header">Development</h2>
        <p class="lead text-center">Wildbook for Whale Sharks is maintained and developed by <a href="http://www.facebook.com/holmbergius">Jason Holmberg (Information Architect)</a> with significant support and input from the research community. This site is a flagship project of Wild Me's <a href="http://www.wildbook.org">Wildbook</a> and <a href="http://www.ibeis.org">IBEIS</a> open source projects. <a href="http://www.simonjpierce.com/">Dr. Simon Pierce</a> provides scientific oversight and guidance.</p>
</section>
</div>


<jsp:include page="footer.jsp" flush="true"/>

<script>
window.addEventListener("resize", function(e) { $("#map_canvas").height($("#map_canvas").width()*0.662); });
google.maps.event.addDomListener(window, "resize", function() {
	 google.maps.event.trigger(map, "resize");
	 map.fitBounds(bounds);
	});
</script>
