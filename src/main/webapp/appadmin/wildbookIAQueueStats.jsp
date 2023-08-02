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
org.ecocean.identity.IBEISIA,org.ecocean.social.*,org.ecocean.ia.Task,
org.apache.poi.ss.usermodel.DateUtil,
java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, 
java.util.Iterator, java.lang.NumberFormatException"%>



<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);



%>

<html>
<head>
<title>Fix Standard Children</title>

</head>


<body>


<ul>
<%

myShepherd.beginDBTransaction();



try{

	long TwoFourHours=1000*60*60*24;
  	String filter= "select from org.ecocean.ia.Task where created > "+(System.currentTimeMillis()-TwoFourHours);
	
	Query q=myShepherd.getPM().newQuery(filter);
	q.setOrdering("created desc");
	
    Collection c=(Collection) (q.execute());
   	ArrayList<Task> allTasks = new ArrayList<Task>(c);
	q.closeAll();
	int count=0;
	
	int numParents=0;
	int numChildTasks=0;
	int hasUsername=0;
	int numDetectionTasks=0;
	int numIDTasks=0;
	
	HashMap<String, Integer> userDistribution = new HashMap<String,Integer>();
	HashMap<String, Integer> algorithms = new HashMap<String,Integer>();
	HashMap<String, Integer> species = new HashMap<String,Integer>();
	HashMap<String, Integer> bulkImports = new HashMap<String,Integer>();
	
	
	%>
	<li>Num tasks last 24 hours: <%=allTasks.size() %></li>
	<%
	
	for(Task task:allTasks){
		count++;
		JSONObject params=task.getParameters();
		if(task.hasChildren()){numParents++;}
		else{

			if(!params.optString("ibeis.detection").equals("")){
				numDetectionTasks++;
			}
			else if(params.optJSONObject("ibeis.identification")!=null){
				numIDTasks++;
				String algo = params.optJSONObject("ibeis.identification").optJSONObject("query_config_dict").optString("pipeline_root");
				if(algo!=null && !algo.equals("")){
					if(!algorithms.containsKey(algo)){algorithms.put(algo, new Integer(1));}
					else{
						int distribCount=algorithms.get(algo).intValue();
						distribCount++;
						algorithms.put(algo, distribCount);
					}
				}
			}
			
			numChildTasks++;
			
			List<Annotation> annots=task.getObjectAnnotations();
			if(annots!=null && annots.size()>0){
				Annotation annot=annots.get(0);
				Encounter enc=annot.findEncounter(myShepherd);
				if(enc.getImportTask(myShepherd)!=null){
					ImportTask tasky=enc.getImportTask(myShepherd);
					if(!bulkImports.containsKey(tasky.getId())){bulkImports.put(tasky.getId(), new Integer(1));}
					else{
						int distribCount=bulkImports.get(tasky.getId()).intValue();
						distribCount++;
						bulkImports.put(tasky.getId(), distribCount);
					}
					
				}
				Taxonomy taxy=enc.getTaxonomy(myShepherd);
				if(taxy!=null){
					if(!species.containsKey(taxy.getScientificName())){species.put(taxy.getScientificName(), new Integer(1));}
					else{
						int distribCount=species.get(taxy.getScientificName()).intValue();
						distribCount++;
						species.put(taxy.getScientificName(), distribCount);
					}
				}
				if(enc!=null && enc.getSubmitterID()!=null){
					
					hasUsername++;
					String username = enc.getSubmitterID();
					if(!userDistribution.containsKey(username)){userDistribution.put(username, new Integer(1));}
					else{
						int distribCount=userDistribution.get(username).intValue();
						distribCount++;
						userDistribution.put(username, distribCount);
					}
					
				}
			}


		}
		
	}
	%>
	<li>Num parent tasks: <%=numParents %></li>
	<li>Num child tasks: <%=numChildTasks %>
		<ul>
			<li>Num detection tasks: <%=numDetectionTasks %></li>
			<li>Num ID tasks: <%=numIDTasks %></li>
		</ul>
	</li>
	<li>Num tasks have username: <%=hasUsername %></li>
	<li>User distribution: <%=userDistribution.toString() %></li>
	<li>Species distribution: <%=species.toString() %></li>
	<li>Algorithms: <%=algorithms.toString() %></li>
	<li>Bulk imports: <%=bulkImports.toString() %></li>
	<%
	


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


</body>
</html>
