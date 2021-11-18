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

<html>
<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.ShepherdProperties,
             org.ecocean.servlet.ServletUtilities,
             org.ecocean.CommonConfiguration,
             org.ecocean.Shepherd,
             org.ecocean.AccessControl,
             org.ecocean.Util,
             org.ecocean.SystemValue,
             org.ecocean.User,
             java.util.ArrayList,
             java.util.List,
             java.util.Properties,
             org.apache.commons.lang.WordUtils,
             org.ecocean.security.Collaboration,
             org.ecocean.ContextConfiguration
              "
%>

<%
String context="context0";
context=ServletUtilities.getContext(request);
String langCode=ServletUtilities.getLanguageCode(request);
Properties props = new Properties();
props = ShepherdProperties.getProperties("header.properties", langCode, context);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("header.jsp");
boolean uwMode = Util.booleanNotFalse(SystemValue.getString(myShepherd, "uwMode"));
String urlLoc = "//" + CommonConfiguration.getURLLocation(request);
boolean isAnonymous = (AccessControl.simpleUserString(request) == null);
String pageTitle = (String)request.getAttribute("pageTitle");
if (pageTitle == null) pageTitle = CommonConfiguration.getHTMLTitle(context);



User thisUser = AccessControl.getUser(request, myShepherd);
/*
if (thisUser == null) {
    System.out.println("USERCHECK: header has logged out user (no check)");
} else {
    System.out.println("USERCHECK: header has uwMode=" + uwMode + " and thisUser=" + thisUser + " username=[" + thisUser.getUsername() + "] affiliation=[" + thisUser.getAffiliation() + "]");
    boolean uwUser = "U-W".equals(thisUser.getAffiliation());
    if (thisUser.getUsername().equals("tomcat")) {
        System.out.println("USERCHECK: tomcat always okay");
    } else if ((uwUser && !uwMode) || (!uwUser && uwMode)) {
        System.out.println("USERCHECK: incompatible user/mode -- invalidating session");
        session.invalidate();
    }
}
*/
%>

<html xmlns="http://www.w3.org/1999/xhtml">
    <head>
      <title><%=pageTitle%></title>
      <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">
      <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
      <meta name="Description"
            content="<%=CommonConfiguration.getHTMLDescription(context) %>"/>
      <meta name="Keywords"
            content="<%=CommonConfiguration.getHTMLKeywords(context) %>"/>
      <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor(context) %>"/>
      <link rel="shortcut icon"
            href="<%=CommonConfiguration.getHTMLShortcutIcon(context) %>"/>
      <link href='//fonts.googleapis.com/css?family=Oswald:400,300,700' rel='stylesheet' type='text/css'/>
      <link rel="stylesheet" href="<%=urlLoc %>/cust/mantamatcher/css/manta.css" />

      <link href="//fonts.googleapis.com/css?family=Open+Sans" rel="stylesheet">

      <link rel="stylesheet" href="<%=urlLoc %>/fonts/elusive-icons-2.0.0/css/elusive-icons.min.css">
      <link rel="stylesheet" href="<%=urlLoc %>/fonts/elusive-icons-2.0.0/css/icon-style-overwrite.css">

      <link href="<%=urlLoc %>/tools/jquery-ui/css/jquery-ui.css" rel="stylesheet" type="text/css"/>
      <link href="<%=urlLoc %>/tools/hello/css/zocial.css" rel="stylesheet" type="text/css"/>
      <!-- <link href="<%=urlLoc %>/tools/timePicker/jquery.ptTimeSelect.css" rel="stylesheet" type="text/css"/> -->
	    <link rel="stylesheet" href="<%=urlLoc %>/tools/jquery-ui/css/themes/smoothness/jquery-ui.css" type="text/css" />

      <link rel="stylesheet" href="<%=urlLoc %>/css/createadoption.css">

      <script src="<%=urlLoc %>/tools/jquery/js/jquery.min.js"></script>
      <script src="<%=urlLoc %>/tools/bootstrap/js/bootstrap.min.js"></script>
      <script type="text/javascript" src="<%=urlLoc %>/javascript/core.js"></script>
      <script type="text/javascript" src="<%=urlLoc %>/tools/jquery-ui/javascript/jquery-ui.min.js"></script>

        <script type="text/javascript" src="<%=urlLoc %>/javascript/ia.js"></script>
        <script type="text/javascript" src="<%=urlLoc %>/javascript/ia.IBEIS.js"></script>  <!-- TODO plugin-ier -->

     <script type="text/javascript" src="<%=urlLoc %>/javascript/jquery.blockUI.js"></script>
	   <script type="text/javascript" src="<%=urlLoc %>/javascript/jquery.cookie.js"></script>


      <script type="text/javascript" src="<%=urlLoc %>/tools/hello/javascript/hello.all.js"></script>


      <script type="text/javascript"  src="<%=urlLoc %>/JavascriptGlobals.js"></script>
      <script type="text/javascript"  src="<%=urlLoc %>/javascript/collaboration.js"></script>

        <link rel="stylesheet" href="<%=urlLoc %>/css/NoteField.css" />
        <script src="<%=urlLoc %>/javascript/NoteField.js"></script>
        <link href="https://cdn.quilljs.com/2.0.0-dev.2/quill.snow.css" rel="stylesheet" />
        <script src="https://cdn.quilljs.com/2.0.0-dev.2/quill.js"></script>

      <script type="text/javascript"  src="<%=urlLoc %>/javascript/imageEnhancer.js"></script>
      <link type="text/css" href="<%=urlLoc %>/css/imageEnhancer.css" rel="stylesheet" />

      <script src="<%=urlLoc %>/javascript/lazysizes.min.js"></script>

 	<!-- Start Open Graph Tags -->
 	<meta property="og:url" content="<%=request.getRequestURI() %>?<%=request.getQueryString() %>" />
  	<meta property="og:site_name" content="<%=CommonConfiguration.getHTMLTitle(context) %>"/>
  	<!-- End Open Graph Tags -->

    <!--  <link rel="stylesheet" href="/resources/demos/style.css">

    <link rel="stylesheet" href="//code.jquery.com/ui/1.12.1/themes/base/jquery-ui.css">
    <script src="https://code.jquery.com/jquery-1.12.4.js"></script>
    <script src="https://code.jquery.com/ui/1.12.1/jquery-ui.js"></script>
    -->

	<!-- Clockpicker on creatSurvey jsp -->
    <script type="text/javascript" src="<%=urlLoc %>/tools/clockpicker/jquery-clockpicker.min.js"></script>
    <link type="text/css" href="<%=urlLoc %>/tools/clockpicker/jquery-clockpicker.min.css" rel="stylesheet" />

    <style>
      ul.nav.navbar-nav {
        width: 100%;
      }

/* this hackiness is cuz we un-fixed the navbar */
.maincontent {
    margin-top: 0 !important;
    padding-top: 0 !important;
}
.navbar {
    margin-bottom: 0 !important;
}
.page-header {
    padding-bottom: 0 !important;
    padding-top: 0 !important;
    margin-top: 0 !important;
}

.partners-header {
    position: absolute;
    bottom: 0;
    padding: 5px;
    right: 0;
}

    .partners-header img {
        height: 70px;
    }
@media (min-width: 768px) {
    .partners-header img {
        height: 140px;
    }
}
    </style>

    </head>

    <body role="document">

        <!-- ****header**** -->
        <header class="page-header clearfix">
            <nav class="navbar navbar-default xnavbar-fixed-top" style="margin-top: -5px;">
              <div class="header-top-wrapper">
                <div class="container">
                <!-- wild-me-badge -->
                  <div class="search-and-secondary-wrapper">
                  <%
                  if(CommonConfiguration.allowAdoptions(context)){
                  %>
                    <a href="<%=urlLoc%>/adoptananimal.jsp"><button name='adopt an animal' class='large adopt'><%=props.getProperty("adoptAnAnimal") %></button></a>
                  <%
                  }
                  %>
                    <%-- <a href="<%=urlLoc %>/adoptashark.jsp"><%=props.getProperty("adoptions")%></a> --%>
                    <ul class="secondary-nav hor-ul no-bullets">

                      <%

	                      if(request.getUserPrincipal()!=null){
	                    	  myShepherd = new Shepherd(context);
	                    	  myShepherd.setAction("header.jsp");

	                          try{
	                        	  myShepherd.beginDBTransaction();
		                    	  String username = request.getUserPrincipal().toString();
		                    	  User user = myShepherd.getUser(username);
		                    	  String fullname=username;
		                    	  if(user.getFullName()!=null){fullname=user.getFullName();}
		                    	  String profilePhotoURL=urlLoc+"/images/empty_profile.jpg";
		                          if(user.getUserImage()!=null){
		                          	profilePhotoURL="/"+CommonConfiguration.getDataDirectoryName(context)+"/users/"+user.getUsername()+"/"+user.getUserImage().getFilename();
		                          }

		                  %>

<!--
		                      		<li><a href="<%=urlLoc %>/myAccount.jsp" title=""><img align="left" title="Your Account" style="border-radius: 3px;border:1px solid #ffffff;margin-top: -7px;" width="*" height="32px" src="<%=profilePhotoURL %>" /></a></li>
-->
		             				      <!-- <li><a href="<%=urlLoc %>/logout.jsp" ><%=props.getProperty("logout") %></a></li> -->

		                      		<%
	                          }
	                          catch(Exception e){e.printStackTrace();}
	                          finally{
	                        	  myShepherd.rollbackDBTransaction();
	                        	  myShepherd.closeDBTransaction();
	                          }
	                      }
	                      else{
	                      %>

	                      	<!-- <li><a href="<%=urlLoc %>/welcome.jsp" title=""><%=props.getProperty("login") %></a></li> -->

	                      <%
	                      }

                      %>

                       <!--
                      <li><a href="#" title="">English</a></li>
                     -->



                      <%
                      if (CommonConfiguration.getWikiLocation(context)!=null) {
                      %>
                        <li style="display: none;"><a target="_blank" href="<%=CommonConfiguration.getWikiLocation(context) %>"><%=props.getProperty("userWiki")%></a></li>
                      <%
                      }



                      List<String> contextNames=ContextConfiguration.getContextNames();
                		int numContexts=contextNames.size();
                		if(numContexts>1){
                		%>

                		<li>
                						<form>
                						<%=props.getProperty("switchContext") %>&nbsp;
                							<select style="color: black;" id="context" name="context">
			                					<%
			                					for(int h=0;h<numContexts;h++){
			                						String selected="";
			                						if(ServletUtilities.getContext(request).equals(("context"+h))){selected="selected=\"selected\"";}
			                					%>

			                						<option value="context<%=h%>" <%=selected %>><%=contextNames.get(h) %></option>
			                					<%
			                					}
			                					%>
                							</select>
                						</form>
                			</li>
                			<script type="text/javascript">

	                			$( "#context" ).change(function() {

		                  			//alert( "Handler for .change() called with new value: "+$( "#context option:selected" ).text() +" with value "+ $( "#context option:selected").val());
		                  			$.cookie("wildbookContext", $( "#context option:selected").val(), {
		                  			   path    : '/',          //The value of the path attribute of the cookie
		                  			                           //(default: path of page that created the cookie).

		                  			   secure  : false          //If set to true the secure attribute of the cookie
		                  			                           //will be set and the cookie transmission will
		                  			                           //require a secure protocol (defaults to false).
		                  			});

		                  			//alert("I have set the wildbookContext cookie to value: "+$.cookie("wildbookContext"));
		                  			location.reload(true);

	                			});

                			</script>
                			<%
                		}
                		%>
                		   <!-- Can we inject language functionality here? -->
                    <%

            		List<String> supportedLanguages=CommonConfiguration.getIndexedPropertyValues("language", context);
            		int numSupportedLanguages=supportedLanguages.size();

            		if(numSupportedLanguages>1){
            		%>
            			<li style="display: none;">


            					<%
            					for(int h=0;h<numSupportedLanguages;h++){
            						String selected="";
            						if(ServletUtilities.getLanguageCode(request).equals(supportedLanguages.get(h))){selected="selected=\"selected\"";}
            						String myLang=supportedLanguages.get(h);
            					%>
            						<img style="cursor: pointer" id="flag_<%=myLang %>" title="<%=CommonConfiguration.getProperty(myLang, context) %>" src="//<%=CommonConfiguration.getURLLocation(request) %>/images/flag_<%=myLang %>.gif" />
            						<script type="text/javascript">

            							$( "#flag_<%=myLang%>" ).click(function() {

            								//alert( "Handler for .change() called with new value: "+$( "#langCode option:selected" ).text() +" with value "+ $( "#langCode option:selected").val());
            								$.cookie("wildbookLangCode", "<%=myLang%>", {
            			   						path    : '/',          //The value of the path attribute of the cookie
            			                           //(default: path of page that created the cookie).

            			   						secure  : false          //If set to true the secure attribute of the cookie
            			                           //will be set and the cookie transmission will
            			                           //require a secure protocol (defaults to false).
            								});

            								//alert("I have set the wildbookContext cookie to value: "+$.cookie("wildbookContext"));
            								location.reload(true);

            							});

            						</script>
            					<%
            					}
            					%>

            		</li>
            		<%
            		}
            		%>
            		<!-- end language functionality injection -->




                    </ul>


                    <style type="text/css">
                      #header-search-button, #header-search-button:hover {
                        color: inherit;
                        background-color: inherit;
                        padding: 0px;
                        margin: 0px;
                      }
                    </style>
                    <script>
                      $('#header-search-button').click(function() {
                        document.forms['header-search'].submit();
                      })
                    </script>


                    <div class="search-wrapper" style="display: none;">
                      <label class="search-field-header">
                            <form name="form2" id="header-search" method="get" action="<%=urlLoc %>/individuals.jsp">
                              <input type="text" id="search-site" placeholder="nickname, id, site, encounter nr., etc." class="search-query form-control navbar-search ui-autocomplete-input" autocomplete="off" name="number" />
                              <button type="submit" id="header-search-button"><span class="el el-lg el-search"></span></button>
                          </form>
                      </label>
                    </div>
                  </div>
                  <a class="navbar-brand" xtarget="_blank" href="<%=urlLoc %>">Wildbook for Mark-Recapture Studies</a>
                </div>
              </div>

              <div class="nav-bar-wrapper">
                <div class="container">
                  <div class="navbar-header clearfix">
                    <button type="button" class="navbar-toggle collapsed" data-toggle="collapse" data-target="#navbar" aria-expanded="false" aria-controls="navbar">
                      <span class="sr-only">Toggle navigation</span>
                      <span class="icon-bar"></span>
                      <span class="icon-bar"></span>
                      <span class="icon-bar"></span>
                    </button>
                  </div>

                  <div id="navbar" class="navbar-collapse collapse">
                  <div id="notifications"><%= Collaboration.getNotificationsWidgetHtml(request) %></div>
                    <ul class="nav navbar-nav">


            <li class="dropdown">
                <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false">ABOUT <span class="caret"></span></a>
                    <ul class="dropdown-menu" role="menu">
                        <li><a href="<%=urlLoc %>/mission.jsp">MISSION AND GOALS</a></li>
                        <li><a href="<%=urlLoc %>/whoAreWe.jsp">WHO WE ARE</a></li>
                        <li><a href="<%=urlLoc %>/financial.jsp">FINANCIAL SUPPORTERS</a></li>
                        <li<a href="<%=urlLoc %>/contactus.jsp">CONTACT</a></li>
                    </ul>
            </li>

            <li class="dropdown">
                <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false">CAT SCIENCE <span class="caret"></span></a>
                    <ul class="dropdown-menu" role="menu">
                        <li><a href="<%=urlLoc %>/why.jsp">WHY THIS IS NEEDED</a></li>
                        <li><a href="<%=urlLoc %>/how.jsp">WHO WE ARE</a></li>
                        <li><a href="<%=urlLoc %>/spayneuter.jsp">S/N IMPACT RESEARCH</a></li>
                    </ul>
            </li>

            <li class="dropdown">
                            <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false">LOCATIONS <span class="caret"></span></a>
                                <ul class="dropdown-menu" role="menu">
                                    <li><a href="<%=urlLoc %>/locations.jsp">CURRENT LOCATIONS</a></li>
                                    <li><a href="<%=urlLoc %>/locations.jsp">COLLABORATE</a></li>
                                </ul>
                        </li>

                      <!-- submit encounter, survey -->

                      <li class="dropdown" style="display: none;">
                        <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false">Volunteer <span class="caret"></span></a>
                        <ul class="dropdown-menu" role="menu">
                            <li><a href="<%=urlLoc %>/submit.jsp"><%=props.getProperty("report")%></a></li>
							              <li class="dropdown"><a href="<%=urlLoc %>/surveys/createSurvey.jsp"><%=props.getProperty("createSurvey")%></a></li>
                        </ul>
                      </li>

                      <!-- end submit -->


                      <li class="dropdown" style="display: none;">
                        <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false"><%=props.getProperty("learn")%> <span class="caret"></span></a>
                        <ul class="dropdown-menu" role="menu">
                        	<li><a href="<%=urlLoc %>/photographing.jsp"><%=props.getProperty("howToPhotograph")%></a></li>

                               <li><a href="<%=urlLoc %>/publications.jsp">Publications</a></li>

                             <li class="dropdown"><a href="<%=urlLoc %>/whoAreWe.jsp">Collaborators</a></li>


                          	<li><a target="_blank" href="http://www.wildme.org/wildbook"><%=props.getProperty("learnAboutShepherd")%></a></li>
                        	<li class="divider"></li>
                        </ul>
                      </li>

                      <li>
                        <a href="<%=urlLoc %>/publications.jsp">PUBLICATIONS</a>
                      </li>

                      <li>
                                              <a href="<%=urlLoc %>/donate.jsp">DONATE</a>
                                            </li>


                                            <li class="dropdown">
                                                                        <a href="#" style="color:#9dc327;" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false">VOLUNTEER <span class="caret"></span></a>
                                                                            <ul class="dropdown-menu" role="menu">
                                                                                <li><a href="<%=urlLoc %>/locations.jsp">CAT WALK (SURVEYS)</a></li>
                                                                                <li><a href="<%=urlLoc %>/locations.jsp">CAT AND MOUSE (ONLINE)</a></li>
                                                                                <li><a href="<%=urlLoc %>/locations.jsp">ONLINE TASK INSTRUCTIONS</a></li>
                                                                            </ul>
                                                                    </li>

                      <li class="dropdown">
                        <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false">Volunteer <span class="caret"></span></a>
                    <ul class="dropdown-menu" role="menu">
<!-- TODO make this only show to non-logged in -->
<% if (isAnonymous) { %>
                        <li><a href="<%=urlLoc %>/register.jsp">Cat and Mouse: Online Tasks</a></li>
                        <li><a href="<%=urlLoc %>/catwalk.jsp">Cat Walk: Cat Surveys</a></li>
                        <li><a href="<%=urlLoc %>/queue.jsp">Login</a></li>
<% } else { %>
                        <li><a href="<%=urlLoc %>/register.jsp?instructions">Instructions</a></li>
                        <li><a href="<%=urlLoc %>/queue.jsp">Process Submissions</a></li>
                        <li><a href="<%=urlLoc %>/logout.jsp" ><%=props.getProperty("logout") %></a></li>
<% } %>
                    </ul>
                      </li>


                      <li class="dropdown" style="display: none;">
                        <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false"><%=props.getProperty("individuals")%> <span class="caret"></span></a>
                        <ul class="dropdown-menu" role="menu">
                          <li><a href="<%=urlLoc %>/gallery.jsp"><%=props.getProperty("gallery")%></a></li>

                          <li><a href="<%=urlLoc %>/individualSearchResults.jsp"><%=props.getProperty("viewAll")%></a></li>
                        </ul>
                      </li>
                      <li class="dropdown" style="display: none;">
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
                          <li class="divider"></li>
                          <li><a href="<%=urlLoc %>/encounters/thumbnailSearchResults.jsp?noQuery=true"><%=props.getProperty("viewImages")%></a></li>
                          <li><a href="<%=urlLoc %>/xcalendar/calendar2.jsp"><%=props.getProperty("encounterCalendar")%></a></li>
                          <% if(request.getUserPrincipal()!=null) { %>
                            <li><a href="<%=urlLoc %>/encounters/searchResults.jsp?username=<%=request.getRemoteUser()%>"><%=props.getProperty("viewMySubmissions")%></a></li>
                          <% } %>
                        </ul>
                      </li>

                      <!-- moved search to admin access section --!>

<% if (request.isUserInRole("admin")) { %>
                      <li class="dropdown">
                        <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false"><%=props.getProperty("search")%> <span class="caret"></span></a>
                        <ul class="dropdown-menu" role="menu">
                              <li><a href="<%=urlLoc %>/encounters/encounterSearch.jsp"><%=props.getProperty("encounterSearch")%></a></li>
                              <li><a href="<%=urlLoc %>/individualSearch.jsp"><%=props.getProperty("individualSearch")%></a></li>
                              <li style="display: none;"><a href="<%=urlLoc %>/occurrenceSearch.jsp"><%=props.getProperty("occurrenceSearch")%></a></li>
                              <li><a href="<%=urlLoc %>/surveys/surveySearch.jsp"><%=props.getProperty("surveySearch")%></a></li>
                           </ul>
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

                            <% if(CommonConfiguration.useSpotPatternRecognition(context)) { %>
                                <li class="divider"></li>
                                  <li class="dropdown-header">SharkGrid</li>

                                <li><a href="<%=urlLoc %>/appadmin/scanTaskAdmin.jsp">Check SharkGrid</a></li>

                                 <li><a href="<%=urlLoc %>/software/software.jsp"><%=props.getProperty("gridSoftware")%></a></li>
                                <li class="divider"></li>
                                <% } %>


                            <% }
                            if(CommonConfiguration.allowBatchUpload(context) && (request.isUserInRole("admin"))) { %>
                              <li><a href="<%=urlLoc %>/BatchUpload/start"><%=props.getProperty("batchUpload")%></a></li>
                            <% }
                            if(request.isUserInRole("admin")) { %>


                            <li class="dropdown-header">Admins Only</li>
                              <li><a href="<%=urlLoc %>/appadmin/routeCreator.jsp">Route Creator</a></li>
                              <li><a href="<%=urlLoc %>/appadmin/admin.jsp"><%=props.getProperty("general")%></a></li>
                              <li><a href="<%=urlLoc %>/appadmin/logs.jsp"><%=props.getProperty("logs")%></a></li>

                                <li><a href="<%=urlLoc %>/appadmin/users.jsp?context=context0"><%=props.getProperty("userManagement")%></a></li>
								<li><a href="<%=urlLoc %>/appadmin/intelligentAgentReview.jsp?context=context0"><%=props.getProperty("intelligentAgentReview")%></a></li>

                                <%
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
                                <% }
                            } //end if admin %>
                        </ul>
                      </li>
                    </ul>
<% }   //if (!isAnonymous)  %>




                  </div>

                </div>
              </div>
            </nav>
        </header>

        <script>

var utickState = {
    pageId: wildbook.uuid(),
    pageTick: 0,
    inactiveTick: 0,
    active: true,
    keyActivity: false,
    mouseButtonActivity: false,
    mouseActivity: false
};
$(document).on('mousemove', function(ev) { utickState.mouseActivity = true; });
$(document).on('mousedown', function(ev) { utickState.mouseButtonActivity = true; });
$(document).on('keydown', function(ev) { utickState.keyActivity = true; });
$(document).ready(function() {
    console.info('initializing utickLoop');
    wildbook.utickLoop(null, function() {
        utickState.pageTick++;
        if (utickState.mouseActivity || utickState.mouseButtonActivity || utickState.mouseActivity) {
            utickState.active = true;
            utickState.inactiveTick = 0;
        } else {
            utickState.active = false;
            utickState.inactiveTick++;
        }
        utickState.uri = document.location.pathname + document.location.search;
        var data = JSON.parse(JSON.stringify(utickState));
        utickState.mouseActivity = false;
        utickState.mouseButtonActivity = false;
        utickState.keyActivity = false;
        delete(utickState.data);
        if (data.inactiveTick > 10) data._doNotSend = true;
        return data;
    });
});

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
                    window.location.replace("<%=("//" + CommonConfiguration.getURLLocation(request)+"/individuals.jsp?number=") %>" + ui.item.value);
                }
                else if (ui.item.type == "locationID") {
                	window.location.replace("<%=("//" + CommonConfiguration.getURLLocation(request)+"/encounters/searchResultsAnalysis.jsp?locationCodeField=") %>" + ui.item.value);
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
                    url: '<%=("//" + CommonConfiguration.getURLLocation(request)) %>/SiteSearch',
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

        <!-- ****/header**** -->
