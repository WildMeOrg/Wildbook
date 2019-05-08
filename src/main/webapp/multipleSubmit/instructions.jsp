<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*" %>
<%

//setup our Properties object to hold all properties
	
	
	String context = ServletUtilities.getContext(request);
	String lang = ServletUtilities.getLanguageCode(request);
	Properties props = new Properties();
  props = ShepherdProperties.getProperties("instructionsMultiSubmit.properties", lang, context);

%>


<jsp:include page="header.jsp" flush="true"/>

<div class="container maincontent">
		  <h2>How to use mutiple submit.</h2>
	
			<p>Stuff.</p>

			<p>&nbsp;</p>
		
			<p>&nbsp;</p>
	</div>
	

<jsp:include page="footer.jsp" flush="true" />

