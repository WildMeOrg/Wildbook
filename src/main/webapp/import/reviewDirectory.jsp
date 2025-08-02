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
<%@ page import="org.ecocean.shepherd.core.Shepherd" %>

<%
String context = ServletUtilities.getContext(request);
Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("import.jsp");
String langCode=ServletUtilities.getLanguageCode(request);
System.out.println("Starting import.jsp !");

String subdir = UploadServlet.getSubdirForUpload(myShepherd, request);
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

</style>


<div class="container maincontent">

  <h1 class="import-header">Bulk Import: Photo Review</h1>
  <p class="import-explanation">Please confirm that you have uploaded all the images in this import</p>

  <p class="info"><b><%=nImages %> images found:</b></p>

	<div id="filename-boxed-list">
    	<ol class="filelist">
  		<% 
  		for (File photo: imageFiles) {
                        String displayName = photo.getName();
                        String userFilename = (String)request.getSession().getAttribute("userFilename:" + displayName);
                        if (userFilename != null) {
                                // something is encoding the strings as ISO8859 so we have to do the conversion here
                                byte[] charset = userFilename.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
                                displayName = new String(charset, java.nio.charset.StandardCharsets.UTF_8);
                        }
  			%>
                            <li><%=displayName%></li>
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

