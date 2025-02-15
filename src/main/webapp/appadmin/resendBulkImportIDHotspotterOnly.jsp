
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
org.ecocean.cache.*,org.ecocean.ia.*,
org.json.*,org.ecocean.servlet.importer.ImportTask,org.ecocean.identity.IBEISIA,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%!

public static Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns,
        final Task parentTask, boolean fastlane) {
        // List<List<Annotation>> annses = binAnnotsByIaClass(anns);
        //// slightly complicated bc we need to create child tasks only if there are multiple iaClasses
        // if (annses.size() == 1) return intakeAnnotationsOneIAClass(myShepherd, annses.get(0), parentTask);
        //// here we make child tasks
        // Task topTask = (parentTask==null) ? new Task() : parentTask;
        // for (List<Annotation> annsOneIaClass: annses) {
        // topTask.addChild(intakeAnnotationsOneIAClass(myShepherd, anns, parentTask));
        // }
        // return topTask;
        // }
        // public static Task intakeAnnotationsOneIAClass(Shepherd myShepherd, List<Annotation> anns, final Task parentTask) {
        // System.out.println("Starting intakeAnnotations");
        if ((anns == null) || (anns.size() < 1)) return null;
        Task topTask = new Task();
        if (parentTask != null) topTask.setParameters(parentTask.getParameters());
        topTask.setObjectAnnotations(anns);
        String context = myShepherd.getContext();

        /*
            what we do *for now* is punt to "legacy" IBEISIA queue stuff... but obviously this should be expanded as needed for this we use
               IBEISIA.identOpts to decide how many flavors of identification we need to do!   if have more than one we need to make a set of subtasks
         */

/*
        String iaClass = anns.get(0).getIAClass(); //IAClass is a standard with image analysis that identifies the featuretype used for identification
           List<JSONObject> opts = null;
        // below gets it working for dolphins but can be generalized easily from IA.properties String inferredIaClass =
           IBEISIA.inferIaClass(anns.get(0), myShepherd);
        String bottlenose = "dolphin_bottlenose_fin";
        if (bottlenose.equals(iaClass) || bottlenose.equals(inferredIaClass)) {
            System.out.println("IA.java is sending a Tursiops truncatus job");
            opts = IBEISIA.identOpts(context, bottlenose);
        } else { // defaults to the default ia.properties IBEISIdentOpt, in our case humpback flukes opts = IBEISIA.identOpts(context);
        }
 */
        // List<JSONObject> opts = IBEISIA.identOpts(myShepherd, anns.get(0));
        IAJsonProperties iaConfig = IAJsonProperties.iaConfig();
        List<List<Annotation> > annotsByIaClass = IA.binAnnotsByIaClass(anns);
        for (List<Annotation> annsOneIAClass : annotsByIaClass) {
            List<JSONObject> opts = iaConfig.identOpts(myShepherd, annsOneIAClass.get(0));
            if (opts != null) {
                Iterator<JSONObject> itr = opts.iterator();
                while (itr.hasNext()) {
                	JSONObject nextOne = itr.next();
                    //if (!itr.next().optBoolean("default", true)) itr.remove();
                    if(nextOne.optJSONObject("query_config_dict")==null||!nextOne.optJSONObject("query_config_dict").optBoolean("sv_on"))itr.remove();
                }
            }
            System.out.println("identOpts: " + opts);
            List<Task> tasks = new ArrayList<Task>();
            JSONObject newTaskParams = new JSONObject(); // we merge parentTask.parameters in with opts from above
            if (parentTask != null && parentTask.getParameters() != null) {
                newTaskParams = parentTask.getParameters();
                System.out.println("newTaskParams: " + newTaskParams.toString());
                if (newTaskParams.optJSONArray("matchingAlgorithms") != null) {
                    JSONArray matchingAlgorithms = newTaskParams.optJSONArray("matchingAlgorithms");
                    System.out.println("matchingAlgorithms1: " + matchingAlgorithms.toString());
                    ArrayList<JSONObject> newOpts = new ArrayList<JSONObject>();
                    int maLength = matchingAlgorithms.length();
                    for (int y = 0; y < maLength; y++) {
                        newOpts.add(matchingAlgorithms.getJSONObject(y));
                    }
                    System.out.println("matchingAlgorithms2: " + newOpts.toString());
                    if (newOpts.size() > 0) {
                        opts = newOpts;
                        System.out.println("Swapping opts for newOpts!!");
                    }
                }
            }
            if ((opts == null) || (opts.size() < 1)) continue; // no ID for this iaClass.
            // just one IA class, one algorithm case
            if (opts.size() == 1 && annotsByIaClass.size() == 1) {
                newTaskParams.put("ibeis.identification",
                    ((opts.get(0) == null) ? "DEFAULT" : opts.get(0)));
                topTask.setParameters(newTaskParams);
                tasks.add(topTask); // topTask will be used as *the*(only) task -- no children
            } else {
                for (int i = 0; i < opts.size(); i++) {
                    Task t = new Task();
                    t.setObjectAnnotations(annsOneIAClass);
                    newTaskParams.put("ibeis.identification",
                        ((opts.get(i) == null) ? "DEFAULT" : opts.get(i)));                                        // overwrites each time
                    t.setParameters(newTaskParams);
                    topTask.addChild(t);
                    tasks.add(t);
                }
            }
            newTaskParams.put("fastlane", fastlane);
            if (fastlane) newTaskParams.put("lane", "fast");
            myShepherd.storeNewTask(topTask);

            // these are re-used in every task
            JSONArray annArr = new JSONArray();
            for (Annotation ann : annsOneIAClass) {
                annArr.put(ann.getId());
            }
            JSONObject aj = new JSONObject();
            aj.put("annotationIds", annArr);
            String baseUrl = IA.getBaseURL(context);
            for (int i = 0; i < opts.size(); i++) {
                JSONObject qjob = new JSONObject();
                qjob.put("identify", aj);
                qjob.put("taskId", tasks.get(i).getId());
                qjob.put("__context", context);
                qjob.put("__baseUrl", baseUrl);
                if (opts.get(i) != null) qjob.put("opt", opts.get(i));
                boolean sent = false;
                try {
                    if (fastlane) {
                        // if fastlane and a smaller, bespoke request, get this into the faster queue
                        qjob.put("fastlane", fastlane);
                        qjob.put("lane", "fast");
                        tasks.get(i).setQueueResumeMessage(qjob.toString());
                        sent = org.ecocean.servlet.IAGateway.addToDetectionQueue(context,
                            qjob.toString());
                    } else {
                        tasks.get(i).setQueueResumeMessage(qjob.toString());
                        sent = org.ecocean.servlet.IAGateway.addToQueue(context, qjob.toString());
                    }
                } catch (java.io.IOException iox) {
                    System.out.println("ERROR[" + i +
                        "]: IA.intakeAnnotations() addToQueue() threw " + iox.toString());
                }
                System.out.println("INFO: IA.intakeAnnotations() [opt " + i + "] accepted " +
                    annsOneIAClass.size() + " annots; queued? = " + sent + "; " + tasks.get(i));
            }
        } // end for each iaClass
        System.out.println("INFO: IA.intakeAnnotations() finished as " + topTask);
        return topTask;
    }

%>


<%
response.setContentType("application/json");
response.setCharacterEncoding("UTF-8");
response.setHeader("Access-Control-Allow-Origin", "*");


System.out.println("==> In ImporIA Servlet ");

String context= ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("resendBulkImportID.jsp");
myShepherd.beginDBTransaction();

JSONObject res = new JSONObject();
//JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);

//String queryEncounterId = null;
String importIdTask = request.getParameter("importIdTask");
List<String> locationIDs = new ArrayList<String>(); 
if(request.getParameterValues("locationID")!=null) {
  String[] vals=request.getParameterValues("locationID");
  locationIDs = Arrays.asList(vals);
}

try {
    res.put("success","false");

    //projectIdPrefix = j.optString("projectIdPrefix", null);
    //queryEncounterId = j.optString("queryEncounterId", null);

    if (Util.stringExists(importIdTask)) {

        //Project project = myShepherd.getProjectByProjectIdPrefix(projectIdPrefix);
        ImportTask itask = myShepherd.getImportTask(importIdTask);
        //Encounter queryEnc = myShepherd.getEncounter(queryEncounterId);
        if (itask!=null) {
        	
       		//JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);
      	  	JSONObject j = new JSONObject();
       		JSONObject taskParameters = j.optJSONObject("taskParameters");
       		if (taskParameters == null) taskParameters = new JSONObject(); 
      	  	taskParameters.optString("importTaskId", itask.getId());
            
            JSONObject tp = new JSONObject();
            JSONObject mf = new JSONObject();
            //matchingSetFilter = { locationIds: locationIds }
            if(locationIDs!=null && locationIDs.size()>0)mf.put("locationIds",locationIDs);
            //mf.put("projectId", project.getId());
            taskParameters.put("matchingSetFilter", mf);

            

      	  	Task parentTask = new Task();  // root task to hold all others, to connect to ImportTask
            parentTask.setParameters(taskParameters);
            myShepherd.storeNewTask(parentTask);
            myShepherd.updateDBTransaction();
            itask.setIATask(parentTask);
            myShepherd.updateDBTransaction();

            List<Encounter> targetEncs = itask.getEncounters();
            //List<Annotation> targetAnns = new ArrayList<>();
            JSONArray initiatedJobs = new JSONArray();
            for(Encounter queryEnc:targetEncs) {
            	
            	if(queryEnc.getIndividual()==null){
            	
	            	Task subParentTask = new Task();  
	                
	                subParentTask.setParameters(taskParameters);
	                myShepherd.storeNewTask(subParentTask);
	                myShepherd.updateDBTransaction();
	            
	              List<Annotation> matchMeAnns = new ArrayList<Annotation>();
	              for (Annotation queryAnn : queryEnc.getAnnotations()) {
	                if (IBEISIA.validForIdentification(queryAnn)) {
	                  matchMeAnns.add(queryAnn);
	                }
	              }
	              
	              System.out.println("BulkImport:"+importIdTask+" sending "+matchMeAnns.size()+" annots for Encounter "+queryEnc.getCatalogNumber());
	                  
	
	              if(matchMeAnns.size()>0){
	            	  
	
		
		              Task childTask = intakeAnnotations(myShepherd, matchMeAnns, subParentTask,false);
		              
		              myShepherd.storeNewTask(childTask);
		              myShepherd.updateDBTransaction();
		              subParentTask.addChild(childTask);
		              myShepherd.updateDBTransaction();
		              JSONObject jobJSON = new JSONObject();
		              jobJSON.put("topTaskId", parentTask.getId());
		              jobJSON.put("childTaskId", childTask.getId());
		              //jobJSON.put("queryAnnId", queryAnn.getId());
		              initiatedJobs.put(jobJSON);
		              myShepherd.updateDBTransaction();
	              }
              
            	}

            }
            res.put("success","true");
            res.put("initiatedJobs", initiatedJobs);
            response.setStatus(HttpServletResponse.SC_OK);
            //JSONObject rtnIA = IBEISIA.sendIdentify(qanns, tanns, queryConfigDict, userConfidence, baseUrl, context);
        
        
        
        }
    }

    out.println(res);
    out.close();

} catch (NullPointerException npe) {
    npe.printStackTrace();
    //addErrorMessage(res, "NullPointerException npe");
    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
} catch (JSONException je) {
    je.printStackTrace();
    //addErrorMessage(res, "JSONException je");
  response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
} catch (Exception e) {
    e.printStackTrace();
    //addErrorMessage(res, "Exception e");
    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
} finally {
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
    out.println(res);
}
%>
