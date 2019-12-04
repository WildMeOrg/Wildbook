<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
              org.ecocean.servlet.ServletUtilities,
			  org.ecocean.resumableupload.UploadServlet,
              java.util.ArrayList,
              java.util.List,
              java.util.Map,
              java.util.Iterator,
              java.util.Properties,
              java.util.StringTokenizer
              "
%>

<jsp:include page="../header.jsp" flush="true"/>

<%

String context=ServletUtilities.getContext(request);
//Shepherd myShepherd=new Shepherd(context);


// this is being intentionlly set randomly ... but if you want to use it, override it in your live/deployed uploader.jsp to some string you can share
//   (this allows the session to be automatically consider non-bot, so the upload can happen)
String password = "fhqwhgads";
String subdir = UploadServlet.getSubdirForUpload(request);
// stores subdir on session so it can go to the other import servlets
UploadServlet.setSubdirForUpload(subdir, request);

String checkSubdir = ServletUtilities.getSessionAttribute("subdir", request);
System.out.println("I'm double checking the 'subdir' attribute: "+checkSubdir);

if (password.equals(request.getParameter("key"))) {
	System.out.println("uploader.jsp key/password successful");
    request.getSession().setAttribute("reCAPTCHA-passed", true);
}

if (!org.ecocean.servlet.ReCAPTCHA.sessionIsHuman(request)) {
	out.println("<h1 style=\"margin-top: 100px;\">no access</h1>");
	return;
}
%>

<script>
/*
    note: make sure you have nginx (etc) set to allow big enough upload
        e.g.  https://www.cyberciti.biz/faq/linux-unix-bsd-nginx-413-request-entity-too-large/

        client_max_body_size 100M;
*/
function uploadFinished() {
	console.log("uploadFinished! Callback executing");
	document.getElementById('updone').innerHTML = '<i>upload finished, redirecting...</i>';
	// forward user to the review page
	window.location.replace('reviewDirectory.jsp');
}
</script>
<body onLoad="uploaderInit(uploadFinished,'<%=subdir%>')">
<div class="container maincontent">
<script src="https://sdk.amazonaws.com/js/aws-sdk-2.2.33.min.js"></script>
<script src="../tools/flow.min.js"></script>
<script src="../javascript/localUploader.js"></script>

<style>
div#file-activity {
	font-family: sans;
	border: solid 2px black;
	padding: 8px;
	margin: 20px;
	height: 250px;
	overflow-y: scroll;
}
div.file-item {
	position: relative;
	background-color: #DDD;
	border-radius: 3px;
	margin: 2px;
}

div.file-item div {
	display: inline-block;
	padding: 3px 7px;
}
.file-name {
	width: 30%;
}
.file-size {
	width: 8%;
}

.file-bar {
	position: absolute;
	width: 0;
	height: 100%;
	padding: 0 !important;
	left: 0;
	border-radius: 3px;
	background-color: rgba(100,100,100,0.3);
}
.import-explanation {
}
.import-header {
	margin-top: 0px;
}
</style>


</head>


<h1 class="import-header">Bulk Import: Photo Upload</h1>
<p class="import-explanation">On your computer, organize the photos you'd like to upload into a single folder. Remember, the image names must correspond exactly to the "Encounter.MediaAsset" entries in your Wildbook Standard Format spreadsheet.</p>

<p class="import-explanation">Using the tool below, upload your image folder to our server. Your images will be stored in your private directory until the import is complete.</p>

<p class="import-explanation"> Your photos are saved in a private directory on our server. You can upload photos in multiple sessions, and your excel file has access to any of the photos you have uploaded in the past (visible on the <a href="reviewDirectory.jsp">photo review page</a>).
</p>

<div id="file-activity"></div>

<div id="updone"></div>

<div id="upcontrols" style="padding: 20px;">
	<input type="file" id="file-chooser" webkitdirectory directory multiple accept="audio/*,video/*,image/*" onChange="return filesChanged(this)" /> 
	<button id="upload-button">begin upload</button>
	<br>
	<p><b>If you have selected a large number of photos they may take a while to load in the interface.<b> Once you have clicked the 'Begin Upload' button the images will be sent, and you will automatically
	be taken to the next page when they are finished.</p>
<% System.out.println("Done with photos.jsp. About to print footer."); %>
</div>

</div> <!-- container maincontent -->

<jsp:include page="../footer.jsp" flush="true"/>




