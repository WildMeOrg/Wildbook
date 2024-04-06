<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Map,
java.util.ArrayList,
java.util.Collections,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,
org.json.JSONArray,
org.json.JSONObject,
org.ecocean.identity.*,
org.ecocean.Project,
org.ecocean.media.*,
org.ecocean.ia.Task"
%>


<%


String id = request.getParameter("id");
String taskId = request.getParameter("taskId");
String projectId = request.getParameter("projectId");
boolean taskCompleted=false;
if ((id == null) && (taskId == null)) {
	out.println("{\"success\": false, \"error\": \"no object/task id passed\"}");
	return;
}

Shepherd myShepherd = new Shepherd("context0");
myShepherd.setAction("iaLogs.jsp");



myShepherd.beginDBTransaction();
long startTime=System.currentTimeMillis();

//if the Task is completed, we can skip some checks
if(request.getParameter("taskId")!=null){
	Task t=myShepherd.getTask(request.getParameter("taskID"));
	if(t!=null && t.getStatusNoWBIA()!=null && t.getStatusNoWBIA().equals("completed")){
		taskCompleted=true;
	}
}

try{
	
	ArrayList<IdentityServiceLog> logs = null;
	if (id != null) {
		logs = IdentityServiceLog.loadMostRecentByObjectID("IBEISIA", id, myShepherd);
	} else {
		
		logs = IdentityServiceLog.loadByTaskID(taskId, "IBEISIA", myShepherd);
		long queryTime=System.currentTimeMillis();
		Collections.reverse(logs);  //so it has newest first like mostRecent above
		long reverseTime=System.currentTimeMillis();
		System.out.println("Query time: "+(queryTime-startTime));
		System.out.println("Reverse time: "+(reverseTime-queryTime));
	}
	
	if (logs == null) {
		out.println("[]");
		myShepherd.rollbackDBTransaction();
		myShepherd.closeDBTransaction();
		return;
	}
	
	JSONArray all = new JSONArray();
	
	if (projectId!=null) {
		Project project = myShepherd.getProjectByUuid(projectId);
		if (project!=null) {
			JSONObject projectData = new JSONObject();
			projectData.put("projectData", project.asJSONObjectWithEncounterMetadata(myShepherd, request));
			//projectData.put("projectACMIds", project.getAllACMIdsJSON());
			projectData.put("projectACMIds", myShepherd.getAllProjectACMIdsJSON(project.getId()));
			//projectData.put("projectAnnotIds", project.getAllAnnotIdsJSON());
			all.put(projectData);
		}
	}
	System.out.println("projectTime: "+(System.currentTimeMillis()-startTime));
	
	for (IdentityServiceLog l : logs) {
		all.put(l.toJSONObject(taskCompleted));
	}
	
	System.out.println("islPutTime: "+(System.currentTimeMillis()-startTime));
	
	
	out.println(all.toString());

}
catch(Exception e){
	e.printStackTrace();
	out.println("[]");
}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
}

%>



