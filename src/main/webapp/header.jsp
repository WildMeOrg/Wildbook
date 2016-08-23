<%--
  ~ Wildbook - A Mark-Recapture Framework
  ~ Copyright (C) 2008-2015 Jason Holmberg
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

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.ShepherdProperties,
             org.ecocean.servlet.ServletUtilities,
             org.ecocean.CommonConfiguration,
             org.ecocean.Shepherd,
             org.ecocean.User,
             java.util.ArrayList,
             java.util.List,
             java.util.Properties,
             org.apache.commons.lang.WordUtils,
             org.ecocean.security.Collaboration,
             org.ecocean.ContextConfiguration,
             org.ecocean.media.*
              "
%>

<%
String context="context0";
context=ServletUtilities.getContext(request);
String langCode=ServletUtilities.getLanguageCode(request);
Properties props = new Properties();
props = ShepherdProperties.getProperties("header.properties", langCode, context);

String urlLoc = "http://" + CommonConfiguration.getURLLocation(request);

Boolean isUserResearcher = request.isUserInRole("researcher");
%>
<script>console.log("isUserResearcher=<%=isUserResearcher%>")</script>

<html xmlns="http://www.w3.org/1999/xhtml">
    <head>
      <title><%=CommonConfiguration.getHTMLTitle(context)%>
      </title>
      <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">
      <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
      <meta name="Description"
            content="<%=CommonConfiguration.getHTMLDescription(context) %>"/>
      <meta name="Keywords"
            content="<%=CommonConfiguration.getHTMLKeywords(context) %>"/>
      <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor(context) %>"/>
      <link rel="shortcut icon"
            href="<%=CommonConfiguration.getHTMLShortcutIcon(context) %>"/>
      <link href='http://fonts.googleapis.com/css?family=Oswald:400,300,700' rel='stylesheet' type='text/css'/>
      <link rel="stylesheet" href="<%=urlLoc %>/cust/mantamatcher/css/manta.css" />
      <link href="<%=urlLoc %>/tools/jquery-ui/css/jquery-ui.css" rel="stylesheet" type="text/css"/>
      <link href="<%=urlLoc %>/tools/hello/css/zocial.css" rel="stylesheet" type="text/css"/>
	  <link rel="stylesheet" href="<%=urlLoc %>/tools/jquery-ui/css/themes/smoothness/jquery-ui.css" type="text/css" />


      <script src="<%=urlLoc %>/tools/jquery/js/jquery.min.js"></script>
      <script src="<%=urlLoc %>/tools/bootstrap/js/bootstrap.min.js"></script>
      <script type="text/javascript" src="<%=urlLoc %>/javascript/core.js"></script>
      <script type="text/javascript" src="<%=urlLoc %>/tools/jquery-ui/javascript/jquery-ui.min.js"></script>

     <script type="text/javascript" src="<%=urlLoc %>/javascript/jquery.blockUI.js"></script>
	<script type="text/javascript" src="<%=urlLoc %>/javascript/jquery.cookie.js"></script>


      <script type="text/javascript" src="<%=urlLoc %>/tools/hello/javascript/hello.all.js"></script>
      <script type="text/javascript"  src="<%=urlLoc %>/JavascriptGlobals.js"></script>
      <script type="text/javascript"  src="<%=urlLoc %>/javascript/collaboration.js"></script>

      <script type="text/javascript"  src="<%=urlLoc %>/javascript/imageEnhancer.js"></script>
      <link type="text/css" href="<%=urlLoc %>/css/imageEnhancer.css" rel="stylesheet" />

  	<script src="<%=urlLoc %>/cust/mantamatcher/js/___behaviour.js"></script>

<script src="<%=urlLoc %>/javascript/lazysizes.min.js"></script>

 	<!-- Start Open Graph Tags -->
 	<meta property="og:url" content="<%=request.getRequestURI() %>?<%=request.getQueryString() %>" />
  	
  	
  	<%
  //find the endorseImage if set
  	if((request.getParameter("endorseimage")!=null)&&(!request.getParameter("endorseimage").trim().equals(""))){
  		Shepherd myShepherd=new Shepherd(context);
  		myShepherd.beginDBTransaction();
  		if(myShepherd.getMediaAsset(request.getParameter("endorseimage"))!=null){
  			MediaAsset ma=myShepherd.getMediaAsset(request.getParameter("endorseimage"));
  			%>
	    		<meta property="og:image" content="<%=ma.webURLString().replaceAll("52.40.15.8", "norppagalleria.wwf.fi") %>" />
	    		<meta property="og:image:width" content="<%=(int)ma.getWidth() %>" />
				<meta property="og:image:height" content="<%=(int)ma.getHeight() %>" />
				<meta property="og:description" content="Tutustu sin&auml;kin Pullervoon, Terttuun, Teemuun ja satoihin muihin saimaannorppiin WWF:n Norppagalleriassa!  #Norppagalleria" />
  	
	    	<%
  		}

  	  	else{
  	    	%>
  	    		<meta property="og:image" content="/cust/mantamatcher/img/hero_manta.jpg" />
  	    		<meta property="og:image:width" content="1082" />
				<meta property="og:image:height" content="722" />
				<meta property="og:description" content="Tutustu sin&auml;kin Pullervoon, Terttuun, Teemuun ja satoihin muihin saimaannorppiin WWF:n Norppagalleriassa!  #Norppagalleria" />
  	

  	    	<%
  	    	}
  	}
		else if(request.getRequestURL().toString().indexOf("gallery.jsp")!=-1){
  			//http://norppagalleria.wwf.fi/images/image_for_sharing_individual.jpg
  			%>
  				<meta property="og:image" content="http://norppagalleria.wwf.fi/images/image_for_sharing_individual.jpg" />
	    		<meta property="og:image:width" content="1200" />
				<meta property="og:image:height" content="627" />
				<meta property="og:description" content="WWF:n Norppagalleriassa voit tutustua kaikkiin tunnistettuihin saimaannorppiin. K&auml;y sin&auml;kin katsomassa!" />
  	
				
  			
  			<%
  		}
  	else{
  	%>
  		<meta property="og:image" content="/cust/mantamatcher/img/hero_manta.jpg" />
  		<meta property="og:image:width" content="1082" />
				<meta property="og:image:height" content="722" />
				<meta property="og:description" content="Tutustu sin&auml;kin Pullervoon, Terttuun, Teemuun ja satoihin muihin saimaannorppiin WWF:n Norppagalleriassa!  #Norppagalleria" />
  	
  	<%
  	}

  	%>

  	<!-- End Open Graph Tags -->

    </head>

    <body role="document">

        <!-- ****header**** -->
        <header class="page-header clearfix">
            <nav class="navbar navbar-default navbar-fixed-top">
              <div class="header-top-wrapper">
                <div class="container-fluid navbar-container">
                <!--<a href="http://www.ibeis.org" id="ibeis-badge">An IBEIS Project</a>-->
                  <div class="search-and-secondary-wrapper">
                    <ul class="secondary-nav hor-ul no-bullets">

                      <%
	                      if(request.getUserPrincipal()!=null){
		                  %>

                      <style>
                      @media (min-width: 53.75em) {
                          .navbar-default .navbar-nav>li>a {
                          font-size: 12px;
                          font-size: 0.8rem
                        }
                      }
                      @media (min-width: 63.9em) {
                        .navbar-default .navbar-nav>li>a {
                          font-size: 14px;
                          font-size: 1rem;
                        }
                      }

                      </style>



  		                  <li class="loginout"><a href="<%=urlLoc %>/myAccount.jsp" title="Your Account"><%=props.getProperty("myAccount")%> </a></li>
  		             			<li class="loginout"><a href="<%=urlLoc %>/logout.jsp" ><%=props.getProperty("logout") %></a></li>
		                  <%
	                      }
	                      else{
	                      %>
                        <li class="loginout"> <a href="http://www.wwf.fi" > WWF:n kotisivulle</a> </li>

	                       <li class="loginout"><a href="<%=urlLoc %>/welcome.jsp" title=""><%=props.getProperty("login")%></a></li>
	                      <%
	                      }
                      %>



                    </ul>

                  </div>
                  <a class="navbar-brand" href="<%=urlLoc %>">Wildbook for Mark-Recapture Studies</a>
                </div>
              </div>

              <div class="nav-bar-wrapper">
                <div class="container-fluid cont-primary">
                  <div class="navbar-header clearfix">
                    <button type="button" class="navbar-toggle collapsed" data-toggle="collapse" data-target="#navbar" aria-expanded="false" aria-controls="navbar">
                      <span class="sr-only">Toggle navigation</span>
                      <span class="icon-bar"></span>
                      <span class="icon-bar"></span>
                      <span class="icon-bar"></span>
                    </button>
                  </div>

                  <div id="navbar" class="navbar-collapse collapse">
                  <ul class="nav navbar-nav nav-primary" >
                      <li><a href="<%=urlLoc %>/submit.jsp"><%=props.getProperty("report")%></a></li>

                      <% if (request.getUserPrincipal()!=null) {
                        %>

                      <li class="dropdown">
                        <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false"><%=props.getProperty("learn")%> <span class="caret"></span></a>
                        <ul class="dropdown-menu" role="menu">

                        	<!--
                        	<li class="dropdown"><a href="<%=urlLoc %>/overview.jsp"><%=props.getProperty("aboutYourProject")%></a></li>

                          	<li><a href="<%=urlLoc %>/citing.jsp"><%=props.getProperty("citing")%></a></li>

                          	<li><a href="<%=urlLoc %>/photographing.jsp"><%=props.getProperty("howToPhotograph")%></a></li>
                             -->

                          	<li><a target="_blank" href="http://www.wildme.org/wildbook"><%=props.getProperty("learnAboutShepherd")%></a></li>
                        </ul>
                      </li>

                      <li class="dropdown">
                        <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false"><%=props.getProperty("participate")%> <span class="caret"></span></a>
                        <ul class="dropdown-menu" role="menu">
                        <%
                        if(CommonConfiguration.getProperty("allowAdoptions", context).equals("true")){
                        %>
                          <li><a href="<%=urlLoc %>/adoptananimal.jsp"><%=props.getProperty("adoptions")%></a></li>
                        <%
                        }
                        %>
                          <li><a href="<%=urlLoc %>/userAgreement.jsp"><%=props.getProperty("userAgreement")%></a></li>

                          <!--  examples of navigation dividers
                          <li class="divider"></li>
                          <li class="dropdown-header">Nav header</li>
                           -->

                        </ul>
                      </li>
                      <li class="dropdown">
                        <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false"><%=props.getProperty("individuals")%> <span class="caret"></span></a>
                        <ul class="dropdown-menu" role="menu">
                          <li><a href="<%=urlLoc %>/individualSearchResults.jsp"><%=props.getProperty("viewAll")%></a></li>
                        </ul>
                      </li>
                      <li class="dropdown">
                        <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false"><%=props.getProperty("encounters")%> <span class="caret"></span></a>
                        <ul class="dropdown-menu" role="menu">
                          <li class="dropdown-header"><%=props.getProperty("states")%></li>

                        <!-- list encounters by state -->
                          <% boolean moreStates=true;
                             int cNum=0;
                             while(moreStates) {
                                 String currentLifeState = "encounterState"+cNum;
                                 if (CommonConfiguration.getProperty(currentLifeState,context)!=null) { %>
                                   <li><a href="<%=urlLoc %>/encounters/searchResults.jsp?state=<%=CommonConfiguration.getProperty(currentLifeState,context) %>"><%=props.getProperty("viewEncounters").trim().replaceAll(" ",(" "+WordUtils.capitalize(CommonConfiguration.getProperty(currentLifeState,context))+" "))%></a></li>
                                 <% cNum++;
                                 } else {
                                     moreStates=false;
                                 }
                            } //end while %>
                          <li><a href="<%=urlLoc %>/encounters/thumbnailSearchResults.jsp?noQuery=true"><%=props.getProperty("viewImages")%></a></li>
                          <li><a href="<%=urlLoc %>/xcalendar/calendar.jsp"><%=props.getProperty("encounterCalendar")%></a></li>
                          <% if(request.getUserPrincipal()!=null) { %>
                            <li><a href="<%=urlLoc %>/encounters/searchResults.jsp?username=<%=request.getRemoteUser()%>"><%=props.getProperty("viewMySubmissions")%></a></li>
                          <% } %>
                        </ul>
                      </li>

                      <!-- start locationID sites -->
                       <li class="dropdown">
                        <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false"><%=props.getProperty("sites") %> <span class="caret"></span></a>
                        <ul class="dropdown-menu" role="menu">


                        <!-- list sites by locationID -->
                          <% boolean moreLocationIDs=true;
                             int siteNum=0;
                             while(moreLocationIDs) {
                                 String currentLocationID = "locationID"+siteNum;
                                 if (CommonConfiguration.getProperty(currentLocationID,context)!=null) { %>
                                   <li><a href="<%=urlLoc %>/encounters/searchResultsAnalysis.jsp?locationCodeField=<%=CommonConfiguration.getProperty(currentLocationID,context) %>"><%=WordUtils.capitalize(CommonConfiguration.getProperty(currentLocationID,context)) %></a></li>
                                 <% siteNum++;
                                 } else {
                                	 moreLocationIDs=false;
                                 }
                            } //end while %>

                        </ul>
                      </li>
                      <!-- end locationID sites -->

                      <li class="dropdown">
                        <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false"><%=props.getProperty("search")%> <span class="caret"></span></a>
                        <ul class="dropdown-menu" role="menu">
                              <li><a href="<%=urlLoc %>/encounters/encounterSearch.jsp"><%=props.getProperty("encounterSearch")%></a></li>
                              <li><a href="<%=urlLoc %>/individualSearch.jsp"><%=props.getProperty("individualSearch")%></a></li>
                              <li><a href="<%=urlLoc %>/encounters/searchComparison.jsp"><%=props.getProperty("locationSearch")%></a></li>
                           </ul>
                      </li>

                      <li>
                        <a href="<%=urlLoc %>/contactus.jsp"><%=props.getProperty("contactUs")%> </a>
                      </li>
                      <li class="dropdown">
                        <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false"><%=props.getProperty("administer")%> <span class="caret"></span></a>
                        <ul class="dropdown-menu" role="menu">
                            <% if (CommonConfiguration.getWikiLocation(context)!=null) { %>
                              <li><a target="_blank" href="<%=CommonConfiguration.getWikiLocation(context) %>/photographing.jsp"><%=props.getProperty("userWiki")%></a></li>
                            <% }
                            if(request.getUserPrincipal()!=null) {
                            %>
                              <li><a href="<%=urlLoc %>/myAccount.jsp"><%=props.getProperty("myAccount")%></a></li>
                            <% }
                            if(CommonConfiguration.allowBatchUpload(context) && (request.isUserInRole("admin"))) { %>
                              <li><a href="<%=urlLoc %>/BatchUpload/start"><%=props.getProperty("batchUpload")%></a></li>
                            <% }
                            if(request.isUserInRole("admin")) { %>
                              <li><a href="<%=urlLoc %>/appadmin/admin.jsp"><%=props.getProperty("general")%></a></li>
                              <li><a href="<%=urlLoc %>/appadmin/logs.jsp"><%=props.getProperty("logs")%></a></li>
                                <li><a href="<%=urlLoc %>/software/software.jsp"><%=props.getProperty("gridSoftware")%></a></li>

                                <li><a href="<%=urlLoc %>/appadmin/users.jsp?context=context0"><%=props.getProperty("userManagement")%></a></li>

                                <% if (CommonConfiguration.getTapirLinkURL(context) != null) { %>
                                  <li><a href="<%=CommonConfiguration.getTapirLinkURL(context) %>"><%=props.getProperty("tapirLink")%></a></li>
                                <% }
                                if (CommonConfiguration.getIPTURL(context) != null) { %>
                                  <li><a href="<%=CommonConfiguration.getIPTURL(context) %>"><%=props.getProperty("iptLink")%></a></li>
                                <% } %>
                                <li><a href="<%=urlLoc %>/appadmin/kwAdmin.jsp"><%=props.getProperty("photoKeywords")%></a></li>
                                <% if (CommonConfiguration.allowAdoptions(context)) { %>
                                  <li class="divider"></li>
                                  <li class="dropdown-header"><%=props.getProperty("adoptions")%></li>
                                  <li><a href="<%=urlLoc %>/adoptions/adoption.jsp"><%=props.getProperty("createEditAdoption")%></a></li>
                                  <li><a href="<%=urlLoc %>/adoptions/allAdoptions.jsp"><%=props.getProperty("viewAllAdoptions")%></a></li>
                                  <li class="divider"></li>
                                <% } %>
                                <li><a target="_blank" href="http://www.wildme.org/wildbook"><%=props.getProperty("shepherdDoc")%></a></li>
                                <li><a href="<%=urlLoc %>/javadoc/index.html">Javadoc</a></li>
                                <% if(CommonConfiguration.isCatalogEditable(context)) { %>
                                  <li class="divider"></li>
                                  <li><a href="<%=urlLoc %>/appadmin/import.jsp"><%=props.getProperty("dataImport")%></a></li>
                                <%
                                }

                            } //end if admin
                            %>
                                <li class="divider"></li>
                                  <li class="dropdown-header"><%=props.getProperty("grid")%></li>

                                <li><a href="<%=urlLoc %>/appadmin/scanTaskAdmin.jsp?context=context0"><%=props.getProperty("gridAdministration")%></a></li>
                                <li><a href="<%=urlLoc %>/software/software.jsp"><%=props.getProperty("gridSoftware")%></a></li>

                        </ul>
                      </li>
                      <% }  else { %> <!-- end if user logged in -->



                      <li>
                        <a href="tasta.jsp">T&Auml;ST&Auml; ON KYSE</a>
                      </li>

                      <li>
                        <a href="http://norppagalleria.wwf.fi/gallery.jsp">GALLERIA</a>
                      </li>

                      <% } %>


                      <li class="donate pull-right"><a class="bc1-primary-bkg donate-link" href="http://wwf.fi/norppakummiksi/" itemprop="url">Liity ja lahjoita</a>
                        <ul class="nav-donate" itemscope="" itemtype="http://www.schema.org/SiteNavigationElement">
                          <li><a href="#">Liity kummiksi</a></li>
                          <li><a href="#">Tee kertalahjoitus</a></li>
                        </ul>
                      </li>

					 <%
	                 if(request.getUserPrincipal()!=null){
		             %>
                      <li class="pull-right">
                        <div class="search-wrapper">
                          <label class="search-field-header">
                                <form name="form2" method="get" action="<%=urlLoc %>/individuals.jsp" style="margin-bottom:0">
                                  <input type="text" id="search-site" placeholder="Etsi nimell&auml;" class="search-query form-control navbar-search ui-autocomplete-input" autocomplete="off" name="number" />
                                  <input type="hidden" name="langCode" value="<%=langCode%>"/>
                                  <!--<input type="submit" class="submitLink" value="search"/>-->
                              </form>
                          </label>
                        </div>
                      </li>
					<%
	                 }
					%>

                    </ul>





                  </div>

                </div>
              </div>
            </nav>
        </header>

        <script>
        $('#search-site').autocomplete({
            appendTo: $('#navbar-top'),
            response: function(ev, ui) {
                if (ui.content.length < 1) {
                    $('#search-help').show();
                } else {
                    $('#search-help').hide();
                }
            },
            select: function(ev, ui) {
                if (ui.item.type == "individual") {
                    window.location.replace("<%=("http://" + CommonConfiguration.getURLLocation(request)+"/individuals.jsp?number=") %>" + ui.item.value);
                }
                else if (ui.item.type == "locationID") {
                	window.location.replace("<%=("http://" + CommonConfiguration.getURLLocation(request)+"/encounters/searchResultsAnalysis.jsp?locationCodeField=") %>" + ui.item.value);
                }
                /*
                //restore user later
                else if (ui.item.type == "user") {
                    window.location.replace("/user/" + ui.item.value);
                }
                else {
                    alertplus.alert("Unknown result [" + ui.item.value + "] of type [" + ui.item.type + "]");
                }
                */
                return false;
            },
            //source: app.config.wildbook.proxyUrl + "/search"
            source: function( request, response ) {
                $.ajax({
                    url: '<%=("http://" + CommonConfiguration.getURLLocation(request)) %>/SiteSearch',
                    dataType: "json",
                    data: {
                        term: request.term
                    },
                    success: function( data ) {
                        var res = $.map(data, function(item) {
                            var label="";
                            if ((item.type == "individual")&&(item.species!=null)) {
//                                label = item.species + ": ";
                            }
                            else if (item.type == "user") {
                                label = "User: ";
                            } else {
                                label = "";
                            }
                            return {label: label + item.label,
                                    value: item.value,
                                    type: item.type};
                            });

                        response(res);
                    }
                });
            }
        });
        </script>
        <script>
        		var ipAddress   = "83.142.73.162";
        		var xipHostname = "http://patternlab.*.xip.io";
        	</script>	<script>
        		var patternPaths = {"atoms":{"animations":"00-atoms-01-global-animations","colors":"00-atoms-01-global-colors","copyright":"00-atoms-01-global-copyright","donate-link":"00-atoms-01-global-donate-link","fonts":"00-atoms-01-global-fonts","visibility":"00-atoms-01-global-visibility","wwf-copyright":"00-atoms-01-global-wwf-copyright","buttons":"00-atoms-buttons-00-buttons","buttons-brand-colors-1":"00-atoms-buttons-01-buttons-brand-colors-1","buttons-brand-colors-2":"00-atoms-buttons-02-buttons-brand-colors-2","buttons-brand-colors-3":"00-atoms-buttons-03-buttons-brand-colors-3","buttons-brand-colors-4":"00-atoms-buttons-04-buttons-brand-colors-4","checkbox":"00-atoms-forms-checkbox","html5-inputs":"00-atoms-forms-html5-inputs","radio-buttons":"00-atoms-forms-radio-buttons","select-menu":"00-atoms-forms-select-menu","text-fields":"00-atoms-forms-text-fields","icon-angle-down":"00-atoms-icons-icon-angle-down","icon-angle-left":"00-atoms-icons-icon-angle-left","icon-angle-right":"00-atoms-icons-icon-angle-right","icon-angle-up":"00-atoms-icons-icon-angle-up","icon-blog":"00-atoms-icons-icon-blog","icon-chat":"00-atoms-icons-icon-chat","icon-chevron-left":"00-atoms-icons-icon-chevron-left","icon-chevron-right":"00-atoms-icons-icon-chevron-right","icon-earth":"00-atoms-icons-icon-earth","icon-facebook-btn":"00-atoms-icons-icon-facebook-btn","icon-facebook":"00-atoms-icons-icon-facebook","icon-file-pdf":"00-atoms-icons-icon-file-pdf","icon-gallery":"00-atoms-icons-icon-gallery","icon-google-plus-btn":"00-atoms-icons-icon-google-plus-btn","icon-google-plus":"00-atoms-icons-icon-google-plus","icon-grid":"00-atoms-icons-icon-grid","icon-home":"00-atoms-icons-icon-home","icon-list":"00-atoms-icons-icon-list","icon-menu":"00-atoms-icons-icon-menu","icon-minus":"00-atoms-icons-icon-minus","icon-ok-btn":"00-atoms-icons-icon-ok-btn","icon-ok":"00-atoms-icons-icon-ok","icon-plus":"00-atoms-icons-icon-plus","icon-remove-btn":"00-atoms-icons-icon-remove-btn","icon-remove":"00-atoms-icons-icon-remove","icon-rss":"00-atoms-icons-icon-rss","icon-search":"00-atoms-icons-icon-search","icon-twitter-btn":"00-atoms-icons-icon-twitter-btn","icon-twitter":"00-atoms-icons-icon-twitter","icon-vimeo":"00-atoms-icons-icon-vimeo","icon-youtube":"00-atoms-icons-icon-youtube","favicon":"00-atoms-images-favicon","featured-image":"00-atoms-images-featured-image","flex-slider-image":"00-atoms-images-flex-slider-image","image-with-copyright":"00-atoms-images-image-with-copyright","loading-icon":"00-atoms-images-loading-icon","medium-image":"00-atoms-images-medium-image","news-feed-image":"00-atoms-images-news-feed-image","rounded-image":"00-atoms-images-rounded-image","small-image":"00-atoms-images-small-image","wide-landscape-image":"00-atoms-images-wide-landscape-image","definition":"00-atoms-lists-definition","ordered":"00-atoms-lists-ordered","styled-definition":"00-atoms-lists-styled-definition","unordered":"00-atoms-lists-unordered","logo":"00-atoms-logos-00-logo","logo-image-svg":"00-atoms-logos-01-logo-image-svg","logo-image-png":"00-atoms-logos-02-logo-image-png","logo-link":"00-atoms-logos-03-logo-link","logo-alt":"00-atoms-logos-04-logo-alt","logo-alt-image-svg":"00-atoms-logos-05-logo-alt-image-svg","logo-alt-image-png":"00-atoms-logos-06-logo-alt-image-png","logo-alt-link":"00-atoms-logos-07-logo-alt-link","youtube-video":"00-atoms-media-youtube-video","area-list-nav":"00-atoms-navigation-area-list-nav","area-teaser-nav":"00-atoms-navigation-area-teaser-nav","footer-follow-links":"00-atoms-navigation-footer-follow-links","footer-navigation":"00-atoms-navigation-footer-navigation","footer-sitemap":"00-atoms-navigation-footer-sitemap","header-primary-nav-bc4":"00-atoms-navigation-header-primary-nav-bc4","header-primary-nav-bc5":"00-atoms-navigation-header-primary-nav-bc5","header-primary-nav":"00-atoms-navigation-header-primary-nav","header-secondary-nav":"00-atoms-navigation-header-secondary-nav","news-feed-nav":"00-atoms-navigation-news-feed-nav","table":"00-atoms-tables-table","blockquote":"00-atoms-text-blockquote","comment-bubble":"00-atoms-text-comment-bubble","definition-figure":"00-atoms-text-definition-figure","definition-term":"00-atoms-text-definition-term","emphasis-colors":"00-atoms-text-emphasis-colors","headings-with-links":"00-atoms-text-headings-with-links","headings":"00-atoms-text-headings","hr":"00-atoms-text-hr","ingress":"00-atoms-text-ingress","inline-elements":"00-atoms-text-inline-elements","lorem-ipsum":"00-atoms-text-lorem-ipsum","paragraph":"00-atoms-text-paragraph","preformatted-text":"00-atoms-text-preformatted-text","site-intro":"00-atoms-text-site-intro","time":"00-atoms-text-time"},"molecules":{"accordion":"01-molecules-components-accordion","flex-slide":"01-molecules-components-flex-slide","google-maps":"01-molecules-components-google-maps","more-info":"01-molecules-components-more-info","social-share":"01-molecules-components-social-share","tabs":"01-molecules-components-tabs","comment-form":"01-molecules-forms-comment-form","generated-form-with-errors":"01-molecules-forms-generated-form-with-errors","generated-form-without-errors":"01-molecules-forms-generated-form-without-errors","newsletter-form":"01-molecules-forms-newsletter-form","search":"01-molecules-forms-search","icons":"01-molecules-icons-and-logos-icons","logos":"01-molecules-icons-and-logos-logos","basic-grid":"01-molecules-layout-00-basic-grid","changing-grid":"01-molecules-layout-01-changing-grid","jumping-grid":"01-molecules-layout-02-jumping-grid","nested-grid":"01-molecules-layout-03-nested-grid","smaller-gutter":"01-molecules-layout-04-smaller-gutter","blocked-grid":"01-molecules-layout-05-blocked-grid","no-gutter":"01-molecules-layout-06-no-gutter","list-items":"01-molecules-layout-07-list-items","no-additional-classes":"01-molecules-layout-08-no-additional-classes","complex-grid":"01-molecules-layout-09-complex-grid","contacts-with-image":"01-molecules-media-contacts-with-image","figure-with-caption-and-copyright":"01-molecules-media-figure-with-caption-and-copyright","figure-with-caption":"01-molecules-media-figure-with-caption","figure":"01-molecules-media-figure","main-article-image":"01-molecules-media-main-article-image","youtube-video":"01-molecules-media-youtube-video","area-navigation":"01-molecules-navigation-area-navigation","article-navigation":"01-molecules-navigation-article-navigation","breadcrumbs-immersive":"01-molecules-navigation-breadcrumbs-immersive","breadcrumbs":"01-molecules-navigation-breadcrumbs","follow-links":"01-molecules-navigation-follow-links","footer-navigation-with-logo":"01-molecules-navigation-footer-navigation-with-logo","footer-navigation":"01-molecules-navigation-footer-navigation","header-navigation-bc4":"01-molecules-navigation-header-navigation-bc4","header-navigation-bc5":"01-molecules-navigation-header-navigation-bc5","header-navigation":"01-molecules-navigation-header-navigation","pagination":"01-molecules-navigation-pagination","action-teaser-with-image":"01-molecules-teasers-action-teaser-with-image","area-teaser-with-image":"01-molecules-teasers-area-teaser-with-image","download-teaser-with-image":"01-molecules-teasers-download-teaser-with-image","medium-teaser-with-title-and-caption":"01-molecules-teasers-medium-teaser-with-title-and-caption","promo-teaser-with-image":"01-molecules-teasers-promo-teaser-with-image","related-teaser-with-image":"01-molecules-teasers-related-teaser-with-image","social-media-teaser-with-image":"01-molecules-teasers-social-media-teaser-with-image","social-media-teaser-without-image":"01-molecules-teasers-social-media-teaser-without-image","wide-teaser-with-title-and-caption":"01-molecules-teasers-wide-teaser-with-title-and-caption","address":"01-molecules-text-address","article-heading":"01-molecules-text-article-heading","attribution":"01-molecules-text-attribution","blockquote-with-citation":"01-molecules-text-blockquote-with-citation","byline-author":"01-molecules-text-byline-author","contact-info":"01-molecules-text-contact-info","copyright":"01-molecules-text-copyright","definition-figures":"01-molecules-text-definition-figures","entry-summary":"01-molecules-text-entry-summary","footer-copyright":"01-molecules-text-footer-copyright","footer-heading":"01-molecules-text-footer-heading","news-feed-heading":"01-molecules-text-news-feed-heading","news-item":"01-molecules-text-news-item","page-intro":"01-molecules-text-page-intro","page-section":"01-molecules-text-page-section","publish-date":"01-molecules-text-publish-date","pullquote":"01-molecules-text-pullquote","support-info":"01-molecules-text-support-info"},"organisms":{"header":"02-organisms-00-common-00-header","header-bc4":"02-organisms-00-common-01-header-bc4","header-bc5":"02-organisms-00-common-01-header-bc5","footer":"02-organisms-00-common-02-footer","home-header":"02-organisms-00-common-home-header","page-header":"02-organisms-00-common-page-header","article-header":"02-organisms-article-00-article-header","article-body":"02-organisms-article-01-article-body","article-footer":"02-organisms-article-02-article-footer","article-immersive-header":"02-organisms-article-immersive-00-article-immersive-header","article-immersive-body":"02-organisms-article-immersive-01-article-immersive-body","article-immersive-footer":"02-organisms-article-immersive-02-article-immersive-footer","flex-slider":"02-organisms-components-flex-slider","article-figures":"02-organisms-content-article-figures","contacts":"02-organisms-content-contacts","definition-figures":"02-organisms-content-definition-figures","news-feed-header":"02-organisms-news-feed-00-news-feed-header","news-feed-body":"02-organisms-news-feed-01-news-feed-body","action-teasers-with-title":"02-organisms-teasers-action-teasers-with-title","action-teasers":"02-organisms-teasers-action-teasers","area-teasers":"02-organisms-teasers-area-teasers","download-teasers-with-title":"02-organisms-teasers-download-teasers-with-title","download-teasers":"02-organisms-teasers-download-teasers","medium-teasers":"02-organisms-teasers-medium-teasers","promo-teasers-with-title":"02-organisms-teasers-promo-teasers-with-title","promo-teasers":"02-organisms-teasers-promo-teasers","related-teasers-with-title":"02-organisms-teasers-related-teasers-with-title","related-teasers":"02-organisms-teasers-related-teasers","social-feed-with-title":"02-organisms-teasers-social-feed-with-title","social-media-teasers":"02-organisms-teasers-social-media-teasers","wide-teasers-with-title":"02-organisms-teasers-wide-teasers-with-title","wide-teasers":"02-organisms-teasers-wide-teasers"},"templates":{"area":"03-templates-area","article-immersive":"03-templates-article-immersive","article":"03-templates-article","contacts-page":"03-templates-contacts-page","homepage":"03-templates-homepage","news-feed":"03-templates-news-feed"},"pages":{"article-immersive":"04-pages-article-immersive","article":"04-pages-article","contacts-page":"04-pages-contacts-page","homepage":"04-pages-homepage","news-feed":"04-pages-news-feed"}}
        	</script>	<script>
        		var viewAllPaths = {"atoms":{"global":"00-atoms-01-global","buttons":"00-atoms-buttons","forms":"00-atoms-forms","icons":"00-atoms-icons","images":"00-atoms-images","lists":"00-atoms-lists","logos":"00-atoms-logos","media":"00-atoms-media","navigation":"00-atoms-navigation","tables":"00-atoms-tables","text":"00-atoms-text"},"molecules":{"components":"01-molecules-components","forms":"01-molecules-forms","icons-and-logos":"01-molecules-icons-and-logos","layout":"01-molecules-layout","media":"01-molecules-media","navigation":"01-molecules-navigation","teasers":"01-molecules-teasers","text":"01-molecules-text"},"organisms":{"common":"02-organisms-00-common","article":"02-organisms-article","article-immersive":"02-organisms-article-immersive","components":"02-organisms-components","content":"02-organisms-content","news-feed":"02-organisms-news-feed","teasers":"02-organisms-teasers"}}
        	</script>
        <!-- ****/header**** -->
