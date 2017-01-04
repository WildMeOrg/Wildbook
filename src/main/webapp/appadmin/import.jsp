<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,java.util.Properties" %>
<%

String context="context0";
context=ServletUtilities.getContext(request);
  //setup our Properties object to hold all properties
  Properties props = new Properties();
 // String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  


  //set up the file input stream
  //FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/"+langCode+"/submit.properties"));
   //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/submit.properties"));
   props=ShepherdProperties.getProperties("submit.properties", langCode,context);



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

