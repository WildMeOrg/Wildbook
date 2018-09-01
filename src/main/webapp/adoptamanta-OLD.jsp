<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.ArrayList" %>
<%
	String context="context0";
	context=ServletUtilities.getContext(request);
	String langCode=ServletUtilities.getLanguageCode(request);
	Properties props = ShepherdProperties.getProperties("adopt.properties", langCode, context);

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


	<h1 class="intro"><%=props.getProperty("section1_title")%></h1>


	<p><%=props.getProperty("section1_text1")%></p>
	<ul>
		<li><%=props.getProperty("section1_list1_item1")%></li>
		<li><%=props.getProperty("section1_list1_item2")%></li>
		<li><%=props.getProperty("section1_list1_item3")%></li>
	</ul>
	<p><%=props.getProperty("section1_text2")%></p>
	<p><%=props.getProperty("section1_text3")%></p>
	<ul>
		<li><%=props.getProperty("section1_list2_item1")%></li>
		<li><%=props.getProperty("section1_list2_item2")%></li>
		<li><%=props.getProperty("section1_list2_item3")%></li>
		<li><%=props.getProperty("section1_list2_item4")%></li>
	</ul>
	<p><%=props.getProperty("section1_text4")%></p>

	<table><tr><td>
		<h3><%=props.getProperty("section1a_title")%></h3>
		<p><%=props.getProperty("section1a_text1")%></p>
		<ol>
			<li><%=props.getProperty("section1a_list1_item1")%>
				<form action="https://www.paypal.com/cgi-bin/webscr" method="post">
					<input type="hidden" name="cmd" value="_s-xclick">
					<input type="hidden" name="hosted_button_id" value="QFCG98BD8DS5Q">
					<input type="image" src="https://www.paypalobjects.com/en_US/i/btn/btn_donateCC_LG.gif" border="0" name="submit" alt="PayPal - The safer, easier way to pay online!">
					<img alt="" border="0" src="https://www.paypalobjects.com/en_US/i/scr/pixel.gif" width="1" height="1">
				</form>
			</li>
			<li><%=props.getProperty("section1a_list1_item2")%>
				<ul>
					<li><%=props.getProperty("section1a_list1a_item1")%></li>
					<li><%=props.getProperty("section1a_list1a_item2")%></li>
					<li><%=props.getProperty("section1a_list1a_item3")%></li>
					<li><%=props.getProperty("section1a_list1a_item4")%></li>
					<li><%=props.getProperty("section1a_list1a_item5")%></li>
					<li><%=props.getProperty("section1a_list1a_item6")%></li>
				</ul>
			</li>
		</ol>
		<p><%=props.getProperty("section1a_text2")%></p>
		<p><%=props.getProperty("section1a_text3")%></p>
		<p><em><strong><%=props.getProperty("section1a_text4")%></strong></em></p>
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
			<tr><td><%=props.getProperty("quote_question")%></td></tr>
			<tr><td><em>"<%=exampleAdoption.getAdopterQuote()%>"</em></td></tr>
			<tr><td>&nbsp;</td></tr>
<%	} %>

		</table>

	  <p align="center">
			<strong><%=props.getProperty("sample_for")%> &ldquo;<%=exampleAdoption.getMarkedIndividual()%>&rdquo;</strong>.<br/>
			<strong><a href="http://<%=CommonConfiguration.getURLLocation(request)%>/individuals.jsp?number=<%=exampleAdoption.getMarkedIndividual()%>">Click here to see it on the manta page.</a></strong>
		</p>
	  </td></tr></table>
<%	} %>
	</div>
	
<jsp:include page="footer.jsp" flush="true" />

