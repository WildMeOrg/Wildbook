<%@ page contentType="text/html; charset=iso-8859-1" language="java"
         import="org.ecocean.CommonConfiguration,org.ecocean.servlet.ServletUtilities" %>
<%@ page import="org.ecocean.Shepherd" %>

<%

String context="context0";
context=ServletUtilities.getContext(request);
  Shepherd myShepherd = new Shepherd(context);

%>

    <jsp:include page="../header.jsp" flush="true" />
    
    
       <div class="container maincontent">
      <p><font size="+1"><strong>Do you want to leave the
        <%=request.getParameter("name")%> mailing list?</strong></font></p>

      <p>You have followed a link that will remove you from the <%=request.getParameter("name")%>
        mailing list. Do you want to be removed?</p>

      <form action="../MailHandler" method="post" name="optOut" id="optOut">
        <div align="center"><input name="action" type="hidden"
                                   value="removeEmail"> <input name="address" type="hidden"
                                                               value="<%=request.getParameter("address")%>">
          <input
            name="name" type="hidden" value="<%=request.getParameter("name")%>">
          <input name="remover" type="submit" id="remover"
                 value="Remove My E-mail From This List"></div>
      </form>
      <br> 
</div>

      <jsp:include page="../footer.jsp" flush="true"/>



