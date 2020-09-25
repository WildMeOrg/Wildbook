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
org.json.JSONException,
java.net.URL,
org.ecocean.servlet.ServletUtilities,
org.ecocean.identity.IBEISIA,
org.ecocean.identity.IdentityServiceLog,
org.ecocean.Annotation,
org.ecocean.media.*
              "
%>




<%

String context = "context0";


//String rootDir = getServletContext().getRealPath("/");
//String baseDir = ServletUtilities.dataDir("context0", rootDir);

response.setHeader("Content-type", "application/javascript");

String jobID = request.getParameter("jobid");


System.out.println("==================================================== IBEIS-IA callback got jobid=" + jobID);
if ((jobID == null) || jobID.equals("")) {
//System.out.println("fake fail * * * * * * * * * *");
	out.println("{\"success\": false, \"error\": \"invalid Job ID\"}");

} else {

	Shepherd myShepherd = new Shepherd(context);
	myShepherd.setAction("IBEISIAGetJobStatus.jsp");
	myShepherd.beginDBTransaction();
	//this checks if we *already* have process this job (manually?) to prevent duplication (minus race conditions, sigh)
	JSONObject rtn = checkJob(jobID, context, myShepherd);
	if (!rtn.optBoolean("continue", false)) {
		System.out.println("checkJob(" + jobID + ") claimed we should not continue; bailing");
		out.println(rtn.toString());
		myShepherd.rollbackAndClose();
		return;
	}

	runIt(jobID, context, request);
	out.println(rtn.toString());
	System.out.println("((((all done with main thread))))");
	myShepherd.rollbackAndClose();
}

%>

<%!

private JSONObject checkJob(String jobID, String context, Shepherd myShepherd) {
	JSONObject rtn = new JSONObject();
	String taskId = IBEISIA.findTaskIDFromJobID(jobID, context);
	if (taskId == null) {
		rtn.put("success", false);
		rtn.put("error", "unable to find taskId for jobId=" + jobID);
		return rtn;
	}
	rtn.put("taskId", taskId);

	ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(taskId, "IBEISIA", myShepherd);
	boolean cont = true;
	if (logs == null) {
		rtn.put("message", "logs = null");
	} else {
		JSONArray arr = new JSONArray();
		for (IdentityServiceLog l : logs) {
			rtn.put("logTimestamp", l.getTimestamp());
			JSONObject status = l.getStatusJson();
			if ((status != null) && (status.optString("_action") != null)) {
				if (status.getString("_action").equals("getJobStatus")) cont = false;
				arr.put(status.getString("_action"));
			}
		}
		rtn.put("actions", arr);
	}
	rtn.put("success", true);
	rtn.put("continue", cont);
	return rtn;
}

private void runIt(final String jobID, final String context, final HttpServletRequest request) {
System.out.println("---<< jobID=" + jobID + ", trying spawn . . . . . . . . .. . .................................");

	Runnable r = new Runnable() {
		public void run() {
			try {
				tryToGet(jobID, context, request);
			} catch (Exception ex) {
				System.out.println("tryToGet(" + jobID + ") got exception " + ex);
			}
	//myShepherd.rollbackDBTransaction();
	//myShepherd.closeDBTransaction();
		}
	};
	new Thread(r).start();
	System.out.println("((( done runIt() )))");
	return;
}
 
private void tryToGet(String jobID, String context, HttpServletRequest request) throws JSONException {
System.out.println("<<<<<<<<<< tryToGet(" + jobID + ")----");
	JSONObject statusResponse = new JSONObject();
//if (jobID != null) return;

	try {
		statusResponse = IBEISIA.getJobStatus(jobID, context);
	} catch (Exception ex) {
System.out.println("except? " + ex.toString());
		statusResponse.put("_error", ex.toString());
		//success = !(ex instanceof java.net.SocketTimeoutException);  //for now only re-try if we had a timeout; so may *fail* for other reasons
	}

System.out.println(statusResponse.toString());
	JSONObject jlog = new JSONObject();
	jlog.put("jobID", jobID);

	//Shepherd myShepherd=new Shepherd(context);
	//myShepherd.setAction("IBEISIAGetJobStatus.jsp");			
	//we have to find the taskID associated with this IBEIS-IA job
	String taskID = IBEISIA.findTaskIDFromJobID(jobID, context);
	if (taskID == null) {
		jlog.put("error", "could not determine task ID from job " + jobID);
	} else {
		jlog.put("taskID", taskID);
	}

	jlog.put("_action", "getJobStatus");
	jlog.put("_response", statusResponse);


	IBEISIA.log(taskID, jobID, jlog, context);

	JSONObject all = new JSONObject();
	all.put("jobStatus", jlog);
System.out.println(">>>>------[ jobID = " + jobID + " -> taskID = " + taskID + " ]----------------------------------------------------");

try {
	if ((statusResponse != null) && statusResponse.has("status") &&
	    statusResponse.getJSONObject("status").getBoolean("success") &&
	    statusResponse.has("response") && statusResponse.getJSONObject("response").has("status") &&
            "ok".equals(statusResponse.getJSONObject("response").getString("status")) &&
            "completed".equals(statusResponse.getJSONObject("response").getString("jobstatus")) &&
            "ok".equals(statusResponse.getJSONObject("response").getString("exec_status"))) {
System.out.println("HEYYYYYYY i am trying to getJobResult(" + jobID + ")");
		JSONObject resultResponse = IBEISIA.getJobResult(jobID, context);
		JSONObject rlog = new JSONObject();
		rlog.put("jobID", jobID);
		rlog.put("_action", "getJobResult");
		rlog.put("_response", resultResponse);
		IBEISIA.log(taskID, jobID, rlog, context);
		all.put("jobResult", rlog);

		JSONObject proc = IBEISIA.processCallback(taskID, rlog, request);
		System.out.println("processCallback returned --> " + proc);
	}
} catch (Exception ex) {
	System.out.println("whoops got exception: " + ex.toString());
	ex.printStackTrace();
}
finally{
	//myShepherd.rollbackDBTransaction();
	//myShepherd.closeDBTransaction();
}

	all.put("_timestamp", System.currentTimeMillis());
//System.out.println("-------- >>> " + all.toString() + "\n##################################################################");
	return;

}



%>



