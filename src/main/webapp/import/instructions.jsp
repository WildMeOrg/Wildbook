<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,
	java.util.ArrayList,
	java.util.List,
        org.joda.time.DateTime,
	java.util.Collection,
	java.io.File,
	org.ecocean.*,
	org.ecocean.resumableupload.UploadServlet,
	java.util.Properties,
	org.slf4j.Logger,
	org.slf4j.LoggerFactory,
	org.apache.commons.lang3.StringEscapeUtils,
	org.apache.commons.io.FileUtils" %>

<%
String context = ServletUtilities.getContext(request);
Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("import.jsp");
String langCode=ServletUtilities.getLanguageCode(request);
System.out.println("Starting import.jsp !");

String subdir = UploadServlet.getSubdirForUpload(myShepherd, request);
UploadServlet.setSubdirForUpload(subdir, request);

String dirName = UploadServlet.getUploadDir(request);

String wbName = ContextConfiguration.getNameForContext(context);

%>
<jsp:include page="../header.jsp" flush="true"/>
<style> 
.import-explanation {
}
.import-header {
	margin-top: 0px;
}
.warning {
  border-radius: 5px;
  background-color: lightgrey;
  color: red;
  padding: 5px;
}
</style>


<div class="container maincontent">

  <h1 class="import-header">Bulk Import: Instructions</h1>

  <p class="warning">
    <strong>This feature is in Alpha.</strong> This means it is untested and unreleased. Please only use this tool if you are willing to help us test the UI and report bugs. Your data might not be imported correctly until we have done further testing and development.
  </p>

  <p>The goal of this tool is to allow scientists to add large amounts of data to <%=wbName%> at once, such as an entire season's observations, while performing the data-blending and data-integrity checks themselves.</p>
  
  <p>To ensure data integrity, this process is split into several steps with review in-between.</p>
  
  <ol>
  	<li><h5>Photo Upload</h5></li>
  	<li><h5>Spreadsheet Upload</h5></li>
  	<li><h5>Import Process</h5></li>
  </ol>

  <p>Each page has instructions, and you must complete the steps in order.</p>


  <h3>Preparation: The Wildbook Standard Format</h3>

  <p>
  	As a data collector or curator, your job to prepare for this import is to collect all of your images into one folder, and especially to transform your associated sightings data into a <em>Wildbook Standard Format</em> excel sheet.
  </p>

  <p>
  	The Wildbook Standard Format is very simple, and perfectly mirrors the Wildbook data model: each row in your .xlsx file corresponds to one Encounter on <%=wbName%>. Each column header is of the form <code>ClassName.fieldName</code>, for example, <code>Encounter.locationID</code>. Column order does not matter and empty columns are ignored.
  </p>

  <p>Most importantly, <strong>the <code>Encounter.mediaAsset</code> column(s) must contain the <em>exact</em> filename(s) of the photo(s)</strong> associated with each record. These are the names of the photos uploaded in the Photo Upload step, and this is how the computer identifies which photo goes where.</p>

  <p>If you do not have a value for something, please leave it blank or remove the column. Values like "N/A" added to a number field will be rejected.</p>

  <p>

<%
String rootDir = getServletContext().getRealPath("/");
File xlsFile = org.ecocean.servlet.importer.StandardImport.importXlsFile(rootDir);
if (xlsFile == null) {
%>
    <b class="error">There was an error finding the latest <b>Wildbook Standard Format XLS</b> file.  Please contact your admin.</b>
    </p><p>
<% } else {
        DateTime dt = new DateTime(xlsFile.lastModified());
%>
  	<a href="<%=xlsFile.getName()%>">Download the <b>Wildbook Standard Format XLS template</b> here.</a>
        <i>("<%=xlsFile.getName()%>", updated <%=dt.toString().substring(0,10)%>)</i>
<% } %>

Descriptions of each class field are included. You can use this file for your upload after filling it out.
  </p>

  <p>When your data is prepared, get started on the Photo Upload page:</p>

  <div>
	<form method="GET" action="photos.jsp">
		<input type="submit" value="Upload photos">
	</form>
  </div>
          
</div>

<jsp:include page="../footer.jsp" flush="true"/>

