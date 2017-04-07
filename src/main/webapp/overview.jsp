<%@ page contentType="text/html; charset=utf-8" language="java"
import="org.ecocean.*,
         org.ecocean.servlet.ServletUtilities,
         java.util.Properties
         "
%>

<%
String context=ServletUtilities.getContext(request);

Properties props = new Properties();
//Find what language we are in.
String langCode = ServletUtilities.getLanguageCode(request);
//Grab the properties file with the correct language strings.
props = ShepherdProperties.getProperties("overview.properties", langCode, context);
%>


<jsp:include page="header.jsp" flush="true"/>
<!--  <link rel="stylesheet" href="css/bassStyles.css"> -->
<div class="container maincontent">


        
        
  <h1><%=props.getProperty("mainHeader") %></h1>

  <p>
  <%=props.getProperty("paragraph1") %>
  </p>

  <p>
  <%=props.getProperty("paragraph2") %>
  </p>

  <p>
  <%=props.getProperty("paragraph3") %>  
  </p>

  <br>

  <p>
  <%=props.getProperty("paragraph4") %> 
  </p>

  <p>
  <%=props.getProperty("paragraph5") %>
  </p>

  <p>
  <%=props.getProperty("paragraph6") %>
  </p>

  <p>
  <%=props.getProperty("paragraph7") %>
  </p>

<!--
<h2>Founding Supporters</h2>

<table>
<tr><td><img src="images/logo_WHMSI.jpg" /></td><td><img src="images/OAS_Seal_ENG_Principal.gif" /></td></tr>

<tr><td><img src="images/DSWPlogoLongText.png" /></td><td><img src="images/caribwhale-logo.jpg" /></td></tr>
<tr>
	<td><a href="http://www.cresli.org"><img width="350px" height="*" src="images/cresli.jpg" /></a></td>
	<td>&nbsp;</td>
</tr>
</table>

-->
</div>

<jsp:include page="footer.jsp" flush="true"/>

