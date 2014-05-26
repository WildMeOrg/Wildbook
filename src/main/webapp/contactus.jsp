<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
        <%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.ShepherdProperties,org.ecocean.servlet.ServletUtilities,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException" %>
<%

String context="context0";
context=ServletUtilities.getContext(request);

  //setup our Properties object to hold all properties
  Properties props = new Properties();
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  

  //set up the file input stream
  //FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/"+langCode+"/submit.properties"));
  //props.load(propsInputStream);
  //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/submit.properties"));
  props = ShepherdProperties.getProperties("overview.properties", langCode,context);


%>

<html>
<head>
<title>Wild Me - Contact Us</title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta name="Description" content="The Wildbook for Whale Sharks photo-identification library is a visual database of whale shark (Rhincodon typus) encounters and of individually catalogued whale sharks. The library is maintained and used by marine biologists to collect and analyze whale shark encounter data to learn more about these amazing creatures." />
<meta name="Keywords" content="whale shark,whale,shark,Rhincodon typus,requin balleine,Rhineodon,Rhiniodon,big fish,Wild Me,Brad Norman, fish, coral, sharks, elasmobranch, mark, recapture, photo-identification, identification, conservation, citizen science" />
<meta name="Author" content="Wild Me - info@whaleshark.org" />
<link href="css/ecocean.css" rel="stylesheet" type="text/css" />
<link rel="shortcut icon" href="images/favicon.ico" />

</head>

<body>
<div id="wrapper">
<div id="page">
<jsp:include page="header.jsp" flush="true">
	<jsp:param name="isResearcher" value="<%=request.isUserInRole(\"researcher\")%>"/>
	<jsp:param name="isManager" value="<%=request.isUserInRole(\"manager\")%>"/>
	<jsp:param name="isReviewer" value="<%=request.isUserInRole(\"reviewer\")%>"/>
	<jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>"/>
</jsp:include>	
<div id="main">
	
	<div id="maincol-wide-solo">

		<div id="maintext">
		  <h1 class="intro">Contact us </h1>
		</div>
			
		<p>We welcome your comments and questions, though we are currently unable to respond to every email.</p>
		<p><strong>Media questions</strong></p>
		<p>If you are a journalist, writer or documentary filmmaker, we want to hear from you.</p>
		<p>Wild Me is always looking for opportunities to better tell the story of whale sharks and the growing body of research and discoveries made possible by dedicated scientists, volunteers, and the general public.</p>
		<p>Please contact us at <em>media at whaleshark dot org</em>.</p>
		<p><strong>Web site questions</strong></p>
		<p>If you have technical questions about this web site, please contact <em>webmaster at whaleshark dot org</em>. </p>
		<p><strong>General questions</strong></p>
		<p>If you have general questions about whale sharks or our research, please contact <em>info at whaleshark dot org</em>.  </p>
	</div>
	<!-- end maintext -->

  </div><!-- end maincol -->

<jsp:include page="footer.jsp" flush="true" />
</div><!-- end page -->
</div><!--end wrapper -->
</body>
</html>
