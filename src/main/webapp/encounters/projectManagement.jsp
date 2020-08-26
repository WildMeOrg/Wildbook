<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,
         org.ecocean.servlet.ServletUtilities, java.io.File,
         java.io.FileOutputStream, java.io.OutputStreamWriter,
         java.util.*, org.datanucleus.api.rest.orgjson.JSONArray,
         org.json.JSONObject, org.datanucleus.api.rest.RESTUtils,
         org.datanucleus.api.jdo.JDOPersistenceManager,
         java.nio.charset.StandardCharsets,
         java.net.URLEncoder " %>

<%
String context="context0";
context=ServletUtilities.getContext(request);
String langCode=ServletUtilities.getLanguageCode(request);
Properties projprops = new Properties();
projprops=ShepherdProperties.getProperties("searchResults.properties", langCode, context);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("searchResults.jsp");
try{
  //--let's estimate the number of results that might be unique
  Integer numUniqueEncounters = null;
  Integer numUnidentifiedEncounters = null;
  Integer numDuplicateEncounters = null;
%>

<jsp:include page="../header.jsp" flush="true"/>
<script src="../javascript/underscore-min.js"></script>
<script src="../javascript/backbone-min.js"></script>
<script src="../javascript/core.js"></script>
<script src="../javascript/classes/Base.js"></script>
<link rel="stylesheet" href="../javascript/tablesorter/themes/blue/style.css" type="text/css" media="print, projection, screen" />
<link rel="stylesheet" href="../css/pageableTable.css" />
<script src="../javascript/tsrt.js"></script>
<div class="container maincontent">
  <h1 class="intro"><%=projprops.getProperty("title")%></h1>
<%
String queryString="";
if(request.getQueryString()!=null){queryString=request.getQueryString();}
%>
<ul id="tabmenu">
  <li><a><%=projprops.getProperty("table")%>
  </a></li>
  <li><a class="active"
    href="projectManagement.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=projprops.getProperty("projectManagement")%>
  </a></li>
  <li><a
    href="thumbnailSearchResults.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=projprops.getProperty("matchingImages")%>
  </a></li>
  <li><a
    href="mappedSearchResults.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=projprops.getProperty("mappedResults")%>
  </a></li>
  <li><a
    href="../xcalendar/calendar.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=projprops.getProperty("resultsCalendar")%>
  </a></li>
        <li><a
     href="searchResultsAnalysis.jsp?<%=queryString %>"><%=projprops.getProperty("analysis")%>
   </a></li>
      <li><a
     href="exportSearchResults.jsp?<%=queryString %>"><%=projprops.getProperty("export")%>
   </a></li>
</ul>

<script type="text/javascript">
	var needIAStatus = false;
<%
	String encsJson = "false";
  StringBuffer prettyPrint=new StringBuffer("");
  Map<String,Object> paramMap = new HashMap<String, Object>();
  String filter=EncounterQueryProcessor.queryStringBuilder(request, prettyPrint, paramMap);
%>
var searchResults = <%=encsJson%>;
var jdoql = '<%= URLEncoder.encode(filter,StandardCharsets.UTF_8.toString()) %>';
var howMany = 10;
var start = 0;
var results = [];
var encs;
$(document).ready( function() {
	wildbook.init(function() {
		encs = new wildbook.Collection.Encounters();
		encs.fetch({
			fetch: "searchResults",
			noDecorate: true,
			jdoql: jdoql,
			success: function() {
        searchResults = encs.models;
        console.log("searchResults are:");
        console.log(searchResults);
        //TODO then do things
      },
		});
	});
});
</script>
<%
try{
  String order ="catalogNumber ASC NULLS LAST";
  EncounterQueryResult result = EncounterQueryProcessor.processQuery(myShepherd, request, order);
  System.out.println("got past EncounterQueryProcessor");
  List<Encounter> encounters = result.getResult();
  List<Encounter> encountersUserCanAdd = new ArrayList<Encounter>();
  List<Encounter> encountersUserCannotAdd = new ArrayList<Encounter>();
  if(encounters != null && encounters.size()>0){
    for(int i=0; i< encounters.size(); i++){
      Encounter currentEncounter = encounters.get(i);
      if(ServletUtilities.isUserAuthorizedForEncounter(currentEncounter, request) == true){
        encountersUserCanAdd.add(currentEncounter);
      } else{
          encountersUserCannotAdd.add(currentEncounter);
      }
    }
    // List<Project> projects = Shepherd.getProjectsForUserId();
    // FormUtilities.setUpProjectDropdown();
  }
  %>
  <p>Encounters that will be added: <%= encountersUserCanAdd.size()%></p>
  <p>Encounters that you cannot add to the project: <%= encountersUserCannotAdd.size()%></p>
  <%
  System.out.println("got past getting encounters");
}catch(Exception e){e.printStackTrace();}
%>
</div>
<%
  }
  catch(Exception e){e.printStackTrace();}
  finally{
	  myShepherd.rollbackDBTransaction();
	  myShepherd.closeDBTransaction();
  }
%>

<jsp:include page="../footer.jsp" flush="true"/>
