<%@ page contentType="text/html; charset=iso-8859-1" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*" %>



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
     

      <h1>Data Integrity Checks</h1>
     
<h3>Check for Annotations with Multiple Individual IDs</h3>
<p>Annotations assigned to two or more different individuals represent data errors that can cause matching to fail or result in falsely merged individuals.</p>
<p><a target="_blank" href="sharedAnnotations.jsp">Click here to check</a></p>      
</div>


      <jsp:include page="../footer.jsp" flush="true"/>



