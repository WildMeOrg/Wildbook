<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,java.util.ArrayList,java.util.List,java.util.ListIterator,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*, org.apache.commons.lang3.StringEscapeUtils" %>
<%

//setup our Properties object to hold all properties
	Properties props=new Properties();
	String langCode=ServletUtilities.getLanguageCode(request);
	
	String context="context0";
	context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
	
	//set up the file input stream
	//FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/"+langCode+"/submit.properties"));
	//props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/whoweare.properties"));
	props=ShepherdProperties.getProperties("whoweare.properties", langCode, context);

        request.setAttribute("pageTitle", "Kitizen Science &gt; Who We Are");
%>

<jsp:include page="header.jsp" flush="true" />

<div class="container maincontent">

<%= NoteField.buildHtmlDiv("32ce5f32-fb60-491b-a537-787fa806b3c3", request, myShepherd) %>

</div>

<jsp:include page="footer.jsp" flush="true" />

