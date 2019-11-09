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
<%

String filename = request.getParameter("filename");

// This file is for window-dressing at the bottom of the (java-servlet) uploader at WebImport.java

String uploadAction = "standard-upload?filename="+filename+"&commit=true&isUserUpload=true";
%>
<a href="<%=uploadAction%>"><button>Commit these results.</button></a></p>


</div><!-- container maincontent -->