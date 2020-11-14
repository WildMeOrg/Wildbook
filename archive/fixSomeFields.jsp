<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
java.io.IOException,
java.nio.charset.StandardCharsets,org.ecocean.ia.Task,
java.nio.file.Files,org.ecocean.media.*,
java.nio.file.Paths,org.ecocean.servlet.importer.*,
java.util.stream.Stream,
org.ecocean.grid.*,java.nio.file.Files,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%!

private  String readAllBytesJava7(String filePath) 
{
    String content = "";

    try
    {
        content = new String ( Files.readAllBytes( Paths.get(filePath) ) );
    } 
    catch (IOException e) 
    {
        e.printStackTrace();
    }

    return content;
}

%>


<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);



%>

<html>
<head>
<title>Fix Some Fields</title>

</head>


<body>

<%

myShepherd.beginDBTransaction();

int numFixes=0;
int misMatch=0;
int reverts=0;

ArrayList<String> suspects=new ArrayList<String>();


try {
	
	String actual = readAllBytesJava7("/home/ubuntu/cloned.clones");

	//first, ensure we're only talking about wild dogs
	String filter="select from org.ecocean.Encounter where genus == 'Lycaon'";
	Query q=myShepherd.getPM().newQuery(filter);
	Collection c = (Collection)q.execute();
	ArrayList<Encounter> encs=new ArrayList<Encounter>(c);
	q.closeAll();
	%>
	
	<p>First, we look for MediaAssets spanning Encounters: <%=encs.size() %></p>
	<%
	for(Encounter enc:encs){
		System.out.println(enc.getCatalogNumber());
		
		for(MediaAsset ma:enc.getMedia()){
			ma.setAcmId(null);
			myShepherd.updateDBTransaction();
		}
		
		
		/*
		if(actual.indexOf(enc2trash.getCatalogNumber())!=-1){
			numFixes++;
			
			//throw away Encounter
	          Occurrence occ = myShepherd.getOccurrenceForEncounter(enc2trash.getID());
	          if (occ==null&&(enc2trash.getOccurrenceID()!=null)&&(myShepherd.isOccurrence(enc2trash.getOccurrenceID()))) {
	            occ = myShepherd.getOccurrence(enc2trash.getOccurrenceID());
	          }
	          
	          if(occ!=null) {
	            occ.removeEncounter(enc2trash);
	            enc2trash.setOccurrenceID(null);
	            
	            
	            myShepherd.commitDBTransaction();
	            myShepherd.beginDBTransaction();
	     
	          }
	          
	          //Remove it from an ImportTask if needed
	          ImportTask task=myShepherd.getImportTaskForEncounter(enc2trash.getCatalogNumber());
	          if(task!=null) {
	            task.removeEncounter(enc2trash);
	            task.addLog("Servlet EncounterDelete removed Encounter: "+enc2trash.getCatalogNumber());
	            myShepherd.updateDBTransaction();
	          }
	          
	          

	          if (myShepherd.getImportTaskForEncounter(enc2trash)!=null) {
	            ImportTask itask = myShepherd.getImportTaskForEncounter(enc2trash);
	            itask.removeEncounter(enc2trash);
	            myShepherd.commitDBTransaction();
	            myShepherd.beginDBTransaction();
	          }
	          
	          //Set all associated annotations matchAgainst to false
	          enc2trash.useAnnotationsForMatching(false);
	          
	          //break association with User object submitters
	          if(enc2trash.getSubmitters()!=null){
	            enc2trash.setSubmitters(null);
	            myShepherd.commitDBTransaction();
	            myShepherd.beginDBTransaction();
	          }
	          
	          //break asociation with User object photographers
	          if(enc2trash.getPhotographers()!=null){
	            enc2trash.setPhotographers(null);
	            myShepherd.commitDBTransaction();
	            myShepherd.beginDBTransaction();
	          }

	          //record who deleted this encounter
	          enc2trash.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Deleted this encounter from the database.");
	          myShepherd.commitDBTransaction();

	          ArrayList<Annotation> anns = enc2trash.getAnnotations();
	          for (Annotation ann : anns) {
	            myShepherd.beginDBTransaction();
	            enc2trash.removeAnnotation(ann);
	            myShepherd.updateDBTransaction();
	            List<Task> iaTasks = Task.getTasksFor(ann, myShepherd);
	            if (iaTasks!=null&&!iaTasks.isEmpty()) {
	              for (Task iaTask : iaTasks) {
	                iaTask.removeObject(ann);
	                myShepherd.updateDBTransaction();
	              }
	            }
	            myShepherd.throwAwayAnnotation(ann);
	            myShepherd.commitDBTransaction();
	          }
			
			
			myShepherd.throwAwayEncounter(enc2trash);
			myShepherd.updateDBTransaction();
			
		}
		*/
		
		//else{
		/*	
			for(Annotation annot:enc.getAnnotations()){
				if(annot.getMediaAsset()!=null){
					
					//reset the annot
					annot.getMediaAsset().setDetectionStatus(null);
					if(annot.getSiblings()!=null && annot.getSiblings().size()>0 ){
						enc.removeAnnotation(annot);
						
						myShepherd.updateDBTransaction();
			            List<Task> iaTasks = Task.getTasksFor(annot, myShepherd);
			            if (iaTasks!=null&&!iaTasks.isEmpty()) {
			              for (Task iaTask : iaTasks) {
			                iaTask.removeObject(annot);
			                myShepherd.updateDBTransaction();
			              }
			            }
						myShepherd.throwAwayAnnotation(annot);
						myShepherd.updateDBTransaction();
					}
					else if(!annot.isTrivial())annot.revertToTrivial(myShepherd);
					
					myShepherd.updateDBTransaction();
					reverts++;
					
				}
				else{
					
					//delete the annot
					misMatch++;
					enc.removeAnnotation(annot);
					myShepherd.updateDBTransaction();
					myShepherd.throwAwayAnnotation(annot);
					myShepherd.updateDBTransaction();
				}
			}
			*/
			
		//}
		
		
		
		/*
		List<Annotation> annots=enc.getAnnotations();
		for(Annotation annot:annots){
			
			if(!annot.isTrivial() && annot.getSiblings() !=null && annot.getSiblings().size()>0){
				
				//now check that at least one of the siblings is attached to Encounters
				List<Annotation> sibs=annot.getSiblings();
				boolean sibAttached=false;
				for(Annotation sib:sibs){
				
					if(sib.findEncounter(myShepherd)!=null){
						
						Encounter e=sib.findEncounter(myShepherd);
						if(!suspects.contains(e.getCatalogNumber())){
							sibAttached=true;
						}
						
					}
					
				}
				if(sibAttached && !suspects.contains(enc.getCatalogNumber())){
					suspects.add(enc.getCatalogNumber());
				}
				
				
			}
			
			
		}
		*/
		
		
		
	}
	%>
	<p>Suspects size: <%=suspects.size() %></p>
	<ul>
	<%
	for(String s:suspects){
		%>
		<li><a target="_blank" href="../encounters/encounter.jsp?number=<%=s %>"><%=s %></a></li>
		<%
	}
	%>
	</ul>
	<%

	
}
catch(Exception e){
	e.printStackTrace();
	myShepherd.rollbackDBTransaction();
}
finally{
	myShepherd.closeDBTransaction();

}

%>



<p>Enc Deletes: <%=numFixes %></p>
<p>Annot Mismatches: <%=misMatch %></p>
<p>Annot reverts: <%=reverts %></p>

</body>
</html>
