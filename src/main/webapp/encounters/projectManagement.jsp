<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,
         java.io.File,
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
Properties projProps = new Properties();
projProps=ShepherdProperties.getProperties("searchResults.properties", langCode, context);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("searchResults.jsp");
User currentUser = myShepherd.getUser(request);
if(currentUser == null){
  System.out.println("oh she null");
}
// System.out.println("currentUser is " + currentUser.getUsername());
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
<style>
  
</style>
<div class="container maincontent">
  <h1 class="intro"><%=projProps.getProperty("title")%></h1>
<%
String queryString="";
if(request.getQueryString()!=null){queryString=request.getQueryString();}
%>
<ul id="tabmenu">
  <li><a><%=projProps.getProperty("table")%>
  </a></li>
  <li><a class="active"
    href="projectManagement.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=projProps.getProperty("projectManagement")%>
  </a></li>
  <li><a
    href="thumbnailSearchResults.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=projProps.getProperty("matchingImages")%>
  </a></li>
  <li><a
    href="mappedSearchResults.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=projProps.getProperty("mappedResults")%>
  </a></li>
  <li><a
    href="../xcalendar/calendar.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=projProps.getProperty("resultsCalendar")%>
  </a></li>
        <li><a
     href="searchResultsAnalysis.jsp?<%=queryString %>"><%=projProps.getProperty("analysis")%>
   </a></li>
      <li><a
     href="exportSearchResults.jsp?<%=queryString %>"><%=projProps.getProperty("export")%>
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
  }
  %>
  <p>Encounters that will be added: <strong><%= encountersUserCanAdd.size()%></strong></p>
  </br>
  <p>Encounters that you cannot add to the project: <strong><%= encountersUserCannotAdd.size()%></strong></p>
  <form id="add-encounter-to-project-form"
  method="post"
  enctype="multipart/form-data"
  name="add-encounter-to-project-form"
  action="../ProjectUpdate"
  accept-charset="UTF-8">
  <div class="row flexbox" id="project-list-container">
  <%
  if(currentUser != null){
    System.out.println("currentUser not null");
    FormUtilities.setUpProjectDropdown(6,"Select Projects To Add To","id", projProps, out, request, myShepherd);

    List<Project> projects = myShepherd.getProjectsForUserId(currentUser.getUUID());
    if(projects != null && projects.size()>0){
      System.out.println("projects not null");
      %>
      <div class="col-xs-12 col-sm-12 col-md-6 col-lg-6 bump-down flex-left-justify">
        <table class="row tissueSample alernatingRows">
        <strong>Encounters that are already in project:</strong>
          <thead>
            <tr>
              <th>Project Name</th>
              <th>Number of Encounters Already In Project</th>
            </tr>
          </thead>
          <tbody>
      <%
      int encounterAlreadyInProjectCounter = 0;
      List<Integer> encounterCountsAlreadyInProject = new ArrayList<Integer>();
      for(int i=0; i<projects.size(); i++){
        encounterAlreadyInProjectCounter = 0;
        Project currentProject = projects.get(i);
        List<Encounter> currentEncounters = currentProject.getEncounters();
        for(int j=0; j<currentEncounters.size(); j++){
          if(encountersUserCanAdd.contains(currentEncounters.get(j))){
            encounterAlreadyInProjectCounter ++;
          }
        }
        %>
        <tr>
          <td><%= currentProject.getResearchProjectName() %></td>
          <td><%= encounterAlreadyInProjectCounter%></td>
        <tr>
        <%
        encounterCountsAlreadyInProject.add(encounterAlreadyInProjectCounter);
      }
      %>
            </tbody>
          </table>
        </div>
      <%
    }
    %>
    </div>
    <button type="button" id="add-project-button" onclick="addProjects();">Add to Project(s) <span class="glyphicon glyphicon-plus"><span></button>
    </form>
    <%
  }
  System.out.println("got past getting encounters");
}catch(Exception e){e.printStackTrace();}
%>
<div id="alert-div" class="alert alert-success" role="alert">
  <button type="button" class="close" onclick="dismissAlert()" aria-label="Close"><span aria-hidden="true">&times;</span></button>
  <strong>Success!</strong> Encounters have been added to project(s)!
</div>
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
<script>
function dismissAlert(){
  console.log("dismissAlert entered");
  $('#alert-div').hide();
}
function addProjects(){
  console.log("addUserToProject clicked!");
  // $('#alert-div').show(); //TODO remove me
  let formDataArray = $("#add-encounter-to-project-form").serializeArray();
  let formJson = {};
  console.log("formDataArray is");
  console.log(formDataArray);
  for(i=0; i<formDataArray.length; i++){
    let currentName = formDataArray[i].name;
    if (Object.values(formDataArray[i])[0] === "Select Projects To Add To"){
      formJson[currentName].push(formDataArray[i].value);
    }else{
      formJson[currentName]=formDataArray[i].value;
    }
  }
  // ProjectUpdate.addOrRemoveEncountersFromProject(project, myShepherd, encountersToAddJSONArr, "add"); //TODO
  $.ajax({
    url: wildbookGlobals.baseUrl + '../ProjectUpdate',
    type: 'POST',
    data: JSON.stringify(formJson),
    dataType: 'json',
    contentType : 'application/json',
    success: function(data){
      console.log(data);
      //TODO make success div visible
      $('#alert-div').show();
    },
    error: function(x,y,z) {
      console.warn('%o %o %o', x, y, z);
    }
  });
}
</script>
