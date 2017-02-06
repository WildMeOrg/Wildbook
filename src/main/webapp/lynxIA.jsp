
<%@
page contentType="text/html; charset=utf-8"
language="java"
import="org.ecocean.servlet.ServletUtilities,
        org.ecocean.*,
        org.ecocean.media.*,
        java.util.Properties,
        java.util.ArrayList,
        java.io.IOException,
        org.json.JSONObject,
        org.json.JSONArray,
        org.ecocean.servlet.ServletUtilities,
        org.ecocean.identity.IBEISIA,
        org.ecocean.identity.IdentityServiceLog,
        org.ecocean.media.*
"
%>


<%!

  private boolean sendDetectResHasJobID(JSONObject res) {
    return (
      res != null &&
      res.has("sendDetect") &&
      res.getJSONObject("sendDetect").has("response")
    );
  }

  private String getJobIDFromSendDetectRes(JSONObject res) {
    return res.getJSONObject("sendDetect").getString("response");
  }

  private boolean canGetJobResult(JSONObject jobStatusResponse) {
    return (
      jobStatusResponse != null && jobStatusResponse.has("status") &&
  	  jobStatusResponse.getJSONObject("status").getBoolean("success") &&
  	  jobStatusResponse.has("response") && jobStatusResponse.getJSONObject("response").has("status") &&
      "ok".equals(jobStatusResponse.getJSONObject("response").getString("status")) &&
      "completed".equals(jobStatusResponse.getJSONObject("response").getString("jobstatus")) &&
      "ok".equals(jobStatusResponse.getJSONObject("response").getString("exec_status"))
    );
  }


  private void runIt(final String jobID, final String context) {
    System.out.println("::runIt called on jobID = " + jobID);

  	Runnable r = new Runnable() {
  		public void run() {
  			tryToGet(jobID, context);
  		}
  	};
  	new Thread(r).start();
    System.out.println("::runIt completed");
  	return;
  }

  private void tryToGet(String jobID, String context) {
    System.out.println("::::tryToGet called on jobID = " + jobID);

  	JSONObject statusResponse = new JSONObject();
  	try {
  		statusResponse = IBEISIA.getJobStatus(jobID);
  	} catch (Exception ex) {
      System.out.println("except? " + ex.toString());
  		statusResponse.put("_error", ex.toString());
  	}
    System.out.println("::::tryToGet statusResponse = "+statusResponse.toString());

  	JSONObject jlog = new JSONObject();
  	jlog.put("jobID", jobID);

  	String taskID = IBEISIA.findTaskIDFromJobID(jobID, context);
    System.out.println("::::tryToGet jobID = "+jobID+" -> taskID = " + taskID);
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

  try {
  	if (canGetJobResult(statusResponse)) {
      System.out.println("::::tryToGet trying to getJobResult(" + jobID + ")");
  		JSONObject resultResponse = IBEISIA.getJobResult(jobID);
  		JSONObject rlog = new JSONObject();
  		rlog.put("jobID", jobID);
  		rlog.put("_action", "getJobResult");
  		rlog.put("_response", resultResponse);
  		IBEISIA.log(taskID, jobID, rlog, context);
  		all.put("jobResult", rlog);

      System.out.println("::::tryToGet about to call process callback!");
  		JSONObject proc = IBEISIA.processCallback(taskID, rlog, context);
      System.out.println("::::tryToGet processCallback returned --> " + proc);
  	}
  } catch (Exception ex) {
  	System.out.println("whoops got exception: " + ex.toString());
  	ex.printStackTrace();
  }

  	all.put("_timestamp", System.currentTimeMillis());
    System.out.println("::::tryToGet final JSON = " + all.toString() + "\n##################################################################");
  	return;

  }

%>


<%

String context="context0";
context=ServletUtilities.getContext(request);
String langCode=ServletUtilities.getLanguageCode(request);

Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("lynxIA.jsp");




//set up the file input stream
  Properties props = ShepherdProperties.getProperties("login.properties", langCode,context);
  String encID = request.getParameter("encid");
  Encounter enc = (encID != null)? myShepherd.getEncounter(encID) : null;

  String baseUrl = CommonConfiguration.getServerURL(request, request.getContextPath());
  System.out.println();
  System.out.println();
  System.out.println("baseUrl="+baseUrl);
  System.out.println();
  System.out.println();


%>

<jsp:include page="header.jsp" flush="true"/>

<div class="container maincontent">

  <h1 class="intro">Image Analysis</h1>
  <h2>Encounter <em><%=encID%></em></h2>



  <p align="left">

    <div style="padding: 10px;" class="error">
      <%
      if (session.getAttribute("error") != null) {
      	out.println(session.getAttribute("error"));
      	session.removeAttribute("error");
      }
      %>
    </div>
    <%
    if (enc!=null) {
      %><p>Encounter not null!</p><%
      ArrayList<MediaAsset> assets = enc.getMedia();
      JSONObject iaResult = new JSONObject();
      if (assets.size()>0) {
        %><p>And we have assets! Sending to detection.</p><%
        try {
          iaResult = IBEISIA.beginDetect(assets, baseUrl, context);


        } catch (Exception e) {
          System.out.println("DETECTION ERROR!");
          e.printStackTrace();
          iaResult.put("caughtError","true");
        }
        %><p>iaResult = <%=iaResult%></p><%
        %><p>iaResult has jobID = <%=sendDetectResHasJobID(iaResult)%></p>
        <% String jobID = getJobIDFromSendDetectRes(iaResult);
        %><p>iaResult jobID = <%=jobID%></p><%

        if (jobID!=null && !(jobID.equals(""))) {

          %><p>about to call runIt!<p><%
          runIt(jobID, context);



        }



      }

    }
    %>
  </p>
</div>

<jsp:include page="footer.jsp" flush="true"/>
