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

	<p>&nbsp;</p>

		<h2><strong>Acknowledging Internet of Turtles in a publication</strong></h2>
		<br>
		<p><em>If use of the Internet of Turtles library made a significant contribution to a research project, please make the following acknowledgement in any resulting publication: </em></p>
		<br>
		<p><b>This research has made use of data and software tools provided by <em>Internet of Turtles</em>, an online mark-recapture database operated by the non-profit scientific organization <em>Wild Me.</em></b></p>

	<p>&nbsp;</p>
	
		
</div>
<jsp:include page="footer.jsp" flush="true" />