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
Shepherd myShepherd=new Shepherd(context);
String subdir = UploadServlet.getSubdirForUpload(myShepherd, request);
UploadServlet.setSubdirForUpload(subdir, request);

// this is being intentionlly set randomly ... but if you want to use it, override it in your live/deployed uploader.jsp to some string you can share
//   (this allows the session to be automatically consider non-bot, so the upload can happen)
String password = "fhqwhgads";

if (password.equals(request.getParameter("key"))) {
	System.out.println("uploader.jsp key/password successful");
        request.getSession().setAttribute("reCAPTCHA-passed", true);
}

if (!org.ecocean.servlet.ReCAPTCHA.sessionIsHuman(request)) {
	out.println("<h1 style=\"margin-top: 100px;\">no access</h1>");
	return;
}
%>

<style type="text/css">
	.hiddenFilename {
		display: none;
	}
</style>

<div id="hiddenFilename"></div>


<script>
function uploadFinished() {
	console.log("uploadFinished! Callback executing");
	document.getElementById('updone').innerHTML = '<i>upload finished, redirecting...</i>';
	var filename = document.getElementById('hiddenFilename').innerHTML;
	// forward user to the review page
	window.location.replace('standard-upload?filename='+filename+"&isUserUpload=true");
}
</script>
<body onLoad="uploaderInit(uploadFinished)">
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

<h1 class="import-header">Bulk Import: Spreadsheet Upload</h1>
<p class="import-explanation">Upload your Excel sheet (.xlsx) in Wildbook Standard Format.</p>

<div id="file-activity"></div>

<div id="updone"></div>

<div id="upcontrols" style="padding: 20px;">
	<input type="file" id="file-chooser" accept=".xlsx" onChange="return filesChangedSetFilename(this)" /> 
	<button id="upload-button">begin upload</button>
<% System.out.println("Done with import/spreadsheet.jsp. About to print footer."); %>
</div>
<jsp:include page="../footer.jsp" flush="true"/>
</body>
</html>



