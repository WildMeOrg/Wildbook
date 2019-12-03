<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,
	java.util.ArrayList,
	java.util.List,
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
//Shepherd myShepherd=new Shepherd(context);
//myShepherd.setAction("import.jsp");
String langCode=ServletUtilities.getLanguageCode(request);
System.out.println("Starting import.jsp !");

String subdir = UploadServlet.getSubdirForUpload(request);
UploadServlet.setSubdirForUpload(subdir, request);

String dirName = UploadServlet.getUploadDir(request);




File uploadDir = null;
Collection<File> contents = null;
int nImages = 0;

try {
	uploadDir = new File(dirName);
	contents = FileUtils.listFiles(uploadDir, null, true);
} catch (Exception e) {
	System.out.println("Exception! On ReviewDirectory.jsp!!!");
	e.printStackTrace();
}


List<File> imageFiles = new ArrayList<File>();
if (contents!=null) {
	for (File f: contents) {
		String name = f.getName().toLowerCase();
		// I will never use regex. Never!!!! READABILITY SHALL REIGN ETERNAL
		if (name.endsWith(".jpg")||name.endsWith(".png")||name.endsWith(".jpeg")) {
			imageFiles.add(f);
		}
	}
}
nImages = imageFiles.size();


%>
<jsp:include page="../header.jsp" flush="true"/>
<style> 
.import-explanation {
}
.import-header {
	margin-top: 0px;
}

ol.filelist {
    font-size: 0.9em;
    width: 40%;
    max-height: 60%;
    overflow-y: auto;
    display: inline-block;
    color: #666;
    padding: 0 0 0 30px;
}

ol.filelist li {
    background-color: #EEA;
    margin: 3px 0;
    padding: 0 12px;
}

</style>


<div class="container maincontent">

  <h1 class="import-header">Bulk Import: Photo Review</h1>
  <p class="import-explanation">Please confirm that you have uploaded all the images in this import</p>

  <p class="info"><b><%=nImages %> images found:</b></p>
    <ol class="filelist">
  	<% 
  	for (File photo: imageFiles) {
  		%>
  		<li><%=photo.getName()%></li>
  		<%
	}
  	%>

    </ol>
  <div>
		<input style="background-color: #CCC;" onClick="document.location='photos.jsp';" type="submit" value="Go back and try again">
		<input onClick="document.location.href='spreadsheet.jsp';" type="submit" value="Accept and move on">
  </div>

          
</div>

<jsp:include page="../footer.jsp" flush="true"/>

