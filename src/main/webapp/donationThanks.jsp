<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities, org.ecocean.CommonConfiguration, java.io.File" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>
<%
String context="context0";
context=ServletUtilities.getContext(request);

  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);


  //setup data dir

%>

    <jsp:include page="/header.jsp" flush="true" />

        <div class="container maincontent">

          <h1 class="intro">Thank you.</h1>
          <p><strong>Your donation was successfully processed.</strong></p>
          <p>You will shortly recieve a <strong>one time</strong> email to confirm your payment that you can retain for tax purposes.</p>

          <p><a
            href="//<%=CommonConfiguration.getURLLocation(request)%>"><h3>Wildbook Home</h3>
          </a>.</p>


        </div>
    <jsp:include page="/footer.jsp" flush="true"/>
