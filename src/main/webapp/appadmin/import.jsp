<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="java.util.Locale" %>
<%@ page import="java.util.Properties" %>
<%@ page import="org.ecocean.ShepherdProperties" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<%
  String context = ServletUtilities.getContext(request);
  String langCode = ServletUtilities.getLanguageCode(request);
  Locale locale = new Locale(langCode);
  Properties props = ShepherdProperties.getProperties("admin.properties", langCode, context);
%>

    <jsp:include page="../header.jsp" flush="true" />

    <div class="container maincontent">
 

        
          <h1><%=props.getProperty("import.title")%></h1>
      

        <p><%=props.getProperty("import.text")%></p>
		<p><strong><%=props.getProperty("import.srgd.title")%></strong></p>
        <p><%=props.getProperty("import.srgd.text")%></p>
		<p><img src="../images/Warning_icon.png" width="25px" height="*" align="absmiddle" /> <em><%=props.getProperty("import.srgd.warning")%></em></p>
		<p>
		<!--  ignore this comment -->
		<table>
  <tr>
    <td class="para">
      <form action="../ImportSRGD" method="post" enctype="multipart/form-data" name="ImportSRGD">
	   <strong>
	   <img align="absmiddle" src="../images/CSV.png"/> <%=props.getProperty("import.srgd.file")%></strong>&nbsp;
        <input name="file2add" type="file" size="40" />
        <p><input name="addtlFile" type="submit" id="addtlFile" value="<%=props.getProperty("import.srgd.submit")%>" /></p>
		</form>
    </td>
  </tr>
</table>

      </div>

    

    <jsp:include page="../footer.jsp" flush="true"/>

