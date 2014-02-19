<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
        <%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.*,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException" %>
<%

	
	//language setup
	String langCode="en";
	if(session.getAttribute("langCode")!=null){langCode=(String)session.getAttribute("langCode");}

	Properties props=new Properties();
	props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/submit.properties"));

	
	
%>

<html>
<head>
<title><%=CommonConfiguration.getHTMLTitle() %></title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta name="Description" content="<%=CommonConfiguration.getHTMLDescription() %>" />
<meta name="Keywords" content="<%=CommonConfiguration.getHTMLKeywords() %>" />
<meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor() %>" />
<link href="<%=CommonConfiguration.getCSSURLLocation(request) %>" rel="stylesheet" type="text/css" />
<link rel="shortcut icon" href="<%=CommonConfiguration.getHTMLShortcutIcon() %>" />

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

	<div id="maincol-wide">

		<div id="maintext">
		  <h1 class="intro">Adopt a Manta</h1>
		</div>
			
			<p>You can support the ongoing research of the ECOCEAN MantaMatcher by adopting a manta ray. A manta adoption allows you to:</p>
			<ul>
			  <li>support cutting-edge manta research through the ECOCEAN MantaMatcher</li>
	    <li> receive email updates of resightings of your adopted manta</li>
		<li>display your photo and a quote from you on the manta's page in our library</li>
		</ul>
			<p>Funds raised by manta adoptions are used to offset the costs of maintaining this global library and to support new and existing research projects for the species.</p>
			<p>You can adopt a manta at the following levels:</p>
			<ul>
			<li> Children's adoption = USD $25/year</li>
			  <li> Individual adoption = USD $50/year</li>
	    <li>Group adoption = USD $200/year </li>
	          <li>Corporate adoption = USD $1000/year</li>
		</ul>
			<p>The cost of your adoption is tax deductible in the United States through ECOCEAN USA, a 501(c)(3) non-profit organization.</p>
			
			<table><tr><td>
			<h3>Creating an adoption</h3>
			<p>To adopt a manta, follow these steps.</p>
			<p>1. Make the appropriate donation using the PayPal link below.</p>
	
<form action="https://www.paypal.com/cgi-bin/webscr" method="post">
<input type="hidden" name="cmd" value="_s-xclick">
<input type="hidden" name="hosted_button_id" value="QFCG98BD8DS5Q">
<input type="image" src="https://www.paypalobjects.com/en_US/i/btn/btn_donateCC_LG.gif" border="0" name="submit" alt="PayPal - The safer, easier way to pay online!">
<img alt="" border="0" src="https://www.paypalobjects.com/en_US/i/scr/pixel.gif" width="1" height="1">
</form>


			<p>2. Send an email to adoptions@mantamatcher.org. Include the following in the email:</p>
			<ul>
			  <li> your name and address</li>
	    <li>your donation amount and the email/userid that made the PayPal donation </li>
	          <li>the manta you wish to adopt.</li>
		<li>the email to notify with future resightings of the manta</li>
		<li>a photo of yourself, your group, or a corporate logo</li>
		<li>a quote from you stating why manta research and conservation are important </li>
		</ul>
	<p>Please allow 24-48 hours after receipt of your email for processing. We are currently working to automate and speed this process through PayPal. </p>
	<p>Your adoption (photograph, name, and quote) will be displayed on the web site page for your shark, and one adoption will be randomly chosen to be displayed on the front page of whaleshark.org.</p>
	<p><em><strong>Thank you for adopting a manta and supporting our global research efforts! </strong></em></p>
	</td>
	<td width="200" align="left">
	<p align="center"><a href="http://www.whaleshark.org/individuals.jsp?number=A-001"><img src="images/sample_adoption.gif" border="0" /></a>	
	</p>
	  <p align="center"><strong>
	  Sample adoption for manta XXXXX. <br />
	  </strong><strong><a href="http://www.mantamatcher.org/individuals.jsp?number=A-001">Click here to see it on the manta page. </a> </strong></p>
	  </td></tr></table>
	</div>
	<!-- end maintext -->

  </div><!-- end maincol -->

<jsp:include page="footer.jsp" flush="true" />
</div><!-- end page -->
</div><!--end wrapper -->
</body>
</html>
