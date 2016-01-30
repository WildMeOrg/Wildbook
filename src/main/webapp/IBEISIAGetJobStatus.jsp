<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Map,
java.util.ArrayList,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,
org.json.JSONObject,
org.json.JSONArray,
java.net.URL,
org.ecocean.servlet.ServletUtilities,
org.ecocean.identity.IBEISIA,
org.ecocean.identity.IdentityServiceLog,
org.ecocean.Annotation,
org.ecocean.media.*
              "
%>




<%

Shepherd myShepherd=null;
myShepherd = new Shepherd("context0");

//String rootDir = getServletContext().getRealPath("/");
//String baseDir = ServletUtilities.dataDir("context0", rootDir);

response.setHeader("Content-type", "application/javascript");

String jobID = request.getParameter("jobid");
System.out.println("IBEIS-IA callback got jobid=" + jobID);
if ((jobID == null) || jobID.equals("")) {
	out.println("{\"success\": false, \"error\": \"invalid Job ID\"}");

} else {
	JSONObject statusResponse = new JSONObject();

	int tries = 10;
	boolean success = false;

while (!success && (tries > 0)) {
System.out.println("---<< jobID=" + jobID + ", tries=" + tries);

	try {
		statusResponse = IBEISIA.getJobStatus(jobID);
		success = true;
	} catch (Exception ex) {
System.out.println("except? " + ex.toString());
		statusResponse.put("_error", ex.toString());
		success = !(ex instanceof java.net.SocketTimeoutException);  //for now only re-try if we had a timeout; so may *fail* for other reasons
	}

System.out.println(statusResponse.toString());
System.out.println(">>>>---");
	JSONObject jlog = new JSONObject();
	jlog.put("jobID", jobID);

	//we have to find the taskID associated with this IBEIS-IA job
	String taskID = IBEISIA.findTaskIDFromJobID(jobID, myShepherd);
	if (taskID == null) {
		jlog.put("error", "could not determine task ID from job " + jobID);
	} else {
		jlog.put("taskID", taskID);
	}

	jlog.put("_action", "getJobStatus");
	jlog.put("_tries", tries);
	jlog.put("_response", statusResponse);


	IBEISIA.log(taskID, jobID, jlog, myShepherd);

	JSONObject all = new JSONObject();
	all.put("jobStatus", jlog);

	if ((statusResponse != null) && statusResponse.has("status") &&
	    statusResponse.getJSONObject("status").getBoolean("success") &&
	    statusResponse.has("response") && statusResponse.getJSONObject("response").has("status") &&
            "ok".equals(statusResponse.getJSONObject("response").getString("status")) &&
            "completed".equals(statusResponse.getJSONObject("response").getString("jobstatus")) &&
            "ok".equals(statusResponse.getJSONObject("response").getString("exec_status"))) {
		JSONObject resultResponse = IBEISIA.getJobResult(jobID);
		JSONObject rlog = new JSONObject();
		rlog.put("jobID", jobID);
		rlog.put("_action", "getJobResult");
		rlog.put("_response", resultResponse);
		IBEISIA.log(taskID, jobID, rlog, myShepherd);
		all.put("jobResult", rlog);
	}

	all.put("_timestamp", System.currentTimeMillis());
	out.println(all.toString());

	tries--;
	if (!success) {
System.out.println("resting and will try again...");
		Thread.sleep(2000);
	}

}  //end while (success...)


}



%>



