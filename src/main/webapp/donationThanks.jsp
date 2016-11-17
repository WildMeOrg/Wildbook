<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.Adoption, org.ecocean.MarkedIndividual, org.ecocean.CommonConfiguration,org.ecocean.Shepherd,java.awt.*, java.io.File" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>
<%
String context="context0";
context=ServletUtilities.getContext(request);
  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("donationThanks.jsp");


  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);


  //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
     File adoptionsDir=new File(shepherdDataDir.getAbsolutePath()+"/adoptions");
  if(!adoptionsDir.exists()){adoptionsDir.mkdirs();}


  File thisAdoptionDir = new File(adoptionsDir.getAbsolutePath()+"/" + request.getParameter("number"));
  if(!thisAdoptionDir.exists()){thisAdoptionDir.mkdirs();}

%>

    <jsp:include page="../header.jsp" flush="true" />

        <div class="container maincontent">

          <h1 class="intro">Adoption Edit</h1>
          <h3><%=nickName%> - <%=shark%></h3>
          <p><strong>Your adoption was successfully updated.</strong></p>



          <p><a href="http://<%=CommonConfiguration.getURLLocation(request)%>/individuals.jsp?number=<%=markedIndividual%>">
            View your shark's updated profile</a>.</p>
          <p><a
            href="http://<%=CommonConfiguration.getURLLocation(request)%>"><h3>Wildbook Home</h3>
          </a>.</p>


        </div>
        <jsp:include page="../footer.jsp" flush="true"/>
