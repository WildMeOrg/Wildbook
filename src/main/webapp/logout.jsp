<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*, java.util.Properties,org.slf4j.Logger,org.slf4j.LoggerFactory" %>
<%

String context="context0";
context=ServletUtilities.getContext(request);

  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

//setup our Properties object to hold all properties
  Properties props = new Properties();
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  props = ShepherdProperties.getProperties("login.properties", langCode,context);

  

  //set up the file input stream
  //props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/submit.properties"));


%>
<jsp:include page="header.jsp" flush="true"/>
<div class="container maincontent">
          <p>

          <h1 class="intro"><%=props.getProperty("logout") %></h1>
          </p>
        

        <p><%=props.getProperty("loggedOut") %></p>
        
            <%
		        Logger log = LoggerFactory.getLogger(getClass());
		        log.info(request.getRemoteUser()+" logged out.");
	
	    %>

        <p><a href="welcome.jsp"><%=props.getProperty("clickHere") %></a></p>

        <p>&nbsp;</p>
      </div>
            <jsp:include page="footer.jsp" flush="true"/>
<%
  session.invalidate();
%>

