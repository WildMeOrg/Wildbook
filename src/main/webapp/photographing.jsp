<%@ page contentType="text/html; charset=utf-8" language="java"
import="org.ecocean.*,
         org.ecocean.servlet.ServletUtilities,
         java.util.Properties
         "
%>

<%
String context=ServletUtilities.getContext(request);

Properties props = new Properties();
//Find what language we are in.
String langCode = ServletUtilities.getLanguageCode(request);
//Grab the properties file with the correct language strings.
props = ShepherdProperties.getProperties("photographing.properties", langCode, context);
%>


<jsp:include page="header.jsp" flush="true"/>

<div class="container maincontent">
		  <h2><%=props.getProperty("mainHeader")%></h2>
		  <h3><%=props.getProperty("subHeader")%></h3>
			
			<br>
			<h5><%=props.getProperty("list1Header")%></h5>
			<ol>
				<li><%=props.getProperty("list1Item1")%></li>
				<li><%=props.getProperty("list1Item2")%></li>
				<li><%=props.getProperty("list1Item3")%></li>
				<li><%=props.getProperty("list1Item4")%></li>
				<li><%=props.getProperty("list1Item5")%></li>
			</ol>
			
			<br>
			<h5><%=props.getProperty("list2Header")%></h5>
			<ol>
				<li><%=props.getProperty("list2Item1")%></li>
				<li><%=props.getProperty("list2Item2")%></li>
				<li><%=props.getProperty("list2Item3")%></li>
				<li><%=props.getProperty("list2Item4")%></li>
			</ol>
			
			<h3><%=props.getProperty("exampleHeader")%></h3>
			
			<p>[Some images]</p>
			
			<br>
			<h3><%=props.getProperty("ownershipHeader")%></h3>
			<p><%=props.getProperty("ownershipBody")%></p>
		
			<p>&nbsp;</p>
	</div>
	

<jsp:include page="footer.jsp" flush="true" />

