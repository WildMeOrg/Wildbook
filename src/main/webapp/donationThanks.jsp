<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities, org.ecocean.CommonConfiguration,java.awt.*, java.io.File" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>
<%
String context="context0";
context=ServletUtilities.getContext(request);
  myShepherd.setAction("donationThanks.jsp");


  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);


%>

    <jsp:include page="/header.jsp" flush="true" />

        <div class="container maincontent">
           

          	
          <h1 class="intro">Donation Complete</h1>
          <p><strong>Your payment was successfully processed.</strong></p>
	  <p>Thank you for contributing to Whaleshark.org</p>


          <p><a
            href="http://<%=CommonConfiguration.getURLLocation(request)%>"><h3>Wildbook Home</h3>
          </a>.</p>


        </div>
        <jsp:include page="/footer.jsp" flush="true"/>
