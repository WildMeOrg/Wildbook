<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
        <%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.*,java.util.Properties,java.io.FileInputStream, java.io.File, java.io.FileNotFoundException" %>
	
	

<html>
<head>
<title><%=CommonConfiguration.getHTMLTitle() %></title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta name="Description" content="<%=CommonConfiguration.getHTMLDescription() %>" />
<meta name="Keywords" content="<%=CommonConfiguration.getHTMLKeywords() %>" />
<meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor() %>" />
<link href="<%=CommonConfiguration.getCSSURLLocation(request) %>" rel="stylesheet" type="text/css" />
<link rel="shortcut icon" href="<%=CommonConfiguration.getHTMLShortcutIcon() %>" />

<style>
.myTableText td { color:#FFFFFF; }
.myTableText td a:link { color:#FFFFFF;text-decoration:underline; }
.myTableText td a:visited { color:#FFFFFF; }
.myTableText td a:hover { color:#FFFFFF;text-decoration:underline; }
.myTableText td a:active { color:#FFFFFF; }

</style>

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
<div id="main" style="padding-top:0px">
	
	<div id="maincol-wide-solo">

<table class="myTableText">
  <tr>
  <td align="left" bgcolor="#000000" style="padding-top:25px;
padding-bottom:25px;
padding-right:50px;
padding-left:50px;">

    <p><center>Thanks for clicking through! Scroll down to adopt a shark!</center></p>
    <p>Whaleshark.org is one of a kind. It enables global scientific research on a beloved, gentle species, the biggest fish in the sea.  </p>
    <p>But it is not funded by any government. It is not funded by any for-profit corporation.  </p>
    <p>Whaleshark.org is run by a non-profit charity that relies on donations to host and manage  one of the world's most sophisticated wildlife databases, to make it available to researchers,  citizen scientists, and the general public. Without a steady flow of financial contributions, the  site can't continue to operate.  </p>
    <p>If whaleshark.org goes dark, a vital research capability is lost. There's nothing else like it. </p>
    <p>If you're a visitor to our site, please consider a donation or adoption. (Adoptions for children  cost just $25/year, and you get to nickname a shark!) If you're in the ecotourism business,  consider what whaleshark.org adds to your clients' experience, and help us keep alive their  continued fascination with whale sharks. If you're a researcher who uses and depends  on the photo-ID library, help us help you by budgeting for this precious resource in your  research grant applications and contributing your vital financial support. We're all in this  together! </p>
    <p>Your contribution supports research that directly informs conservation practices. To see  the difference we’re already making, visit our <a href="publications.jsp">Publications page</a>. And we have exciting new  developments on the horizon.  </p>
    <p>Every little bit helps. Like giant whale sharks thriving on a diet of microscopic life (in  vast quantities!), many small contributions from our dedicated community can keep  whaleshark.org flourishing. Please help in any way you can. And thank you!</p></td></tr></table>

<p>&nbsp;</p>
		<div id="maintext">
		  <h1 class="intro"><img src="images/adoption.gif" width="56" height="48" align="absmiddle" />Adopt a Shark</h1>
		</div>
			<table><tr>
			<td valign="top"><p>You can support the ongoing research of the Whale Shark Photo-identification Library by adopting a whale shark. A whale shark adoption allows you to: 
			<ul>
			  <li>support cutting-edge whale shark research</li>
	    <li> receive email updates of resightings of your adopted shark</li>
		<li>display your photo and a quote from you on the shark's page in our library</li>
		</ul>
			<p>Funds raised by shark adoptions are used to offset the costs of maintaining this global library and to support new and existing research projects for the world's most mysterious fish.</p>
			<p>You can adopt a shark at the following levels:</p>
			<ul>
			<li> Children's adoption = USD $25/year</li>
			  <li> Individual adoption = USD $50/year</li>
	    <li>Group adoption = USD $200/year </li>
	          <li>Corporate adoption = USD $1000/year</li>
		</ul>
			<p>The cost of your adoption is tax deductible in the United States through ECOCEAN USA, a 501(c)(3) non-profit organization.</p>
			</td>
			<td width="200" align="left">
				<p align="center"><a href="http://www.whaleshark.org/individuals.jsp?number=A-001"><img src="images/sample_adoption.gif" border="0" /></a>	
				</p>
				  <p align="center"><strong>
				  Sample whale shark adoption for whale shark A-001. <br />
				  </strong><strong><a href="http://www.whaleshark.org/individuals.jsp?number=A-003">Click here to see an example. </a> </strong></p>
	  </td></table>
			</p>
			
			<table><tr><td>
			<h3>Creating an adoption</h3>
			<p>To adopt a whale shark, follow these steps.</p>
			<p>1. Make the appropriate donation using the appropriate PayPal button below.</p>
<table cellpadding="5">

<tr>
	<td width="250px" valign="top"><em>Use the button below if you would like your Adoption funds directed to ECOCEAN USA. ECOCEAN USA offers tax deductability in the United States as a 501(c)(3) nonprofit organization.</em></td>
	
	<!--
	<td width="250px" valign="top"><em>Use the button on the right if you would like your Adoption funds directed to ECOCEAN (Australia).</em></td>
	-->

</tr>
<tr><td>	
<form action="https://www.paypal.com/cgi-bin/webscr" method="post">
<input type="hidden" name="cmd" value="_s-xclick">
<input type="hidden" name="hosted_button_id" value="5075222">
<input type="image" src="https://www.paypal.com/en_US/i/btn/btn_donateCC_LG.gif" border="0" name="submit" alt="PayPal - The safer, easier way to pay online!">
<img alt="" border="0" src="https://www.paypal.com/en_US/i/scr/pixel.gif" width="1" height="1">
</form>
</td>

<!--
<td><form action="https://www.paypal.com/cgi-bin/webscr" method="post" target="_top">

<input type="hidden" name="cmd" value="_s-xclick">
<input type="hidden" name="hosted_button_id" value="47YS8D5TXGZBY">
<input type="image" src="https://www.paypalobjects.com/en_US/i/btn/btn_donateCC_LG.gif" border="0" name="submit" alt="PayPal - The safer, easier way to pay online!">
<img alt="" border="0" src="https://www.paypalobjects.com/en_AU/i/scr/pixel.gif" width="1" height="1">
</form>
 </td>
 -->
</tr>
</table>


			<p>2. Send an email to <img src="images/adoptions_email.gif" width="228" height="18" align="absmiddle" />. Include the following in the email</p>
			<ul>
			  <li> your name and address</li>
	    <li>your donation amount and the email/userid that made the PayPal donation </li>
	          <li>the shark you wish to adopt.</li>
		<li>the email to notify with future resightings of the shark </li>
		<li>a photo of yourself, your group, or a corporate logo</li>
		<li>a quote from you stating why whale shark research and conservation are important </li>
		</ul>
	<p>Please allow 24-48 hours after receipt of your email for processing. We are currently working to automate and speed this process through PayPal. </p>
	<p>Your adoption (photograph, name, and quote) will be displayed on the web site page for your shark, and one adoption will be randomly chosen to be displayed on the front page of whaleshark.org.</p>
	<p><em><strong>Thank you for adopting a shark and supporting our global research efforts! </strong></em></p>
	</td>
	</tr></table>
	</div>
	<!-- end maintext -->

  </div><!-- end maincol -->

<jsp:include page="footer.jsp" flush="true" />
</div><!-- end page -->
</div><!--end wrapper -->
</body>
</html>
