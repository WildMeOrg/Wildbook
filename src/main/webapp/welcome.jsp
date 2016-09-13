<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,java.util.ArrayList,org.ecocean.*,java.util.Properties,org.slf4j.Logger,org.slf4j.LoggerFactory,org.apache.commons.lang3.StringEscapeUtils" %>

<%
String context="context0";
context=ServletUtilities.getContext(request);


  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility


  //setup our Properties object to hold all properties
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  


  //set up the file input stream
  Properties props = new Properties();
  //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/welcome.properties"));
  props = ShepherdProperties.getProperties("welcome.properties", langCode,context);


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


          <p><%=props.getProperty("loggedInAs")%> <strong><%=StringEscapeUtils.escapeHtml4(request.getRemoteUser())%>
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

