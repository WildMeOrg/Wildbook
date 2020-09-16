<%@ page contentType="text/html; charset=utf-8" language="java"
         import ="org.ecocean.servlet.ServletUtilities,
         com.drew.imaging.jpeg.JpegMetadataReader,
         com.drew.metadata.Directory,
         org.ecocean.*,
         java.util.regex.Pattern,
         org.ecocean.servlet.ServletUtilities,
         org.json.JSONObject,
         org.json.JSONArray,
         javax.jdo.Extent, javax.jdo.Query,
         java.io.File, java.text.DecimalFormat,
         org.apache.commons.lang.StringEscapeUtils,
         java.util.*,org.ecocean.security.Collaboration" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>

<%
  String context="context0";
  context=ServletUtilities.getContext(request);
  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("editProject.jsp");
  String projId = request.getParameter("id").replaceAll("\\+", "").trim();
  String rootWebappPath = getServletContext().getRealPath("/");
  File webappsDir = new File(rootWebappPath).getParentFile();
  File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
  File projectsDir=new File(shepherdDataDir.getAbsolutePath()+"/projects");

  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

  String langCode=ServletUtilities.getLanguageCode(request);

  boolean proceed = true;
  boolean haveRendered = false;
  Properties collabProps = new Properties();
  String urlLoc = "//" + CommonConfiguration.getURLLocation(request);
  collabProps=ShepherdProperties.getProperties("collaboration.properties", langCode, context);
  User currentUser = AccessControl.getUser(request, myShepherd);
%>
<style type="text/css">
  .disabled-btn { /* moving this to _encounter-pages.less AND moving that beneath buttons custom import in manta.less did not work. */
    background:#62676d30;
    border:0;
    color:#fff;
    line-height:2em;
    padding:7px 13px;
    font-weight:300;
    vertical-align:middle;
    margin-right:10px;
    margin-top:15px
  }
</style>
<jsp:include page="../header.jsp" flush="true"/>
<link rel="stylesheet" href="<%=urlLoc %>/cust/mantamatcher/css/manta.css"/>
<jsp:include page="../footer.jsp" flush="true"/>
<div id="projectDetails">
</div>

<script>
function showEditProject() {
    let ownerId = '<%=currentUser.getId()%>';
    let projectId = '<%=projId%>';
    let getEncounterMetadata = true;
    let json = {};
    json['projectUUID'] = projectId;
    json['getEncounterMetadata'] = getEncounterMetadata;
    doProjectGetAjax(json);
}

function doProjectGetAjax(json){
  $.ajax({
      url: wildbookGlobals.baseUrl + '../ProjectGet',
      type: 'POST',
      data: JSON.stringify(json),
      dataType: 'json',
      contentType: 'application/json',
      success: function(data) {
          populateHtml(data.projects[0]);
      },
      error: function(x,y,z) {
        //TODO some sort of indication on user end that something has gone wrong
          console.warn('%o %o %o', x, y, z);
      }
  });
}

function populateHtml(project){
  let projectHTML = '<div class="researchProjectIdDiv" id="'+project.researchProjectId+'">';
  projectHTML += '<p>' + project.researchProjectName + '</p>';
  projectHTML += '<p><input class="btn btn-md" type="button" onclick="deleteProject(this)" value="Delete"/></p>';
  projectHTML += '</div>';
  $("#projectDetails").append(projectHTML);
}

function deleteProject(el) {
  let projectId = $(el).closest(".researchProjectIdDiv").attr("id");
  let json = {};
  json['researchProjectId'] = projectId;
  doDeleteAjax(json);
}

function doDeleteAjax(json){
  $.ajax({
      url: wildbookGlobals.baseUrl + '../ProjectDelete',
      type: 'POST',
      data: JSON.stringify(json),
      dataType: 'json',
      contentType: 'application/json',
      success: function(data) {
        //TODO indicate to user that something good happened
          window.location.replace('/projects/projectList.jsp');
      },
      error: function(x,y,z) {
          console.warn('%o %o %o', x, y, z);
      }
  });

}


$(document).ready(function() { //TODO not sure why this is not working
    showEditProject();
});
</script>
