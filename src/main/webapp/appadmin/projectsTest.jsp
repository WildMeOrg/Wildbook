<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.ia.*,java.util.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

//handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("setup.jsp");

  String currentUserId = myShepherd.getUser(request).getId();

  myShepherd.closeDBTransaction();

%>
<jsp:include page="../header.jsp" flush="true"/>

<div class="container maincontent">
    <h2>Testing Projects</h2>

    <div class="row">
        <div class="col-xs-4 col-sm-4 col-md-4 col-lg-4 col-xl-4">
            <label>Make a project!</label>
            <div class="form-group">
                <br/>
                <input class="btn btn-md" type="button" onclick="makeTestProject()" value="Create"/>
                <br/>
                <input class="form-control" name="researchProjectId" type="text" id="researchProjectId" placeholder="Research Project Id">
                <br/>
                <input class="form-control" name="researchProjectName" type="text" id="researchProjectName" placeholder="Research Project Name">
            </div>
        </div>
    </div>


    <div class="row">
        <div id="projectList" class="col-xs-4 col-sm-4 col-md-4 col-lg-4 col-xl-4">
            <label>Show Projects!</label>

        </div>
    </div>

    <div class="col-xs-4 col-sm-4 col-md-4 col-lg-4 col-xl-4">
        <p id="servletResponse"></p>
    </div>

</div>
<script>

    $(document).ready(function(){
        showTestProjects();
    });

    function showTestProjects() {
        console.log('Current User ID : <%=currentUserId%>');
        let ownerId = '<%=currentUserId%>';
        console.log("SENDING OWNERID: "+ownerId);
        let json = {};
        json['ownerId'] = ownerId;
        console.log("here!");
        console.log(JSON.stringify(json));

        $.ajax({
            url: wildbookGlobals.baseUrl + '../ProjectGet',
            type: 'POST',
            data: JSON.stringify(json),
            dataType: 'json',
            contentType: 'application/json',
            success: function(d) {
                console.log("literal response: "+d.projects);
                $("#servletResponse").text("SUCCESS");
                $("#servletResponse").append(d);
                //console.info('Success in ProjectGet retrieving data! Got back '+JSON.stringify(d));

                $("#projectList").empty();

                let projectsArr = d.projects;
                for (let i=0;i<projectsArr.length;i++) {
                    let thisProject = projectsArr[i];
                    console.log("PROJECT #"+i);
                    console.log(JSON.stringify(thisProject));
                    let projectHTML = '<div class="researchProjectIdDiv" id="'+thisProject.researchProjectId+'">';
                    projectHTML += "<p>"+thisProject.ownerId+"</p>";
                    projectHTML += "<p>"+thisProject.researchProjectName+"</p>";
                    projectHTML += "<p>"+thisProject.researchProjectId+"</p>";
                    projectHTML += "<p>"+thisProject.dateCreatedLong+"</p>";
                    projectHTML += "<p>"+thisProject.dateLastModifiedLong+"</p>";
                    projectHTML += '<p><input class="btn btn-md" type="button" onclick="deleteTestProject(this)" value="Delete"/></p>';
                    projectHTML += '</div><br>';
                    $("#projectList").append(projectHTML);
                }

            },
            error: function(x,y,z) {
                $("#servletResponse").text("ERR");
                $("#servletResponse").append("x: "+JSON.stringify(x));
                $("#servletResponse").append("y: "+JSON.stringify(y));
                $("#servletResponse").append("z: "+JSON.stringify(z));
                console.warn('%o %o %o', x, y, z);
            }
        });

    }

    function makeTestProject() {

        let projectId = $("#researchProjectId").val();
        let projectName = $("#researchProjectName").val();
        let json = {};

        json['researchProjectId'] = projectId;
        json['researchProjectName'] = projectName;

        $.ajax({
            url: wildbookGlobals.baseUrl + '../ProjectCreate',
            type: 'POST',
            data: JSON.stringify(json),
            dataType: 'json',
            contentType: 'application/json',
            success: function(d) {
                $("#servletResponse").text("SUCCESS");
                $("#servletResponse").append(JSON.stringify(d));
                console.info('Success Creating project! Got back '+JSON.stringify(d));
            },
            error: function(x,y,z) {
                $("#servletResponse").text("ERR");
                $("#servletResponse").append("x: "+JSON.stringify(x));
                $("#servletResponse").append("y: "+JSON.stringify(x));
                $("#servletResponse").append("z: "+JSON.stringify(x));
                console.warn('%o %o %o', x, y, z);
            }
        });
    }

    function deleteTestProject(el) {
        let projectId = $(el).closest(".researchProjectIdDiv").attr("id");
        //console.log("current parent in delete?? "+$(this).closest('div'));
        console.log("PROJECTID FOR DELETE: "+projectId);
        let json = {};

        json['researchProjectId'] = projectId;

        $.ajax({
            url: wildbookGlobals.baseUrl + '../ProjectDelete',
            type: 'POST',
            data: JSON.stringify(json),
            dataType: 'json',
            contentType: 'application/json',
            success: function(d) {
                $("#servletResponse").text("SUCCESS");
                $("#servletResponse").append(JSON.stringify(d));
                console.info('Success Deleting project! Got back '+JSON.stringify(d));
                showTestProjects();
            },
            error: function(x,y,z) {
                $("#servletResponse").text("ERR");
                $("#servletResponse").append("x: "+JSON.stringify(x));
                $("#servletResponse").append("y: "+JSON.stringify(x));
                $("#servletResponse").append("z: "+JSON.stringify(x));
                console.warn('%o %o %o', x, y, z);
            }
        });
    }

</script>

<jsp:include page="../footer.jsp" flush="true"/>
