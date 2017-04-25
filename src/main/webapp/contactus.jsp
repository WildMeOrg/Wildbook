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

    <jsp:include page="footer.jsp" flush="true"/>

