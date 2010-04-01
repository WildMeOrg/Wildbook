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
<p class="credit">All ECOCEAN Whale Shark Photo-identification
Library contents copyright ECOCEAN, ECOCEAN USA, and respective
individual contributors. Unauthorized usage of any material for any
purpose is strictly prohibited. Java and the Java Get Powered logo are
trademarks or registered trademarks of Sun Microsystems, Inc. in the
United States and other countries. Sun Microsystems, Java, Java Coffee
Cup Logo, and Duke Logo are trademarks of Sun Microsystems, Inc. used
under permission.</p>
<p class="credit">For more information about intellectual property
protection and our terms of usage, please read our <a
	href="http://www.whaleshark.org/wiki/doku.php?id=visitor_agreement"
	target="_blank">Visitor Agreement</a>.</p>
</div>
<div id="sponsors">
<p class="credit"><a href="http://java.com"><img
	src="http://<%=CommonConfiguration.getURLLocation()%>/javagetpowered.gif"
	title="Java Get Powered" alt="Java Get Powered" width="102" height="39"
	border="0" /></a></p>
</div>

<%
if(request.getParameter("noscript")==null){
%> <script src="http://www.google-analytics.com/urchin.js"
	type="text/javascript"></script> <script type="text/javascript">
			_uacct = "UA-707112-24";
			urchinTracker();
		</script> <%
}
%>
</div>
<!-- end footer -->
