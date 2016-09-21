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
myShepherd.setAction("index.jsp");


//check for and inject a default user 'tomcat' if none e



%>

<style type="text/css">

  p.image-copyright {
      text-align: right;
      position: absolute;
      top: 5px;
      right: 25px;
      color: #fff;
      font-size: 0.8rem;
  }

  .crop-outer {
    cursor: pointer;
    width: auto;
    overflow: hidden;
  }

  .crop-outer .crop {
    text-align: center;
    overflow: hidden;
  }



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
    background-position: center;
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

<script src="javascript/sss/sss.min.js"></script>
<link rel="stylesheet" href="javascript/sss/sss.css" type="text/css" media="all">

<script>

jQuery(function($) {
	$('.slider').sss({
		slideShow : true, // Set to false to prevent SSS from automatically animating.
		startOn : 0, // Slide to display first. Uses array notation (0 = first slide).
		transition : 400, // Length (in milliseconds) of the fade transition.
		speed : 350000, // Slideshow speed in milliseconds.
		showNav : true // Set to false to hide navigation arrows.
	});
});

</script>


<section class="hero container-fluid main-section relative" >
  <div class="container-fluid relative">
    <div class="col-lg-12 bc4">
      <h2 class="jumboesque">Kuvasitko <br/> Saimaannorpan?</h2>
      <h2>
        <a href="submit.jsp">
          <button class="btn btn-primary btn-large">Ilmoita havainto</button>
        </a>
      </h2>
    </div>

	</div>


</section>

<section class="container-fluid text-center main-section main-text grey-background">

	<h2 class="section-header">Tavoitteena norppien tunnistaminen</h2>

	<div class="row">
    <div class="col-md-6 col-md-offset-3">

      <p>
        Saimaassa uiskentelee yli 300 norppaa. Tavoitteenamme on tunnistaa niist&auml; mahdollisimman moni. Siihen tarvitsemme my&ouml;s sinun apuasi! Otamme ilolla vastaan kuvia saimaannorpista, ja jo tunnistettuihin viiksiniekkoihin p&auml;&auml;set tutustumaan galleriassa. Kuvien lis&auml;ksi olemme kiinnostuneita my&ouml;s n&auml;k&ouml;havainnoista, sill&auml; nekin auttavat t&auml;m&auml;n eritt&auml;in uhanalaisen hylkeen suojelussa.
      </p>
      <p style="font-size:2em;color:#999">
        <a href="https://www.facebook.com/sharer/sharer.php?&endorseimage=http://norppagalleria.wwf.fi/images/pic_for_social_media.jpg&u=http://norppagalleria.wwf.fi&title=Tutustu sin&auml;kin Pullervoon, Terttuun, Teemuun ja satoihin muihin saimaannorppiin WWF:n Norppagalleriassa!  #Norppagalleria" title="Jaa Facebookissa" class="btnx" target="_blank" rel="external" >

        	<i class="icon icon-facebook-btn" aria-hidden="true"></i>
        </a>
        <a target="_blank" rel="external" href="http://twitter.com/intent/tweet?status=Tutustu sin&auml;kin Pullervoon, Terttuun, Teemuun ja satoihin muihin saimaannorppiin WWF:n Norppagalleriassa!+http://norppagalleria.wwf.fi/images/pic_for_social_media.jpg">
        	<i class="icon icon-twitter-btn" aria-hidden="true"></i>
        </a>
        <a target="_blank" rel="external" href="https://plus.google.com/share?url=http://norppagalleria.wwf.fi/images/pic_for_social_media.jpg"><i class="icon icon-google-plus-btn" aria-hidden="true"></i></a>


      </p>
    </div>
  </div>

</section>


<div class="container-fluid">
  <section class="container-fluid main-section front-gallery">

<div class="slider">
	<div class="row grey-outline">
		<div class="col-md-6">
      <!---->
      <div class="crop-outer">
        <div class="crop" >
		      <a href="http://norppagalleria.wwf.fi:80/gallery.jsp"><img src="cust/mantamatcher/img/front-slider/Pullervo_2013_3.jpg" /></a>
		      <p class="image-copyright"> &copy; UEF / WWF </p>
          <!---->
        </div>
      </div>


		</div>
		<div class="col-md-6 full-height per-seal-info">
		  <h2 class="greenish">Pullervo</h2>
		  <p>Pullervo on Suomen tunnetuin saimaannorppa. Se nousi koko kansan tietoisuuteen Norppaliven toisena p&auml;&auml;t&auml;hten&auml; toukokuussa 2016. Pullervo tunnettiin ennen koodinimell&auml; Phs106. Sen nime&auml;miseksi k&auml;ynnistettiin kilpailu, ja ehdotuksia nimeksi tuli yli 6 000. Pullervo on erityisen vaalea ja isohko uros.</p>
		  <p class=pfooter-link>
		    <a href="http://norppagalleria.wwf.fi:80/gallery.jsp">Siirry galleriaan >></a>
		  </p>
		</div>
	</div>


 <div class="row grey-outline">
	<div class="col-md-6">
    <div class="crop-outer">
      <div class="crop" >
        <a href="http://norppagalleria.wwf.fi:80/gallery.jsp"><img src="cust/mantamatcher/img/front-slider/Terttu_2.JPG" /></a>
      	<p class="image-copyright"> &copy; UEF / WWF </p>
      </div>
    </div>
  </div>

	<div class="col-md-6 full-height per-seal-info">
	  <h2 class="greenish">Terttu</h2>
	  <p>Haukivedell&auml; vuonna 2008 syntyneen Tertun el&auml;m&auml;n alkutaivalta seurattiin tarkasti l&auml;hettimen avulla. Karvaan kiinnitetty l&auml;hetin putosi seuraavana vuonna Tertun kyydist&auml;, sill&auml; norpat vaihtavat karvansa kev&auml;isin. Seuraavaa havaintoa saatiin odottaa kolme vuotta. Sen j&auml;lkeen Terttu on n&auml;hty vuosittain, aina alle seitsem&auml;n kilometrin p&auml;&auml;ss&auml; syntym&auml;paikastaan. Vuonna 2015 saatiin iloisia perheuutisia, kun Terttu synnytti poikasen.</p>
	  <p class=pfooter-link>
	    <a href="http://norppagalleria.wwf.fi:80/gallery.jsp">Siirry galleriaan >></a>
	  </p>
	</div>
</div>

<div class="row grey-outline">
	<div class="col-md-6">
    <div class="crop-outer">
      <div class="crop" >
        <a href="http://norppagalleria.wwf.fi:80/gallery.jsp"><img src="cust/mantamatcher/img/front-slider/Teemu_2.jpg" /></a>
      	<p class="image-copyright"> &copy; Juha Taskinen / WWF </p>
      </div>
    </div>
  </div>

	<div class="col-md-6 full-height per-seal-info">
	  <h2 class="greenish">Teemu</h2>
	  <p>Haukivedell&auml; uiskenteleva Teemu on tallennettu todenn&auml;k&ouml;isesti moniin kotialbumeihin. Se on koko Saimaan useimmiten tavattu norppa, joka ei huomiota tai veneit&auml; kavahda. Ensimm&auml;isen kerran Teemu n&auml;htiin vuonna 2006. Vuonna 2009 Teemulle asennettiin radiol&auml;hetin, jolloin se my&ouml;s punnittiin: painoa oli kertynyt komeat 103 kiloa. Teemu vaikuttaa olevan varsin kotiseuturakas eik&auml; se ei juuri harhaile pois tutuilta vesilt&auml;.</p>
	  <p class=pfooter-link>
	    <a href="http://norppagalleria.wwf.fi:80/gallery.jsp">Siirry galleriaan >></a>
	  </p>
	</div>
</div>

<div class="row grey-outline">
	<div class="col-md-6">
    <div class="crop-outer">
      <div class="crop" >
        <a href="http://norppagalleria.wwf.fi:80/gallery.jsp"><img src="cust/mantamatcher/img/front-slider/Ritva_2.JPG" /></a>
      	<p class="image-copyright"> &copy; UEF / WWF </p>
      </div>
    </div>
  </div>

	<div class="col-md-6 full-height per-seal-info">
	  <h2 class="greenish">Ritva</h2>
	  <p>Ritva alkaa olla kunnioitettavassa i&auml;ss&auml;, ja se onkin pisimp&auml;&auml;n seurattu saimaannorppa. Ensimm&auml;isen kerran Ritva havaittiin vuonna 1998 Haukivedell&auml;, jolloin tutkijat seurasivat sen liikkeit&auml; radiol&auml;hettimen avulla. Ritva on naaraaksi varsin suuri. Se painoi vuonna 1998 per&auml;ti 95 kiloa. Seuraavana vuonna Ritva synnytti kuutin, mink&auml; j&auml;lkeen sit&auml; ei n&auml;hty pitk&auml;&auml;n aikaan. Viime vuosina Ritvasta on kuitenkin lukuisia havaintoja, kaikki alle nelj&auml;n kilometrin s&auml;teell&auml; toisistaan.</p>
	  <p class=pfooter-link>
	    <a href="http://norppagalleria.wwf.fi:80/gallery.jsp">Siirry galleriaan >></a>
	  </p>
	</div>
</div>
<div class="row grey-outline">
	<div class="col-md-6">
    <div class="crop-outer">
      <div class="crop">
        <a href="http://norppagalleria.wwf.fi:80/gallery.jsp"><img src="cust/mantamatcher/img/front-slider/Arka_2.jpg" /></a>
      	<p class="image-copyright"> &copy; Juha Taskinen / WWF </p>
      </div>
    </div>
  </div>

	<div class="col-md-6 full-height per-seal-info">
	  <h2 class="greenish">Arka</h2>
	  <p>Arka-norpasta erityisen tekev&auml;t sen perhesiteet. Arka on nimitt&auml;in toinen norppakaksosista. Kaksoset syntyiv&auml;t vuonna 2009 Pihlajavedell&auml;, ja Arka-naaraasta on lukuisia havaintoja vuosien varrelta. Vuonna 2015 se kuvattiin j&auml;&auml;ll&auml; poikasen kanssa. Samana kes&auml;n&auml; Arka jakoi lepopaikan toisen aikuisen norpan kanssa. Kyseess&auml; ei kuitenkaan ollut sen kaksossisarus Parka, josta ei valitettavasti ole tehty havaintoja syntym&auml;kev&auml;&auml;n j&auml;lkeen. </p>
	  <p class=pfooter-link>
	    <a href="http://norppagalleria.wwf.fi:80/gallery.jsp">Siirry galleriaan >></a>
	  </p>
	  
	</div>
</div>


<div class="row grey-outline">
	<div class="col-md-6">
    <div class="crop-outer">
      <div class="crop">
        <a href="http://norppagalleria.wwf.fi:80/gallery.jsp"><img src="cust/mantamatcher/img/front-slider/Mitro_1.jpg" /></a>
      	<p class="image-copyright"> &copy; UEF / WWF </p>
      </div>
    </div>
  </div>

	<div class="col-md-6 full-height per-seal-info">
	  <h2 class="greenish">Mitro</h2>
	  <p>Mitro on reipas liikkeiss&auml;&auml;n. Se havaittiin ensimm&auml;isen kerran Pihlajavedell&auml; vuonna 2011. Se loikoili samoilla tienoilla my&ouml;s seuraavana vuonna, mutta vuonna 2013 Mitro yll&auml;tti tutkijat t&auml;ysin: se tallentui nimitt&auml;in riistakamerakuviin yli 35 kilometrin p&auml;&auml;ss&auml; aiemmin suosimastaan paikasta. Mitro oli my&ouml;s GPS-seurannassa, jonka aikana sen havaittiin tehneen yli 60 kilometrin uintilenkin ja k&auml;yneen aina Puruvedell&auml; asti. Se ei kaihda seuraa, vaan k&ouml;ll&ouml;ttelee usein toisen norpan kanssa vierekk&auml;isill&auml; kivill&auml;. Mitro on kuvattu my&ouml;s makoilemasta rantaruohikolla.</p>
	  <p class=pfooter-link style="margin-top:-10px;margin-bottom:2px;">
	    <a href="http://norppagalleria.wwf.fi:80/gallery.jsp">Siirry galleriaan >></a>
	  </p>
	</div>
</div>

<div class="row grey-outline">

  <div class="col-md-6">
    <div class="crop-outer">
      <div class="crop" >
        <a href="http://norppagalleria.wwf.fi:80/gallery.jsp"><img src="cust/mantamatcher/img/front-slider/enc_2801_cropped.jpg" /></a>
      	<p class="image-copyright"> &copy; UEF / WWF </p>
      </div>
    </div>
  </div>

  <div class="col-md-6 full-height per-seal-info">
    <h2 class="greenish">Siiri</h2>
    <p>Siiri oli toinen Norppalivess&auml; n&auml;hdyist&auml; saimaannorpista. Se on tavattu samalla lepokivell&auml; jo kolmena per&auml;kk&auml;isen&auml; vuotena. Vuonna 2015 Siiri oli It&auml;-Suomen yliopiston tutkijoilla l&auml;hetinseurannassa, jolloin sen liikkeist&auml; saatiin t&auml;rke&auml;&auml; tietoa.</p>
    <p class="pfooter-link">
      <a href="http://norppagalleria.wwf.fi:80/gallery.jsp">Siirry galleriaan >></a>
    </p>
  </div>
</div>





 </div>


  </section>
</div>



<div class="container-fluid">
    <section class="container-fluid text-center  main-section grey-background">
        <div class="row">
            <section class="col-xs-12 col-sm-4 col-md-4 col-lg-4 padding">
                <p class="brand-primary"><span class="massive"><%=numMarkedIndividuals %></span>
                Tunnistettua saimaannorppaa</p>
            </section>
            <section class="col-xs-12 col-sm-4 col-md-4 col-lg-4 padding">
                <p class="brand-primary"><span class="massive"><%=numEncounters %></span> Norppahavaintoa</p>
            </section>
            <section class="col-xs-12 col-sm-4 col-md-4 col-lg-4 padding">

                <p class="brand-primary"><span class="massive"><%=numDataContributors %></span> Osallistujaa</p>
            </section>
        </div>
    </section>
</div>

<div class="container-fluid grey-background">
    <div class="row saimaa-row">


    <div class="col-sm-6">
      <div class="saimaa-info">
      <h2 class="section-header map-header">Selaa norppia alueittain<!--Encounters around the world--></h2>



      <ol>
        <%
        //List<String> locs2=CommonConfiguration.getIndexedPropertyValues("locationID", context);
        ArrayList<String> locs2=new ArrayList<String>();
        locs2.add("PS");
        locs2.add("HV");
        locs2.add("JV");
        locs2.add("PEV");
        locs2.add("KV");
        locs2.add("PV");
        locs2.add("PUV");

        locs2.add("KS");
        locs2.add("LL");
        locs2.add("ES");





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
            <!--<li><a href="<%=urlLoc %>/encounters/searchResults.jsp?locationCodeField=<%=locID %>"><h3><%=locID%></h3></a>-->
            <%
            String actualName=locID.replaceAll("PS","Pohjois-Saimaa")
              .replaceAll("HV","Haukivesi")
              .replaceAll("JV","Joutenvesi")
              .replaceAll("PEV","Pyyvesi – Enonvesi")
              .replaceAll("KV","Kolovesi")
              .replaceAll("PV","Pihlajavesi")
              .replaceAll("PUV","Puruvesi")
              .replaceAll("KS","Lepist&ouml;nselk&auml; – Katosselk&auml; – Haapaselk&auml;")
              .replaceAll("LL","Luonteri – Lietvesi")
              .replaceAll("ES","Etel&auml;-Saimaa");
            %>
            <li><a href="<%=urlLoc %>/gallery.jsp?locationCodeField=<%=locID%>"<h3><%=actualName%></h3></a>
              <p><%=numSightings%> tunnistettua norppaa</p>
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
    <div class="col-sm-6 saimaa-map">
    </div>
  </div>
</div>

<jsp:include page="footer.jsp" flush="true"/>

<script>
window.addEventListener("resize", function(e) { $("#map_canvas").height($("#map_canvas").width()*0.662); });
google.maps.event.addDomListener(window, "resize", function() {
	 google.maps.event.trigger(map, "resize");
	 map.fitBounds(bounds);
	});
</script>

<script src="<%=urlLoc %>/javascript/imageCropper.js"></script>

<script>
var maxHeight = function() {
  return Math.max.apply(null, $("div.per-seal-info").map(function (){
    return $(this).height();
  }).get())
};
var maxWidth = function() {
  Math.max.apply(null, $("div.per-seal-info").map(function (){
      return $(this).width();
  }).get());
}

console.log("maxHeight = "+maxHeight());
//$("div.per-seal-info").height(maxHeight());
//$('div.crop-outer').css('max-height',maxHeight()+'px');

imageCropper.cropIndexPics = function() {
  console.log("cropIndexPics");
  imageCropper.cropPics('div.crop-outer div.crop', 16.0/9.0);
};

/*imageCropper.cropIndexPics();
$( window ).resize(function(){
  imageCropper.cropIndexPics();
});*/



/*
var cropDesktopPics = function(maxHeight) {
  $('.crop-outer .crop a img').each(function() {
    var scaleRatio = maxHeight/$(this).height();
    var newWidth = scaleRatio * $(this).width();
    var horiz_offset = (newWidth - $(this).width())/2;
    if (scaleRatio > 1) {
      $(this).height(maxHeight);
      $(this).css({"max-width":(newWidth)+"px"})
      $(this).width('100%');
      $(this).css('margin-left','-'+horiz_offset+'px');
    }
    else {
      $(this).width('100%');
      $(this).css('margin-left','-'+horiz_offset+'px');
    }
  });
}
cropDesktopPics(maxHeight);

$( window ).resize(function(){
  cropDesktopPics(maxHeight);
});
*/
/*
$('.crop-outer .crop a img').height(maxHeight);

$('.crop-outer .crop a img').each(function() {
  console.log("original height = "+$(this).height());
  $(this).height(maxHeight);
});

*/

</script>

<%
myShepherd.closeDBTransaction();
myShepherd=null;
%>
