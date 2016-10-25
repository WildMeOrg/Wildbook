<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities, org.ecocean.*, java.util.Properties, java.util.Date, java.util.Enumeration, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException" %>
<%


//handle some cache-related security
response.setHeader("Cache-Control", "no-cache");
//Forces caches to obtain a new copy of the page from the origin server
response.setHeader("Cache-Control", "no-store");
//Directs caches not to store the page under any circumstance
response.setDateHeader("Expires", 0);
//Causes the proxy cache to see the page as "stale"
response.setHeader("Pragma", "no-cache");
//HTTP 1.0 backward compatibility

String context="context0";
context=ServletUtilities.getContext(request);

	//language setup
	String langCode="en";
	if(session.getAttribute("langCode")!=null){langCode=(String)session.getAttribute("langCode");}

	Properties props=new Properties();
	props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/submit.properties"));


%>
<jsp:include page="header.jsp" flush="true"/>

<div class="container maincontent">



						<table><tr>
			<td valign="top"><p>You can support the ongoing research of the Wildbook for Whale Sharks photo-identification library by adopting a whale shark. A whale shark adoption allows you to:
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
			<p>The cost of your adoption is tax deductible in the United States through Wild Me, a 501(c)(3) non-profit organization.</p>
			</td>
	 <td width="400" align="left">
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
	<td width="250px" valign="top"><em>Use the button below if you would like your Adoption funds directed to Wild Me. Wild Me offers tax deductability in the United States as a 501(c)(3) nonprofit organization.</em></td>

	<!--
	<td width="250px" valign="top"><em>Use the button on the right if you would like your Adoption funds directed to ECOCEAN (Australia).</em></td>
	-->

</tr>
<tr>
<!--  
<td>
<form action="https://www.paypal.com/cgi-bin/webscr" method="post">
<input type="hidden" name="cmd" value="_s-xclick">
<input type="hidden" name="hosted_button_id" value="5075222">
<input type="image" src="https://www.paypal.com/en_US/i/btn/btn_donateCC_LG.gif" border="0" name="submit" alt="PayPal - The safer, easier way to pay online!">
<img alt="" border="0" src="https://www.paypal.com/en_US/i/scr/pixel.gif" width="1" height="1">
</form>
</td>
<--
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



	<p><em><strong>Thank you for adopting a shark and supporting our global research efforts! </strong></em></p>
	</td>
	</tr></table>
</div>
<jsp:include page="footer.jsp" flush="true" />
