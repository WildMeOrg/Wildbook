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
String urlLoc = "http://" + CommonConfiguration.getURLLocation(request);


//set up our Shepherd

Shepherd myShepherd=null;
myShepherd=new Shepherd(context);


//check for and inject a default user 'tomcat' if none e



%>

<style type="text/css">

  .saimaa-info {
    padding-left: 40px;
  }

  .container-fluid .row.saimaa-row {
    margin-left: 0;
    margin-right: 0;
  }

   .row.saimaa-row, .main-section row.saimaa-row li {
    background: #3ac6f7;
    color: #f7f7f7;
  }
   .row.saimaa-row h2 {
    margin-bottom: 10px;
    text-align: left;
    font-weight: 700;
    font-size: 36px;
    line-height: 2rem;
    color: #f7f7f7;
  }
 .row.saimaa-row h3, .main-section .row.saimaa-row a h3  {
  font-family: 'open-sans', Helvetica, Arial, sans-serif;
  font-size: 1.4rem;
  margin-bottom: 0;
  color: #f7f7f7;
}
 .row.saimaa-row a, .main-section .row.saimaa-row a:hover {
  color: #f7f7f7;
}

  .row.saimaa-row ol {
    padding-left:20px;
    padding-top:20px;
    font-size: 1.4rem;
  }

  .row.saimaa-row ol li p {
    color: #007476;
    font-size: 1rem;
    margin-bottom: 10px;
    margin-top: -5px;
  }
 .saimaa-map {
  background: url(cust/mantamatcher/img/wwf-saimaa.png);
  background-size: contain;
  background-repeat: no-repeat;
  min-height: 618px;
}
  .saimaa-row .section-header.map-header {
   background: #3ac6f7;
   color: #f7f7f7
   margin-top: 7px;
 }

 [class^="icon-"], [class*=" icon-"] {
    font-family: 'wwf-icons' !important;
    speak: none;
    font-style: normal;
    font-weight: normal;
    font-variant: normal;
    text-transform: none;
    line-height: 1;
    -webkit-font-smoothing: antialiased;
    -moz-osx-font-smoothing: grayscale;
}


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
</style>

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
          mapTypeId: google.maps.MapTypeId.HYBRID
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

	 		          var divString<%=i%> = "<div style=\"font-weight:bold;text-align: center;line-height: 45px;vertical-align: middle;width:60px;height:49px;padding: 2px; background-image: url('http://www.flukebook.org/cust/mantamatcher/img/manta-silhouette.png');background-size: cover\"><a href=\"http://www.mantamatcher.org/encounters/searchResults.jsp?locationCodeField=<%=locID %>\"><%=numSightingsInteger.toString() %></a></div>";


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
 		catch(Exception e){
 			e.printStackTrace();
 		}
 		finally{
 			myShepherd.rollbackDBTransaction();
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

              Role newRole8=new Role("tomcat","researcher");
              newRole8.setContext("context0");
              myShepherd.getPM().makePersistent(newRole8);

            myShepherd.commitDBTransaction();


              System.out.println("Creating tomcat user account...");
           }
     }




//let's quickly get the data we need from Shepherd

int numMarkedIndividuals=0;
int numEncounters=0;
int numDataContributors=0;

myShepherd.beginDBTransaction();

try{

    /* below three lines can be commented out to reduce load time during development */
    numMarkedIndividuals=myShepherd.getNumMarkedIndividuals();
    numEncounters=myShepherd.getNumEncounters();
    numDataContributors=myShepherd.getNumUsers();


}
catch(Exception e){
    e.printStackTrace();
}
finally{
   myShepherd.rollbackDBTransaction();
   myShepherd.closeDBTransaction();
}
%>

<section class="hero container-fluid main-section relative" title="&copy; Wild Wonders of Europe / Wildstrand / WWF">
    <div class="container-fluid relative">
        <div class="col-lg-12 bc4">
            <!--<h1 class="hidden">Wildbook</h1>-->
            <h2 class="jumboesque">Kuvasitko <br/> Saimaannorpan?</h2>
            <!--
            <button id="watch-movie" class="large light">
				Watch the movie
				<span class="button-icon" aria-hidden="true">
			</button>
    -->   <h2>
            <a href="submit.jsp">
                <!--<button class="large">Report encounter<span class="button-icon" aria-hidden="true"></button>-->
                <button class="btn btn-primary btn-large">Ilmoita havainto</button>
            </a>
          </h2>
        </div>

	</div>
	 <div class="video-wrapper">
		<div class="embed-container">
			<iframe id="herovideo" src="http://player.vimeo.com/video/123083341?api=1&amp;player_id=herovideo" frameborder="0" webkitAllowFullScreen mozallowfullscreen allowFullScreen></iframe>
		</div>
	</div>

</section>

<section class="container-fluid text-center main-section main-text grey-background">

	<h2 class="section-header">Tavoitteena kaikkien norppien tunnistaminen</h2>

	<div class="row">
    <div class="col-md-6 col-md-offset-3">

      <p>
        Mik&auml; ihmeen Lorem ipsum?

        Lorem ipsum on 1500-luvulta l&auml;htien olemassa ollut t&auml;yteteksti, jota k&auml;ytet&auml;&auml;n usein ulkoasun testaamiseen graafisessa suunnittelussa, kun mit&auml;&auml;n oikeata sis&auml;lt&ouml;&auml; ei viel&auml; ole. Lorem ipsumia k&auml;ytet&auml;&auml;n n&auml;ytt&auml;m&auml;&auml;n, milt&auml; esimerkiksi kirjasin tai julkaisun tekstin asettelu n&auml;ytt&auml;v&auml;t
      </p>
      <p style="font-size:2em;color:#999">
        <i class="icon icon-facebook-btn" aria-hidden="true"></i>
        <i class="icon icon-twitter-btn" aria-hidden="true"></i>
        <i class="icon icon-google-plus-btn" aria-hidden="true"></i>
      </p>
    </div>
  </div>

</section>

<div class="container-fluid">
  <section class="container-fluid main-section front-gallery">
    <div class="row">

      <jsp:include page="individualGalleryPanel.jsp" flush="true">
        <jsp:param name="individualID" value="Phs002" />
      </jsp:include>

    </div>
  </section>
</div>



<div class="container-fluid">
    <section class="container-fluid text-center  main-section grey-background">
        <div class="row">
            <section class="col-xs-12 col-sm-4 col-md-4 col-lg-4 padding">
                <p class="brand-primary"><span class="massive"><%=numMarkedIndividuals %></span>
                Identified individuals</p>
            </section>
            <section class="col-xs-12 col-sm-4 col-md-4 col-lg-4 padding">
                <p class="brand-primary"><span class="massive"><%=numEncounters %></span> Reported encounters</p>
            </section>
            <section class="col-xs-12 col-sm-4 col-md-4 col-lg-4 padding">

                <p class="brand-primary"><span class="massive"><%=numDataContributors %></span> contributors</p>
            </section>
        </div>
    </section>
</div>

<div class="container-fluid grey-background">
    <div class="row saimaa-row">


    <div class="col-xs-6 col-lg-6">
      <div class="saimaa-info">
      <h2 class="section-header map-header">Kuvatut Norpat Alueittain<!--Encounters around the world--></h2>



      <ol>
        <%
        List<String> locs2=CommonConfiguration.getIndexedPropertyValues("locationID", context);
        int numLocIDs = locs2.size();
        %>
        <script>console.log("numLocationIDs = <%=numLocationIDs%>");</script>
        <%
        myShepherd.beginDBTransaction();
        try{
    	 		for(int i=0;i<numLocationIDs;i++){
            String locID = locs2.get(i);
            int numSightings = myShepherd.getNumMarkedIndividualsSightedAtLocationID(locID);
            %>
            <!-- TODO: double check this link (need to clean locID?)-->
            <li><a href="<%=urlLoc %>/encounters/searchResults.jsp?locationCodeField=<%=locID %>"><h3><%=locID%></h3></a>
              <p><%=numSightings%> tunnistettu norppaa</p>
            </li>
            <%
          }
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          myShepherd.rollbackDBTransaction();
          myShepherd.closeDBTransaction();
        }
        %>

      </ol>

    </div>
  </div>

    <div class="col-xs-6 col-lg-6 saimaa-map">
    </div>





      <!--<div id="map_canvas" style="width: 100% !important; height: 510px; margin: 0 auto;"></div>
      -->
      </div>

</div>

<!--
<div class="container-fluid">
    <section class="container main-section">
        <h2 class="section-header">How can I help?</h2>
        <p class="lead text-center">If you are not on site, there are still other ways to get engaged</p>

        <section class="adopt-section row">
            <div class=" col-xs-12 col-sm-6 col-md-6 col-lg-6">
                <h3 class="uppercase">Adopt an animal</h3>
                <ul>
                    <li>Support individual research programs in different regions</li>
					<li>Receive email updates when we resight your adopted animal</li>
					<li>Display your photo and a quote on the animal's page in our database</li>
</ul>
                <a href="adoptananimal.jsp" title="">Learn more about adopting an individual animal in our study</a>
            </div>
            <%
            myShepherd.beginDBTransaction();
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
            finally{myShepherd.rollbackDBTransaction();}

            %>


        </section>


        <hr />
        <section class="donate-section">
            <div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
                <h3>Donate</h3>
                <p>Donations, including in-kind, large or small, are always welcome. Your support helps the continued development of our project and can support effective, science-based conservation management, and safeguard these animals and their habitat.</p>
                <a href="adoptananimal.jsp" title="More information about donations">Learn more about how to donate</a>
            </div>
            <div class="col-xs-12 col-sm-5 col-md-5 col-lg-5 col-sm-offset-1 col-md-offset-1 col-lg-offset-1">
                <a href="adoptananimal.jsp">
	                <button class="large contrast">
	                    Donate
	                    <span class="button-icon" aria-hidden="true">
	                </button>
                </a>
            </div>
        </section>

    </section>
</div>
-->

<jsp:include page="footer.jsp" flush="true"/>

<script>
window.addEventListener("resize", function(e) { $("#map_canvas").height($("#map_canvas").width()*0.662); });
google.maps.event.addDomListener(window, "resize", function() {
	 google.maps.event.trigger(map, "resize");
	 map.fitBounds(bounds);
	});
</script>

<%
myShepherd.closeDBTransaction();
myShepherd=null;
%>
