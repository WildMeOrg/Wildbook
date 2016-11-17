<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities, org.ecocean.CommonConfiguration,org.ecocean.Shepherd,java.awt.*, java.io.File" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>
<%
String context="context0";
context=ServletUtilities.getContext(request);
  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("adoptionSuccess.jsp");


  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);


  //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));

%>

    <jsp:include page="../header.jsp" flush="true" />

        <div class="container maincontent">

          <h1 class="intro">Thank you for your support!</h1>
          <h3>Your donation will go to support the ongoing work at Whaleshark.org.</h3>


          <p><a href="http://<%=CommonConfiguration.getURLLocation(request)%>">
            Return to Homepage</a>.
          </p>
        </div>
        <jsp:include page="../footer.jsp" flush="true"/>
