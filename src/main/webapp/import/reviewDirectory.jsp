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

String urlLoc = "//" + CommonConfiguration.getURLLocation(request);

File uploadDir = null;
Collection<File> contents = null;
int totalImages = 0;
int newImages = 0;

try {
	uploadDir = new File(dirName);
	if (!uploadDir.isDirectory()) uploadDir.mkdirs();
	contents = FileUtils.listFiles(uploadDir, null, true);
} catch (Exception e) {
	System.out.println("Exception! On ReviewDirectory.jsp!!!");
	e.printStackTrace();
}


List<File> imageFiles = new ArrayList<File>();
if (contents!=null) {
	for (File f: contents) {
		String name = f.getName().toLowerCase();
		if (name.endsWith(".jpg")||name.endsWith(".png")||name.endsWith(".jpeg")) {
			imageFiles.add(f);
		}
	}
}

List<String> latestFiles = new ArrayList<String>();
List<String> previousFiles = new ArrayList<String>();

long latestUpload = 1L;
long currentTime = System.currentTimeMillis();
long hour = 3600000;

for (File f : imageFiles) {
	long modified = f.lastModified();
	if (modified>latestUpload) latestUpload = modified;
}
for (File f : imageFiles) {
	// "recent" uploaded images in last 12 hours
	if (f.lastModified()>(latestUpload-(hour*12))) {
		latestFiles.add(f.getName());
	} else {
		previousFiles.add(f.getName());
	}	
}

totalImages = imageFiles.size();
newImages = latestFiles.size();

%>
<jsp:include page="../header.jsp" flush="true"/>
<%-- <style> 
.import-explanation {
}
.import-header {
	margin-top: 0px;
}

ol.filelist {
    font-size: 0.9em;
	/*
	width: 40%;
	overflow: auto;
	min-height: 500px;
	max-height: 600px;
	max-height: 60%;
	overflow-y: auto;
	overflow-x: scroll;
    display: inline-block;
	*/
    color: #666;
    padding: 0 0 0 30px;
}

ol.filelist li {
    background-color: #EEA;
    margin: 3px 0;
    padding: 0 12px;
}

#filename-boxed-list {
	min-height: 400px;
	max-height: 500px;
	overflow: scroll;
	overflow-x: hidden;
}

</style> --%>

<link rel="stylesheet" href="<%=urlLoc %>/import/bulkUploader.css" />


<div class="container maincontent">

  <h1 class="import-header">Bulk Import: Photo Review</h1>
  <p class="import-explanation"><b>These are the images currently uploaded by your account:</b></p>
  <%-- <p>This includes images uploaded in the past.</p> --%>

  <p class="info"><b><%=totalImages %> total images found</b></p>
  <p class="info"><b><%=newImages %> new images found (last 12 hours)</b></p>

	<div id="filename-boxed-list">
    	<ol class="filelist">
  		<% 
		for (String photo : latestFiles) {

			%>
  				<li><label class="new-upload-image">NEW</label>&nbsp<%=photo%></li>
  			<%

		}
  		for (String photo : previousFiles) {
  			%>
  				<li><%=photo%></li>
  			<%
		}
  		%>

    	</ol>
	</div>
  <div>
		<input style="background-color: #CCC;" onClick="document.location='photos.jsp';" type="submit" value="Go back and try again">
		<input onClick="document.location.href='spreadsheet.jsp';" type="submit" value="Accept and move on">
  </div>

          
</div> <!-- container maincontent -->

<jsp:include page="../footer.jsp" flush="true"/>

