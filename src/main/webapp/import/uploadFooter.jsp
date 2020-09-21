<%@ page
		contentType="text/html; charset=utf-8"
		language="java"
     	import="org.ecocean.CommonConfiguration,
     	org.ecocean.ContextConfiguration,
     	org.ecocean.resumableupload.UploadServlet,
     	org.ecocean.servlet.ServletUtilities,
     	org.ecocean.Util"
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

async function sendAndRedirect(link, uuid){
	$("#commitButton").prop("disabled", true);
    $.ajax({
        url: link,
        success: function(data) {
            cosole.log("finished task");
        }
    });
    $("body").css("cursor", "progress");
    await new Promise(r => setTimeout(r, 3000));
    $("body").css("cursor", "default");
    window.location.href = "../imports.jsp?taskId="+uuid;
	
}


</script>
<%


String commitStr = request.getParameter("commit");
boolean committing = (commitStr!=null);

String filename = request.getParameter("filename");
String uuid=Util.generateUUID();
String uploadAction = "standard-upload?filename="+filename+"&commit=true&isUserUpload=true&taskID="+uuid;

// This file is for window-dressing at the bottom of the (java-servlet) uploader at WebImport.java


if (!committing) {
%>
	<p>If you are adding many images and encounters (more than a couple hundred if each) this may take a while. You will be redirected to a status page as the process completes.</p>
	<p><a onclick="sendAndRedirect('<%=uploadAction %>','<%=uuid %>')"><button id="commitButton" onclick="confirmCommit()">Commit these results.</button></a></p>
<%
}
%>

</div></div><!-- container maincontent -->