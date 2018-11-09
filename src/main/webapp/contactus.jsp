<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.CommonConfiguration,java.util.Properties, org.ecocean.servlet.ServletUtilities" %>
<%

  //setup our Properties object to hold all properties
  
  String langCode = ServletUtilities.getLanguageCode(request);

  //set up the file input stream
  //FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/"+langCode+"/submit.properties"));
  //props.load(propsInputStream);
  
  

  
  String context=ServletUtilities.getContext(request);

%>
<jsp:include page="header.jsp" flush="true"/>
<div class="container maincontent">
  <h1 class="intro">Contact Us</h1>
  <p>The Internet of Turtles team welcomes your comments and questions.</p>

  <p>Please email us at <em>services at wildme dot org</em>, and one of us will respond as quickly as possible.</p>

  <p>Internet of Turtles is an instance of <a href="http://www.wildbook.org/doku.php">Wildbook</a> software for mark recapture, which is a project by 501(c)3 non-profit <a href="https://www.wildme.org/">Wild Me</a>.</p>

  <h2>Photos for Media Publications about Wildbook for Turtles</h2>
  <h2>Logos</h2>
  
  <p>The following logos may be used inconjunction with our project.</p>

  <h3>Internet of Turtles</h3>

  <p><img src="images/iot_logo.png" width="400px" height="*" /></p>

  <h3>Wild Me</h3>

  <p><img src="images/wild-me-logo-high-resolution.png" width="300px" height="*" /></p>

  <h3>Wildbook&reg;</h3>

  <p><img src="images/WildBook_logo_300dpi-04.png" width="300px" height="*" /></p>

</div>

<jsp:include page="footer.jsp" flush="true"/>

