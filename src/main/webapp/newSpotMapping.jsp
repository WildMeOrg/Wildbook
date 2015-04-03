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
		  <h1 class="intro">There is a new way to map spots!</h1>
		</div>
		
		<p>Thank you for trying to submit a spot pattern through Interconnect! While it served us well, we are happy to announce that spot mapping is now possible in the browser (Firefox, Chrome, and Safari on Mac)!</p>
		
		<p>To map left- and right-side spots the new way, click <b>Mark spots</b> above any image in the encounter page to get started.</p>

			
		<p><a href="encounters/encounter.jsp?number=<%=request.getParameter("number")%>">Go to your encounter to map spots.</a></p>	
	
		<p>Do you want to see the new tool in action? Here's a quick video!</p>
		<iframe width="854" height="510" src="https://www.youtube.com/embed/VxKKvNs7Kog" frameborder="0" allowfullscreen></iframe>
	
	</div>
	<!-- end maintext -->

  </div><!-- end maincol -->

<jsp:include page="footer.jsp" flush="true" />
</div><!-- end page -->
</div><!--end wrapper -->
</body>
</html>
