<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"
	import="java.util.Properties, java.lang.Exception, org.ecocean.*"%>
<%
    
    
    String overview_language = "Select Language";
    String langCode="en";
    if(request.getParameter("langCode")!=null){
    	langCode=request.getParameter("langCode");
    }
    else if(session.getAttribute("langCode")!=null){
    	langCode=(String)session.getAttribute("langCode");
    }
    
    session.setAttribute("langCode",langCode);
    
	//set up the file input stream
	Properties props=new Properties();
	
	try{
		props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/overview.properties"));
		overview_language=props.getProperty("overview_language");
	}
	catch(Exception langEx){
		langEx.printStackTrace();
	}
	
	Shepherd myShepherd = new Shepherd();
    
    %>
<a name="lang" />
<div class="module">
<h3><%=overview_language%></h3>
<a href="?langCode=en#lang"><img src="images/flag_en.gif" width="19"
	height="12" border="0" title="English" alt="English" /></a> <a
	href="?langCode=de" title="Auf Deutsch"><img
	src="http://<%=CommonConfiguration.getURLLocation() %>/images/flag_de.gif"
	width="19" height="12" border="0" title="Deutsch" alt="Deutsch" /></a> <a
	href="?langCode=fr#lang" title="En fran&ccedil;ais"><img
	src="http://<%=CommonConfiguration.getURLLocation() %>/images/flag_fr.gif"
	width="19" height="12" border="0" title="Fran&ccedil;ais"
	alt="Fran&cedil;ais" /></a> <a href="?langCode=es#lang"
	title="En espa&ntilde;ol"><img
	src="http://<%=CommonConfiguration.getURLLocation() %>/images/flag_es.gif"
	width="19" height="12" border="0" title="Espa&ntilde;ol"
	alt="Espa&ntilde;ol" /></a></div>