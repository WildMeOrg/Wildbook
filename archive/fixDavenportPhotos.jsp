<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
org.ecocean.media.MediaAsset,org.ecocean.servlet.importer.ImportTask,
java.io.*,java.util.*, java.io.FileInputStream, 
java.text.SimpleDateFormat,
java.util.Date,
org.apache.poi.ss.usermodel.DateUtil,
java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, 
java.util.Iterator, java.lang.NumberFormatException"%>

<%!

private List<List<String>> getRecords(){

	List<List<String>> records = new ArrayList<>();
	try (BufferedReader br = new BufferedReader(new FileReader("/home/ubuntu/imagesByOccurrence.csv"))) {
	    String line;
	    while ((line = br.readLine()) != null) {
	        String[] values = line.split(",");
	        records.add(Arrays.asList(values));
	    }
	}
	catch(Exception e){
		e.printStackTrace();
	}
	return records;
}

%>

<%!
private boolean hasPhoto(String filename, List<MediaAsset> assets){
	for(MediaAsset asset:assets){
		//System.out.println("Comparing: "+asset.getFilename()+" and "+filename);
		if(asset.getFilename().toLowerCase().equals(filename.trim().toLowerCase())) return true;
	}	
	return false;
}
%>


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



<%

myShepherd.beginDBTransaction();

int numFixes=0;
int numOrphanAssets=0;	
int numDatalessAssets=0;	
int numDatasFixed=0;	
int numAssetsFixed=0;	
int numAssetsWithoutStore=0;	

boolean committing=true;


try{

 // List<Encounter> allEncs=myShepherd.getEncountersByField("specificEpithet","cepedianus");
 
 	//below only to reset an import task
	ImportTask it = myShepherd.getImportTask("b0229c75-71fe-4f62-acc4-411ddc470604");
	List<Encounter> allEncs = it.getEncounters();

	int count = 0;
	int needFixing=0;
	List<List<String>> records = getRecords();
	int numRecords = records.size();
	%>
	<ol>
	<%
	for(List<String> line:records){
		String occurrenceID = line.get(0);
		String photo1 = line.get(1);
		String photo2 = line.get(2);
		String photo3 = line.get(3);
		
		List<String> missing = new ArrayList<String>();
		
		//boolean missing=false;
		
		if(occurrenceID!=null && !occurrenceID.trim().equals("")){
			Occurrence occur=myShepherd.getOccurrence(occurrenceID);
			if(occur!=null){
				Encounter enc = occur.getEncounters().get(0);
				if(enc!=null)count++;
				List<MediaAsset> assets = enc.getMedia();
				
				if(!photo1.trim().equals("\"\"")&&!hasPhoto(photo1,assets)){
					missing.add(photo1);
				}
				if(!photo2.trim().equals("\"\"")&&!hasPhoto(photo2,assets)){
					missing.add(photo2);
				}
				if(!photo3.trim().equals("\"\"")&&!hasPhoto(photo3,assets)){
					missing.add(photo3);
				}
				
				if(missing.size()>0){
					needFixing++;
				
					//System.out.println(occurrenceID+":"+photo1+":"+photo2+":"+photo3);
					String fixMe=occurrenceID+":";
					for(String miss:missing){fixMe+=miss+",";}
					%>
					<li><%=fixMe %></li>
					<%
				}
			}
		}
		
	}
	
	%>
	</ol>          
	<p>Records: <%=records.size() %></p>
	<p>Num processed: <%=count %></p>  
	<p>Needs fixin': <%=needFixing %></p>
	
	
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
