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

  <div class="container">
    <div class="row">
      <div class="col-xs-3">
      </div>	
      <div class="col-xs-6">
	    <img class="bass_image" src="cust/mantamatcher/img/bass/diverJeffAboutProject.jpg" />
	    <label class="image_label"><%=props.getProperty("diverJeffImage") %></label>
      </div>
    </div>
  </div>
	
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
  
  <div class="container">
    <div class="row">
      <div class="col-xs-3">
      </div>	
      <div class="col-xs-6">
	    <img class="bass_image" src="cust/mantamatcher/img/bass/danHardingAboutProject.png" />
	    <label class="image_label"><%=props.getProperty("danHardingImage") %></label>
      </div>
    </div>
  </div>

</div>

<jsp:include page="footer.jsp" flush="true"/>

