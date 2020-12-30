<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.*,java.util.Properties, org.ecocean.servlet.ServletUtilities" %>
<%
  //setup our Properties object to hold all properties

  String langCode = ServletUtilities.getLanguageCode(request);
  String context=ServletUtilities.getContext(request);

//setup our Properties object to hold all properties
  Properties props=new Properties();

  props=ShepherdProperties.getProperties("contactus.properties", langCode, context);

%>

<jsp:include page="header.jsp" flush="true"/>
<div class="container maincontent">
    <h1 class="intro"><%=props.getProperty("contactHeader") %></h1>

    <p><%=props.getProperty("contactInstructions") %></p>

	<br>

	<p><%=props.getProperty("contactDetails") %></p>

      <!-- end maintext -->
    <br>

</div>

<h2>Logos</h2>
The following logos may be used in conjunction with our project.

<h3>Wild Me</h3>

<p><img src="images/WildMe-Logo-04.png" width="500px" height="*" /></p>

<h3>Wildbook&reg;</h3>

<p><img src="images/WildBook_logo_300dpi-04.png" width="500px" height="*" /></p>



      <!-- end maintext -->
      </div>
