<%@ page contentType="text/html; charset=utf-8" language="java"
         import="com.drew.imaging.jpeg.JpegMetadataReader,com.drew.metadata.Metadata,com.drew.metadata.Tag,org.ecocean.mmutil.MediaUtilities,
javax.jdo.datastore.DataStoreCache, org.datanucleus.jdo.*,javax.jdo.Query,
org.datanucleus.api.rest.orgjson.JSONObject,
org.datanucleus.api.rest.orgjson.JSONException,
org.ecocean.media.MediaAsset,
java.net.URL,
org.datanucleus.ExecutionContext,java.text.SimpleDateFormat,
		 org.joda.time.DateTime,org.ecocean.*,org.ecocean.social.*,org.ecocean.servlet.ServletUtilities,java.io.File, java.util.*, org.ecocean.genetics.*,org.ecocean.security.Collaboration, org.ecocean.security.HiddenEncReporter, com.google.gson.Gson,
org.datanucleus.api.rest.RESTUtils, org.datanucleus.api.jdo.JDOPersistenceManager, java.text.SimpleDateFormat, org.apache.commons.lang3.StringUtils" %>

<%!
  public static ArrayList<org.datanucleus.api.rest.orgjson.JSONObject> getExemplarImagesFast(MarkedIndividual thisIndiv, Shepherd myShepherd, HttpServletRequest req, int numResults, String imageSize) throws JSONException {
    ArrayList<org.datanucleus.api.rest.orgjson.JSONObject> al=new ArrayList<org.datanucleus.api.rest.orgjson.JSONObject>();

    List<MediaAsset> assets=new ArrayList<MediaAsset>();
    
    String[] kwReadableNames = {"ProfilePhoto"};
long t0 = System.currentTimeMillis();
    List<MediaAsset> profilePhotos =  myShepherd.getKeywordPhotosForIndividual(thisIndiv, kwReadableNames, 5);
//System.out.println("TIME A>>>>> " + (System.currentTimeMillis() - t0));
    if(profilePhotos==null || profilePhotos.size()<numResults) {
      //add what we have
      if(profilePhotos!=null)assets.addAll(profilePhotos);
      //but we need a few more examples
      String[] noKeywordNames = new String[0];
      List<MediaAsset> otherPhotos = myShepherd.getKeywordPhotosForIndividual(thisIndiv, noKeywordNames, (numResults-assets.size()));
//System.out.println("TIME B>>>>> " + (System.currentTimeMillis() - t0));
      if(otherPhotos!=null)assets.addAll(otherPhotos);
    }
    else{assets=profilePhotos;}
//System.out.println("TIME C>>>>> " + (System.currentTimeMillis() - t0));
    
    for(MediaAsset ma:assets) {
        if (ma != null) {
          
//System.out.println("TIME c>>>>> " + (System.currentTimeMillis() - t0) + " >>> " + ma);
          //JSONObject j = ma.sanitizeJson(req, new JSONObject());
          JSONObject j = new JSONObject();
//System.out.println("TIME d>>>>> " + (System.currentTimeMillis() - t0) + " >>> " + j);

          //we get a url which is potentially more detailed than we might normally be allowed (e.g. anonymous user)
          // we have a throw-away shepherd here which is fine since we only care about the url ultimately
          URL midURL = null;
          ArrayList<MediaAsset> kids = ma.findChildrenByLabel(myShepherd, imageSize);
//System.out.println("TIME D>>>>> " + (System.currentTimeMillis() - t0));
          if ((kids != null) && (kids.size() > 0)) midURL = kids.get(0).webURL();
          if (midURL != null) j.put("url", midURL.toString()); //this overwrites url that was set in ma.sanitizeJson()

          if ((j!=null)&&(ma.getMimeTypeMajor()!=null)&&(ma.getMimeTypeMajor().equals("image"))) {
            //put ProfilePhotos at the beginning
            if(ma.hasKeyword("ProfilePhoto")){al.add(0, j);}
            //do nothing and don't include it if it has NoProfilePhoto keyword
            //due to this dropout, you may get less than request
            //but there may be less than requested anyway for any individual
            else if(ma.hasKeyword("NoProfilePhoto")){}
            //otherwise, just add it to the bottom of the stack
            else{
              al.add(j);
            }
          }
        }
        if(al.size()>=numResults){return al;}
    }
//System.out.println("TIME E>>>>> " + (System.currentTimeMillis() - t0));
    return al;
  }

%>

<%
String context="context0";
context=ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("individuals.jsp");

if (request.getParameter("number")!=null) {
	String oldWorld = request.getParameter("number").trim();
        //we also check individualID (uuid) too, just in case some href in jsp is still using number=
		myShepherd.beginDBTransaction();
        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", "SELECT \"INDIVIDUALID\" FROM \"MARKEDINDIVIDUAL\" WHERE \"LEGACYINDIVIDUALID\" = ? OR \"ALTERNATEID\" LIKE ? OR \"INDIVIDUALID\" = ?");
        List results = (List) q.execute(oldWorld, "%" + oldWorld + "%", oldWorld);

        String tryId = null;
        if (results.iterator().hasNext()) tryId = (String) results.iterator().next();
        q.closeAll();
        myShepherd.rollbackAndClose();
        response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
        response.setHeader("Location", "individuals.jsp?id=" + tryId);
        response.flushBuffer();
}
else{

  boolean isLoggedIn=false;
  if(request.getUserPrincipal()!=null)isLoggedIn=true;
  String blocker = "";
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
  String locationID = props.getProperty("locationID");
  String id = request.getParameter("number");

  List<Collaboration> collabs = Collaboration.collaborationsForCurrentUser(request);

  boolean visible = false;

%>
<jsp:include page="header.jsp" flush="true"/>
<%
if (request.getParameter("id")!=null || request.getParameter("number")!=null) {
    //System.out.println("    |=-| INDIVIDUALS.JSP  INSIDE ID block");
    id = request.getParameter("id");
    if (id==null) id = request.getParameter("number");
	myShepherd.beginDBTransaction();
	try {

		MarkedIndividual indie = myShepherd.getMarkedIndividual(id);
		if (indie != null) {
			Vector myEncs=indie.getEncounters();

      		HiddenEncReporter hiddenData = new HiddenEncReporter(myEncs, request, myShepherd);
      		myEncs = hiddenData.securityScrubbedResults(myEncs);

			int numEncs=myEncs.size();

	      	// This is a big hack to make sure an encounter's annotations are loaded into the JDO cache
	      	// without this hack
	      	int numAnns = 0;
		      for (Object obj: myEncs) {
		        Encounter enc = (Encounter) obj;
		        if (enc!=null && enc.getAnnotations()!=null) {
		          for (Annotation ann: enc.getAnnotations()) {
		            if (ann!=null) {
		              String makeSureWeHaveIt = ann.getIAClass();
		              numAnns++;
		            }
		          }
		        }
		      }
		      //System.out.println("");
		      //System.out.println("individuals.jsp: I think a bot is loading this page, so here's some loggin':");
		      //System.out.println("This marked individual has "+numAnns+" anotations");

					//boolean visible = indie.canUserAccess(request);
		      visible = Collaboration.canUserAccessMarkedIndividual(indie, request);


				if (!visible) {

			        // remove any potentially-sensitive data, labeled with the secure-field class
			        System.out.println("Not visible! Printing stuff!");
			        %>
			        <script src="javascript/hide-secure-fields.js"></script>
			        <%
			  			ArrayList<String> uids = indie.getAllAssignedUsers();
							ArrayList<String> possible = new ArrayList<String>();
							for (String u : uids) {
								Collaboration c = null;
								if (collabs != null) c = Collaboration.findCollaborationWithUser(u, collabs);
								if ((c == null) || (c.getState() == null)) {
									User user = myShepherd.getUser(u);
									String fullName = u;
									if (user!=null && user.getFullName()!=null) fullName = user.getFullName();
									possible.add(u + ":" + fullName.replace(",", " ").replace(":", " ").replace("\"", " "));
								}
							}
							String cmsg = "<p>" + collabProps.getProperty("deniedMessage") + "</p>";
							cmsg = cmsg.replace("'", "\\'");

							if (possible.size() > 0) {
			    			String arr = new Gson().toJson(possible);
								blocker = "<script>$(document).ready(function() { $.blockUI({ message: '" + cmsg + "' + _collaborateMultiHtml(" + arr + ", "+isLoggedIn+") }) });</script>";
							} else {
								cmsg += "<p><input type=\"button\" onClick=\"window.history.back()\" value=\"BACK\" /></p>";
								blocker = "<script>$(document).ready(function() { $.blockUI({ message: '" + cmsg + "' }) });</script>";
							}
				} //end if visible

			} //end if is an individual
		}
		catch(Exception e){e.printStackTrace();}
		finally{
			myShepherd.rollbackDBTransaction();
		}
}

%>



<!--  FACEBOOK SHARE BUTTON -->
<div id="fb-root"></div>
<script type="text/javascript">(function(d, s, id) {
  var js, fjs = d.getElementsByTagName(s)[0];
  if (d.getElementById(id)) return;
  js = d.createElement(s); js.id = id;
  js.src = "//connect.facebook.net/en_US/all.js#xfbml=1";
  fjs.parentNode.insertBefore(js, fjs);
}(document, 'script', 'facebook-jssdk'));</script>



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

<script>
function setIndivAutocomplete(el) {
    if (!el || !el.length) return;
    var args = {
        resMap: function(data) {
            var res = $.map(data, function(item) {
                if (item.type != 'individual') return null;
                var label = item.label;
                if (item.species) label += '   ( ' + item.species + ' )';
                return { label: label, type: item.type, value: item.value };
            });
            return res;
        }
    };
    wildbook.makeAutocomplete(el[0], args);
}
</script>

<style>
.ptcol-maxYearsBetweenResightings {
	width: 100px;
}
.ptcol-numberLocations {
	width: 100px;
}
input.nameKey, input.nameValue {
  display: none;
}
</style>

<link rel="stylesheet" type="text/css" href="css/individualStyles.css">
<link href='//fonts.googleapis.com/css?family=Source+Sans+Pro:200,600,200italic,600italic' rel='stylesheet' type='text/css'>
<script src="//d3js.org/d3.v4.min.js"></script>
<script src="javascript/bubbleDiagram/extarray.js"></script>
<script src="javascript/bubbleDiagram/misc.js"></script>
<script src="javascript/bubbleDiagram/micro-observer.js"></script>
<script src="javascript/bubbleDiagram/microplugin.js"></script>
<script src="javascript/kdTree.js"></script>
<script src="javascript/relationshipDiagrams/jsonParser.js"></script>
<script src="javascript/relationshipDiagrams/graphAbstract.js"></script>
<script src="javascript/relationshipDiagrams/forceLayoutAbstract.js"></script>
<script src="javascript/bubbleDiagram/coOccurrenceGraph.js"></script>
<script src="javascript/relationshipDiagrams/socialGraph.js"></script>
<script src="javascript/bubbleDiagram/encounter-calls.js"></script>



<style>
  input.nameValue, input.nameKey {
    display: none;
  }

</style>


<script type="text/javascript">
  $(document).ready( function() {
    $("input.nameKey, input.nameValue").hide();

    $("#socialDiagramTab").click(e => {
      e.preventDefault();
      $(".socialVis").hide();
      $("#socialDiagram").show();
      $(".socialVisTab").removeClass("active");
      $("#socialDiagramTab").addClass("active");
    });

    $("#communityTableTab").click(e => {
      e.preventDefault();
      $(".socialVis").hide();
      $("#communityTable").show();
      $(".socialVisTab").removeClass("active");
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


    var buttons = $("#edit, #closeEdit").on("click", function(){
        buttons.toggle();
    })

    // edit button click area!!
    $("#edit").click(function() {
      $(".noEditText, #nameCheck, #namerCheck, #sexCheck, #birthCheck, #deathCheck, #altIdCheck, #nameError, #namerError, #sexError, #birthError, #deathError, #altIdError, span.nameKey, span.nameValue,.nameValue .hidden,.namebutton .hidden, .deletename .hidden").hide();
      $(".editForm, .clickDateText, #Name, #Add, #birthy, #deathy, #AltID, input.nameKey, input.nameValue, #NicknameColon, #defaultNameColon, input.btn.deletename, input.namebutton, div.newnameButton").show();
      $("#nameDiv, #namerDiv, #birthDiv, #deathDiv, #altIdDiv").removeClass("has-success");
      $("#nameDiv, #namerDiv, #birthDiv, #deathDiv, #altIdDiv").removeClass("has-error");
    });

    //btn btn-sm editFormBtn namebutton
    //.nameValue .hidden,.namebutton .hidden, .deletename .hidden
    $("#closeEdit").click(function() {
      //$(".namebutton").css("visibility", "hidden");
      $(".editForm, input.nameKey, input.nameValue, #defaultNameColon, #NicknameColon, input.namebutton, input.btn.deletename").hide();
      $(".clickDateText").hide();
      $(".noEditText, span.nameKey, span.nameValue").show();
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
tableDictionary['numencounters'] = "<%= numencounters%>";
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
tableDictionary['occurrenceNumber'] = "<%= occurrenceNumber%>";
tableDictionary['locationID'] = "<%= locationID%>";


$(document).ready(function() {
    languageTable(tableDictionary);
});
</script>

<%---------- Main Div ----------%>
<div class="container maincontent secure-field">
  <%=blocker%>
  <%
  myShepherd.beginDBTransaction();
  try {
    if (myShepherd.isMarkedIndividual(id) && visible) { %>
  <%-- Header Row --%>
  <div class="row mainHeader secure-field" style="position:relative;">
    <div class="col-sm-6">

          <%
          MarkedIndividual sharky=myShepherd.getMarkedIndividual(id);

          // replace this with canUserViewIndividual?
          // boolean isOwner = ServletUtilities.isUserAuthorizedForIndividual(sharky, request);
          boolean isOwner = Collaboration.canUserFullyEditMarkedIndividual(sharky, request);
          //System.out.println("    |=-| INDIVIDUALS.JSP we have sharkID "+id+", isOwner="+isOwner+" and names "+sharky.getNames());

          if (CommonConfiguration.allowNicknames(context)) {
            if ((sharky.getNickName() != null) && (!sharky.getNickName().trim().equals(""))) {
              String myNickname = "";

              String nameInProjectContext = sharky.getDisplayName(request, myShepherd);
              if (nameInProjectContext!=null) {
                myNickname = nameInProjectContext;
              } else {
                myNickname = sharky.getDisplayName("Nickname");
              }
            %>

            <h1 id="markedIndividualHeader" class="nickNameHeader" data-individualId ="<%=sharky.getIndividualID()%>"><span id="headerDisplayNickname"><%=myNickname%></span>
                  <%
                  if (isOwner && CommonConfiguration.isCatalogEditable(context)) {%>

            <div>
              <button class="btn btn-md" type="button" name="button" id="edit"><%=props.getProperty("edit") %></button>
              <button class="btn btn-md" type="button" name="button" id="closeEdit"><%=props.getProperty("closeEdit") %></button>
            </div>
            <%}%></h1>


            <%


          } else { // no nicknames allowed in cc.props
            System.out.println("no nicknames allowed, trying sharky.getDisplayName(request, myShepherd) = "+sharky.getDisplayName(request, myShepherd) );
            %>
            <h1 id="markedIndividualHeader"><%=markedIndividualTypeCaps%> <%=sharky.getDisplayName(request, myShepherd)%>
            <%
            if (isOwner && CommonConfiguration.isCatalogEditable(context)) {%>
            <div>
              <button class="btn btn-md" type="button" name="button" id="edit"><%= props.getProperty("edit") %></button>
              <button class="btn btn-md" type="button" name="button" id="closeEdit"><%= props.getProperty("closeEditCaps") %></button>
            </div>
            <%}%></h1>
          <%
          }
        }
                  //System.out.println("    |=-| INDIVIDUALS.JSP after nickname");

          %>



      <%-- Descriptions --%>
      <div class="row">
        <div class="col-sm-6">
            <p><!--<%=markedIndividualTypeCaps%>--><%
String allNames = null;
if (sharky.getNames() != null) {
    List<String> names = sharky.getNamesList();
    if ((names != null) && (names.size() > 0)) allNames = String.join(", ", names);
    String defaultName = sharky.getDefaultName();
    String nickyName = sharky.getNickName();

    %>
    <div class="namesection default">
      <input class="form-control nameKey name" name="nameKey" type="text" id="nameKey" data-oldkey="Default" data-oldvalue="<%=defaultName%>" value="Default" placeholder="Default" >
      <span id="defaultNameColon">:</span>
      <%
      if(defaultName!=null){
      %>
      	<span style="font-weight: bold;" class="nameValue default" data-oldvalue="<%=defaultName%>"><%=defaultName %></span>
      <%
      }
      else{defaultName="";}
      %>
      <input class="form-control nameValue name" name="nameValue" type="text" id="nameValue" data-oldkey="Default" data-oldvalue="<%=defaultName%>" value="<%=defaultName%>" placeholder="<%=defaultName %>" >
      <input class="btn btn-sm editFormBtn namebutton" type="submit" value="Update">
      <span class="nameCheck">&check;</span>
      <span class="nameError">X</span>
      <input class="btn btn-sm editFormBtn deletename" type="submit" value="X">
      <%
      if(names.size()>1){
      %>
      <br><%=props.getProperty("previousNames") %> <%=names.toString().replaceAll(defaultName+",", "").replaceAll("\\[","").replaceAll("\\]","").trim() %>
      <%
		}
      %>
    </div>

    <div class="namesection nickname">
      <input class="form-control nameKey name" name="nameKey" type="text" id="nameKey" data-oldkey="Nickname" data-oldvalue="<%=nickyName%>" value="Nickname" placeholder="Nickname" >
      <span id="NicknameColon">:</span>
      <%
      if(nickyName!=null){
      %>
      	<span style="font-weight: bold;" class="nameValue" data-oldvalue="<%=nickyName%>"><%=nickyName %></span>
      <%
      }
      else{nickyName="";}
      %>
      <input class="form-control nameValue name" name="nameValue" type="text" id="nameValue" data-oldkey="Nickname" data-oldvalue="<%=nickyName%>" value="<%=nickyName%>" placeholder="<%=nickyName %>" >
      <input class="btn btn-sm editFormBtn namebutton" type="submit" value="Update">
      <span class="nameCheck">&check;</span>
      <span class="nameError">X</span>
      <input class="btn btn-sm editFormBtn deletename" type="submit" value="X">
      <%
      List<String> nicknames=sharky.getNamesList(MarkedIndividual.NAMES_KEY_NICKNAME);
      if(nicknames!=null && nicknames.size()>1){
      %>
      <br><em><%=props.getProperty("previousNames") %> <%=nicknames.toString().replaceAll(nicknames.get(0)+",", "").replaceAll("\\[","").replaceAll("\\]","").trim() %></em>
      <%
		}
      %>
    </div>



    <%
    // make UI for non-default names here
    if ((sharky.getNames() != null) && (sharky.getNames().size() > 0) && (sharky.getNames().getKeys()!=null)){
    	System.out.println("About to go through the names for keys: "+String.join(", ",sharky.getNames().getKeys()));
      	boolean inProjectsAndWillGetDisplayedInSeparateSection = false;
	    for (String nameKey: sharky.getNames().getKeys()) {
	      	if (MultiValue.isDefault(nameKey)) continue;
	      	if (nameKey.equals("Nickname")) continue;
        	if(StringUtils.isBlank(nameKey)){ //blanks needed to be caught and skipped
          		continue;
        	}
	      	if (MarkedIndividual.NAMES_KEY_LEGACYINDIVIDUALID.equals(nameKey)) continue;
        	MarkedIndividual indie = myShepherd.getMarkedIndividual(id);
        	List<Project> projects = myShepherd.getAllProjectsForMarkedIndividual(indie);
        	if(nameKey.trim().contains("Merged") && (sharky.getName(nameKey) == null || sharky.getName(nameKey).equals(""))){ //skip the "merged" nameKeys if they're empty
          		continue;
        	}
        	if (projects!=null&&projects.size()>0) {
          		for(Project currentProject: projects){
            		String researchProjName = currentProject.getResearchProjectName();
            		if (nameKey.contains(researchProjName) && !nameKey.contains("Merged")){
              			inProjectsAndWillGetDisplayedInSeparateSection = true;
              			continue;
            		}
          		}
        	}
        	if(inProjectsAndWillGetDisplayedInSeparateSection == true){
          		inProjectsAndWillGetDisplayedInSeparateSection = false;
          		continue;
        	}

	      	String nameLabel=nameKey;
	      	if (MarkedIndividual.NAMES_KEY_NICKNAME.equals(nameKey)) nameLabel = nickname;
	      	else if (MarkedIndividual.NAMES_KEY_ALTERNATEID.equals(nameKey)) nameLabel = alternateID;
	      	String nameValue = sharky.getName(nameKey);
	      	%>
	      	<div class="namesection <%=nameKey%>">
	        	<span class="nameKey" data-oldkey="<%=nameKey%>"><em><%=nameLabel%></em></span>
	        	<input class="form-control name nameKey" name="nameKey" type="text" id="nameKey" value="<%=nameKey%>" placeholder="<%=nameKey %>" >
	        	<span id="nameColon">:</span>

	        	<span class="nameValue <%=nameKey%>" data-oldvalue="<%=nameValue%>"><%=nameValue%></span>
	        	<input class="form-control name nameValue" name="nameValue" type="text" id="nameValue" value="<%=nameValue%>" placeholder="<%=nameValue %>" >
	        	<input class="btn btn-sm editFormBtn namebutton" type="submit" value="Update">

	        	<span class="nameCheck">&check;</span>
	        	<span class="nameError">X</span>
	        	<input class="btn btn-sm editFormBtn deletename" type="submit" value="X">
	      	</div>

        	<%
	    } //end for loop on non-project names
	}

  MarkedIndividual indie = myShepherd.getMarkedIndividual(id);
  List<Project> projects = myShepherd.getAllProjectsForMarkedIndividual(indie);
  if(projects!=null && projects.size()>0){
	  
  	int count=0;
    for(Project currentProject: projects){
    	count++;
    
      String researchProjId = currentProject.getProjectIdPrefix();
      String researchProjName = currentProject.getResearchProjectName();
      String incrementalId = indie.getName(researchProjName);
      if(incrementalId != null && researchProjName!=null && researchProjId !=null && request.getUserPrincipal()!=null){
        %>
        <div class="namesection <%=researchProjName%>">
	        <span class="nameKey" data-oldkey="<%=researchProjName%>"><em><%=researchProjName %></em></span>
	        <input class="form-control name nameKey" name="nameKey" type="text" id="nameKey" value="<%= researchProjName%>" placeholder="<%= researchProjName%>" >
	        <span id="nameColon">:</span>

	        <span class="nameValue <%=researchProjName %>" data-oldvalue="<%=incrementalId%>"><%=incrementalId%></span>
	        <input class="form-control name nameValue" name="nameValue" type="text" id="nameValue" value="<%=incrementalId%>" placeholder="<%=incrementalId %>" >
	        <input class="btn btn-sm editFormBtn namebutton" type="submit" value="Update">

	        <span class="nameCheck">&check;</span>
	        <span class="nameError">X</span>
	        <input class="btn btn-sm editFormBtn deletename" type="submit" value="X">
	      </div>

        <%
      }
      else if(request.getUserPrincipal()!=null){
        String noIdTxt = props.getProperty("noIdIn");
        %>
        <div class="namesection <%=researchProjName%>">
          <span class="nameKey" data-oldkey="<%=researchProjName%>"><em><%=researchProjName%></em></span>
          <input class="form-control name nameKey" name="nameKey" type="text" id="nameKey" value="<%= researchProjName%>" placeholder="<%= researchProjName%>" >
          <span id="nameColon">:</span>

          <span class="nameValue <%=researchProjName%>" data-oldvalue="<%=noIdTxt%>"><%=noIdTxt%></span>
          <input class="form-control name nameValue" name="nameValue" type="text" id="nameValue" value="<%=noIdTxt%>" placeholder="<%=noIdTxt %>" >
          <input class="btn btn-sm editFormBtn namebutton" type="submit" value="Update">

          <span class="nameCheck">&check;</span>
          <span class="nameError">X</span>
          <input class="btn btn-sm editFormBtn deletename" type="submit" value="X">
        </div>

        <%
      }
    }
  }

    // "add new name" Edit section
    %>

        <div class="newnameButton">
      <input id="newNameButton" class="btn btn-sm editFormBtn namebutton newname" type="submit" value="Add New Name">
    </div>

    <div class="namesection newname">
      <span class="nameKey newname" data-oldkey=""><em></em></span>
      <input class="form-control nameKey name" name="nameKey" type="text" id="nameKey" value="" placeholder="" >
      <span id="nameColon">:</span>

      <span class="nameValue newname"></span>
      <input class="form-control nameValue name" name="nameValue" type="text" id="nameValue" value="" placeholder="" >
      <input class="btn btn-sm editFormBtn namebutton" type="submit" value="Update">

      <span class="nameCheck">&check;</span>
      <span class="nameError">X</span>

    </div>

    </div>
    <div class="row">
      <div class="col-sm-6">



    <style>
      #defaultNameColon, #NicknameColon, .nameCheck, .nameError, .nameErrorDiv, div.newname, input.namebutton, input.btn.deletename {
        display: none;
      }
      span.nameKey {
        font-style: oblique;
      }
      input.form-control.name {
        width: 25%
      }
      input.deletename {
        background: red;
      }
      input.namebutton, input.deletename {
        width: auto;
        margin-right:0;
        margin-top:0;
      }
      input.namebutton.newname {
        width: auto;
        margin-right:0;
        margin-top:0;
      }
      .namesection .nameCheck {
        color: green;
      }
      .namesection .nameError {
        color: red;
      }

    </style>


    <script type="text/javascript">
    $(document).ready(function() {

      $("#newNameButton").click(function (event) {
        console.log("newnamebutton clicked!");
        $(this).hide();
        $("div.newname").show();
      });

      $(".namebutton").click(function(event) {
        event.preventDefault();

        var nameKeySpan = $(this).siblings(".nameKey");
        var nameValueSpan = $(this).siblings(".nameValue");

        var oldKey = nameKeySpan.data("oldkey");
        var oldVal = nameValueSpan.data("oldvalue");

        var newKey = $(this).siblings("input.nameKey").val();
        var newVal = $(this).siblings("input.nameValue").val();

        console.log("namebutton 1 was clicked with vars newKey="+newKey+", newValue="+newVal+", oldKey="+oldKey+", oldVal="+oldVal);

        if (newKey===oldKey && newVal===oldVal) return;

        var indID = "<%=id%>";
        var userId = 'null';
        <%
        if(myShepherd.getUser(request)!=null){
        %>
        userId = '<%=myShepherd.getUser(request).getUsername() %>';
        <%
        }
        %>
        var rememberMe = this;

        $.post("IndividualSetName", {"individualID": indID, "oldKey": oldKey, "oldValue": oldVal, "newKey": newKey, "newValue": newVal},
        function() {
          console.log("SUCCESSFUL callback on individualSetName. this = ");
          console.log(this);
          const indNewNameComments = "Changed name to: " + newVal + " for key: " + newKey + " individual: " + indID;
          $.post("../IndividualAddComment", {"individual": indID, "user": userId, "comments": indNewNameComments},
          function() {
            $("#autoCommentErrorDiv").hide();
            $("#autoCommentsDiv").prepend("<p>" + indNewNameComments + "</p>");
            $("#autoComments").val("");
          })
          .fail(function(response) {
            $("#autoCommentErrorDiv").show();
            $("#autoCommentErrorDiv").html(response.responseText);
          });
          // show success and checkbox
          $(rememberMe).siblings("input.name").addClass("has-success");
          $(rememberMe).siblings(".nameCheck, nameColon").show();
          // update the values in the name elements
          $(rememberMe).siblings(".nameKey").html(newKey);
          $(rememberMe).siblings(".nameKey").data("oldkey",newKey);
          $(rememberMe).siblings(".nameValue").html(newVal);
          $(rememberMe).siblings(".nameKey").data("oldvalue",newvalue);
        })
        .fail(function(response) {
          console.log("FAILED callback on individualSetName");
          $(this).siblings("input.name").addClass("has-error");
          $(this).siblings(".nameError").show();
          $(this).siblings("div.nameError").html(response.responseText);
        });
      });

      $("input.deletename").click(function(event) {
        event.preventDefault();

        var nameKeySpan = $(this).siblings(".nameKey");
        var nameValueSpan = $(this).siblings(".nameValue");

        var oldKey = nameKeySpan.data("oldkey");
        var oldVal = nameValueSpan.data("oldvalue");
        var indID = "<%=id%>";

        var rememberMe = this;

        var confirmDelete = confirm("Are you sure you want to remove the name \""+oldVal+"\" with label \""+oldKey+"\" from this individual?");

        if (confirmDelete) {
          $.post("IndividualSetName", {"individualID": indID, "oldKey": oldKey, "oldValue": oldVal, "delete": true},
        function() {
          console.log("SUCCESSFUL callback on individualSetName, delete case. this = "+this);
          // show success and checkbox
          $(rememberMe).siblings("input.name").addClass("has-success");
          $(rememberMe).siblings(".nameValue, .namebutton, .deletename").addClass("hidden");
          $(rememberMe).siblings(".nameCheck").show();
          // update the values in the name elements
          $(rememberMe).siblings(".nameKey").html(newKey);
          $(rememberMe).siblings(".nameValue").html(newVal);
          $(rememberMe).siblings(".hidden").hide();
        })
        .fail(function(response) {
          console.log("FAILED callback on individualSetName, delete case");
          $(this).siblings("input.name").addClass("has-error");
          $(this).siblings(".nameError").show();
          $(this).siblings("div.nameError").html(response.responseText);
        });

        }


        console.log("namebutton 2 was clicked with vars newKey="+newKey+", newValue="+newVal+", oldKey="+oldKey+", oldVal="+oldVal);

        if (newKey===oldKey && newVal===oldVal) return;

        var indID = "<%=id%>";
        var rememberMe = this;

      });

      $("#nickname, #namer").click(function() {
        $("#nameError, #nameCheck, #namerCheck, #namerError, #nicknameErrorDiv").hide()
        $("#nameDiv, #namerDiv").removeClass("has-success");
        $("#nameDiv, #namerDiv").removeClass("has-error");
        $("#Name").show();
      });
    });
  </script>
    <%
}
            %></p>
            <%
            String sexValue="";
            if(sharky.getSex()!=null){sexValue=sharky.getSex();}
            %>
            <p class="noEditText"><%=sex %>: <span id="displaySex"><%=sexValue %></span></p>

            <script type="text/javascript">
              $(document).ready(function() {
            	<%
            	if(!sexValue.equals("")){
            	%>
                	$("#selectSex option[value=<%=sexValue%>]").attr('selected','selected');
				<%
            	}
				%>
                $("#Add").click(function(event) {
                  event.preventDefault();
                  $("#Add").hide();

                  var individual = "<%=sharky.getIndividualID() %>";
                  var sex = $("#newSex").val();

                  $.post("IndividualSetSex", {"individual": individual, "selectSex": sex},
                  function() {
                    $("#sexErrorDiv").hide();
                    $("#sexCheck").show();
                    $("#displaySex").html(sex);
                    $("svg.bubbleChart").remove();
                    getData(individual, null);
                  })
                  .fail(function(response) {
                    $("#sexError, #sexErrorDiv").show();
                    $("#sexErrorDiv").html(response.responseText);
                  });
                });

                $("#newSex").click(function() {
                  $("#sexError, #sexCheck, #sexErrorDiv").hide()
                  $("#Add").show();
                });
              });
            </script>

          <div class="highlight" id="sexErrorDiv"></div>
          <form name="setxsexshark" class="editForm">
            <input name="individual" type="hidden" value="<%=request.getParameter("id")%>"/>
            <div class="form-group row" id="selectSex">
              <div class="col-sm-4">
                <label><%=sex %>: </label>
              </div>
              <div class="col-sm-7 col-xs-11 editFormInput">
                <select id="newSex" name="selectSex" size="1" class="form-control">
                  <option value="unknown"><%=props.getProperty("unknown") %></option>
                  <option value="male"><%=props.getProperty("male") %></option>
                  <option value="female"><%=props.getProperty("female") %></option>
                </select>
              </div>
              <input class="btn btn-sm editFormBtn" name="Add" type="submit" id="Add" value="<%=update %>">
                <span id="sexCheck">&check;</span>
                <span id="sexError">X</span>
            </div>
          </form>
          <%-- End edit sex form --%>

          <%
            if(CommonConfiguration.showProperty("showTaxonomy",context)){

            String genusSpeciesFound=props.getProperty("notAvailable");
            if(sharky.getGenusSpeciesDeep()!=null){genusSpeciesFound=sharky.getGenusSpeciesDeep();}
            %>
            <p>
              <%=props.getProperty("taxonomy")%>: <em><%=genusSpeciesFound%></em>
            </p>
            <%
              }
              %>
                </div>
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
            <p class="noEditText"><%=props.getProperty("birthdate") %>: <span id="displayBirth"><%=displayTimeOfBirth%></span></p>


            <script type="text/javascript">
              $(document).ready(function() {
                $("#birthy").click(function(event) {
                  event.preventDefault();

                  $("#birthy").hide();

                  var individual = $("input[name='individual']").val();
                  var timeOfBirth = $("#timeOfBirth").val();

                  $.post("IndividualSetYearOfBirth", {"individual": individual, "timeOfBirth": timeOfBirth},
                  function() {
                    $("#birthErrorDiv").hide();
                    $("#birthDiv").addClass("has-success");
                    $("#birthCheck").show();
                    $("#displayBirth").html(timeOfBirth);
                  })
                  .fail(function(response) {
                    $("#birthDiv").addClass("has-error");
                    $(".birthDateText").hide();
                    $("#birthError, #birthErrorDiv").show();
                    $("#birthErrorDiv").html(response.responseText);
                  });
                });

                $("#timeOfBirth").click(function() {
                  $("#birthError, #birthCheck, #birthErrorDiv").hide()
                  $("#birthDiv").removeClass("has-success");
                  $("#birthDiv").removeClass("has-error");
                  $("#birthy, .birthDateText").show();
                });
              });
            </script>

            <%-- Edit birth date form --%>
            <div class="highlight" id="birthErrorDiv"></div>
            <p class="clickDateText birthDateText"><%=props.getProperty("clickDate")%></p>
            <p class="clickDateText birthDateText"><%=props.getProperty("leaveBlank")%></p>

            <form class="editForm" name="set_birthdate">
              <input name="individual" type="hidden" value="<%=request.getParameter("id")%>"/>
              <div class="form-group has-feedback row" id="birthDiv">
                <div class="col-sm-4">
                  <label><%=props.getProperty("birthdate")  %>:</label>
                </div>
                <div class="col-sm-7 editFormInput">
                  <input class="form-control" name="timeOfBirth" type="text" id="timeOfBirth" value="<%=timeOfBirth %>" placeholder="2013-12-21">
                  <span class="form-control-feedback" id="birthCheck">&check;</span>
                  <span class="form-control-feedback" id="birthError">X</span>
                </div>
                  <input class="btn btn-sm editFormBtn" name="birthy" type="submit" id="birthy" value="<%=update %>">
              </div>
            </form>
            <%-- End edit birth date form --%>

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
            <p class="noEditText"><%=props.getProperty("deathdate")  %>: <span id="displayDeath"><%=displayTimeOfDeath%></span></p>

            <script type="text/javascript">
              $(document).ready(function() {
                $("#deathy").click(function(event) {
                  event.preventDefault();

                  $("#deathy").hide();

                  var individual = $("input[name='individual']").val();
                  var timeOfDeath = $("#timeOfDeath").val();

                  $.post("IndividualSetYearOfDeath", {"individual": individual, "timeOfDeath": timeOfDeath},
                  function() {
                    $("#deathErrorDiv").hide();
                    $("#deathDiv").addClass("has-success");
                    $("#deathCheck").show();
                    $("#displayDeath").html(timeOfDeath);
                  })
                  .fail(function(response) {
                    $("#deathDiv").addClass("has-error");
                    $(".deathDateText").hide();
                    $("#deathError, #deathErrorDiv").show();
                    $("#deathErrorDiv").html(response.responseText);
                  });
                });

                $("#timeOfDeath").click(function() {
                  $("#deathError, #deathCheck, #deathErrorDiv").hide()
                  $("#deathDiv").removeClass("has-success");
                  $("#deathDiv").removeClass("has-error");
                  $("#deathy, .deathDateText").show();
                });
              });
            </script>

            <%-- Edit death date form --%>
            <div class="highlight" id="deathErrorDiv"></div>
            <p class="clickDateText deathDateText"><%=props.getProperty("clickDate")%></p>
            <p class="clickDateText deathDateText"><%=props.getProperty("leaveBlank")%></p>

            <form class="editForm" name="set_deathdate">
              <input name="individual" type="hidden" value="<%=request.getParameter("id")%>" />
              <div class="form-group has-feedback row" id="deathDiv">
                <div class="col-sm-4">
                  <label><%=props.getProperty("deathdate")  %>:</label>
                </div>
                <div class="col-sm-7 editFormInput">
                  <input class="form-control" name="timeOfDeath" type="text" id="timeOfDeath" value="<%=timeOfDeath %>" placeholder="2013-12-21"/>
                  <span class="form-control-feedback" id="deathCheck">&check;</span>
                  <span class="form-control-feedback" id="deathError">X</span>
                </div>
                  <input class="btn btn-sm editFormBtn" name="deathy" type="submit" id="deathy" value="<%=update %>">
              </div>
            </form>
            <%-- End edit death date form --%>
          <!-- end death date -->

          <a name="alternateid"></a>
          <%
            String altID="";
/*
            if(sharky.getAlternateID()!=null){
            altID=sharky.getAlternateID();
            }
*/

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
              <input name="individual" type="hidden" value="<%=request.getParameter("id")%>" />
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
    <p class="viewAllImgs"><a style="color:white;" href="encounters/thumbnailSearchResults.jsp?individualIDExact=<%=sharky.getIndividualID()%>"><%=props.getProperty("allImages")%></a></p></div>


    <div class="slider col-sm-6 center-slider">
      <%-- Get images for slider --%>
      <%
      ArrayList<JSONObject> photoObjectArray = getExemplarImagesFast(sharky, myShepherd, request, 5, "_mid");
      String imgurlLoc = "//" + CommonConfiguration.getURLLocation(request);

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
          href="//<%=CommonConfiguration.getURLLocation(request) %>/individuals.jsp?number=<%=id%>&edit=dynamicproperty&name=<%=nm%>#dynamicproperty"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="images/Crystal_Clear_action_edit.png" /></a></font>
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
    <!-- begin social unit memberships-->

    <p><strong><%=props.getProperty("socialUnitMemberships")%></strong></p>
    <input class="btn btn-sm" type="button" id="editSocialMembership" value="<%=props.getProperty("editSocialMembership") %>">
    <br/>

    <div id="allDisplayMemberships">
    <br/>
    <%
      List<SocialUnit> units = myShepherd.getAllSocialUnitsForMarkedIndividual(sharky);
      String unitName = "";
      String role = "";
      String startDate = "";
      String endDate = "";
      if (isOwner&&CommonConfiguration.isCatalogEditable(context)) {
        if (units!=null) {
            for (SocialUnit unit : units) {
                Membership membership = unit.getMembershipForMarkedIndividual(sharky);
                if (unit.getSocialUnitName()!=null) {unitName=unit.getSocialUnitName();}
                if (membership.getRole()!=null) {role=membership.getRole();}
                if (membership.getStartDate()!=null) {startDate=String.valueOf(membership.getStartDate());}
                if (membership.getEndDate()!=null) {endDate=String.valueOf(membership.getEndDate());}
                boolean hasData = (role!=null&&!"".equals(role));

      %>

        <!--display current social unit membership-->
        <div id="displayMembership<%=unitName%>" class="socialUnitEditForm socialUnitMargin">
          <div class="form-group row" style="padding: 5px;">

            <div class="col-xs-3 col-sm-2">
              <label><strong><%=props.getProperty("socialGroupName") %></strong></label>
              <p class="socialGroupName"><a href="socialUnit.jsp?name=<%=unitName%>"><%=unitName%></a></p>
            </div>

            <div class="col-xs-3 col-sm-2">
              <label><strong><%=props.getProperty("socialRoleName") %></strong></label>
              <p class="socialRoleName"><%=role%></p>
            </div>

            <div class="col-xs-3 col-sm-2">
              <label><strong><%=props.getProperty("socialGroupMembershipStart") %></strong></label>
              <p class="socialGroupMembershipStart"><%=startDate%></p>
            </div>

            <div class="col-xs-3 col-sm-2">
              <label><strong><%=props.getProperty("socialGroupMembershipEnd") %></strong></label>
              <p class="socialGroupMembershipEnd"><%=endDate%></p>
            </div>

          </div>
        </div>

        <%
      }
    }
    %>

  </div>

        <!-- form to edit or create social unit membership -->
        <br/>
        <div id="editOrCreateMembership" class="hidden socialUnitEditForm socialUnitMargin">
          <div class="form-group row" style="padding: 5px;">

            <div class="col-xs-3 col-sm-2">
              <label><strong><%=props.getProperty("socialGroupName") %></strong></label>

              <!-- if unit name is empty, only display the 'new field. '-->

              <select id="socialGroupNameSelect" name="socialGroupNameSelect" onchange="socialGroupNameSelectChanged(this)">
                <option value="new" selected>CREATE NEW</option>
                <%
                System.out.println("How many social unit??? "+units.size());
                if (units!=null&&units.size()>0) {
                  for (SocialUnit formUnit : units) {
                    System.out.println("THIS UNIT:  "+formUnit.getSocialUnitName());
                    String formUnitName = "Unassigned";
                    if (formUnit.getSocialUnitName()!=null&&!"".equals(formUnit.getSocialUnitName())) formUnitName = formUnit.getSocialUnitName();
                    %>
                      <option value="<%=formUnitName%>"><%=props.getProperty("editSocialUnit")%> <%=formUnitName%></option>
                    <%
                  }
                }
                %>
              </select>

              <div id="newSocialGroupNameInput">
                <label><small>New Social Unit Name</small></label>
                <!-- should show current selection, like other fields, or nothing for new -->
                <br/>
                <input id="socialGroupNameField" class="form-control" value="<%=unitName%>" type="text"></input>
              </div>
            </div>

            <div class="col-xs-3 col-sm-2">
              <label><strong><%=props.getProperty("socialRoleName") %></strong></label>
              <input id="socialRoleNameField" class="form-control" value="<%=role%>"type="text"></input>
            </div>

            <div class="col-xs-3 col-sm-2">
              <label><strong><%=props.getProperty("socialGroupMembershipStart") %></strong></label>
              <input id="socialGroupMembershipStartField" class="form-control" value="<%=startDate%>" type="date"></input>
            </div>

            <div class="col-xs-3 col-sm-2">
              <label><strong><%=props.getProperty("socialGroupMembershipEnd") %></strong></label>
              <input id="socialGroupMembershipEndField" class="form-control" value="<%=endDate%>" type="date"></input>
            </div>

          </div>
          <div style="padding: 5px;">
            <input class="btn btn-sm btn" type="button" name="button" id="submitSocialMembership" value="<%=props.getProperty("editCreate")%>">

            <input class="btn btn-sm btn" type="button" name="button" id="deleteSocialMembership" value="<%=props.getProperty("deleteMembership")%>">
            <!--sucess/fail message from servlet-->
            <label id="membershipActionResponse" ></label>
          </div>
          </br>
        </div>


      <%
      }
      %>

            <%-- Start Encounter Table --%>
      <p><strong><%=numencounters %> &amp; <%=props.getProperty("tissueSamples") %></strong></p>
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

      <%-- Relationship Graphs --%>
      <div>
        <a name="socialRelationships"></a>
        <p><strong><%=props.getProperty("social")%></strong></p>

      <!--begin html for display current socialunits-->
        <!-- relationships of type social unit will delete, preserve mother/calf -->

        <script type="text/javascript">
          $(document).ready(function() {

              $(document).on('click', "#editSocialMembership",function(e) {
                  e.preventDefault();
                  console.log("CLICK!");

                  toggleEditSocialGroup();
              });

              $(document).on('click', "#submitSocialMembership",function(e) {
                  e.preventDefault();
                  console.log("CLICK SUBMIT social group!");
                  createOrEditMembership();
              });

              $(document).on('click', "#deleteSocialMembership",function(e) {
                  e.preventDefault();
                  console.log("CLICK SUBMIT delete membership!");
                  deleteMembership();
                  clearSocialUnitMembershipFields();
              });
          });

          function createOrEditMembership() {
              console.log("click edit member..");
              var membershipJSON = {};

              let id = "<%=sharky.getIndividualID()%>";
              membershipJSON["miId"] = id;

              if ($("#socialGroupNameSelect").val()=="new") {
                membershipJSON["groupName"] = $("#socialGroupNameField").val();
              } else {
                membershipJSON["groupName"] = $("#socialGroupNameSelect").val();
              }

              membershipJSON["roleName"] = $("#socialRoleNameField").val();

              if ($("#socialGroupMembershipStartField").val()) {
                  let startDate = new Date($("#socialGroupMembershipStartField").val());
                  membershipJSON["startDate"] = startDate;
              }

              if ($("#socialGroupMembershipEndField").val()) {
                  let endDate = new Date($("#socialGroupMembershipEndField").val())
                  membershipJSON["endDate"] = endDate;
              }

              if (membershipJSON["groupName"].length>0) {
                $.ajax({
                    url: 'MembershipCreate',
                    type: 'POST',
                    dataType: 'json',
                    contentType: 'application/javascript',
                    data: JSON.stringify(membershipJSON),

                    success: function(d) {
                        console.info('Success! Got back '+JSON.stringify(d));
                        $("#membershipActionResponse").text("Success!");
                        clearSocialUnitMembershipFields()
                        updateSocialUnitMembershipFields(d);
                    },
                    error: function(x,y,z) {
                        $("#membershipActionResponse").text("An error has occurred.");
                        console.warn('%o %o %o', x, y, z);
                    }
                });
              } else {
                $("#membershipActionResponse").text("Valid Social Group name required.");
              }
          }

        function deleteMembership() {

            var membershipDeleteJSON = {};

            let id = "<%=sharky.getIndividualID()%>";
            membershipDeleteJSON["miId"] = id;

            if ($("#socialGroupNameSelect").val()=="new") {
              membershipDeleteJSON["groupName"] = $("#socialGroupNameField").val();
            } else {
              membershipDeleteJSON["groupName"] = $("#socialGroupNameSelect").val();
            }

            console.warn("Sending to delete???? -------> "+JSON.stringify(membershipDeleteJSON))

            if (membershipDeleteJSON["groupName"].length>0) {
              $.ajax({
                  url: 'MembershipDelete',
                  type: 'POST',
                  dataType: 'json',
                  contentType: 'application/javascript',
                  data: JSON.stringify(membershipDeleteJSON),

                  success: function(d) {
                      console.info('Success! Got back '+JSON.stringify(d));
                      $("#membershipActionResponse").text("Success in DeleteMembership!");

                      // on success remove select option, and display div. leave edit form open
                      console.log("cleaning up elements for group membership with "+d.groupName);
                      $("#displayMembership"+d.groupName).remove();
                      $("#socialGroupNameSelect option[value='"+d.groupName+"']").remove();

                      //cheat a bit to reuse the method
                      let nameSelect = {};
                      nameSelect.value = "new";
                      socialGroupNameSelectChanged(nameSelect);

                  },
                  //error: function(x,y,z) {
                    error: function(d) {
                      console.log("---> Err from MembershipDelete ajax");
                      $("#membershipActionResponse").text("An error has occurred in DeleteMembership.");
                      console.warn(JSON.stringify(d));
                  }
              });
            }
        }

        function updateSocialUnitMembershipFields(json) {

          console.log("Yet again,,,,, the JSON: "+JSON.stringify(json));

          let role = json["role"];
          if (typeof role == 'undefined') role = "";
          let groupName = json["groupName"];
          if (typeof groupName == 'undefined') groupName = "";
          let startDate = json["startDate"];
          if (typeof startDate == 'undefined') startDate = "";
          let endDate = json["endDate"];
          if (typeof endDate == 'undefined') endDate = "";

          console.log("typeof endDate: "+(typeof endDate));

          if (json.isNewSocialUnit==false) {
            // look for existing div with SU name
            console.log("looking for this social unit div to modify: displayMembership"+json.groupName);
            //$("#displayMembership"+json.groupName+" .socialGroupName").css("background-color", "red");
            $("#displayMembership"+json.groupName+" .socialGroupNameField").text(groupName);
            $("#displayMembership"+json.groupName+" .socialGroupName").html('<p class="socialGroupName"><a href="socialUnit.jsp?name='+groupName+'">'+groupName+'</a>');

            $("#displayMembership"+json.groupName+" .socialRoleNameField").text(role);
            $("#displayMembership"+json.groupName+" .socialRoleName").text(role);
            $("#displayMembership"+json.groupName+" .socialGroupMembershipStart").text(startDate);
            $("#displayMembership"+json.groupName+" .socialGroupMembershipStartField").text(startDate);
            $("#displayMembership"+json.groupName+" .socialGroupMembershipEnd").text(endDate);
            $("#displayMembership"+json.groupName+" .socialGroupMembershipEndField").text(endDate);
          } else {
            //generate and append div for new social unit
            console.log("appending div for new social unit: "+json.groupName);

            $('#socialGroupNameSelect').append('<option value="'+json.groupName+'">edit: '+json.groupName+'</option>');

            let newMembershipDiv = "";
            //newMembershipDiv+= '<br/>';
            newMembershipDiv+= '<div id="displayMembership'+json.groupName+'" class="socialUnitEditForm newMembershipFromServer socialUnitMargin">';
            newMembershipDiv+=   '<div class="form-group row" style="padding: 5px;">';

            newMembershipDiv+=     '<div class="col-xs-3 col-sm-2">';
            newMembershipDiv+=        '<label><strong><%=props.getProperty("socialGroupName") %></strong></label>';
            newMembershipDiv+=        '<p class="socialGroupName"><a href="socialUnit.jsp?name='+groupName+'">'+groupName+'</a></p>';
            newMembershipDiv+=    '</div>';

            newMembershipDiv+=    '<div class="col-xs-3 col-sm-2">';
            newMembershipDiv+=      '<label><strong><%=props.getProperty("socialRoleName") %></strong></label>';
            newMembershipDiv+=      '<p class="socialRoleName">'+role+'</p>';
            newMembershipDiv+=    '</div>';

            newMembershipDiv+=    '<div class="col-xs-3 col-sm-2">';
            newMembershipDiv+=      '<label><strong><%=props.getProperty("socialGroupMembershipStart") %></strong></label>';
            newMembershipDiv+=      '<p class="socialGroupMembershipStart">'+startDate+'</p>';
            newMembershipDiv+=    '</div>';

            newMembershipDiv+=    '<div class="col-xs-3 col-sm-2">';
            newMembershipDiv+=      '<label><strong><%=props.getProperty("socialGroupMembershipEnd") %></strong></label>';
            newMembershipDiv+=      '<p class="socialGroupMembershipEnd">'+endDate+'</p>';
            newMembershipDiv+=    '</div>';
            newMembershipDiv+=  '</div>';
            newMembershipDiv+= '</div>';

            //if (!$("#allDisplayMemberships").last().is("br")) {
            //  $("#allDisplayMemberships").append('<br/>');
            //}
            $("#allDisplayMemberships").append(newMembershipDiv);

          }
        }

        function clearSocialUnitMembershipFields() {
          $("#socialGroupNameField").val("");
          $("#socialRoleNameField").val("");
          $("#socialGroupMembershipStartField").val("");
          $("#socialGroupMembershipEndField").val("");
        }

        function socialGroupNameSelectChanged(nameSelect) {
          $("#membershipActionResponse").empty();
          console.log("changed social unit name selection in form to "+nameSelect.value);
          clearSocialUnitMembershipFields();
          if (nameSelect.value==="new") {
            $("#newSocialGroupNameInput").show();
            console.log("hiding delete button?");
            $("#deleteSocialMembership").hide();
          } else {
            // auto fill other fields
            $("#socialRoleNameField").val($("#displayMembership"+nameSelect.value+" .socialRoleName").html());
            $("#socialGroupMembershipStartField").val($("#displayMembership"+nameSelect.value+" .socialGroupMembershipStart").html());
            $("#socialGroupMembershipEndField").val($("#displayMembership"+nameSelect.value+" .socialGroupMembershipEnd").html());

            $("#newSocialGroupNameInput").val("")
            $("#newSocialGroupNameInput").hide();
            console.log("showing delete button?");
            $("#deleteSocialMembership").show();
          }
        }

        function toggleEditSocialGroup() {
            $("#membershipActionResponse").text();
            $("select option[value='new']").prop("selected",true);
            $("#newSocialGroupNameInput").show();
            $("#deleteSocialMembership").hide();
            clearSocialUnitMembershipFields();
            if ($("#editOrCreateMembership").hasClass("hidden")) {
                $("#editOrCreateMembership").removeClass("hidden");
            } else {
                $("#editOrCreateMembership").addClass("hidden");
            }
        }

        </script>

        <!-- end social unit memberships -->

        <%
        if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
        %>
        <input class="btn btn-md" type="button" name="button" id="addRelationshipBtn" value="<%=props.getProperty("addRelationship") %>">
        <%
        }
        %>

        <script type="text/javascript">
          $(document).ready(function() {
              $("#addRelationshipBtn").click(function() {
              $("#addRelationshipForm").show();
              resetForm($('#setRelationship'), "<%=sharky.getIndividualID()%>");
              $("#setRelationshipResultDiv").hide();
          });

          $("#closeRelationshipForm").click(function() {
              $("#addRelationshipForm").hide();
          });

          $("#EditRELATIONSHIP").click(function(event) {
              event.preventDefault();

              var persistenceID = "";
              var relationshipID = $("#inputPersistenceID").val();
              if ((relationshipID != null) && (relationshipID != "")) {
                  //persistenceID = relationshipID;
            	  persistenceID = relationshipID + "[OID]org.ecocean.social.Relationship";

              }
              var type = $("#type").val();
              var markedIndividualName1 = $("#individual1").val();
	            console.log("editRELATIONSHIP indy.jsp : " + markedIndividualName1);
              console.log(markedIndividualName2);
              var markedIndividualRole1 = $("#role1").val();
              var markedIndividualName2 = $("#individual2").val();
              var markedIndividualRole2 = $("#role2").val();
              var relatedCommunityName = $("#relatedCommunityName").val();
              var startTime = $("#startTime").val();
              var endTime = $("#endTime").val();
              var markedIndividual1DirectionalDescriptor = $("#descriptor1").val();
              var markedIndividual2DirectionalDescriptor = $("#descriptor2").val();
              var bidirectional = $("#bidirectional").val();

	            if (startTime == "-1") {
                 startTime = "";
              }
              if (endTime == "-1") {
                 endTime = "";
              }

   	          console.log("persistenceID sent to encounter-calls: " + persistenceID + " relationshipID: "+ relationshipID );
              $.post("RelationshipCreate", {
                  "persistenceID": persistenceID,
                  "type": type,
                  "markedIndividualName1": markedIndividualName1,
                  "markedIndividualRole1": markedIndividualRole1,
                  "markedIndividualName2": markedIndividualName2,
                  "markedIndividualRole2": markedIndividualRole2,
                  "relatedCommunityName": relatedCommunityName,
                  "startTime": startTime,
                  "endTime": endTime,
                  "markedIndividual1DirectionalDescriptor": markedIndividual1DirectionalDescriptor,
                  "markedIndividual2DirectionalDescriptor": markedIndividual2DirectionalDescriptor,
                  "bidirectional": bidirectional
              },

              function(response) {
                  window.location.reload(true);
                  $("#relationshipErrorDiv").empty();
                  $("#addRelationshipForm").hide();
                  <% String relationshipIndividualID = sharky.getIndividualID();%>

                  getRelationshipTableData("<%=relationshipIndividualID%>");

                  $("#communityTable").empty();
                  $("#communityTable").html("<table id='relationshipTable' class='table table-bordered table-sm table-striped'><thead id='relationshipHead'></thead><tbody id='relationshipBody'></tbody></table>");
              }).fail(function(response) {
                  console.log("Relationship update failure!");
                  $("#setRelationshipResultDiv").show();
                  $("#relationshipErrorDiv").html(response.responseText);
                  $("#relationshipSuccessDiv").empty();
                  $("#addRelationshipForm").hide();
              });
            });

          });
        </script>

        <%
        if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
        %>
          <div id="setRelationshipResultDiv">
            <span class="highlight" id="relationshipErrorDiv"></span>
            <span class="successHighlight" id="relationshipSuccessDiv"></span>
          </div>
          <div id="addRelationshipForm">

          <h4><%=props.getProperty("setRelationship")%></h4>

          <form id="setRelationship">
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

                %>

            <div class="form-group row">
              <div  class="col-xs-3 col-sm-2">
                <label class="requiredLabel"><%=props.getProperty("type")%></label>
                <p class="highlight"><small><%=props.getProperty("required")%></small></p>
              </div>
              <div class="col-xs-9 col-sm-3">
                <select required name="type" class="form-control relationshipInput" id="type">
                  <%
                    String indID = sharky.getIndividualID();
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
              </div>
            </div>
            <div class="form-group row">
              <div class="col-xs-3 col-sm-2">
                <label class="requiredLabel"><%=props.getProperty("individualID1")%></label>
                <p><small class="highlight"><%=props.getProperty("required")%></small></p>
              </div>
              <div class="col-xs-9 col-sm-3">
                <p id="individual1set"><%=sharky.getIndividualID()%></p>
                <input required class="form-control relationshipInput" type="text" value="<%=indID%>" id="individual1" placeholder="<%=indID%>"/>
              </div>

            </div>
            <div class="form-group row">
              <div class="col-xs-3 col-sm-2">
                <label class="requiredLabel"><%=props.getProperty("individualRole1")%></label>
                <p class="highlight"><small><%=props.getProperty("required")%></small></p>
              </div>
              <div class="col-xs-9 col-sm-3">
                <select id="role1" required class="form-control relationshipInput" name="markedIndividualRole1">
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
              </div>
              <div class="col-xs-2 col-sm-1">
                <label><%=props.getProperty("markedIndividual1DirectionalDescriptor")%></label>
              </div>
              <div class="col-xs-7 col-sm-3">
                <input id="descriptor1" class="form-control relationshipInput" name="markedIndividual1DirectionalDescriptor" type="text" value="<%=markedIndividual1DirectionalDescriptor%>" placeholder="<%=props.getProperty("markedIndividual1DirectionalDescriptor")%>"/>
              </div>
            </div>
            <div class="form-group row">
              <div class="col-xs-3 col-sm-2">
                <label><%=props.getProperty("individualID2")%></label>
              </div>
              <div class="col-xs-9 col-sm-3">
                <input class="form-control relationshipInput" type="text" id="individual2" placeholder="<%=props.getProperty("individualID2")%>"/>
              </div>
            </div>
            <div class="form-group row">
              <div class="col-xs-3 col-sm-2">
                <label class="requiredLabel"><%=props.getProperty("individualRole2")%></label>
                <p class="highlight"><small><%=props.getProperty("required")%></small></p>
              </div>
              <div class="col-xs-9 col-sm-3">
                <select id="role2" required class="form-control relationshipInput" name="markedIndividualRole2">
                  <%
                  for(int g=0;g<numRoles;g++){

                    String selectedText="";
                    if(markedIndividual2Role.equals(roles.get(g))){selectedText="selected=\"selected\"";}
                    %>
                    <option <%=selectedText%>><%=roles.get(g)%></option>
                    <%
                  }
                  %>
                </select>
              </div>
              <div class="col-xs-2 col-sm-1">
                <label><%=props.getProperty("markedIndividual2DirectionalDescriptor")%></label>
              </div>
              <div class="col-xs-7 col-sm-3">
                <input id="descriptor2" class="form-control relationshipInput" name="markedIndividual2DirectionalDescriptor" type="text" value="<%=markedIndividual2DirectionalDescriptor%>" placeholder="<%=props.getProperty("markedIndividual2DirectionalDescriptor")%>"/>
              </div>
            </div>
            <div class="form-group row">
              <div class="col-xs-3 col-sm-2">
                <label><%=props.getProperty("relatedCommunityName")%></label>
              </div>
              <div class="col-xs-9 col-sm-3">
                <input id="relatedCommunityName" class="form-control relationshipInput" name="relatedCommunityName" type="text" value="<%=communityName%>" placeholder="<%=props.getProperty("relatedCommunityName")%>"/>
              </div>
            </div>
            <div class="form-group row">
              <div class="col-xs-3 col-sm-2">
                <label><%=props.getProperty("startTime")%></label>
              </div>
              <div class="col-xs-9 col-sm-3">
                <input id="startTime" class="form-control relationshipInput" name="startTime" type="text" value="<%=startTime%>" placeholder="YYYY-MM-DD"/>
           	<p style="font-size:0.6em;">YYYY-MM-DD</p>
	    </div>
            </div>
            <div class="form-group row">
              <div class="col-xs-3 col-sm-2">
                <label><%=props.getProperty("endTime")%></label>
              </div>
              <div class="col-xs-9 col-sm-3">

               <input id="endTime" class="form-control relationshipInput" name="endTime" type="text" size="20" maxlength="100" value="<%=endTime%>" placeholder="YYYY-MM-DD"/>
	       <p style="font-size:0.6em;">YYYY-MM-DD</p>
	      </div>
            </div>
            <div class="form-group row">
              <div class="col-xs-3 col-sm-2">
                <label><%=props.getProperty("bidirectional")%></label>
              </div>
              <div class="col-xs-9 col-sm-3">
                <select id="bidirectional" name="bidirectional" class="form-control relationshipInput">
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
		<input id="inputPersistenceID" class="form-control persistenceID" name="persistenceID" type="hidden" value="">
              </div>
            </div>
            <input class="btn btn-md" name="EditRELATIONSHIP" type="submit" id="EditRELATIONSHIP" value="<%=props.getProperty("update") %>">
            <input class="btn btn-md" type="button" id="closeRelationshipForm" value="Cancel">
          </form>

          		<script type="text/javascript">
	                    $(document).ready(function() {

	                    	//set autocomplete on #individualAddEncounterInput above
	                    	setIndivAutocomplete($('#individual2'));



	                    });
                </script>

        </div>

        <%
          	}

          %>

        <div role="navigation" id="socialNavigation">
          <ul class="nav nav-tabs">
	    <li id="socialDiagramTab" class="active socialVisTab">
	      <a href="#socialDiagram">Social Diagram</a>
	    </li>
            <li id="communityTableTab" class="socialVisTab">
              <a href="#communityTable"><%=props.getProperty("social")%> Table</a>
            </li>
          </ul>
        </div>

	<div id="socialDiagram" class="socialVis">
	  <div id="familyChart">
	    <div id="graphFilters">
	      <div id="graphOptions">
    	        <button type="button" id="reset">Reset Filters</button>
	        <button type="button" id="gZoomIn">Zoom In</button>
	        <button type="button" id="gZoomOut">Zoom Out</button>
	      </div>
	      <div id="filterGender" class="filterOptions">
	        <label>
	          <input type="checkbox" id="maleBox">
	          <span>Male</span>
	          </label>
	        <label>
	          <input type="checkbox" id="femaleBox">
	          <span>Female</span>
	        </label>
	        <label>
	          <input type="checkbox" id="unknownGenderBox">
	          <span>Unknown Gender</span>
	        </label>
	      </div>
	      <div id="filterSocialRole" class="filterOptions">
	        <label>
	          <input type="checkbox" id="alphaBox">
	          <span>Alpha</span>
	        </label>
	        <label>
	          <input type="checkbox" id="unknownRoleBox">
	          <span>Unknown Role</span>
	        </label>
	      </div>
	      <div class="filterOptions">
	        <label>
	          <input type="checkbox" id="selectFamilyBox">
	          <span>Select Family</span>
	        </label>
                <label>
	          <input type="checkbox" id="filterFamilyBox">
	          <span>Filter Family</span>
	        </label>
	      </div>
	    </div>
	    <div class="loadingIcon">
	      <img src="loadingSpinner.svg">
            </div>
	  </div>

          <div class="graphSliders">
      	    <div class="sliderWrapper">
	      <label for="nodeCount"> Nodes Displayed (Count) - <span class="sliderLabel" id="nodeCountVal"></span></label>
	      <input type="range" min=0 class="graphSlider" id="nodeCount">
	    </div>
	    <div class="sliderWrapper">
	      <label for="nodeDist"> Node Distance (Geodesic) - <span class="sliderLabel" id="nodeDistVal"></span></label>
	      <input type="range" min=0 class="graphSlider" id="nodeDist">
	    </div>
          </div>
	  <% String individualID = sharky.getIndividualID();%>
	</div>

        <%
        if (!(isOwner && CommonConfiguration.isCatalogEditable(context))) {
        %>

        <script type="text/javascript">
          setTimeout(function() {
            $("#relationshipTable td:nth-child(5)").attr("class", "hide");
            $("#relationshipTable th:nth-child(5)").attr("class", "hide");
            $("#relationshipTable td:nth-child(6)").attr("class", "hide");
            $("#relationshipTable th:nth-child(6)").attr("class", "hide");
          }, 5000)
        </script>

        <%
        }
        %>

        <script type="text/javascript">
          getRelationshipTableData("<%=individualID%>");

          $(document).ready(function() {
            setTimeout(function() {
              var deletedMarkedIndividualName2 = "";
              var deletedType = "";
              $(document).on('click', '.editRelationshipBtn', function (event) {
                $("#setRelationshipResultDiv").hide();
                var relationshipID = event.target.value;
                var persistenceID = relationshipID + "[OID]org.ecocean.social.Relationship";

                getRelationshipData(relationshipID);
		$("#inputPersistenceID").val(relationshipID);
		$("#individual1").val("<%=individualID%>");
              });

              $(document).on('click', '.deleteRelationshipBtn', function () {
                var relationshipID = ($(this).attr("value"));
                $("#addRelationshipForm").hide();
                $("#remove" + relationshipID).hide();
                $("div[value='" + relationshipID + "']").show();
              });

              $(document).on('click', '.yesDelete', function(event) {
                event.preventDefault();

                var $row = $(this).closest("tr"),
                    $td1 = $row.find("td:nth-child(2)"),
                    $td2 = $row.find("td:nth-child(3)");

                $.each($td1, function() {
                    var tableData = ($(this).text().match(/[A-Z][a-z]+|[0-9]+/g));
                    deletedMarkedIndividualName2 = tableData[0];
                });

                $.each($td2, function() {
                  deletedType = ($(this).text());
                });

                var relationshipID = ($(this).attr("value"));
                var persistenceID = persistenceID = relationshipID + "[OID]org.ecocean.social.Relationship";
                var deletedMarkedIndividualName1 = "<%=individualID%>";
                $("div[value='" + relationshipID + "']").hide();
                $("#remove" + relationshipID).show();

                $.post("RelationshipDelete", {"persistenceID": persistenceID, "markedIndividualName1": deletedMarkedIndividualName1, "markedIndividualName2": deletedMarkedIndividualName2, "type": deletedType},
                function(response) {
		  window.location.reload(true);
                  $("#communityTable").empty();
                  $("#communityTable").html("<table id='relationshipTable' class='table table-bordered table-sm table-striped'><thead id='relationshipHead'></thead><tbody id='relationshipBody'></tbody></table>");
                  getRelationshipTableData("<%=individualID%>");
                  $("#setRelationshipResultDiv").show();
                  $("#relationshipSuccessDiv").html(response);
                })
                .fail(function(response) {
                  $("#setRelationshipResultDiv").show();
                  $("#relationshipErrorDiv").html(response.responseText);
                });
              });

              $(".cancelDelete").click(function() {
                var relationshipID = ($(this).attr("value"));
                $("div[value='" + relationshipID + "']").hide();
                $("#remove" + relationshipID).show();
              });
            }, 5000);
          });
        </script>

        <div id="communityTable" class="mygrid-wrapper-div socialVis">
          <table id="relationshipTable" class="table table-bordered table-sm table-striped">
              <thead id="relationshipHead"></thead>
              <tbody id="relationshipBody"></tbody>
          </table>
        </div>
        <br/>

        <br>
        <%-- Cooccurrence table starts here --%>
        <a name="cooccurrence"></a>
	<p><strong><%=props.getProperty("cooccurrence")%></strong></p>
	<script type="text/javascript">
        <% String occurrenceIndividualID = sharky.getIndividualID();%>
        <%



        String individualGenus = sharky.getGenus();
		String individualEpithet = sharky.getSpecificEpithet();
		if(individualGenus == null || individualEpithet==null){
			if(sharky.getGenusSpeciesDeep()!=null){

				StringTokenizer str=new StringTokenizer(sharky.getGenusSpeciesDeep()," ");
				if(str.hasMoreTokens()){individualGenus=str.nextToken();}
				if(str.hasMoreTokens()){individualEpithet=str.nextToken();}
			}
		}

		%>

        $(document).ready(function() {
          getData("<%=occurrenceIndividualID%>", "<%=sharky.getDisplayName(request, myShepherd) %>");
        });
        </script>

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
            <div id="bubbleChart">
	      <div id="graphFilters">
	      	<div id="graphOptions">
    	          <button type="button" id="reset">Reset Filters</button>
	          <button type="button" id="gZoomIn">Zoom In</button>
	          <button type="button" id="gZoomOut">Zoom Out</button>
	        </div>
	        <div id="filterGender" class="filterOptions">
	          <label>
	            <input type="checkbox" id="maleBox">
	            <span>Male</span>
	            </label>
	          <label>
	            <input type="checkbox" id="femaleBox">
	            <span>Female</span>
	          </label>
	          <label>
	            <input type="checkbox" id="unknownGenderBox">
	            <span>Unknown Gender</span>
	          </label>
	        </div>
	        <div id="filterSocialRole" class="filterOptions">
	          <label>
	            <input type="checkbox" id="alphaBox">
	            <span>Alpha</span>
	          </label>
	          <label>
	            <input type="checkbox" id="unknownRoleBox">
	            <span>Unknown Role</span>
	          </label>
	        </div>
	        <div class="filterOptions">
	          <label>
	            <input type="checkbox" id="selectFamilyBox">
	            <span>Select Family</span>
	          </label>
                  <label>
	            <input type="checkbox" id="filterFamilyBox">
	            <span>Filter Family</span>
	          </label>
	        </div>
	      </div>
	      <div class="loadingIcon">
	        <img src="loadingSpinner.svg">
              </div>
            </div>
	    <div class="graphSliders">
      	      <div class="coOccurrenceSliderWrapper">
	        <label for="nodeCount"> Nodes Displayed (Count) - <span class="sliderLabel" id="nodeCountVal"></span></label>
	  	<input type="range" min=0 class="graphSlider" id="nodeCount">
      	      </div>
    	    </div>
	      <script type="text/javascript">
	        let querier = new JSONQuerier(wildbookGlobals);
	        querier.preFetchData("<%=occurrenceIndividualID%>", "<%=individualGenus%>", "<%=individualEpithet%>",
			     [setupSocialGraph, setupOccurrenceGraph],
			     ["#socialDiagram", "#bubbleChart"]);
            </script>
          </div>

          <div id="cooccurrenceTable" class="table-responsive mygrid-wrapper-div">
            <table id="coTable" class="table table-striped table-bordered table-sm table-hover">
                <thead id="coHead"></thead>
                <tbody id="coBody"></tbody>
            </table>
          </div>
        </div>

        </td>
        </tr>


        </table>

        </td>
        </tr>
        </table>
      </div>
      <%-- End of Relationship Graphs --%>
      <br>


      <%-- Map --%>
      <br>
      <div>
        <jsp:include page="individualMapEmbed.jsp" flush="true">
          <jsp:param name="name" value="<%=id%>"/>
        </jsp:include>
      </div>
      <br>
      <%-- End of Map --%>
      <%
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
            <td><a href="/<%=CommonConfiguration.getDataDirectoryName(context) %>/individuals/<%=sharky.getId()%>/<%=file_name%>"><%=file_name%>
            </a></td>
            <td>&nbsp;&nbsp;&nbsp;[<a
              href="IndividualRemoveDataFile?individual=<%=id%>&filename=<%=file_name%>"><%=delete %>
            </a>]
            </td>
          </tr>

          <%}%>
        </table>
        <%}
        else {
        	%>
        	<%=none %></p>
        	<%
          }
        if (CommonConfiguration.isCatalogEditable(context)) {
        %>
        <form action="IndividualAddFile" method="post" enctype="multipart/form-data" name="addDataFiles">
        	<input name="action" type="hidden" value="fileadder" id="action">
          	<input name="individual" type="hidden" value="<%=sharky.getId()%>" id="individual">
          	<p><%=addDataFile %>:</p>
          	<p><input name="file2add" type="file" size="50"></p>
          	<p><input name="addtlFile" type="submit" id="addtlFile" value="<%=sendFile %>"></p>
         </form>
         <br>
        <%
          }




          }
		%>


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
                    String profilePhotoURL="images/user-profile-grey-grey.png";
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
          <div id='commentBoard' style="text-align:left;border:1px solid lightgray;width:100%;height:250px;overflow-y:scroll;overflow-x:scroll;border-radius:5px;">
            <p><%=sharky.getComments().replaceAll("\n", "<br>")%></p>
          </div>
          <%
          if (CommonConfiguration.isCatalogEditable(context) && isOwner) {
            %>
            <p>
              <form name="addComments">
                <input name="user" type="hidden" value="<%=request.getRemoteUser()%>" id="user">
                <input name="individual" type="hidden" value="<%=sharky.getId()%>" id="individual">
                <input name="action" type="hidden" value="comments" id="action">

                  <p><textarea name="comments" cols="60" id="comments" class="form-control" rows="3" style="width: 100%"></textarea> <br />
                  <input name="Submit" type="submit" value="<%=addComments %>" id="addCommentsBtn">
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
      <script type = "text/javascript">
          $(window).on('load', function() {
              $("#addCommentsBtn").click(function(event) {
                  event.preventDefault();

                  $("#addCommentsBtn").hide();

                  var addCommentUser = $("#user").val();
                  var addCommentIndividual = $("#individual").val();
                  var addCommentAction = $("#action").val();
                  var addCommentComment = $("#comments").val();

                  $.post("IndividualAddComment", {"user": addCommentUser, "individual": addCommentIndividual, "action": addCommentAction, "comments": addCommentComment},
                  function(response) {

                      $("#comments").val("");
                      let match = response.match(/(<p>.*<\/p>)/);
                      if (match) {
                          let newComment = match[1];
                          $("#commentBoard").append(newComment);
                      } else {
                          console.log('Incorrectly Formatted Comment')
                      }

                  }).fail(function(response) {

                      console.log(response);

                  })

                  $("#addCommentsBtn").show();

              })
          })
      </script>
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

   <script type="text/javascript">
		function encountTableAuxClick(el) {
			goToEncounterURL(el.getAttribute("class"));
			return false;
		}
		function cooccurTableAuxClick(el) {
			goToWhaleURL(el.getAttribute("class"));
    		return false;
		}
   </script>

</div>

  <%
  }

  //could not find the specified individual!
  else {
	    %>
	    <p><%=matchingRecord %> <strong><%=id%></strong></p>
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

<%
  } //end if ! ?number=
%>
