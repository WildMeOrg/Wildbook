<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.media.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>




<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("inspectShaneData.jsp");



%>

<html>
<head>
<title>Fix Some Fields</title>

</head>


<body>

<%

myShepherd.beginDBTransaction();

/*

"ENCOUNTER"."GENUS" = 'Physeter'

and

"ENCOUNTER"."SPECIFICEPITHET" = 'macrocephalus'

and "ENCOUNTER"."DWCDATEADDEDLONG" >= 1575504000000

-- and "ENCOUNTER"."SUBMITTERID" = 'UR-Stelle'

and "MEDIAASSET"."DETECTIONSTATUS" is null

*/

HashMap<String, MarkedIndividual> indies=new HashMap<String, MarkedIndividual>();


String filter="SELECT FROM org.ecocean.Encounter WHERE genus == \"Physeter\" && dwcDateAddedLong >= 1575504000000";


//Create a FetchGroup on the PMF called "TestGroup" for MyClass
//FetchGroup grp = pm.getPersistenceManagerFactory().getFetchGroup(MarkedIndividual.class, "TestGroup");
//grp.addMember("field1").addMember("field2");


try {
	
    Query q=myShepherd.getPM().newQuery(filter);
    Collection c=(Collection) (q.execute());
    ArrayList<Encounter> list = new ArrayList<Encounter>(c);
	q.closeAll();
	
	StringBuffer sb=new StringBuffer();
	int count=0;
	int uncount=0;
	int foundAnnots=0;
	int noMAACMID=0;
	
	
	

		for(Encounter enc:list){
			
			//assets
			List<MediaAsset> assets=enc.getMedia();
			for(MediaAsset asset:assets){
				if(asset.getDetectionStatus()==null){

					
					//sb.append(asset.getAcmId()+"\n");
					count++;
					
				}
				else{uncount++;}
				
				if(asset.getAcmId()==null)noMAACMID++;
				
			}
			
			//annots
			List<Annotation> annots=enc.getAnnotations();
			for(Annotation annot:annots){
				if(!annot.isTrivial()){

					
					//sb.append(asset.getAcmId()+"\n");
					foundAnnots++;
					
				}
			}
			
			if(request.getParameter("fixDuplicateIndividuals")!=null && enc.getIndividual()!=null){
				
				MarkedIndividual indy=enc.getIndividual();
				String displayName=indy.getDisplayName();
				
				//if we've never seen this whale before, let's add it to the hashmap
				if(!indies.containsKey(displayName)){
					indies.put(displayName, indy);
				}
				//if we have seen it before, we need to consolidate!
				else if(indy.getEncounters().size()==1){
					
					MarkedIndividual masterIndy=indies.get(displayName);
				      // here's where the magic happens
				      masterIndy.mergeIndividual(indy, request, myShepherd);
				
				     myShepherd.getPM().deletePersistent(indy);
				     myShepherd.updateDBTransaction();
					
				}
				
				
			}
			
			//if there are past individuals outside this data set with the same species and display name
			if(request.getParameter("reconcileWithPastIndividuals")!=null && enc.getIndividual()!=null){
				
				MarkedIndividual indy=enc.getIndividual();
				String displayName=indy.getDisplayName().trim();
				String regex = "(^|.*;)"+displayName+"(;.*|$)";
			      //System.out.println("withName: "+genus+" "+specificEpithet);
				List<MarkedIndividual> inds = MarkedIndividual.findByNames(myShepherd, regex, "Physeter", "macrocephalus");
				inds.remove(indy);
				
				for(MarkedIndividual m:inds){
					if(!m.getDisplayName().trim().equals(displayName)){
						inds.remove(m);
					}
				}
				
				if(inds.size()>0){
					%>
					<p>Found duplicates(<%=inds.size() %>) for: <%=displayName %></p>
					<%
					
					MarkedIndividual masterIndy=inds.get(0);
				      // here's where the magic happens
				      if(!masterIndy.getIndividualID().equals(indy.getIndividualID())){
				      	masterIndy.mergeIndividual(indy, request, myShepherd);
				
				     	myShepherd.getPM().deletePersistent(indy);
				     	myShepherd.updateDBTransaction();
				      }
					
				}
				
			}
			
			
			
			
			
		}

	
	//Util.writeToFile(sb.toString(), "/tmp/exportACMIDs.csv");
	
	%>
	<p>Remaining to send to detection <%=count %>/<%=(uncount+count) %> with <%=foundAnnots %> flukes found so far</p>
	<p>Num Mediassets missing acmID: <%=noMAACMID %></p>
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



</body>
</html>
