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

  <!--
  <p>
    <%=props.getProperty("paragraph4") %> 
  </p>
  -->

  <p>
    <%=props.getProperty("paragraph5") %>
  </p>

  <p>
    <%=props.getProperty("paragraph6") %>
  </p>

  <p>
    <%=props.getProperty("paragraph7") %>
  </p>

</div>

<jsp:include page="footer.jsp" flush="true"/>

