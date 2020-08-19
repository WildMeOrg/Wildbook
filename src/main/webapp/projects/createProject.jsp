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
    <title>Create A Project</title>
    <div class="container maincontent">
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
                <div class="form-group">
                  <div class="form-inline col-xs-12 col-sm-12 col-md-6 col-lg-6">
                    <label><%=props.getProperty("proj_name") %></label>
                    <input class="form-control" type="text" id="proj-name" name="proj-name"/>
                  </div>
                </div>
                <div class="form-group required">
                  <div class="form-inline col-xs-12 col-sm-12 col-md-6 col-lg-6">
                    <label class="control-label text-danger"><%=props.getProperty("proj_id") %></label>
                    <input class="form-control" type="text" style="position: relative; z-index: 101;" id="proj_id" name="proj_id" size="20" />
                  </div>
                </div>
                <div class="form-group">
                  <div class="form-inline col-xs-12 col-sm-12 col-md-6 col-lg-6">
                    <select multiple name="username" id="username">
                    <option value="None"></option>
                    <%
                    List<String> users = myShepherd.getAllNativeUsernames();
                    users.remove(null);
                    Collections.sort(users,String.CASE_INSENSITIVE_ORDER);
                    int numUsers = users.size();
                    for (int n = 0; n < numUsers; n++) {
                      String username = users.get(n);
                      %>
                      <option value="<%=username%>"><%=username%></option>
                      <%
                    }
                    %>
                    </select>
                  </div>
                </div>
                    <%
                      FormUtilities.printStringFieldSearchRowBoldTitle(false, "userName", users, users, out, props);
                      FormUtilities.setUpOrgDropdown(false, props, out, request, myShepherd);
                    %>
              </form>
              <%
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
