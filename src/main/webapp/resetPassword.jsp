<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="java.util.Properties" %>
<%@ page import="org.ecocean.*" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<jsp:include page="header.jsp" flush="true"/>
<%
  String context = ServletUtilities.getContext(request);
  String langCode = ServletUtilities.getLanguageCode(request);
  Properties props = ShepherdProperties.getProperties("login.properties", langCode, context);
%>


  <!-- Make sure window is not in a frame -->

  <script language="JavaScript" type="text/javascript">

    <!--
    if (window.self != window.top) {
      window.open(".", "_top");
    }
    // -->

  </script>

<div class="container maincontent">

    
              <h1><%=props.getProperty("resetPassword")%>
              </h1>

              <p><%=props.getProperty("resetPasswordDescription")%>
              </p>

              <p>

              
              <form action="UserResetPasswordSendEmail" method="post">
    <table align="left" border="0" cellspacing="0" cellpadding="3">
        <tr>
            <td><%=props.getProperty("usernameOrEmail")%>:</td>
            <td><input type="text" name="username" maxlength="50" /></td>
        </tr>
   
        
        <tr>
            <td colspan="2" align="left"><input type="submit" name="submit" value="<%=props.getProperty("reset")%>" /></td>
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
        