<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
              org.ecocean.servlet.ServletUtilities,
              java.util.ArrayList,
              java.util.List,
              java.util.Map,
              java.util.Iterator,
              java.util.Properties,
              java.util.StringTokenizer
              "
%>



<jsp:include page="header.jsp" flush="true"/>

<%
/*
String context=ServletUtilities.getContext(request);
Shepherd myShepherd=null;
myShepherd=new Shepherd(context);
*/

// this is being intentionlly set randomly ... but if you want to use it, override it in your live/deployed uploader.jsp to some string you can share
//   (this allows the session to be automatically consider non-bot, so the upload can happen)
String password = Util.generateUUID();

if (password.equals(request.getParameter("key"))) {
	System.out.println("uploader.jsp key/password successful");
        request.getSession().setAttribute("reCAPTCHA-passed", true);
}

if (!org.ecocean.servlet.ReCAPTCHA.sessionIsHuman(request)) {
	out.println("<h1 style=\"margin-top: 100px;\">no access</h1>");
	return;
}
%>


<script src="https://sdk.amazonaws.com/js/aws-sdk-2.2.33.min.js"></script>
<script src="tools/flow.min.js"></script>
<script src="javascript/uploader.js"></script>

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

</style>

<script>
function uploadFinished() {
	document.getElementById('updone').innerHTML = '<i>upload finished</i>';
}
</script>

</head>
<body onLoad="uploaderInit(uploadFinished)">

<div style="margin-top: 100px; padding: 5px;" >upload method being used: <b><span id="uptype"></span></b></div>

<div id="file-activity"></div>

<div id="updone"></div>

<div id="upcontrols" style="padding: 20px;">
	<input type="file" id="file-chooser" webkitdirectory directory multiple accept="audio/*,video/*,image/*" onChange="return filesChanged(this)" /> 
	<button id="upload-button">begin upload</button>
</div>

</body>
</html>


