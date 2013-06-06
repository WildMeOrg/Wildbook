
<%--
  ~ The Shepherd Project - A Mark-Recapture Framework
  ~ Copyright (C) 2011 Jason Holmberg
  ~
  ~ This program is free software; you can redistribute it and/or
  ~ modify it under the terms of the GNU General Public License
  ~ as published by the Free Software Foundation; either version 2
  ~ of the License, or (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU General Public License for more details.
  ~
  ~ You should have received a copy of the GNU General Public License
  ~ along with this program; if not, write to the Free Software
  ~ Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
  --%>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
         import="com.drew.imaging.jpeg.JpegMetadataReader, com.drew.metadata.Directory, com.drew.metadata.Metadata, com.drew.metadata.Tag, org.ecocean.*,org.ecocean.servlet.ServletUtilities,org.ecocean.Util,org.ecocean.Measurement, org.ecocean.Util.*, org.ecocean.genetics.*, org.ecocean.tag.*, java.awt.Dimension, javax.jdo.Extent, javax.jdo.Query, java.io.File, java.text.DecimalFormat, java.util.*" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>         



<%

//get encounter number
String num = request.getParameter("number").replaceAll("\\+", "").trim();

//let's set up references to our file system components
String rootWebappPath = getServletContext().getRealPath("/");
File webappsDir = new File(rootWebappPath).getParentFile();
File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName());
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
  String langCode = "en";

  //check what language is requested
  if (session.getAttribute("langCode") != null) {
    langCode = (String) session.getAttribute("langCode");
  }


//let's load encounters.properties
  //Properties encprops = new Properties();
  //encprops.load(getClass().getResourceAsStream("/bundles/" + langCode + "/encounter.properties"));

  Properties encprops = ShepherdProperties.getProperties("encounter.properties", langCode);


  pageContext.setAttribute("num", num);


  Shepherd myShepherd = new Shepherd();
  Extent allKeywords = myShepherd.getPM().getExtent(Keyword.class, true);
  Query kwQuery = myShepherd.getPM().newQuery(allKeywords);
  boolean proceed = true;
  boolean haveRendered = false;

  pageContext.setAttribute("set", encprops.getProperty("set"));
%>



<head>
  <title><%=encprops.getProperty("encounter") %> <%=num%>
  </title>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
  <meta name="Description"
        content="<%=CommonConfiguration.getHTMLDescription() %>"/>
  <meta name="Keywords"
        content="<%=CommonConfiguration.getHTMLKeywords() %>"/>
  <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor() %>"/>
  <link href="<%=CommonConfiguration.getCSSURLLocation(request) %>"
        rel="stylesheet" type="text/css"/>
  <link rel="shortcut icon"
        href="<%=CommonConfiguration.getHTMLShortcutIcon() %>"/>
  <style type="text/css">
    <!--

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
          	      map: map
          	  });
          
          	  map.setCenter(location);
          	  
          	    var ne_lat_element = document.getElementById('lat');
          	    var ne_long_element = document.getElementById('longitude');
          
          
          	    ne_lat_element.value = location.lat();
          	    ne_long_element.value = location.lng();
	}
	</script>

  <script type="text/javascript">
  

  
      hs.graphicsDir = '../highslide/highslide/graphics/';
      hs.align = 'center';
      hs.transitions = ['expand', 'crossfade'];
      hs.outlineType = 'rounded-white';
      hs.fadeInOut = true;


    //block right-click user copying if no permissions available
    <%
    if(request.getUserPrincipal()!=null){
    %>
    hs.blockRightClick = false;
    <%
    }
    else{
    %>
    hs.blockRightClick = true;
	<%
    }
	%>
    // Add the controlbar
    hs.addSlideshow({
      //slideshowGroup: 'group1',
      interval: 5000,
      repeat: false,
      useControls: true,
      fixedControls: 'fit',
      overlayOptions: {
        opacity: 0.75,
        position: 'bottom center',
        hideOnMouseOut: true
      }
    });
    
    //test comment
    


  </script>
  
  <script>
            function initialize() {
            //alert("Initializing map!");
              var mapZoom = 2;
          	if($("#map_canvas").hasClass("full_screen_map")){mapZoom=3;}
      
              
              var center = new google.maps.LatLng(10.8, 160.8);
              
              map = new google.maps.Map(document.getElementById('map_canvas'), {
                zoom: mapZoom,
                center: center,
                mapTypeId: google.maps.MapTypeId.HYBRID
        });
        
        	if(marker!=null){
			marker.setMap(map);    
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
</style>

<script src="http://maps.google.com/maps/api/js?sensor=false"></script>
<script type="text/javascript" src="http://ajax.googleapis.com/ajax/libs/jquery/1.4.1/jquery.min.js"></script>

<!--added below for improved map selection -->
 <script type="text/javascript" src="http://geoxml3.googlecode.com/svn/branches/polys/geoxml3.js"></script>


  <script type="text/javascript" src="StyledMarker.js"></script>

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

<body <%if (request.getParameter("noscript") == null) {%>
  onload="initialize()" <%}%>>
<div id="wrapper">
<div id="page">
<jsp:include page="../header.jsp" flush="true">

  <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
  
</jsp:include>
<div id="main">
<%
  myShepherd.beginDBTransaction();


  if (myShepherd.isEncounter(num)) {

    try {

      Encounter enc = myShepherd.getEncounter(num);
      pageContext.setAttribute("enc", enc);
      String livingStatus = "";
      if ((enc.getLivingStatus()!=null)&&(enc.getLivingStatus().equals("dead"))) {
        livingStatus = " (deceased)";
      }
      //int numImages = enc.getAdditionalImageNames().size();
	int numImages=myShepherd.getAllSinglePhotoVideosForEncounter(enc.getCatalogNumber()).size();
      
//let's see if this user has ownership and can make edits
      boolean isOwner = ServletUtilities.isUserAuthorizedForEncounter(enc, request);
      pageContext.setAttribute("editable", isOwner && CommonConfiguration.isCatalogEditable());
      boolean loggedIn = false;
      try{
      	if(request.getUserPrincipal()!=null){loggedIn=true;}
      }
      catch(NullPointerException nullLogged){}
      


%>
<table width="720" border="0" cellpadding="3" cellspacing="5">
<tr>
  <td colspan="3">
    <%
      if ((enc.getState()!=null)&&(enc.getState().equals("unidentifiable"))) {%>
    <table width="810">
      <tr>
        <td bgcolor="#0033CC" colspan="3">
          <p><font color="#FFFFFF" size="4"><%=encprops.getProperty("unidentifiable_title") %>
            : <%=num%><%=livingStatus %>
          </font>
        </td>
      </tr>
    </table>
    </p>
    <%

    } 
    else if ((enc.getState()!=null)&&(enc.getState().equals("unapproved"))) {%>
    <table width="810">
      <tr>
        <td bgcolor="#CC6600" colspan="3">
          <p><font color="#FFFFFF" size="4"><%=encprops.getProperty("unapproved_title") %>
            : <%=num%><%=livingStatus %>
          </font>
          </p>
        </td>
      </tr>
    </table>
    <%
    } 
    else {
    %>

    <p><font size="4"><strong><%=encprops.getProperty("title") %></strong>: <%=num%><%=livingStatus %>
    </font></p>
<%
    }
%>
     
    <p class="caption"><em><%=encprops.getProperty("description") %></em></p>
 <table><tr valign="middle">  
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
<!-- Facebook LIKE button -->
<div class="fb-like" data-send="false" data-layout="button_count" data-width="100" data-show-faces="false"></div>
</td>
</tr></table> 
<br />
<br />
    <%
    if (enc.isAssignedToMarkedIndividual().equals("Unassigned")) {
  %>
    <p class="para"><img align="absmiddle" src="../images/tag_big.gif" width="50px" height="*">
      <%=encprops.getProperty("identified_as") %>: <%=enc.isAssignedToMarkedIndividual()%> 
      <%
        if (isOwner && CommonConfiguration.isCatalogEditable()) {
      %>
      <font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=manageIdentity">edit</a>]</font>
      <%
        }
      %>
    </p>
    <%
    } else {
    %>
    <p class="para"><img align="absmiddle" src="../images/tag_big.gif" width="50px" height="*">
      <%=encprops.getProperty("identified_as") %>: <a
        href="../individuals.jsp?langCode=<%=langCode%>&number=<%=enc.isAssignedToMarkedIndividual()%><%if(request.getParameter("noscript")!=null){%>&noscript=true<%}%>"><%=enc.isAssignedToMarkedIndividual()%>
      </a></font>
      <%
        if (isOwner && CommonConfiguration.isCatalogEditable()) {
      %>[<a href="encounter.jsp?number=<%=num%>&edit=manageIdentity">edit</a>]<%
        }
        if (isOwner) {
      %><br> <img align="absmiddle"
                  src="../images/Crystal_Clear_app_matchedBy.gif"> <%=encprops.getProperty("matched_by") %>
      : <%=enc.getMatchedBy()%>
      <%
        if (isOwner && CommonConfiguration.isCatalogEditable()) {
      %>[<a
        href="encounter.jsp?number=<%=num%>&edit=manageMatchedBy#matchedBy">edit</a>]<%
        }
      %> <%
        }
      %>
    </p>
    <%
      } //end else
	
    
    if (enc.getEventID() != null) {
  %>
  <p class="para"><%=encprops.getProperty("eventID") %>:
    <%=enc.getEventID() %>
  </p>
  <%
    }
  
  %>
	<p class="para">
	<%=encprops.getProperty("occurrenceID") %>:
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

        if (isOwner && CommonConfiguration.isCatalogEditable()) {
      %>
      <font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=manageOccurrence">edit</a>]</font>
      <%
        }
      %>
  </p>
<%
    if(CommonConfiguration.showProperty("showTaxonomy")){
    
    String genusSpeciesFound=encprops.getProperty("notAvailable");
    if((enc.getGenus()!=null)&&(enc.getSpecificEpithet()!=null)){genusSpeciesFound=enc.getGenus()+" "+enc.getSpecificEpithet();}
    %>
    
        <p class="para"><img align="absmiddle" src="../images/taxontree.gif">
          <%=encprops.getProperty("taxonomy")%>: <em><%=genusSpeciesFound%></em>&nbsp;<%
            if (isOwner && CommonConfiguration.isCatalogEditable()) {
          %>[<a href="encounter.jsp?number=<%=num%>&edit=genusSpecies#genusSpecies">edit</a>]<%
            }
          %>
       </p>

<%
}
%>
    
    <p class="para"><img align="absmiddle" src="../images/life_icon.gif">
      <%=encprops.getProperty("status")%>: 
      <%
      if(enc.getLivingStatus()!=null){
      %>
      <%=enc.getLivingStatus()%>
       <%
    }
        if (isOwner && CommonConfiguration.isCatalogEditable()) {
      %>[<a
        href="encounter.jsp?number=<%=num%>&edit=livingStatus#livingStatus">edit</a>]<%
        }
      %>
    </p>


    <p class="para">
    	<img align="absmiddle" src="../images/alternateid.gif"> <%=encprops.getProperty("alternate_id")%>
      : <%=enc.getAlternateID()%>
      <%
        if (isOwner && CommonConfiguration.isCatalogEditable()) {
      %>[<a href="encounter.jsp?number=<%=num%>&edit=alternateid#alternateid">edit</a>]<%
        }
      %>
    </p>
    <%


      if ((loggedIn) && (enc.getSubmitterID() != null)) {
    %>
    <p class="para"><img align="absmiddle"
                         src="../images/Crystal_Clear_app_Login_Manager.gif"> <%=encprops.getProperty("assigned_user")%>
      : <%=enc.getSubmitterID()%> <%
        if (isOwner && CommonConfiguration.isCatalogEditable()) {
      %><font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=user#user">edit</a>]</font>
      <%
        }
      %>
    </p>
    <%
      }
    %>
  </td>
</tr>
<tr>
  <%

  String isLoggedInValue="true";
  String isOwnerValue="true";

  if(!loggedIn){isLoggedInValue="false";}
  if(!isOwner){isOwnerValue="false";}

	if(CommonConfiguration.isCatalogEditable()){
	%>

 
 
  <jsp:include page="encounterFormsEmbed.jsp" flush="true">
    <jsp:param name="encounterNumber" value="<%=num%>" />

    	<jsp:param name="isOwner" value="<%=isOwnerValue %>" />

    	<jsp:param name="loggedIn" value="<%=isLoggedInValue %>" />

  </jsp:include>


  <%
		}
		%>
  
<td align="left" valign="top">
<table border="0" cellspacing="0" cellpadding="5">

<tr>
<td width="300" align="left" valign="top">
<p class="para"><strong><%=encprops.getProperty("date") %>
</strong><br/>
  <a
    href="http://<%=CommonConfiguration.getURLLocation(request)%>/xcalendar/calendar.jsp?scDate=<%=enc.getMonth()%>/1/<%=enc.getYear()%>">
    <%=enc.getDate()%>
  </a>
    <%
				if(isOwner&&CommonConfiguration.isCatalogEditable()) {
 					%><font size="-1">[<a
    href="encounter.jsp?number=<%=num%>&edit=date#date">edit</a>]</font> <%
        		}
        		%>
  <br/>

    <%=encprops.getProperty("verbatimEventDate")%>:
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
				if(isOwner&&CommonConfiguration.isCatalogEditable()) {
 					%> <font size="-1">[<a
    href="encounter.jsp?number=<%=num%>&edit=verbatimEventDate#verbatimEventDate">edit</a>]</font> <%
        		}
        		%>
<%
  pageContext.setAttribute("showReleaseDate", CommonConfiguration.showReleaseDate());
%>
<c:if test="${showReleaseDate}">
  <p class="para"><strong><%=encprops.getProperty("releaseDate") %></strong>
    <fmt:formatDate value="${enc.releaseDate}" pattern="dd/MM/yyyy"/>
    <c:if test="${editable}">
        <font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=releaseDate#releaseDate">edit</a>]</font>
    </c:if>
  </p>
</c:if>

<p class="para"><strong><%=encprops.getProperty("location") %>
</strong><br/> 
<%
if(enc.getLocation()!=null){
%>
<%=enc.getLocation()%>
<%
}
%>

  <%
    if (isOwner && CommonConfiguration.isCatalogEditable()) {
  %><font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=location#location">edit</a>]</font>
  <%
    }
  %>
  
  <br/>
  <em><%=encprops.getProperty("locationID") %></em>: <%=enc.getLocationCode()%>
  <%
    if (isOwner && CommonConfiguration.isCatalogEditable()) {%>
  <font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=loccode#loccode">edit</a>]</font>
  <a href="<%=CommonConfiguration.getWikiLocation()%>locationID" target="_blank"><img
    src="../images/information_icon_svg.gif" alt="Help" border="0" align="absmiddle"></a> <%
    }
  %>
  
  <br/>
  <em><%=encprops.getProperty("country") %></em>: 
  <%
  if(enc.getCountry()!=null){
  %>
  <%=enc.getCountry()%>
  <%
  }
    if (isOwner && CommonConfiguration.isCatalogEditable()) {%>
  <font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=country#country">edit</a>]</font>
  <a href="<%=CommonConfiguration.getWikiLocation()%>country" target="_blank"><img
    src="../images/information_icon_svg.gif" alt="Help" border="0" align="absmiddle"></a> <%
    }
  %>
  
  
  <br/>
  <em><%=encprops.getProperty("latitude") %></em>:
  <%
    if ((enc.getDWCDecimalLatitude() != null) && (!enc.getDWCDecimalLatitude().equals("-9999.0"))) {
  %>
  <br/> <%=gpsFormat.format(Double.parseDouble(enc.getDWCDecimalLatitude()))%>
  <%
    }
  %>
  <br/> <em><%=encprops.getProperty("longitude") %></em>:
  <%
    if ((enc.getDWCDecimalLongitude() != null) && (!enc.getDWCDecimalLongitude().equals("-9999.0"))) {
  %>
  <br/> <%=gpsFormat.format(Double.parseDouble(enc.getDWCDecimalLongitude()))%>
  <%
    }
  %>
  <br/> <%
    if (isOwner && CommonConfiguration.isCatalogEditable()) {
  %><font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=gps#map">edit</a>]</font>
  <%
    }
  %><br/> <a href="#map"><%=encprops.getProperty("view_map") %>
  </a>

</p>


  <!-- Display maximumDepthInMeters so long as show_maximumDepthInMeters is not false in commonCOnfiguration.properties-->
    <%
		if(CommonConfiguration.showProperty("maximumDepthInMeters")){
		%>

<p class="para"><strong><%=encprops.getProperty("depth") %>
</strong><br/>
  <%
    if (enc.getDepthAsDouble() !=null) {
  %> 
  <%=enc.getDepth()%> <%=encprops.getProperty("meters")%> <%
  } else {
  %> <%=encprops.getProperty("unknown") %><%
    }
    if (isOwner && CommonConfiguration.isCatalogEditable()) {
  %><font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=depth#depth">edit</a>]</font>
  <%
    }
  %>
</p>
<%
  }
%>
<!-- End Display maximumDepthInMeters -->

<!-- Display maximumElevationInMeters so long as show_maximumElevationInMeters is not false in commonCOnfiguration.properties-->
<%
  if (CommonConfiguration.showProperty("maximumElevationInMeters")) {
%>
<p class="para"><strong><%=encprops.getProperty("elevation") %>
</strong><br/>

<%
    if (enc.getMaximumElevationInMeters()!=null) {
  %> 
    <%=enc.getMaximumElevationInMeters()%> <%=encprops.getProperty("meters")%> <%
  } else {
  %> 
  <%=encprops.getProperty("unknown") %>
  <%
    }


    if (isOwner && CommonConfiguration.isCatalogEditable()) {
  %><font size="-1">[<a
    href="encounter.jsp?number=<%=num%>&edit=elevation#elevation">edit</a>]</font>
  <%
    }
  %>
</p>
<%
  }
%>
<!-- End Display maximumElevationInMeters -->

<p class="para"><strong><%=encprops.getProperty("sex") %>
</strong><br/> <%=enc.getSex()%> <%
 	if(isOwner&&CommonConfiguration.isCatalogEditable()) {
 %><font size="-1">[<a
  href="encounter.jsp?number=<%=num%>&edit=sex#sex">edit</a>]</font>
    <%
 	}
 %>

<p class="para"><strong><%=encprops.getProperty("scarring") %>
</strong><br/> 
<%
String recordedScarring="";
if(enc.getDistinguishingScar()!=null){recordedScarring=enc.getDistinguishingScar();}
%>
<%=recordedScarring%>
    <%
 	if(isOwner&&CommonConfiguration.isCatalogEditable()) {
 	%>
  <font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=scar#scar">edit</a>]</font>
    <%
 	}
 	%>

<p class="para"><strong><%=encprops.getProperty("behavior") %>
</strong> <br/>
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
    if (isOwner && CommonConfiguration.isCatalogEditable()) {
  %>
  <font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=behavior#behavior">edit</a>]</font>
  <%
    }
  %>
</p>
<%
  if (CommonConfiguration.showProperty("showLifestage")) {
%>
<p class="para"><strong><%=encprops.getProperty("lifeStage") %>
</strong> <br/>
  <%
    if (enc.getLifeStage() != null) {
  %>
  <%=enc.getLifeStage()%>
  <%
  } 
    if (isOwner && CommonConfiguration.isCatalogEditable()) {
  %>
  <font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=lifeStage#lifeStage">edit</a>]</font>
  <%
    }
  %>
</p>
<%
  }
%>
<%
  pageContext.setAttribute("showMeasurements", CommonConfiguration.showMeasurements());
  pageContext.setAttribute("showMetalTags", CommonConfiguration.showMeasurements());
  pageContext.setAttribute("showAcousticTag", CommonConfiguration.showAcousticTag());
  pageContext.setAttribute("showSatelliteTag", CommonConfiguration.showSatelliteTag());
%>
<c:if test="${showMeasurements}">
<%
  pageContext.setAttribute("measurementTitle", encprops.getProperty("measurements"));
  pageContext.setAttribute("measurements", Util.findMeasurementDescs(langCode));
%>
<p class="para"><strong><c:out value="${measurementTitle}"></c:out></strong>
<c:if test="${editable and !empty measurements}">
  <font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=measurements#measurements">edit</a>]</font>
</c:if>
<table>
<tr>
<th class="measurement">Type</th><th class="measurement">Size</th><th class="measurement">Units</th><c:if test="${!empty samplingProtocols}"><th class="measurement">Sampling Protocol</th></c:if>
</tr>
<c:forEach var="item" items="${measurements}">
 <% 
    MeasurementDesc measurementDesc = (MeasurementDesc) pageContext.getAttribute("item");
    Measurement event =  enc.findMeasurementOfType(measurementDesc.getType());
    if (event != null) {
        pageContext.setAttribute("measurementValue", event.getValue());
        pageContext.setAttribute("samplingProtocol", Util.getLocalizedSamplingProtocol(event.getSamplingProtocol(), langCode));
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
</c:if>

<c:if test="${showMetalTags}">
<%
  pageContext.setAttribute("metalTagTitle", encprops.getProperty("metalTags"));
  pageContext.setAttribute("metalTags", Util.findMetalTagDescs(langCode));
%>
<p class="para"><strong><c:out value="${metalTagTitle}"></c:out></strong>
<c:if test="${editable and !empty metalTags}">
  <font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=metalTags#metalTags">edit</a>]</font>
</c:if>
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
</c:if>

<c:if test="${showAcousticTag}">
<%
  pageContext.setAttribute("acousticTagTitle", encprops.getProperty("acousticTag"));
  pageContext.setAttribute("acousticTag", enc.getAcousticTag());
%>
<p class="para"><strong><c:out value="${acousticTagTitle}"></c:out></strong>
<c:if test="${editable}">
  <font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=acousticTag#acousticTag">edit</a>]</font>
</c:if>
<table>
<tr>
    <td>Serial Number:</td><td><c:out value="${empty acousticTag ? '' : acousticTag.serialNumber}"/></td>
</tr>
<tr>
    <td>ID:</td><td><c:out value="${empty acousticTag ? '' : acousticTag.idNumber}"/></td>
</tr>
</table>
</p>
</c:if>

<c:if test="${showSatelliteTag}">
<%
  pageContext.setAttribute("satelliteTagTitle", encprops.getProperty("satelliteTag"));
  pageContext.setAttribute("satelliteTag", enc.getSatelliteTag());
%>
<p class="para"><strong><c:out value="${satelliteTagTitle}"></c:out></strong>
<c:if test="${editable}">
  <font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=satelliteTag#satelliteTag">edit</a>]</font>
</c:if>
<table>
<tr>
    <td>Name:</td><td><c:out value="${satelliteTag.name}"/></td>
</tr>
<tr>
    <td>Serial Number:</td><td><c:out value="${empty satelliteTag ? '' : satelliteTag.serialNumber}"/></td>
</tr>
<tr>
    <td>Argos PTT Number:</td><td><c:out value="${empty satelliteTag ? '' : satelliteTag.argosPttNumber}"/></td>
</tr>
</table>
</p>
</c:if>

<%

  if (enc.getDynamicProperties() != null) {
    //let's create a TreeMap of the properties
    StringTokenizer st = new StringTokenizer(enc.getDynamicProperties(), ";");
    while (st.hasMoreTokens()) {
      String token = st.nextToken();
      int equalPlace = token.indexOf("=");
      String nm = token.substring(0, (equalPlace));
      String vl = token.substring(equalPlace + 1);
%>
<p class="para"><img align="absmiddle" src="../images/lightning_dynamic_props.gif"> <strong><%=nm%>
</strong><br/> <%=vl%>
  <%
    if (isOwner && CommonConfiguration.isCatalogEditable()) {
  %>
  <font size="-1">[<a
    href="encounter.jsp?number=<%=num%>&edit=dynamicproperty&name=<%=nm%>#dynamicproperty">edit</a>]</font>
  <%
    }
  %>
</p>


<%
  }

%>


<%
  }
%>

<p class="para"><strong><%=encprops.getProperty("comments") %>
</strong><br/> 
<%
String recordedComments="";
if(enc.getComments()!=null){recordedComments=enc.getComments();}
%>
<%=recordedComments%><br/>
  <%
    if (isOwner && CommonConfiguration.isCatalogEditable()) {
  %><font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=comments#comments">edit</a>]</font>
  <%
    }
  %>

</p>


<p class="para"><strong><%=encprops.getProperty("submitter") %>
</strong> <%
 	if(isOwner&&CommonConfiguration.isCatalogEditable()) {
 %><font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=contact#contact">edit</a>]</font>
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
			
			%> <br/><a href="mailto:<%=nextie%>?subject=Information%20Request%20for%20Stranding%20<%=enc.getCatalogNumber()%>:<%=CommonConfiguration.getProperty("htmlTitle")%>"><%=nextie%></a> <%
			}
				
		}
		else if((enc.getSubmitterEmail()!=null)&&(!enc.getSubmitterEmail().equals(""))) {
			%> <br/>
			<a href="mailto:<%=enc.getSubmitterEmail()%>?subject=Information%20Request%20for%20Stranding%20<%=enc.getCatalogNumber()%>:<%=CommonConfiguration.getProperty("htmlTitle")%>"><%=enc.getSubmitterEmail()%></a> 
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
		%>
		<%
		if((enc.getSubmitterOrganization()!=null)&&(!enc.getSubmitterOrganization().equals(""))){%>
			<br/><%=enc.getSubmitterOrganization()%>
		<%
		}
		if((enc.getSubmitterProject()!=null)&&(!enc.getSubmitterProject().equals(""))){%>
			<br/><%=enc.getSubmitterProject()%>
		<%}%>
		
		
    <%
	}
%>

<p class="para"><strong><%=encprops.getProperty("photographer") %>
</strong> <%
 	if(isOwner&&CommonConfiguration.isCatalogEditable()) {
		 %><font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=contact#contact">edit</a>]</font>
    	<%
 	}
 if(enc.getPhotographerName()!=null){	
 %>
 	<br/> <%=enc.getPhotographerName()%> <%
 }
 
if (isOwner) {

if((enc.getPhotographerEmail()!=null)&&(!enc.getPhotographerEmail().equals(""))){
%>
	<br/><a href="mailto:<%=enc.getPhotographerEmail()%>?subject=Information%20Request%20for%20Stranding%20<%=enc.getCatalogNumber()%>:<%=CommonConfiguration.getProperty("htmlTitle")%>"><%=enc.getPhotographerEmail()%></a> 
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
%>



<p class="para"><strong><%=encprops.getProperty("inform_others") %>
</strong> <%
 	if(isOwner&&CommonConfiguration.isCatalogEditable()) {
 %><font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=others#others">edit</a>]</font>
    <%
 	}
 %><br/> <%
        	if(enc.getInformOthers()!=null){
        		
        		if(enc.getInformOthers().indexOf(",")!=-1) {
        	//break up the string
        	StringTokenizer stzr=new StringTokenizer(enc.getInformOthers(),",");
        	
        	while(stzr.hasMoreTokens()) {
        %> <%=stzr.nextToken()%><br/> <%
				}
				
					}
					else{
			%> <%=enc.getInformOthers()%><br/> <%
			}
				}
				else {
		%>
  <%=encprops.getProperty("none") %>
  <%
			}
		%> <%
	}
 
		 if (isOwner) {
%>
  <!-- Display spot patterning so long as show_spotpatterning is not false in commonCOnfiguration.properties-->
    <%
		if(CommonConfiguration.useSpotPatternRecognition()){
		%>

<p class="para"><strong>Ready to scan</strong> <a
  href="<%=CommonConfiguration.getWikiLocation()%>processing_a_new_encounter"
  target="_blank"><img src="../images/information_icon_svg.gif"
                       alt="Help" border="0" align="absmiddle"></a> <br/>
    <%
 				String ready="No. Please add spot data.";
 	  			if ((enc.getNumSpots()>0)||(enc.getNumRightSpots()>0)) {
 	           		ready="Yes. ";
 	   			if(enc.getNumSpots()>0) {
 	   				ready+=" "+enc.getNumSpots()+" left-side spots added.";
 	   			}
 	   			if(enc.getNumRightSpots()>0) {
 	   				ready+=" "+enc.getNumRightSpots()+" right-side spots added.";
 	   			}
 	   		
 	  }
 		%>
    <%=ready%>
    <%
		if((enc.getNumSpots()>0)||(enc.getNumRightSpots()>0)) { %>
  <br/><font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=rmSpots#rmSpots">remove left or
    right spots</a>]</font> <%
	  	}

    	File leftScanResults = new File(encounterDir.getAbsolutePath() + "/lastFullScan.xml");
    	File rightScanResults = new File(encounterDir.getAbsolutePath() + "/lastFullRightScan.xml");
    	File I3SScanResults = new File(encounterDir.getAbsolutePath() + "/lastFullI3SScan.xml");
    	File rightI3SScanResults = new File(encounterDir.getAbsolutePath() + "/lastFullRightI3SScan.xml");

    	
	  	if((leftScanResults.exists())&&(enc.getNumSpots()>0)) {
	  		%> <br/><br/><a
    href="scanEndApplet.jsp?writeThis=true&number=<%=num%>">Groth:
    Left-side scan results</a> <%
	  			}
	  	if((rightScanResults.exists())&&(enc.getNumRightSpots()>0)) {
	  		%> <br/><br/>
	  		<a href="scanEndApplet.jsp?writeThis=true&number=<%=num%>&rightSide=true">Groth:
    Right-side scan results</a> <%
	  			}
	  	if((I3SScanResults.exists())&&(enc.getNumSpots()>0)) {
	  		%> <br/><br/><a
    href="i3sScanEndApplet.jsp?writeThis=true&number=<%=num%>&I3S=true">I3S:
    Left-side scan results</a> <%
	  			}
	  	if((rightI3SScanResults.exists())&&(enc.getNumRightSpots()>0)) {
	  		%> <br/><br/><a
    href="i3sScanEndApplet.jsp?writeThis=true&number=<%=num%>&rightSide=true&I3S=true">I3S:
    Right-side scan results</a> <%
	  			}
	  		} //end if-owner
		} //end if show spots
	  		%>
  <!-- End Display spot patterning so long as show_spotpatterning is not false in commonConfiguration.properties-->


</td>


<td width="250" align="left" valign="top">
<%
//String isLoggedInValue="true";
//String isOwnerValue="true";

if(!loggedIn){isLoggedInValue="false";}
if(!isOwner){isOwnerValue="false";}
%>

  <jsp:include page="encounterImagesEmbed.jsp" flush="true">
    <jsp:param name="encounterNumber" value="<%=num%>" />

    	<jsp:param name="isOwner" value="<%=isOwnerValue %>" />

    	<jsp:param name="loggedIn" value="<%=isLoggedInValue %>" />

  </jsp:include>




<%
  if (CommonConfiguration.allowAdoptions()) {
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
<a name="tissueSamples"></a>
<p class="para"><img align="absmiddle" src="../images/microscope.gif">
    <strong><%=encprops.getProperty("tissueSamples") %></strong></p>
    <p class="para"><a href="encounter.jsp?number=<%=enc.getCatalogNumber() %>&edit=tissueSample#tissueSample"><img align="absmiddle" width="24px" style="border-style: none;" src="../images/Crystal_Clear_action_edit_add.png" /></a> <a href="encounter.jsp?number=<%=enc.getCatalogNumber() %>&edit=tissueSample#tissueSample"><%=encprops.getProperty("addTissueSample") %></a></p>
<p>
<%
List<TissueSample> tissueSamples=enc.getTissueSamples();
//List<TissueSample> tissueSamples=myShepherd.getAllTissueSamplesForEncounter(enc.getCatalogNumber());

int numTissueSamples=tissueSamples.size();
if(numTissueSamples>0){
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
				</span></td><td style="border-style: none;"><a href="encounter.jsp?number=<%=enc.getCatalogNumber() %>&sampleID=<%=thisSample.getSampleID() %>&analysisID=<%=mito.getAnalysisID() %>&edit=haplotype#haplotype"><img width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a></td><td style="border-style: none;"><a href="../TissueSampleRemoveHaplotype?encounter=<%=enc.getCatalogNumber()%>&sampleID=<%=thisSample.getSampleID()%>&analysisID=<%=mito.getAnalysisID() %>"><img width="20px" height="20px" style="border-style: none;" src="../images/cancel.gif" /></a></td></tr></li>
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
				</span></td><td style="border-style: none;"><a href="encounter.jsp?number=<%=enc.getCatalogNumber() %>&sampleID=<%=thisSample.getSampleID() %>&analysisID=<%=mito.getAnalysisID() %>&edit=sexAnalysis#sexAnalysis"><img width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a></td><td style="border-style: none;"><a href="../TissueSampleRemoveSexAnalysis?encounter=<%=enc.getCatalogNumber()%>&sampleID=<%=thisSample.getSampleID()%>&analysisID=<%=mito.getAnalysisID() %>"><img width="20px" height="20px" style="border-style: none;" src="../images/cancel.gif" /></a></td></tr></li>
			<%
			}
			else if(ga.getAnalysisType().equals("MicrosatelliteMarkers")){
				MicrosatelliteMarkersAnalysis mito=(MicrosatelliteMarkersAnalysis)ga;
				
			%>
			<tr>
				<td style="border-style: none;">
					<p><span class="caption"><strong><%=encprops.getProperty("msMarkers") %></strong></span></p>
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
				<td style="border-style: none;"><a href="encounter.jsp?number=<%=enc.getCatalogNumber() %>&sampleID=<%=thisSample.getSampleID() %>&analysisID=<%=mito.getAnalysisID() %>&edit=msMarkers#msMarkers"><img width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a></td><td style="border-style: none;"><a href="../TissueSampleRemoveMicrosatelliteMarkers?encounter=<%=enc.getCatalogNumber()%>&sampleID=<%=thisSample.getSampleID()%>&analysisID=<%=mito.getAnalysisID() %>"><img width="20px" height="20px" style="border-style: none;" src="../images/cancel.gif" /></a></td></tr></li>
			
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
				</span></td><td style="border-style: none;"><a href="encounter.jsp?number=<%=enc.getCatalogNumber() %>&sampleID=<%=thisSample.getSampleID() %>&analysisID=<%=mito.getAnalysisID() %>&edit=addBiologicalMeasurement#addBiologicalMeasurement"><img width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a></td><td style="border-style: none;"><a href="../TissueSampleRemoveBiologicalMeasurement?encounter=<%=enc.getCatalogNumber()%>&sampleID=<%=thisSample.getSampleID()%>&analysisID=<%=mito.getAnalysisID() %>"><img width="20px" height="20px" style="border-style: none;" src="../images/cancel.gif" /></a></td></tr></li>
			<%
			}
		}
		%>
		</table>
		<p><span class="caption"><a href="encounter.jsp?number=<%=enc.getCatalogNumber() %>&sampleID=<%=thisSample.getSampleID() %>&edit=haplotype#haplotype"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit_add.png" /></a> <a href="encounter.jsp?number=<%=enc.getCatalogNumber() %>&sampleID=<%=thisSample.getSampleID() %>&edit=haplotype#haplotype"><%=encprops.getProperty("addHaplotype") %></a></span></p>
		<p><span class="caption"><a href="encounter.jsp?number=<%=enc.getCatalogNumber() %>&sampleID=<%=thisSample.getSampleID() %>&edit=msMarkers#msMarkers"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit_add.png" /></a> <a href="encounter.jsp?number=<%=enc.getCatalogNumber() %>&sampleID=<%=thisSample.getSampleID() %>&edit=msMarkers#msMarkers"><%=encprops.getProperty("addMsMarkers") %></a></span></p>
		<p><span class="caption"><a href="encounter.jsp?number=<%=enc.getCatalogNumber() %>&sampleID=<%=thisSample.getSampleID() %>&edit=sexAnalysis#sexAnalysis"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit_add.png" /></a> <a href="encounter.jsp?number=<%=enc.getCatalogNumber() %>&sampleID=<%=thisSample.getSampleID() %>&edit=sexAnalysis#sexAnalysis"><%=encprops.getProperty("addGeneticSex") %></a></span></p>
		<p><span class="caption"><a href="encounter.jsp?number=<%=enc.getCatalogNumber() %>&sampleID=<%=thisSample.getSampleID() %>&edit=addBiologicalMeasurement#addBiologicalMeasurement"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit_add.png" /></a> <a href="encounter.jsp?number=<%=enc.getCatalogNumber() %>&sampleID=<%=thisSample.getSampleID() %>&edit=addBiologicalMeasurement#addBiologicalMeasurement"><%=encprops.getProperty("addBiologicalMeasurement") %></a></span></p>
	
	</td>
	
	
	<td><a href="encounter.jsp?number=<%=enc.getCatalogNumber() %>&sampleID=<%=thisSample.getSampleID()%>&edit=tissueSample#tissueSample"><img width="24px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a></td><td><a href="../EncounterRemoveTissueSample?encounter=<%=enc.getCatalogNumber()%>&sampleID=<%=thisSample.getSampleID()%>"><img style="border-style: none;" src="../images/cancel.gif" /></a></td></tr>
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
} //end if loggedIn
%>
<p>
    <%
	  	  	  	if (request.getParameter("noscript")==null) {
	  	  	  %>
<hr />
<p><a name="map"><strong><img
  src="../images/2globe_128.gif" width="56" height="56"
  align="absmiddle"/></a><%=encprops.getProperty("mapping") %></strong></p>

<p><%=encprops.getProperty("map_note") %>
</p>



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
           marker = new StyledMarker({styleIcon:new StyledIcon(StyledIconTypes.MARKER,{color:"<%=haploColor%>",text:"<%=markerText%>"}),position:latLng,map:map});
	    

 
	
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
    </script>
 <div id="map_canvas" style="width: 510px; height: 350px; "></div>
 
 
 <!-- adding ne submit GPS-->
 <%
 if(loggedIn){
 String longy="";
       		String laty="";
       		if(enc.getLatitudeAsDouble()!=null){laty=enc.getLatitudeAsDouble().toString();}
       		if(enc.getLongitudeAsDouble()!=null){longy=enc.getLongitudeAsDouble().toString();}
       		
     		%> <a name="gps"></a>
     		
     		
     		<table>
     		<tr>
				<td>
				<form name="resetGPSform" method="post" action="../EncounterSetGPS">
				    <input name="action" type="hidden" value="resetGPS" />
    				
				<strong><%=encprops.getProperty("latitude")%>:</strong>
		
					<input name="lat" type="text" id="lat" size="10" value="<%=laty%>" /> &deg;
					<strong><%=encprops.getProperty("longitude")%>:</strong>
				<input name="longitude" type="text" id="longitude" size="10" value="<%=longy%>" />
				&deg;
				<br />
				<input name="setGPSbutton" type="submit" id="setGPSbutton" value="<%=encprops.getProperty("setGPS")%>" />
    				
				<br/>
				<br/> GPS coordinates are in the decimal degrees
				format. Do you have GPS coordinates in a different format? <a
					href="http://www.csgnetwork.com/gpscoordconv.html" target="_blank">Click
				here to find a converter.</a>
				<input name="number" type="hidden" value=<%=num%> /> 
				    				
					</form></td>
	</tr>
     		</table>
     		
 <!--end adding submit GPS-->

<%
}

if(loggedIn){

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
    		<jsp:param name="isOwner" value="<%=isOwnerValue %>" />
		</jsp:include>

    <%

 currentImportNum++;
} //end while for jspImports


%>

<br/>
<hr />
<table>
  <tr>
    <td valign="top">
      <img align="absmiddle" src="../images/Crystal_Clear_app_kaddressbook.gif">
    </td>
    <td valign="top">
      <%=encprops.getProperty("auto_comments")%>: </p>
      <%
        if (enc.getRComments() != null) {
      %>
      <div style="text-align:left;border:1px solid black;width:100%;height:400px;overflow-y:scroll;overflow-x:scroll;">
      
      <p class="para"><%=enc.getRComments().replaceAll("\n", "<br />")%>
      </p>
      </div>
      <%
        }
        if (CommonConfiguration.isCatalogEditable()) {
      %>
      <form action="../EncounterAddComment" method="post" name="addComments">
        <p class="para">
          <input name="user" type="hidden" value="<%=request.getRemoteUser()%>" id="user">
          <input name="number" type="hidden" value="<%=enc.getEncounterNumber()%>" id="number">
          <input name="action" type="hidden" value="enc_comments" id="action">

        <p>
          <textarea name="autocomments" cols="50" id="autocomments"></textarea> <br/>
          <input name="Submit" type="submit" value="<%=encprops.getProperty("add_comment")%>">
        </p>
      </form>
    </td>
  </tr>
</table>

<%
      }
    }


  }

%>

</p>
</td>
</tr>

</table>

<br/>
<table>
  <tr>
    <td>
      <%
      if(enc.getInterestedResearchers()!=null){
        Vector trackers = enc.getInterestedResearchers();
        if ((isOwner) && (trackers.size() > 0)) {%>

      <p><font size="-1"><%=encprops.getProperty("trackingEmails")%>: <%

        int numTrack = trackers.size();
        for (int track = 0; track < numTrack; track++) {%> <a
        href="mailto:<%=((String)trackers.get(track))%>"><%=((String) trackers.get(track))%>
      </a></a>&nbsp;|&nbsp;
        <%}%></font></p>

      <%}
      }%>
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
<p>Hit an error.<br /> <%=e.toString()%>
</p>
</body>
</html>
<%
  }

} else {
  myShepherd.rollbackDBTransaction();
  myShepherd.closeDBTransaction();
%>
<p class="para">There is no encounter #<%=num%> in the database.
  Please double-check the encounter number and try again.</p>

<form action="encounter.jsp" method="post" name="encounter"><strong>Go
  to encounter: </strong> <input name="number" type="text" value="<%=num%>"
                                 size="20"> <input name="Go" type="submit" value="Submit"></form>
<p><font color="#990000"><a href="allEncounters.jsp">View
  all encounters</a></font></p>

<p><font color="#990000"><a href="../allIndividuals.jsp">View
  all individuals</a></font></p>

<p></p>
<%}%>


</div>



<jsp:include page="../footer.jsp" flush="true"/>

</div>
<!-- end page -->

</div>

<!--end wrapper -->


<%
if (request.getParameter("noscript") == null) {
%>
<script type="text/javascript">

  function submitForm(oForm) {
    // Hide the code in first div tag
    document.getElementById('formDiv').style.display = 'none';

    // Display code in second div tag
    //document.getElementById('pleaseWaitDiv').style.display = 'block';

    oForm.submit();
  }

</script>
<%
}
%>


</body>
</html>


