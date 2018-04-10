<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*, java.util.Properties" %>


<%

String context="context0";
context=ServletUtilities.getContext(request);

  //setup our Properties object to hold all properties
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  


//set up the file input stream
  Properties props = new Properties();
  //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/login.properties"));
  props = ShepherdProperties.getProperties("login.properties", langCode,context);


%>


  <!-- Make sure window is not in a frame -->

  <script language="JavaScript" type="text/javascript">

    <!--
    if (window.self != window.top) {
      window.open(".", "_top");
    }
    // -->

  </script>

 <jsp:include page="header.jsp" flush="true"/>

<div class="container maincontent">

    
              <h1><%=props.getProperty("resetPassword")%>
              </h1>

              <p><%=props.getProperty("resetPasswordDescription")%>
              </p>

              <p>

              
              <form action="UserResetPasswordSendEmail" method="post">
    <table align="left" border="0" cellspacing="0" cellpadding="3">
        <tr>
            <td><%=props.getProperty("usernameOrEmail") %></td>
            <td><input type="text" name="username" maxlength="50" /></td>
        </tr>
   
        
        <tr>
            <td colspan="2" align="left"><input type="submit" name="submit" value="<%=props.getProperty("reset") %>" /></td>
        </tr>
    </table>
</form>
              
              </p>


              <p>&nbsp;</p>

              </td>
              </tr>
              </table>

              <p>&nbsp;</p>
            </div>
           <jsp:include page="footer.jsp" flush="true"/>
        