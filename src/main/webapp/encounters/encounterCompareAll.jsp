<%@ page contentType="text/html; charset=iso-8859-1" language="java"
         import="org.ecocean.servlet.ServletUtilities,
org.ecocean.identity.IdentityServiceLog, org.ecocean.media.MediaAsset,
java.util.ArrayList,org.ecocean.Annotation, org.ecocean.Encounter,org.json.JSONArray, org.json.JSONObject,java.net.URL, org.ecocean.servlet.ServletUtilities, org.ecocean.identity.IBEISIA,
org.ecocean.identity.IdentityServiceLog, org.dom4j.Document, org.dom4j.Element,org.dom4j.io.SAXReader, org.ecocean.*, org.ecocean.grid.MatchComparator, org.ecocean.grid.MatchObject, java.io.File, java.util.Arrays, java.util.Iterator, java.util.List, java.util.Vector, java.nio.file.Files, java.nio.file.Paths, java.nio.file.Path" %>
<%

String context="context0";
context=ServletUtilities.getContext(request);

//handle some cache-related security
response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

//let's set up references to our file system components
String rootDir = getServletContext().getRealPath("/");
File webappsDir = new File(rootDir).getParentFile();
File dataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
File encountersDir=new File(dataDir.getAbsolutePath()+"/encounters");

Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("encounterCompareAll.jsp"); 

myShepherd.beginDBTransaction();
Vector allEncs = myShepherd.getAllEncountersNoFilterAsVector();


JSONArray arrayAll = new JSONArray();
for (Object obj : allEncs) {
	Encounter enc = (Encounter)obj;
	String id = Util.generateUUID();
	if ((enc.getAnnotations() == null)||(enc.getAnnotations().size()<1)) continue;
	
	//This is just grabbing one image for each encounter to feed to IA. 
	MediaAsset ma = enc.getAnnotations().get(0).getMediaAsset();
	if (ma == null) continue;
	
	JSONObject jObject = new JSONObject();
	
	
	
	if (enc.getIndividualID() != null) {
		jObject.put("individualId", enc.getIndividualID());		
	}
	//Here's what I *think* IA wants as a job ID for each annotation. 
	jObject.put("jobId", id);
	jObject.put("encId", enc.getCatalogNumber());
	jObject.put("asset",Util.toggleJSONObject(ma.sanitizeJson(request, new org.datanucleus.api.rest.orgjson.JSONObject())));
	
	arrayAll.put(jObject);

}
myShepherd.commitDBTransaction();




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
	
	try {
		statusResponse = IBEISIA.getJobStatus(jobID);
		System.out.println("\nStatus Response from IBEISIA : ");
	} catch (Exception e) {
		e.printStackTrace();
		System.out.println("Thrown exception while grabbing job status from IBEISIA!");
	}
	JSONObject jlog = new JSONObject();
	jlog.put("jobID", jobID);
	
	Shepherd myShepherd = new Shepherd(context);
	
	String taskID = IBEISIA.findTaskIDFromJobID(jobID, context);
	
	if (taskID == null) {
		jlog.put("error", "!!! Could not get a Task ID "+jobID+" from IBEISIA !!!" );
	} else {
		jlog.put("taskID", taskID);
	}
	
	jlog.put("_action", "sendIdentify");
	jlog.put("_response", statusResponse);
	
	IBEISIA.log(taskID, jobID, jlog, context);
	JSONObject all = new JSONObject();
	all.put("jobStatus", jlog);
	System.out.println(">>>>------[ jobID = " + jobID + " -> taskID = " + taskID + " ]----------------------------------------------------");
	
	try {
		myShepherd.beginDBTransaction();
		
	} catch (Exception e) {
		
	}
	
	
	
} 
%>






