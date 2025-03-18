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
<!DOCTYPE html>
<html>
<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.ShepherdProperties,
             org.ecocean.servlet.ServletUtilities,
             org.ecocean.CommonConfiguration,
             org.ecocean.Shepherd,
             org.ecocean.Util,
             org.ecocean.Organization,
             org.ecocean.User,
             java.util.ArrayList,
             java.util.List,
             java.util.Properties,
             org.apache.commons.text.WordUtils,
             org.ecocean.security.Collaboration,
             org.ecocean.ContextConfiguration,
             org.slf4j.Logger,org.slf4j.LoggerFactory
              "
%>

<%
    if ("logout".equals(request.getParameter("action"))) {
        System.out.println("Logging out");
        response.setHeader("Cache-Control", "no-cache"); 
        response.setHeader("Cache-Control", "no-store"); 
        response.setDateHeader("Expires", 0); 
        response.setHeader("Pragma", "no-cache"); 

        Logger log = LoggerFactory.getLogger(getClass());

        if (request.getRemoteUser() != null) {
            log.info(request.getRemoteUser() + " logged out.");
        }
        session.invalidate();

        response.sendRedirect(request.getContextPath() + "/login.jsp");
        return;
    }
%>

<%
String context="context0";
context=ServletUtilities.getContext(request);
String langCode=ServletUtilities.getLanguageCode(request);
Properties props = new Properties();
props = ShepherdProperties.getProperties("header.properties", langCode, context);
String urlLoc = "//" + CommonConfiguration.getURLLocation(request);
String gtmKey = CommonConfiguration.getGoogleTagManagerKey(context);
String gaId = CommonConfiguration.getGoogleAnalyticsId(context);
int sessionWarningTime = CommonConfiguration.getSessionWarningTime(context);
int sessionCountdownTime = CommonConfiguration.getSessionCountdownTime(context);


String pageTitle = (String)request.getAttribute("pageTitle");  //allows custom override from calling jsp (must set BEFORE include:header)
if (pageTitle == null) {
    pageTitle = CommonConfiguration.getHTMLTitle(context);
} else {
    pageTitle = CommonConfiguration.getHTMLTitle(context) + " | " + pageTitle;
}

String username = null;
User user = null;
String profilePhotoURL=urlLoc+"/images/empty_profile.jpg";
// we use this arg bc we can only log out *after* including the header on logout.jsp. this way we can still show the logged-out view in the header
boolean loggingOut = Util.requestHasVal(request, "loggedOut");

String notifications="";
//check if user is logged in and has pending notifications
if(request.getUserPrincipal()!=null){
  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("header.jsp");
  myShepherd.beginDBTransaction();
  try {
  
    notifications=Collaboration.getNotificationsWidgetHtml(request, myShepherd);
  
    if(request.getUserPrincipal()!=null && !loggingOut){
      user = myShepherd.getUser(request);
      username = (user!=null) ? user.getUsername() : null;
      if(user.getUserImage()!=null){
        profilePhotoURL="/"+CommonConfiguration.getDataDirectoryName(context)+"/users/"+user.getUsername()+"/"+user.getUserImage().getFilename();
      }
    }
  }
  catch(Exception e){
    System.out.println("Exception on indocetCheck in header.jsp:");
    e.printStackTrace();
    myShepherd.closeDBTransaction();
  }
  finally{
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
  }

}
%>


<html>
    <head>

      <!-- Global site tag (gtag.js) - Google Analytics -->
      <script async src="https://www.googletagmanager.com/gtag/js?id=<%=gaId %>"></script>

      <script>
        window.dataLayer = window.dataLayer || [];
        function gtag(){dataLayer.push(arguments);}
        gtag('js', new Date());

        gtag('config', '<%=gaId %>');
      </script>

      <script>
        function logoutAndRedirect() {
            window.location.href = '/header.jsp?action=logout';
        }
      </script>

      <!-- Google Tag Manager -->
      <script>(function (w, d, s, l, i) {
          w[l] = w[l] || []; w[l].push({
            'gtm.start':
              new Date().getTime(), event: 'gtm.js'
          }); var f = d.getElementsByTagName(s)[0],
            j = d.createElement(s), dl = l != 'dataLayer' ? '&l=' + l : ''; j.async = true; j.src =
              'https://www.googletagmanager.com/gtm.js?id=' + i + dl; f.parentNode.insertBefore(j, f);
        })(window, document, 'script', 'dataLayer', '<%=gtmKey %>');</script>
      <!-- End Google Tag Manager -->

      <title><%=pageTitle%></title>
      <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">
      <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
      <meta charset="UTF-8">
      <meta name="Description"
            content="<%=CommonConfiguration.getHTMLDescription(context) %>"/>
      <meta name="Keywords"
            content="<%=CommonConfiguration.getHTMLKeywords(context) %>"/>
      <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor(context) %>"/>
      <link rel="shortcut icon"
            href="<%=CommonConfiguration.getHTMLShortcutIcon(context) %>"/>
      <link href='//fonts.googleapis.com/css?family=Oswald:400,300,700' rel='stylesheet' type='text/css'/>
      <link rel="stylesheet" href="<%=urlLoc %>/cust/mantamatcher/css/manta.css" />
      
      <!-- Icon font necessary for indocet style, but for consistency will be applied everywhere -->
      <link rel="stylesheet" href="<%=urlLoc %>/fonts/elusive-icons-2.0.0/css/elusive-icons.min.css">
      <link rel="stylesheet" href="<%=urlLoc %>/fonts/elusive-icons-2.0.0/css/icon-style-overwrite.css">
      <link href="<%=urlLoc %>/tools/jquery-ui/css/jquery-ui.css" rel="stylesheet" type="text/css"/>
      <%
      if((CommonConfiguration.getProperty("allowSocialMediaLogin", context)!=null)&&(CommonConfiguration.getProperty("allowSocialMediaLogin", context).equals("true"))){
      %>
       <link href="<%=urlLoc %>/tools/hello/css/zocial.css" rel="stylesheet" type="text/css"/>
      <%
      }
      %>


      <!-- <link href="<%=urlLoc %>/tools/timePicker/jquery.ptTimeSelect.css" rel="stylesheet" type="text/css"/> -->
      <link rel="stylesheet" href="<%=urlLoc %>/tools/jquery-ui/css/themes/smoothness/jquery-ui.css" type="text/css" />


      <script src="<%=urlLoc %>/tools/jquery/js/jquery.min.js"></script>
      <script src="<%=urlLoc %>/tools/bootstrap/js/bootstrap.min.js"></script>
      <script type="text/javascript" src="<%=urlLoc %>/javascript/core.js"></script>
      <script type="text/javascript" src="<%=urlLoc %>/tools/jquery-ui/javascript/jquery-ui.min.js"></script>

      <link rel="stylesheet" href="<%=urlLoc %>/css/header.css" type="text/css"/>
      <link rel="stylesheet" href="<%=urlLoc %>/css/footer.css" type="text/css"/>

      <script type="text/javascript" src="<%=urlLoc %>/javascript/ia.js"></script>
      <script type="text/javascript" src="<%=urlLoc %>/javascript/ia.IBEIS.js"></script>  <!-- TODO plugin-ier -->

      <script type="text/javascript" src="<%=urlLoc %>/javascript/jquery.blockUI.js"></script>
      <script type="text/javascript" src="<%=urlLoc %>/javascript/jquery.cookie.js"></script>

      <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/bootstrap-icons/1.8.1/font/bootstrap-icons.min.css">

      <%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>


      <%
      if((CommonConfiguration.getProperty("allowSocialMediaLogin", context)!=null)&&(CommonConfiguration.getProperty("allowSocialMediaLogin", context).equals("true"))){
      %>
      <script type="text/javascript" src="<%=urlLoc %>/tools/hello/javascript/hello.all.js"></script>
      <%
      }
      %>


      <script type="text/javascript"  src="<%=urlLoc %>/JavascriptGlobals.js"></script>
      <script type="text/javascript"  src="<%=urlLoc %>/javascript/collaboration.js"></script>
      <script type="text/javascript"  src="<%=urlLoc %>/javascript/translator.js"></script>

      <script type="text/javascript" src="<%=urlLoc %>/javascript/notifications.js"></script>

      <script type="text/javascript"  src="<%=urlLoc %>/javascript/imageEnhancer.js"></script>
      <link type="text/css" href="<%=urlLoc %>/css/imageEnhancer.css" rel="stylesheet" />
    
      <script src="<%=urlLoc %>/javascript/lazysizes.min.js"></script>
      <script type="text/javascript">
        $(document).ready(function() {
          $('.navbar .dropdown').hover(
            function() {
              $(this).find('.dropdown-menu').first().stop(true, true).delay(250).show();
            },
            function() {
              $(this).find('.dropdown-menu').first().stop(true, true).delay(100).hide();
            }
          );
        });
      </script>

      <%
        if(user != null && !loggingOut){
      %>
        <script type="text/javascript">

        $(document).ready(function()

        {  
            var warningTime = <%= sessionWarningTime %>; // Session warning time in minutes.
            var activityTimeout = warningTime * 60 * 1000; // Convert warning time to milliseconds.
            var activityCheckInterval = 1000; // Frequency to check for activity in milliseconds.
            var lastActivityTimestamp;
            var countdownInterval;

            // Function to show session warning modal.
            function showWarning() {
                var now = Date.now();
                lastActivityTimestamp = localStorage.getItem('lastActivity');
                var timeSinceLastActivity = now - lastActivityTimestamp;

                if (timeSinceLastActivity < activityTimeout) {
                    // User has been active recently, postpone the warning.
                    $('#sessionModal').modal('hide');
                    startSessionTimer();
                    return;
                }
                $('#sessionModal').modal('show');
                startCountdown();
            }


            function handleSessionButtonClick(element) {

                var action = $(element).data('action');

                if (action === 'login'){
                     window.open('<%=urlLoc %>/welcome.jsp', '_blank');
                }
                else {

                    $.ajax({
                        url: wildbookGlobals.baseUrl + '../ExtendSession',
                        type: 'GET',
                        success: function(data) {
                            console.log(data);
                            // Indicate that the session has been extended and hide the modal.
                            localStorage.setItem('sessionExtended', Date.now().toString());
                            $('#sessionModal').modal('hide');
                            clearInterval(countdownInterval); // Stop the countdown timer.
                            resetActivity(); // Restart the session timer for the next expiration warning.
                        },
                        error: function(x, y, z) {
                            console.warn('%o %o %o', x, y, z);
                        }
                    });

                }



            }

            // Starts a timer to show the session expiration warning modal at the configured warning time.
            function startSessionTimer() {
                $('#extendSessionBtn').text('<%=props.getProperty("extendButton") %>');
                $('#extendSessionBtn').data('action', 'extendSession'); // Change the data-action attribute
                $('#modal-text').text('<%=props.getProperty("sessionModalContent") %>');
                $('#sessionModal').modal('hide');
                clearTimeout(countdownInterval); // Clear any existing timer.
                countdownInterval = setTimeout(showWarning, activityTimeout); // Set new timer for warning.
            }

            // Function to update activity timestamp.
            function resetActivity() {
                lastActivityTimestamp = Date.now();
                localStorage.setItem('lastActivity', lastActivityTimestamp.toString());
                startSessionTimer(); // Reset the session timer.
            }

            // Starts a countdown timer based on the session's warning time.
            function startCountdown() {

                var warningCountdownTime = <%= sessionCountdownTime %>; // Session countdown time in minutes.
                var countdownTimeout = warningCountdownTime * 60 * 1000;

                countdownInterval = setInterval(function()
                {

                    var now = Date.now();
                    lastActivityTimestamp = parseInt(localStorage.getItem('lastActivity'));
                    var countDownTime = (lastActivityTimestamp + activityTimeout + countdownTimeout) - now
                    var countDownTimeSeconds = Math.floor(countDownTime / 1000);
                    updateCountdownDisplay(countDownTimeSeconds);

                    if (countDownTime < 0 ) {
                        clearInterval(countdownInterval);
                        $('#extendSessionBtn').text("<%=props.getProperty("sessionLoginButton") %>");
                        $('#extendSessionBtn').data('action', 'login'); // Change the data-action attribute to 'login'
                        $('#modal-text').text("<%=props.getProperty("sessionLoginModalContent") %>");
                        $('.modal-body #countdown').text("");
                    }
                }, 1000);
            }

            function updateCountdownDisplay(seconds) {
                var minutes = Math.floor(seconds / 60);
                var secondsLeft = seconds % 60;
                $('.modal-body #countdown').text(minutes + ':' + (secondsLeft < 10 ? '0' : '') + secondsLeft); // Format and display the countdown.
            }

            $("#extendSessionBtn").click(function() {
                handleSessionButtonClick(this);
            });

            // Storage event listener to handle updates across tabs.
            window.addEventListener('storage', function(e) {
                if (e.key === 'lastActivity') {
                    lastActivityTimestamp = parseInt(e.newValue);
                    startSessionTimer(); // Adjust the session timer based on new activity.
                } else if (e.key === 'sessionExtended') {
                    resetActivity();
                }
            });

            resetActivity();
        });



      </script>
      <%
        }
      %>



      <!-- Start Open Graph Tags -->
        <meta property="og:url" content="<%=request.getRequestURI() %>?<%=request.getQueryString() %>" />
        <meta property="og:site_name" content="<%=CommonConfiguration.getHTMLTitle(context) %>"/>
      <!-- End Open Graph Tags -->


      <!-- Clockpicker on creatSurvey jsp -->
      <script type="text/javascript" src="<%=urlLoc %>/tools/clockpicker/jquery-clockpicker.min.js"></script>
      <link type="text/css" href="<%=urlLoc %>/tools/clockpicker/jquery-clockpicker.min.css" rel="stylesheet" />

      <style>
        ul.nav.navbar-nav {
          width: 100%;
        }
      </style>

    </head>

    <body role="document">

    <div class="modal fade" id="sessionModal" tabindex="-1" role="dialog" aria-labelledby="sessionModalLabel" aria-hidden="true">
      <div class="modal-dialog" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title" id="sessionModalLabel"><%=props.getProperty("sessionHeaderWarning") %></h5>
          </div>
          <div class="modal-body">
            <div id="modal-text" style="display: inline-block;"><%=props.getProperty("sessionModalContent") %></div>
            <div id="countdown" style="display: inline-block;"></div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" id="extendSessionBtn" data-action="extendSession"><%=props.getProperty("extendButton") %></button>
            <button type="button" class="btn btn-secondary" data-dismiss="modal"><%=props.getProperty("closeButton") %></button>
          </div>
        </div>
        </div>
    </div>


      <!-- Google Tag Manager (noscript) -->
      <noscript><iframe src="https://www.googletagmanager.com/ns.html?id=<%=gtmKey %>" height="0" width="0"
          style="display:none;visibility:hidden"></iframe></noscript>
      <!-- End Google Tag Manager (noscript) -->

        <!-- ****header**** -->
        <header class="page-header clearfix header-font" style="padding-top: 0px;padding-bottom:0px; ">
          <nav class="navbar navbar-default navbar-fixed-top" style="background-color: #303336; ">
            <div class="nav-bar-wrapper" style="background-color: transparent">
              <div class="container " style="height: 100%; display: flex; flex-direction: row; align-items: center; justify-content: space-between">
                <a class="nav-brand" target="_blank" href="<%=urlLoc %>">                
                  <img src="<%=urlLoc %>/cust/mantamatcher/img/acw_puppy_logo_stacked_left.svg" alt="Logo" style="height:45px;">                
                </a>
                <a class="site-name" target="_blank" href="<%=urlLoc %>">
                    <%= props.getProperty("siteName") != null ? props.getProperty("siteName") : "Wildbook" %>
                </a>                        
              
                <div class="navbar-header clearfix">
                  <button type="button" class="navbar-toggle collapsed" data-toggle="collapse" data-target="#navbar" aria-expanded="false" aria-controls="navbar">
                    <span class="sr-only">Toggle navigation</span>
                    <span class="icon-bar"></span>
                    <span class="icon-bar"></span>
                    <span class="icon-bar"></span>
                  </button>

                  <div id="navbar" class="navbar-collapse collapse">                
                    <ul class="nav navbar-nav">

                      <%-- <li><!-- the &nbsp on either side of the icon aligns it with the text in the other navbar items, because by default them being different fonts makes that hard. Added two for horizontal symmetry -->
                      </li> --%>

                      <li class="dropdown">
                        <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false"><%=props.getProperty("submit")%> <span class="svg-placeholder"></span></a>
                        <ul class="dropdown-menu" role="menu">

                            <li><a href="<%=urlLoc %>/submit.jsp" ><%=props.getProperty("report")%></a></li>

                            <!--
                              <li class="dropdown"><a href="<%=urlLoc %>/surveys/createSurvey.jsp"><%=props.getProperty("createSurvey")%></a></li>
                            -->

                            <li class="dropdown"><a href="<%=urlLoc %>/import/instructions.jsp"><%=props.getProperty("bulkImport")%></a></li>
                        </ul>
                      </li>
                      <li class="dropdown">
                        <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false"><%=props.getProperty("learn")%> <span class="caret"></span></a>
                        <ul class="dropdown-menu solid-menu-background" role="menu">

                        	<li class="dropdown"><a href="<%=urlLoc %>/overview.jsp"><%=props.getProperty("aboutYourProject")%></a></li>

                            <!-- <li><a href="<%=urlLoc %>/citing.jsp"><%=props.getProperty("citing")%></a></li> -->

                            <li><a href="<%=urlLoc %>/aboutUs.jsp"><%=props.getProperty("aboutUs")%></a></li>
                          	<li><a href="<%=urlLoc %>/photographing.jsp"><%=props.getProperty("howToPhotograph")%></a></li>
                            <li><a href="<%=urlLoc %>/contactus.jsp"><%=props.getProperty("contactUs")%></a></li>
                            <li class="divider"></li>
                            <li><a target="_blank" href="<%=urlLoc %>/learnMore.jsp"><%=props.getProperty("learnAboutShepherd")%></a></li>
                            <li><a target="_blank" href="<%=urlLoc %>/privacyPolicy.jsp"><%=props.getProperty("privacyPolicy")%></a></li>
                            <li><a target="_blank" href="<%=urlLoc %>/termsOfUse.jsp"><%=props.getProperty("termsOfUse")%></a></li>
                        	
                        </ul>
                      </li>
                      <li class="dropdown">
                        <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false"><%=props.getProperty("myData")%> <span class="svg-placeholder"></span></a>
                        <ul class="dropdown-menu" role="menu">
                          <li class="dropdown-submenu">
                            <a class="d-flex align-items-center justify-space-between" tabindex="-1" href="<%=urlLoc %>/encounters/searchResults.jsp?username=<%=request.getRemoteUser()%>"><%=props.getProperty("myEncounters")%>
                              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-chevron-right" viewBox="0 0 16 16">
                              <path fill-rule="evenodd" d="M4.646 1.646a.5.5 0 0 1 .708 0l6 6a.5.5 0 0 1 0 .708l-6 6a.5.5 0 0 1-.708-.708L10.293 8 4.646 2.354a.5.5 0 0 1 0-.708"/>
                            </svg>
                            </a>
                            
                            <ul class="dropdown-menu">
                                <li><a href="<%=urlLoc %>/encounters/searchResults.jsp?username=<%=request.getRemoteUser()%>&state=approved"><%=props.getProperty("myApprovedAnimals")%></a></li>
                                <li><a href="<%=urlLoc %>/encounters/searchResults.jsp?username=<%=request.getRemoteUser()%>&state=unapproved"><%=props.getProperty("myUnapprovedAnimals")%></a></li>
                                <li><a href="<%=urlLoc %>/encounters/searchResults.jsp?username=<%=request.getRemoteUser()%>&state=unidentifiable"><%=props.getProperty("myUnidentifiableAnimals")%></a></li> 
                          
                            </ul>
                            </li>
                          
                          <li><a href="<%=urlLoc %>/individualSearchResults.jsp?username=<%=request.getRemoteUser()%>"><%=props.getProperty("myIndividuals")%></a></li>
                          <li><a href="<%=urlLoc %>/occurrenceSearchResults.jsp?submitterID=<%=request.getRemoteUser()%>"><%=props.getProperty("mySightings")%></a></li>
                          <li><a href="<%=urlLoc %>/imports.jsp"><%=props.getProperty("myBulkImports")%></a></li>
                          <li><a href="<%=urlLoc %>/projects/projectList.jsp"><%=props.getProperty("myProjects")%></a></li>

                        </ul>
                      </li>
                      <li class="dropdown">
                        <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false"><%=props.getProperty("search")%><span class="svg-placeholder"></span> </a>
                        <ul class="dropdown-menu" role="menu">
                          <li><a href="<%=urlLoc %>/encounters/encounterSearch.jsp"><%=props.getProperty("encounters")%></a></li>

                          <li><a href="<%=urlLoc %>/individualSearch.jsp"><%=props.getProperty("individuals")%></a></li>
                          <li><a href="<%=urlLoc %>/occurrenceSearch.jsp"><%=props.getProperty("sightings")%></a></li>

                        </ul>
                      </li>
                      <li class="dropdown">
                        <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false"><%=props.getProperty("animals")%> <span class="svg-placeholder"></span></a>
                        <ul class="dropdown-menu" role="menu">

                          <li><a href="<%=urlLoc %>/gallery.jsp"><%=props.getProperty("individualGallery")%></a></li>
                          <li><a href="<%=urlLoc %>/xcalendar/calendar.jsp"><%=props.getProperty("encounterCalendar")%></a></li>
                        </ul>
                      </li>
                      <li class="dropdown">
                        <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false"><%=props.getProperty("administer")%><span class="svg-placeholder"></span> </a>
                        <ul class="dropdown-menu" role="menu">

                          <li><a href="<%=urlLoc %>/myUsers.jsp"><%=props.getProperty("manageMyAccounts")%></a></li>
                          <li><a href="<%=urlLoc %>/appadmin/users.jsp?context=context0"><%=props.getProperty("userManagement")%></a></li>
                          <li><a href="<%=urlLoc %>/appadmin/admin.jsp"><%=props.getProperty("libraryAdministration")%></a></li>
                          <li><a href="<%=urlLoc %>/appadmin/logs.jsp"><%=props.getProperty("logs")%></a></li>
                          <li><a href="<%=urlLoc %>/appadmin/kwAdmin.jsp"><%=props.getProperty("photoKeywords")%></a></li>
                          <li><a href="<%=urlLoc %>/product-docs/en/wildbook/introduction/"><%=props.getProperty("softwareDocumentation")%></a></li>
                          <li><a href="<%=urlLoc %>/appadmin/dataIntegrity.jsp"><%=props.getProperty("dataIntegrity")%></a></li>
                          <li class="divider"></li>
                          <li><a href="<%=urlLoc %>/import/instructions.jsp"><%=props.getProperty("bulkImport")%></a></li>
                          <li><a href="<%=urlLoc %>/imports.jsp"><%=props.getProperty("standardImportListing")%></a></li>
                            <%
                            if(CommonConfiguration.useSpotPatternRecognition(context)){
                            %>
                              <li class="divider"></li>
                              <li class="dropdown-header"><%=props.getProperty("grid")%></li>
                              <li><a href="<%=urlLoc %>/appadmin/scanTaskAdmin.jsp?context=context0"><%=props.getProperty("gridAdministration")%></a></li>
                              <li><a href="<%=urlLoc %>/software/software.jsp"><%=props.getProperty("gridSoftware")%></a></li>
                            <%
                            }
                            %>
                        </ul>

                      </li>
                      <div class="search-and-secondary-wrapper d-flex" >
                        <!-- notifications -->
                        <div id="notifications">
                          <% if(user != null && !loggingOut)
                            {  
                          %>
                            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-bell" viewBox="0 0 16 16">
                              <path d="M8 16a2 2 0 0 0 2-2H6a2 2 0 0 0 2 2M8 1.918l-.797.161A4 4 0 0 0 4 6c0 .628-.134 2.197-.459 3.742-.16.767-.376 1.566-.663 2.258h10.244c-.287-.692-.502-1.49-.663-2.258C12.134 8.197 12 6.628 12 6a4 4 0 0 0-3.203-3.92zM14.22 12c.223.447.481.801.78 1H1c.299-.199.557-.553.78-1C2.68 10.2 3 6.88 3 6c0-2.42 1.72-4.44 4.005-4.901a1 1 0 1 1 1.99 0A5 5 0 0 1 13 6c0 .88.32 4.2 1.22 6"/>
                            </svg><%=notifications %>
                          <%
                            } 
                          %>
                        </div>
                        <!-- end of notifications -->
                        
                        <!-- language -->
                        <%

                          List<String> supportedLanguages=CommonConfiguration.getIndexedPropertyValues("language", context);
                          int numSupportedLanguages=supportedLanguages.size();

                          if(numSupportedLanguages>1){
                        %>                
                        <div class="custom-select-wrapper">
                          <div class="custom-select" onclick="toggleDropdown()">
                            <%                           
                              String selectedLangCode = "en";
                              Cookie[] cookies = request.getCookies(); 

                              if (cookies != null) {
                                  for (Cookie cookie : cookies) {
                                      if ("wildbookLangCode".equals(cookie.getName())) {
                                          selectedLangCode = cookie.getValue(); 
                                          break;
                                      }
                                  }
                              }

                              String selectedImgURL = "//" + CommonConfiguration.getURLLocation(request) + "/images/globe.png";

                            %>
                            
                            <div class="custom-select-selected" >
                              <img class="globle-image" src="<%= selectedImgURL %>" alt="Flag">
                              <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" fill="currentColor" class="bi bi-chevron-down" viewBox="0 0 16 16">
                                <path fill-rule="evenodd" d="M1.646 4.646a.5.5 0 0 1 .708 0L8 10.293l5.646-5.647a.5.5 0 0 1 .708.708l-6 6a.5.5 0 0 1-.708 0l-6-6a.5.5 0 0 1 0-.708"/>
                              </svg>
                            </div>
                            <div class="custom-select-items hidden">
                                <% for(int h=0;h<numSupportedLanguages;h++){
                                    String myLang = supportedLanguages.get(h);
                                    String langName = CommonConfiguration.getProperty(myLang, context);
                                    String imgURL = "//" + CommonConfiguration.getURLLocation(request) + "/images/flag_" + myLang + ".gif";
                                %>
                                  <div onclick="selectItem(this, '<%= myLang %>', '<%= imgURL %>')">
                                    <%= WordUtils.capitalize(langName) %>
                                  </div>
                                <%
                                } 
                                %>
                            </div>
                          </div>
                        </div>
                        <%
                        }
                        %>
                        <!-- end of language -->

                        
                      </div>
                    </ul>
                  </div>
                  <!-- start profile wrapper -->
                        <%
                          if(user != null && !loggingOut){
                              try {
                                String fullname=request.getUserPrincipal().toString();
                                if (user.getFullName()!=null) fullname=user.getFullName();
                        %>
                                <div class="profile-wrapper">
                                  <div class="profile-icon" style="background-image: url('<%=profilePhotoURL %>');"></div>
                                  
                                  <ul class="dropdown-menu">
                                      <li><a href="<%=urlLoc %>/react/">Landing Page</a></li>
                                      <li><a href="<%=urlLoc %>/myAccount.jsp">User Profile</a></li>
                                      <li><a href="#" onclick="logoutAndRedirect()">Logout</a></li>
                                  </ul>   
                                </div>              

                                <%
                              }
                              catch(Exception e){e.printStackTrace();}
                          }
                          else{
                            %>
                              <a href="<%= request.getContextPath() %>/login.jsp" title="" style="white-space: nowrap"><%= props.getProperty("login") %></a>
                        <%
                          }

                        %>
                  <!-- end profile wrapper -->              
                </div> 
                                

              </div>              

                <script>
                  var mySvg = `
                  <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" fill="currentColor" class="bi bi-chevron-down" viewBox="0 0 16 16">
                    <path fill-rule="evenodd" d="M1.646 4.646a.5.5 0 0 1 .708 0L8 10.293l5.646-5.647a.5.5 0 0 1 .708.708l-6 6a.5.5 0 0 1-.708 0l-6-6a.5.5 0 0 1 0-.708"/>
                  </svg>
                  `;

                  document.addEventListener("DOMContentLoaded", function() {
                      var elements = document.getElementsByClassName('svg-placeholder');
                      for (var i = 0; i < elements.length; i++) {
                          elements[i].innerHTML = mySvg;
                      }
                  });
                </script>
            </div>               
          </nav>

          <script type="text/javascript">
                          
            function toggleDropdown() {
                var items = document.querySelector('.custom-select-items');
                items.classList.toggle('hidden');
            }

            function selectItem(element, langCode, imgUrl) {
                var selectedDiv = document.querySelector('.custom-select-selected');
                selectedDiv.setAttribute('data-lang', langCode); 
                document.querySelector('.custom-select-items').classList.add('hidden');

                console.log("Language changed to: " + langCode);

                $.cookie("wildbookLangCode", langCode, {
                    path: '/',
                    secure: false
                });

                location.reload(true);
            }
              
          </script>
        </header>

        <!-- ****/header**** -->

