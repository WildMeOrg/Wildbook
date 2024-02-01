<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.*, java.util.Properties,org.ecocean.servlet.ServletUtilities" %>

<%

String context="context0";
context=ServletUtilities.getContext(request);

  //setup our Properties object to hold all properties
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
    


//set up the file input stream
  Properties props = new Properties();
 // props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/login.properties"));
  props = ShepherdProperties.getProperties("login.properties", langCode,context);


%>

  <script language="JavaScript" type="text/javascript">

    <!--
    if (window.self != window.top) {
      window.open(".", "_top");
    }
    // -->

  </script>
   <jsp:include page="header.jsp" flush="true"/>
<div class="container maincontent">
  

              <h1>Access Denied</h1>

              <p align="left">You do not have permission to access this resource or execute this command.
              </p>

 
              
            </div>
          <jsp:include page="footer.jsp" flush="true"/>
       