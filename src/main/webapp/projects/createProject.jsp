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
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility
  String langCode=ServletUtilities.getLanguageCode(request);
  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("createProject.jsp1");
  boolean proceed = true;
  boolean haveRendered = false;
  Properties collabProps = new Properties();
  String urlLoc = "//" + CommonConfiguration.getURLLocation(request);
  collabProps=ShepherdProperties.getProperties("collaboration.properties", langCode, context);
  User currentUser = AccessControl.getUser(request, myShepherd);
  Properties props = new Properties();
  props = ShepherdProperties.getProperties("createProject.properties", langCode, context);
%>

<jsp:include page="../header.jsp" flush="true"/>
  <link rel="stylesheet" href="<%=urlLoc %>/cust/mantamatcher/css/manta.css"/>
    <div class="container maincontent">
      <title>Create A Project</title>
          <%
          try{
            if(currentUser != null){
              System.out.println(props.getProperty("proj_name"));
              %>
              <h1>New Project</h1>
              <form id="create-project-form"
              method="post"
              action="../ProjectCreate"
              accept-charset="UTF-8">
                <div class="form-group row">
                  <div class="form-inline col-xs-12 col-sm-12 col-md-12 col-lg-12">
                    <label><strong><%=props.getProperty("proj_name") %></strong></label>
                    <input class="form-control" type="text" id="proj-name" name="proj-name"/>
                  </div>
                </div>
                <div class="form-group required row">
                  <div class="form-inline col-xs-12 col-sm-12 col-md-12 col-lg-12">
                    <label class="control-label text-danger"><strong><%=props.getProperty("proj_id") %></strong></label>
                    <input class="form-control" type="text" style="position: relative; z-index: 101;" id="proj_id" name="proj_id" size="20" />
                  </div>
                </div>
                    <div class="col-xs-6 col-sm-6 col-md-6 col-lg-6 col-xl-6">
                      <label><strong><%=props.getProperty("userAccess") %></strong></label>
                      <input class="form-control" name="userAccess" type="text" id="userAccess" placeholder="<%=props.getProperty("typeToSearch") %>">
                    </div>
                    <div id="userAccessListContainer">
                      <strong>Users To Be Granted Access</strong>
                      <div id="userAccessList">
                      </div>
                    </div>
                    <div class="col-xs-6 col-sm-6 col-md-6 col-lg-6 col-xl-6">
                      <input id="addUserToProjectButton" name="addUserToProjectButton" type="button" value="<%=props.getProperty("addUserToProject")%>" onclick="addUserToProject();">
                      </input>
                    </div>
                    <div class="row">
                      <%
                        FormUtilities.setUpOrgDropdown("organizationAccess", false, props, out, request, myShepherd);
                      %>
                    </div>
                    <button id="createProjectButton" class="large" type="submit" onclick="createButtonClicked();">
                      <%=props.getProperty("submit_send") %>
                      <span class="button-icon" aria-hidden="true" />
                    </button>
              </form>
              <h4>To add encounters to this project, use the project option in encounter search</h4>
              <%
            }else{

            }
          }
          catch(Exception e){
            e.printStackTrace();
          }
          finally{
          }
          %>
    </div>
    <jsp:include page="../footer.jsp" flush="true"/>

    <script>
    let myName = '<%=request.getUserPrincipal().getName()%>';
    let userNamesOnAccessList = [];
    $('#userAccess').autocomplete({
      source: function(request, response){
        $.ajax({
          url: wildbookGlobals.baseUrl + '/UserGetSimpleJSON?searchUser=' + request.term,
          type: 'GET',
          dataType: "json",
          success: function(data){
            console.log("autocompleting...");
            let res = $.map(data, function(item){
              console.log("data is: ");
              console.log(data);
              if(item.username==myName || typeof item.username == 'undefined' || item.username == undefined||item.username===""){
                return;
              }
              let fullName = "";
              if(item.fullName!=null && item.fullName!="undefined"){
                fullName=item.fullName;
              }
              let label = ("name: " + fullName + " user: " + item.username);
              return {label: label, value: item.username};
            });
            response(res);
          }
        });
      }
    });

    function updateUserAccessDisplay(){
      userNamesOnAccessList = [...new Set(userNamesOnAccessList)];
      $('#userAccessList').empty();
      for(i=0; i<userNamesOnAccessList.length; i++){
        console.log("got into for loop");
        let elem = "<div class=\"chip\">" + userNamesOnAccessList[i] + "  <span class=\"glyphicon glyphicon-remove\" aria-hidden=\"true\" onclick=\"removeUserFromProj('" + userNamesOnAccessList[i] + "'); return false\"></span></div>";
        $('#userAccessList').append(elem);
      }
    }

    function addUserToProject(){
      console.log("addUserToProject entered");
      if($('#userAccess').val()){
        let currentUserToAdd = $('#userAccess').val();
        console.log("currentUserToAdd is " + currentUserToAdd);
        userNamesOnAccessList.push(currentUserToAdd);
        updateUserAccessDisplay();
      }
    }

    function removeUserFromProj(name){
      console.log("removeUserFromProj entered");
      console.log("got user name " + name);
      userNamesOnAccessList = userNamesOnAccessList.filter(element => element !== name);
      updateUserAccessDisplay();
    }

    function createButtonClicked() {
    	console.log('createButtonClicked()');
    	if(!$('#proj_id').val()){
    		console.log("no proj_id entered");
    		$('#proj_id').closest('.form-group').addClass('required-missing');
    		window.setTimeout(function() { alert('You must provide a Project ID.'); }, 100);
    		return false;
    	}
    	return true;
    }
    </script>
