<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<%
  String context = "context0";
  context = ServletUtilities.getContext(request);
//  String langCode = ServletUtilities.getLanguageCode(request);
//  Properties props = ShepherdProperties.getProperties("thirdparty.properties", langCode, context);
%>

    <jsp:include page="../header.jsp" flush="true" />

    <div class="container maincontent">
 

        
          <h1>Data Import</h1>
      

        <p>Use the following forms to import data into the Shepherd Project.</p>
		<p><strong>SRGD Data Import</strong></p>
        <p>The SRGD data format was developed under the GeneGIS initiative and can be used to import genetic and identity data into the Shepherd Project.</p>
		<p><img src="../images/Warning_icon.png" width="25px" height="*" align="absmiddle" /> <em>Importing an SRGD file may override existing data and cause data loss.</em></p>
		<p>
		<!--  ignore this comment -->
		<table>
  <tr>
    <td class="para">
      <form action="../ImportSRGD" method="post" enctype="multipart/form-data" name="ImportSRGD">
	   <strong>
	   <img align="absmiddle" src="../images/CSV.png"/> SRGD CSV file:</strong>&nbsp;
        <input name="file2add" type="file" size="40" />
        <p><input name="addtlFile" type="submit" id="addtlFile" value="Upload" /></p>
		</form>
    </td>
  </tr>
</table>

      </div>

    

    <jsp:include page="../footer.jsp" flush="true"/>

