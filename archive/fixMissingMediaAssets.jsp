<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.ia.*,java.util.*,
org.ecocean.media.*,org.ecocean.servlet.importer.ImportTask,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>


<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);



%>

<html>
<head>
<title>Fix Missing Media Assets</title>

</head>


<body>


<%!

public void RecursiveList(File f, ArrayList<File> files)  
{ 


      
    // for files 
    if(f.isFile()) 
        files.add(f); 
      
    // for sub-directories 
    else if(f.isDirectory()) 
    { 
     
        // recursion for sub-directories 
        List<File> list = Arrays.asList(f.listFiles());
        for(File m:list){
        	RecursiveList(m, files); 
        }
        
    } 
    
} 
 
%>

<%

myShepherd.beginDBTransaction();

//look here for images
String localPath="/import/IcelandHumpbacks";
File loc=new File(localPath);

ArrayList<File> files=new ArrayList<File>();
RecursiveList(loc, files);

try {
	
	//the query to look for 0-byte images that are missing
	Query q=myShepherd.getPM().newQuery("SELECT FROM org.ecocean.Encounter WHERE ( submitterID == 'THudson' )");
	
	Collection c=(Collection)q.execute();
	ArrayList<Encounter> al=new ArrayList<Encounter>(c);
	
	ArrayList<MediaAsset> missing=new ArrayList<MediaAsset>();
	int numMA=0;
	
	int numNeedingDetection=0;
	
	for(Encounter enc:al){
		
		
		
		List<MediaAsset> mas=enc.getMedia(); 
		for(MediaAsset ma:mas){
			File f=ma.localPath().toFile();
			long l=f.length();
			if(l==0){
				missing.add(ma);
			}
			numMA++;
		}
		
		//now look if it only has trivial annots...those need to be sent to detection
		boolean onlyTrivials=true;
		ArrayList<Annotation> annots=enc.getAnnotations();
		for(Annotation annot:annots){
			if(!annot.isTrivial())onlyTrivials=false;
		}
		if(onlyTrivials){
				numNeedingDetection+=mas.size();
		
		
			//for(MediaAsset ma:mas){
			//	ma.setDetectionStatus(null);
			//	myShepherd.updateDBTransaction();
			//}
		}
	}
	
	%>
	
	<p>Num Missing: <%=missing.size() %>/<%=numMA %></p>
	<p>Num pot. replacements: <%=files.size() %></p>
	<%
	
	int numCouldBeSolved=0;
	for(MediaAsset ma:missing){
		for(File f:files){
			if(f.getName().equals(ma.getFilename())){
				numCouldBeSolved++;
				ma.copyIn(f);
				ma.updateStandardChildren(myShepherd);
				ma.setDetectionStatus(null);
				myShepherd.updateDBTransaction();
			}
		}
	}
	
	%>
	<p>Num could be solved: <%=numCouldBeSolved %></p>
	<p>Num needing detection: <%=numNeedingDetection %></p>
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
