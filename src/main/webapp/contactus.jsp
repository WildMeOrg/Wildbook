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
        <h1 class="intro">Contact us</h1>
        <p>The team welcomes your comments and questions.</p>
	<p>Email us at <em>info at wildme dot org</em>, and one of us will respond as quickly as possible.</p>

	<h2>Photos for Media Publications about Wildbook</h2>
	<p>For any resources that can be used without express permission from the Wild Me team, see <a href="https://www.wildme.org/media-resources.html">Media Resources</a>.</p>

	<h2>Logos</h2>
	<p>The following logos may be used in conjunction with our project.</p>

	<h3>Wild Me</h3>
	<p><img src="images/WildMe-Logo-04.png" width="500px" height="*" /></p>

	<h3>Wildbook&reg;</h3>
	<p><img src="images/WildBook_logo_300dpi-04.png" width="500px" height="*" /></p>

	<p>Any additional photo resources should be obtained with express permission from the Wild Me or CXL team.</p>

      <!-- end maintext -->
      </div>
