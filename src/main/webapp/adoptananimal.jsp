<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException" %>
<%

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


		  <h1 class="intro">Adopt an Animal</h1>
	
			
			<p>You can support the ongoing research of our team by adopting one of our animals. You can provide program specifics here.</p>
		
	   </div>
<jsp:include page="footer.jsp" flush="true" />

