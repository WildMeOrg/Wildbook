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


String context = "context0";

final Shepherd myShepherd = new Shepherd(context);
System.out.println("I've made a 'final' Shepherd");
myShepherd.setAction("IBEISIADetectCallback.jsp");

String rootDir = getServletContext().getRealPath("/");
String baseDir = ServletUtilities.dataDir("context0", rootDir);

response.setHeader("Content-type", "application/javascript");

String jobID = request.getParameter("jobid");


System.out.println("==================================================== IBEIS-IA DETECT callback got jobid=" + jobID);
if ((jobID == null) || jobID.equals("")) {
//System.out.println("fake fail * * * * * * * * * *");
	out.println("{\"success\": false, \"error\": \"invalid Job ID\"}");

} else {

	runIt(jobID, context, request);
	out.println("{\"success\": true}");
System.out.println("((((all done with main thread))))");
}
myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();

%>

<%!

private void runIt(final String jobID, final String context, final HttpServletRequest request) {
System.out.println("---<< jobID=" + jobID + ", trying spawn . . . . . . . . .. . .................................");

	Runnable r = new Runnable() {
		public void run() {
			tryToGet(jobID, context, request);
//myShepherd.rollbackDBTransaction();
//myShepherd.closeDBTransaction();
		}
	};
	new Thread(r).start();
System.out.println("((( done runIt() )))");
	return;
}

private void tryToGet(String jobID, String context, HttpServletRequest request) {
  System.out.println("<<<<<<<<<< tryToGet(" + jobID + ")----");
	JSONObject statusResponse = new JSONObject();
  //if (jobID != null) return;

	try {
		statusResponse = IBEISIA.getJobStatus(jobID);
	} catch (Exception ex) {
    System.out.println("except? " + ex.toString());
		statusResponse.put("_error", ex.toString());
		//success = !(ex instanceof java.net.SocketTimeoutException);  //for now only re-try if we had a timeout; so may *fail* for other reasons
	}

  System.out.println(statusResponse.toString());
	JSONObject jlog = new JSONObject();
	jlog.put("jobID", jobID);

	Shepherd myShepherd=new Shepherd(context);
  myShepherd.setAction("IBEISIALynxCallback.jsp");
  //FeatureType.initAll(myShepherd);

	//we have to find the taskID associated with this IBEIS-IA job
	String taskID = IBEISIA.findTaskIDFromJobID(jobID, context);
	if (taskID == null) {
		jlog.put("error", "could not determine task ID from job " + jobID);
	} else {
		jlog.put("taskID", taskID);
	}

	jlog.put("_action", "detectCallback");
	jlog.put("_response", statusResponse);


	IBEISIA.log(taskID, jobID, jlog, context);

	JSONObject all = new JSONObject();
	all.put("jobStatus", jlog);
  System.out.println(">>>>------[ jobID = " + jobID + " -> taskID = " + taskID + " ]----------------------------------------------------");

  try {
    myShepherd.beginDBTransaction();
  	if (canGetJobResult(statusResponse)) {

      System.out.println("HEYYYYYYY i am trying to getJobResult(" + jobID + ")");
  		JSONObject resultResponse = IBEISIA.getJobResult(jobID);
  		JSONObject rlog = new JSONObject();
  		rlog.put("jobID", jobID);
  		rlog.put("_action", "getJobResult");
  		rlog.put("_response", resultResponse);
  		IBEISIA.log(taskID, jobID, rlog, context);
  		all.put("jobResult", rlog);
      JSONObject proc = IBEISIA.processCallback(taskID, rlog, context);

      JSONObject jobResultLog  = getJobResultLog(proc);
      JSONArray  jobResultList = getJobResultList(jobResultLog);
      int numPhotosThisJob = jobResultList.length();
      String[] maUUID = getUUIDs(jobResultLog);


      boolean isLynx = hasLynx(jobResultList);
      boolean[] isLynxArray = hasLynxArray(jobResultList);


      //System.out.println("processCallback returned --> " + proc);
      System.out.println("isLynx = "+isLynx);
      System.out.println("checking that this is executed...");
      ArrayList<Annotation> annotsToID = new ArrayList<Annotation>();


      for (int i=0; i<maUUID.length; i++) {
        if (!isLynxArray[i]) continue; // negative detection result -- array OOB except.
        JSONObject thisRes = jobResultList.getJSONArray(i).getJSONObject(0);

        //taskUUIDs.add(Util.generateUUID());
        annotsToID.add(lynxDetectCallback(thisRes, maUUID[i], myShepherd)); // return this to user so they can find flukebook results page
      }

      // return if annotsToID is empty

      String species = (annotsToID!=null && annotsToID.size()>0) ? annotsToID.get(0).getSpecies() : null;
      //ArrayList<Annotation> exemplars = Annotation.getExemplars(species, myShepherd);
      ArrayList<Annotation> exemplars = null;

      JSONObject queryConfigDict = null;
      JSONObject userConfidence = null;

      System.out.println("request isNull="+(request==null));
      //String baseUrl = CommonConfiguration.getServerURL(request, request.getContextPath());
      String baseUrl = "http://lynx.wildbook.org:80";






      System.out.println("about to call beginIdentifyAnnotations");

      for (Annotation ann: annotsToID) {
        ArrayList<Annotation> justThis = new ArrayList<Annotation>();
        justThis.add(ann);
        String thisTaskID = ann.getIdentificationStatus();
        IBEISIA.beginIdentifyAnnotations(justThis, exemplars, queryConfigDict, userConfidence, myShepherd, species, thisTaskID, baseUrl, context);
      }

      System.out.println("done with beginIdentifyAnnotations");
  	}
  } catch (Exception ex) {
  	System.out.println("whoops got exception: " + ex.toString());
  	ex.printStackTrace();
    myShepherd.rollbackDBTransaction();
  }
  finally{
  	myShepherd.commitDBTransaction();
  	myShepherd.closeDBTransaction();
  }

	all.put("_timestamp", System.currentTimeMillis());
  //System.out.println("-------- >>> " + all.toString() + "\n##################################################################");
	return;

}

private boolean canGetJobResult(JSONObject statusResponse) {
  return( statusResponse != null &&
          statusResponse.has("status") &&
          statusResponse.getJSONObject("status").getBoolean("success") &&
          statusResponse.has("response") &&
          statusResponse.getJSONObject("response").has("status") &&
          "ok".equals(statusResponse.getJSONObject("response").getString("status")) &&
          "completed".equals(statusResponse.getJSONObject("response").getString("jobstatus")) &&
          "ok".equals(statusResponse.getJSONObject("response").getString("exec_status"))
        );
}
private boolean isDetectedAsLynx(JSONObject detRes) {

  JSONArray  jobResultList = getJobResultListFromDetRes(detRes);
  return hasLynx(jobResultList);
}
private JSONArray getJobResultListFromDetRes(JSONObject detectResult) {
  JSONObject jobResultLog  = getJobResultLog(detectResult);
  System.out.println("Job result = "+jobResultLog);
  if (jobResultLog == null) return null;
  JSONArray  jobResultList = getJobResultList(jobResultLog);
  System.out.println("job result list = "+jobResultList);
  return jobResultList;
}

// helper funcs for isDetectedAsLynx
private JSONObject getJobResultLog(JSONObject detectResult) {
  System.out.println("beginning getJobResultLog. detectResult has logs = "+detectResult.has("_logs"));
  JSONArray logs = detectResult.getJSONArray("_logs");
  for (int i=0; i<logs.length(); i++) {
    JSONObject thisLog = logs.getJSONObject(i);
    if (
      thisLog.has("statusJson") &&
      thisLog.getJSONObject("statusJson").has("_action") &&
      "getJobResult".equals(thisLog.getJSONObject("statusJson").getString("_action"))
    ) {
      return thisLog;
    }
  }
  return null;
}
private JSONArray getJobResultList(JSONObject jobResultLog) {
  if (
    jobResultLog.has("statusJson") &&
    jobResultLog.getJSONObject("statusJson").has("_response") &&
    jobResultLog.getJSONObject("statusJson").getJSONObject("_response").has("response") &&
    jobResultLog.getJSONObject("statusJson").getJSONObject("_response").getJSONObject("response").has("json_result") &&
    jobResultLog.getJSONObject("statusJson").getJSONObject("_response").getJSONObject("response").getJSONObject("json_result").has("results_list")
  ) {
    return jobResultLog.getJSONObject("statusJson").getJSONObject("_response").getJSONObject("response").getJSONObject("json_result").getJSONArray("results_list");
  }
  return null;
}
private boolean hasLynx(JSONArray jobResultList) {
  if (jobResultList == null) return false;
  for (int i=0;i<jobResultList.length();i++) {
    JSONArray thisSubList = jobResultList.getJSONArray(i);
    for (int ii=0; ii<thisSubList.length(); ii++) {
      JSONObject thisResult = thisSubList.getJSONObject(ii);
      System.out.println("thisResult = "+thisResult);
      if (
        thisResult.has("class") &&
        "lynx".equals(thisResult.getString("class"))
      ) return true;
    }
  }
  return false;
}
private boolean[] hasLynxArray(JSONArray jobResultList) {
  System.out.println();
  System.out.println("hasLynxArray called on "+jobResultList);
  System.out.println();
  if (jobResultList == null) return new boolean[0];
  boolean[] isLynx = new boolean[jobResultList.length()];
  for (int i=0; i<isLynx.length ;i++) {
    JSONArray thisSubList = jobResultList.getJSONArray(i);
    for (int ii=0; ii<thisSubList.length(); ii++) {
      JSONObject thisResult = thisSubList.getJSONObject(ii);
      System.out.println("thisResult = "+thisResult);
      isLynx[i] = (
        thisResult.has("class") &&
        "lynx".equals(thisResult.getString("class"))
      );
    }
  }
  return isLynx;
}

private String[] getUUIDs(JSONObject jobResultLog) {
  if (
    jobResultLog.has("statusJson") &&
    jobResultLog.getJSONObject("statusJson").has("_response") &&
    jobResultLog.getJSONObject("statusJson").getJSONObject("_response").has("response") &&
    jobResultLog.getJSONObject("statusJson").getJSONObject("_response").getJSONObject("response").has("json_result") &&
    jobResultLog.getJSONObject("statusJson").getJSONObject("_response").getJSONObject("response").getJSONObject("json_result").has("image_uuid_list")
  ) {
    JSONArray image_uuid_list =  jobResultLog.getJSONObject("statusJson").getJSONObject("_response").getJSONObject("response").getJSONObject("json_result").getJSONArray("image_uuid_list");
    String[] uuids = new String[image_uuid_list.length()];
    for (int i=0; i<uuids.length; i++) {
      uuids[i] = IBEISIA.fromFancyUUID(image_uuid_list.getJSONObject(i));
    }
    return uuids;
  }
  return new String[0];
}

private Annotation lynxDetectCallback(JSONObject jobResItem, String assetUUID, Shepherd myShepherd) {
  System.out.println("lynxDetectCallback!");
  System.out.println("jobResItem = "+jobResItem);

  //myShepherd.beginDBTransaction();

  MediaAsset asset = MediaAssetFactory.loadByUuid(assetUUID, myShepherd);
  System.out.println("lynxDetectCallback has asset = "+asset+" and its clean status is "+asset.isCleanForIBEIS());

  Annotation newAnn = createAnnotationFromLynxIAResult(jobResItem, asset, myShepherd);
  //Annotation oldAnn = null; //TODO
  Annotation oldAnn = asset.getAnnotations().get(0);
  // might have weird behavior if there are multiple encounters
  Encounter oldEnc = oldAnn.findEncounter(myShepherd);


  // here we create what will be used as the IA ID task ID
  newAnn.setIdentificationStatus(Util.generateUUID());


  System.out.println("I have a new annotation!");
  System.out.println("newAnn = "+newAnn.toString());
  System.out.println("newAnn's ID status = "+newAnn.getIdentificationStatus());

  System.out.println("Attaching newAnn to oldEnc");
  oldEnc.addAnnotationReplacingUnityFeature(newAnn);
  asset.setDetectionStatus("lynx");

  System.out.println("I added it and it's time to persist");



  //

  // try {
  //   myShepherd.commitDBTransaction();
  // } catch (Exception e) {
  //   System.out.println("Exception on commiting Shepherd transaction!");
  //   e.printStackTrace();
  //   myShepherd.rollbackDBTransaction();
  // } finally {
  //   myShepherd.closeDBTransaction();
  // }

  return newAnn;

}
//{"xtl":910,"height":413,"theta":0,"width":444,"class":"giraffe_reticulated","confidence":0.2208,"ytl":182}

public static Encounter getOrigEncounter(MediaAsset asset, Shepherd myShepherd) {
  Annotation oldAnn = asset.getAnnotations().get(0);
  // might have weird behavior if there are multiple encounters
  return oldAnn.findEncounter(myShepherd);
  // media asset -> feature -> annotation -> encounter

}

public static Annotation createAnnotationFromLynxIAResult(JSONObject jann, MediaAsset asset, Shepherd myShepherd) {
    FeatureType.initAll(myShepherd);

    Annotation ann = IBEISIA.convertAnnotationWiggleBox(asset, jann);
    if (ann == null) return null;
    //Encounter enc = new Encounter(ann);
    String[] sp = IBEISIA.convertSpecies(ann.getSpecies());
    //if (sp.length > 0) enc.setGenus(sp[0]);
    //if (sp.length > 1) enc.setSpecificEpithet(sp[1]);
//TODO other fields on encounter!!  (esp. dates etc)
    //Occurrence occ = asset.getOccurrence();
    //if (occ != null) {
    //    enc.setOccurrenceID(occ.getOccurrenceID());
    //    occ.addEncounter(enc);
    //}
    // set exemplar = true by default
    ann.setIsExemplar(true);

    // wiggle the bounding box



    myShepherd.getPM().makePersistent(ann);
    System.out.println("Persisted annotation = "+ann.toString());
    //System.out.println("(x,y,w,h) = ("+ann.getX()+", "+ann.getY()+", "+ann.getWidth()+", "+ann.getHeight()+") and #features = "+ann.getFeatures().size());
    System.out.println("first feature = "+ann.getFeatures().get(0).toString());

    //myShepherd.getPM().makePersistent(enc);
    //if (occ != null) myShepherd.getPM().makePersistent(occ);
    return ann;
}




%>
