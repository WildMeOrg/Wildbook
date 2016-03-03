<%@ page contentType="text/html; charset=iso-8859-1" language="java" %>
<%@ page import="java.util.Properties" %>
<%@ page import="org.ecocean.Shepherd" %>
<%@ page import="org.ecocean.ShepherdProperties" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<%
  String context = ServletUtilities.getContext(request);
  String langCode = ServletUtilities.getLanguageCode(request);
  Properties props = ShepherdProperties.getProperties("admin.properties", langCode, context);

//handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility
%>



    <jsp:include page="../header.jsp" flush="true" />



<div class="container maincontent">
 

      <h1><%=props.getProperty("logs.title")%></h1>
     

<ul>
<li><a href="../logs/user-access.htm"><%=props.getProperty("logs.userAccess")%></a></li>
<li><a href="../logs/encounter-submission.htm"><%=props.getProperty("logs.submissions")%></a></li>
<li><a href="../logs/encounter-delete.htm"><%=props.getProperty("logs.deletedEncounters")%></a></li>
</ul>

</div>
      <jsp:include page="../footer.jsp" flush="true"/>
   
  



