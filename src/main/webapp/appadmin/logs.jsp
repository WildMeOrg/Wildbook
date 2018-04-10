<%@ page contentType="text/html; charset=iso-8859-1" language="java"
         import="org.ecocean.CommonConfiguration,org.ecocean.servlet.ServletUtilities" %>
<%@ page import="org.ecocean.Shepherd" %>


<%

String context="context0";
context=ServletUtilities.getContext(request);


//handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility
%>



    <jsp:include page="../header.jsp" flush="true" />



<div class="container maincontent">
 

      <h1>Logs</h1>
     

<ul>
<li><a href="/<%=CommonConfiguration.getDataDirectoryName(context) %>/logs/user-access.htm">User access log</a></li>
<li><a href="/<%=CommonConfiguration.getDataDirectoryName(context) %>/logs/encounter-submission.htm">Encounter submissions log</a></li>
<li><a href="/<%=CommonConfiguration.getDataDirectoryName(context) %>/logs/encounter-delete.htm">Deleted encounters log</a></li>
</ul>

</div>
      <jsp:include page="../footer.jsp" flush="true"/>
   
  



