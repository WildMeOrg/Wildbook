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
          <h1 class="intro">Contact us </h1>
     

        <p>The team welcomes your comments and questions.</p>

<p>Please email us at <em>info at whaleshark dot org</em>, and one of us will respond as quickly as possible.</p>

<h2>Photos for Media Publications about Wildbook for Whale Sharks</h2>
<p>The following photos from Wild Me Director Dr. Simon Pierce may be used freely, for editorial purposes, in return for a link back to https://www.simonjpierce.com (online) and/or correct attribution (print).</p>
<p>
	<img src="images/-simon-pierce-1140612.jpg" width="500px" height="*"/><br>
	<img src="images/-simon-pierce-1140648.jpg" width="500px" height="*"/><br>
	<img src="images/-simon-pierce-1180243.jpg" width="500px" height="*"/><br>
	<img src="images/-simon-pierce-1260314.jpg" width="500px" height="*"/><br>
	<img src="images/-simon-pierce-7180025.jpg" width="500px" height="*"/><br>
</p>

<h2>Logos</h2>
The following logos may be used inconjunction with our project.

<h3>Wild Me</h3>

<p><img src="images/wild-me-logo-high-resolution.png" width="500px" height="*" /></p>

<h3>Wildbook&reg;</h3>

<p><img src="images/WildBook_logo_300dpi-04.png" width="500px" height="*" /></p>


   
      <!-- end maintext -->
      </div>


