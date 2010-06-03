<%@ page contentType="text/html; charset=utf-8" language="java"
	import="java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*"%>

<%

//setup our Properties object to hold all properties
	Properties props=new Properties();
	String langCode="en";
	
	//check what language is requested
	if(request.getParameter("langCode")!=null){
		if(request.getParameter("langCode").equals("fr")) {langCode="fr";}
		if(request.getParameter("langCode").equals("de")) {langCode="de";}
		if(request.getParameter("langCode").equals("es")) {langCode="es";}
	}
	
	//set up the file input stream
	//props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/overview.properties"));



%>
<div id="footer">
<div id="credits">
<p class="credit">All contents under copyright. Unauthorized usage of any material for any
purpose is strictly prohibited.</p>
</div>
<div id="sponsors">

</div>

</div>
<!-- end footer -->
