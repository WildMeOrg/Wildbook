<%@ page
		contentType="text/html; charset=utf-8"
		language="java"
     	import="org.ecocean.CommonConfiguration,
     	org.ecocean.ContextConfiguration,
     	org.ecocean.resumableupload.UploadServlet,
     	org.ecocean.servlet.ServletUtilities"
%>
<style>
	a.btn {
	  -webkit-appearance: button;
	  -moz-appearance: button;
	  appearance: button;
	}
</style>
<script>
function confirmCommit() {
	confirm("Start full import? This process may take a long time. Do not close this browser window if you continue.");
}
</script>
<%


String commitStr = request.getParameter("commit");
boolean committing = (commitStr!=null);

String filename = request.getParameter("filename");

// This file is for window-dressing at the bottom of the (java-servlet) uploader at WebImport.java
Boolean isUserUpload = false;
isUserUpload = Boolean.valueOf(request.getParameter("isUserUpload"));

String uploadAction = "StandardImport?filename="+filename+"&commit=true&isUserUpload="+isUserUpload;

if (!committing) {
%>
	<p>If you are adding many images and encounters (more than a couple hundred if each) this may take a while. You will be redirected when the process is finished.</p>
	<a href="<%=uploadAction%>"><button onclick="confirmCommit()">Commit these results.</button></a></p>
<%
}
%>

</div><!-- container maincontent -->
