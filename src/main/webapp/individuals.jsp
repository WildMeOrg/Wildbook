<%@ page contentType="text/html; charset=utf-8" language="java"
         import="com.drew.imaging.jpeg.JpegMetadataReader,com.drew.metadata.Metadata,com.drew.metadata.Tag,org.ecocean.mmutil.MediaUtilities,
javax.jdo.datastore.DataStoreCache, org.datanucleus.jdo.*,javax.jdo.Query,
org.datanucleus.api.rest.orgjson.JSONObject,
org.datanucleus.ExecutionContext,
		 org.joda.time.DateTime,org.ecocean.*,org.ecocean.social.*,org.ecocean.servlet.ServletUtilities,java.io.File, java.util.*, org.ecocean.genetics.*,org.ecocean.security.Collaboration, com.google.gson.Gson,
org.datanucleus.api.rest.RESTUtils, org.datanucleus.api.jdo.JDOPersistenceManager" %>

<%
String blocker = "";
String context="context0";
context=ServletUtilities.getContext(request);
  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

  //setup data dir
  String rootWebappPath = getServletContext().getRealPath("/");
  File webappsDir = new File(rootWebappPath).getParentFile();
  File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
  //if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
  File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
  //if(!encountersDir.exists()){encountersDir.mkdirs();}
  //File thisEncounterDir = new File(encountersDir, number);

//setup our Properties object to hold all properties
  Properties props = new Properties();
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);



  //load our variables for the submit page

 // props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/individuals.properties"));
  props = ShepherdProperties.getProperties("individuals.properties", langCode,context);

	Properties collabProps = new Properties();
 	collabProps=ShepherdProperties.getProperties("collaboration.properties", langCode, context);


  String markedIndividualTypeCaps = props.getProperty("markedIndividualTypeCaps");
  String nickname = props.getProperty("nickname");
  String nicknamer = props.getProperty("nicknamer");
  String alternateID = props.getProperty("alternateID");
  String sex = props.getProperty("sex");
  String setsex = props.getProperty("setsex");
  String numencounters = props.getProperty("numencounters");
  String encnumber = props.getProperty("number");
  String dataTypes = props.getProperty("dataTypes");
  String date = props.getProperty("date");
  String size = props.getProperty("size");
  String spots = props.getProperty("spots");
  String location = props.getProperty("location");
  String mapping = props.getProperty("mapping");
  String mappingnote = props.getProperty("mappingnote");
  String setAlternateID = props.getProperty("setAlternateID");
  String setNickname = props.getProperty("setNickname");
  String unknown = props.getProperty("unknown");
  String noGPS = props.getProperty("noGPS");
  String update = props.getProperty("update");
  String additionalDataFiles = props.getProperty("additionalDataFiles");
  String delete = props.getProperty("delete");
  String none = props.getProperty("none");
  String addDataFile = props.getProperty("addDataFile");
  String sendFile = props.getProperty("sendFile");
  String researcherComments = props.getProperty("researcherComments");
  String edit = props.getProperty("edit");
  String matchingRecord = props.getProperty("matchingRecord");
  String tryAgain = props.getProperty("tryAgain");
  String addComments = props.getProperty("addComments");
  String record = props.getProperty("record");
  String getRecord = props.getProperty("getRecord");
  String allEncounters = props.getProperty("allEncounters");
  String allIndividuals = props.getProperty("allIndividuals");

  String name = request.getParameter("number").trim();
  Shepherd myShepherd = new Shepherd(context);


	List<Collaboration> collabs = Collaboration.collaborationsForCurrentUser(request);

%>
<%
if (request.getParameter("number")!=null) {
	myShepherd.beginDBTransaction();
	try{
		
		if(myShepherd.isMarkedIndividual(name)){
			MarkedIndividual indie=myShepherd.getMarkedIndividual(name);
			Vector myEncs=indie.getEncounters();
			int numEncs=myEncs.size();


			boolean visible = indie.canUserAccess(request);

			if (!visible) {
  			ArrayList<String> uids = indie.getAllAssignedUsers();
				ArrayList<String> possible = new ArrayList<String>();
				for (String u : uids) {
					Collaboration c = null;
					if (collabs != null) c = Collaboration.findCollaborationWithUser(u, collabs);
					if ((c == null) || (c.getState() == null)) {
						User user = myShepherd.getUser(u);
						String fullName = u;
						if (user.getFullName()!=null) fullName = user.getFullName();
						possible.add(u + ":" + fullName.replace(",", " ").replace(":", " ").replace("\"", " "));
					}
				}
				String cmsg = "<p>" + collabProps.getProperty("deniedMessage") + "</p>";
				cmsg = cmsg.replace("'", "\\'");

				if (possible.size() > 0) {
    			String arr = new Gson().toJson(possible);
					blocker = "<script>$(document).ready(function() { $.blockUI({ message: '" + cmsg + "' + _collaborateMultiHtml(" + arr + ") }) });</script>";
				} else {
					cmsg += "<p><input type=\"button\" onClick=\"window.history.back()\" value=\"BACK\" /></p>";
					blocker = "<script>$(document).ready(function() { $.blockUI({ message: '" + cmsg + "' }) });</script>";
				}
			}



		}
	}
	catch(Exception e){e.printStackTrace();}
	finally{
		myShepherd.rollbackDBTransaction();
	}
}
%>
<jsp:include page="header.jsp" flush="true"/>



<!--  FACEBOOK SHARE BUTTON -->
<div id="fb-root"></div>
<script type="text/javascript">(function(d, s, id) {
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

<script src="javascript/underscore-min.js"></script>
<script src="javascript/backbone-min.js"></script>
<script src="javascript/core.js"></script>
<script src="javascript/classes/Base.js"></script>

<link rel="stylesheet" href="javascript/tablesorter/themes/blue/style.css" type="text/css" media="print, projection, screen" />

<link rel="stylesheet" href="css/pageableTable.css" />
<script src="javascript/tsrt.js"></script>
<script src="javascript/sss.js"></script>
<link rel="stylesheet" href="css/sss.css" type="text/css" media="all">

<script>
  jQuery(function($) {
    $('.slider').sss({
      slideShow : false, // Set to false to prevent SSS from automatically animating.
      startOn : 0, // Slide to display first. Uses array notation (0 = first slide).
      transition : 400, // Length (in milliseconds) of the fade transition.
      speed : 3500, // Slideshow speed in milliseconds.
      showNav : true // Set to false to hide navigation arrows.
      });

      $(".slider").show();
    });
</script>

<style>
.ptcol-maxYearsBetweenResightings {
	width: 100px;
}
.ptcol-numberLocations {
	width: 100px;
}
</style>

<link rel="stylesheet" type="text/css" href="css/individualStyles.css">

<link href='http://fonts.googleapis.com/css?family=Source+Sans+Pro:200,600,200italic,600italic' rel='stylesheet' type='text/css'>
<script src="http://d3js.org/d3.v3.min.js"></script>
<script src="http://phuonghuynh.github.io/js/bower_components/cafej/src/extarray.js"></script>
<script src="http://phuonghuynh.github.io/js/bower_components/cafej/src/misc.js"></script>
<script src="http://phuonghuynh.github.io/js/bower_components/cafej/src/micro-observer.js"></script>
<script src="http://phuonghuynh.github.io/js/bower_components/microplugin/src/microplugin.js"></script>
<script src="javascript/bubbleDiagram/bubble-chart.js"></script>
<script src="javascript/bubbleDiagram/encounter-calls.js"></script>
<script src="javascript/relationshipDiagrams/familyTree.js"></script>

<script type="text/javascript">

  $(document).ready( function() {
  	// wildbook.init(function() { doTable(); });
    $("#familyDiagramTab").click(function (e) {
      e.preventDefault()
      $("#familyDiagram").show();
      $("#communityTable").hide();
      $("#familyDiagramTab").addClass("active");
      $("#communityTableTab").removeClass("active");
    });

    $("#communityTableTab").click(function (e) {
      e.preventDefault()
      $("#familyDiagram").hide();
      $("#communityTable").show();
      $("#familyDiagramTab").removeClass("active");
      $("#communityTableTab").addClass("active");
    });

    $("#cooccurrenceDiagramTab").click(function (e) {
      e.preventDefault()
      $("#cooccurrenceDiagram").show();
      $("#cooccurrenceTable").hide();
      $("#cooccurrenceDiagramTab").addClass("active");
      $("#cooccurrenceTableTab").removeClass("active");
    });

    $("#cooccurrenceTableTab").click(function (e) {
      e.preventDefault()
      $("#cooccurrenceTable").show();
      $("#cooccurrenceDiagram").hide();
      $("#cooccurrenceTableTab").addClass("active");
      $("#cooccurrenceDiagramTab").removeClass("active");
    });

    $("#bioSamplesTableTab").click(function (e) {
      e.preventDefault()
      $("#bioSamplesTable").show();
      $("#encountersTable").hide();
      $("#innerEncountersTable").hide();
      $("#bioSamplesTableTab").addClass("active");
      $("#encountersTableTab").removeClass("active");
      });

    $("#encountersTableTab").click(function (e) {
      e.preventDefault()
      $("#encountersTable").show();
      $("#innerEncountersTable").show();
      $("#bioSamplesTable").hide();
      $("#encountersTableTab").addClass("active");
      $("#bioSamplesTableTab").removeClass("active");
      });

      setTimeout(function() {
      $("td:contains('TissueSample')").html("<img class='encounterSample' src='images/microscope.gif'/>");
      $("td:contains('image')").html("<img class='encounterImg' src='images/Crystal_Clear_filesystem_folder_image.png'/>");
      $("td:contains('both')").html("<img class='encounterImg' src='images/Crystal_Clear_filesystem_folder_image.png'/>").append("<img class='encounterSample' src='images/microscope.gif'/>");

      $('#encountTable tr').click(function() {
        selectedWhale = ($(this).attr("class"));
        goToEncounterURL(selectedWhale);
      });

      $('#cooccurrenceTable tr').click(function() {
        selectedWhale = ($(this).attr("class"));
        goToWhaleURL(selectedWhale);
      });
      $("#encountTable td:nth-child(1)").attr("class", "hide");
      $("#encountTable th:nth-child(1)").attr("class", "hide");

    }, 6000);
  });

</script>


<%---------- Main Div ----------%>
<div class="container row maincontent maincontainer">
  <%=blocker%>
  <%
  myShepherd.beginDBTransaction();
  try {
    if (myShepherd.isMarkedIndividual(name)) { %>
  <%-- Header Row --%>
  <div class="row mainHeader" style="position:relative;">
    <div class="col-sm-6">

          <%
          MarkedIndividual sharky=myShepherd.getMarkedIndividual(name);

          boolean isOwner = ServletUtilities.isUserAuthorizedForIndividual(sharky, request);

          if (CommonConfiguration.allowNicknames(context)) {
            if ((sharky.getNickName() != null) && (!sharky.getNickName().trim().equals(""))) {
              String myNickname = "";
              myNickname = sharky.getNickName();
            %>

            <h1 id="markedIndividualHeader"><%=myNickname%><%if (isOwner && CommonConfiguration.isCatalogEditable(context)) {%><a id="nickname" style="color:blue;cursor: pointer;"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="images/Crystal_Clear_action_edit.png" /></a><%}%></h1>

            <%


          } else {
            %>
            <h1 id="markedIndividualHeader"><%=markedIndividualTypeCaps%> <%=sharky.getIndividualID()%></h1>
          <%
          }
        }
          %>
          <%-- <p class="caption"><em><%=props.getProperty("description") %></em></p> --%>

      <%-- Social Media Buttons --%>
      <div>
        <!-- Google PLUS-ONE button -->
        <g:plusone size="small" annotation="none"></g:plusone>
        <!--  Twitter TWEET THIS button -->
        <a href="https://twitter.com/share" class="twitter-share-button" data-count="none">Tweet</a>
        <script>!function(d,s,id){var js,fjs=d.getElementsByTagName(s)[0];if(!d.getElementById(id)){js=d.createElement(s);js.id=id;js.src="//platform.twitter.com/widgets.js";fjs.parentNode.insertBefore(js,fjs);}}(document,"script","twitter-wjs");</script>
        <!-- Facebook LIKE button -->
        <div class="fb-share-button" data-href="http://<%=CommonConfiguration.getURLLocation(request) %>/individuals.jsp?number=<%=request.getParameter("number") %>" data-type="button_count"></div>
        <%
        if(CommonConfiguration.isIntegratedWithWildMe(context)){
          %>
          <a href="http://fb.wildme.org/wildme/public/profile/<%=CommonConfiguration.getProperty("wildMeDataSourcePrefix", context) %><%=sharky.getIndividualID()%>" target="_blank"><img src="images/wild-me-link.png" /></a>
          <%
        }
        %>
        <%-- End of Social Media   --%>
      </div>
      <br>
      <%-- Descriptions --%>
      <div class="row">
        <div class="col-sm-6">
            <p><%=markedIndividualTypeCaps%> <%=sharky.getIndividualID()%></p>
            <%

            if (CommonConfiguration.allowNicknames(context)) {
              String myNickname = "";
              if (sharky.getNickName() != null) {
                myNickname = sharky.getNickName();
              }
              String myNicknamer = "";
              if (sharky.getNickNamer() != null) {
                myNicknamer = sharky.getNickNamer();
              }
              %>

              <p><%=nickname %>: <%=myNickname%><%if (isOwner && CommonConfiguration.isCatalogEditable(context)) {%><a id="nickname" style="color:blue;cursor: pointer;"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="images/Crystal_Clear_action_edit.png" /></a><%}%></p>

              <p><%=nicknamer %>: <%=myNicknamer%></p>
              <%
            }
            %>
            <!-- Now prep the nickname popup dialog -->
            <div id="dialogNickname" title="<%=setNickname %>" style="display:none">
              <table border="1" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">

                <tr>
                  <td align="left" valign="top">
                    <form name="nameShark" method="post" action="IndividualSetNickName">
                    <input name="individual" type="hidden"
                    value="<%=request.getParameter("number")%>"> <%=nickname %>:
                    <input name="nickname" type="text" id="nickname" size="15"
                    maxlength="50"><br> <%=nicknamer %>: <input name="namer" type="text" id="namer" size="15" maxlength="50"><br> <input
                    name="Name" type="submit" id="Name" value="<%=update %>"></form>
                  </td>
                </tr>
              </table>
            </div>
            <!-- nickname popup dialog script -->
            <script>
              var dlgNick = $("#dialogNickname").dialog({
                autoOpen: false,
                draggable: false,
                resizable: false,
                width: 500
              });

              $("a#nickname").click(function() {
                dlgNick.dialog("open");
              });
            </script>
            <%
            String sexValue="";
            if(sharky.getSex()!=null){sexValue=sharky.getSex();}
            %>
            <p><%=sex %>: <%=sexValue %> <%if (isOwner && CommonConfiguration.isCatalogEditable(context)) {%><a id="sex" style="color:blue;cursor: pointer;"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="images/Crystal_Clear_action_edit.png" /></a><%}%><br />
            <%
              //edit sex
              if (CommonConfiguration.isCatalogEditable(context) && isOwner) {%>

              <!-- Now prep the sex popup dialog -->
              <div id="dialogSex" title="<%=setsex %>" style="display:none">
                <table border="1" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">

                  <tr>
                    <td align="left" valign="top">
                      <form name="setxsexshark" action="IndividualSetSex" method="post">

                        <select name="selectSex" size="1" id="selectSex">
                          <option value="unknown"><%=props.getProperty("unknown") %></option>
                          <option value="male"><%=props.getProperty("male") %></option>
                          <option value="female"><%=props.getProperty("female") %></option>
                        </select><br> <input name="individual" type="hidden" value="<%=name%>" id="individual" />
                        <input name="Add" type="submit" id="Add" value="<%=update %>" />
                      </form>
                    </td>
                  </tr>
                </table>
              </div>
            <!-- sex popup dialog script -->
            <script>
              var dlgSex = $("#dialogSex").dialog({
              autoOpen: false,
              draggable: false,
              resizable: false,
              width: 500
              });

              $("a#sex").click(function() {
              dlgSex.dialog("open");
              });
            </script>
            <%}%>
          </p>

          <%
            if(CommonConfiguration.showProperty("showTaxonomy",context)){

            String genusSpeciesFound=props.getProperty("notAvailable");
            if(sharky.getGenusSpecies()!=null){genusSpeciesFound=sharky.getGenusSpecies();}
            %>
            <p>
              <%=props.getProperty("taxonomy")%>: <em><%=genusSpeciesFound%></em>
            </p>
            <%
              }
              %>

        </div>

        <div class="col-sm-6">
          <!-- start birth date -->
          <a name="birthdate"></a>
          <%
            String timeOfBirth="";
            //System.out.println("Time of birth is: "+sharky.getTimeOfBirth());
            if(sharky.getTimeOfBirth()>0){
            String timeOfBirthFormat="yyyy-MM-d";
            if(props.getProperty("birthdateJodaFormat")!=null){
            timeOfBirthFormat=props.getProperty("birthdateJodaFormat");
            }
            timeOfBirth=(new DateTime(sharky.getTimeOfBirth())).toString(timeOfBirthFormat);
            }

            String displayTimeOfBirth=timeOfBirth;
            //if(displayTimeOfBirth.indexOf("-")!=-1){displayTimeOfBirth=displayTimeOfBirth.substring(0,displayTimeOfBirth.indexOf("-"));}

            %>
            <p><%=props.getProperty("birthdate")  %>:
            <%=displayTimeOfBirth%> <%if (isOwner && CommonConfiguration.isCatalogEditable(context)) {%><a style="color:blue;cursor: pointer;" id="birthdate"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="images/Crystal_Clear_action_edit.png" /></a><%}%>
            </p>

            <!-- Now prep the birth date popup dialog -->
            <div id="dialogBirthDate" title="<%=props.getProperty("setBirthDate") %>" style="display:none">
              <table border="1" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">

                <tr><td align="left" valign="top">
                  <strong>
                    <font color="#990000"> <%=props.getProperty("clickDate")%>
                  </font>
                </strong>
                <br /><%=props.getProperty("dateFormat")%>
                <br /> <font size="-1"><%=props.getProperty("leaveBlank")%></font>
              </td></tr>

              <tr>
                <td align="left" valign="top">
                  <form name="set_birthdate" method="post" action="IndividualSetYearOfBirth">

                    <input name="individual" type="hidden" value="<%=request.getParameter("number")%>" />
                    <%=props.getProperty("birthdate")  %>:
                    <input name="timeOfBirth" type="text" id="timeOfBirth" size="15" maxlength="150" value="<%=timeOfBirth %>" />

                    <br /> <input name="birthy" type="submit" id="birthy" value="<%=update %>"></form>
                  </td>
                </tr>
              </table>

            </div>
            <!-- birth date popup dialog script -->
            <script>
              var dlgBirthDate = $("#dialogBirthDate").dialog({
              autoOpen: false,
              draggable: false,
              resizable: false,
              width: 600
              });

              $("a#birthdate").click(function() {
              dlgBirthDate.dialog("open");
              });
            </script>
            </p>
            <!-- end birth date -->

          <!-- start death date -->
          <a name="deathdate"></a>
          <%
            String timeOfDeath="";
            if(sharky.getTimeofDeath()>0){
            String timeOfDeathFormat="yyyy-MM-d";
            if(props.getProperty("deathdateJodaFormat")!=null){
            timeOfDeathFormat=props.getProperty("deathdateJodaFormat");
            }
            timeOfDeath=(new DateTime(sharky.getTimeofDeath())).toString(timeOfDeathFormat);
            }
            String displayTimeOfDeath=timeOfDeath;
            //if(displayTimeOfDeath.indexOf("-")!=-1){displayTimeOfDeath=displayTimeOfDeath.substring(0,displayTimeOfDeath.indexOf("-"));}

            %>
            <p><%=props.getProperty("deathdate")  %>:
            <%=displayTimeOfDeath%> <%if (isOwner && CommonConfiguration.isCatalogEditable(context)) {%><a style="color:blue;cursor: pointer;" id="deathdate"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="images/Crystal_Clear_action_edit.png" /></a><%}%>
            </p>

            <!-- Now prep the death date popup dialog -->
            <div id="dialogDeathDate" title="<%=props.getProperty("setDeathDate") %>" style="display:none">
              <table border="1" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">

                <tr><td align="left" valign="top">
                  <strong>
                    <font color="#990000"> <%=props.getProperty("clickDate")%>
                  </font>
                </strong>
                <br /><%=props.getProperty("dateFormat")%>
                <br /> <font size="-1"><em><%=props.getProperty("leaveBlank")%></em></font>

              </td></tr>

              <tr>
                <td align="left" valign="top">
                  <form name="set_deathdate" method="post" action="IndividualSetYearOfDeath">
                    <input name="individual" type="hidden" value="<%=request.getParameter("number")%>" />
                    <%=props.getProperty("deathdate")  %>:
                    <input name="timeOfDeath" type="text" id="timeOfDeath" size="15" maxlength="150" value="<%=timeOfDeath %>" /><br /> <input name="deathy" type="submit" id="deathy" value="<%=update %>"></form>
                  </td>
                </tr>
              </table>

            </div>
            <!-- death date popup dialog script -->
            <script>
              var dlgDeathDate = $("#dialogDeathDate").dialog({
              autoOpen: false,
              draggable: false,
              resizable: false,
              width: 600
              });

              $("a#deathdate").click(function() {
              dlgDeathDate.dialog("open");
              });
            </script>
            </p>
          <!-- end death date -->

          <a name="alternateid"></a>
          <%
            String altID="";
            if(sharky.getAlternateID()!=null){
            altID=sharky.getAlternateID();
            }

            %>
            <p> <%=alternateID %>:
            <%=altID%> <%if (isOwner && CommonConfiguration.isCatalogEditable(context)) {%><a style="color:blue;cursor: pointer;" id="alternateID"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="images/Crystal_Clear_action_edit.png" /></a><%}%>
          </p>
          <!-- Now prep the alternateId popup dialog -->
          <div id="dialogAlternateID" title="<%=setAlternateID %>" style="display:none">
            <table border="1" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">

            <tr>
              <td align="left" valign="top">
                <form name="set_alternateid" method="post" action="IndividualSetAlternateID">
                  <input name="individual" type="hidden" value="<%=request.getParameter("number")%>" /> <%=alternateID %>:
                  <input name="alternateid" type="text" id="alternateid" size="15" maxlength="150" value="<%=altID %>" /><br /> <input name="Name" type="submit" id="Name" value="<%=update %>"></form>
                </td>
              </tr>
            </table>

          </div>
          <!-- alternateId popup dialog script -->
          <script>
            var dlg = $("#dialogAlternateID").dialog({
            autoOpen: false,
            draggable: false,
            resizable: false,
            width: 600
            });

            $("a#alternateID").click(function() {
            dlg.dialog("open");
            });
          </script>
        </div>

      </div>
      <%-- End Descriptions --%>
    </div>

    <div class="viewAllImgs" style="
        position: absolute;
        right: 15px;
        bottom: 0px;
        z-index: 10;
        color: white;
        text-shadow:
        -1px -1px 0 #000,
        1px -1px 0 #000,
        -1px 1px 0 #000,
        1px 1px 0 #000;
    ">
    <p class="viewAllImgs"><a style="color:white;" href="individualThumbnailSearchResults.jsp?individualID=<%=sharky.getIndividualID()%>"><%=props.getProperty("allImages")%>...</a></p></div>


    <div class="slider col-sm-6 center-slider">
      <%-- Get images for slider --%>
      <%
      ArrayList<JSONObject> photoObjectArray = sharky.getExemplarImages(request);
      String imgurlLoc = "http://" + CommonConfiguration.getURLLocation(request);

      for (int extraImgNo=0; (extraImgNo<photoObjectArray.size() && extraImgNo<5); extraImgNo++) {
        JSONObject newMaJson = new JSONObject();
        newMaJson = photoObjectArray.get(extraImgNo);
        String newimgUrl = newMaJson.optString("url", imgurlLoc+"/cust/mantamatcher/img/hero_manta.jpg");

        %>
        <div class="crop-outer">
          <div class="crop">
              <img src="cust/mantamatcher/img/individual_placeholder_image.jpg" class="sliderimg lazyload" data-src="<%=newimgUrl%>" alt="<%=sharky.getIndividualID()%>" />
          </div>
        </div>
        <%
      }
      %>
    </div>
  </div>
  <%-- End of Header Row --%>

  <%-- Body Row --%>
  <br><br>
  <div class="row">

    <%-- Main Column --%>
    <div class="col-md-12 mainColumn">

      <%-- TODO does this vv go here? --%>
      <%
      if (sharky.getDynamicProperties() != null) {
      //let's create a TreeMap of the properties
      StringTokenizer st = new StringTokenizer(sharky.getDynamicProperties(), ";");
      while (st.hasMoreTokens()) {
      String token = st.nextToken();
      int equalPlace = token.indexOf("=");
      String nm = token.substring(0, (equalPlace));
      String vl = token.substring(equalPlace + 1);
      %>
      <p class="para"><img align="absmiddle" src="images/lightning_dynamic_props.gif"> <strong><%=nm%>
      </strong><br/> <%=vl%>
      <%
      if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
      %>
      <font size="-1"><a
      href="http://<%=CommonConfiguration.getURLLocation(request) %>/individuals.jsp?number=<%=request.getParameter("number").trim()%>&edit=dynamicproperty&name=<%=nm%>#dynamicproperty"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="images/Crystal_Clear_action_edit.png" /></a></font>
      <%
      }
      %>
      </p>

      <%
      }
      }
      %>
      <%-- Relationship Graphs --%>
      <div>
        <a name="socialRelationships"></a>
        <p><strong><%=props.getProperty("social")%></strong></p>
        <%
        if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
        %>
        <p class="para">
        	<a id="addRelationship" class="launchPopup">
        		<img align="absmiddle" width="24px" style="border-style: none;" src="images/Crystal_Clear_action_edit_add.png"/>
        	</a>
        	<a id="addRelationship" class="launchPopup">
        		<%=props.getProperty("addRelationship") %>
        	</a>
        </p>
        <%
        }
        %>

        <!-- start relationship popup code -->
        <%
        if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
        %>
        <div id="dialogRelationship" title="<%=props.getProperty("setRelationship")%>" style="display:none; z-index: 99999 !important">

        <form id="setRelationship" action="RelationshipCreate" method="post">
        <table cellspacing="2" bordercolor="#FFFFFF">

        <%
        	String markedIndividual1Name="";
        String markedIndividual2Name="";
        String markedIndividual1Role="";
        String markedIndividual2Role="";
        String type="";
        String startTime="";
        String endTime="";
        String bidirectional="";
        String markedIndividual1DirectionalDescriptor="";
        String markedIndividual2DirectionalDescriptor="";
        String communityName="";

        //if(myShepherd.isRelationship(request.getParameter("type"), request.getParameter("markedIndividualName1"), request.getParameter("markedIndividualName2"), request.getParameter("markedIndividualRole1"), request.getParameter("markedIndividualRole2"),false)){

        	if(request.getParameter("persistenceID")!=null){

        	//Relationship myRel=myShepherd.getRelationship(request.getParameter("type"), request.getParameter("markedIndividualName1"), request.getParameter("markedIndividualName2"), request.getParameter("markedIndividualRole1"), request.getParameter("markedIndividualRole2"));

        	Object identity = myShepherd.getPM().newObjectIdInstance(org.ecocean.social.Relationship.class, request.getParameter("persistenceID"));

        	Relationship myRel=(Relationship)myShepherd.getPM().getObjectById(identity);

        	if(myRel.getMarkedIndividualName1()!=null){
        		markedIndividual1Name=myRel.getMarkedIndividualName1();
        	}
        	if(myRel.getMarkedIndividualName2()!=null){
        		markedIndividual2Name=myRel.getMarkedIndividualName2();
        	}
        	if(myRel.getMarkedIndividualRole1()!=null){
        		markedIndividual1Role=myRel.getMarkedIndividualRole1();
        	}
        	if(myRel.getMarkedIndividualRole2()!=null){
        		markedIndividual2Role=myRel.getMarkedIndividualRole2();
        	}
        	if(myRel.getType()!=null){
        		type=myRel.getType();
        	}
        	if(myRel.getMarkedIndividual1DirectionalDescriptor()!=null){
        		markedIndividual1DirectionalDescriptor=myRel.getMarkedIndividual1DirectionalDescriptor();
        	}
        	if(myRel.getMarkedIndividual2DirectionalDescriptor()!=null){
        		markedIndividual2DirectionalDescriptor=myRel.getMarkedIndividual2DirectionalDescriptor();
        	}

        	if(myRel.getStartTime()>-1){
        		startTime=(new DateTime(myRel.getStartTime())).toString();
        	}
        	if(myRel.getEndTime()>-1){
        		endTime=(new DateTime(myRel.getEndTime())).toString();
        	}

        	if(myRel.getBidirectional()!=null){
        		bidirectional=myRel.getBidirectional().toString();
        	}

        	if(myRel.getRelatedSocialUnitName()!=null){
        		communityName=myRel.getRelatedSocialUnitName();
        	}


        }
        %>

            <tr>
              	<td width="200px">
                  <strong><%=props.getProperty("type")%></strong><br />
                  <div style="font-size: smaller;">(<%=props.getProperty("required")%>)</div></td>
                <td>
                	<select name="type">
        			<%
        				List<String> types=CommonConfiguration.getIndexedPropertyValues("relationshipType",context);
        				int numTypes=types.size();
        				for(int g=0;g<numTypes;g++){

        					String selectedText="";
        					if(type.equals(types.get(g))){selectedText="selected=\"selected\"";}
        			%>
                  		<option <%=selectedText%>><%=types.get(g)%></option>
                  	<%
                  		}
                  	%>
                  	</select>


                </td>
             </tr>
             <tr>
             	<td>

                  <strong><%=props.getProperty("individualID1")%></strong><br />
                   <div style="font-size: smaller;">(<%=props.getProperty("required")%>)</div>
                   </td>
                  <td>

                     <%
                               	if((markedIndividual1Name.equals(""))&&(markedIndividual2Name.equals(""))){
                               %>
                       			<%=sharky.getIndividualID()%><input type="hidden" name="markedIndividualName1" value="<%=sharky.getIndividualID()%>"/>

                       		<%
                       			               			}
                       			               		            else if(!markedIndividual1Name.equals(sharky.getIndividualID())){
                       			               		%>
                		<input name="markedIndividualName1" type="text" size="20" maxlength="100" value="<%=markedIndividual1Name%>" />
               		<%
                			}
                		        	else{
                		%>
               			<%=markedIndividual1Name%><input type="hidden" name="markedIndividualName1" value="<%=sharky.getIndividualID()%>"/>
               		<%
               			}
               		%>
               </td>
           	</tr>
           	<tr>
             	<td>
                  <strong><%=props.getProperty("individualRole1")%></strong>
                  <br /> <div style="font-size: smaller;">(<%=props.getProperty("required")%>)</div>
                 </td>
                 <td>

                 <select name="markedIndividualRole1">
        			<%
        				List<String> roles=CommonConfiguration.getIndexedPropertyValues("relationshipRole",context);
        				int numRoles=roles.size();
        				for(int g=0;g<numRoles;g++){

        					String selectedText="";
        					if(markedIndividual1Role.equals(roles.get(g))){selectedText="selected=\"selected\"";}
        			%>
                  		<option <%=selectedText%>><%=roles.get(g)%></option>
                  	<%
                  		}
                  	%>
                  	</select>

                 </td>

                 <td>
                 	<%=props.getProperty("markedIndividual1DirectionalDescriptor")%>
                 </td>
                 <td>
                 	<input name="markedIndividual1DirectionalDescriptor" type="text" size="20" maxlength="100" value="<%=markedIndividual1DirectionalDescriptor%>" />
                 </td>

           	</tr>

            <tr>
             	<td><strong><%=props.getProperty("individualID2")%></strong></td>
                <td>
           			<%
           				if(!markedIndividual2Name.equals(sharky.getIndividualID())){
           			%>
                		<input name="markedIndividualName2" type="text" size="20" maxlength="100" value="<%=markedIndividual2Name%>" />
               		<%
                			}
                		        	else{
                		%>
               			<%=markedIndividual2Name%><input type="hidden" name="markedIndividualName2" value="<%=sharky.getIndividualID()%>"/>
               		<%
               			}
               		%>
               </td>
           	</tr>
           	<tr>
             	<td>

                  <strong><%=props.getProperty("individualRole2")%></strong>
                  <br /> <div style="font-size: smaller;">(<%=props.getProperty("required")%>)</div></td>
                  <td>
                  	<select name="markedIndividualRole2">
        			<%
        				for(int g=0;g<numRoles;g++){

        					String selectedText="";
        					if(markedIndividual2Role.equals(roles.get(g))){selectedText="selected=\"selected\"";}
        			%>
                  		<option <%=selectedText%>><%=roles.get(g)%></option>
                  	<%
                  		}
                  	%>
                  	</select></td>
               <td>
                 	<%=props.getProperty("markedIndividual2DirectionalDescriptor")%>
                 </td>
                 <td>
                 	<input name="markedIndividual2DirectionalDescriptor" type="text" size="20" maxlength="100" value="<%=markedIndividual2DirectionalDescriptor%>" />
                 </td>
           	</tr>

           <tr>
             	<td>

                  <strong><%=props.getProperty("relatedCommunityName")%></strong></td><td><input name="relatedCommunityName" type="text" size="20" maxlength="100" value="<%=communityName%>" />
               </td>
           	</tr>

           	   <tr>
             	<td>

                  <strong><%=props.getProperty("startTime")%></strong></td>
                  <td><input name="startTime" type="text" size="20" maxlength="100" value="<%=startTime%>" />
               </td>
               </tr>
               <tr>
               <td>

                 <strong><%=props.getProperty("endTime")%></strong></td>
                  <td><input name="endTime" type="text" size="20" maxlength="100" value="<%=endTime%>" />
               </td>

           	</tr>

           	<tr>
             	<td>

                  <strong><%=props.getProperty("bidirectional")%></strong>
               </td>
               <td>
                  	<select name="bidirectional">


                  		<option value=""></option>
                  		<%
                  			String selected="";
                  		          	if(bidirectional.equals("true")){
                  		          		selected="selected=\"selected\"";
                  		          	}
                  		%>
                  		<option value="true" <%=selected%>>true</option>
                  		<%
                  			selected="";
                  		          	if(bidirectional.equals("false")){
                  		          		selected="selected=\"selected\"";
                  		          	}
                  		%>
                  		<option value="false" <%=selected%>>false</option>
                  	</select>

               </td>
           	</tr>


            <tr><td colspan="2">
                    	<input name="EditRELATIONSHIP" type="submit" id="EditRELATIONSHIP" value="<%=props.getProperty("update") %>" />
           			</td>
           	</tr>


              </td>
            </tr>
          </table>

          <%
            	if(request.getParameter("persistenceID")!=null){
            %>
          	<input name="persistenceID" type="hidden" value="<%=request.getParameter("persistenceID")%>"/>
          <%
          	}
          %>

        </form>
        </div>
          <!-- popup dialog script -->
        <script>
          var dlgRel = $("#dialogRelationship").dialog({
            autoOpen: false,
            draggable: false,
            resizable: false,
            width: 600
          });

          $("a#addRelationship").click(function() {
            dlgRel.dialog("open");
            //$("#setRelationship").find("input[type=text], textarea").val("");


          });
        </script>
        <%
           	}

           //setup the javascript to handle displaying an edit tissue sample dialog box
           if( (request.getParameter("edit")!=null) && request.getParameter("edit").equals("relationship")){
           %>
        <script>
        dlgRel.dialog("open");
        </script>

        <%
          	}

          //end relationship code

          List<Relationship> relationships=myShepherd.getAllRelationshipsForMarkedIndividual(sharky.getIndividualID());

          if(relationships.size()>0){
          %>

        <div role="navigation">
          <ul class="nav nav-tabs">
            <li id="familyDiagramTab"  class="active">
              <a href="#familyDiagram">Familial Diagram</a>
            </li>
            <li id="communityTableTab">
              <a href="#communityTable"><%=props.getProperty("social")%> Table</a>
            </li>
          </ul>
        </div>

        <div id="familyDiagram">
          <% String individualID = sharky.getIndividualID();%>
          <script type="text/javascript">

          setupFamilyTree('<%=individualID%>');
          </script>
        </div>

        <div id="communityTable" class="mygrid-wrapper-div">
          <table width="100%" class="table table-striped table-bordered table-sm">
          <th><%=props.getProperty("roles")%></th><th><%=props.get("relationshipWith")%></th><th><%=props.getProperty("type")%></th><th><%=props.getProperty("community")%></th>
          <%
          	if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
          %>
          <th><%=props.getProperty("numSightingsTogether")%></th>
          <th><%=props.getProperty("edit")%></th><th><%=props.getProperty("remove")%></th>
          <%
          	}
          %>

          </tr>
          <%
          	int numRels=relationships.size();
          for(int f=0;f<numRels;f++){
          	Relationship myRel=relationships.get(f);
          	String indieName1=myRel.getMarkedIndividualName1();
          	String indieName2=myRel.getMarkedIndividualName2();
          	String otherIndyName=indieName2;
          	String thisIndyRole="";
          	String otherIndyRole="";
          	if(myRel.getMarkedIndividualRole1()!=null){thisIndyRole=myRel.getMarkedIndividualRole1();}
          	if(myRel.getMarkedIndividualRole2()!=null){otherIndyRole=myRel.getMarkedIndividualRole2();}
          	if(otherIndyName.equals(sharky.getIndividualID())){
          		otherIndyName=indieName1;
          		thisIndyRole=myRel.getMarkedIndividualRole2();
          		otherIndyRole=myRel.getMarkedIndividualRole1();
          	}
          	MarkedIndividual otherIndy=myShepherd.getMarkedIndividualQuiet(otherIndyName);
          	String type="";
          	if(myRel.getType()!=null){type=myRel.getType();}

          	String community="";
          	if(myRel.getRelatedSocialUnitName()!=null){community=myRel.getRelatedSocialUnitName();}
          %>
          	<tr>
          	<td><em><%=thisIndyRole %></em>-<%=otherIndyRole %></td>

<%
if (otherIndy == null) {  // this is bad
	System.out.println("ERROR: relationship match error - type '" + type + "' on " + sharky.getIndividualID() + " could not find " + otherIndyName);
%>

<td colspan="3" class="error">There was an error locating MarkedIndividual <b><%=otherIndyName%></b>.</td>

<% } else { %>
          	<td>
          	<a target="_blank" href="http://<%=CommonConfiguration.getURLLocation(request) %>/individuals.jsp?number=<%=otherIndy.getIndividualID()%>"><%=otherIndy.getIndividualID() %></a>
          		<%
          		if(otherIndy.getNickName()!=null){
          		%>
          		<br /><%=props.getProperty("nickname") %>: <%=otherIndy.getNickName()%>
          		<%
          		}
          		if(otherIndy.getAlternateID()!=null){
          		%>
          		<br /><%=props.getProperty("alternateID") %>: <%=otherIndy.getAlternateID()%>
          		<%
          		}
          		if(otherIndy.getSex()!=null){
          		%>
          			<br /><span class="caption"><%=props.getProperty("sex") %>: <%=otherIndy.getSex() %></span>
          		<%
          		}

          		if(otherIndy.getHaplotype()!=null){
          		%>
          			<br /><span class="caption"><%=props.getProperty("haplotype") %>: <%=otherIndy.getHaplotype() %></span>
          		<%
          		}
          		%>
          	</td>
          	<td><%=type %></td>
          	<td><a href="socialUnit.jsp?name=<%=community%>"><%=community %></a></td>

          	<%
          	if (isOwner && CommonConfiguration.isCatalogEditable(context)) {

          		String persistenceID=myShepherd.getPM().getObjectId(myRel).toString();

          		//int bracketLocation=persistenceID.indexOf("[");
          		//persistenceID=persistenceID.substring(0,bracketLocation);

          	%>
          	<td>
          	<%=myShepherd.getNumCooccurrencesBetweenTwoMarkedIndividual(otherIndy.getIndividualID(),sharky.getIndividualID()) %>

          	</td>



          	<td>
          		<a href="http://<%=CommonConfiguration.getURLLocation(request) %>/individuals.jsp?number=<%=request.getParameter("number") %>&edit=relationship&type=<%=myRel.getType()%>&markedIndividualName1=<%=myRel.getMarkedIndividualName1() %>&markedIndividualRole1=<%=myRel.getMarkedIndividualRole1() %>&markedIndividualName2=<%=myRel.getMarkedIndividualName2() %>&markedIndividualRole2=<%=myRel.getMarkedIndividualRole2()%>&persistenceID=<%=persistenceID%>"><img width="24px" style="border-style: none;" src="images/Crystal_Clear_action_edit.png" /></a>
          	</td>
          	<td>
          		<a onclick="return confirm('Are you sure you want to delete this relationship?');" href="RelationshipDelete?type=<%=myRel.getType()%>&markedIndividualName1=<%=myRel.getMarkedIndividualName1() %>&markedIndividualRole1=<%=myRel.getMarkedIndividualRole1() %>&markedIndividualName2=<%=myRel.getMarkedIndividualName2() %>&markedIndividualRole2=<%=myRel.getMarkedIndividualRole2()%>&persistenceID=<%=persistenceID%>"><img style="border-style: none;" src="images/cancel.gif" /></a>
          	</td>
          	<%
          	}

}  //end not-null-other
          	%>

          	</tr>
          <%


          }
          %>

          </table>
        </div>
        <br/>
        <%
        }
        else {
        %>
        	<p class="para"><%=props.getProperty("noSocial") %></p><br />
        <%
        }
        //

        %>


        <br>
        <%-- Cooccurrence table starts here --%>
        <a name="cooccurrence"></a>
        <p><strong><%=props.getProperty("cooccurrence")%></strong></p>


        <script type="text/javascript">
        // <% String individualID = sharky.getIndividualID();%>
        getData("<%=individualID%>");
        // getTableData(<%=individualID%>);
        </script>

        <%
        List<Map.Entry> otherIndies=myShepherd.getAllOtherIndividualsOccurringWithMarkedIndividual(sharky.getIndividualID());

        if(otherIndies.size()>0){

        //ok, let's iterate the social relationships
        %>
        <div class="cooccurrences">

          <div role="navigation">
            <ul class="nav nav-tabs">
              <li id="cooccurrenceDiagramTab" class="active">
                <a href="#cooccurrenceDiagram"><%=props.getProperty("cooccurrence")%> Diagram</a>
              </li>
              <li id="cooccurrenceTableTab">
                <a href="#cooccurrenceTable"><%=props.getProperty("cooccurrence")%> Table</a>
              </li>
            </ul>
          </div>

          <div id="cooccurrenceDiagram">
              <div class="bubbleChart">
                <div id="buttons" class="btn-group btn-group-sm" role="group">
                  <button type="button" class="btn btn-default" id="zoomIn"><span class="glyphicon glyphicon-plus"></span></button>
                  <button type="button" class="btn btn-default" id="zoomOut"><span class="glyphicon glyphicon-minus"></span></button>
                  <button type="button" class="btn btn-default" id="reset">Reset</button>
                </div>
              </div>
          </div>

          <div id="cooccurrenceTable" class="table-responsive mygrid-wrapper-div">
            <table id="coTable" class="table table-striped table-bordered table-sm table-hover">
                <thead id="coHead"></thead>
                <tbody id="coBody"></tbody>
            </table>
          </div>
        </div>

        <%
        }
        else {
        %>
        	<p class="para"><%=props.getProperty("noCooccurrences") %></p><br />
        <%
        }
        //



          if (isOwner) {
        %>
        <br />
        <p>
        <strong><img align="absmiddle" src="images/48px-Crystal_Clear_mimetype_binary.png" /> <%=additionalDataFiles %></strong>
        <%if ((sharky.getDataFiles()!=null)&&(sharky.getDataFiles().size() > 0)) {%>
        </p>
        <table>
          <%
            Vector addtlFiles = sharky.getDataFiles();
            for (int pdq = 0; pdq < addtlFiles.size(); pdq++) {
              String file_name = (String) addtlFiles.get(pdq);
          %>

          <tr>
            <td><a href="/<%=CommonConfiguration.getDataDirectoryName(context) %>/individuals/<%=sharky.getName()%>/<%=file_name%>"><%=file_name%>
            </a></td>
            <td>&nbsp;&nbsp;&nbsp;[<a
              href="IndividualRemoveDataFile?individual=<%=name%>&filename=<%=file_name%>"><%=delete %>
            </a>]
            </td>
          </tr>

          <%}%>
        </table>
        <%} else {%> <%=none %>
        </p>
        <%
          }
          if (CommonConfiguration.isCatalogEditable(context)) {
        %>
        <form action="IndividualAddFile" method="post"
              enctype="multipart/form-data" name="addDataFiles"><input
          name="action" type="hidden" value="fileadder" id="action"> <input
          name="individual" type="hidden" value="<%=sharky.getName()%>"
          id="individual">

          <p><%=addDataFile %>:</p>

          <p><input name="file2add" type="file" size="50"></p>

          <p><input name="addtlFile" type="submit" id="addtlFile"
                    value="<%=sendFile %>"></p></form>
        <%
          }




          }
        %>

        </td>
        </tr>


        </table>

        </td>
        </tr>
        </table>
      </div>
      <%-- End of Relationship Graphs --%>
      <br>
      <%-- Start Encounter Table --%>
      <p><strong><%=props.getProperty("tissueSamples") %> &amp; <%=numencounters %></strong></p>
      <div class="encountersBioSamples">
        <div role="navigation">
          <ul class="nav nav-tabs">
            <li id="encountersTableTab"  class="active">
              <a href="#encountersTable"><%=sharky.totalEncounters()%> <%=numencounters %></a>
            </li>
            <li id="bioSamplesTableTab">
              <a href="#bioSamplesTable"><%=props.getProperty("tissueSamples") %></a>
            </li>
          </ul>
        </div>

        <div id="encountersTable" class="mygrid-wrapper-div">

          <table id="encountTable" class="table table-bordered table-striped table-sm table-hover">
            <thead id="encountHead"></thead>
            <tbody id="encountBody"></tbody>
          </table>
        </div>
        <%-- End Encounter Table --%>

        <!-- Start genetics -->
        <div id="bioSamplesTable">
          <a name="tissueSamples"></a>
          <p>
            <%
            List<TissueSample> tissueSamples=myShepherd.getAllTissueSamplesForMarkedIndividual(sharky);

            int numTissueSamples=tissueSamples.size();
            if(numTissueSamples>0){
              %>
              <table width="100%" class="table table-striped table-bordered table-sm">
                <tr>
                  <th><%=props.getProperty("sampleID") %></th>
                  <th><%=props.getProperty("correspondingEncounterNumber") %></th>
                  <th><%=props.getProperty("values") %></th>
                  <th><%=props.getProperty("analyses") %></th></tr>
                  <%
                  for(int j=0;j<numTissueSamples;j++){
                    TissueSample thisSample=tissueSamples.get(j);
                    %>
                    <tr>
                      <td><span class="caption"><a href="encounters/encounter.jsp?number=<%=thisSample.getCorrespondingEncounterNumber() %>#tissueSamples"><%=thisSample.getSampleID()%></a></span></td>
                      <td><span class="caption"><a href="encounters/encounter.jsp?number=<%=thisSample.getCorrespondingEncounterNumber() %>#tissueSamples"><%=thisSample.getCorrespondingEncounterNumber()%></a></span></td>
                      <td><span class="caption"><%=thisSample.getHTMLString() %></span>
                    </td>

                    <td><table>
                      <%
                      int numAnalyses=thisSample.getNumAnalyses();
                      List<GeneticAnalysis> gAnalyses = thisSample.getGeneticAnalyses();
                      for(int g=0;g<numAnalyses;g++){
                        GeneticAnalysis ga = gAnalyses.get(g);
                        if(ga.getAnalysisType().equals("MitochondrialDNA")){
                          MitochondrialDNAAnalysis mito=(MitochondrialDNAAnalysis)ga;
                          %>
                          <tr><td style="border-style: none;"><strong><span class="caption"><%=props.getProperty("haplotype") %></strong></span></strong>: <span class="caption"><%=mito.getHaplotype() %></span></td></tr></li>
                          <%
                        }
                        else if(ga.getAnalysisType().equals("SexAnalysis")){
                          SexAnalysis mito=(SexAnalysis)ga;
                          %>
                          <tr><td style="border-style: none;"><strong><span class="caption"><%=props.getProperty("geneticSex") %></strong></span></strong>: <span class="caption"><%=mito.getSex() %></span></td></tr></li>
                          <%
                        }
                        else if(ga.getAnalysisType().equals("MicrosatelliteMarkers")){
                          MicrosatelliteMarkersAnalysis mito=(MicrosatelliteMarkersAnalysis)ga;

                          %>
                          <tr>
                            <td style="border-style: none;">
                              <p><span class="caption"><strong><%=props.getProperty("msMarkers") %></strong></span>&nbsp;
                              <%
                                if(request.getUserPrincipal()!=null){
                                %>
                                <a href="individualSearch.jsp?individualDistanceSearch=<%=sharky.getIndividualID()%>"><img height="20px" width="20px" align="absmiddle" alt="Individual-to-Individual Genetic Distance Search" src="images/Crystal_Clear_app_xmag.png"></img></a>
                                <%
                                  }
                                  %>
                                </p>
                                <span class="caption"><%=mito.getAllelesHTMLString() %></span>
                              </td>
                            </tr></li>

                            <%
                              }
                              else if(ga.getAnalysisType().equals("BiologicalMeasurement")){
                              BiologicalMeasurement mito=(BiologicalMeasurement)ga;
                              %>
                              <tr><td style="border-style: none;"><strong><span class="caption"><%=mito.getMeasurementType()%> <%=props.getProperty("measurement") %></span></strong><br /> <span class="caption"><%=mito.getValue().toString() %> <%=mito.getUnits() %> (<%=mito.getSamplingProtocol() %>)
                              <%
                                if(!mito.getSuperHTMLString().equals("")){
                                %>
                                <em>
                                  <br /><%=props.getProperty("analysisID")%>: <%=mito.getAnalysisID()%>
                                  <br /><%=mito.getSuperHTMLString()%>
                                </em>
                                <%
                                  }
                                  %>
                                </span></td></tr></li>
                                <%
                                  }
                                  }
                                  %>
                                </table>

                              </td>


                            </tr>
                            <%
                              }
                              %>
                            </table>
                          </p>
                          <%
                            }
                            else {
                            %>
                            <p class="para"><%=props.getProperty("noTissueSamples") %></p>
                            <%
                              }
                              %>
        </div>
        <!-- End genetics -->
      </div>
      <br></br>
      <%-- Start Adoption --%>
      <div>
        <%
          if (CommonConfiguration.allowAdoptions(context)) {
        %>

        <div id="rightcol" style="vertical-align: top;">
          <div id="menu" style="vertical-align: top;">


            <div class="module">
              <jsp:include page="individualAdoptionEmbed.jsp" flush="true">
                <jsp:param name="name" value="<%=name%>"/>
              </jsp:include>
            </div>


          </div><!-- end menu -->
        </div>

          <%
           }
        %>
      </div>
      <%-- End Adoption --%>

      <br>
      <%-- Start Collaborators --%>
      <div>

          <%
          if(CommonConfiguration.showUsersToPublic(context)){
            Shepherd userShepherd=new Shepherd("context0");
            userShepherd.beginDBTransaction();
            %>
              <p><strong><%=props.getProperty("collaboratingResearchers") %></strong></p>

            <%
            //myShepherd.beginDBTransaction();

            //loop through users to display photos
            ArrayList<User> relatedUsers =  userShepherd.getAllUsersForMarkedIndividual(sharky);
            int numUsers=relatedUsers.size();
            if(numUsers>0){
              %><div id="researchers"><%
                  for(int userNum=0;userNum<numUsers;userNum++){
                    User thisUser=relatedUsers.get(userNum);
                    String username=thisUser.getUsername();
                    String profilePhotoURL="images/empty_profile.jpg";
                    if(thisUser.getUserImage()!=null){
                      profilePhotoURL="/"+CommonConfiguration.getDataDirectoryName("context0")+"/users/"+thisUser.getUsername()+"/"+thisUser.getUserImage().getFilename();
                    }
                    %>
                    <div class="col-xl-3 col-sm-4 col-xs-6 researcherPhotoContainer thumbnail">
                      <%
                      String displayName="";
                      if(thisUser.getFullName()!=null) {
                        displayName=thisUser.getFullName();
                        %>
                        <div class="caption researcherName">
                          <p><%=displayName%></p>
                        </div>
                        <%
                      }
                      %>
                      <img src="<%=profilePhotoURL%>" class="researcherPhoto">

                      <div class="researcherInfo" id="info<%=userNum%>">
                        <%
                        if(thisUser.getAffiliation()!=null){
                          %>
                          <p><strong>Affiliation:</strong> <%=thisUser.getAffiliation() %></p>
                          <%
                        }
                        if(thisUser.getUserProject()!=null){
                          %>
                          <p><strong>Research Project:</strong> <%=thisUser.getUserProject() %></p>
                          <%
                            }
                            if(thisUser.getUserURL()!=null){
                            %>
                            <p><strong>Web site:</strong> <a style="font-weight:normal;color: blue" class="ecocean" href="<%=thisUser.getUserURL()%>"><%=thisUser.getUserURL() %></a><p>
                            <%
                              }
                              if(thisUser.getUserStatement()!=null){
                              %>
                              <p><em>"<%=thisUser.getUserStatement() %>"</em></p>
                              <%
                                }
                                %>
                      </div>
                    </div>
                          <%
                            } //end for loop of users

                            } //end if loop if there are any users
                            else{
                            %>

                            <p><%=props.getProperty("noCollaboratingResearchers") %></p>
                            <%
                              }

                              %>
                              <%
                                userShepherd.rollbackDBTransaction();
                                userShepherd.closeDBTransaction();
                                } //end if showUsersToGeneralPublic

                                //myShepherd.beginDBTransaction();

                            %>
                </div>

      <%-- End Collaborators --%>
      </div>

      <%-- Map --%>
      <br>
      <div>
        <jsp:include page="individualMapEmbed.jsp" flush="true">
          <jsp:param name="name" value="<%=name%>"/>
        </jsp:include>
      </div>
      <%-- End of Map --%>

      <br>
      <%-- Comments --%>
      <div class="col-sm-6">
        <%
        if(isOwner){
          %>
          <p><img align="absmiddle" src="images/Crystal_Clear_app_kaddressbook.gif"> <strong><%=researcherComments %></strong>: </p>

          <div style="text-align:left;border:1px solid lightgray;width:100%;height:250px;overflow-y:scroll;overflow-x:scroll;border-radius:5px;">
            <p><%=sharky.getComments().replaceAll("\n", "<br>")%></p>
          </div>
          <%
          if (CommonConfiguration.isCatalogEditable(context) && isOwner) {
            %>
            <p>
              <form action="IndividualAddComment" method="post" name="addComments">
                <input name="user" type="hidden" value="<%=request.getRemoteUser()%>" id="user">
                <input name="individual" type="hidden" value="<%=sharky.getName()%>" id="individual">
                <input name="action" type="hidden" value="comments" id="action">

                  <p><textarea name="comments" cols="60" id="comments" class="form-control" rows="3" style="width: 100%"></textarea> <br />
                  <input name="Submit" type="submit" value="<%=addComments %>">
                </form>
              </p>
              <%
            } //if isEditable

          }
          %>

        </td>
          </tr>
        </table>
      </div>
      <%-- End Comments --%>

    </div>
  <%-- End of Body Row --%>
  </div>

  <script src="<%=imgurlLoc %>/javascript/imageCropper.js"></script>
  <%-- Crop the images. I am sure there is a better way to do this, but we set max height to 400px arbitrarily. Varying results between Firefox and Chrome. Doesn't work great responsively...yet --%>
  <script>

    var maxHeight = 400;
    $('div.crop-outer').css('max-height',maxHeight+'px');
    var cropDesktopPics = function(maxHeight) {
      $('.crop-outer .crop img').each(function() {
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

   $(document).ready(function(){
     cropDesktopPics(maxHeight);
   });
   $( window ).resize(function(){
     cropDesktopPics(maxHeight);
   });
   </script>




  <%
  }

  //could not find the specified individual!
  else {

  //let's check if the entered name is actually an alternate ID
  List<MarkedIndividual> al = myShepherd.getMarkedIndividualsByAlternateID(name);
  List<MarkedIndividual> al2 = myShepherd.getMarkedIndividualsByNickname(name);
  List<Encounter> al3 = myShepherd.getEncountersByAlternateID(name);

  if (myShepherd.isEncounter(name)) {
    %>
    <meta http-equiv="REFRESH"
      content="0;url=http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=name%>">
    </HEAD>
    <%
  }
  else if(myShepherd.isOccurrence(name)) {
    %>
    <meta http-equiv="REFRESH"
      content="0;url=http://<%=CommonConfiguration.getURLLocation(request)%>/occurrence.jsp?number=<%=name%>">
    </HEAD>
    <%
  }

  else if (al.size() > 0) {
    //just grab the first one
    MarkedIndividual shr = al.get(0);
    String realName = shr.getName();
    %>

    <meta http-equiv="REFRESH"
      content="0;url=http://<%=CommonConfiguration.getURLLocation(request)%>/individuals.jsp?number=<%=realName%>">
    </HEAD>
    <%
  } else if (al2.size() > 0) {
    //just grab the first one
    MarkedIndividual shr = al2.get(0);
    String realName = shr.getName();
    %>

    <meta http-equiv="REFRESH"
      content="0;url=http://<%=CommonConfiguration.getURLLocation(request)%>/individuals.jsp?number=<%=realName%>">
    </HEAD>
    <%
  } else if (al3.size() > 0) {
      //just grab the first one
      Encounter shr = al3.get(0);
      String realName = shr.getEncounterNumber();
      %>

      <meta http-equiv="REFRESH"
        content="0;url=http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=realName%>">
      </HEAD>
      <%
  } else {
    %>
    <p><%=matchingRecord %>: <strong><%=name%></strong></p>
    <p>
      <%=tryAgain %>
    </p>

    <p>

      <form action="individuals.jsp" method="get" name="sharks"><strong><%=record %>:</strong>
      <input name="number" type="text" id="number" value=<%=name%>> <input
      name="sharky_button" type="submit" id="sharky_button"
      value="<%=getRecord %>"></form>
    </p>
    <p>
      <font color="#990000">
        <a href="encounters/encounterSearch.jsp">
          <%=props.getProperty("searchEncounters") %>
        </a>
      </font>
    </p>

    <p>
      <font color="#990000">
        <a href="individualSearch.jsp">
          <%=props.getProperty("searchIndividuals") %>
        </a>
      </font>
    </p>
  <%
  }
  %>

  <%
  }
  }

  catch (Exception eSharks_jsp) {
  System.out.println("Caught and handled an exception in individuals.jsp!");
  eSharks_jsp.printStackTrace();
  }



  myShepherd.rollbackDBTransaction();
  myShepherd.closeDBTransaction();


  %>


<%------- End of Main Div -------%>
</div>

<!--db: These are the necessary tools for photoswipe.-->
<%-- <%
String urlLoc = "http://" + CommonConfiguration.getURLLocation(request);
String pswipedir = urlLoc+"/photoswipe";
%>
<link rel='stylesheet prefetch' href='<%=pswipedir %>/photoswipe.css'>
<link rel='stylesheet prefetch' href='<%=pswipedir %>/default-skin/default-skin.css'>
<!--<p>Looking for photoswipe in <%=pswipedir %></p>-->
<jsp:include page='photoswipe/photoswipeTemplate.jsp' flush="true"/>
<script src='<%=pswipedir%>/photoswipe.js'></script>
<script src='<%=pswipedir%>/photoswipe-ui-default.js'></script> --%>

<%-- Import Footer --%>
<jsp:include page="footer.jsp" flush="true"/>
