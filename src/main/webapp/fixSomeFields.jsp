<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

	Shepherd myShepherd=new Shepherd(context);

// pg_dump -Ft sharks > sharks.out

//pg_restore -d sharks2 /home/webadmin/sharks.out


%>

<html>
<head>
<title>Fix Some Fields</title>

</head>


<body>
<p>Photo paths to fix.</p>
<ul>
<%

myShepherd.beginDBTransaction();

//build queries




try{


	String rootDir = getServletContext().getRealPath("/");
	String baseDir = ServletUtilities.dataDir(context, rootDir).replaceAll("dev_data_dir", "caribwhale_data_dir");
	
	Iterator allSPV=myShepherd.getAllSinglePhotoVideosNoQuery();
   
   
	while(allSPV.hasNext()){
		
		SinglePhotoVideo spv=(SinglePhotoVideo)allSPV.next();
		
		//String encNumber=spv.getCorrespondingEncounterNumber();
		String newPath=spv.getFullFileSystemPath();
		if(newPath.indexOf("encounters")!=-1){
			int position=newPath.indexOf("encounters");
			newPath=newPath.substring(position);
			newPath="/var/lib/tomcat7/webapps/wildbook_data_dir/"+newPath;
		}
		
		%>
		
		<%=newPath %><br>
		<%
		
		
		//spv.setFullFileSystemPath(newPath);
		
		//myShepherd.commitDBTransaction();
		//myShepherd.beginDBTransaction();
	}
	myShepherd.rollbackDBTransaction();

/*

while(allEncs.hasNext()){
	
	Encounter enc=(Encounter)allEncs.next();

	if(enc.getPatterningCode()==null){
		int numKwords=kwords.size();
		for(int i=0;i<numKwords;i++){
			String keyword=kwords.get(i);
			if(enc.hasKeyword(myShepherd.getKeyword(keyword))){
				enc.setPatterningCode(keyword);
				myShepherd.commitDBTransaction();
				myShepherd.beginDBTransaction();
			}
			else if((enc.getIndividualID()!=null)&&(!enc.getIndividualID().toLowerCase().equals("unassigned"))){
				MarkedIndividual indy=myShepherd.getMarkedIndividual(enc.getIndividualID());
				if(indy.getPatterningCode()!=null){enc.setPatterningCode(indy.getPatterningCode());}
			}
		}
	}
	
}
    
    */

%>




<%
} 
catch(Exception ex) {

	System.out.println("!!!An error occurred on page fixSomeFields.jsp. The error was:");
	ex.printStackTrace();
	myShepherd.rollbackDBTransaction();


}
finally{
	
	myShepherd.closeDBTransaction();
	myShepherd=null;
}
%>

</ul>
<p>Done successfully!</p>
</body>
</html>
