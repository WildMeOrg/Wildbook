<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Properties" %>
<%@ page import="org.ecocean.*" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<%@ page import="org.slf4j.Logger" %>
<%@ page import="org.slf4j.LoggerFactory" %>
<%
  String context = ServletUtilities.getContext(request);

  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

  String langCode = ServletUtilities.getLanguageCode(request);
  Properties props = ShepherdProperties.getProperties("welcome.properties", langCode, context);

  session = request.getSession(true);
  session.putValue("logged", "true");
  if ((request.getParameter("reflect") != null)) {
    response.sendRedirect(request.getParameter("reflect"));
  }
  ;
%>
<jsp:include page="header.jsp" flush="true"/>

<div class="container maincontent">

          <h1 class="intro"><%=props.getProperty("loginSuccess")%>
          </h1>


          <p><%=props.getProperty("loggedInAs")%> <strong><%=request.getRemoteUser()%>
          </strong>.
          </p>

          <p><%=props.getProperty("grantedRole")%><br />
			<%
			Shepherd myShepherd=new Shepherd("context0");
			myShepherd.setAction("welcome.jsp");
			myShepherd.beginDBTransaction();
			%>
             <em><%=myShepherd.getAllRolesForUserAsString(request.getRemoteUser()).replaceAll("\r","<br />")%></em></p>
            
            <%
            
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            
	        Logger log = LoggerFactory.getLogger(getClass());
	        log.info(request.getRemoteUser()+" logged in from IP address "+request.getRemoteAddr()+".");
			
	    %>


          <p><%=props.getProperty("pleaseChoose")%>
          </p>

          <p>&nbsp;</p>
        </div>

      <jsp:include page="footer.jsp" flush="true"/>

