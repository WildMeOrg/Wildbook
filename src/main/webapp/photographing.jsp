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
		  <h2>How to Photograph</h2>
		  <h3>Ideal Photo for ID</h3>
			
			<br>
			<h5>The image should:</h5>
			<ol>
				<li>Be at right angles to the left or right hand flank (side) of the fish.</li>
				<li>Be clear enough to show spots and markings on the skin of the giant sea bass.</li>
				<li>Not be over-exposed. Over-exposure makes the marks difficult to differentiate.</li>
				<li>Be captured in the highest resolution for your camera type.</li>
				<li>Be submitted in jpeg format, preferably no larger than 2 Mb.</li>
			</ol>
			
			<br>
			<h5>Method for photographing Giant Sea Bass</h5>
			<ol>
				<li>Approach dive site in a calm and controlled manner.</li>
				<li>Position yourself in a suitable location to obtain clear images of giant sea bass.</li>
				<li>Not be over-exposed. Over-exposure makes the marks difficult to differentiate.</li>
				<li>Be patient, remain as still as possible in order to allow the giant sea bass to swim past you in their natural pattern.</li>
				<li>Do not chase or harm the giant sea bass.</li>
			</ol>
			
			<h3>Examples of Good Photographs</h3>
			
			<p>[Some images]</p>
			
			<br>
			<h3>Image Ownership</h3>
			<p>We only require these images for research purposes. The ownership of these images always remains with you. </p>
		
			<p>&nbsp;</p>
	</div>
	

<jsp:include page="footer.jsp" flush="true" />

