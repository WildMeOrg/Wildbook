<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,org.ecocean.servlet.importer.*,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
org.ecocean.media.MediaAsset,org.ecocean.servlet.importer.ImportTask,
java.io.*,java.util.*, java.io.FileInputStream, 
java.text.SimpleDateFormat,
java.util.Date,org.ecocean.ia.*,org.json.JSONObject,
org.ecocean.identity.IBEISIA,org.ecocean.social.*,org.ecocean.ia.Task, org.json.JSONArray,
org.apache.poi.ss.usermodel.DateUtil,org.ecocean.identity.*,org.ecocean.queue.*,
java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, 
java.util.Iterator, java.lang.NumberFormatException"%>
<%@ page import="org.ecocean.shepherd.core.Shepherd" %>

<%!
public static boolean resumeIntakeAnnotations(Shepherd myShepherd, Task task) {

    if ((task.getObjectAnnotations() == null) || (task.getObjectAnnotations().size() < 1)) return false;

    String context = myShepherd.getContext();


    IAJsonProperties iaConfig = IAJsonProperties.iaConfig();


        List<JSONObject> opts = iaConfig.identOpts(myShepherd, task.getObjectAnnotations().get(0));
        System.out.println("identOpts: "+opts);
        if ((opts == null) || (opts.size() < 1)) return false;  // no ID for this iaClass.

        boolean fastlane = task.isFastlane(myShepherd);


        //these are re-used in every task
        JSONArray annArr = new JSONArray();
        for (Annotation ann : task.getObjectAnnotations()) {
            annArr.put(ann.getId());
        }
        JSONObject aj = new JSONObject();
        aj.put("annotationIds", annArr);
        String baseUrl = IA.getBaseURL(context);

        for (int i = 0 ; i < opts.size() ; i++) {
            JSONObject qjob = new JSONObject();
            qjob.put("identify", aj);
            qjob.put("taskId", task.getId());
            qjob.put("__context", context);
            qjob.put("__baseUrl", baseUrl);
            if (opts.get(i) != null) qjob.put("opt", opts.get(i));
            boolean sent = false;
            try {
              if(fastlane) {
                //if fastlane and a smaller, bespoke request, get this into the faster queue
                qjob.put("fastlane", fastlane);
                qjob.put("lane", "fast");
                //task.setQueueResumeMessage(qjob.toString());
                sent = org.ecocean.servlet.IAGateway.addToDetectionQueue(context, qjob.toString());
              }
              else {
            	//tasks.get(i).setQueueResumeMessage(qjob.toString());
                sent = org.ecocean.servlet.IAGateway.addToQueue(context, qjob.toString());
              }
            } catch (java.io.IOException iox) {
                System.out.println("ERROR[" + i + "]: IA.intakeAnnotations() addToQueue() threw " + iox.toString());
            }
//System.out.println("INFO: IA.intakeAnnotations() [opt " + i + "] accepted " + annsOneIAClass.size() + " annots; queued? = " + sent + "; " + tasks.get(i));
        }
   
//System.out.println("INFO: IA.intakeAnnotations() finished as " + topTask);
    return false;
}
%>


<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);



%>



<jsp:include page="../header.jsp" flush="true"/>
<div class="container maincontent">

<h1>Wildbook Machine Learning Queue Monitoring</h1>


<%

myShepherd.beginDBTransaction();



try{

	long TwoFourHours=1000*60*60*24*3;
  	String filter= "select from org.ecocean.ia.Task where created > "+(System.currentTimeMillis()-TwoFourHours);
	
	Query q=myShepherd.getPM().newQuery(filter);
	q.setOrdering("created desc");
	
    Collection c=(Collection) (q.execute());
   	ArrayList<Task> allTasks = new ArrayList<Task>(c);
   	System.out.println("Num tasks: "+c.size());
	q.closeAll();
	int count=0;
	for(Task task:allTasks){
		try{

		
			if(task.getParameters()!=null && (task.getChildren()==null || task.getChildren().size()==0) && task.getStatus(myShepherd).equals("waiting to queue") && task.getObjectAnnotations()!=null && task.getObjectAnnotations().size()>0){
			
				JSONObject aj = new JSONObject();
				JSONArray annArr = new JSONArray();
	        	for (Annotation ann : task.getObjectAnnotations()) {
	            	if(ann.getIAClass()!=null && ann.getMatchAgainst())annArr.put(ann.getId());
	        	}
	       		aj.put("annotationIds", annArr);
        		if(annArr.length()>0){
        			count++;
					//if(count==3||count == 4){
						
						System.out.println("Trying to resurrect task: "+task.getId());
						JSONObject jobj = task.getParameters();
			            jobj.put("__context", context);
			            jobj.put("__baseUrl", IA.getBaseURL(context));
			            jobj.put("__enqueuedByIAGateway", System.currentTimeMillis());
						jobj.put("identify", aj);
						jobj.put("taskId", task.getId());
						boolean worked=IAGateway.addToQueue(context, jobj.toString());
						//System.out.println("     requeue task: "+worked);
						
					//}
        		}
			}  //end if
		
		}
		catch(Exception er){er.printStackTrace();}
		
		
		
	} //end task for loop

System.out.println("Final fixme count was: "+count);
}
catch(Exception e){
	e.printStackTrace();
}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();

}


%>

</ul>
</div>
<jsp:include page="../footer.jsp" flush="true"/>
