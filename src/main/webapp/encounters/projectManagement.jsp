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
}
// System.out.println("currentUser is " + currentUser.getUsername());
try{
  //--let's estimate the number of results that might be unique
  Integer numUniqueEncounters = null;
  Integer numUnidentifiedEncounters = null;
  Integer numDuplicateEncounters = null;
%>
<style type="text/css">
</style>

<jsp:include page="../header.jsp" flush="true"/>
<script src="../javascript/underscore-min.js"></script>
<script src="../javascript/backbone-min.js"></script>
<script src="../javascript/core.js"></script>
<script src="../javascript/classes/Base.js"></script>
<link rel="stylesheet" href="../javascript/tablesorter/themes/blue/style.css" type="text/css" media="print, projection, screen" />
<link rel="stylesheet" href="../css/pageableTable.css" />
<script src="../javascript/tsrt.js"></script>
<div class="container maincontent">
  <h1 class="intro"><%=projProps.getProperty("title")%></h1>
<%
String queryString="";
if(request.getQueryString()!=null){queryString=request.getQueryString();}
%>
<ul id="tabmenu">
  <li><a
    href="searchResults.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=projProps.getProperty("table")%>
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
  <div class="padded-from-the-top">
    <p>Encounters that will be added: <strong><%= encountersUserCanAdd.size()%></strong></p>
    </br>
    <p>Encounters that you cannot add to the project: <strong><%= encountersUserCannotAdd.size()%></strong></p>
  </div>
  <form id="add-encounter-to-project-form"
  method="post"
  enctype="multipart/form-data"
  name="add-encounter-to-project-form"
  action="../ProjectUpdate"
  accept-charset="UTF-8">
  <div class="row flexbox padded-for-the-gods" id="project-list-container">
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
          if(encounters.contains(currentEncounters.get(j))){
            encounterAlreadyInProjectCounter ++;
          }
        }
        %>
        <tr>
          <td><%= currentProject.getResearchProjectName() %></td>
          <td id="<%= currentProject.getId()%>"><%= encounterAlreadyInProjectCounter%></td>
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
    <button  class="disabled-btn" id="disabled-add-project-button" style="display: none;">Add to Project(s) <span class="glyphicon glyphicon-plus"><span></button>
    </form>
    <%
  }
  System.out.println("got past getting encounters");
}catch(Exception e){e.printStackTrace();}
%>
  <div id="adding-div" class="alert alert-info" role="alert" style="display: none;">
    Adding Encounters... Please Wait for Confirmation.
  </div>
  <div id="empty-form-div" class="alert alert-warning" role="alert" style="display: none;">
    <button type="button" class="close" onclick="dismissAlert()" aria-label="Close"><span aria-hidden="true">&times;</span></button>
    You did not select any projects to add encounters to!
  </div>
  <div id="alert-div" class="alert alert-success" role="alert" style="display: none;">
    <button type="button" class="close" onclick="dismissAlert()" aria-label="Close"><span aria-hidden="true">&times;</span></button>
    <strong>Success!</strong> Encounters have been added to project(s)! See your projects <a href="/projects/projectList.jsp">here</a>
  </div>
  <div id="alert-div-warn" class="alert alert-danger" role="alert" style="display: none;">
    <button type="button" class="close" onclick="dismissAlert()" aria-label="Close"><span aria-hidden="true">&times;</span></button>
    Encounters were not added to project(s)!
  </div>
</div>
<script>
// $('.#add-project-button').on('click',function() {
//     $(this).prop("disabled",true);
// });
function dismissAlert(){
  $('#alert-div').hide();
  $('#alert-div-warn').hide();
  $('#empty-form-div').hide();
}

function addProjects(){
  disableAddButton();
  $('#adding-div').show();
  let formDataArray = $("#add-encounter-to-project-form").serializeArray();
  console.log(formDataArray);
  if(formDataArray.length==1 && formDataArray[0].value ==="None"){
    console.log("formDataArray is empty!")
    $('#adding-div').hide();
    $('#empty-form-div').show();
    enableAddButton();
  }
  let formJson = {};
  formJson["projects"] = [];
  for(i=0; i<formDataArray.length; i++){
    let currentName = formDataArray[i].name;
    if (currentName === "id"){
      let currentProjId = formDataArray[i].value;
      formJson = constructProjectObjJsonFromIdAndAddToJsonArray(currentProjId, formJson);
    }else{
      console.log("ack I shouldn't get here!!!!!!!!!!!!!!!!!!");
    }
  }
  doAjaxCall(formJson);
}

function doAjaxCall(formJson){
  console.log("doAjaxCall entered");
  $.ajax({
    url: wildbookGlobals.baseUrl + '../ProjectUpdate',
    type: 'POST',
    data: JSON.stringify(formJson),
    dataType: 'json',
    contentType : 'application/json',
    success: function(data){
      console.log("got into data return");
      let modifiedStatus = data["modified"];
      if(modifiedStatus){
        updateEncountersAddedInDom(data);
        $('#adding-div').hide();
        enableAddButton();
        $('#alert-div').show();
      }
      if(!modifiedStatus){
        $('#adding-div').hide();
        enableAddButton();
        $('#alert-div-warn').show();
      }
    },
    error: function(x,y,z) {
      console.warn('%o %o %o', x, y, z);
    }
  });
}

function enableAddButton(){
  console.log("enableAddButton entered");
  $('#disabled-add-project-button').hide();
  $('#add-project-button').show();
}

function disableAddButton(){
  console.log("disableAddButton entered");
  $('#add-project-button').hide();
  $('#disabled-add-project-button').show();
}

function updateEncountersAddedInDom(data){
  let formDataArray = $("#add-encounter-to-project-form").serializeArray();
  if(formDataArray){
    for(i=0; i<formDataArray.length; i++){
        let currentName = formDataArray[i].name;
        if (currentName === "id"){
          let currentProjId = formDataArray[i].value;
          let currentCount = parseInt(data["encountersAddedForProj_" + currentProjId]);
          let currentNumber = parseInt($('#'+ currentProjId).text());
          $('#'+currentProjId).html(currentNumber+ currentCount);
        }else{
          console.log("ack I shouldn't get here!!!!!!!!!!!!!!!!!!");
        }
    }
  }
}

function constructProjectObjJsonFromIdAndAddToJsonArray(projectUuid, formJson){
  let singleProjObj = {id: projectUuid};
  singleProjObj["encountersToAdd"]=[];
  <%
    String order ="catalogNumber ASC NULLS LAST";
    EncounterQueryResult result = EncounterQueryProcessor.processQuery(myShepherd, request, order);
    List<Encounter> encounters = result.getResult();
    for(int i=0; i<encounters.size(); i++){
      %>
      singleProjObj["encountersToAdd"].push("<%= encounters.get(i).getCatalogNumber()%>");
      <%
    }
  %>
  formJson["projects"].push(singleProjObj);
  return formJson;
}
</script>
<%
  }
  catch(Exception e){e.printStackTrace();}
%>

<jsp:include page="../footer.jsp" flush="true"/>
