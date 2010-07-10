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

</div>
<p class="credit">This software is distributed under the <a href="http://www.gnu.org/licenses/gpl-2.0.html" target="_blank">GPL v2 license</a> and is intended to support mark-recapture field studies. Open source and commercially licensed products used in this framework are listed <a href="http://<%=CommonConfiguration.getURLLocation()%>/thirdparty.jsp">here</a>.</p>
<p><a href="http://www.ecoceanusa.org" target="_blank"><img border="0" src="http://<%=CommonConfiguration.getURLLocation()%>/images/lib_bottom.gif"></a></p>


</div>
<!-- end footer -->
