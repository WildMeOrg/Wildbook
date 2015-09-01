<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.ArrayList" %>
<%

String context="context0";
context=ServletUtilities.getContext(request);
	
	//language setup
	String langCode="en";
	if(session.getAttribute("langCode")!=null){langCode=(String)session.getAttribute("langCode");}

	Properties props=new Properties();
	props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/submit.properties"));

	// Find adoptions from which to pick an example to display.
	Shepherd myShepherd = new Shepherd(context);
	ArrayList<Adoption> adoptions = new ArrayList<Adoption>();
	for (Iterator<Adoption> iter = myShepherd.getAllAdoptionsNoQuery(); iter.hasNext();) {
		Adoption x = iter.next();
		// Filter for interesting adoptions.
		if (x.getMarkedIndividual() != null && !x.getMarkedIndividual().trim().equals("") &&
						x.getAdopterImage() != null && !x.getAdopterImage().trim().equals("") &&
						x.getAdopterName() != null && !x.getAdopterName().trim().equals("") &&
						x.getAdopterQuote() != null && !x.getAdopterQuote().trim().equals("")
				)
			adoptions.add(x);
	}
	Adoption exampleAdoption = (adoptions.isEmpty()) ? null : adoptions.get((int)(Math.random() * adoptions.size()));

%>
<jsp:include page="header.jsp" flush="true"/>
<style type="text/css">
	<!--
	.style1 {
		font-weight: bold
	}

	table.adopter {
		border-width: 0px 0px 0px 0px;
		border-spacing: 0px;
		border-style: solid solid solid solid;
		border-color: black black black black;
		border-collapse: separate;

	}

	table.adopter td {
		border-width: 1px 1px 1px 1px;
		padding: 3px 3px 3px 3px;
		border-style: none none none none;
		border-color: gray gray gray gray;
		background-color: #D7E0ED;
		-moz-border-radius: 0px 0px 0px 0px;
		font-size: 12px;
		font-weight: bold;
		color: #330099;
	}

	table.adopter td.name {
		font-size: 12px;
		text-align: center;
		background-color: #D7E0ED;

	}

	table.adopter td.image {
		padding: 0px 0px 0px 0px;

	}

	.style2 {
		font-size: x-small;
		color: #000000;
	}

	-->
</style>

<div class="container maincontent">


		  <h1 class="intro">Adopt a Manta</h1>
	
			
			<p>You can support the ongoing research of MantaMatcher by adopting a manta ray. A manta adoption allows you to:</p>
			<ul>
			  <li>support cutting-edge manta research through MantaMatcher</li>
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
			<p>The cost of your adoption is tax deductible in the United States through Wild Me, a 501(c)(3) non-profit organization.</p>
			
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
	<p>Your adoption (photograph, name, and quote) will be displayed on the web site page for your manta, and one adoption will be randomly chosen to be displayed on the front page of mantamatcher.org.</p>
	<p><em><strong>Thank you for adopting a manta and supporting our global research efforts! </strong></em></p>
	</td>

<%	if (exampleAdoption != null) { %>
	<td width="200" align="left">
		<table class="adopter" bgcolor="#D7E0ED" style="background-color:#D7E0Ed " width="190px">
			<tr><td class="image"><img border="0" src="images/meet-adopter-frame.gif"/></td></tr>
<%	if ((exampleAdoption.getAdopterImage() != null) && (!exampleAdoption.getAdopterImage().trim().equals(""))) { %>
			<tr><td class="image" style="padding-top: 0px;"><img width="188px" src="/<%=CommonConfiguration.getDataDirectoryName(context) %>/adoptions/<%=exampleAdoption.getID()%>/thumb.jpg"/></td></tr>
<%	} %>


			<tr>
				<td class="name">
					<center><strong><font color="#282460" size="+1"><%=exampleAdoption.getAdopterName()%></font></strong></center>
				</td>
			</tr>
			<tr><td>&nbsp;</td></tr>
<%	if (exampleAdoption.getAdopterQuote() != null && !exampleAdoption.getAdopterQuote().trim().equals("")) { %>
			<tr><td>Why are research and conservation for this species important?</td></tr>
			<tr><td><em>"<%=exampleAdoption.getAdopterQuote()%>"</em></td></tr>
			<tr><td>&nbsp;</td></tr>
<%	} %>

		</table>

	  <p align="center">
			<strong>Sample adoption for manta &ldquo;<%=exampleAdoption.getMarkedIndividual()%>&rdquo;</strong>.<br/>
			<strong><a href="http://<%=CommonConfiguration.getURLLocation(request)%>/individuals.jsp?number=<%=exampleAdoption.getMarkedIndividual()%>">Click here to see it on the manta page.</a></strong>
		</p>
	  </td></tr></table>
<%	} %>
	</div>
	
<jsp:include page="footer.jsp" flush="true" />

