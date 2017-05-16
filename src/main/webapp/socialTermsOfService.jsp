<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.*,java.util.Properties, org.ecocean.servlet.ServletUtilities" %>
<%
  //setup our Properties object to hold all properties
  
  String langCode = ServletUtilities.getLanguageCode(request);
  String context=ServletUtilities.getContext(request);

//setup our Properties object to hold all properties
  Properties props=new Properties();

  props=ShepherdProperties.getProperties("socialTermsOfService.properties", langCode, context);
    	
%>

<jsp:include page="header.jsp" flush="true"/>
<div class="container maincontent">
    <h1 class="intro"><%=props.getProperty("termsHeader") %></h1>
     
    <p><%=props.getProperty("termsOfService") %></p>
   
      <!-- end maintext -->
    <br>  
    
</div>

    <jsp:include page="footer.jsp" flush="true"/>

