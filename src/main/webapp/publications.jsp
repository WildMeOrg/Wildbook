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

		  <h1>Publications</h1>
		  <ul>
		  <li><a href="#acknowl">Acknowledging Wildbook for Zebras in a publication</a></li>
		  <li><a href="#scipubs">Scientific publications</a></li>
		  </ul>
	
		
		<p>&nbsp;</p>
	
		<a name="acknowl"></a><strong>Acknowledging Wildbook for Zebras in a publication</strong>
		<p><em>If use of the Wildbook for Zebras library made a significant contribution to a research project, please make the following acknowledgement in any resulting publication: </em></p>
		<p>This research has made use of data and software tools provided by <em>Wildbook for Zebras</em>, an online mark-recapture database operated by the non-profit scientific organization <em>Wild Me</em></p>
		
		<p>&nbsp;</p>.
	
		<a name="scipubs"></a><strong>Scientific publications</strong>
		<p><em>The following reports and publications have either directly used data from Wildbook for Zebras or contributed to its ultimate development and launch.</em></p>
		
		<p>Parham, Jason & Stewart, Charles & Crall, J.P. & Rubenstein, Daniel & Holmberg, Jason & Berger-Wolf, Tanya. (2018). An Animal Detection Pipeline for Identification. 1075-1083. 10.1109/WACV.2018.00123.<br>
		<a href="https://cthulhu.dyn.wildme.io/public/posters/parham_wacv_2018.pdf">Web link</a>
		</p>
		
	
			   </div>
<jsp:include page="footer.jsp" flush="true" />