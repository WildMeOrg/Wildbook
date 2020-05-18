<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException" %>
<%

String context="context0";
context=ServletUtilities.getContext(request);
	
	//language setup
	String langCode="en";
	if(session.getAttribute("langCode")!=null){langCode=(String)session.getAttribute("langCode");}

	Properties props=new Properties();
	props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/header.properties"));

	
	
%>

<jsp:include page="header.jsp" flush="true"/>

<div class="container maincontent">

    <h2><%=props.getProperty("privacyPolicy")%></h2>

    <hr/>

    <embed src="cust/20200515_ACW privacy_policy final.pdf" type="application/pdf" width="100%" height="1000px" />

    <hr/>

</div>

<jsp:include page="footer.jsp" flush="true"/>

