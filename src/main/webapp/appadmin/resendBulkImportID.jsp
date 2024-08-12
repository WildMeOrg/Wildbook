
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
org.ecocean.cache.*,org.ecocean.ia.*,
org.json.*,org.ecocean.servlet.importer.ImportTask,org.ecocean.identity.IBEISIA,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>
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
String ownerFilter = "";  
if(request.getParameterValues("locationID")!=null) {
  String[] vals=request.getParameterValues("locationID");
  locationIDs = Arrays.asList(vals);
}

if(request.getParameter("owner")!=null) {
    ownerFilter=request.getParameter("owner");
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

            if (!ownerFilter.isEmpty()) {
                mf.put("owner", ownerFilter);
            }


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
            	  

	
	              Task childTask = IA.intakeAnnotations(myShepherd, matchMeAnns, subParentTask,false);
	              
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
