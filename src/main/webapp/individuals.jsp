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
  String setsex = props.getProperty("setsex");
  String numencounters = props.getProperty("numencounters");
  String encnumber = props.getProperty("number");
  String date = props.getProperty("date");
  String size = props.getProperty("size");
  String spots = props.getProperty("spots");
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
  String matchingRecord = props.getProperty("matchingRecord");
  String tryAgain = props.getProperty("tryAgain");
  String addComments = props.getProperty("addComments");
  String record = props.getProperty("record");
  String getRecord = props.getProperty("getRecord");
  String allEncounters = props.getProperty("allEncounters");
  String allIndividuals = props.getProperty("allIndividuals");

  String sex = props.getProperty("sex");
  String location = props.getProperty("location");
  String alternateID = props.getProperty("alternateID");
  String occurringWith = props.getProperty("occurringWith");
  String behavior = props.getProperty("behavior");
  String haplotype = props.getProperty("location");
  String dataTypes = props.getProperty("dataTypes"); 
  String catalogNumber = props.getProperty("catalogNumber");
  String rolesOf = props.getProperty("roles");
  String relationshipWith = props.getProperty("relationshipWith");
  String typeOf = props.getProperty("type");
  String socialUnit = props.getProperty("socialUnit");
  String relationshipID = props.getProperty("relationshipID");
  String edit = props.getProperty("edit");
  String remove = props.getProperty("remove");
  String occurrenceNumber = props.getProperty("occurrenceNumber");
  
  String name = "";
  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("individuals.jsp");


	List<Collaboration> collabs = Collaboration.collaborationsForCurrentUser(request);

%>

<%
if (request.getParameter("number")!=null) {
	name=Util.sanitizeURLParameter(request.getParameter("number"));

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

  <link rel="stylesheet" href="css/createadoption.css">


<link href='//fonts.googleapis.com/css?family=Source+Sans+Pro:200,600,200italic,600italic' rel='stylesheet' type='text/css'>
<script src="//d3js.org/d3.v3.min.js"></script>
<script src="//phuonghuynh.github.io/js/bower_components/cafej/src/extarray.js"></script>
<script src="//phuonghuynh.github.io/js/bower_components/cafej/src/misc.js"></script>
<script src="//phuonghuynh.github.io/js/bower_components/cafej/src/micro-observer.js"></script>
<script src="//phuonghuynh.github.io/js/bower_components/microplugin/src/microplugin.js"></script>
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

    var buttons = $("#edit, #closeEdit").on("click", function(){
        buttons.toggle();
    })

    $("#edit").click(function() {
      $(".noEditText, #nameCheck, #namerCheck, #sexCheck, #birthCheck, #deathCheck, #altIdCheck, #nameError, #namerError, #sexError, #birthError, #deathError, #altIdError").hide();
      $(".editForm, .clickDateText, #Name, #Add, #birthy, #deathy, #AltID").show();
      $("#nameDiv, #namerDiv, #birthDiv, #deathDiv, #altIdDiv").removeClass("has-success");
      $("#nameDiv, #namerDiv, #birthDiv, #deathDiv, #altIdDiv").removeClass("has-error");
    });

    $("#closeEdit").click(function() {
      $(".editForm").hide();
      $(".clickDateText").hide();
      $(".noEditText").show();
    });
  });

</script>
<script>
 // Needed to get language specific values into javascript for table rendering.
 
var tableDictionary = {}

tableDictionary['sex'] = "<%= sex %>";
tableDictionary['location'] = "<%= location %>";
tableDictionary['alternateID'] = "<%= alternateID %>";
tableDictionary['occurringWith'] = "<%= occurringWith %>";
tableDictionary['behavior'] = "<%= behavior %>";
tableDictionary['haplotype'] = "<%= haplotype %>";
tableDictionary['dataTypes'] = "<%= dataTypes %>";
tableDictionary['catalogNumber'] = "<%= catalogNumber %>";
tableDictionary['roles'] = "<%= rolesOf %>";
tableDictionary['relationshipWith'] = "<%= relationshipWith %>";
tableDictionary['type'] = "<%= typeOf %>";
tableDictionary['socialUnit'] = "<%= socialUnit %>";
tableDictionary['relationshipID'] = "<%= relationshipWith %>";
tableDictionary['edit'] = "<%= edit %>";
tableDictionary['remove'] = "<%= remove %>";
tableDictionary['date'] = "<%= date %>";
tableDictionary['unknown'] = "<%= unknown %>";
tableDictionary['nickname'] = "<%= nickname %>";
tableDictionary['occurenceNumber'] = "<%= occurrenceNumber %>";


$(document).ready(function() {
    languageTable(tableDictionary);
});
</script>

<%---------- Main Div ----------%>
<div class="container maincontent">
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

            <h1 id="markedIndividualHeader" class="nickNameHeader" data-individualId ="<%=sharky.getIndividualID()%>"><span id="headerDisplayNickname"><%=myNickname%></span>
                  <%
                  if(CommonConfiguration.allowAdoptions(context)){
                  %>
                    <a href="createadoption.jsp?number=<%=sharky.getIndividualID()%>"><button class="btn btn-md"><%=props.getProperty("adoptMe") %><span class="button-icon" aria-hidden="true"></button></a>
                  <%
                  }
                  if (isOwner && CommonConfiguration.isCatalogEditable(context)) {%>

            <div>
              <button class="btn btn-md" type="button" name="button" id="edit"><%=props.getProperty("edit") %></button>
              <button class="btn btn-md" type="button" name="button" id="closeEdit"><%=props.getProperty("closeEdit") %></button>
            </div>
            <%}%></h1>


            <%


          } else {
            %>
            <h1 id="markedIndividualHeader"><%=markedIndividualTypeCaps%> <%=sharky.getIndividualID()%>
            <%
            if(CommonConfiguration.allowAdoptions(context)){
                  %>
                    <a href="createadoption.jsp?number=<%=sharky.getIndividualID()%>"><button class="btn btn-md">
                    <%= props.getProperty("nicknameMe") %><span class="button-icon" aria-hidden="true"></button></a>
                  <%
                  }
            if (isOwner && CommonConfiguration.isCatalogEditable(context)) {%>
            <div>
              <button class="btn btn-md" type="button" name="button" id="edit"><%= props.getProperty("edit") %></button>
              <button class="btn btn-md" type="button" name="button" id="closeEdit"><%= props.getProperty("closeEditCaps") %>t</button>
            </div>
            <%}%></h1>
          <%
          }
        }
          %>



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

              <p class="noEditText"><%=nickname %>: <span id="displayNickname"><%=myNickname%></span></p>

              <script type="text/javascript">
                $(document).ready(function() {

                  $("#Name").click(function(event) {
                    event.preventDefault();

                    $("#Name").hide();

                    var individual = $("input[name='individual']").val();
                    var nickname = $("#nickname").val();
                    var namer = $("#namer").val();

                    $.post("IndividualSetNickName", {"individual": individual, "nickname": nickname, "namer": namer},
                    function() {
                      $("#nicknameErrorDiv").hide();
                      $("#nameDiv, #namerDiv").addClass("has-success");
                      $("#nameCheck, #namerCheck").show();

                      $("#headerDisplayNickname, #displayNickname").html(nickname);
                    })
                    .fail(function(response) {
                      $("#nameDiv, #namerDiv").addClass("has-error");
                      $("#nameError, #namerError, #nicknameErrorDiv").show();
                      $("#nicknameErrorDiv").html(response.responseText);
                    });
                  });

                  $("#nickname, #namer").click(function() {
                    $("#nameError, #nameCheck, #namerCheck, #namerError, #nicknameErrorDiv").hide()
                    $("#nameDiv, #namerDiv").removeClass("has-success");
                    $("#nameDiv, #namerDiv").removeClass("has-error");
                    $("#Name").show();
                  });
                });
              </script>

                <%-- Edit nickname form --%>
                <p id="checkIndividualValue"></p>
                <div class="highlight" id="nicknameErrorDiv"></div>
                  <form name="nameShark" class="editForm">
                    <input name="individual" type="hidden" value="<%=name%>">
                      <div class="form-group has-feedback row" id="nameDiv">
                        <div class="col-sm-4">
                          <label><%=nickname %>:</label>
                        </div>
                        <div class="col-sm-7 editFormInput">
                          <input class="form-control" name="nickname" type="text" id="nickname" value="<%=myNickname%>" placeholder="<%=nickname %>">
                          <span class="form-control-feedback" id="nameCheck">&check;</span>
                          <span class="form-control-feedback" id="nameError">X</span>
                        </div>
                      </div>

                      <div class="form-group has-feedback row" id="namerDiv">
                        <div class="col-sm-4">
                          <label><%=nicknamer %>:</label>
                        </div>
                        <div class="col-sm-7 editFormInput">
                          <input class="form-control" name="namer" type="text" id="namer" value="<%=myNicknamer%>" placeholder="<%=nicknamer %>">
                          <span class="form-control-feedback" id="namerCheck">&check;</span>
                          <span class="form-control-feedback" id="namerError">X</span>
                        </div>
                        <input class="btn btn-sm editFormBtn" type="submit" name="Name" id="Name" value="<%=update %>">
                      </div>
                  </form>
                  <%-- End edit nickname form --%>

              <%
            }
            %>
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


          <a name="alternateid"></a>
          <%
            String altID="";
            if(sharky.getAlternateID()!=null){
            altID=sharky.getAlternateID();
            }

            %>
            <p class="noEditText"><%=alternateID %>: <span id="displayAltID"><%=altID%></span></p>

            <script type="text/javascript">
              $(document).ready(function() {
                $("#AltID").click(function(event) {
                  event.preventDefault();

                  $("#AltID").hide();

                  var individual = $("input[name='individual']").val();
                  var alternateid = $("#alternateid").val();

                  $.post("IndividualSetAlternateID", {"individual": individual, "alternateid": alternateid},
                  function() {
                    $("#altIdErrorDiv").hide();
                    $("#altIdDiv").addClass("has-success");
                    $("#altIdCheck").show();
                    $("#displayAltID").html(alternateid);
                  })
                  .fail(function(response) {
                    $("#altIdDiv").addClass("has-error");
                    $("#altIdError, #altIdErrorDiv").show();
                    $("#altIdErrorDiv").html(response.responseText);
                  });
                });

                $("#alternateid").click(function() {
                  $("#altIdError, #altIdCheck, #altIdErrorDiv").hide()
                  $("#altIdDiv").removeClass("has-success");
                  $("#altIdDiv").removeClass("has-error");
                  $("#AltID").show();
                });
              });
            </script>

            <%-- Start alt id form --%>
            <div class="highlight" id="altIdErrorDiv"></div>
            <form name="set_alternateid" class="editForm">
              <input name="individual" type="hidden" value="<%=name%>" />
              <div class="form-group has-feedback row" id="altIdDiv">
                <div class="col-sm-4">
                  <label><%=alternateID %>:</label>
                </div>
                <div class="col-sm-7 editFormInput">
                  <input class="form-control" name="alternateid" type="text" id="alternateid" value="<%=altID %>" placeholder="<%=alternateID %>"/>
                  <span class="form-control-feedback" id="altIdCheck">&check;</span>
                  <span class="form-control-feedback" id="altIdError">X</span>
                </div>
                  <input class="btn btn-sm editFormBtn" name="AltID" type="submit" id="AltID" value="<%=update %>">
              </div>
            </form>
            <%-- End alt id form --%>
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
    <p class="viewAllImgs"><a style="color:white;" href="encounters/thumbnailSearchResults.jsp?individualID=<%=sharky.getIndividualID()%>"><%=props.getProperty("allImages")%></a></p></div>


    <div class="slider col-sm-6 center-slider">
      <%-- Get images for slider --%>
      <%
///note this is very hacky... as jon about it
int numPhotos = 0;
for (Encounter enJ : sharky.getDateSortedEncounters()) {
	for (org.ecocean.media.MediaAsset maJ : enJ.getMedia()) {
		if (maJ.getMetadata() != null) maJ.getMetadata().getDataAsString();
	}
}
      ArrayList<JSONObject> photoObjectArray = sharky.getExemplarImages(request);
      numPhotos = photoObjectArray.size();
      String imgurlLoc = "//" + CommonConfiguration.getURLLocation(request);

      for (int extraImgNo=0; (extraImgNo<numPhotos&&extraImgNo<5); extraImgNo++) {
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
      href="//<%=CommonConfiguration.getURLLocation(request) %>/individuals.jsp?number=<%=name%>&edit=dynamicproperty&name=<%=nm%>#dynamicproperty"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="images/Crystal_Clear_action_edit.png" /></a></font>
      <%
      }
      %>
      </p>

      <%
      }
      }
      %>

        <br>
        <%-- Cooccurrence table starts here --%>
        <a name="cooccurrence"></a>
        <p><strong><%=props.getProperty("cooccurrence")%></strong></p>


        <script type="text/javascript">
        // <% String individualID = sharky.getIndividualID();%>
        $(document).ready(function() {

          getData("<%=individualID%>");
        });
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
      <p><strong><%=numencounters %></strong></p>
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

      <%-- Map --%>
      <br>
      <div>
        <jsp:include page="individualMapEmbed.jsp" flush="true">
          <jsp:param name="name" value="<%=name%>"/>
        </jsp:include>
      </div>
      <%-- End of Map --%>



              <%-- Start Adoption --%>
        <%
          if (CommonConfiguration.allowAdoptions(context)) {
        %>

      <p><strong><%=props.getProperty("meetAdopters") %></strong></p>
      <div style="width: 100%;">

          <jsp:include page="individualAdoptionEmbed.jsp" flush="true">
            <jsp:param name="name" value="<%=name%>"/>
          </jsp:include>
                </div>

          <%
           }
        %>
      <%-- End Adoption --%>

      <br>
      <%-- Start Collaborators --%>
      <div style="width: 100%;clear:both;">

          <%
          if(CommonConfiguration.showUsersToPublic(context)){
            Shepherd userShepherd=new Shepherd("context0");
            userShepherd.setAction("individuals.jsp2");
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
                      <img src="cust/mantamatcher/img/individual_placeholder_image.jpg" data-src="<%=profilePhotoURL%>" class="researcherPhoto lazyload">

                      <div class="researcherInfo" id="info<%=userNum%>">
                        <%
                        if(thisUser.getAffiliation()!=null){
                          %>
                          <p><strong><%=props.getProperty("affiliation") %></strong> <%=thisUser.getAffiliation() %></p>
                          <%
                        }
                        if(thisUser.getUserProject()!=null){
                          %>
                          <p><strong><%=props.getProperty("researchProject") %></strong> <%=thisUser.getUserProject() %></p>
                          <%
                            }
                            if(thisUser.getUserURL()!=null){
                            %>
                            <p><strong><%=props.getProperty("webSite") %></strong> <a style="font-weight:normal;color: blue" class="ecocean" href="<%=thisUser.getUserURL()%>"><%=thisUser.getUserURL() %></a><p>
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

$(document).ready(function(){
  var photos = parseInt("<%=numPhotos%>");
  if (photos<2) {
    $('.sssprev, .sssnext').hide();
  }
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
      content="0;url=//<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=name%>">
    </HEAD>
    <%
  }
  else if(myShepherd.isOccurrence(name)) {
    %>
    <meta http-equiv="REFRESH"
      content="0;url=//<%=CommonConfiguration.getURLLocation(request)%>/occurrence.jsp?number=<%=name%>">
    </HEAD>
    <%
  }

  else if (al.size() > 0) {
    //just grab the first one
    MarkedIndividual shr = al.get(0);
    String realName = shr.getName();
    %>

    <meta http-equiv="REFRESH"
      content="0;url=//<%=CommonConfiguration.getURLLocation(request)%>/individuals.jsp?number=<%=realName%>">
    </HEAD>
    <%
  } else if (al2.size() > 0) {
    //just grab the first one
    MarkedIndividual shr = al2.get(0);
    String realName = shr.getName();
    %>

    <meta http-equiv="REFRESH"
      content="0;url=//<%=CommonConfiguration.getURLLocation(request)%>/individuals.jsp?number=<%=realName%>">
    </HEAD>
    <%
  } else if (al3.size() > 0) {
      //just grab the first one
      Encounter shr = al3.get(0);
      String realName = shr.getEncounterNumber();
      %>

      <meta http-equiv="REFRESH"
        content="0;url=//<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=realName%>">
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
